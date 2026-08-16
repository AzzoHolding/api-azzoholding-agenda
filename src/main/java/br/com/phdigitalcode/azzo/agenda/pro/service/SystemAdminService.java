package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SystemAdminDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailTemplateConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FeedbackSuggestion;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProductCapability;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailTemplateConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FeedbackSuggestionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductCapabilityRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RefreshTokenRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/** Espelha {@code modules/admin/application/SystemAdminService.java}. */
@Service
public class SystemAdminService {

  @PersistenceContext
  private EntityManager entityManager;

  private final RefreshTokenRepository refreshTokenRepository;
  private final UsuarioRepository usuarioRepository;
  private final TenantRepository tenantRepository;
  private final ProductRepository productRepository;
  private final ProductCapabilityRepository productCapabilityRepository;
  private final FeedbackSuggestionRepository feedbackSuggestionRepository;
  private final AuditService auditService;
  private final AuthenticatedUser authenticatedUser;
  private final EmailTemplateConfigRepository emailTemplateConfigRepository;
  private final EmailTemplateRendererService emailTemplateRendererService;

  public SystemAdminService(
      RefreshTokenRepository refreshTokenRepository,
      UsuarioRepository usuarioRepository,
      TenantRepository tenantRepository,
      ProductRepository productRepository,
      ProductCapabilityRepository productCapabilityRepository,
      FeedbackSuggestionRepository feedbackSuggestionRepository,
      AuditService auditService,
      AuthenticatedUser authenticatedUser,
      EmailTemplateConfigRepository emailTemplateConfigRepository,
      EmailTemplateRendererService emailTemplateRendererService) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.usuarioRepository = usuarioRepository;
    this.tenantRepository = tenantRepository;
    this.productRepository = productRepository;
    this.productCapabilityRepository = productCapabilityRepository;
    this.feedbackSuggestionRepository = feedbackSuggestionRepository;
    this.auditService = auditService;
    this.authenticatedUser = authenticatedUser;
    this.emailTemplateConfigRepository = emailTemplateConfigRepository;
    this.emailTemplateRendererService = emailTemplateRendererService;
  }

  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public SystemAdminDtos.CommercialOverviewResponse commercialOverview() {
    SystemAdminDtos.CommercialOverviewResponse response = new SystemAdminDtos.CommercialOverviewResponse();

    response.totalTenants = asLong(entityManager.createNativeQuery("SELECT COUNT(*) FROM tenants").getSingleResult());
    response.totalSignups30d = asLong(entityManager.createNativeQuery("""
        SELECT COUNT(*)
        FROM tenants
        WHERE created_at >= (NOW() - INTERVAL '30 days')
        """).getSingleResult());

    response.payingTenants = asLong(entityManager.createNativeQuery("""
        SELECT COUNT(DISTINCT tenant_id)
        FROM payments
        WHERE status IN ('RECEIVED', 'CONFIRMED')
        """).getSingleResult());

    response.activeTenants = countTenantsByPlanStatus("ACTIVE");
    response.expiredTenants = countTenantsByPlanStatus("EXPIRED");
    response.suspendedTenants = countTenantsByPlanStatus("SUSPENDED");

    response.revenueReceived30d = NumericUtil.fromCents(asLong(entityManager.createNativeQuery("""
        SELECT COALESCE(SUM(amount_cents), 0)
        FROM payments
        WHERE status IN ('RECEIVED', 'CONFIRMED')
          AND paid_at >= (NOW() - INTERVAL '30 days')
        """).getSingleResult()));

    response.pendingAmount = NumericUtil.fromCents(asLong(entityManager.createNativeQuery("""
        SELECT COALESCE(SUM(amount_cents), 0)
        FROM payments
        WHERE status = 'PENDING'
        """).getSingleResult()));

    if (response.totalTenants > 0) {
      response.conversionRatePercent = (response.payingTenants * 100.0d) / response.totalTenants;
    } else {
      response.conversionRatePercent = 0.0d;
    }

    List<Object[]> statusRows = entityManager.createNativeQuery("""
        SELECT ps.code, COUNT(*)
        FROM tenants t
        JOIN plan_status ps ON ps.id = t.plan_status_id
        GROUP BY ps.code
        ORDER BY COUNT(*) DESC, ps.code ASC
        """).getResultList();

    response.tenantsByPlanStatus = new ArrayList<>();
    for (Object[] row : statusRows) {
      SystemAdminDtos.TenantPlanStatusItem item = new SystemAdminDtos.TenantPlanStatusItem();
      item.planStatus = row[0] != null ? row[0].toString() : "UNKNOWN";
      item.count = asLong(row[1]);
      response.tenantsByPlanStatus.add(item);
    }

    return response;
  }

  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public SystemAdminDtos.GlobalAuditListResponse listGlobalAudits(
      String from,
      String to,
      String tenantId,
      String module,
      String action,
      String status,
      String sourceChannel,
      String entityType,
      String actorUserId,
      String requestId,
      String text,
      Integer limit) {
    int lim = sanitizeLimit(limit);
    StringBuilder sql = new StringBuilder("""
        SELECT
          ae.id,
          ae.tenant_id,
          t.name AS tenant_name,
          ae.actor_user_id,
          ae.actor_role,
          ae.module,
          ae.action,
          ae.entity_type,
          ae.entity_id,
          ae.status,
          ae.error_code,
          ae.request_id,
          ae.source_channel,
          ae.ip_address,
          ae.created_at
        FROM audit_events ae
        LEFT JOIN tenants t ON t.id = ae.tenant_id
        WHERE 1=1
        """);

    if (from != null && !from.isBlank()) sql.append(" AND ae.created_at >= :from");
    if (to != null && !to.isBlank()) sql.append(" AND ae.created_at <= :to");
    if (tenantId != null && !tenantId.isBlank()) sql.append(" AND CAST(ae.tenant_id AS TEXT) = :tenantId");
    if (module != null && !module.isBlank()) sql.append(" AND UPPER(ae.module) = :module");
    if (action != null && !action.isBlank()) sql.append(" AND UPPER(ae.action) = :action");
    if (status != null && !status.isBlank()) sql.append(" AND UPPER(ae.status) = :status");
    if (sourceChannel != null && !sourceChannel.isBlank()) sql.append(" AND UPPER(ae.source_channel) = :sourceChannel");
    if (entityType != null && !entityType.isBlank()) sql.append(" AND UPPER(COALESCE(ae.entity_type,'')) = :entityType");
    if (actorUserId != null && !actorUserId.isBlank()) sql.append(" AND CAST(ae.actor_user_id AS TEXT) = :actorUserId");
    if (requestId != null && !requestId.isBlank()) sql.append(" AND ae.request_id = :requestId");
    if (text != null && !text.isBlank()) {
      sql.append(" AND (UPPER(COALESCE(ae.action,'')) LIKE :text");
      sql.append(" OR UPPER(COALESCE(ae.entity_type,'')) LIKE :text");
      sql.append(" OR UPPER(COALESCE(ae.request_id,'')) LIKE :text");
      sql.append(" OR UPPER(COALESCE(ae.error_code,'')) LIKE :text");
      sql.append(" OR UPPER(COALESCE(t.name,'')) LIKE :text)");
    }

    sql.append(" ORDER BY ae.created_at DESC, ae.id DESC LIMIT :limit");

    Query query = entityManager.createNativeQuery(sql.toString());
    if (from != null && !from.isBlank()) query.setParameter("from", Instant.parse(from.trim()));
    if (to != null && !to.isBlank()) query.setParameter("to", Instant.parse(to.trim()));
    if (tenantId != null && !tenantId.isBlank()) query.setParameter("tenantId", tenantId.trim());
    if (module != null && !module.isBlank()) query.setParameter("module", module.trim().toUpperCase());
    if (action != null && !action.isBlank()) query.setParameter("action", action.trim().toUpperCase());
    if (status != null && !status.isBlank()) query.setParameter("status", status.trim().toUpperCase());
    if (sourceChannel != null && !sourceChannel.isBlank()) query.setParameter("sourceChannel", sourceChannel.trim().toUpperCase());
    if (entityType != null && !entityType.isBlank()) query.setParameter("entityType", entityType.trim().toUpperCase());
    if (actorUserId != null && !actorUserId.isBlank()) query.setParameter("actorUserId", actorUserId.trim());
    if (requestId != null && !requestId.isBlank()) query.setParameter("requestId", requestId.trim());
    if (text != null && !text.isBlank()) query.setParameter("text", "%" + text.trim().toUpperCase() + "%");
    query.setParameter("limit", lim);

    List<Object[]> rows = query.getResultList();

    SystemAdminDtos.GlobalAuditListResponse response = new SystemAdminDtos.GlobalAuditListResponse();
    response.limit = lim;
    response.items = new ArrayList<>();
    for (Object[] row : rows) {
      SystemAdminDtos.GlobalAuditItem item = new SystemAdminDtos.GlobalAuditItem();
      item.id = toString(row[0]);
      item.tenantId = toString(row[1]);
      item.tenantName = toString(row[2]);
      item.actorUserId = toString(row[3]);
      item.actorRole = toString(row[4]);
      item.module = toString(row[5]);
      item.action = toString(row[6]);
      item.entityType = toString(row[7]);
      item.entityId = toString(row[8]);
      item.status = toString(row[9]);
      item.errorCode = toString(row[10]);
      item.requestId = toString(row[11]);
      item.sourceChannel = toString(row[12]);
      item.ipAddress = toString(row[13]);
      item.createdAt = toInstantString(row[14]);
      response.items.add(item);
    }
    return response;
  }

  @Transactional(readOnly = true)
  public SystemAdminDtos.GlobalAuditDetailResponse getGlobalAuditDetail(String id) {
    UUID eventId;
    try {
      eventId = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("id de auditoria invalido.");
    }

    Object[] row = (Object[]) entityManager.createNativeQuery("""
        SELECT
          ae.id,
          ae.tenant_id,
          t.name AS tenant_name,
          ae.actor_user_id,
          ae.actor_role,
          ae.module,
          ae.action,
          ae.entity_type,
          ae.entity_id,
          ae.status,
          ae.error_code,
          ae.request_id,
          ae.source_channel,
          ae.ip_address,
          ae.created_at,
          ae.error_message,
          ae.user_agent,
          ae.before_json,
          ae.after_json,
          ae.metadata_json,
          ae.has_changes,
          ae.changed_fields_json,
          ae.event_hash,
          ae.prev_event_hash
        FROM audit_events ae
        LEFT JOIN tenants t ON t.id = ae.tenant_id
        WHERE ae.id = :id
        LIMIT 1
        """)
        .setParameter("id", eventId)
        .getResultStream()
        .findFirst()
        .orElse(null);

    if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento de auditoria nao encontrado.");

    SystemAdminDtos.GlobalAuditDetailResponse item = new SystemAdminDtos.GlobalAuditDetailResponse();
    item.id = toString(row[0]);
    item.tenantId = toString(row[1]);
    item.tenantName = toString(row[2]);
    item.actorUserId = toString(row[3]);
    item.actorRole = toString(row[4]);
    item.module = toString(row[5]);
    item.action = toString(row[6]);
    item.entityType = toString(row[7]);
    item.entityId = toString(row[8]);
    item.status = toString(row[9]);
    item.errorCode = toString(row[10]);
    item.requestId = toString(row[11]);
    item.sourceChannel = toString(row[12]);
    item.ipAddress = toString(row[13]);
    item.createdAt = toInstantString(row[14]);
    item.errorMessage = toString(row[15]);
    item.userAgent = toString(row[16]);
    item.beforeJson = toString(row[17]);
    item.afterJson = toString(row[18]);
    item.metadataJson = toString(row[19]);
    item.hasChanges = row[20] instanceof Boolean b ? b : false;
    item.changedFieldsJson = toString(row[21]);
    item.eventHash = toString(row[22]);
    item.prevEventHash = toString(row[23]);
    return item;
  }

  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public SystemAdminDtos.GlobalSuggestionListResponse listGlobalSuggestions(
      String tenantId, String status, String category, String text, Integer limit) {
    int lim = sanitizeLimit(limit);

    UUID tenantUuid = parseUuidOrNull(tenantId);
    if (tenantId != null && !tenantId.isBlank() && tenantUuid == null) {
      throw new IllegalArgumentException("tenantId invalido.");
    }

    StringBuilder sql = new StringBuilder("""
        SELECT
          fs.id,
          fs.tenant_id,
          t.name AS tenant_name,
          fs.user_id,
          fs.user_name,
          fs.user_role,
          fs.category,
          fs.title,
          fs.message,
          fs.status,
          fs.admin_response,
          fs.responded_by_user_id,
          fs.responded_by_user_name,
          fs.responded_at,
          fs.closed_at,
          fs.source_page,
          fs.created_at,
          fs.updated_at
        FROM feedback_suggestions fs
        JOIN tenants t ON t.id = fs.tenant_id
        WHERE 1=1
        """);
    if (tenantUuid != null) sql.append(" AND fs.tenant_id = :tenantId");
    if (status != null && !status.isBlank()) sql.append(" AND UPPER(fs.status) = :status");
    if (category != null && !category.isBlank()) sql.append(" AND UPPER(fs.category) = :category");
    if (text != null && !text.isBlank()) {
      sql.append(" AND (UPPER(COALESCE(fs.title,'')) LIKE :text");
      sql.append(" OR UPPER(COALESCE(fs.message,'')) LIKE :text");
      sql.append(" OR UPPER(COALESCE(fs.user_name,'')) LIKE :text");
      sql.append(" OR UPPER(COALESCE(t.name,'')) LIKE :text)");
    }
    sql.append(" ORDER BY fs.created_at DESC, fs.id DESC LIMIT :limit");

    Query query = entityManager.createNativeQuery(sql.toString());
    if (tenantUuid != null) query.setParameter("tenantId", tenantUuid);
    if (status != null && !status.isBlank()) query.setParameter("status", status.trim().toUpperCase());
    if (category != null && !category.isBlank()) query.setParameter("category", category.trim().toUpperCase());
    if (text != null && !text.isBlank()) query.setParameter("text", "%" + text.trim().toUpperCase() + "%");
    query.setParameter("limit", lim);

    List<Object[]> rows = query.getResultList();
    SystemAdminDtos.GlobalSuggestionListResponse response = new SystemAdminDtos.GlobalSuggestionListResponse();
    response.limit = lim;
    response.items = new ArrayList<>();
    for (Object[] row : rows) {
      SystemAdminDtos.GlobalSuggestionItem item = new SystemAdminDtos.GlobalSuggestionItem();
      item.id = toString(row[0]);
      item.tenantId = toString(row[1]);
      item.tenantName = toString(row[2]);
      item.userId = toString(row[3]);
      item.userName = toString(row[4]);
      item.userRole = toString(row[5]);
      item.category = toString(row[6]);
      item.title = toString(row[7]);
      item.message = toString(row[8]);
      item.status = toString(row[9]);
      item.adminResponse = toString(row[10]);
      item.respondedByUserId = toString(row[11]);
      item.respondedByUserName = toString(row[12]);
      item.respondedAt = toInstantString(row[13]);
      item.closedAt = toInstantString(row[14]);
      item.sourcePage = toString(row[15]);
      item.createdAt = toInstantString(row[16]);
      item.updatedAt = toInstantString(row[17]);
      response.items.add(item);
    }
    return response;
  }

  @Transactional(readOnly = true)
  public SystemAdminDtos.GlobalSuggestionItem getGlobalSuggestionDetail(String id) {
    UUID suggestionId = parseUuidOrNull(id);
    if (suggestionId == null) throw new IllegalArgumentException("id de sugestao invalido.");
    FeedbackSuggestion entity = feedbackSuggestionRepository.findById(suggestionId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sugestao nao encontrada."));
    String tenantName = (String) entityManager.createNativeQuery("SELECT name FROM tenants WHERE id = :tenantId")
        .setParameter("tenantId", entity.getTenantId())
        .getResultStream()
        .findFirst()
        .orElse(null);
    return toGlobalSuggestionItem(entity, tenantName);
  }

  @Transactional
  public SystemAdminDtos.GlobalSuggestionItem updateGlobalSuggestion(String id, SystemAdminDtos.SuggestionUpdateRequest request) {
    UUID suggestionId = parseUuidOrNull(id);
    if (suggestionId == null) throw new IllegalArgumentException("id de sugestao invalido.");

    FeedbackSuggestion entity = feedbackSuggestionRepository.findById(suggestionId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sugestao nao encontrada."));

    String nextStatus = normalizeSuggestionStatus(request != null ? request.status : null, entity.getStatus());
    String normalizedResponse = normalizeOptional(request != null ? request.adminResponse : null);

    if (normalizedResponse == null && nextStatus.equals(entity.getStatus())) {
      throw new IllegalArgumentException("Informe uma resposta ou altere o status da sugestao.");
    }

    if (normalizedResponse != null) {
      entity.setAdminResponse(normalizedResponse);
      entity.setRespondedAt(Instant.now());
      entity.setRespondedByUserId(authenticatedUser.idOuNulo());
      entity.setRespondedByUserName(authenticatedUser.nomeOuAdmin());
    }

    entity.setStatus(nextStatus);
    if ("CLOSED".equals(nextStatus)) {
      entity.setClosedAt(Instant.now());
    } else if ("OPEN".equals(nextStatus)) {
      entity.setClosedAt(null);
    }
    feedbackSuggestionRepository.save(entity);

    String tenantName = (String) entityManager.createNativeQuery("SELECT name FROM tenants WHERE id = :tenantId")
        .setParameter("tenantId", entity.getTenantId())
        .getResultStream()
        .findFirst()
        .orElse(null);
    return toGlobalSuggestionItem(entity, tenantName);
  }

  @Transactional
  public SystemAdminDtos.RevokeSessionsResponse revokeSessions(SystemAdminDtos.RevokeSessionsRequest request) {
    String tenantIdRaw = request != null ? request.tenantId : null;
    String userIdRaw = request != null ? request.userId : null;
    UUID tenantId = parseUuidOrNull(tenantIdRaw);
    UUID userId = parseUuidOrNull(userIdRaw);

    if (tenantId == null && userId == null) {
      throw new IllegalArgumentException("Informe tenantId ou userId para revogar sessao.");
    }

    if (tenantId != null && tenantRepository.findById(tenantId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant nao encontrado.");
    }

    long revoked;
    if (userId != null) {
      Usuario user = usuarioRepository.findById(userId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado."));
      if (tenantId != null && user.getTenantId() != null && !tenantId.equals(user.getTenantId())) {
        throw new IllegalArgumentException("userId nao pertence ao tenant informado.");
      }
      revoked = refreshTokenRepository.revokeAllByUser(userId, Instant.now());
      tenantId = user.getTenantId();
    } else {
      revoked = refreshTokenRepository.revokeAllByTenant(tenantId, Instant.now());
    }

    SystemAdminDtos.RevokeSessionsResponse response = new SystemAdminDtos.RevokeSessionsResponse();
    response.status = "OK";
    response.message = "Sessoes revogadas com sucesso (refresh tokens).";
    response.tenantId = tenantId != null ? tenantId.toString() : null;
    response.userId = userId != null ? userId.toString() : null;
    response.revokedCount = revoked;
    return response;
  }

  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public SystemAdminDtos.SessionListResponse listSessions(String tenantId, Boolean includeRevoked, Integer limit) {
    int lim = sanitizeLimit(limit);
    boolean includeRevokedValue = includeRevoked != null && includeRevoked;

    UUID tenantUuid = parseUuidOrNull(tenantId);
    if (tenantId != null && !tenantId.isBlank() && tenantUuid == null) {
      throw new IllegalArgumentException("tenantId invalido.");
    }

    StringBuilder sql = new StringBuilder("""
        SELECT
          rt.id,
          rt.tenant_id,
          t.name AS tenant_name,
          rt.user_id,
          u.name AS user_name,
          u.email AS user_email,
          u.role AS user_role,
          rt.created_at,
          rt.expires_at,
          rt.revoked_at
        FROM refresh_tokens rt
        JOIN users u ON u.id = rt.user_id
        JOIN tenants t ON t.id = rt.tenant_id
        WHERE 1=1
        """);
    if (tenantUuid != null) sql.append(" AND rt.tenant_id = :tenantId");
    if (!includeRevokedValue) sql.append(" AND rt.revoked_at IS NULL AND rt.expires_at > NOW()");
    sql.append(" ORDER BY rt.created_at DESC LIMIT :limit");

    Query query = entityManager.createNativeQuery(sql.toString());
    if (tenantUuid != null) query.setParameter("tenantId", tenantUuid);
    query.setParameter("limit", lim);

    List<Object[]> rows = query.getResultList();
    SystemAdminDtos.SessionListResponse response = new SystemAdminDtos.SessionListResponse();
    response.limit = lim;
    response.items = new ArrayList<>();
    for (Object[] row : rows) {
      SystemAdminDtos.SessionItem item = new SystemAdminDtos.SessionItem();
      item.refreshTokenId = toString(row[0]);
      item.tenantId = toString(row[1]);
      item.tenantName = toString(row[2]);
      item.userId = toString(row[3]);
      item.userName = toString(row[4]);
      item.userEmail = toString(row[5]);
      item.userRole = toString(row[6]);
      item.createdAt = toInstantString(row[7]);
      item.expiresAt = toInstantString(row[8]);
      item.revokedAt = toInstantString(row[9]);
      Instant expiresAt = toInstant(row[8]);
      Instant revokedAt = toInstant(row[9]);
      item.active = revokedAt == null && expiresAt != null && expiresAt.isAfter(Instant.now());
      response.items.add(item);
    }
    return response;
  }

  @Transactional
  public SystemAdminDtos.RevokeSessionsResponse revokeSessionToken(SystemAdminDtos.RevokeSessionTokenRequest request) {
    String refreshTokenIdRaw = request != null ? request.refreshTokenId : null;
    UUID refreshTokenId = parseUuidOrNull(refreshTokenIdRaw);
    if (refreshTokenId == null) throw new IllegalArgumentException("refreshTokenId invalido.");

    UUID tenantId = parseUuidOrNull(request != null ? request.tenantId : null);
    if (tenantId != null && tenantRepository.findById(tenantId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant nao encontrado.");
    }

    int updated = entityManager.createNativeQuery("""
        UPDATE refresh_tokens
        SET revoked_at = NOW()
        WHERE id = :id
          AND revoked_at IS NULL
          AND (:tenantId IS NULL OR tenant_id = :tenantId)
        """)
        .setParameter("id", refreshTokenId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();

    SystemAdminDtos.RevokeSessionsResponse response = new SystemAdminDtos.RevokeSessionsResponse();
    response.status = "OK";
    response.message = updated > 0 ? "Sessao revogada com sucesso." : "Nenhuma sessao ativa encontrada para revogar.";
    response.tenantId = tenantId != null ? tenantId.toString() : null;
    response.userId = null;
    response.revokedCount = updated;
    return response;
  }

  @Transactional
  public SystemAdminDtos.MfaResetResponse resetUserMfa(String userId, SystemAdminDtos.MfaResetRequest request) {
    UUID userUuid = parseUuidOrNull(userId);
    if (userUuid == null) throw new IllegalArgumentException("userId invalido.");

    Usuario user = usuarioRepository.findById(userUuid)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado."));

    UUID tenantId = parseUuidOrNull(request != null ? request.tenantId : null);
    if (tenantId != null && !tenantId.equals(user.getTenantId())) {
      throw new IllegalArgumentException("userId nao pertence ao tenant informado.");
    }

    String reason = normalizeOptional(request != null ? request.reason : null);
    if (reason == null) {
      throw new IllegalArgumentException("reason obrigatorio para reset de MFA.");
    }

    boolean revokeSessions = request == null || request.revokeSessions == null || request.revokeSessions;
    boolean wasEnabled = user.isMfaEnabled();
    boolean wasEnrolled = user.getMfaSecretEnc() != null && !user.getMfaSecretEnc().isBlank();

    user.setMfaEnabled(false);
    user.setMfaSecretEnc(null);
    usuarioRepository.save(user);

    long revokedCount = revokeSessions ? refreshTokenRepository.revokeAllByUser(user.getId(), Instant.now()) : 0L;
    auditAdminMfaReset(user, reason, revokedCount, wasEnabled, wasEnrolled);

    SystemAdminDtos.MfaResetResponse response = new SystemAdminDtos.MfaResetResponse();
    response.status = "OK";
    response.message = wasEnabled || wasEnrolled
        ? "MFA resetado com sucesso."
        : "Usuario nao possuia MFA ativo/configurado, mas as sessoes foram tratadas conforme solicitado.";
    response.tenantId = user.getTenantId() != null ? user.getTenantId().toString() : null;
    response.userId = user.getId() != null ? user.getId().toString() : null;
    response.mfaWasEnabled = wasEnabled;
    response.mfaWasEnrolled = wasEnrolled;
    response.revokedCount = revokedCount;
    return response;
  }

  @Transactional(readOnly = true)
  public SystemAdminDtos.PlanListResponse listPlans() {
    SystemAdminDtos.PlanListResponse response = new SystemAdminDtos.PlanListResponse();
    response.items = productRepository.listarTodosOrdenadosParaAdmin().stream().map(this::toPlanItem).toList();
    return response;
  }

  @Transactional
  public SystemAdminDtos.PlanItem createPlan(SystemAdminDtos.PlanUpsertRequest request) {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    applyPlanRequest(product, request);
    productRepository.save(product);
    upsertPlanCapability(product.getId(), request != null ? request.maxProfessionals : null);
    enforceSingleActiveTrial(product.getId(), product.isTrial(), product.isActive());
    recordPlanAudit("ADMIN_PLAN_CREATE", product);
    return toPlanItem(productRepository.findById(product.getId()).orElseThrow());
  }

  @Transactional
  public SystemAdminDtos.PlanItem updatePlan(String planId, SystemAdminDtos.PlanUpsertRequest request) {
    Product product = requirePlan(planId);
    applyPlanRequest(product, request);
    productRepository.save(product);
    upsertPlanCapability(product.getId(), request != null ? request.maxProfessionals : null);
    enforceSingleActiveTrial(product.getId(), product.isTrial(), product.isActive());
    recordPlanAudit("ADMIN_PLAN_UPDATE", product);
    return toPlanItem(product);
  }

  @Transactional
  public SystemAdminDtos.PlanItem updatePlanStatus(String planId, SystemAdminDtos.PlanStatusUpdateRequest request) {
    if (request == null || request.active == null) {
      throw new IllegalArgumentException("active obrigatorio");
    }
    Product product = requirePlan(planId);
    product.setActive(Boolean.TRUE.equals(request.active));
    productRepository.save(product);
    enforceSingleActiveTrial(product.getId(), product.isTrial(), product.isActive());
    recordPlanAudit("ADMIN_PLAN_STATUS_UPDATE", product);
    return toPlanItem(product);
  }

  @Transactional(readOnly = true)
  public SystemAdminDtos.EmailTemplateListResponse listEmailTemplates() {
    Map<EmailTemplateType, EmailTemplateConfig> byType = new LinkedHashMap<>();
    for (EmailTemplateConfig config : emailTemplateConfigRepository.findAllGlobals()) {
      byType.put(config.getTemplateType(), config);
    }

    SystemAdminDtos.EmailTemplateListResponse response = new SystemAdminDtos.EmailTemplateListResponse();
    response.items = new ArrayList<>();
    for (EmailTemplateRendererService.TemplateDefinition definition : emailTemplateRendererService.definitions()) {
      SystemAdminDtos.EmailTemplateSummaryItem item = new SystemAdminDtos.EmailTemplateSummaryItem();
      EmailTemplateConfig config = byType.get(definition.type());
      item.templateType = definition.type().name();
      item.label = definition.label();
      item.configured = config != null;
      item.active = config != null && config.isActive();
      item.updatedAt = config != null ? toInstantString(config.getUpdatedAt()) : null;
      response.items.add(item);
    }
    return response;
  }

  @Transactional(readOnly = true)
  public SystemAdminDtos.EmailTemplateDetailResponse getEmailTemplate(String type) {
    EmailTemplateType templateType = parseEmailTemplateType(type);
    EmailTemplateRendererService.TemplateDefinition definition = emailTemplateRendererService.definition(templateType);
    EmailTemplateConfig config = emailTemplateConfigRepository.findGlobalByType(templateType).orElse(null);
    return toEmailTemplateDetail(definition, config);
  }

  @Transactional
  public SystemAdminDtos.EmailTemplateDetailResponse saveEmailTemplate(String type, SystemAdminDtos.EmailTemplateUpsertRequest request) {
    EmailTemplateType templateType = parseEmailTemplateType(type);
    if (request == null) throw new IllegalArgumentException("payload obrigatorio");

    String subjectTemplate = normalizeRequired(request.subjectTemplate, "Assunto obrigatorio.");
    String htmlTemplate = normalizeRequired(request.htmlTemplate, "HTML obrigatorio.");

    EmailTemplateConfig config = emailTemplateConfigRepository.findGlobalByType(templateType).orElseGet(() -> {
      EmailTemplateConfig entity = new EmailTemplateConfig();
      entity.setTemplateType(templateType);
      entity.setTenantId(null);
      entity.setActive(true);
      return entity;
    });

    config.setActive(request.active == null ? config.isActive() : Boolean.TRUE.equals(request.active));
    config.setFromEmail(normalizeOptional(request.fromEmail));
    config.setFromName(normalizeOptional(request.fromName));
    config.setReplyTo(normalizeOptional(request.replyTo));
    config.setSubjectTemplate(subjectTemplate);
    config.setHtmlTemplate(emailTemplateRendererService.sanitizeHtml(htmlTemplate));
    config.setUpdatedBy(authenticatedUser.idOuNulo());

    validateEmailTemplateConfig(config);
    emailTemplateConfigRepository.save(config);

    auditEmailTemplateChange("EMAIL_TEMPLATE_UPDATED", config);
    return toEmailTemplateDetail(emailTemplateRendererService.definition(templateType), config);
  }

  @Transactional
  public SystemAdminDtos.EmailTemplateDetailResponse updateEmailTemplateStatus(String type, SystemAdminDtos.EmailTemplateStatusUpdateRequest request) {
    EmailTemplateType templateType = parseEmailTemplateType(type);
    if (request == null || request.active == null) {
      throw new IllegalArgumentException("active obrigatorio.");
    }
    EmailTemplateConfig config = emailTemplateConfigRepository.findGlobalByType(templateType)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template ainda nao foi criado."));
    config.setActive(Boolean.TRUE.equals(request.active));
    config.setUpdatedBy(authenticatedUser.idOuNulo());
    config.setUpdatedAt(Instant.now());
    emailTemplateConfigRepository.save(config);
    auditEmailTemplateChange("EMAIL_TEMPLATE_STATUS_UPDATED", config);
    return toEmailTemplateDetail(emailTemplateRendererService.definition(templateType), config);
  }

  @Transactional(readOnly = true)
  public SystemAdminDtos.EmailTemplatePreviewResponse previewEmailTemplate(String type, SystemAdminDtos.EmailTemplateUpsertRequest request) {
    EmailTemplateType templateType = parseEmailTemplateType(type);
    if (request == null) throw new IllegalArgumentException("payload obrigatorio");

    EmailTemplateRendererService.RenderedTemplate preview = emailTemplateRendererService.preview(
        templateType,
        new EmailTemplateRendererService.TemplateInput(
            normalizeOptional(request.fromEmail),
            normalizeOptional(request.fromName),
            normalizeOptional(request.replyTo),
            normalizeOptional(request.subjectTemplate),
            normalizeOptional(request.htmlTemplate)));

    SystemAdminDtos.EmailTemplatePreviewResponse response = new SystemAdminDtos.EmailTemplatePreviewResponse();
    response.templateType = preview.type().name();
    response.label = preview.label();
    response.subject = preview.subject();
    response.html = preview.html();
    response.fromEmail = preview.fromEmail();
    response.fromName = preview.fromName();
    response.replyTo = preview.replyTo();
    response.placeholders = new ArrayList<>(preview.placeholders());
    response.sampleValues = new LinkedHashMap<>(preview.sampleValues());
    return response;
  }

  @Transactional
  public SystemAdminDtos.EmailTemplateDetailResponse restoreDefaultEmailTemplate(String type) {
    EmailTemplateType templateType = parseEmailTemplateType(type);
    EmailTemplateConfig config = emailTemplateConfigRepository.findGlobalByType(templateType).orElse(null);
    if (config != null) {
      emailTemplateConfigRepository.delete(config);
    }
    auditEmailTemplateRestore(templateType);
    return toEmailTemplateDetail(emailTemplateRendererService.definition(templateType), null);
  }

  private Product requirePlan(String planId) {
    UUID id;
    try {
      id = UUID.fromString(planId);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("planId invalido");
    }
    return productRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano nao encontrado."));
  }

  private SystemAdminDtos.EmailTemplateDetailResponse toEmailTemplateDetail(
      EmailTemplateRendererService.TemplateDefinition definition, EmailTemplateConfig config) {
    SystemAdminDtos.EmailTemplateDetailResponse response = new SystemAdminDtos.EmailTemplateDetailResponse();
    response.templateType = definition.type().name();
    response.label = definition.label();
    response.configured = config != null;
    response.active = config != null && config.isActive();
    response.fromEmail = config != null ? config.getFromEmail() : null;
    response.fromName = config != null ? config.getFromName() : null;
    response.replyTo = config != null ? config.getReplyTo() : null;
    response.subjectTemplate = config != null ? config.getSubjectTemplate() : definition.defaultSubjectTemplate();
    response.htmlTemplate = config != null ? config.getHtmlTemplate() : definition.defaultHtmlTemplate();
    response.placeholders = new ArrayList<>(definition.placeholders());
    response.updatedAt = config != null ? toInstantString(config.getUpdatedAt()) : null;
    response.updatedBy = config != null && config.getUpdatedBy() != null ? config.getUpdatedBy().toString() : null;
    response.sampleValues = new LinkedHashMap<>(emailTemplateRendererService.sampleVariables());
    return response;
  }

  private void applyPlanRequest(Product product, SystemAdminDtos.PlanUpsertRequest request) {
    if (request == null) throw new IllegalArgumentException("payload obrigatorio");
    String name = normalizeRequired(request.name, "Nome do plano obrigatorio");
    String currency = normalizeRequired(request.currency, "Moeda obrigatoria").toUpperCase(Locale.ROOT);
    BigDecimal price = request.price != null
        ? request.price
        : request.priceCents != null ? request.priceCents : BigDecimal.ZERO;
    int validityMonths = request.validityMonths != null ? request.validityMonths : 1;
    Integer validityDays = request.validityDays;
    int priority = request.priority != null ? request.priority : 0;
    boolean active = request.active == null || Boolean.TRUE.equals(request.active);
    boolean trial = Boolean.TRUE.equals(request.trial);

    if (price.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("price invalido");
    if (validityMonths <= 0) throw new IllegalArgumentException("validityMonths invalido");
    if (validityDays != null && validityDays <= 0) throw new IllegalArgumentException("validityDays invalido");
    if (request.maxProfessionals != null && request.maxProfessionals < 0) {
      throw new IllegalArgumentException("maxProfessionals invalido");
    }
    if (trial && price.compareTo(BigDecimal.ZERO) > 0) {
      throw new IllegalArgumentException("Plano trial nao pode ter cobranca.");
    }

    product.setName(name);
    product.setDescription(normalizeOptional(request.description));
    product.setCurrency(currency);
    product.setPriceCents(price);
    product.setValidityMonths(validityMonths);
    product.setValidityDays(validityDays);
    product.setHighlight(normalizeOptional(request.highlight));
    product.setFeaturesJson(normalizeFeaturesJson(request.featuresJson));
    product.setActive(active);
    product.setTrial(trial);
    product.setPriority(priority);
    // Flag de venda interna: default preserva o comportamento (plano publico).
    product.setExclusivoVendaInterna(Boolean.TRUE.equals(request.exclusivoVendaInterna));
  }

  private EmailTemplateType parseEmailTemplateType(String rawType) {
    if (rawType == null || rawType.isBlank()) {
      throw new IllegalArgumentException("type obrigatorio.");
    }
    try {
      return EmailTemplateType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Tipo de template invalido.");
    }
  }

  private void validateEmailTemplateConfig(EmailTemplateConfig config) {
    if (config == null) throw new IllegalArgumentException("Template invalido.");
    if (!isValidEmailOrNull(config.getFromEmail())) {
      throw new IllegalArgumentException("fromEmail invalido.");
    }
    if (!isValidEmailOrNull(config.getReplyTo())) {
      throw new IllegalArgumentException("replyTo invalido.");
    }
  }

  private boolean isValidEmailOrNull(String value) {
    String normalized = normalizeOptional(value);
    if (normalized == null) return true;
    return normalized.matches("(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$");
  }

  private void upsertPlanCapability(UUID productId, Integer maxProfessionals) {
    ProductCapability capability = productCapabilityRepository.findById(productId).orElse(null);
    if (capability == null) {
      capability = new ProductCapability();
      capability.setProductId(productId);
      capability.setMaxProfessionals(maxProfessionals);
      productCapabilityRepository.save(capability);
      return;
    }
    capability.setMaxProfessionals(maxProfessionals);
    productCapabilityRepository.save(capability);
  }

  private void enforceSingleActiveTrial(UUID currentProductId, boolean trial, boolean active) {
    if (!trial || !active) return;
    List<Product> others = productRepository.findOutrosTrialsAtivos(currentProductId);
    for (Product other : others) {
      other.setActive(false);
    }
    productRepository.saveAll(others);
  }

  private SystemAdminDtos.PlanItem toPlanItem(Product product) {
    SystemAdminDtos.PlanItem item = new SystemAdminDtos.PlanItem();
    item.id = product.getId() != null ? product.getId().toString() : null;
    item.name = product.getName();
    item.description = product.getDescription();
    item.currency = product.getCurrency();
    item.price = product.getPriceCents();
    item.validityMonths = product.getValidityMonths();
    item.validityDays = product.getValidityDays();
    item.highlight = product.getHighlight();
    item.featuresJson = product.getFeaturesJson();
    item.active = product.isActive();
    item.trial = product.isTrial();
    item.priority = product.getPriority();
    item.exclusivoVendaInterna = product.isExclusivoVendaInterna();
    item.createdAt = product.getCreatedAt() != null ? product.getCreatedAt().toString() : null;
    item.updatedAt = product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : null;
    ProductCapability capability = productCapabilityRepository.findById(product.getId()).orElse(null);
    item.maxProfessionals = capability != null ? capability.getMaxProfessionals() : null;
    return item;
  }

  private void recordPlanAudit(String action, Product product) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = authenticatedUser.tenantIdOuNulo();
      command.actorUserId = authenticatedUser.idOuNulo();
      command.module = AuditConstants.Module.SYSTEM;
      command.action = action;
      command.entityType = "PRODUCT";
      command.entityId = product.getId() != null ? product.getId().toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      Map<String, Object> after = new LinkedHashMap<>();
      after.put("name", product.getName());
      after.put("currency", product.getCurrency());
      after.put("price", product.getPriceCents());
      after.put("validityMonths", product.getValidityMonths());
      after.put("validityDays", product.getValidityDays());
      after.put("active", product.isActive());
      after.put("trial", product.isTrial());
      after.put("priority", product.getPriority());
      after.put("exclusivoVendaInterna", product.isExclusivoVendaInterna());
      command.after = after;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve quebrar o fluxo administrativo.
    }
  }

  private void auditEmailTemplateChange(String action, EmailTemplateConfig config) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = null;
      command.actorUserId = authenticatedUser.idOuNulo();
      command.module = AuditConstants.Module.SYSTEM;
      command.action = action;
      command.entityType = "EMAIL_TEMPLATE";
      command.entityId = config.getId() != null ? config.getId().toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      Map<String, Object> after = new LinkedHashMap<>();
      after.put("templateType", config.getTemplateType() != null ? config.getTemplateType().name() : null);
      after.put("fromEmail", config.getFromEmail());
      after.put("fromName", config.getFromName());
      after.put("replyTo", config.getReplyTo());
      after.put("subjectTemplate", config.getSubjectTemplate());
      command.after = after;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
    }
  }

  private void auditEmailTemplateRestore(EmailTemplateType templateType) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = null;
      command.actorUserId = authenticatedUser.idOuNulo();
      command.module = AuditConstants.Module.SYSTEM;
      command.action = "EMAIL_TEMPLATE_RESTORED_DEFAULT";
      command.entityType = "EMAIL_TEMPLATE";
      command.entityId = templateType.name();
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.metadata = Map.of("templateType", templateType.name());
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
    }
  }

  private long countTenantsByPlanStatus(String statusCode) {
    return asLong(entityManager.createNativeQuery("""
        SELECT COUNT(*)
        FROM tenants t
        JOIN plan_status ps ON ps.id = t.plan_status_id
        WHERE ps.code = :code
        """)
        .setParameter("code", statusCode)
        .getSingleResult());
  }

  private int sanitizeLimit(Integer limit) {
    if (limit == null || limit < 1) return 50;
    return Math.min(limit, 200);
  }

  private long asLong(Object value) {
    if (value == null) return 0L;
    if (value instanceof Number number) return number.longValue();
    return Long.parseLong(value.toString());
  }

  private String toString(Object value) {
    return value != null ? value.toString() : null;
  }

  private String normalizeSuggestionStatus(String rawStatus, String fallback) {
    String status = rawStatus == null || rawStatus.isBlank()
        ? (fallback == null || fallback.isBlank() ? "OPEN" : fallback.trim().toUpperCase(Locale.ROOT))
        : rawStatus.trim().toUpperCase(Locale.ROOT);
    if (!"OPEN".equals(status) && !"CLOSED".equals(status)) {
      throw new IllegalArgumentException("Status de sugestao invalido. Use OPEN ou CLOSED.");
    }
    return status;
  }

  private String normalizeOptional(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  private String normalizeRequired(String value, String message) {
    String normalized = normalizeOptional(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }

  private String normalizeFeaturesJson(String value) {
    String normalized = normalizeOptional(value);
    return normalized != null ? normalized : "[]";
  }

  private SystemAdminDtos.GlobalSuggestionItem toGlobalSuggestionItem(FeedbackSuggestion entity, String tenantName) {
    SystemAdminDtos.GlobalSuggestionItem item = new SystemAdminDtos.GlobalSuggestionItem();
    item.id = entity.getId() != null ? entity.getId().toString() : null;
    item.tenantId = entity.getTenantId() != null ? entity.getTenantId().toString() : null;
    item.tenantName = tenantName;
    item.userId = entity.getUserId() != null ? entity.getUserId().toString() : null;
    item.userName = entity.getUserName();
    item.userRole = entity.getUserRole();
    item.category = entity.getCategory();
    item.title = entity.getTitle();
    item.message = entity.getMessage();
    item.status = entity.getStatus();
    item.adminResponse = entity.getAdminResponse();
    item.respondedByUserId = entity.getRespondedByUserId() != null ? entity.getRespondedByUserId().toString() : null;
    item.respondedByUserName = entity.getRespondedByUserName();
    item.respondedAt = entity.getRespondedAt() != null ? entity.getRespondedAt().toString() : null;
    item.closedAt = entity.getClosedAt() != null ? entity.getClosedAt().toString() : null;
    item.sourcePage = entity.getSourcePage();
    item.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    item.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return item;
  }

  private UUID parseUuidOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private String toInstantString(Object value) {
    if (value == null) return null;
    if (value instanceof Instant instant) return instant.toString();
    if (value instanceof Timestamp ts) return ts.toInstant().toString();
    if (value instanceof java.util.Date date) return date.toInstant().toString();
    return value.toString();
  }

  private Instant toInstant(Object value) {
    if (value == null) return null;
    if (value instanceof Instant instant) return instant;
    if (value instanceof Timestamp ts) return ts.toInstant();
    if (value instanceof java.util.Date date) return date.toInstant();
    try {
      return DataUtil.parseInstantISO(value.toString());
    } catch (Exception ignored) {
      return null;
    }
  }

  private void auditAdminMfaReset(Usuario user, String reason, long revokedCount, boolean wasEnabled, boolean wasEnrolled) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = user.getTenantId();
      command.actorUserId = authenticatedUser.idOuNulo();
      command.module = AuditConstants.Module.AUTH;
      command.action = "AUTH_MFA_RECOVERY_ADMIN_RESET";
      command.entityType = "USER_AUTH";
      command.entityId = user.getId() != null ? user.getId().toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.metadata = Map.of(
          "reason", reason,
          "revokedSessions", revokedCount,
          "targetUserRole", user.getRole() != null ? user.getRole().name() : "",
          "mfaWasEnabled", wasEnabled,
          "mfaWasEnrolled", wasEnrolled);
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve impedir o reset administrativo de MFA.
    }
  }
}
