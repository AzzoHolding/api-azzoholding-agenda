package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.AberturaCaixaRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.FechamentoCaixaRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.FechamentoCaixaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FechamentoCaixa;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusFechamentoCaixa;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FechamentoCaixaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Espelha {@code modules/finance/application/ServicoFechamentoCaixa.java}.
 *
 * <p>O {@code NotFoundException} do JAX-RS vira {@link ApiClientErrorException} com status 404,
 * tratada pelo {@code GlobalExceptionHandler} — mesmo contrato HTTP.
 */
@Service
public class ServicoFechamentoCaixa {

  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");
  private static final TypeReference<LinkedHashMap<String, BigDecimal>> TOTALS_TYPE =
      new TypeReference<>() {};

  @PersistenceContext private EntityManager entityManager;

  private final FechamentoCaixaRepository fechamentoCaixaRepository;
  private final TransacaoRepository transacaoRepository;
  private final TransacaoQueryRepository transacaoQueryRepository;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public ServicoFechamentoCaixa(
      FechamentoCaixaRepository fechamentoCaixaRepository,
      TransacaoRepository transacaoRepository,
      TransacaoQueryRepository transacaoQueryRepository,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.fechamentoCaixaRepository = fechamentoCaixaRepository;
    this.transacaoRepository = transacaoRepository;
    this.transacaoQueryRepository = transacaoQueryRepository;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<FechamentoCaixaResponse> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fechamentoCaixaRepository
        .findByTenantIdOrderByBusinessDateDescCreatedAtDesc(tenantId)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public FechamentoCaixaResponse buscar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return toResponse(getOrFail(tenantId, id));
  }

  @Transactional
  public FechamentoCaixaResponse abrir(AberturaCaixaRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate businessDate = parseBusinessDateOrToday(request != null ? request.businessDate : null);
    if (businessDate.isAfter(LocalDate.now(ZONA_BR))) {
      throw new IllegalArgumentException("Nao e possivel abrir caixa para uma data futura.");
    }
    if (fechamentoCaixaRepository.findByTenantIdAndBusinessDate(tenantId, businessDate).isPresent()) {
      throw new IllegalStateException("Ja existe fechamento de caixa para a data informada");
    }

    FechamentoCaixa fechamento = new FechamentoCaixa();
    fechamento.setTenantId(tenantId);
    fechamento.setBusinessDate(businessDate);
    fechamento.setStatus(StatusFechamentoCaixa.OPEN);
    fechamento.setOpenedAt(Instant.now());
    fechamento.setOpenedBy(authenticatedUser.idOuNulo());
    fechamento.setOpeningNotes(normalizeNullable(request != null ? request.notes : null));
    fechamento.setExpectedTotalsJson("{}");
    fechamento.setCountedTotalsJson("{}");
    fechamento.setDifferenceTotalsJson("{}");

    fechamentoCaixaRepository.save(fechamento);
    registrarAuditoria(
        tenantId, "FINANCE_CASH_CLOSING_OPEN", null, buildAuditPayload(fechamento), fechamento.getId());
    return toResponse(fechamento);
  }

  @Transactional
  public FechamentoCaixaResponse fechar(UUID id, FechamentoCaixaRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FechamentoCaixa fechamento = getOrFail(tenantId, id);
    if (fechamento.getStatus() != StatusFechamentoCaixa.OPEN) {
      throw new IllegalStateException("Fechamento de caixa ja encerrado");
    }
    if (request == null || request.countedTotals == null) {
      throw new IllegalArgumentException("countedTotals e obrigatorio");
    }

    Map<MetodoPagamento, BigDecimal> expected =
        calcularEsperadoAoVivo(tenantId, fechamento.getBusinessDate());
    Map<MetodoPagamento, BigDecimal> counted = normalizeCountedTotals(request.countedTotals);
    Map<MetodoPagamento, BigDecimal> difference = calculateDifference(expected, counted);

    Map<String, Object> before = buildAuditPayload(fechamento);

    fechamento.setStatus(StatusFechamentoCaixa.CLOSED);
    fechamento.setClosedAt(Instant.now());
    fechamento.setClosedBy(authenticatedUser.idOuNulo());
    fechamento.setClosingNotes(normalizeNullable(request.notes));
    fechamento.setExpectedTotalsJson(writeTotals(expected));
    fechamento.setCountedTotalsJson(writeTotals(counted));
    fechamento.setDifferenceTotalsJson(writeTotals(difference));

    registrarAuditoria(
        tenantId, "FINANCE_CASH_CLOSING_CLOSE", before, buildAuditPayload(fechamento), fechamento.getId());
    return toResponse(fechamento);
  }

