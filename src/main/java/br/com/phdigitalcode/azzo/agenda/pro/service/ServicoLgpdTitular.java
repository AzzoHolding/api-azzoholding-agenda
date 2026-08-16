package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.LgpdRequestDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequestEvent;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Espelha {@code modules/lgpd/application/ServicoLgpdTitular.java}. */
@Service
public class ServicoLgpdTitular {

  private static final String STATUS_ABERTO = "ABERTO";
  private static final String STATUS_EM_VALIDACAO = "EM_VALIDACAO";
  private static final String STATUS_RESPONDIDO = "RESPONDIDO";
  private static final String STATUS_ENCERRADO = "ENCERRADO";
  private static final long INITIAL_RESPONSE_SLA_DAYS = 15;
  private static final long FINAL_RESOLUTION_SLA_DAYS = 30;
  private static final Set<String> STATUSES =
      Set.of(STATUS_ABERTO, STATUS_EM_VALIDACAO, STATUS_RESPONDIDO, STATUS_ENCERRADO);

  private static final DateTimeFormatter PROTOCOL_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final UsuarioRepository usuarioRepository;
  private final LgpdDataSubjectRequestRepository requestRepository;
  private final LgpdDataSubjectRequestEventRepository eventRepository;
  private final AuditService auditService;

