package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RelatorioComissaoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RelatorioDiarioResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RelatoriosReportsDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionEntry;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionEntryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository.SummaryTotals;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository.TransacaoFilter;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Espelha {@code modules/reports/application/ServicoRelatorios.java}.
 *
 * <p><b>Escopo desta etapa</b>: todos os metodos do original exceto {@code abandono} — delega
 * para {@code ServicoDashboard.listarFilaReativacaoWhatsApp}, que depende do modulo {@code chat}
 * (ainda nao portado). Ver o Javadoc de {@link ServicoDashboard} e o registro em
 * MIGRACAO-QUARKUS-SPRING.md, secao "reports".
 *
 * <p>{@code profissionalRepository} e injetado sem uso — o original ja tinha esse campo morto
 * (import nao utilizado em nenhum metodo); preservado por paridade, nao "corrigido".
 */
@Service
public class ServicoRelatorios {

  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  @PersistenceContext private EntityManager entityManager;

  private final ContextoTenant contextoTenant;
  private final AgendamentoRepository agendamentoRepository;
  private final TransacaoQueryRepository transacaoQueryRepository;
  private final ProfissionalRepository profissionalRepository;
  private final ProfissionalWorkingHourRepository profissionalWorkingHourRepository;
  private final CommissionEntryRepository commissionEntryRepository;

  public ServicoRelatorios(
      ContextoTenant contextoTenant,
      AgendamentoRepository agendamentoRepository,
      TransacaoQueryRepository transacaoQueryRepository,
      ProfissionalRepository profissionalRepository,
      ProfissionalWorkingHourRepository profissionalWorkingHourRepository,
      CommissionEntryRepository commissionEntryRepository) {
    this.contextoTenant = contextoTenant;
    this.agendamentoRepository = agendamentoRepository;
    this.transacaoQueryRepository = transacaoQueryRepository;
    this.profissionalRepository = profissionalRepository;
    this.profissionalWorkingHourRepository = profissionalWorkingHourRepository;
    this.commissionEntryRepository = commissionEntryRepository;
  }

  @Transactional(readOnly = true)
  public RelatorioDiarioResponse diario(String date) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate dia = DataUtil.parseDataISO(date);
    var inicioDia = dia.atStartOfDay(ZONA_BR).toInstant();
    var fimDia = dia.plusDays(1).atStartOfDay(ZONA_BR).minusNanos(1).toInstant();

    List<Agendamento> agendamentosDia = agendamentoRepository.listByTenantAndDate(tenantId, dia, Pageable.unpaged());
    SummaryTotals summaryTotals =
        transacaoQueryRepository.summarizeFiltered(
            new TransacaoFilter(tenantId, inicioDia, fimDia, null, null, null, null, null));

