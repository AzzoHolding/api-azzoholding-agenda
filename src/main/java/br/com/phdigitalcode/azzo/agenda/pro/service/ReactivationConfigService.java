package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.CycleResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.OptOutPagedItem;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.OptOutPagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.ReactivationCyclesPagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.ReactivationMetricsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.TenantReactivationConfigResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.UpdateReactivationConfigRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationConsentHistoryEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantReactivationConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStatus;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationConsentHistoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationSendLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantReactivationConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;

/**
 * Espelha {@code modules/chat/application/ReactivationConfigService.java} (444 linhas no
 * original).
 *
 * <p>Os dois limites LGPD ({@code maxMessagesPerMonthPerClient <= 4}, {@code minIntervalDays >=
 * 7}) sao aplicados aqui exatamente como no original — {@link Math#min}/{@link Math#max}, nao
 * validacao que rejeita a requisicao. {@code @Valid} no controller ja restringe os extremos
 * opostos ({@code @Max(4)}/{@code @Min(7)}), entao esta dupla checagem e redundante mas fiel.
 *
 * <p>O original declara {@code listOptOuts(UUID)} (versao nao paginada), mas o
 * {@code ReactivationConfigResource} NUNCA a invoca — so chama {@code listOptOutsPaged}. Codigo
 * morto confirmado por leitura do resource; omitido aqui, mesmo criterio ja aplicado a
 * {@code personalizeReplyWithContactName} em {@code WhatsAppWebhookController}.
 */
@Service
public class ReactivationConfigService {

  private static final Logger LOG = LoggerFactory.getLogger(ReactivationConfigService.class);
  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  private final TenantReactivationConfigRepository configRepository;
  private final ClienteRepository clienteRepository;
  private final ReactivationConsentHistoryRepository consentHistoryRepository;
  private final WhatsAppBookingReactivationCycleRepository cycleRepository;
  private final WhatsAppBookingReactivationService reactivationService;
  private final ReactivationSendLogRepository sendLogRepository;
  private final AuditService auditService;

