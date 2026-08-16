package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequestAuditContext;
import br.com.phdigitalcode.azzo.agenda.pro.util.LogSanitizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Equivalente Spring de {@code modules/audit/application/AuditService.java}.
 *
 * <p>Substitui o PLACEHOLDER que apenas logava (introduzido enquanto o modulo {@code audit} ainda
 * nao existia nesta aplicacao — ver nota em {@link AuditConstants}) pela implementacao real:
 * persiste em {@code audit_events} com encadeamento de hash (SHA-256 sobre o evento anterior do
 * tenant), mascaramento de campos sensiveis e deteccao de campos alterados. Mesma interface
 * publica ({@code recordSuccess}/{@code recordError}/{@code recordDenied}) usada por
 * {@code GlobalExceptionHandler} e {@code AuthServiceImpl} — nenhum caller precisou mudar.
 */
@Service
public class AuditService {

  private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

  private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Set.of(
      "password",
      "senha",
      "token",
      "access_token",
      "accessToken",
      "refresh_token",
      "refreshToken",
      "secret",
      "apiKey",
      "api_key",
      "authorization",
      "cookie",
      "jwt",
      "cvv",
      "cardNumber",
      "number",
      // Campos de notas de atendimento — dados sensiveis de saude/comportamento do cliente (LGPD art. 11)
      "serviceExecutionNotes",
      "service_execution_notes",
      "clientFeedbackNotes",
      "client_feedback_notes",
      "internalFollowupNotes",
      "internal_followup_notes",
      "clientNotes",
      "client_notes",
      "notes"));
  private static final Set<String> SENSITIVE_FRAGMENTS = Set.of(
      "token",
      "password",
      "secret",
      "authorization",
      "cookie",
      "apikey",
      "api_key",
      "jwt",
      "bearer");

  private final AuditEventRepository auditEventRepository;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final RequestAuditContext requestAuditContext;

  public AuditService(
      AuditEventRepository auditEventRepository,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      RequestAuditContext requestAuditContext) {
    this.auditEventRepository = auditEventRepository;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.requestAuditContext = requestAuditContext;
  }

  @Transactional
  public AuditEvent recordSuccess(AuditEventCommand command) {
    return persist(command, AuditConstants.Status.SUCCESS);
  }

  @Transactional
  public AuditEvent recordError(AuditEventCommand command) {
    return persist(command, AuditConstants.Status.ERROR);
  }

  @Transactional
  public AuditEvent recordDenied(AuditEventCommand command) {
    return persist(command, AuditConstants.Status.DENIED);
  }

  private AuditEvent persist(AuditEventCommand command, String status) {
    Timer.Sample sample = meterRegistry != null ? Timer.start(meterRegistry) : null;
    if (command == null) throw new IllegalArgumentException("AuditEventCommand obrigatorio");
    if (command.tenantId == null) throw new IllegalArgumentException("tenantId obrigatorio");
    if (command.action == null || command.action.isBlank()) throw new IllegalArgumentException("action obrigatoria");
    if (command.module == null || command.module.isBlank()) throw new IllegalArgumentException("module obrigatorio");

    enrichWithRequestContext(command);
    String requestId = requestIdOrGenerated(command.requestId);
    String module = command.module.trim().toUpperCase();
    String action = command.action.trim().toUpperCase();
    try {
      Instant now = Instant.now();
      String prevHash = auditEventRepository.findLastByTenant(command.tenantId)
          .map(AuditEvent::getEventHash)
          .orElse(null);

      String beforeJson = toCanonicalJson(command.before);
      String afterJson = toCanonicalJson(command.after);
      String metadataJson = toCanonicalJson(command.metadata);
      ChangeResult change = detectChanges(beforeJson, afterJson);
      String changedFieldsJson = toCanonicalJson(change.changedFields());

      AuditEvent event = new AuditEvent();
      event.setTenantId(command.tenantId);
      event.setActorUserId(command.actorUserId);
      event.setActorRole(sanitize(command.actorRole));
      event.setModule(module);
      event.setAction(action);
      event.setEntityType(sanitize(command.entityType));
      event.setEntityId(sanitize(command.entityId));
      event.setStatus(status);
      event.setErrorCode(sanitize(command.errorCode));
      event.setErrorMessage(truncate(LogSanitizer.sanitizeLogMessage(command.errorMessage), 500));
      event.setRequestId(requestId);
      event.setIdempotencyKey(sanitize(command.idempotencyKey));
      event.setSourceChannel(sourceOrDefault(command.sourceChannel));
      event.setIpAddress(sanitize(command.ipAddress));
      event.setUserAgent(truncate(command.userAgent, 300));
      event.setBeforeJson(beforeJson);
      event.setAfterJson(afterJson);
      event.setMetadataJson(metadataJson);
      event.setHasChanges(change.hasChanges());
      event.setChangedFieldsJson(changedFieldsJson);
      event.setPrevEventHash(prevHash);
      event.setCreatedAt(now);
      event.setEventHash(hashEvent(event));

      auditEventRepository.save(event);
      registrarMetricas(status, true, sample);
      LOG.info(
          "audit_event_persisted tenant_id={} request_id={} actor_user_id={} module={} action={} status={} event_id={}",
          command.tenantId,
          requestId,
          command.actorUserId,
          module,
          action,
          status,
          event.getId());
      return event;
    } catch (RuntimeException e) {
      registrarMetricas(status, false, sample);
      if (meterRegistry != null) {
        meterRegistry.counter("audit.write.errors", "status", status).increment();
      }
      LOG.error(
          "audit_write_failed tenant_id={} request_id={} actor_user_id={} module={} action={} status={}",
          command.tenantId,
          requestId,
          command.actorUserId,
          module,
          action,
          status,
          e);
      throw e;
    }
  }

  private String hashEvent(AuditEvent event) {
    String payload = String.join(
        "|",
        safe(event.getTenantId()),
        safe(event.getModule()),
        safe(event.getAction()),
        safe(event.getEntityType()),
        safe(event.getEntityId()),
        safe(event.getStatus()),
        safe(event.getRequestId()),
        safe(event.getSourceChannel()),
        safe(event.getBeforeJson()),
        safe(event.getAfterJson()),
        safe(event.getMetadataJson()),
        safe(event.getChangedFieldsJson()),
        safe(event.getPrevEventHash()),
        safe(event.getCreatedAt()));
    return sha256(payload);
  }

  private ChangeResult detectChanges(String beforeJson, String afterJson) {
    if (beforeJson == null && afterJson == null) return new ChangeResult(false, List.of());
    if (beforeJson == null || afterJson == null) return new ChangeResult(true, List.of("_root"));
    try {
      JsonNode beforeNode = objectMapper.readTree(beforeJson);
      JsonNode afterNode = objectMapper.readTree(afterJson);
      if (beforeNode.equals(afterNode)) return new ChangeResult(false, List.of());

      if (beforeNode.isObject() && afterNode.isObject()) {
        Set<String> fields = new LinkedHashSet<>();
        Iterator<String> beforeIt = beforeNode.fieldNames();
        while (beforeIt.hasNext()) fields.add(beforeIt.next());
        Iterator<String> afterIt = afterNode.fieldNames();
        while (afterIt.hasNext()) fields.add(afterIt.next());
        ArrayList<String> changed = new ArrayList<>();
        for (String field : fields) {
          JsonNode b = beforeNode.get(field);
          JsonNode a = afterNode.get(field);
          if (b == null && a != null) {
            changed.add(field);
            continue;
          }
          if (b != null && a == null) {
            changed.add(field);
            continue;
          }
          if (b != null && !b.equals(a)) changed.add(field);
        }
        return new ChangeResult(true, changed);
      }
      return new ChangeResult(true, List.of("_root"));
    } catch (Exception e) {
      LOG.warn("Falha ao detectar campos alterados; fallback para _unknown", e);
      return new ChangeResult(true, List.of("_unknown"));
    }
  }

  private String toCanonicalJson(Object value) {
    if (value == null) return null;
    try {
      ObjectMapper canonicalMapper = objectMapper.copy()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
      JsonNode node = canonicalMapper.valueToTree(value);
      maskSensitive(node);
      return canonicalMapper.writeValueAsString(node);
    } catch (Exception e) {
      LOG.warn("Falha ao serializar payload de auditoria", e);
      return truncate(String.valueOf(value), 2000);
    }
  }

  private void maskSensitive(JsonNode node) {
    if (node == null) return;
    if (node.isObject()) {
      Iterator<String> names = node.fieldNames();
      List<String> fields = new ArrayList<>();
      while (names.hasNext()) fields.add(names.next());
      for (String field : fields) {
        JsonNode child = node.get(field);
        if (isSensitiveField(field) && child != null && !child.isNull()) {
          ((ObjectNode) node).put(field, "***");
        } else {
          maskSensitive(child);
        }
      }
      return;
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        maskSensitive(child);
      }
    }
  }

  private boolean isSensitiveField(String field) {
    if (field == null) return false;
    String normalized = field.trim();
    if (normalized.isBlank()) return false;
    String lower = normalized.toLowerCase();
    if (SENSITIVE_KEYS.contains(normalized) || SENSITIVE_KEYS.contains(lower)) return true;
    String compact = lower.replaceAll("[^a-z0-9_]", "");
    for (String fragment : SENSITIVE_FRAGMENTS) {
      if (compact.contains(fragment)) return true;
    }
    return false;
  }

  private String requestIdOrGenerated(String requestId) {
    String value = sanitize(requestId);
    return value != null ? value : UUID.randomUUID().toString();
  }

  private String sourceOrDefault(String sourceChannel) {
    String value = sanitize(sourceChannel);
    if (value == null) return AuditConstants.SourceChannel.SYSTEM;
    return value.toUpperCase();
  }

  private String sanitize(String value) {
    if (value == null) return null;
    String v = value.trim();
    return v.isBlank() ? null : v;
  }

  private String truncate(String value, int max) {
    if (value == null) return null;
    if (value.length() <= max) return value;
    return value.substring(0, max);
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao gerar hash de auditoria", e);
    }
  }

  private String safe(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private void registrarMetricas(String status, boolean success, Timer.Sample sample) {
    if (meterRegistry == null) return;
    try {
      meterRegistry.counter(
              "audit.events.total",
              "status",
              status,
              "success",
              String.valueOf(success))
          .increment();
      if (sample != null) {
        sample.stop(
            Timer.builder("audit.write.latency")
                .tag("status", status)
                .tag("success", String.valueOf(success))
                .register(meterRegistry));
      }
    } catch (Exception e) {
      meterRegistry.counter("audit.write.errors").increment();
      LOG.warn("Falha ao registrar metricas de auditoria", e);
    }
  }

  private void enrichWithRequestContext(AuditEventCommand command) {
    if (requestAuditContext == null) return;
    RequestAuditContext context;
    try {
      context = requestAuditContext;
      // Forca a resolucao do proxy de escopo de requisicao; fora de uma requisicao HTTP
      // (ex.: scheduler chamando AuditService) isso lanca, e o catch abaixo trata como "sem
      // contexto disponivel" — mesmo comportamento do Instance<RequestAuditContext> original.
      context.getRequestId();
    } catch (Exception ignored) {
      return;
    }
    if ((command.requestId == null || command.requestId.isBlank()) && context.getRequestId() != null) {
      command.requestId = context.getRequestId();
    }
    if ((command.ipAddress == null || command.ipAddress.isBlank()) && context.getIpAddress() != null) {
      command.ipAddress = context.getIpAddress();
    }
    if ((command.userAgent == null || command.userAgent.isBlank()) && context.getUserAgent() != null) {
      command.userAgent = context.getUserAgent();
    }
  }

  private record ChangeResult(boolean hasChanges, List<String> changedFields) {}
}