  /**
   * Remove um fechamento de caixa criado indevidamente para uma data futura (ex.: erro de
   * fuso/relogio do dispositivo ao abrir). So permitido quando a data e futura e nao ha nenhuma
   * transacao lancada nesse dia — evita apagar um caixa com movimento real.
   */
  @Transactional
  public void remover(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FechamentoCaixa fechamento = getOrFail(tenantId, id);

    if (!fechamento.getBusinessDate().isAfter(LocalDate.now(ZONA_BR))) {
      throw new IllegalArgumentException("So e possivel remover caixas de datas futuras.");
    }

    Instant from = fechamento.getBusinessDate().atStartOfDay(ZONA_BR).toInstant();
    Instant to =
        fechamento.getBusinessDate().plusDays(1).atStartOfDay(ZONA_BR).minusNanos(1).toInstant();
    if (transacaoRepository.existsInPeriod(tenantId, from, to)) {
      throw new IllegalArgumentException("Este caixa ja tem movimento lancado e nao pode ser removido.");
    }

    Map<String, Object> before = buildAuditPayload(fechamento);
    fechamentoCaixaRepository.delete(fechamento);
    registrarAuditoria(tenantId, "FINANCE_CASH_CLOSING_DELETE", before, null, id);
  }

  // ─── INTERNOS ────────────────────────────────────────────────────────────