    RelatorioDiarioResponse r = new RelatorioDiarioResponse();
    r.date = dia.toString();
    r.totalAppointments = agendamentosDia.size();
    r.totalRevenue = summaryTotals.totalIncome();
    r.totalExpenses = summaryTotals.totalExpenses();
    r.balance = NumericUtil.subtract(r.totalRevenue, r.totalExpenses);
    return r;
  }

  @Transactional(readOnly = true)
  public RelatorioComissaoResponse comissoes(String from, String to, String professionalId, String professionalUserId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate inicio = DataUtil.parseDataISO(from);
    LocalDate fim = DataUtil.parseDataISO(to);
    if (fim.isBefore(inicio)) throw new IllegalArgumentException("Periodo invalido");

    UUID profissionalId = parseProfessionalId(professionalId, professionalUserId);

    List<Agendamento> agendamentos =
        agendamentoRepository.listByTenantAndProfessional(tenantId, profissionalId, Pageable.unpaged());

    BigDecimal receitaPeriodo =
        agendamentos.stream()
            .filter(a -> a.getDate() != null && !a.getDate().isBefore(inicio) && !a.getDate().isAfter(fim))
            .filter(a -> a.getStatus() == StatusAgendamento.COMPLETED)
            .map(Agendamento::resolveEffectiveTotalPrice)
            .reduce(NumericUtil.zero(), NumericUtil::add);

    List<CommissionEntry> entries =
        commissionEntryRepository.listByTenantAndProfessionalAndCreatedAtRange(
            tenantId,
            profissionalId,
            inicio.atStartOfDay(ZONA_BR).toInstant(),
            fim.plusDays(1).atStartOfDay(ZONA_BR).toInstant());

    long valorComissaoCents =
        entries.stream()
            .filter(entry -> !"REVERSED".equals(entry.getEntryStatus()))
            .mapToLong(CommissionEntry::getTotalAmountCents)
            .sum();

    RelatorioComissaoResponse r = new RelatorioComissaoResponse();
    r.professionalId = profissionalId.toString();
    r.from = inicio.toString();
    r.to = fim.toString();
    r.totalRevenue = receitaPeriodo;
    r.commissionRate = resolveSingleCommissionRate(entries);
    r.commissionValue = NumericUtil.fromCents(valorComissaoCents);
    return r;
  }

  // ─── Heatmap de ocupacao (F16) ────────────────────────────────────────────

  @Transactional(readOnly = true)
  public RelatoriosReportsDtos.HeatmapReportResponse heatmap(String dataInicio, String dataFim, String professionalId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate inicio = DataUtil.parseDataISO(dataInicio);
    LocalDate fim = DataUtil.parseDataISO(dataFim);
    if (inicio == null || fim == null) throw new IllegalArgumentException("Periodo obrigatorio");
    if (fim.isBefore(inicio)) throw new IllegalArgumentException("Periodo invalido");
    if (inicio.plusDays(370).isBefore(fim)) throw new IllegalArgumentException("Periodo maximo de 370 dias");

    UUID profissionalId = professionalId != null && !professionalId.isBlank() ? UUID.fromString(professionalId) : null;

    HeatmapAccumulator[][] buckets = new HeatmapAccumulator[7][24];
    for (int day = 0; day < 7; day++) {
      for (int hour = 0; hour < 24; hour++) {
        buckets[day][hour] = new HeatmapAccumulator(day, hour);
      }
    }

    List<ProfissionalWorkingHour> workingHours = listWorkingHours(tenantId, profissionalId);
    for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
      int diaSemana = toJsDayOfWeek(dia);
      int diaIso = dia.getDayOfWeek().getValue();
      for (ProfissionalWorkingHour workingHour : workingHours) {
        if (!isValidWorkingHour(workingHour) || !matchesDay(workingHour.getDayOfWeek(), diaIso)) continue;
        addMinutesByHour(buckets[diaSemana], workingHour.getStartTime(), workingHour.getEndTime(), true);
      }
    }

    List<Agendamento> agendamentos = listAppointmentsForHeatmap(tenantId, profissionalId, inicio, fim);
    for (Agendamento agendamento : agendamentos) {
      if (agendamento == null || agendamento.getDate() == null || agendamento.getStatus() == StatusAgendamento.CANCELLED) {
        continue;
      }
      LocalTime start = parseTimeOrNull(agendamento.getStartTime());
      LocalTime end = parseTimeOrNull(agendamento.getEndTime());
      if (start == null || end == null || !start.isBefore(end)) continue;
      int diaSemana = toJsDayOfWeek(agendamento.getDate());
      buckets[diaSemana][start.getHour()].appointments++;
      addMinutesByHour(buckets[diaSemana], start, end, false);
    }

    RelatoriosReportsDtos.HeatmapReportResponse response = new RelatoriosReportsDtos.HeatmapReportResponse();
    response.dataInicio = inicio.toString();
    response.dataFim = fim.toString();
    response.professionalId = profissionalId != null ? profissionalId.toString() : null;
    response.matrix = new ArrayList<>();
    for (int day = 0; day < 7; day++) {
      List<RelatoriosReportsDtos.HeatmapCell> row = new ArrayList<>();
      for (int hour = 0; hour < 24; hour++) {
        row.add(toHeatmapCell(buckets[day][hour]));
      }
      response.matrix.add(row);
    }
    return response;
  }

  private List<ProfissionalWorkingHour> listWorkingHours(UUID tenantId, UUID professionalId) {
    if (professionalId != null) {
      return profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId);
    }
    return profissionalWorkingHourRepository.listByTenant(tenantId);
  }

  private List<Agendamento> listAppointmentsForHeatmap(UUID tenantId, UUID professionalId, LocalDate inicio, LocalDate fim) {
    if (professionalId != null) {
      return agendamentoRepository.listByTenantAndProfessionalAndDateRangeExcludingStatus(
          tenantId, professionalId, inicio, fim, StatusAgendamento.CANCELLED);
    }
    return agendamentoRepository.listByTenantAndDateRangeExcludingStatus(tenantId, inicio, fim, StatusAgendamento.CANCELLED);
  }

  private static boolean isValidWorkingHour(ProfissionalWorkingHour workingHour) {
    return workingHour != null
        && workingHour.isWorking()
        && workingHour.getStartTime() != null
        && workingHour.getEndTime() != null
        && workingHour.getStartTime().isBefore(workingHour.getEndTime());
  }

  private static boolean matchesDay(int configuredDay, int targetDayIso) {
    if (configuredDay == targetDayIso) return true;
    int targetJs = targetDayIso == DayOfWeek.SUNDAY.getValue() ? 0 : targetDayIso;
    return configuredDay == targetJs;
  }

  private static int toJsDayOfWeek(LocalDate date) {
    return date.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : date.getDayOfWeek().getValue();
  }

  private static LocalTime parseTimeOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalTime.parse(value.trim());
    } catch (Exception e) {
      return null;
    }
  }

  static void addMinutesByHour(HeatmapAccumulator[] dayBuckets, LocalTime start, LocalTime end, boolean available) {
    if (dayBuckets == null || start == null || end == null || !start.isBefore(end)) return;

    LocalTime cursor = start;
    while (cursor.isBefore(end)) {
      int hour = cursor.getHour();
      LocalTime hourEnd = LocalTime.of(hour, 0).plusHours(1);
      LocalTime sliceEnd = end.isBefore(hourEnd) ? end : hourEnd;
      int minutes = (int) java.time.Duration.between(cursor, sliceEnd).toMinutes();
      if (minutes > 0) {
        if (available) {
          dayBuckets[hour].availableMinutes += minutes;
        } else {
          dayBuckets[hour].busyMinutes += minutes;
        }
      }
      cursor = sliceEnd;
    }
  }

  private static RelatoriosReportsDtos.HeatmapCell toHeatmapCell(HeatmapAccumulator bucket) {
    if (bucket.availableMinutes <= 0) return null;

    RelatoriosReportsDtos.HeatmapCell cell = new RelatoriosReportsDtos.HeatmapCell();
    cell.diaSemana = bucket.dayOfWeek;
    cell.hora = bucket.hour;
    cell.agendamentos = bucket.appointments;
    cell.minutosOcupados = bucket.busyMinutes;
    cell.minutosDisponiveis = bucket.availableMinutes;
    cell.ocupacaoPercent =
        BigDecimal.valueOf(bucket.busyMinutes)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(bucket.availableMinutes), 2, RoundingMode.HALF_UP);
    return cell;
  }

  static final class HeatmapAccumulator {
    final int dayOfWeek;
    final int hour;
    int appointments;
    int busyMinutes;
    int availableMinutes;

    HeatmapAccumulator(int dayOfWeek, int hour) {
      this.dayOfWeek = dayOfWeek;
      this.hour = hour;
    }
  }

  // ─── Relatorio Estoque ────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public RelatoriosReportsDtos.EstoqueReportResponse relatorioEstoque(String from, String to, String itemId, String tipo) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate inicio = from != null && !from.isBlank() ? DataUtil.parseDataISO(from) : LocalDate.now().withDayOfMonth(1);
    LocalDate fim = to != null && !to.isBlank() ? DataUtil.parseDataISO(to) : LocalDate.now();

    StringBuilder sql =
        new StringBuilder(
            """
            SELECT m.id::text, m.tipo, m.item_estoque_id::text,
                   i.nome, i.unidade_medida,
                   m.quantidade, m.saldo_anterior, m.saldo_posterior,
                   m.motivo, m.origem::text,
                   m.valor_unitario_pago, m.valor_total_movimentacao,
                   m.created_at::text
            FROM movimentacoes_estoque m
            JOIN itens_estoque i ON i.id = m.item_estoque_id AND i.tenant_id = m.tenant_id
            WHERE m.tenant_id = :tenantId
              AND (m.created_at AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN :from AND :to
            """);
    if (itemId != null && !itemId.isBlank()) {
      sql.append(" AND m.item_estoque_id = :itemId");
    }
    if (tipo != null && !tipo.isBlank()) {
      sql.append(" AND m.tipo = :tipo");
    }
    sql.append(" ORDER BY m.created_at DESC LIMIT 500");

    var q =
        entityManager
            .createNativeQuery(sql.toString())
            .setParameter("tenantId", tenantId)
            .setParameter("from", inicio)
            .setParameter("to", fim);
    if (itemId != null && !itemId.isBlank()) {
      q.setParameter("itemId", UUID.fromString(itemId));
    }
    if (tipo != null && !tipo.isBlank()) {
      q.setParameter("tipo", tipo);
    }

    List<Object[]> rows = q.getResultList();

    RelatoriosReportsDtos.EstoqueReportResponse response = new RelatoriosReportsDtos.EstoqueReportResponse();
    response.summary = new RelatoriosReportsDtos.EstoqueReportSummary();
    response.items = new ArrayList<>();

    BigDecimal zero = BigDecimal.ZERO;
    BigDecimal totalEntradas = zero;
    BigDecimal totalSaidas = zero;

    for (Object[] row : rows) {
      RelatoriosReportsDtos.EstoqueMovimentacaoItem item = new RelatoriosReportsDtos.EstoqueMovimentacaoItem();
      item.id = str(row[0]);
      item.tipo = str(row[1]);
      item.itemId = str(row[2]);
      item.itemNome = str(row[3]);
      item.unidadeMedida = str(row[4]);
      item.quantidade = bd(row[5]);
      item.saldoAnterior = bd(row[6]);
      item.saldoPosterior = bd(row[7]);
      item.motivo = str(row[8]);
      item.origem = str(row[9]);
      item.valorUnitarioPago = bd(row[10]);
      item.valorTotalMovimentacao = bd(row[11]);
      item.createdAt = str(row[12]);
      response.items.add(item);

      if ("ENTRADA".equals(item.tipo)) {
        response.summary.totalEntradas++;
        totalEntradas = totalEntradas.add(item.valorTotalMovimentacao != null ? item.valorTotalMovimentacao : zero);
      } else if ("SAIDA".equals(item.tipo)) {
        response.summary.totalSaidas++;
        totalSaidas = totalSaidas.add(item.valorTotalMovimentacao != null ? item.valorTotalMovimentacao : zero);
      } else {
        response.summary.totalAjustes++;
      }
    }

    response.summary.valorTotalEntradas = totalEntradas;
    response.summary.valorTotalSaidas = totalSaidas;

    Number abaixoMinimo =
        (Number)
            entityManager
                .createNativeQuery(
                    "SELECT COUNT(*) FROM itens_estoque WHERE tenant_id = :tenantId AND ativo = true AND saldo_atual < estoque_minimo")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
    response.summary.itensAbaixoMinimo = abaixoMinimo != null ? abaixoMinimo.intValue() : 0;

    return response;
  }

  // ─── Relatorio Vendas ────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public RelatoriosReportsDtos.VendasReportResponse relatorioVendas(String from, String to, String professionalId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate inicio = from != null && !from.isBlank() ? DataUtil.parseDataISO(from) : LocalDate.now().withDayOfMonth(1);
    LocalDate fim = to != null && !to.isBlank() ? DataUtil.parseDataISO(to) : LocalDate.now();

    StringBuilder sqlServicos =
        new StringBuilder(
            """
            SELECT s.id::text, s.name,
                   COUNT(DISTINCT a.id)::int AS total,
                   COALESCE(SUM(ai.total_price), 0) AS receita
            FROM appointments a
            JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
            JOIN services s ON s.id = ai.service_id AND s.tenant_id = a.tenant_id
            WHERE a.tenant_id = :tenantId
              AND a.status = 'Concluido'
              AND a.date BETWEEN :from AND :to
            """);
    if (professionalId != null && !professionalId.isBlank()) {
      sqlServicos.append(" AND a.professional_id = :profId");
    }
    sqlServicos.append(" GROUP BY s.id, s.name ORDER BY receita DESC LIMIT 20");

    var qServicos =
        entityManager
            .createNativeQuery(sqlServicos.toString())
            .setParameter("tenantId", tenantId)
            .setParameter("from", inicio)
            .setParameter("to", fim);
    if (professionalId != null && !professionalId.isBlank()) {
      qServicos.setParameter("profId", UUID.fromString(professionalId));
    }

    List<Object[]> servicosRows = qServicos.getResultList();

    StringBuilder sqlProf =
        new StringBuilder(
            """
            SELECT p.id::text, p.name,
                   COUNT(DISTINCT a.id)::int AS total,
                   COALESCE(SUM(ai.total_price), 0) AS receita
            FROM appointments a
            JOIN professionals p ON p.id = a.professional_id AND p.tenant_id = a.tenant_id
            JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
            WHERE a.tenant_id = :tenantId
              AND a.status = 'Concluido'
              AND a.date BETWEEN :from AND :to
            """);
    if (professionalId != null && !professionalId.isBlank()) {
      sqlProf.append(" AND a.professional_id = :profId");
    }
    sqlProf.append(" GROUP BY p.id, p.name ORDER BY receita DESC LIMIT 20");

    var qProf =
        entityManager
            .createNativeQuery(sqlProf.toString())
            .setParameter("tenantId", tenantId)
            .setParameter("from", inicio)
            .setParameter("to", fim);
    if (professionalId != null && !professionalId.isBlank()) {
      qProf.setParameter("profId", UUID.fromString(professionalId));
    }
    List<Object[]> profRows = qProf.getResultList();

    StringBuilder sqlSum =
        new StringBuilder(
            """
            SELECT COUNT(DISTINCT a.id)::int, COALESCE(SUM(ai.total_price), 0)
            FROM appointments a
            JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
            WHERE a.tenant_id = :tenantId
              AND a.status = 'Concluido'
              AND a.date BETWEEN :from AND :to
            """);
    if (professionalId != null && !professionalId.isBlank()) {
      sqlSum.append(" AND a.professional_id = :profId");
    }
    var qSum =
        entityManager
            .createNativeQuery(sqlSum.toString())
            .setParameter("tenantId", tenantId)
            .setParameter("from", inicio)
            .setParameter("to", fim);
    if (professionalId != null && !professionalId.isBlank()) {
      qSum.setParameter("profId", UUID.fromString(professionalId));
    }
    Object[] sumRow = (Object[]) qSum.getSingleResult();

    RelatoriosReportsDtos.VendasReportResponse response = new RelatoriosReportsDtos.VendasReportResponse();
    response.summary = new RelatoriosReportsDtos.VendasReportSummary();
    int totalAgend = sumRow[0] != null ? ((Number) sumRow[0]).intValue() : 0;
    BigDecimal receitaTotal = bd(sumRow[1]);
    response.summary.totalAgendamentos = totalAgend;
    response.summary.receitaTotal = receitaTotal;
    response.summary.ticketMedio =
        totalAgend > 0 ? receitaTotal.divide(BigDecimal.valueOf(totalAgend), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

    response.topServicos = new ArrayList<>();
    for (Object[] row : servicosRows) {
      RelatoriosReportsDtos.VendasServicoItem item = new RelatoriosReportsDtos.VendasServicoItem();
      item.servicoId = str(row[0]);
      item.servicoNome = str(row[1]);
      item.totalAgendamentos = row[2] != null ? ((Number) row[2]).intValue() : 0;
      item.receitaTotal = bd(row[3]);
      item.ticketMedio =
          item.totalAgendamentos > 0
              ? item.receitaTotal.divide(BigDecimal.valueOf(item.totalAgendamentos), 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      response.topServicos.add(item);
    }

    response.topProfissionais = new ArrayList<>();
    for (Object[] row : profRows) {
      RelatoriosReportsDtos.VendasProfissionalItem item = new RelatoriosReportsDtos.VendasProfissionalItem();
      item.profissionalId = str(row[0]);
      item.profissionalNome = str(row[1]);
      item.totalAgendamentos = row[2] != null ? ((Number) row[2]).intValue() : 0;
      item.receitaTotal = bd(row[3]);
      item.ticketMedio =
          item.totalAgendamentos > 0
              ? item.receitaTotal.divide(BigDecimal.valueOf(item.totalAgendamentos), 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      response.topProfissionais.add(item);
    }

    return response;
  }

  // ─── Relatorio Clientes ──────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public RelatoriosReportsDtos.ClientesReportResponse relatorioClientes(String from, String to, boolean onlyInactive) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate inicio = from != null && !from.isBlank() ? DataUtil.parseDataISO(from) : LocalDate.now().minusDays(180);
    LocalDate fim = to != null && !to.isBlank() ? DataUtil.parseDataISO(to) : LocalDate.now();

    String sql =
        """
        SELECT c.id::text, c.name, c.phone, c.email,
               COUNT(a.id)::int AS total_visitas,
               COALESCE(SUM(ai.total_price), 0) AS receita_total,
               MAX(a.date)::text AS ultima_visita,
               COALESCE(CURRENT_DATE - MAX(a.date), 999) AS dias_sem_visita
        FROM clients c
        LEFT JOIN appointments a
          ON a.client_id = c.id
         AND a.tenant_id = c.tenant_id
         AND a.status = 'Concluido'
         AND a.date BETWEEN :from AND :to
        LEFT JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
        WHERE c.tenant_id = :tenantId
        GROUP BY c.id, c.name, c.phone, c.email
        ORDER BY receita_total DESC, total_visitas DESC
        LIMIT 200
        """;

    List<Object[]> rows =
        entityManager
            .createNativeQuery(sql)
            .setParameter("tenantId", tenantId)
            .setParameter("from", inicio)
            .setParameter("to", fim)
            .getResultList();

    RelatoriosReportsDtos.ClientesReportResponse response = new RelatoriosReportsDtos.ClientesReportResponse();
    response.summary = new RelatoriosReportsDtos.ClientesReportSummary();
    response.items = new ArrayList<>();

    BigDecimal receitaTotal = BigDecimal.ZERO;
    int ativos = 0;
    int inativos = 0;

    for (Object[] row : rows) {
      int diasSemVisita = row[7] != null ? ((Number) row[7]).intValue() : 999;
      boolean inativo = diasSemVisita > 60;
      if (onlyInactive && !inativo) continue;

      RelatoriosReportsDtos.ClienteReportItem item = new RelatoriosReportsDtos.ClienteReportItem();
      item.clienteId = str(row[0]);
      item.clienteNome = str(row[1]);
      item.clientePhone = str(row[2]);
      item.clienteEmail = str(row[3]);
      item.totalVisitas = row[4] != null ? ((Number) row[4]).intValue() : 0;
      item.receitaTotal = bd(row[5]);
      item.ultimaVisita = str(row[6]);
      item.diasSemVisita = diasSemVisita;
      item.inativo = inativo;
      response.items.add(item);

      receitaTotal = receitaTotal.add(item.receitaTotal != null ? item.receitaTotal : BigDecimal.ZERO);
      if (inativo) inativos++;
      else ativos++;
    }

    response.summary.totalClientes = ativos + inativos;
    response.summary.clientesAtivos = ativos;
    response.summary.clientesInativos = inativos;
    response.summary.receitaTotal = receitaTotal;
    return response;
  }

  // ─── Relatorio Gerencial ─────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public RelatoriosReportsDtos.GerencialReportResponse relatorioGerencial(String from, String to) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate inicio = from != null && !from.isBlank() ? DataUtil.parseDataISO(from) : LocalDate.now().withDayOfMonth(1);
    LocalDate fim = to != null && !to.isBlank() ? DataUtil.parseDataISO(to) : LocalDate.now();

    var inicioDia = inicio.atStartOfDay(ZONA_BR).toInstant();
    var fimDia = fim.plusDays(1).atStartOfDay(ZONA_BR).minusNanos(1).toInstant();

    SummaryTotals totals =
        transacaoQueryRepository.summarizeFiltered(
            new TransacaoFilter(tenantId, inicioDia, fimDia, null, null, null, null, null));

    Object[] agendSumRow =
        (Object[])
            entityManager
                .createNativeQuery(
                    """
                    SELECT COUNT(DISTINCT a.id)::int,
                           COUNT(DISTINCT a.id) FILTER (WHERE a.status = 'Concluido')::int,
                           COUNT(DISTINCT a.id) FILTER (WHERE a.status = 'Cancelado')::int,
                           COALESCE(SUM(ai.total_price) FILTER (WHERE a.status = 'Concluido'), 0)
                    FROM appointments a
                    LEFT JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
                    WHERE a.tenant_id = :tenantId AND a.date BETWEEN :from AND :to
                    """)
                .setParameter("tenantId", tenantId)
                .setParameter("from", inicio)
                .setParameter("to", fim)
                .getSingleResult();

    int totalAgend = agendSumRow[0] != null ? ((Number) agendSumRow[0]).intValue() : 0;
    int concluidos = agendSumRow[1] != null ? ((Number) agendSumRow[1]).intValue() : 0;
    int cancelados = agendSumRow[2] != null ? ((Number) agendSumRow[2]).intValue() : 0;
    BigDecimal receitaAgend = bd(agendSumRow[3]);

    List<Object[]> topServRows =
        entityManager
            .createNativeQuery(
                """
                SELECT s.id::text, s.name,
                       COUNT(DISTINCT a.id)::int,
                       COALESCE(SUM(ai.total_price), 0)
                FROM appointments a
                JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
                JOIN services s ON s.id = ai.service_id AND s.tenant_id = a.tenant_id
                WHERE a.tenant_id = :tenantId AND a.status = 'Concluido'
                  AND a.date BETWEEN :from AND :to
                GROUP BY s.id, s.name ORDER BY 4 DESC LIMIT 10
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("from", inicio)
            .setParameter("to", fim)
            .getResultList();

    List<Object[]> topProfRows =
        entityManager
            .createNativeQuery(
                """
                SELECT p.id::text, p.name,
                       COUNT(DISTINCT a.id)::int,
                       COALESCE(SUM(ai.total_price), 0)
                FROM appointments a
                JOIN professionals p ON p.id = a.professional_id AND p.tenant_id = a.tenant_id
                JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
                WHERE a.tenant_id = :tenantId AND a.status = 'Concluido'
                  AND a.date BETWEEN :from AND :to
                GROUP BY p.id, p.name ORDER BY 4 DESC LIMIT 10
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("from", inicio)
            .setParameter("to", fim)
            .getResultList();

    List<Object[]> serieRows =
        entityManager
            .createNativeQuery(
                """
                SELECT (date AT TIME ZONE 'America/Sao_Paulo')::date::text AS dia,
                       COALESCE(SUM(amount) FILTER (WHERE type = 'INCOME'), 0) AS receita,
                       COALESCE(SUM(amount) FILTER (WHERE type = 'EXPENSE'), 0) AS despesa
                FROM transactions
                WHERE tenant_id = :tenantId
                  AND date BETWEEN :from AND :to
                GROUP BY 1 ORDER BY 1
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("from", inicioDia)
            .setParameter("to", fimDia)
            .getResultList();

    Object[] ocupacaoRow =
        (Object[])
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      COUNT(DISTINCT p.id)::int AS profissionais_ativos,
                      COUNT(DISTINCT a.id) FILTER (WHERE a.status = 'Concluido')::int AS agendamentos_concluidos
                    FROM professionals p
                    LEFT JOIN appointments a ON a.professional_id = p.id
                      AND a.tenant_id = p.tenant_id
                      AND a.date BETWEEN :from AND :to
                    WHERE p.tenant_id = :tenantId AND p.is_active = true
                    """)
                .setParameter("tenantId", tenantId)
                .setParameter("from", inicio)
                .setParameter("to", fim)
                .getSingleResult();

    int profissionaisAtivos = ocupacaoRow[0] != null ? ((Number) ocupacaoRow[0]).intValue() : 0;
    int agendamentosConcluidos = ocupacaoRow[1] != null ? ((Number) ocupacaoRow[1]).intValue() : 0;
    long diasPeriodo = inicio.until(fim, java.time.temporal.ChronoUnit.DAYS) + 1;
    long slotsDisponiveis = profissionaisAtivos * diasPeriodo * 16L;
    Double occupancyRate =
        (profissionaisAtivos > 0 && slotsDisponiveis > 0)
            ? Math.min(
                100.0,
                BigDecimal.valueOf(agendamentosConcluidos)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(slotsDisponiveis), 2, RoundingMode.HALF_UP)
                    .doubleValue())
            : null;

    RelatoriosReportsDtos.GerencialReportResponse response = new RelatoriosReportsDtos.GerencialReportResponse();

    response.financeiro = new RelatoriosReportsDtos.GerencialFinanceiroSummary();
    response.financeiro.receitaTotal = totals.totalIncome();
    response.financeiro.despesaTotal = totals.totalExpenses();
    response.financeiro.saldo = NumericUtil.subtract(totals.totalIncome(), totals.totalExpenses());

    response.agendamentos = new RelatoriosReportsDtos.GerencialAgendamentosSummary();
    response.agendamentos.totalAgendamentos = totalAgend;
    response.agendamentos.agendamentosConcluidos = concluidos;
    response.agendamentos.agendamentosCancelados = cancelados;
    response.agendamentos.ticketMedio =
        concluidos > 0 ? receitaAgend.divide(BigDecimal.valueOf(concluidos), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

    response.topServicos = new ArrayList<>();
    for (Object[] row : topServRows) {
      RelatoriosReportsDtos.VendasServicoItem item = new RelatoriosReportsDtos.VendasServicoItem();
      item.servicoId = str(row[0]);
      item.servicoNome = str(row[1]);
      item.totalAgendamentos = row[2] != null ? ((Number) row[2]).intValue() : 0;
      item.receitaTotal = bd(row[3]);
      item.ticketMedio =
          item.totalAgendamentos > 0
              ? item.receitaTotal.divide(BigDecimal.valueOf(item.totalAgendamentos), 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      response.topServicos.add(item);
    }

    response.topProfissionais = new ArrayList<>();
    for (Object[] row : topProfRows) {
      RelatoriosReportsDtos.VendasProfissionalItem item = new RelatoriosReportsDtos.VendasProfissionalItem();
      item.profissionalId = str(row[0]);
      item.profissionalNome = str(row[1]);
      item.totalAgendamentos = row[2] != null ? ((Number) row[2]).intValue() : 0;
      item.receitaTotal = bd(row[3]);
      item.ticketMedio =
          item.totalAgendamentos > 0
              ? item.receitaTotal.divide(BigDecimal.valueOf(item.totalAgendamentos), 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      response.topProfissionais.add(item);
    }

    response.serieDiaria = new ArrayList<>();
    for (Object[] row : serieRows) {
      RelatoriosReportsDtos.DailySeriesItem ds = new RelatoriosReportsDtos.DailySeriesItem();
      ds.date = str(row[0]);
      ds.receita = bd(row[1]);
      ds.despesa = bd(row[2]);
      response.serieDiaria.add(ds);
    }

    response.occupancyRate = occupancyRate;

    return response;
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static String str(Object v) {
    return v != null ? v.toString() : null;
  }

  private static BigDecimal bd(Object v) {
    if (v == null) return BigDecimal.ZERO;
    if (v instanceof BigDecimal bd) return bd;
    if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
    try {
      return new BigDecimal(v.toString());
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private UUID parseProfessionalId(String professionalId, String professionalUserId) {
    String raw = !isBlank(professionalId) ? professionalId : professionalUserId;
    if (isBlank(raw)) {
      throw new IllegalArgumentException("professionalId obrigatorio");
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("professionalId invalido");
    }
  }

  private BigDecimal resolveSingleCommissionRate(List<CommissionEntry> entries) {
    LinkedHashSet<BigDecimal> percentuais = new LinkedHashSet<>();
    for (CommissionEntry entry : entries) {
      if (entry == null || "REVERSED".equals(entry.getEntryStatus())) continue;
      if (entry.getPercentValue() == null || entry.getPercentValue().compareTo(BigDecimal.ZERO) <= 0) continue;
      percentuais.add(entry.getPercentValue());
      if (percentuais.size() > 1) return null;
    }
    return percentuais.isEmpty() ? null : percentuais.iterator().next();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