  public ReactivationConfigService(
      TenantReactivationConfigRepository configRepository,
      ClienteRepository clienteRepository,
      ReactivationConsentHistoryRepository consentHistoryRepository,
      WhatsAppBookingReactivationCycleRepository cycleRepository,
      WhatsAppBookingReactivationService reactivationService,
      ReactivationSendLogRepository sendLogRepository,
      AuditService auditService) {
    this.configRepository = configRepository;
    this.clienteRepository = clienteRepository;
    this.consentHistoryRepository = consentHistoryRepository;
    this.cycleRepository = cycleRepository;
    this.reactivationService = reactivationService;
    this.sendLogRepository = sendLogRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public TenantReactivationConfigResponse getConfig(UUID tenantId) {
    TenantReactivationConfigEntity entity = configRepository.findByTenantIdOrDefault(tenantId);
    return toResponse(entity, tenantId);
  }

  @Transactional
  public TenantReactivationConfigResponse updateConfig(
      UUID tenantId, UUID actorUserId, String actorRole, UpdateReactivationConfigRequest request) {
    TenantReactivationConfigEntity entity =
        configRepository
            .findByTenantId(tenantId)
            .orElseGet(
                () -> {
                  TenantReactivationConfigEntity novo = new TenantReactivationConfigEntity();
                  novo.setTenantId(tenantId);
                  return novo;
                });

    TenantReactivationConfigEntity before = snapshot(entity);

    if (request.enabled != null) entity.setEnabled(request.enabled);
    if (request.abandonmentDelayMinutes != null && request.abandonmentDelayMinutes >= 15) {
      entity.setAbandonmentDelayMinutes(request.abandonmentDelayMinutes);
    }
    if (request.maxAttempts != null && request.maxAttempts >= 1 && request.maxAttempts <= 3) {
      entity.setMaxAttempts(request.maxAttempts);
    }
    if (request.attempt1DelayDays != null && request.attempt1DelayDays >= 1) {
      entity.setAttempt1DelayDays(request.attempt1DelayDays);
    }
    if (request.attempt2DelayDays != null && request.attempt2DelayDays >= 2) {
      entity.setAttempt2DelayDays(request.attempt2DelayDays);
    }
    if (request.attempt3DelayDays != null && request.attempt3DelayDays >= 3) {
      entity.setAttempt3DelayDays(request.attempt3DelayDays);
    }
    if (request.sendWindowStart != null) entity.setSendWindowStart(request.sendWindowStart);
    if (request.sendWindowEnd != null) entity.setSendWindowEnd(request.sendWindowEnd);
    // Limite LGPD: nao pode ultrapassar 4 mensagens/mes
    if (request.maxMessagesPerMonthPerClient != null) {
      entity.setMaxMessagesPerMonthPerClient(Math.min(request.maxMessagesPerMonthPerClient, 4));
    }
    // Limite LGPD: intervalo minimo de 7 dias
    if (request.minIntervalDays != null) {
      entity.setMinIntervalDays(Math.max(request.minIntervalDays, 7));
    }
    if (request.templateAttempt1 != null) {
      entity.setTemplateAttempt1(request.templateAttempt1.isBlank() ? null : request.templateAttempt1.trim());
    }
    if (request.templateAttempt2 != null) {
      entity.setTemplateAttempt2(request.templateAttempt2.isBlank() ? null : request.templateAttempt2.trim());
    }
    if (request.templateAttempt3 != null) {
      entity.setTemplateAttempt3(request.templateAttempt3.isBlank() ? null : request.templateAttempt3.trim());
    }

    TenantReactivationConfigEntity saved = configRepository.save(entity);

    AuditEventCommand audit = new AuditEventCommand();
    audit.tenantId = tenantId;
    audit.actorUserId = actorUserId;
    audit.actorRole = actorRole;
    audit.module = AuditConstants.Module.WHATSAPP;
    audit.action = AuditConstants.Action.REACTIVATION_CONFIG_UPDATED;
    audit.entityType = "TenantReactivationConfig";
    audit.entityId = saved.getId() != null ? saved.getId().toString() : null;
    audit.sourceChannel = AuditConstants.SourceChannel.API;
    audit.before = toAuditMap(before);
    audit.after = toAuditMap(saved);
    auditService.recordSuccess(audit);

    return toResponse(saved, tenantId);
  }

  @Transactional
  public void registrarOptOut(UUID tenantId, UUID clientId, UUID actorUserId, String actorRole) {
    Cliente client =
        clienteRepository
            .findByIdAndTenantId(clientId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente nao encontrado"));
    if (Boolean.TRUE.equals(client.getWhatsappOptOut())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cliente ja possui opt-out registrado");
    }

    Instant now = Instant.now();
    client.setWhatsappOptOut(true);
    client.setWhatsappOptOutAt(now);
    client.setWhatsappOptIn(false);
    clienteRepository.save(client);

    ReactivationConsentHistoryEntity consent = new ReactivationConsentHistoryEntity();
    consent.setTenantId(tenantId);
    consent.setClientId(clientId);
    consent.setAction("OPT_OUT");
    consent.setSource("OWNER");
    consent.setRegisteredBy(actorUserId);
    consentHistoryRepository.save(consent);

    // Cancelar ciclos ativos
    List<WhatsAppBookingReactivationCycleEntity> activeCycles =
        cycleRepository.listActiveByTenantAndClient(tenantId, clientId);
    for (WhatsAppBookingReactivationCycleEntity cycle : activeCycles) {
      reactivationService.cancelCycle(cycle, "OPT_OUT");
    }

    AuditEventCommand audit = new AuditEventCommand();
    audit.tenantId = tenantId;
    audit.actorUserId = actorUserId;
    audit.actorRole = actorRole;
    audit.module = AuditConstants.Module.WHATSAPP;
    audit.action = AuditConstants.Action.REACTIVATION_OPT_OUT;
    audit.entityType = "Cliente";
    audit.entityId = clientId.toString();
    audit.sourceChannel = AuditConstants.SourceChannel.API;
    auditService.recordSuccess(audit);

    LOG.info("Opt-out manual registrado: tenantId={} clientId={} actorId={}", tenantId, clientId, actorUserId);
  }

  @Transactional
  public void registrarOptIn(UUID tenantId, UUID clientId, UUID actorUserId, String actorRole) {
    Cliente client =
        clienteRepository
            .findByIdAndTenantId(clientId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente nao encontrado"));

    Instant now = Instant.now();
    client.setWhatsappOptIn(true);
    client.setWhatsappOptInAt(now);
    client.setWhatsappOptOut(false);
    client.setWhatsappOptOutAt(null);
    clienteRepository.save(client);

    ReactivationConsentHistoryEntity consent = new ReactivationConsentHistoryEntity();
    consent.setTenantId(tenantId);
    consent.setClientId(clientId);
    consent.setAction("OPT_IN");
    consent.setSource("OWNER");
    consent.setRegisteredBy(actorUserId);
    consentHistoryRepository.save(consent);

    AuditEventCommand audit = new AuditEventCommand();
    audit.tenantId = tenantId;
    audit.actorUserId = actorUserId;
    audit.actorRole = actorRole;
    audit.module = AuditConstants.Module.WHATSAPP;
    audit.action = AuditConstants.Action.REACTIVATION_OPT_IN;
    audit.entityType = "Cliente";
    audit.entityId = clientId.toString();
    audit.sourceChannel = AuditConstants.SourceChannel.API;
    auditService.recordSuccess(audit);

    LOG.info("Opt-in manual registrado: tenantId={} clientId={} actorId={}", tenantId, clientId, actorUserId);
  }

  @Transactional(readOnly = true)
  public OptOutPagedResponse listOptOutsPaged(UUID tenantId, int page, int size) {
    int normalizedPage = Math.max(page, 0);
    int normalizedSize = size <= 0 ? 20 : Math.min(size, 100);

    Page<Cliente> pageResult =
        clienteRepository.findByTenantIdAndWhatsappOptOutTrueOrderByWhatsappOptOutAtDesc(
            tenantId, PageRequest.of(normalizedPage, normalizedSize));
    List<Cliente> clientes = pageResult.getContent();
    long total = pageResult.getTotalElements();

    List<UUID> clientIds = clientes.stream().map(Cliente::getId).filter(Objects::nonNull).toList();
    Map<UUID, String> sourceMap = new HashMap<>();
    if (!clientIds.isEmpty()) {
      consentHistoryRepository
          .findByClientIdInAndTenantIdAndActionOrderByCreatedAtDesc(clientIds, tenantId, "OPT_OUT")
          .forEach(h -> sourceMap.putIfAbsent(h.getClientId(), h.getSource()));
    }

    List<OptOutPagedItem> items =
        clientes.stream()
            .map(
                c -> {
                  OptOutPagedItem item = new OptOutPagedItem();
                  item.clientId = c.getId();
                  item.clientNameMasked = maskName(c.getName());
                  item.optOutAt = c.getWhatsappOptOutAt();
                  item.source = sourceMap.getOrDefault(c.getId(), "OWNER");
                  return item;
                })
            .toList();

    int totalPages = normalizedSize > 0 ? (int) Math.ceil((double) total / normalizedSize) : 0;
    OptOutPagedResponse resp = new OptOutPagedResponse();
    resp.items = items;
    resp.totalItems = total;
    resp.page = normalizedPage;
    resp.pageSize = normalizedSize;
    resp.totalPages = totalPages;
    resp.hasMore = (normalizedPage + 1) < totalPages;
    return resp;
  }

  @Transactional(readOnly = true)
  public ReactivationCyclesPagedResponse listCycles(
      UUID tenantId, String statusStr, String from, String to, int page, int size) {
    int normalizedPage = Math.max(page, 0);
    int normalizedSize = size <= 0 ? 20 : Math.min(size, 50);

    WhatsAppBookingReactivationStatus status = null;
    if (statusStr != null && !statusStr.isBlank()) {
      try {
        status = WhatsAppBookingReactivationStatus.valueOf(statusStr.toUpperCase());
      } catch (IllegalArgumentException ignored) {
        // status desconhecido e ignorado em silencio, igual ao original
      }
    }
    Instant fromInstant = from != null && !from.isBlank() ? Instant.parse(from + "T00:00:00Z") : null;
    Instant toInstant = to != null && !to.isBlank() ? Instant.parse(to + "T23:59:59Z") : null;

    long total = cycleRepository.countOperational(tenantId, fromInstant, toInstant, status, null, null);
    List<WhatsAppBookingReactivationCycleEntity> cycles =
        cycleRepository.listOperational(
            tenantId, fromInstant, toInstant, status, null, null, normalizedPage, normalizedSize);

    List<CycleResponse> items =
        cycles.stream()
            .map(
                c -> {
                  CycleResponse r = new CycleResponse();
                  r.id = c.getId();
                  r.clientName = c.getCustomerName();
                  r.lastStage = c.getLastStage() != null ? c.getLastStage().name() : null;
                  r.status = c.getStatus() != null ? c.getStatus().name() : null;
                  r.nextAttemptNumber = c.getNextAttemptNumber();
                  r.nextAttemptAt = c.getNextAttemptAt();
                  r.abandonedAt = c.getAbandonedAt();
                  return r;
                })
            .toList();

    int totalPages = normalizedSize > 0 ? (int) Math.ceil((double) total / normalizedSize) : 0;
    ReactivationCyclesPagedResponse resp = new ReactivationCyclesPagedResponse();
    resp.items = items;
    resp.totalItems = total;
    resp.page = normalizedPage;
    resp.pageSize = normalizedSize;
    resp.totalPages = totalPages;
    resp.hasMore = (normalizedPage + 1) < totalPages;
    return resp;
  }

  @Transactional
  public void cancelCycleById(UUID tenantId, UUID cycleId) {
    WhatsAppBookingReactivationCycleEntity cycle =
        cycleRepository
            .findById(cycleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciclo nao encontrado"));
    if (!tenantId.equals(cycle.getTenantId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciclo nao encontrado");
    }
    if (cycle.getStatus() != WhatsAppBookingReactivationStatus.ACTIVE
        && cycle.getStatus() != WhatsAppBookingReactivationStatus.REACTIVATED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ciclo nao pode ser cancelado no status atual");
    }
    reactivationService.cancelCycle(cycle, "MANUAL_CANCEL");
  }

  private String maskName(String name) {
    if (name == null || name.isBlank()) return "****";
    String[] words = name.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.length; i++) {
      if (i > 0) sb.append(' ');
      String word = words[i];
      sb.append(word.charAt(0));
      if (word.length() > 1) sb.append("*".repeat(Math.min(word.length() - 1, 4)));
    }
    return sb.toString();
  }

  @Transactional(readOnly = true)
  public ReactivationMetricsResponse getMetrics(UUID tenantId, String period) {
    // period formato: "2026-06"
    YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now(ZONA_BR);
    Instant inicio = ym.atDay(1).atStartOfDay(ZONA_BR).toInstant();
    Instant fim = ym.atEndOfMonth().atTime(LocalTime.MAX).atZone(ZONA_BR).toInstant();

    List<WhatsAppBookingReactivationCycleEntity> cycles =
        cycleRepository.findByTenantIdAndCreatedAtBetween(tenantId, inicio, fim);

    long total = cycles.size();
    long reativados = cycles.stream().filter(c -> c.getReactivatedAt() != null).count();
    long convertidos =
        cycles.stream().filter(c -> c.getStatus() == WhatsAppBookingReactivationStatus.CONVERTED).count();
    long esgotados =
        cycles.stream().filter(c -> c.getStatus() == WhatsAppBookingReactivationStatus.EXHAUSTED).count();
    long optOuts = cycles.stream().filter(c -> "OPT_OUT".equals(c.getCancelReason())).count();

    Map<String, Long> byStage = new HashMap<>();
    for (WhatsAppBookingReactivationCycleEntity c : cycles) {
      if (c.getLastStage() != null) {
        byStage.merge(c.getLastStage().name(), 1L, Long::sum);
      }
    }

    ReactivationMetricsResponse metrics = new ReactivationMetricsResponse();
    metrics.period = ym.toString();
    metrics.totalCycles = total;
    metrics.reactivated = reativados;
    metrics.converted = convertidos;
    metrics.exhausted = esgotados;
    metrics.optedOut = optOuts;
    metrics.reactivationRate = total > 0 ? (double) reativados / total : 0.0;
    metrics.conversionRate = total > 0 ? (double) convertidos / total : 0.0;
    metrics.byStage = byStage;
    metrics.byAttempt = buildByAttempt(tenantId, inicio, fim);
    return metrics;
  }

  private Map<String, ReactivationMetricsResponse.AttemptMetrics> buildByAttempt(
      UUID tenantId, Instant inicio, Instant fim) {
    Map<String, ReactivationMetricsResponse.AttemptMetrics> map = new HashMap<>();
    for (int i = 1; i <= 3; i++) {
      long sent = sendLogRepository.countByTenantIdAndAttemptNumberAndSentAtBetween(tenantId, i, inicio, fim);
      // ciclos que foram reativados apos tentativa i (aproximacao via reactivatedAt dentro do periodo)
      long reativados =
          cycleRepository.countByTenantIdAndReactivatedAtBetweenAndNextAttemptNumberGreaterThan(
              tenantId, inicio, fim, i);
      ReactivationMetricsResponse.AttemptMetrics am = new ReactivationMetricsResponse.AttemptMetrics();
      am.sent = sent;
      am.reactivated = reativados;
      map.put(String.valueOf(i), am);
    }
    return map;
  }

  private TenantReactivationConfigResponse toResponse(TenantReactivationConfigEntity e, UUID tenantId) {
    TenantReactivationConfigResponse r = new TenantReactivationConfigResponse();
    r.tenantId = e.getTenantId() != null ? e.getTenantId() : tenantId;
    r.enabled = e.isEnabled();
    r.abandonmentDelayMinutes = e.getAbandonmentDelayMinutes();
    r.maxAttempts = e.getMaxAttempts();
    r.attempt1DelayDays = e.getAttempt1DelayDays();
    r.attempt2DelayDays = e.getAttempt2DelayDays();
    r.attempt3DelayDays = e.getAttempt3DelayDays();
    r.sendWindowStart = e.getSendWindowStart();
    r.sendWindowEnd = e.getSendWindowEnd();
    r.maxMessagesPerMonthPerClient = e.getMaxMessagesPerMonthPerClient();
    r.minIntervalDays = e.getMinIntervalDays();
    r.templateAttempt1 = e.getTemplateAttempt1();
    r.templateAttempt2 = e.getTemplateAttempt2();
    r.templateAttempt3 = e.getTemplateAttempt3();
    r.updatedAt = e.getUpdatedAt();
    return r;
  }

  private Map<String, Object> toAuditMap(TenantReactivationConfigEntity e) {
    if (e == null) return null;
    Map<String, Object> m = new HashMap<>();
    m.put("enabled", e.isEnabled());
    m.put("abandonmentDelayMinutes", e.getAbandonmentDelayMinutes());
    m.put("maxAttempts", e.getMaxAttempts());
    m.put("attempt1DelayDays", e.getAttempt1DelayDays());
    m.put("attempt2DelayDays", e.getAttempt2DelayDays());
    m.put("attempt3DelayDays", e.getAttempt3DelayDays());
    m.put("sendWindowStart", e.getSendWindowStart() != null ? e.getSendWindowStart().toString() : null);
    m.put("sendWindowEnd", e.getSendWindowEnd() != null ? e.getSendWindowEnd().toString() : null);
    m.put("maxMessagesPerMonthPerClient", e.getMaxMessagesPerMonthPerClient());
    m.put("minIntervalDays", e.getMinIntervalDays());
    return m;
  }

  private TenantReactivationConfigEntity snapshot(TenantReactivationConfigEntity e) {
    TenantReactivationConfigEntity s = new TenantReactivationConfigEntity();
    s.setId(e.getId());
    s.setTenantId(e.getTenantId());
    s.setEnabled(e.isEnabled());
    s.setAbandonmentDelayMinutes(e.getAbandonmentDelayMinutes());
    s.setMaxAttempts(e.getMaxAttempts());
    s.setAttempt1DelayDays(e.getAttempt1DelayDays());
    s.setAttempt2DelayDays(e.getAttempt2DelayDays());
    s.setAttempt3DelayDays(e.getAttempt3DelayDays());
    s.setSendWindowStart(e.getSendWindowStart());
    s.setSendWindowEnd(e.getSendWindowEnd());
    s.setMaxMessagesPerMonthPerClient(e.getMaxMessagesPerMonthPerClient());
    s.setMinIntervalDays(e.getMinIntervalDays());
    return s;
  }
}
