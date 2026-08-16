package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RelatoriosReportsDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Espelha {@code modules/reports/application/ServicoCatalogoRelatorios.java}.
 *
 * <p>Catalogo de relatorios generico (F18): cada {@code reportKey} mapeia para uma query nativa
 * fixa (colunas/SQL definidos aqui, nao vindos de config externa). Porte verbatim, incluindo os 14
 * relatorios do catalogo e os comentarios de defeito/ausencia de coluna do original.
 */
@Service
public class ServicoCatalogoRelatorios {

  private static final int MAX_PAGE_SIZE = 500;
  private static final int MAX_EXPORT_ROWS = 10_000;

  @PersistenceContext private EntityManager entityManager;

  private final ContextoTenant contextoTenant;

  public ServicoCatalogoRelatorios(ContextoTenant contextoTenant) {
    this.contextoTenant = contextoTenant;
  }

  @Transactional(readOnly = true)
  public RelatoriosReportsDtos.CatalogReportResponse gerar(
      String reportKey,
      String dataInicio,
      String dataFim,
      String professionalId,
      Integer inactiveDays,
      Integer pageIndex,
      Integer pageSize) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ReportDefinition definition = definition(reportKey);
    LocalDate inicio = parseDateOrDefault(dataInicio, LocalDate.now().withDayOfMonth(1));
    LocalDate fim = parseDateOrDefault(dataFim, LocalDate.now());
    validatePeriod(inicio, fim);

    int safePageIndex = pageIndex == null || pageIndex < 0 ? 0 : pageIndex;
    int safePageSize = pageSize == null ? 50 : Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    List<Map<String, Object>> rows =
        execute(definition, tenantId, inicio, fim, professionalId, inactiveDays, safePageIndex, safePageSize);