  private FechamentoCaixa getOrFail(UUID tenantId, UUID id) {
    return fechamentoCaixaRepository
        .findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new ApiClientErrorException("Fechamento de caixa nao encontrado", 404));
  }

  private FechamentoCaixaResponse toResponse(FechamentoCaixa fechamento) {
    FechamentoCaixaResponse response = new FechamentoCaixaResponse();
    response.id = fechamento.getId() != null ? fechamento.getId().toString() : null;
    response.tenantId = fechamento.getTenantId() != null ? fechamento.getTenantId().toString() : null;
    response.businessDate =
        fechamento.getBusinessDate() != null ? fechamento.getBusinessDate().toString() : null;
    response.status = fechamento.getStatus() != null ? fechamento.getStatus().name() : null;
    response.openedAt = fechamento.getOpenedAt() != null ? fechamento.getOpenedAt().toString() : null;
    response.openedBy = fechamento.getOpenedBy() != null ? fechamento.getOpenedBy().toString() : null;
    response.openingNotes = fechamento.getOpeningNotes();
    response.closedAt = fechamento.getClosedAt() != null ? fechamento.getClosedAt().toString() : null;
    response.closedBy = fechamento.getClosedBy() != null ? fechamento.getClosedBy().toString() : null;
    response.closingNotes = fechamento.getClosingNotes();
    // Enquanto o caixa esta OPEN, expectedTotalsJson fica vazio ("{}") — o valor esperado so e
    // congelado em fechar(). Recalcula ao vivo aqui para o "esperado" refletir o movimento do dia
    // em tempo real.
    response.expectedTotals =
        fechamento.getStatus() == StatusFechamentoCaixa.OPEN
            ? writeAmountMapAsStrings(
                calcularEsperadoAoVivo(fechamento.getTenantId(), fechamento.getBusinessDate()))
            : readTotals(fechamento.getExpectedTotalsJson());
    response.countedTotals = readTotals(fechamento.getCountedTotalsJson());
    response.differenceTotals = readTotals(fechamento.getDifferenceTotalsJson());
    response.totalExpected = sumTotals(response.expectedTotals);
    response.totalCounted = sumTotals(response.countedTotals);
    response.totalDifference = sumTotals(response.differenceTotals);
    if (fechamento.getTenantId() != null && fechamento.getBusinessDate() != null) {
      response.commissionSummary =
          calcularCommissionSummary(fechamento.getTenantId(), fechamento.getBusinessDate());
    }
    return response;
  }

  private List<FechamentoCaixaResponse.CommissionSummaryItem> calcularCommissionSummary(
      UUID tenantId, LocalDate businessDate) {
    Instant fromStart = businessDate.atStartOfDay(ZONA_BR).toInstant();
    Instant toExclusive = businessDate.plusDays(1).atStartOfDay(ZONA_BR).toInstant();

    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  e.professional_id,
                  p.name,
                  COALESCE(SUM(CASE WHEN e.entry_status <> 'REVERSED' THEN e.base_amount_cents ELSE 0 END), 0) AS total_revenue,
                  COALESCE(SUM(CASE WHEN e.entry_status <> 'REVERSED' THEN e.total_amount_cents ELSE 0 END), 0) AS commission_amount,
                  COALESCE(AVG(CASE WHEN e.entry_status <> 'REVERSED' AND e.percent_value > 0 THEN e.percent_value END), 0.0) AS commission_rate
                FROM commission_entries e
                JOIN professionals p ON p.id = e.professional_id
                WHERE e.tenant_id = :tenantId
                  AND e.created_at >= :fromStart
                  AND e.created_at < :toExclusive
                  AND e.entry_status <> 'REVERSED'
                GROUP BY e.professional_id, p.name
                ORDER BY p.name ASC
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("fromStart", fromStart)
            .setParameter("toExclusive", toExclusive)
            .getResultList();

    List<FechamentoCaixaResponse.CommissionSummaryItem> summary = new ArrayList<>();
    for (Object[] row : rows) {
      FechamentoCaixaResponse.CommissionSummaryItem item =
          new FechamentoCaixaResponse.CommissionSummaryItem();
      item.professionalId = row[0] != null ? row[0].toString() : null;
      item.professionalName = row[1] != null ? row[1].toString() : null;
      item.totalRevenue = NumericUtil.fromCents(asLong(row[2]));
      item.commissionAmount = NumericUtil.fromCents(asLong(row[3]));
      item.commissionRate =
          row[4] != null
              ? new BigDecimal(row[4].toString()).setScale(4, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      summary.add(item);
    }
    return summary;
  }

  private long asLong(Object value) {
    if (value == null) return 0L;
    if (value instanceof Number n) return n.longValue();
    return Long.parseLong(value.toString());
  }

  private LocalDate parseBusinessDateOrToday(String rawValue) {
    return rawValue == null || rawValue.isBlank()
        ? LocalDate.now(ZONA_BR)
        : DataUtil.parseDataISO(rawValue);
  }

  private Map<MetodoPagamento, BigDecimal> normalizeCountedTotals(Map<String, BigDecimal> rawTotals) {
    Map<MetodoPagamento, BigDecimal> normalized = new LinkedHashMap<>();
    for (MetodoPagamento method : MetodoPagamento.values()) {
      normalized.put(method, BigDecimal.ZERO);
    }
    for (Map.Entry<String, BigDecimal> entry : rawTotals.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        throw new IllegalArgumentException("Metodo de pagamento invalido");
      }
      MetodoPagamento method;
      try {
        method = MetodoPagamento.valueOf(entry.getKey().trim().toUpperCase(Locale.ROOT));
      } catch (Exception e) {
        throw new IllegalArgumentException("Metodo de pagamento invalido: " + entry.getKey());
      }
      normalized.put(method, entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO);
    }
    return normalized;
  }

  private Map<MetodoPagamento, BigDecimal> calculateDifference(
      Map<MetodoPagamento, BigDecimal> expected, Map<MetodoPagamento, BigDecimal> counted) {
    Map<MetodoPagamento, BigDecimal> difference = new LinkedHashMap<>();
    for (MetodoPagamento method : MetodoPagamento.values()) {
      difference.put(
          method,
          counted
              .getOrDefault(method, BigDecimal.ZERO)
              .subtract(expected.getOrDefault(method, BigDecimal.ZERO)));
    }
    return difference;
  }

  private BigDecimal sumTotals(Map<String, BigDecimal> totals) {
    return totals.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String writeTotals(Map<MetodoPagamento, BigDecimal> totals) {
    LinkedHashMap<String, BigDecimal> payload = new LinkedHashMap<>();
    for (MetodoPagamento method : MetodoPagamento.values()) {
      payload.put(method.name(), totals.getOrDefault(method, BigDecimal.ZERO));
    }
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao serializar totais do fechamento de caixa", e);
    }
  }

  private Map<String, BigDecimal> readTotals(String json) {
    LinkedHashMap<String, BigDecimal> raw = new LinkedHashMap<>();
    try {
      if (json != null && !json.isBlank()) {
        raw.putAll(objectMapper.readValue(json, TOTALS_TYPE));
      }
    } catch (Exception ignored) {
      // Dado legado invalido ainda devolve o shape canonico.
    }

    LinkedHashMap<String, BigDecimal> normalized = new LinkedHashMap<>();
    for (MetodoPagamento method : MetodoPagamento.values()) {
      normalized.put(method.name(), raw.getOrDefault(method.name(), BigDecimal.ZERO));
    }
    return normalized;
  }

  private Map<MetodoPagamento, BigDecimal> toAmountMap(Map<MetodoPagamento, Long> centsMap) {
    Map<MetodoPagamento, BigDecimal> amounts = new LinkedHashMap<>();
    for (MetodoPagamento method : MetodoPagamento.values()) {
      amounts.put(method, NumericUtil.fromCents(centsMap.getOrDefault(method, 0L)));
    }
    return amounts;
  }

  /**
   * Soma as transacoes do dia por metodo de pagamento — usado tanto para congelar o "esperado" ao
   * fechar quanto para exibi-lo ao vivo enquanto o caixa esta OPEN.
   */
  private Map<MetodoPagamento, BigDecimal> calcularEsperadoAoVivo(UUID tenantId, LocalDate businessDate) {
    Instant from = businessDate.atStartOfDay(ZONA_BR).toInstant();
    Instant to = businessDate.plusDays(1).atStartOfDay(ZONA_BR).minusNanos(1).toInstant();
    return toAmountMap(transacaoQueryRepository.summarizeNetByPaymentMethod(tenantId, from, to));
  }

  private Map<String, BigDecimal> writeAmountMapAsStrings(Map<MetodoPagamento, BigDecimal> totals) {
    Map<String, BigDecimal> payload = new LinkedHashMap<>();
    for (MetodoPagamento method : MetodoPagamento.values()) {
      payload.put(method.name(), totals.getOrDefault(method, BigDecimal.ZERO));
    }
    return payload;
  }

  private Map<String, Object> buildAuditPayload(FechamentoCaixa fechamento) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(
        "businessDate", fechamento.getBusinessDate() != null ? fechamento.getBusinessDate().toString() : null);
    payload.put("status", fechamento.getStatus() != null ? fechamento.getStatus().name() : null);
    payload.put("openedAt", fechamento.getOpenedAt() != null ? fechamento.getOpenedAt().toString() : null);
    payload.put("openedBy", fechamento.getOpenedBy() != null ? fechamento.getOpenedBy().toString() : null);
    payload.put("openingNotes", fechamento.getOpeningNotes());
    payload.put("closedAt", fechamento.getClosedAt() != null ? fechamento.getClosedAt().toString() : null);
    payload.put("closedBy", fechamento.getClosedBy() != null ? fechamento.getClosedBy().toString() : null);
    payload.put("closingNotes", fechamento.getClosingNotes());
    payload.put("expectedTotals", readTotals(fechamento.getExpectedTotalsJson()));
    payload.put("countedTotals", readTotals(fechamento.getCountedTotalsJson()));
    payload.put("differenceTotals", readTotals(fechamento.getDifferenceTotalsJson()));
    return payload;
  }

  private void registrarAuditoria(
      UUID tenantId, String action, Object before, Object after, UUID entityId) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.FINANCE;
      command.action = action;
      command.entityType = "CASH_CLOSING";
      command.entityId = entityId != null ? entityId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve quebrar o fluxo principal.
    }
  }

  private String normalizeNullable(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
