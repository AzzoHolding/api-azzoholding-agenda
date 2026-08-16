package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.CommissionDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionCycle;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionEntry;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionRule;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionRuleSet;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServiceCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionCycleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionEntryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionRuleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionRuleSetRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServiceCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransactionCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Espelha {@code modules/commission/application/CommissionService.java}.
 *
 * <p>Duas adaptacoes ao contexto Spring, ambas sem mudanca de comportamento:
 *
 * <ul>
 *   <li>O original le a categoria do servico por {@code service.categoryRef.name}. A entidade
 *       {@code Servico} portada guarda so {@code categoryId} escalar (decisao da Etapa 5), entao a
 *       resolucao do nome passa pelo {@link ServiceCategoryRepository}.
 *   <li>O original faz {@code entityManager.find(ItemEstoque.class, productId)} + checagem de
 *       tenant para obter o nome do produto; aqui isso e
 *       {@code ItemEstoqueRepository.findByIdAndTenantId} (Etapa 16).
 * </ul>
 *
 * <p><b>Os outros dois acessos a {@code itens_estoque} por SQL nativo NAO sao divida de migracao —
 * o original tambem os faz assim</b>, e foram mantidos por fidelidade:
 *
 * <ul>
 *   <li>{@code ensureCount("itens_estoque", ...)} e um helper generico parametrizado pelo nome da
 *       tabela, compartilhado com {@code services};
 *   <li>{@code resolveTargetLabel} no caso {@code PRODUCT} consulta {@code SELECT nome FROM
 *       itens_estoque WHERE id = :id} <b>sem filtro de tenant</b> — assimetria do original,
 *       preservada.
 * </ul>
 */
@Service
public class CommissionService {

  private static final DateTimeFormatter PERIOD_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");
  private static final List<String> VALID_ENTRY_STATUS = List.of("OPEN", "REVERSED", "PAID");

  @PersistenceContext private EntityManager entityManager;

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final AuditService auditService;
  private final ProfissionalRepository profissionalRepository;
  private final ServicoRepository servicoRepository;
  private final ServiceCategoryRepository serviceCategoryRepository;
  private final CommissionRuleSetRepository ruleSetRepository;
  private final CommissionRuleRepository ruleRepository;
  private final CommissionEntryRepository entryRepository;
  private final CommissionCycleRepository cycleRepository;
  private final TransacaoRepository transacaoRepository;
  private final TransactionCategoryRepository transactionCategoryRepository;
  private final ItemEstoqueRepository itemEstoqueRepository;