    RelatoriosReportsDtos.CatalogReportResponse response = new RelatoriosReportsDtos.CatalogReportResponse();
    response.reportKey = definition.key();
    response.title = definition.title();
    response.dependencies = definition.dependencies();
    response.columns = definition.columns();
    response.rows = rows;
    response.pageIndex = safePageIndex;
    response.pageSize = safePageSize;
    response.hasMore = rows.size() == safePageSize;
    return response;
  }

  @Transactional(readOnly = true)
  public ExportedReport exportar(
      String reportKey, String dataInicio, String dataFim, String professionalId, String format, Integer inactiveDays) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ReportDefinition definition = definition(reportKey);
    LocalDate inicio = parseDateOrDefault(dataInicio, LocalDate.now().withDayOfMonth(1));
    LocalDate fim = parseDateOrDefault(dataFim, LocalDate.now());
    validatePeriod(inicio, fim);

    List<Map<String, Object>> rows =
        execute(definition, tenantId, inicio, fim, professionalId, inactiveDays, 0, MAX_EXPORT_ROWS);
    String normalizedFormat = format == null || format.isBlank() ? "csv" : format.trim().toLowerCase();
    if ("xlsx".equals(normalizedFormat)) {
      return new ExportedReport(
          toXlsx(definition, rows),
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          definition.key() + "-" + inicio + "-" + fim + ".xlsx",
          rows.size(),
          "XLSX");
    }
    if (!"csv".equals(normalizedFormat)) {
      throw new IllegalArgumentException("Formato invalido. Use csv ou xlsx.");
    }
    return new ExportedReport(
        toCsv(definition, rows).getBytes(StandardCharsets.UTF_8),
        "text/csv; charset=UTF-8",
        definition.key() + "-" + inicio + "-" + fim + ".csv",
        rows.size(),
        "CSV");
  }

  private List<Map<String, Object>> execute(
      ReportDefinition definition,
      UUID tenantId,
      LocalDate inicio,
      LocalDate fim,
      String professionalId,
      Integer inactiveDays,
      int pageIndex,
      int pageSize) {
    var query =
        entityManager
            .createNativeQuery(definition.sql())
            .setParameter("tenantId", tenantId)
            .setParameter("dataInicio", inicio)
            .setParameter("dataFim", fim)
            .setParameter("limit", pageSize)
            .setParameter("offset", pageIndex * pageSize);

    if (definition.sql().contains(":inactiveDays")) {
      query.setParameter("inactiveDays", inactiveDays == null || inactiveDays < 1 ? 90 : inactiveDays);
    }

    if (definition.usesProfessionalFilter()) {
      query.setParameter(
          "professionalId", professionalId == null || professionalId.isBlank() ? null : UUID.fromString(professionalId));
    }

    @SuppressWarnings("unchecked")
    List<Object[]> rawRows = query.getResultList();
    List<Map<String, Object>> rows = new ArrayList<>(rawRows.size());
    for (Object[] rawRow : rawRows) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (int i = 0; i < definition.columns().size(); i++) {
        row.put(definition.columns().get(i), normalize(rawRow.length > i ? rawRow[i] : null));
      }
      rows.add(row);
    }
    return rows;
  }

  private static Object normalize(Object value) {
    if (value instanceof BigDecimal bd) return bd;
    return value != null ? value.toString() : null;
  }

  private static LocalDate parseDateOrDefault(String value, LocalDate fallback) {
    LocalDate parsed = DataUtil.parseDataISO(value);
    return parsed != null ? parsed : fallback;
  }

  private static void validatePeriod(LocalDate inicio, LocalDate fim) {
    if (fim.isBefore(inicio)) throw new IllegalArgumentException("Periodo invalido");
    if (inicio.plusDays(370).isBefore(fim)) throw new IllegalArgumentException("Periodo maximo de 370 dias");
  }

  ReportDefinition definition(String rawKey) {
    String key = rawKey == null ? "" : rawKey.trim().toLowerCase();
    return switch (key) {
      case "faturamento-servico" -> new ReportDefinition(
          key,
          "Faturamento por servico",
          List.of("agenda"),
          List.of("servico_id", "servico", "quantidade", "receita", "ticket_medio"),
          true,
          """
          SELECT s.id::text, s.name, COUNT(DISTINCT a.id)::int,
                 COALESCE(SUM(ai.total_price), 0),
                 CASE WHEN COUNT(DISTINCT a.id) > 0 THEN ROUND(COALESCE(SUM(ai.total_price), 0) / COUNT(DISTINCT a.id), 2) ELSE 0 END
          FROM appointments a
          JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
          JOIN services s ON s.id = ai.service_id AND s.tenant_id = a.tenant_id
          WHERE a.tenant_id = :tenantId
            AND a.date BETWEEN :dataInicio AND :dataFim
            AND a.status = 'Concluido'
            AND (CAST(:professionalId AS uuid) IS NULL OR a.professional_id = CAST(:professionalId AS uuid))
          GROUP BY s.id, s.name
          ORDER BY 4 DESC
          LIMIT :limit OFFSET :offset
          """);
      case "faturamento-profissional" -> new ReportDefinition(
          key,
          "Faturamento por profissional",
          List.of("agenda", "comissoes"),
          List.of("profissional_id", "profissional", "quantidade", "receita", "comissao_gerada"),
          true,
          """
          SELECT p.id::text, p.name, COUNT(DISTINCT a.id)::int,
                 COALESCE(SUM(ai.total_price), 0),
                 COALESCE(MAX(ce.comissao), 0)
          FROM professionals p
          LEFT JOIN appointments a ON a.professional_id = p.id AND a.tenant_id = p.tenant_id
            AND a.date BETWEEN :dataInicio AND :dataFim AND a.status = 'Concluido'
          LEFT JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
          LEFT JOIN (
            SELECT professional_id, SUM(total_amount_cents) / 100.0 AS comissao
            FROM commission_entries
            WHERE tenant_id = :tenantId
              AND entry_status <> 'REVERSED'
              AND created_at::date BETWEEN :dataInicio AND :dataFim
            GROUP BY professional_id
          ) ce ON ce.professional_id = p.id
          WHERE p.tenant_id = :tenantId
            AND (CAST(:professionalId AS uuid) IS NULL OR p.id = CAST(:professionalId AS uuid))
          GROUP BY p.id, p.name
          ORDER BY 4 DESC
          LIMIT :limit OFFSET :offset
          """);
      case "faturamento-meio-pagamento" -> definitionSimple(
          key,
          "Faturamento por meio de pagamento",
          List.of("comanda"),
          List.of("meio_pagamento", "quantidade", "valor_total"),
          """
          SELECT cp.meio, COUNT(*)::int, COALESCE(SUM(cp.valor), 0)
          FROM comanda_pagamentos cp
          JOIN comandas c ON c.id = cp.comanda_id AND c.tenant_id = cp.tenant_id
          WHERE cp.tenant_id = :tenantId
            AND cp.status = 'CONFIRMADO'
            AND COALESCE(cp.paid_at, cp.created_at)::date BETWEEN :dataInicio AND :dataFim
          GROUP BY cp.meio
          ORDER BY 3 DESC
          LIMIT :limit OFFSET :offset
          """);
      case "novos-clientes" -> definitionSimple(
          key,
          "Novos clientes por periodo",
          List.of("clientes"),
          List.of("data", "origem", "quantidade"),
          """
          SELECT c.created_at::date::text, 'PAINEL', COUNT(*)::int
          FROM clients c
          WHERE c.tenant_id = :tenantId
            AND c.created_at::date BETWEEN :dataInicio AND :dataFim
          GROUP BY 1, 2
          ORDER BY 1 DESC
          LIMIT :limit OFFSET :offset
          """);
      // Cliente/last_visit/total_spent nao sao colunas persistidas (ver ClienteRepository.ClienteStats,
      // calculado em memoria) -- aqui calculamos a mesma coisa via subquery de agendamentos concluidos.
      case "clientes-inativos" -> definitionSimple(
          key,
          "Clientes inativos",
          List.of("clientes", "agenda"),
          List.of("cliente_id", "cliente", "telefone", "ultima_visita", "valor_historico"),
          """
          SELECT c.id::text, c.name, c.phone, MAX(a.date)::text, COALESCE(SUM(ai.total_price), 0)
          FROM clients c
          LEFT JOIN appointments a ON a.client_id = c.id AND a.tenant_id = c.tenant_id AND a.status = 'Concluido'
          LEFT JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
          WHERE c.tenant_id = :tenantId
          GROUP BY c.id, c.name, c.phone
          HAVING MAX(a.date) IS NULL OR MAX(a.date) <= (:dataFim - (:inactiveDays || ' days')::interval)::date
          ORDER BY MAX(a.date) NULLS FIRST, c.name
          LIMIT :limit OFFSET :offset
          """);
      case "taxa-retorno" -> definitionSimple(
          key,
          "Taxa de retorno",
          List.of("clientes", "agenda"),
          List.of("clientes_primeira_visita", "retorno_60_dias", "retorno_90_dias", "percentual_60", "percentual_90"),
          """
          WITH first_visit AS (
            SELECT tenant_id, client_id, MIN(date) AS first_date
            FROM appointments
            WHERE tenant_id = :tenantId AND status = 'Concluido'
            GROUP BY tenant_id, client_id
          ), cohort AS (
            SELECT * FROM first_visit WHERE first_date BETWEEN :dataInicio AND :dataFim
          )
          SELECT COUNT(*)::int,
                 COUNT(*) FILTER (WHERE EXISTS (
                   SELECT 1 FROM appointments a WHERE a.tenant_id = cohort.tenant_id AND a.client_id = cohort.client_id
                     AND a.status = 'Concluido' AND a.date > cohort.first_date AND a.date <= cohort.first_date + INTERVAL '60 days'
                 ))::int,
                 COUNT(*) FILTER (WHERE EXISTS (
                   SELECT 1 FROM appointments a WHERE a.tenant_id = cohort.tenant_id AND a.client_id = cohort.client_id
                     AND a.status = 'Concluido' AND a.date > cohort.first_date AND a.date <= cohort.first_date + INTERVAL '90 days'
                 ))::int,
                 CASE WHEN COUNT(*) > 0 THEN ROUND(COUNT(*) FILTER (WHERE EXISTS (
                   SELECT 1 FROM appointments a WHERE a.tenant_id = cohort.tenant_id AND a.client_id = cohort.client_id
                     AND a.status = 'Concluido' AND a.date > cohort.first_date AND a.date <= cohort.first_date + INTERVAL '60 days'
                 )) * 100.0 / COUNT(*), 2) ELSE 0 END,
                 CASE WHEN COUNT(*) > 0 THEN ROUND(COUNT(*) FILTER (WHERE EXISTS (
                   SELECT 1 FROM appointments a WHERE a.tenant_id = cohort.tenant_id AND a.client_id = cohort.client_id
                     AND a.status = 'Concluido' AND a.date > cohort.first_date AND a.date <= cohort.first_date + INTERVAL '90 days'
                 )) * 100.0 / COUNT(*), 2) ELSE 0 END
          FROM cohort
          LIMIT :limit OFFSET :offset
          """);
      // sinal_retido: sinal (F02) pago cuja reserva foi cancelada/nao compareceu -- nao existe status
      // "retido" explicito no schema (AppointmentDeposit so tem PENDING/PAID/EXPIRED/CANCELLED), entao
      // consideramos retido todo deposito PAID vinculado a um agendamento Cancelado/Nao compareceu.
      case "no-show-cancelamentos" -> definitionSimple(
          key,
          "No-show e cancelamentos",
          List.of("agenda", "sinal"),
          List.of("profissional", "servico", "dia_semana", "status", "quantidade", "valor_perdido", "sinal_retido"),
          """
          SELECT p.name, s.name, EXTRACT(DOW FROM a.date)::int, a.status, COUNT(DISTINCT a.id)::int,
                 COALESCE(SUM(ai.total_price), 0), COALESCE(SUM(ad.amount_cents) FILTER (WHERE ad.status = 'PAID'), 0) / 100.0
          FROM appointments a
          JOIN professionals p ON p.id = a.professional_id AND p.tenant_id = a.tenant_id
          LEFT JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
          LEFT JOIN services s ON s.id = ai.service_id AND s.tenant_id = a.tenant_id
          LEFT JOIN appointment_deposits ad ON ad.appointment_id = a.id AND ad.tenant_id = a.tenant_id
          WHERE a.tenant_id = :tenantId
            AND a.date BETWEEN :dataInicio AND :dataFim
            AND a.status IN ('Cancelado', 'Nao compareceu')
          GROUP BY p.name, s.name, 3, a.status
          ORDER BY 5 DESC
          LIMIT :limit OFFSET :offset
          """);
      case "ocupacao-profissional" -> definitionSimple(
          key,
          "Ocupacao por profissional",
          List.of("agenda", "profissionais"),
          List.of("profissional_id", "profissional", "minutos_ocupados", "minutos_disponiveis", "ocupacao_percent"),
          """
          WITH dias AS (
            SELECT d::date AS dia, EXTRACT(ISODOW FROM d)::int AS dow_iso
            FROM generate_series(CAST(:dataInicio AS date), CAST(:dataFim AS date), interval '1 day') d
          ), disponivel AS (
            SELECT wh.professional_id,
                   SUM(EXTRACT(EPOCH FROM (wh.end_time - wh.start_time)) / 60)::int AS minutos
            FROM professional_working_hours wh
            JOIN dias ON wh.day_of_week = dias.dow_iso OR (dias.dow_iso = 7 AND wh.day_of_week = 0)
            WHERE wh.tenant_id = :tenantId AND wh.is_working = true AND wh.start_time < wh.end_time
            GROUP BY wh.professional_id
          ), ocupado AS (
            SELECT a.professional_id,
                   SUM(EXTRACT(EPOCH FROM (a.end_time::time - a.start_time::time)) / 60)::int AS minutos
            FROM appointments a
            WHERE a.tenant_id = :tenantId
              AND a.date BETWEEN :dataInicio AND :dataFim
              AND a.status <> 'Cancelado'
            GROUP BY a.professional_id
          )
          SELECT p.id::text, p.name, COALESCE(o.minutos, 0), COALESCE(d.minutos, 0),
                 CASE WHEN COALESCE(d.minutos, 0) > 0 THEN ROUND(COALESCE(o.minutos, 0) * 100.0 / d.minutos, 2) ELSE NULL END
          FROM professionals p
          LEFT JOIN disponivel d ON d.professional_id = p.id
          LEFT JOIN ocupado o ON o.professional_id = p.id
          WHERE p.tenant_id = :tenantId
          ORDER BY 5 DESC NULLS LAST
          LIMIT :limit OFFSET :offset
          """);
      case "ranking-servicos" -> definitionSimple(
          key,
          "Ranking de servicos",
          List.of("agenda"),
          List.of("servico_id", "servico", "quantidade", "receita", "crescimento_percent"),
          """
          SELECT s.id::text, s.name, COUNT(DISTINCT a.id)::int, COALESCE(SUM(ai.total_price), 0), NULL
          FROM services s
          LEFT JOIN appointment_items ai ON ai.service_id = s.id AND ai.tenant_id = s.tenant_id
          LEFT JOIN appointments a ON a.id = ai.appointment_id AND a.tenant_id = ai.tenant_id
            AND a.status = 'Concluido' AND a.date BETWEEN :dataInicio AND :dataFim
          WHERE s.tenant_id = :tenantId
          GROUP BY s.id, s.name
          ORDER BY 3 DESC, 4 DESC
          LIMIT :limit OFFSET :offset
          """);
      case "curva-abc-clientes" -> definitionSimple(
          key,
          "Curva ABC de clientes",
          List.of("clientes", "agenda"),
          List.of("cliente_id", "cliente", "valor_gasto", "percentual_acumulado", "classe"),
          """
          WITH receita AS (
            SELECT c.id, c.name, COALESCE(SUM(ai.total_price), 0) AS valor
            FROM clients c
            JOIN appointments a ON a.client_id = c.id AND a.tenant_id = c.tenant_id
              AND a.status = 'Concluido' AND a.date BETWEEN :dataInicio AND :dataFim
            JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
            WHERE c.tenant_id = :tenantId
            GROUP BY c.id, c.name
          ), ranked AS (
            SELECT id, name, valor,
                   SUM(valor) OVER (ORDER BY valor DESC) / NULLIF(SUM(valor) OVER (), 0) * 100 AS pct
            FROM receita
          )
          SELECT id::text, name, valor, ROUND(pct, 2),
                 CASE WHEN pct <= 80 THEN 'A' WHEN pct <= 95 THEN 'B' ELSE 'C' END
          FROM ranked
          ORDER BY valor DESC
          LIMIT :limit OFFSET :offset
          """);
      case "descontos" -> definitionSimple(
          key,
          "Descontos concedidos",
          List.of("comanda"),
          List.of("operador_id", "motivo", "quantidade", "valor_desconto"),
          """
          SELECT c.fechada_por::text, COALESCE(NULLIF(c.desconto_motivo, ''), 'Sem motivo'), COUNT(*)::int, COALESCE(SUM(c.desconto), 0)
          FROM comandas c
          WHERE c.tenant_id = :tenantId
            AND c.status = 'FECHADA'
            AND c.closed_at::date BETWEEN :dataInicio AND :dataFim
            AND c.desconto > 0
          GROUP BY c.fechada_por, COALESCE(NULLIF(c.desconto_motivo, ''), 'Sem motivo')
          ORDER BY 4 DESC
          LIMIT :limit OFFSET :offset
          """);
      case "comissoes-periodo" -> definitionSimple(
          key,
          "Comissoes por periodo",
          List.of("comissoes"),
          List.of("profissional_id", "profissional", "status", "quantidade", "valor_comissao"),
          """
          SELECT ce.professional_id::text, p.name, ce.entry_status, COUNT(*)::int, COALESCE(SUM(ce.total_amount_cents), 0) / 100.0
          FROM commission_entries ce
          LEFT JOIN professionals p ON p.id = ce.professional_id AND p.tenant_id = ce.tenant_id
          WHERE ce.tenant_id = :tenantId
            AND ce.created_at::date BETWEEN :dataInicio AND :dataFim
          GROUP BY ce.professional_id, p.name, ce.entry_status
          ORDER BY 5 DESC
          LIMIT :limit OFFSET :offset
          """);
      case "vendas-produtos" -> definitionSimple(
          key,
          "Vendas de produtos",
          List.of("comanda", "estoque"),
          List.of("produto_id", "produto", "quantidade", "receita", "custo_estimado", "margem_estimada"),
          """
          SELECT ci.referencia_id::text, COALESCE(i.nome, ci.descricao), SUM(ci.quantidade)::int, COALESCE(SUM(ci.total), 0),
                 COALESCE(SUM(ci.quantidade * i.custo_medio_unitario), 0),
                 COALESCE(SUM(ci.total), 0) - COALESCE(SUM(ci.quantidade * i.custo_medio_unitario), 0)
          FROM comanda_itens ci
          JOIN comandas c ON c.id = ci.comanda_id AND c.tenant_id = ci.tenant_id
          LEFT JOIN itens_estoque i ON i.id = ci.referencia_id AND i.tenant_id = ci.tenant_id
          WHERE ci.tenant_id = :tenantId
            AND ci.tipo = 'PRODUTO'
            AND c.status = 'FECHADA'
            AND c.closed_at::date BETWEEN :dataInicio AND :dataFim
          GROUP BY ci.referencia_id, COALESCE(i.nome, ci.descricao)
          ORDER BY 4 DESC
          LIMIT :limit OFFSET :offset
          """);
      // client_package_purchases/client_package_balances (F04) nao tem conceito de expiracao de pacote
      // -- coluna "expirados_sem_uso" da referencia original foi removida por nao ter suporte no schema.
      case "pacotes" -> definitionSimple(
          key,
          "Pacotes",
          List.of("pacotes"),
          List.of("pacote_id", "pacote", "vendidos", "saldo_em_aberto"),
          """
          SELECT sp.id::text, sp.nome,
                 COUNT(DISTINCT cpp.id)::int,
                 COALESCE(SUM(cpb.sessoes_totais - cpb.sessoes_usadas), 0)::int
          FROM service_packages sp
          LEFT JOIN client_package_purchases cpp ON cpp.package_id = sp.id AND cpp.tenant_id = sp.tenant_id
            AND cpp.created_at::date BETWEEN :dataInicio AND :dataFim
          LEFT JOIN client_package_balances cpb ON cpb.purchase_id = cpp.id
          WHERE sp.tenant_id = :tenantId
          GROUP BY sp.id, sp.nome
          ORDER BY 3 DESC
          LIMIT :limit OFFSET :offset
          """);
      case "aniversariantes" -> definitionSimple(
          key,
          "Aniversariantes do mes",
          List.of("clientes", "whatsapp"),
          List.of("cliente_id", "cliente", "telefone", "data_nascimento", "atalho_whatsapp"),
          """
          SELECT c.id::text, c.name, c.phone, c.birth_date::text,
                 CASE WHEN c.phone IS NOT NULL THEN 'whatsapp:' || c.phone ELSE NULL END
          FROM clients c
          WHERE c.tenant_id = :tenantId
            AND c.birth_date IS NOT NULL
            AND EXTRACT(MONTH FROM c.birth_date) = EXTRACT(MONTH FROM CAST(:dataInicio AS date))
          ORDER BY EXTRACT(DAY FROM c.birth_date), c.name
          LIMIT :limit OFFSET :offset
          """);
      default -> throw new IllegalArgumentException("Relatorio nao suportado: " + rawKey);
    };
  }

  private static ReportDefinition definitionSimple(
      String key, String title, List<String> dependencies, List<String> columns, String sql) {
    return new ReportDefinition(key, title, dependencies, columns, false, sql);
  }

  private static String toCsv(ReportDefinition definition, List<Map<String, Object>> rows) {
    StringBuilder csv = new StringBuilder();
    csv.append(String.join(",", definition.columns())).append('\n');
    for (Map<String, Object> row : rows) {
      for (int i = 0; i < definition.columns().size(); i++) {
        if (i > 0) csv.append(',');
        csv.append(csvCell(row.get(definition.columns().get(i))));
      }
      csv.append('\n');
    }
    return csv.toString();
  }

  private static String csvCell(Object value) {
    if (value == null) return "";
    String raw = value.toString();
    if (raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")) {
      return "\"" + raw.replace("\"", "\"\"") + "\"";
    }
    return raw;
  }

  private static byte[] toXlsx(ReportDefinition definition, List<Map<String, Object>> rows) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("relatorio");
      var header = sheet.createRow(0);
      for (int i = 0; i < definition.columns().size(); i++) {
        header.createCell(i).setCellValue(definition.columns().get(i));
      }
      for (int r = 0; r < rows.size(); r++) {
        var row = sheet.createRow(r + 1);
        Map<String, Object> item = rows.get(r);
        for (int c = 0; c < definition.columns().size(); c++) {
          Object value = item.get(definition.columns().get(c));
          if (value instanceof Number number) {
            row.createCell(c).setCellValue(number.doubleValue());
          } else {
            row.createCell(c).setCellValue(value != null ? value.toString() : "");
          }
        }
      }
      for (int i = 0; i < definition.columns().size(); i++) {
        sheet.autoSizeColumn(i);
      }
      workbook.write(output);
      return output.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("Falha ao gerar XLSX do relatorio.", ex);
    }
  }

  public record ExportedReport(byte[] content, String mediaType, String fileName, int rowsCount, String format) {}

  record ReportDefinition(
      String key, String title, List<String> dependencies, List<String> columns, boolean usesProfessionalFilter, String sql) {}
}