  public ServicoLgpdTitular(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      UsuarioRepository usuarioRepository,
      LgpdDataSubjectRequestRepository requestRepository,
      LgpdDataSubjectRequestEventRepository eventRepository,
      AuditService auditService) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.usuarioRepository = usuarioRepository;
    this.requestRepository = requestRepository;
    this.eventRepository = eventRepository;
    this.auditService = auditService;
  }

  @Transactional
  public LgpdRequestDtos.ItemResponse criar(LgpdRequestDtos.CreateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID actorUserId = obterUsuarioAutenticadoId();

    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setTenantId(tenantId);
    entity.setProtocolCode(gerarProtocolo(tenantId));
    entity.setRequestType(normalizar(request.requestType));
    entity.setStatus(STATUS_ABERTO);
    entity.setRequesterName(required(request.requesterName, "requesterName obrigatorio"));
    entity.setRequesterEmail(required(request.requesterEmail, "requesterEmail obrigatorio").toLowerCase(Locale.ROOT));
    entity.setRequesterDocument(optional(request.requesterDocument));
    entity.setDescription(optional(request.description));
    entity.setCreatedByUserId(actorUserId);
    requestRepository.save(entity);

    registrarEvento(entity, "REQUEST_CREATED", null, STATUS_ABERTO, "Solicitacao criada", actorUserId);
    auditar(tenantId, actorUserId, "LGPD_REQUEST_CREATE", null, toItemResponse(entity), entity.getId());
    return toItemResponse(entity);
  }

  @Transactional(readOnly = true)
  public List<LgpdRequestDtos.ItemResponse> listar(String status, String requestType, Integer limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String normalizedStatus = normalizeStatusNullable(status);
    String normalizedType = requestType == null || requestType.isBlank() ? null : requestType.trim().toUpperCase(Locale.ROOT);
    return requestRepository.listByTenant(tenantId, normalizedStatus, normalizedType, limit).stream()
        .map(this::toItemResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public LgpdRequestDtos.DetailResponse detalhar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LgpdDataSubjectRequest entity = requestRepository.findByTenantAndId(tenantId, id)
        .orElseThrow(() -> new IllegalArgumentException("Solicitacao LGPD nao encontrada"));

    LgpdRequestDtos.DetailResponse response = new LgpdRequestDtos.DetailResponse();
    response.request = toItemResponse(entity);
    response.events = eventRepository.listByRequestId(id).stream().map(this::toEventResponse).toList();
    return response;
  }

  @Transactional(readOnly = true)
  public LgpdRequestDtos.DetailResponse detalharPorProtocolo(String protocolCode) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String normalizedProtocol = required(protocolCode, "protocolCode obrigatorio").toUpperCase(Locale.ROOT);
    LgpdDataSubjectRequest entity = requestRepository.findByTenantAndProtocol(tenantId, normalizedProtocol)
        .orElseThrow(() -> new IllegalArgumentException("Solicitacao LGPD nao encontrada"));

    LgpdRequestDtos.DetailResponse response = new LgpdRequestDtos.DetailResponse();
    response.request = toItemResponse(entity);
    response.events = eventRepository.listByRequestId(entity.getId()).stream().map(this::toEventResponse).toList();
    return response;
  }

  @Transactional(readOnly = true)
  public LgpdRequestDtos.SummaryResponse resumirOperacao(Integer alertLimit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Instant now = Instant.now();
    Instant initialResponseCutoff = now.minus(INITIAL_RESPONSE_SLA_DAYS, ChronoUnit.DAYS);
    Instant finalResolutionCutoff = now.minus(FINAL_RESOLUTION_SLA_DAYS, ChronoUnit.DAYS);

    LgpdRequestDtos.SummaryResponse response = new LgpdRequestDtos.SummaryResponse();
    response.totalOpen = requestRepository.countByTenantAndStatus(tenantId, STATUS_ABERTO);
    response.totalInValidation = requestRepository.countByTenantAndStatus(tenantId, STATUS_EM_VALIDACAO);
    response.totalResponded = requestRepository.countByTenantAndStatus(tenantId, STATUS_RESPONDIDO);
    response.totalClosed = requestRepository.countByTenantAndStatus(tenantId, STATUS_ENCERRADO);
    response.unassignedActive = requestRepository.countUnassignedActive(tenantId);
    response.overdueInitialResponse = requestRepository.countOverdueInitialResponse(tenantId, initialResponseCutoff);
    response.overdueFinalResolution = requestRepository.countOverdueFinalResolution(tenantId, finalResolutionCutoff);

    List<LgpdDataSubjectRequest> alerts = requestRepository.listOperationalAlerts(tenantId, initialResponseCutoff, alertLimit);
    Map<UUID, String> assignedNames = usuarioRepository.mapNamesByTenantAndIds(
        tenantId,
        alerts.stream()
            .map(LgpdDataSubjectRequest::getAssignedToUserId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList());

    response.alerts = alerts.stream().map(item -> toOperationalAlert(item, assignedNames)).toList();
    return response;
  }

  @Transactional
  public LgpdRequestDtos.ItemResponse atualizarStatus(UUID id, LgpdRequestDtos.UpdateStatusRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID actorUserId = obterUsuarioAutenticadoId();

    LgpdDataSubjectRequest entity = requestRepository.findByTenantAndId(tenantId, id)
        .orElseThrow(() -> new IllegalArgumentException("Solicitacao LGPD nao encontrada"));

    String previousStatus = entity.getStatus() != null ? entity.getStatus().toUpperCase(Locale.ROOT) : STATUS_ABERTO;
    String newStatus = normalizeStatus(request.status);
    validarTransicao(previousStatus, newStatus);

    LgpdRequestDtos.ItemResponse before = toItemResponse(entity);
    entity.setStatus(newStatus);
    entity.setResponseSummary(optional(request.responseSummary));
    entity.setAssignedToUserId(parseUuidNullable(request.assignedToUserId));
    entity.setClosedAt(STATUS_ENCERRADO.equals(newStatus) ? Instant.now() : null);

    String eventNote = request.note == null || request.note.isBlank()
        ? "Status atualizado para " + newStatus
        : request.note.trim();
    registrarEvento(entity, "STATUS_CHANGED", previousStatus, newStatus, eventNote, actorUserId);

    LgpdRequestDtos.ItemResponse after = toItemResponse(entity);
    auditar(tenantId, actorUserId, "LGPD_REQUEST_STATUS_UPDATE", before, after, entity.getId());
    return after;
  }

  private void validarTransicao(String previousStatus, String newStatus) {
    if (previousStatus.equals(newStatus)) return;
    if (STATUS_ENCERRADO.equals(previousStatus)) {
      throw new IllegalArgumentException("Solicitacao encerrada nao pode mudar de status");
    }
    if (STATUS_ABERTO.equals(previousStatus)
        && (STATUS_EM_VALIDACAO.equals(newStatus)
            || STATUS_RESPONDIDO.equals(newStatus)
            || STATUS_ENCERRADO.equals(newStatus))) {
      return;
    }
    if (STATUS_EM_VALIDACAO.equals(previousStatus)
        && (STATUS_RESPONDIDO.equals(newStatus) || STATUS_ENCERRADO.equals(newStatus))) {
      return;
    }
    if (STATUS_RESPONDIDO.equals(previousStatus) && STATUS_ENCERRADO.equals(newStatus)) {
      return;
    }
    throw new IllegalArgumentException("Transicao de status LGPD invalida");
  }

  private void registrarEvento(
      LgpdDataSubjectRequest request,
      String eventType,
      String previousStatus,
      String newStatus,
      String note,
      UUID actorUserId) {
    LgpdDataSubjectRequestEvent event = new LgpdDataSubjectRequestEvent();
    event.setTenantId(request.getTenantId());
    event.setRequestId(request.getId());
    event.setEventType(eventType);
    event.setPreviousStatus(previousStatus);
    event.setNewStatus(newStatus);
    event.setEventNote(note);
    event.setActorUserId(actorUserId);
    eventRepository.save(event);
  }

  private void auditar(UUID tenantId, UUID actorUserId, String action, Object before, Object after, UUID requestId) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = actorUserId;
      command.module = AuditConstants.Module.LGPD;
      command.action = action;
      command.entityType = "LGPD_REQUEST";
      command.entityId = requestId != null ? requestId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      command.metadata = Map.of("compliance", "LGPD");
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve quebrar o fluxo de atendimento LGPD.
    }
  }

  private String gerarProtocolo(UUID tenantId) {
    String date = PROTOCOL_DATE_FORMAT.format(Instant.now());
    for (int i = 0; i < 5; i++) {
      String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
      String protocol = "LGPD-" + date + "-" + suffix;
      if (requestRepository.findByTenantAndProtocol(tenantId, protocol).isEmpty()) return protocol;
    }
    return "LGPD-" + date + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private String normalizeStatusNullable(String status) {
    if (status == null || status.isBlank()) return null;
    return normalizeStatus(status);
  }

  private String normalizeStatus(String status) {
    String value = required(status, "status obrigatorio").toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(value)) {
      throw new IllegalArgumentException("Status LGPD invalido");
    }
    return value;
  }

  private UUID parseUuidNullable(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("assignedToUserId invalido");
    }
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }

  private String optional(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  private String normalizar(String value) {
    return required(value, "requestType obrigatorio").toUpperCase(Locale.ROOT);
  }

  /**
   * Espelha {@code obterUsuarioAutenticadoId()} original: alem de extrair o {@code sub} do JWT,
   * reconfirma que o usuario ainda existe em {@code usuarioRepository} — nunca lanca, devolve
   * {@code null} em qualquer falha (sem token, subject invalido, usuario nao encontrado).
   */
  private UUID obterUsuarioAutenticadoId() {
    try {
      UUID userId = authenticatedUser.idOuNulo();
      if (userId == null) return null;
      return usuarioRepository.findById(userId).isPresent() ? userId : null;
    } catch (Exception e) {
      return null;
    }
  }

  private LgpdRequestDtos.ItemResponse toItemResponse(LgpdDataSubjectRequest entity) {
    LgpdRequestDtos.ItemResponse response = new LgpdRequestDtos.ItemResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.tenantId = entity.getTenantId() != null ? entity.getTenantId().toString() : null;
    response.protocolCode = entity.getProtocolCode();
    response.requestType = entity.getRequestType();
    response.status = entity.getStatus();
    response.requesterName = entity.getRequesterName();
    response.requesterEmail = entity.getRequesterEmail();
    response.requesterDocument = entity.getRequesterDocument();
    response.description = entity.getDescription();
    response.responseSummary = entity.getResponseSummary();
    response.assignedToUserId = entity.getAssignedToUserId() != null ? entity.getAssignedToUserId().toString() : null;
    response.createdByUserId = entity.getCreatedByUserId() != null ? entity.getCreatedByUserId().toString() : null;
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    response.closedAt = entity.getClosedAt() != null ? entity.getClosedAt().toString() : null;
    return response;
  }

  private LgpdRequestDtos.EventResponse toEventResponse(LgpdDataSubjectRequestEvent event) {
    LgpdRequestDtos.EventResponse response = new LgpdRequestDtos.EventResponse();
    response.id = event.getId() != null ? event.getId().toString() : null;
    response.requestId = event.getRequestId() != null ? event.getRequestId().toString() : null;
    response.eventType = event.getEventType();
    response.previousStatus = event.getPreviousStatus();
    response.newStatus = event.getNewStatus();
    response.note = event.getEventNote();
    response.actorUserId = event.getActorUserId() != null ? event.getActorUserId().toString() : null;
    response.createdAt = event.getCreatedAt() != null ? event.getCreatedAt().toString() : null;
    return response;
  }

  private LgpdRequestDtos.OperationalAlertItem toOperationalAlert(
      LgpdDataSubjectRequest entity, Map<UUID, String> assignedNames) {
    LgpdRequestDtos.OperationalAlertItem item = new LgpdRequestDtos.OperationalAlertItem();
    item.id = entity.getId() != null ? entity.getId().toString() : null;
    item.protocolCode = entity.getProtocolCode();
    item.requestType = entity.getRequestType();
    item.status = entity.getStatus();
    item.requesterName = entity.getRequesterName();
    item.requesterEmail = entity.getRequesterEmail();
    item.assignedToUserId = entity.getAssignedToUserId() != null ? entity.getAssignedToUserId().toString() : null;
    item.assignedToUserName = entity.getAssignedToUserId() != null ? assignedNames.get(entity.getAssignedToUserId()) : null;
    item.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    item.ageDays = entity.getCreatedAt() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(entity.getCreatedAt(), Instant.now()));
    item.overdueType = classifyOverdueType(item.ageDays);
    return item;
  }

  private String classifyOverdueType(long ageDays) {
    if (ageDays > FINAL_RESOLUTION_SLA_DAYS) return "FINAL_RESOLUTION";
    if (ageDays > INITIAL_RESPONSE_SLA_DAYS) return "INITIAL_RESPONSE";
    return "NONE";
  }
}