  public CommissionService(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      AuditService auditService,
      ProfissionalRepository profissionalRepository,
      ServicoRepository servicoRepository,
      ServiceCategoryRepository serviceCategoryRepository,
      CommissionRuleSetRepository ruleSetRepository,
      CommissionRuleRepository ruleRepository,
      CommissionEntryRepository entryRepository,
      CommissionCycleRepository cycleRepository,
      TransacaoRepository transacaoRepository,
      TransactionCategoryRepository transactionCategoryRepository,
      ItemEstoqueRepository itemEstoqueRepository) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.auditService = auditService;
    this.profissionalRepository = profissionalRepository;
    this.servicoRepository = servicoRepository;
    this.serviceCategoryRepository = serviceCategoryRepository;
    this.ruleSetRepository = ruleSetRepository;
    this.ruleRepository = ruleRepository;
    this.entryRepository = entryRepository;
    this.cycleRepository = cycleRepository;
    this.transacaoRepository = transacaoRepository;
    this.transactionCategoryRepository = transactionCategoryRepository;
    this.itemEstoqueRepository = itemEstoqueRepository;
  }

  // ─── RULE SETS ───────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public CommissionDtos.RuleSetListResponse listRuleSets(String professionalId, Boolean activeOnly) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID parsedProfessionalId = parseUuidNullable(professionalId, "professionalId invalido");
    List<CommissionRuleSet> ruleSets =
        ruleSetRepository.listByTenant(tenantId, parsedProfessionalId, Boolean.TRUE.equals(activeOnly));
    Map<UUID, String> professionalNames =
        mapProfessionalNames(
            tenantId,
            ruleSets.stream()
                .map(CommissionRuleSet::getProfessionalId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

    CommissionDtos.RuleSetListResponse response = new CommissionDtos.RuleSetListResponse();
    for (CommissionRuleSet item : ruleSets) {
      response.items.add(toRuleSetResponse(item, professionalNames));
    }
    return response;
  }

  @Transactional
  public CommissionDtos.RuleSetResponse createRuleSet(CommissionDtos.RuleSetUpsertRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID actorUserId = authenticatedUser.idOuNulo();

    CommissionRuleSet entity = new CommissionRuleSet();
    applyRuleSet(entity, request, tenantId);
    ruleSetRepository.save(entity);
    entityManager.flush();
    persistRules(entity, request.rules, tenantId);

    CommissionDtos.RuleSetResponse response =
        toRuleSetResponse(entity, mapProfessionalNames(tenantId, professionalIdsForRuleSet(entity)));
    auditSuccess(
        tenantId, actorUserId, "COMMISSION_RULE_SET_CREATE", "COMMISSION_RULE_SET", entity.getId(), null, response);
    return response;
  }

  @Transactional
  public CommissionDtos.RuleSetResponse updateRuleSet(
      UUID ruleSetId, CommissionDtos.RuleSetUpsertRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID actorUserId = authenticatedUser.idOuNulo();
    CommissionRuleSet entity = getRuleSetOrFail(tenantId, ruleSetId);

    CommissionDtos.RuleSetResponse before =
        toRuleSetResponse(entity, mapProfessionalNames(tenantId, professionalIdsForRuleSet(entity)));
    applyRuleSet(entity, request, tenantId);
    ruleRepository.deleteByRuleSet(tenantId, entity.getId());
    entityManager.flush();
    persistRules(entity, request.rules, tenantId);

    CommissionDtos.RuleSetResponse after =
        toRuleSetResponse(entity, mapProfessionalNames(tenantId, professionalIdsForRuleSet(entity)));
    auditSuccess(
        tenantId, actorUserId, "COMMISSION_RULE_SET_UPDATE", "COMMISSION_RULE_SET", entity.getId(), before, after);
    return after;
  }

  @Transactional
  public CommissionDtos.RuleSetResponse setRuleSetActive(
      UUID ruleSetId, CommissionDtos.ActivationRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID actorUserId = authenticatedUser.idOuNulo();
    CommissionRuleSet entity = getRuleSetOrFail(tenantId, ruleSetId);

    CommissionDtos.RuleSetResponse before =
        toRuleSetResponse(entity, mapProfessionalNames(tenantId, professionalIdsForRuleSet(entity)));
    entity.setActive(Boolean.TRUE.equals(request.active));
    if (entity.isActive()) {
      deactivateConflictingRuleSets(entity);
    }
    CommissionDtos.RuleSetResponse after =
        toRuleSetResponse(entity, mapProfessionalNames(tenantId, professionalIdsForRuleSet(entity)));
    auditSuccess(
        tenantId,
        actorUserId,
        "COMMISSION_RULE_SET_ACTIVE_UPDATE",
        "COMMISSION_RULE_SET",
        entity.getId(),
        before,
        after);
    return after;
  }

  // ─── RELATORIOS ──────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public CommissionDtos.ReportResponse report(String from, String to, String professionalId, String status) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate start = parseRequiredDate(from, "from obrigatorio");
    LocalDate end = parseRequiredDate(to, "to obrigatorio");
    if (end.isBefore(start)) throw new IllegalArgumentException("Periodo invalido");
    UUID professionalUuid = parseUuidNullable(professionalId, "professionalId invalido");
    String normalizedStatus = normalizeStatusNullable(status);

    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              e.professional_id,
              p.name,
              COALESCE(SUM(CASE WHEN e.entry_status <> 'REVERSED' AND e.origin_type = 'SERVICE' THEN e.total_amount_cents ELSE 0 END), 0) AS service_total,
              COALESCE(SUM(CASE WHEN e.entry_status <> 'REVERSED' AND e.origin_type = 'PRODUCT' THEN e.total_amount_cents ELSE 0 END), 0) AS product_total,
              COALESCE(SUM(CASE WHEN e.entry_status <> 'REVERSED' AND e.origin_type = 'MANUAL_ADJUSTMENT' THEN e.total_amount_cents ELSE 0 END), 0) AS adjustment_total,
              COALESCE(SUM(CASE WHEN e.entry_status <> 'REVERSED' THEN e.total_amount_cents ELSE 0 END), 0) AS grand_total,
              COALESCE(SUM(CASE WHEN e.entry_status = 'OPEN' THEN e.total_amount_cents ELSE 0 END), 0) AS open_total,
              COALESCE(SUM(CASE WHEN e.entry_status = 'PAID' THEN e.total_amount_cents ELSE 0 END), 0) AS paid_total,
              COUNT(*)
            FROM commission_entries e
            JOIN professionals p ON p.id = e.professional_id
            WHERE e.tenant_id = :tenantId
              AND e.created_at >= :fromStart
              AND e.created_at < :toExclusive
            """);
    if (professionalUuid != null) sql.append(" AND e.professional_id = :professionalId");
    if (normalizedStatus != null) sql.append(" AND e.entry_status = :status");
    sql.append(" GROUP BY e.professional_id, p.name ORDER BY p.name ASC");

    var query =
        entityManager
            .createNativeQuery(sql.toString())
            .setParameter("tenantId", tenantId)
            .setParameter("fromStart", start.atStartOfDay(ZONA_BR).toInstant())
            .setParameter("toExclusive", end.plusDays(1).atStartOfDay(ZONA_BR).toInstant());
    if (professionalUuid != null) query.setParameter("professionalId", professionalUuid);
    if (normalizedStatus != null) query.setParameter("status", normalizedStatus);

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();

    CommissionDtos.ReportResponse response = new CommissionDtos.ReportResponse();
    response.from = start.toString();
    response.to = end.toString();
    response.professionalId = professionalId;
    response.status = normalizedStatus;
    response.totalAmount = BigDecimal.ZERO;
    response.totalOpenAmount = BigDecimal.ZERO;
    response.totalPaidAmount = BigDecimal.ZERO;
    response.totalEntries = 0;

    for (Object[] row : rows) {
      CommissionDtos.ReportItemResponse item = new CommissionDtos.ReportItemResponse();
      item.professionalId = asString(row[0]);
      item.professionalName = asString(row[1]);
      item.serviceAmount = NumericUtil.fromCents(asLong(row[2]));
      item.productAmount = NumericUtil.fromCents(asLong(row[3]));
      item.manualAdjustmentAmount = NumericUtil.fromCents(asLong(row[4]));
      item.totalAmount = NumericUtil.fromCents(asLong(row[5]));
      item.openAmount = NumericUtil.fromCents(asLong(row[6]));
      item.paidAmount = NumericUtil.fromCents(asLong(row[7]));
      item.totalEntries = (int) asLong(row[8]);
      response.items.add(item);
      response.totalAmount = response.totalAmount.add(item.totalAmount);
      response.totalOpenAmount = response.totalOpenAmount.add(item.openAmount);
      response.totalPaidAmount = response.totalPaidAmount.add(item.paidAmount);
      response.totalEntries += item.totalEntries;
    }
    return response;
  }

  @Transactional(readOnly = true)
  public CommissionDtos.ProfessionalReportResponse reportByProfessional(
      UUID professionalId, String from, String to) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate start = parseRequiredDate(from, "from obrigatorio");
    LocalDate end = parseRequiredDate(to, "to obrigatorio");
    if (end.isBefore(start)) throw new IllegalArgumentException("Periodo invalido");

    Profissional professional = getProfessionalOrFail(tenantId, professionalId);
    List<CommissionEntry> entries =
        entryRepository.listByTenantAndProfessionalAndCreatedAtRange(
            tenantId,
            professionalId,
            start.atStartOfDay(ZONA_BR).toInstant(),
            end.plusDays(1).atStartOfDay(ZONA_BR).toInstant());

    CommissionDtos.ProfessionalReportResponse response = new CommissionDtos.ProfessionalReportResponse();
    response.professionalId = professional.getId().toString();
    response.professionalName = professional.getName();
    response.from = start.toString();
    response.to = end.toString();
    response.totalAmount = BigDecimal.ZERO;
    response.totalOpenAmount = BigDecimal.ZERO;
    response.totalPaidAmount = BigDecimal.ZERO;
    for (CommissionEntry entry : entries) {
      CommissionDtos.EntryResponse item = toEntryResponse(entry, professional.getName());
      response.entries.add(item);
      if (!"REVERSED".equals(item.entryStatus)) {
        response.totalAmount = response.totalAmount.add(item.totalAmount);
      }
      if ("PAID".equals(item.entryStatus)) {
        response.totalPaidAmount = response.totalPaidAmount.add(item.totalAmount);
      }
      if ("OPEN".equals(item.entryStatus)) {
        response.totalOpenAmount = response.totalOpenAmount.add(item.totalAmount);
      }
    }
    return response;
  }

  // ─── CICLOS ──────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public CommissionDtos.CycleListResponse listCycles(String status) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<CommissionCycle> cycles =
        status == null || status.isBlank()
            ? cycleRepository.findByTenantIdOrderByPeriodStartDescCreatedAtDesc(tenantId)
            : cycleRepository.listByTenantAndStatus(tenantId, status.trim().toUpperCase(Locale.ROOT));
    CommissionDtos.CycleListResponse response = new CommissionDtos.CycleListResponse();
    for (CommissionCycle cycle : cycles) {
      response.items.add(toCycleResponse(cycle));
    }
    return response;
  }

  @Transactional
  public CommissionDtos.CycleResponse closeCycle(CommissionDtos.CycleCloseRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID actorUserId = authenticatedUser.idOuNulo();
    LocalDate start = parseRequiredDate(request.periodStart, "periodStart obrigatorio");
    LocalDate end = parseRequiredDate(request.periodEnd, "periodEnd obrigatorio");
    if (end.isBefore(start)) throw new IllegalArgumentException("Periodo invalido");
    if (cycleRepository.findByTenantIdAndPeriodStartAndPeriodEnd(tenantId, start, end).isPresent()) {
      throw new IllegalArgumentException("Ja existe fechamento de comissao para este periodo");
    }

    CommissionCycle cycle = new CommissionCycle();
    cycle.setTenantId(tenantId);
    cycle.setPeriodStart(start);
    cycle.setPeriodEnd(end);
    cycle.setStatus("CLOSED");
    cycle.setClosedAt(Instant.now());
    cycle.setClosedByUserId(actorUserId);
    cycleRepository.save(cycle);
    entityManager.flush();

    cycle.setTotalAmountCents(closeCycleEntries(tenantId, cycle, start, end));
    CommissionDtos.CycleResponse response = toCycleResponse(cycle);
    auditSuccess(
        tenantId, actorUserId, "COMMISSION_CYCLE_CLOSE", "COMMISSION_CYCLE", cycle.getId(), null, response);
    return response;
  }

  @Transactional
  public CommissionDtos.CycleResponse payCycle(UUID cycleId, CommissionDtos.CyclePayRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID actorUserId = authenticatedUser.idOuNulo();
    CommissionCycle cycle =
        cycleRepository
            .findByTenantIdAndId(tenantId, cycleId)
            .orElseThrow(() -> new IllegalArgumentException("Ciclo de comissao nao encontrado"));
    if ("PAID".equals(cycle.getStatus())) {
      throw new IllegalArgumentException("Ciclo de comissao ja pago");
    }

    CommissionDtos.CycleResponse before = toCycleResponse(cycle);
    cycle.setStatus("PAID");
    cycle.setPaidAt(Instant.now());
    cycle.setPaidByUserId(actorUserId);

    // Soma por profissional ANTES do UPDATE (mesmo filtro entry_status <> REVERSED usado nele) —
    // sem isso, pagar comissao nunca apareceria no caixa/relatorios financeiros, que so leem a
    // tabela de Transacao.
    lancarDespesaComissaoPorProfissional(tenantId, cycle);

    entryRepository.markCycleEntriesAsPaid(tenantId, cycle.getId());

    CommissionDtos.CycleResponse after = toCycleResponse(cycle);
    auditSuccess(
        tenantId,
        actorUserId,
        "COMMISSION_CYCLE_PAY",
        "COMMISSION_CYCLE",
        cycle.getId(),
        before,
        afterWithNotes(after, request != null ? request.notes : null));
    return after;
  }

  // ─── AJUSTE MANUAL ───────────────────────────────────────────────────────

  @Transactional
  public CommissionDtos.AdjustmentResponse createAdjustment(CommissionDtos.AdjustmentRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID actorUserId = authenticatedUser.idOuNulo();
    UUID professionalId = parseUuidRequired(request.professionalId, "professionalId invalido");
    Profissional professional = getProfessionalOrFail(tenantId, professionalId);
    if (request.amount == null || NumericUtil.isZeroOrNegative(request.amount)) {
      throw new IllegalArgumentException("amount nao pode ser zero");
    }

    Instant effectiveAt = parseInstantNullable(request.effectiveAt);
    if (effectiveAt == null) effectiveAt = Instant.now();

    CommissionEntry entry = new CommissionEntry();
    entry.setTenantId(tenantId);
    entry.setProfessionalId(professionalId);
    entry.setOriginType("MANUAL_ADJUSTMENT");
    entry.setPeriodKey(PERIOD_KEY_FORMAT.format(effectiveAt.atZone(ZONA_BR).toLocalDate()));
    entry.setBaseAmountCents(0L);
    entry.setPercentValue(BigDecimal.ZERO);
    entry.setPercentAmountCents(0L);
    entry.setFixedAmountCents(NumericUtil.toCents(request.amount));
    entry.setTotalAmountCents(NumericUtil.toCents(request.amount));
    entry.setEntryStatus("OPEN");
    entry.setNotes(request.reason.trim());
    entry.setCreatedAt(effectiveAt);
    entryRepository.save(entry);

    CommissionDtos.AdjustmentResponse response = new CommissionDtos.AdjustmentResponse();
    response.entry = toEntryResponse(entry, professional.getName());
    response.message = "Ajuste manual registrado com sucesso";
    auditSuccess(
        tenantId,
        actorUserId,
        "COMMISSION_MANUAL_ADJUSTMENT_CREATE",
        "COMMISSION_ENTRY",
        entry.getId(),
        null,
        response);
    return response;
  }

  // ─── REGISTRO AUTOMATICO (chamado por scheduling / pos / finance) ─────────

  @Transactional
  public void registerServiceCommissionsIfApplicable(
      UUID tenantId,
      UUID appointmentId,
      UUID professionalId,
      Collection<AgendamentoItem> appointmentItems,
      LocalDate appointmentDate) {
    if (tenantId == null
        || appointmentId == null
        || professionalId == null
        || appointmentItems == null
        || appointmentItems.isEmpty()) {
      return;
    }

    Profissional professional = getProfessionalOrFail(tenantId, professionalId);
    CommissionRuleSet ruleSet = resolveApplicableRuleSet(tenantId, professionalId);
    if (ruleSet == null) return;

    for (AgendamentoItem appointmentItem : appointmentItems) {
      if (appointmentItem == null || appointmentItem.getId() == null || appointmentItem.getServiceId() == null) {
        continue;
      }
      boolean alreadyRegistered =
          entryRepository
              .findLatestNonReversedByTenantAndOrigin(tenantId, "SERVICE", appointmentItem.getId())
              .isPresent();
      if (alreadyRegistered) continue;

      Servico service =
          appointmentItem.getService() != null
              ? appointmentItem.getService()
              : servicoRepository.findById(appointmentItem.getServiceId()).orElse(null);
      if (service == null || !tenantId.equals(service.getTenantId())) continue;

      CommissionRule rule = resolveApplicableRule(ruleSet, service);
      if (rule == null) continue;

      BigDecimal grossAmt =
          appointmentItem.getGrossAmount() != null ? appointmentItem.getGrossAmount() : BigDecimal.ZERO;
      BigDecimal netAmt =
          appointmentItem.getTotalPrice() != null ? appointmentItem.getTotalPrice() : BigDecimal.ZERO;
      long baseAmountCents = computeBaseAmount(rule, grossAmt, netAmt);
      long percentAmountCents = calculatePercentAmountCents(baseAmountCents, rule.getPercentValue());
      long fixedAmountCents = rule.getFixedAmountCents();
      long totalAmountCents = percentAmountCents + fixedAmountCents;
      if (totalAmountCents <= 0) continue;

      CommissionEntry entry = new CommissionEntry();
      entry.setTenantId(tenantId);
      entry.setProfessionalId(professionalId);
      entry.setOriginType("SERVICE");
      entry.setOriginId(appointmentItem.getId());
      entry.setOriginReference("APPOINTMENT:" + appointmentId + ":ITEM:" + appointmentItem.getId());
      entry.setRuleSetId(ruleSet.getId());
      entry.setRuleId(rule.getId());
      entry.setPeriodKey(
          PERIOD_KEY_FORMAT.format(appointmentDate != null ? appointmentDate : LocalDate.now()));
      entry.setBaseAmountCents(baseAmountCents);
      entry.setPercentValue(rule.getPercentValue());
      entry.setPercentAmountCents(percentAmountCents);
      entry.setFixedAmountCents(fixedAmountCents);
      entry.setTotalAmountCents(totalAmountCents);
      entry.setEntryStatus("OPEN");
      entry.setNotes("Comissao automatica de servico: " + service.getName());
      entryRepository.save(entry);

      auditSuccess(
          tenantId,
          authenticatedUser.idOuNulo(),
          "COMMISSION_ENTRY_AUTO_CREATE",
          "COMMISSION_ENTRY",
          entry.getId(),
          null,
          toEntryResponse(entry, professional.getName()));
    }
  }

  @Transactional
  public void reverseServiceCommissionIfApplicable(UUID tenantId, UUID appointmentId, String reason) {
    reverseServiceEntriesIfApplicable(
        tenantId,
        appointmentId,
        reason == null || reason.isBlank() ? "Comissao revertida automaticamente" : reason.trim());
  }

  /**
   * Reverte a comissao (PRODUCT ou SERVICE) associada a uma origem especifica — usada no estorno
   * de comanda, onde cada item ja tem sua propria chave de origem.
   */
  @Transactional
  public void reverseEntryForOrigin(UUID tenantId, String originType, UUID originId, String reason) {
    reverseEntryIfApplicable(
        tenantId,
        originType,
        originId,
        reason == null || reason.isBlank() ? "Comissao revertida automaticamente" : reason.trim());
  }

  /**
   * Registra comissao de SERVICO ao fechar uma comanda (F01), inclusive comandas avulsas sem
   * agendamento vinculado. Usa o {@code ComandaItem.id} como chave de origem/dedup.
   */
  @Transactional
  public void registerServiceCommissionForComandaItemIfApplicable(
      UUID tenantId,
      UUID comandaId,
      UUID comandaItemId,
      UUID professionalId,
      UUID serviceId,
      BigDecimal grossAmount,
      BigDecimal netAmount,
      Instant effectiveAt) {
    if (tenantId == null || comandaItemId == null || professionalId == null || serviceId == null) return;
    boolean alreadyRegistered =
        entryRepository.findLatestNonReversedByTenantAndOrigin(tenantId, "SERVICE", comandaItemId).isPresent();
    if (alreadyRegistered) return;

    Profissional professional = getProfessionalOrFail(tenantId, professionalId);
    CommissionRuleSet ruleSet = resolveApplicableRuleSet(tenantId, professionalId);
    if (ruleSet == null) return;

    Servico service = servicoRepository.findById(serviceId).orElse(null);
    if (service == null || !tenantId.equals(service.getTenantId())) return;

    CommissionRule rule = resolveApplicableRule(ruleSet, service);
    if (rule == null) return;

    long baseAmountCents =
        computeBaseAmount(
            rule,
            grossAmount != null ? grossAmount : BigDecimal.ZERO,
            netAmount != null ? netAmount : BigDecimal.ZERO);
    long percentAmountCents = calculatePercentAmountCents(baseAmountCents, rule.getPercentValue());
    long fixedAmountCents = rule.getFixedAmountCents();
    long totalAmountCents = percentAmountCents + fixedAmountCents;
    if (totalAmountCents <= 0) return;

    Instant effective = effectiveAt != null ? effectiveAt : Instant.now();
    CommissionEntry entry = new CommissionEntry();
    entry.setTenantId(tenantId);
    entry.setProfessionalId(professionalId);
    entry.setOriginType("SERVICE");
    entry.setOriginId(comandaItemId);
    entry.setOriginReference("COMANDA:" + comandaId + ":ITEM:" + comandaItemId);
    entry.setRuleSetId(ruleSet.getId());
    entry.setRuleId(rule.getId());
    entry.setPeriodKey(PERIOD_KEY_FORMAT.format(effective.atZone(ZONA_BR).toLocalDate()));
    entry.setBaseAmountCents(baseAmountCents);
    entry.setPercentValue(rule.getPercentValue());
    entry.setPercentAmountCents(percentAmountCents);
    entry.setFixedAmountCents(fixedAmountCents);
    entry.setTotalAmountCents(totalAmountCents);
    entry.setEntryStatus("OPEN");
    entry.setNotes("Comissao automatica de servico: " + service.getName());
    entryRepository.save(entry);

    auditSuccess(
        tenantId,
        authenticatedUser.idOuNulo(),
        "COMMISSION_ENTRY_AUTO_CREATE",
        "COMMISSION_ENTRY",
        entry.getId(),
        null,
        toEntryResponse(entry, professional.getName()));
  }

  @Transactional
  public void registerProductCommissionIfApplicable(
      UUID tenantId,
      UUID transactionId,
      UUID professionalId,
      UUID productId,
      String productCategory,
      long totalPrice,
      Instant transactionDate,
      String description) {
    if (tenantId == null || transactionId == null || professionalId == null) return;
    if (productId == null && (productCategory == null || productCategory.isBlank())) return;
    boolean alreadyRegistered =
        entryRepository.findLatestNonReversedByTenantAndOrigin(tenantId, "PRODUCT", transactionId).isPresent();
    if (alreadyRegistered) return;

    Profissional professional = getProfessionalOrFail(tenantId, professionalId);
    String productName = productId != null ? findProductNameForTenant(tenantId, productId) : null;
    if (productId != null && productName == null) return;

    CommissionRuleSet ruleSet = resolveApplicableRuleSet(tenantId, professionalId);
    if (ruleSet == null) return;
    CommissionRule rule = resolveApplicableProductRule(ruleSet, productId, productCategory);
    if (rule == null) return;

    BigDecimal totalPriceAmount = NumericUtil.fromCents(totalPrice);
    long baseAmountCents = computeBaseAmount(rule, totalPriceAmount, totalPriceAmount);
    long percentAmountCents = calculatePercentAmountCents(baseAmountCents, rule.getPercentValue());
    long fixedAmountCents = rule.getFixedAmountCents();
    long totalAmountCents = percentAmountCents + fixedAmountCents;
    if (totalAmountCents <= 0) return;

    LocalDate effectiveDate =
        transactionDate != null ? transactionDate.atZone(ZONA_BR).toLocalDate() : LocalDate.now(ZONA_BR);

    CommissionEntry entry = new CommissionEntry();
    entry.setTenantId(tenantId);
    entry.setProfessionalId(professionalId);
    entry.setOriginType("PRODUCT");
    entry.setOriginId(transactionId);
    entry.setOriginReference("TRANSACTION:" + transactionId);
    entry.setRuleSetId(ruleSet.getId());
    entry.setRuleId(rule.getId());
    entry.setPeriodKey(PERIOD_KEY_FORMAT.format(effectiveDate));
    entry.setBaseAmountCents(baseAmountCents);
    entry.setPercentValue(rule.getPercentValue());
    entry.setPercentAmountCents(percentAmountCents);
    entry.setFixedAmountCents(fixedAmountCents);
    entry.setTotalAmountCents(totalAmountCents);
    entry.setEntryStatus("OPEN");
    entry.setCreatedAt(transactionDate != null ? transactionDate : Instant.now());
    entry.setNotes(
        "Comissao automatica de produto: " + (productName != null ? productName : blankToNull(description)));
    entryRepository.save(entry);

    auditSuccess(
        tenantId,
        authenticatedUser.idOuNulo(),
        "COMMISSION_ENTRY_PRODUCT_AUTO_CREATE",
        "COMMISSION_ENTRY",
        entry.getId(),
        null,
        toEntryResponse(entry, professional.getName()));
  }

  @Transactional
  public void reverseProductCommissionIfApplicable(UUID tenantId, UUID transactionId, String reason) {
    reverseEntryIfApplicable(
        tenantId,
        "PRODUCT",
        transactionId,
        reason == null || reason.isBlank()
            ? "Comissao de produto revertida automaticamente"
            : reason.trim());
  }

  // ─── INTERNOS ────────────────────────────────────────────────────────────

  private CommissionRuleSet getRuleSetOrFail(UUID tenantId, UUID ruleSetId) {
    return ruleSetRepository
        .findByTenantIdAndId(tenantId, ruleSetId)
        .orElseThrow(() -> new IllegalArgumentException("Regra de comissao nao encontrada"));
  }

  private Profissional getProfessionalOrFail(UUID tenantId, UUID professionalId) {
    return profissionalRepository
        .findById(professionalId)
        .filter(item -> tenantId.equals(item.getTenantId()))
        .orElseThrow(() -> new IllegalArgumentException("Profissional nao encontrado"));
  }

  private void applyRuleSet(
      CommissionRuleSet entity, CommissionDtos.RuleSetUpsertRequest request, UUID tenantId) {
    String scopeType = requiredUpper(request.scopeType, "scopeType obrigatorio");
    if (!"GLOBAL".equals(scopeType) && !"PROFESSIONAL".equals(scopeType)) {
      throw new IllegalArgumentException("scopeType invalido");
    }
    UUID professionalId = parseUuidNullable(request.professionalId, "professionalId invalido");
    if ("GLOBAL".equals(scopeType) && professionalId != null) {
      throw new IllegalArgumentException("Regra global nao pode informar profissional");
    }
    if ("PROFESSIONAL".equals(scopeType) && professionalId == null) {
      throw new IllegalArgumentException("Regra do profissional exige professionalId");
    }
    if (professionalId != null) getProfessionalOrFail(tenantId, professionalId);

    entity.setTenantId(tenantId);
    entity.setScopeType(scopeType);
    entity.setProfessionalId(professionalId);
    entity.setName(required(request.name, "name obrigatorio"));
    entity.setActive(!Boolean.FALSE.equals(request.active));
    if (entity.isActive()) deactivateConflictingRuleSets(entity);
  }

  private void deactivateConflictingRuleSets(CommissionRuleSet entity) {
    ruleSetRepository.deactivateConflicting(
        entity.getTenantId(),
        entity.getScopeType(),
        entity.getId() == null ? UUID.randomUUID() : entity.getId(),
        entity.getProfessionalId());
  }

  private List<UUID> professionalIdsForRuleSet(CommissionRuleSet entity) {
    if (entity == null || entity.getProfessionalId() == null) return List.of();
    return List.of(entity.getProfessionalId());
  }

  private void persistRules(
      CommissionRuleSet entity, List<CommissionDtos.RuleRequest> requests, UUID tenantId) {
    if (requests == null || requests.isEmpty()) {
      throw new IllegalArgumentException("Ao menos uma regra deve ser informada");
    }
    for (CommissionDtos.RuleRequest request : requests) {
      CommissionRule rule = new CommissionRule();
      rule.setTenantId(tenantId);
      rule.setRuleSetId(entity.getId());
      applyRule(rule, request, tenantId);
      ruleRepository.save(rule);
    }
  }

  private void applyRule(CommissionRule entity, CommissionDtos.RuleRequest request, UUID tenantId) {
    entity.setTargetType(requiredUpper(request.targetType, "targetType obrigatorio"));
    entity.setTargetId(parseUuidNullable(request.targetId, "targetId invalido"));
    entity.setTargetCode(blankToNull(request.targetCode));
    entity.setPercentValue(request.percentValue == null ? BigDecimal.ZERO : request.percentValue);
    entity.setFixedAmountCents(request.fixedAmount == null ? 0L : NumericUtil.toCents(request.fixedAmount));
    entity.setPercentBaseType(requiredUpper(request.percentBaseType, "percentBaseType obrigatorio"));
    entity.setRefundPolicy(requiredUpper(request.refundPolicy, "refundPolicy obrigatorio"));
    entity.setActive(!Boolean.FALSE.equals(request.active));
    entity.setStartsAt(parseInstantNullable(request.startsAt));
    entity.setEndsAt(parseInstantNullable(request.endsAt));

    if (entity.getPercentValue().compareTo(BigDecimal.ZERO) < 0
        || entity.getPercentValue().compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new IllegalArgumentException("percentValue deve estar entre 0 e 100");
    }
    if (entity.getPercentValue().compareTo(BigDecimal.ZERO) == 0 && entity.getFixedAmountCents() == 0L) {
      throw new IllegalArgumentException("A regra deve possuir percentual ou valor fixo");
    }
    validateTarget(tenantId, entity);
  }

  private void validateTarget(UUID tenantId, CommissionRule rule) {
    switch (rule.getTargetType()) {
      case "GENERAL" -> {
        if (rule.getTargetId() != null || rule.getTargetCode() != null) {
          throw new IllegalArgumentException("Regra geral nao aceita alvo especifico");
        }
      }
      case "SERVICE" -> {
        if (rule.getTargetId() == null || rule.getTargetCode() != null) {
          throw new IllegalArgumentException("Servico exige targetId");
        }
        ensureCount("services", tenantId, rule.getTargetId(), "Servico nao encontrado");
      }
      case "PRODUCT" -> {
        if (rule.getTargetId() == null || rule.getTargetCode() != null) {
          throw new IllegalArgumentException("Produto exige targetId");
        }
        ensureCount("itens_estoque", tenantId, rule.getTargetId(), "Produto nao encontrado");
      }
      case "SERVICE_CATEGORY", "PRODUCT_CATEGORY" -> {
        if (rule.getTargetId() != null || rule.getTargetCode() == null) {
          throw new IllegalArgumentException("Categoria exige targetCode");
        }
      }
      default -> throw new IllegalArgumentException("targetType invalido");
    }
  }

  private void ensureCount(String table, UUID tenantId, UUID id, String errorMessage) {
    long count =
        asLong(
            entityManager
                .createNativeQuery(
                    "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = :tenantId AND id = :id")
                .setParameter("tenantId", tenantId)
                .setParameter("id", id)
                .getSingleResult());
    if (count <= 0) throw new IllegalArgumentException(errorMessage);
  }

  /**
   * Lanca uma Transacao de EXPENSE por profissional com o total de comissao paga neste ciclo, para
   * o caixa e os relatorios financeiros (que so leem a tabela de Transacao) refletirem o custo real
   * da comissao, nao so a receita bruta.
   */
  private void lancarDespesaComissaoPorProfissional(UUID tenantId, CommissionCycle cycle) {
    List<Object[]> porProfissional =
        entryRepository.sumTotalCentsByProfessionalForCycle(tenantId, cycle.getId());
    if (porProfissional.isEmpty()) return;

    UUID categoriaComissaoId = resolveTransactionCategoryId(tenantId, "COMMISSION");
    for (Object[] row : porProfissional) {
      UUID professionalId = (UUID) row[0];
      long totalCents = row[1] instanceof Number number ? number.longValue() : 0L;
      if (professionalId == null || totalCents <= 0) continue;

      String professionalName =
          profissionalRepository.findById(professionalId).map(Profissional::getName).orElse("profissional");
      Transacao transacao = new Transacao();
      transacao.setTenantId(tenantId);
      transacao.setProfessionalId(professionalId);
      transacao.setType(TipoTransacao.EXPENSE);
      transacao.setCategoryId(categoriaComissaoId);
      transacao.setDescription(
          "Pagamento de comissao - "
              + professionalName
              + " (ciclo "
              + cycle.getPeriodStart()
              + " a "
              + cycle.getPeriodEnd()
              + ")");
      transacao.setAmount(NumericUtil.fromCents(totalCents));
      transacao.setPaymentMethod(MetodoPagamento.OTHER);
      transacao.setDate(Instant.now());
      transacaoRepository.save(transacao);
    }
  }

  private UUID resolveTransactionCategoryId(UUID tenantId, String categoryName) {
    return transactionCategoryRepository
        .findByTenantAndName(tenantId, categoryName)
        .orElseGet(
            () -> {
              TransactionCategory category = new TransactionCategory();
              category.setTenantId(tenantId);
              category.setName(categoryName);
              return transactionCategoryRepository.save(category);
            })
        .getId();
  }

  private long closeCycleEntries(UUID tenantId, CommissionCycle cycle, LocalDate start, LocalDate end) {
    List<CommissionEntry> entries =
        entryRepository.listOpenWithoutCycleInPeriod(
            tenantId,
            start.atStartOfDay(ZONA_BR).toInstant(),
            end.plusDays(1).atStartOfDay(ZONA_BR).toInstant());

    long total = 0L;
    for (CommissionEntry entry : entries) {
      entry.setCycleId(cycle.getId());
      total += entry.getTotalAmountCents();
    }
    return total;
  }

  private CommissionDtos.RuleSetResponse toRuleSetResponse(
      CommissionRuleSet entity, Map<UUID, String> professionalNames) {
    CommissionDtos.RuleSetResponse response = new CommissionDtos.RuleSetResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.scopeType = entity.getScopeType();
    response.professionalId = entity.getProfessionalId() != null ? entity.getProfessionalId().toString() : null;
    response.professionalName =
        entity.getProfessionalId() != null ? professionalNames.get(entity.getProfessionalId()) : null;
    response.name = entity.getName();
    response.active = entity.isActive();
    response.createdAt = asString(entity.getCreatedAt());
    response.updatedAt = asString(entity.getUpdatedAt());

    if (entity.getId() != null) {
      for (CommissionRule rule : ruleRepository.listByRuleSet(entity.getTenantId(), entity.getId())) {
        CommissionDtos.RuleItemResponse item = new CommissionDtos.RuleItemResponse();
        item.id = rule.getId() != null ? rule.getId().toString() : null;
        item.targetType = rule.getTargetType();
        item.targetId = rule.getTargetId() != null ? rule.getTargetId().toString() : null;
        item.targetCode = rule.getTargetCode();
        item.targetLabel = resolveTargetLabel(rule);
        item.percentValue = rule.getPercentValue();
        item.fixedAmount = NumericUtil.fromCents(rule.getFixedAmountCents());
        item.percentBaseType = rule.getPercentBaseType();
        item.refundPolicy = rule.getRefundPolicy();
        item.active = rule.isActive();
        item.startsAt = asString(rule.getStartsAt());
        item.endsAt = asString(rule.getEndsAt());
        response.rules.add(item);
      }
    }
    return response;
  }

  private String resolveTargetLabel(CommissionRule rule) {
    if ("GENERAL".equals(rule.getTargetType())) return "Regra geral";
    if ("SERVICE_CATEGORY".equals(rule.getTargetType()) || "PRODUCT_CATEGORY".equals(rule.getTargetType())) {
      return rule.getTargetCode();
    }
    if (rule.getTargetId() == null) return null;
    if ("SERVICE".equals(rule.getTargetType())) {
      return servicoRepository.findById(rule.getTargetId()).map(Servico::getName).orElse(null);
    }
    if ("PRODUCT".equals(rule.getTargetType())) {
      return asString(
          entityManager
              .createNativeQuery("SELECT nome FROM itens_estoque WHERE id = :id")
              .setParameter("id", rule.getTargetId())
              .getResultStream()
              .findFirst()
              .orElse(null));
    }
    return null;
  }

  private CommissionDtos.EntryResponse toEntryResponse(CommissionEntry entry, String professionalName) {
    CommissionDtos.EntryResponse response = new CommissionDtos.EntryResponse();
    response.id = entry.getId() != null ? entry.getId().toString() : null;
    response.professionalId = entry.getProfessionalId() != null ? entry.getProfessionalId().toString() : null;
    response.professionalName = professionalName;
    response.originType = entry.getOriginType();
    response.originId = entry.getOriginId() != null ? entry.getOriginId().toString() : null;
    response.originReference = entry.getOriginReference();
    response.periodKey = entry.getPeriodKey();
    response.baseAmount = NumericUtil.fromCents(entry.getBaseAmountCents());
    response.percentValue = entry.getPercentValue();
    response.percentAmount = NumericUtil.fromCents(entry.getPercentAmountCents());
    response.fixedAmount = NumericUtil.fromCents(entry.getFixedAmountCents());
    response.totalAmount = NumericUtil.fromCents(entry.getTotalAmountCents());
    response.entryStatus = entry.getEntryStatus();
    response.notes = entry.getNotes();
    response.createdAt = asString(entry.getCreatedAt());
    response.reversedAt = asString(entry.getReversedAt());
    response.cycleId = entry.getCycleId() != null ? entry.getCycleId().toString() : null;
    return response;
  }

  private CommissionDtos.CycleResponse toCycleResponse(CommissionCycle cycle) {
    CommissionDtos.CycleResponse response = new CommissionDtos.CycleResponse();
    response.id = cycle.getId() != null ? cycle.getId().toString() : null;
    response.periodStart = cycle.getPeriodStart() != null ? cycle.getPeriodStart().toString() : null;
    response.periodEnd = cycle.getPeriodEnd() != null ? cycle.getPeriodEnd().toString() : null;
    response.status = cycle.getStatus();
    response.closedAt = asString(cycle.getClosedAt());
    response.closedByUserId = cycle.getClosedByUserId() != null ? cycle.getClosedByUserId().toString() : null;
    response.paidAt = asString(cycle.getPaidAt());
    response.paidByUserId = cycle.getPaidByUserId() != null ? cycle.getPaidByUserId().toString() : null;
    response.totalAmount = NumericUtil.fromCents(cycle.getTotalAmountCents());
    response.createdAt = asString(cycle.getCreatedAt());
    response.entryCount = (int) entryRepository.countByCycleId(cycle.getId());
    return response;
  }

  private Map<String, Object> afterWithNotes(CommissionDtos.CycleResponse cycle, String notes) {
    Map<String, Object> result = new HashMap<>();
    result.put("cycle", cycle);
    if (notes != null && !notes.isBlank()) result.put("notes", notes.trim());
    return result;
  }

  private Map<UUID, String> mapProfessionalNames(UUID tenantId, List<UUID> professionalIds) {
    List<UUID> ids =
        professionalIds == null
            ? List.of()
            : professionalIds.stream().filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty()) return Map.of();
    Map<UUID, String> result = new HashMap<>();
    for (Profissional item : profissionalRepository.findByIdInAndTenantId(ids, tenantId)) {
      result.put(item.getId(), item.getName());
    }
    return result;
  }

  private void auditSuccess(
      UUID tenantId, UUID actorUserId, String action, String entityType, UUID entityId, Object before, Object after) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = actorUserId;
      command.module = AuditConstants.Module.FINANCE;
      command.action = action;
      command.entityType = entityType;
      command.entityId = entityId != null ? entityId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      command.metadata = Map.of("feature", "professional_commissions");
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nunca derruba o fluxo principal (mesmo comportamento do original).
    }
  }

  private void reverseEntryIfApplicable(UUID tenantId, String originType, UUID originId, String reason) {
    if (tenantId == null || originId == null) return;
    CommissionEntry entry = entryRepository.findByTenantAndOrigin(tenantId, originType, originId).orElse(null);
    if (entry == null) return;
    if ("PAID".equals(entry.getEntryStatus()) || "REVERSED".equals(entry.getEntryStatus())) return;

    if (!"REVERSE_COMMISSION".equals(resolveRefundPolicy(entry))) return;

    entry.setEntryStatus("REVERSED");
    entry.setReversedAt(Instant.now());
    entry.setNotes(appendNote(entry.getNotes(), reason));

    auditSuccess(
        tenantId,
        authenticatedUser.idOuNulo(),
        "COMMISSION_ENTRY_AUTO_REVERSE",
        "COMMISSION_ENTRY",
        entry.getId(),
        null,
        Map.of(
            "entryId", entry.getId().toString(),
            "originType", originType,
            "originId", originId.toString(),
            "reason", entry.getNotes()));
  }

  private void reverseServiceEntriesIfApplicable(UUID tenantId, UUID appointmentId, String reason) {
    if (tenantId == null || appointmentId == null) return;
    List<CommissionEntry> entries =
        entryRepository.listByTenantAndOriginReferencePrefix(
            tenantId, "SERVICE", "APPOINTMENT:" + appointmentId + ":ITEM:");
    for (CommissionEntry entry : entries) {
      if (entry == null) continue;
      if ("PAID".equals(entry.getEntryStatus()) || "REVERSED".equals(entry.getEntryStatus())) continue;
      if (!"REVERSE_COMMISSION".equals(resolveRefundPolicy(entry))) continue;

      entry.setEntryStatus("REVERSED");
      entry.setReversedAt(Instant.now());
      entry.setNotes(appendNote(entry.getNotes(), reason));

      auditSuccess(
          tenantId,
          authenticatedUser.idOuNulo(),
          "COMMISSION_ENTRY_AUTO_REVERSE",
          "COMMISSION_ENTRY",
          entry.getId(),
          null,
          Map.of(
              "entryId", entry.getId().toString(),
              "originType", "SERVICE",
              "originReference", entry.getOriginReference(),
              "reason", entry.getNotes()));
    }
  }

  /** Sem regra vinculada, o original assume REVERSE_COMMISSION (reverte). */
  private String resolveRefundPolicy(CommissionEntry entry) {
    if (entry.getRuleId() == null) return "REVERSE_COMMISSION";
    return ruleRepository
        .findById(entry.getRuleId())
        .map(CommissionRule::getRefundPolicy)
        .orElse("REVERSE_COMMISSION");
  }

  private CommissionRuleSet resolveApplicableRuleSet(UUID tenantId, UUID professionalId) {
    List<CommissionRuleSet> professionalRuleSets =
        ruleSetRepository.listActiveProfessionalScoped(tenantId, professionalId);
    if (!professionalRuleSets.isEmpty()) return professionalRuleSets.get(0);

    List<CommissionRuleSet> globalRuleSets = ruleSetRepository.listActiveGlobalScoped(tenantId);
    return globalRuleSets.isEmpty() ? null : globalRuleSets.get(0);
  }

  private CommissionRule resolveApplicableRule(CommissionRuleSet ruleSet, Servico service) {
    List<CommissionRule> rules = ruleRepository.listByRuleSet(ruleSet.getTenantId(), ruleSet.getId());
    CommissionRule serviceRule =
        rules.stream()
            .filter(CommissionRule::isActive)
            .filter(item -> "SERVICE".equals(item.getTargetType()))
            .filter(item -> service.getId().equals(item.getTargetId()))
            .findFirst()
            .orElse(null);
    if (serviceRule != null) return serviceRule;

    String category = resolveServiceCategoryName(service);
    if (category != null && !category.isBlank()) {
      CommissionRule categoryRule =
          rules.stream()
              .filter(CommissionRule::isActive)
              .filter(item -> "SERVICE_CATEGORY".equals(item.getTargetType()))
              .filter(item -> category.equalsIgnoreCase(item.getTargetCode()))
              .findFirst()
              .orElse(null);
      if (categoryRule != null) return categoryRule;
    }

    return rules.stream()
        .filter(CommissionRule::isActive)
        .filter(item -> "GENERAL".equals(item.getTargetType()))
        .findFirst()
        .orElse(null);
  }

  private String resolveServiceCategoryName(Servico service) {
    if (service.getCategoryId() == null) return null;
    return serviceCategoryRepository
        .findById(service.getCategoryId())
        .map(ServiceCategory::getName)
        .map(String::trim)
        .orElse(null);
  }

  private CommissionRule resolveApplicableProductRule(
      CommissionRuleSet ruleSet, UUID productId, String productCategory) {
    List<CommissionRule> rules = ruleRepository.listByRuleSet(ruleSet.getTenantId(), ruleSet.getId());
    if (productId != null) {
      CommissionRule productRule =
          rules.stream()
              .filter(CommissionRule::isActive)
              .filter(item -> "PRODUCT".equals(item.getTargetType()))
              .filter(item -> productId.equals(item.getTargetId()))
              .findFirst()
              .orElse(null);
      if (productRule != null) return productRule;
    }

    String normalizedCategory = productCategory == null ? null : productCategory.trim();
    if (normalizedCategory != null && !normalizedCategory.isBlank()) {
      CommissionRule categoryRule =
          rules.stream()
              .filter(CommissionRule::isActive)
              .filter(item -> "PRODUCT_CATEGORY".equals(item.getTargetType()))
              .filter(item -> normalizedCategory.equalsIgnoreCase(item.getTargetCode()))
              .findFirst()
              .orElse(null);
      if (categoryRule != null) return categoryRule;
    }

    return rules.stream()
        .filter(CommissionRule::isActive)
        .filter(item -> "GENERAL".equals(item.getTargetType()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Nome do produto de estoque, ja validando que ele pertence ao tenant — equivalente ao
   * {@code entityManager.find(ItemEstoque.class, id)} seguido de
   * {@code !tenantId.equals(product.tenantId)} do original. Devolve null quando o produto nao
   * existe ou e de outro tenant, que e o criterio de saida antecipada do chamador.
   */
  private String findProductNameForTenant(UUID tenantId, UUID productId) {
    return itemEstoqueRepository
        .findByIdAndTenantId(productId, tenantId)
        .map(ItemEstoque::getNome)
        .orElse(null);
  }

  private long computeBaseAmount(CommissionRule rule, BigDecimal grossAmount, BigDecimal netAmount) {
    if ("NET_OF_DISCOUNT".equals(rule.getPercentBaseType())) {
      return Math.max(0L, NumericUtil.toCents(netAmount));
    }
    BigDecimal base = NumericUtil.isPositive(grossAmount) ? grossAmount : netAmount;
    return Math.max(0L, NumericUtil.toCents(base));
  }

  private long calculatePercentAmountCents(long baseAmountCents, BigDecimal percentValue) {
    if (baseAmountCents <= 0L || NumericUtil.isZeroOrNegative(percentValue)) return 0L;
    return BigDecimal.valueOf(baseAmountCents)
        .multiply(percentValue)
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact();
  }

  private String appendNote(String previous, String note) {
    if (previous == null || previous.isBlank()) return note;
    return previous + " | " + note;
  }

  private LocalDate parseRequiredDate(String value, String errorMessage) {
    try {
      return LocalDate.parse(required(value, errorMessage));
    } catch (Exception e) {
      throw new IllegalArgumentException(errorMessage);
    }
  }

  private Instant parseInstantNullable(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("Data/hora invalida");
    }
  }

  private String normalizeStatusNullable(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!VALID_ENTRY_STATUS.contains(normalized)) {
      throw new IllegalArgumentException("status invalido");
    }
    return normalized;
  }

  private UUID parseUuidRequired(String value, String errorMessage) {
    UUID parsed = parseUuidNullable(value, errorMessage);
    if (parsed == null) throw new IllegalArgumentException(errorMessage);
    return parsed;
  }

  private UUID parseUuidNullable(String value, String errorMessage) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException(errorMessage);
    }
  }

  private String required(String value, String errorMessage) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(errorMessage);
    return value.trim();
  }

  private String requiredUpper(String value, String errorMessage) {
    return required(value, errorMessage).toUpperCase(Locale.ROOT);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private long asLong(Object value) {
    if (value == null) return 0L;
    if (value instanceof Number number) return number.longValue();
    return Long.parseLong(value.toString());
  }

  private String asString(Object value) {
    return value == null ? null : value.toString();
  }
}
