package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.AuditDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditRetentionEvent;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;

/** Espelha {@code modules/audit/application/AuditQueryService.java}. */
@Service
public class AuditQueryService {

  private static final Logger LOG = LoggerFactory.getLogger(AuditQueryService.class);

  private record ExportEntry(String payload, String format, Instant expiresAt) {}

  private final ConcurrentHashMap<String, ExportEntry> exportStore = new ConcurrentHashMap<>();

  private final AuditEventRepository auditEventRepository;
  private final AuditRetentionEventRepository auditRetentionEventRepository;
  private final UsuarioRepository usuarioRepository;
  private final ObjectMapper objectMapper;

  public AuditQueryService(
      AuditEventRepository auditEventRepository,
      AuditRetentionEventRepository auditRetentionEventRepository,
      UsuarioRepository usuarioRepository,
      ObjectMapper objectMapper) {
    this.auditEventRepository = auditEventRepository;
    this.auditRetentionEventRepository = auditRetentionEventRepository;
    this.usuarioRepository = usuarioRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public AuditDtos.AuditSearchResponse search(
      UUID tenantId,
      String from,
      String to,
      List<String> modules,
      List<String> actions,
      List<String> statuses,
      List<String> entityTypes,
      String entityId,
      List<String> actorUserIds,
      String requestId,
      List<String> sourceChannels,
      String ip,
      Boolean hasChanges,
      String text,
      String cursor,
      Integer limit) {
    int lim = sanitizeLimit(limit);
    CursorParts cursorParts = decodeCursor(cursor);

    Specification<AuditEvent> spec = buildSearchSpecification(
        tenantId, from, to, modules, actions, statuses, entityTypes, entityId, actorUserIds,
        requestId, sourceChannels, ip, hasChanges, text, cursorParts);

    PageRequest pageRequest = PageRequest.of(
        0, lim + 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    List<AuditEvent> raw = auditEventRepository.findAll(spec, pageRequest).getContent();

    boolean hasNext = raw.size() > lim;
    List<AuditEvent> pageItems = hasNext ? raw.subList(0, lim) : raw;

    AuditDtos.AuditSearchResponse response = new AuditDtos.AuditSearchResponse();
    response.limit = lim;
    response.hasNext = hasNext;
    Map<UUID, String> actorNamesById = resolveActorNames(tenantId, pageItems);
    response.items = pageItems.stream().map(event -> toListItem(event, actorNamesById)).toList();
    response.nextCursor = hasNext ? encodeCursor(pageItems.get(pageItems.size() - 1)) : null;
    response.aggregations = aggregationsFromPage(pageItems);
    return response;
  }

  private Specification<AuditEvent> buildSearchSpecification(
      UUID tenantId,
      String from,
      String to,
      List<String> modules,
      List<String> actions,
      List<String> statuses,
      List<String> entityTypes,
      String entityId,
      List<String> actorUserIds,
      String requestId,
      List<String> sourceChannels,
      String ip,
      Boolean hasChanges,
      String text,
      CursorParts cursorParts) {
    return (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      predicates.add(cb.notEqual(root.get("module"), AuditConstants.Module.SYSTEM));
      predicates.add(cb.isNotNull(root.get("actorUserId")));

      if (from != null && !from.isBlank()) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), Instant.parse(from.trim())));
      }
      if (to != null && !to.isBlank()) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), Instant.parse(to.trim())));
      }
      if (modules != null && !modules.isEmpty()) {
        predicates.add(root.get("module").in(normalizeList(modules)));
      }
      if (actions != null && !actions.isEmpty()) {
        predicates.add(root.get("action").in(normalizeList(actions)));
      }
      if (statuses != null && !statuses.isEmpty()) {
        predicates.add(root.get("status").in(normalizeList(statuses)));
      }
      if (entityTypes != null && !entityTypes.isEmpty()) {
        predicates.add(root.get("entityType").in(normalizeList(entityTypes)));
      }
      if (entityId != null && !entityId.isBlank()) {
        predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
      }
      if (actorUserIds != null && !actorUserIds.isEmpty()) {
        List<UUID> ids = actorUserIds.stream().map(UUID::fromString).toList();
        predicates.add(root.get("actorUserId").in(ids));
      }
      if (requestId != null && !requestId.isBlank()) {
        predicates.add(cb.equal(root.get("requestId"), requestId.trim()));
      }
      if (sourceChannels != null && !sourceChannels.isEmpty()) {
        predicates.add(root.get("sourceChannel").in(normalizeList(sourceChannels)));
      }
      if (ip != null && !ip.isBlank()) {
        predicates.add(cb.equal(root.get("ipAddress"), ip.trim()));
      }
      if (hasChanges != null) {
        predicates.add(cb.equal(root.get("hasChanges"), hasChanges));
      }
      if (text != null && !text.isBlank()) {
        String pattern = "%" + text.trim().toUpperCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.upper(root.get("action")), pattern),
                cb.like(cb.upper(cb.coalesce(root.get("errorMessage"), "")), pattern),
                cb.like(cb.upper(cb.coalesce(root.get("metadataJson"), "")), pattern)));
      }
      if (cursorParts != null) {
        predicates.add(
            cb.or(
                cb.lessThan(root.get("createdAt"), cursorParts.createdAt()),
                cb.and(
                    cb.equal(root.get("createdAt"), cursorParts.createdAt()),
                    cb.lessThan(root.get("id"), cursorParts.id()))));
      }
      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
  }

  @Transactional(readOnly = true)
  public AuditDtos.AuditEventDetail detail(UUID tenantId, String id) {
    UUID uuid = UUID.fromString(id);
    AuditEvent event = auditEventRepository.findById(uuid)
        .filter(e -> tenantId.equals(e.getTenantId()))
        .orElseThrow(() -> new IllegalArgumentException("Evento de auditoria nao encontrado"));
    if (AuditConstants.Module.SYSTEM.equalsIgnoreCase(event.getModule())) {
      throw new IllegalArgumentException("Evento de auditoria nao encontrado");
    }
    if (event.getActorUserId() == null) {
      throw new IllegalArgumentException("Evento de auditoria nao encontrado");
    }
    Map<UUID, String> actorNamesById = resolveActorNames(tenantId, List.of(event));
    AuditDtos.AuditEventDetail detail = toDetail(event, actorNamesById);
    detail.chainValid = isChainValidForEvent(tenantId, event);
    return detail;
  }

  @Transactional(readOnly = true)
  public AuditDtos.AuditRetentionPageResponse listRetentionEventsPaged(
      UUID tenantId, String from, String to, String executionId, Integer limit) {
    int lim = (limit == null || limit < 1) ? 50 : Math.min(limit, 200);
    List<AuditRetentionEvent> all = listRetentionEvents(tenantId, from, to, executionId);
    boolean hasNext = all.size() > lim;
    List<AuditRetentionEvent> page = all.stream().limit(lim).toList();
    AuditDtos.AuditRetentionPageResponse response = new AuditDtos.AuditRetentionPageResponse();
    response.items = page;
    response.hasNext = hasNext;
    response.nextCursor = hasNext && !page.isEmpty() ? page.get(page.size() - 1).getId().toString() : null;
    return response;
  }

  @Transactional(readOnly = true)
  public List<AuditRetentionEvent> listRetentionEvents(UUID tenantId, String from, String to, String executionId) {
    Specification<AuditRetentionEvent> spec = (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      if (tenantId != null) {
        predicates.add(cb.or(cb.equal(root.get("tenantId"), tenantId), cb.isNull(root.get("tenantId"))));
      }
      if (from != null && !from.isBlank()) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), Instant.parse(from.trim())));
      }
      if (to != null && !to.isBlank()) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), Instant.parse(to.trim())));
      }
      if (executionId != null && !executionId.isBlank()) {
        predicates.add(cb.equal(root.get("executionId"), executionId.trim()));
      }
      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
    return auditRetentionEventRepository.findAll(
        spec, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
  }

  @Transactional(readOnly = true)
  public AuditDtos.AuditFilterOptionsResponse filterOptions(UUID tenantId, String from, String to) {
    return auditEventRepository.aggregateFilterOptions(
        tenantId,
        from != null && !from.isBlank() ? Instant.parse(from.trim()) : null,
        to != null && !to.isBlank() ? Instant.parse(to.trim()) : null);
  }

  @Transactional(readOnly = true)
  public AuditDtos.AuditExportResponse export(UUID tenantId, AuditDtos.AuditExportRequest request) {
    AuditDtos.AuditSearchResponse search = search(
        tenantId,
        request != null ? request.from : null,
        request != null ? request.to : null,
        request != null ? request.modules : null,
        request != null ? request.actions : null,
        request != null ? request.statuses : null,
        request != null ? request.entityTypes : null,
        request != null ? request.entityId : null,
        request != null ? request.actorUserIds : null,
        request != null ? request.requestId : null,
        request != null ? request.sourceChannels : null,
        request != null ? request.ip : null,
        request != null ? request.hasChanges : null,
        request != null ? request.text : null,
        null,
        200);
    String format = request != null && request.format != null && !request.format.isBlank()
        ? request.format.trim().toUpperCase()
        : "JSON";
    String payload = "CSV".equals(format) ? toCsv(search.items) : toJson(search.items);
    String checksum = sha256(payload);
    String exportId = UUID.randomUUID().toString();
    Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

    // limpar entradas expiradas antes de adicionar a nova
    exportStore.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt()));
    exportStore.put(exportId, new ExportEntry(payload, format, expiresAt));

    AuditDtos.AuditExportResponse response = new AuditDtos.AuditExportResponse();
    response.exportId = exportId;
    response.format = format;
    response.expiresAt = expiresAt.toString();
    response.checksumSha256 = checksum;
    response.downloadUrl = "/api/v1/auditoria/events/export/" + exportId;
    return response;
  }

  public record ExportDownload(String payload, String format, String contentType) {}

  public ExportDownload downloadExport(String exportId) {
    if (exportId == null || exportId.isBlank()) throw new IllegalArgumentException("exportId invalido");
    ExportEntry entry = exportStore.get(exportId.trim());
    if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
      exportStore.remove(exportId.trim());
      throw new IllegalArgumentException("Export nao encontrado ou expirado");
    }
    String contentType = "CSV".equals(entry.format())
        ? "text/csv; charset=UTF-8"
        : "application/json; charset=UTF-8";
    return new ExportDownload(entry.payload(), entry.format(), contentType);
  }

  private String toCsv(List<AuditDtos.AuditEventListItem> items) {
    StringBuilder sb = new StringBuilder();
    sb.append("id,createdAt,module,action,entityType,entityId,status,actorName,actorRole,requestId,sourceChannel,ipAddress,alterado\n");
    for (AuditDtos.AuditEventListItem item : items) {
      sb.append(csvField(item.id)).append(",")
          .append(csvField(item.createdAt)).append(",")
          .append(csvField(item.module)).append(",")
          .append(csvField(item.action)).append(",")
          .append(csvField(item.entityType)).append(",")
          .append(csvField(item.entityId)).append(",")
          .append(csvField(item.status)).append(",")
          .append(csvField(item.actorName)).append(",")
          .append(csvField(item.actorRole)).append(",")
          .append(csvField(item.requestId)).append(",")
          .append(csvField(item.sourceChannel)).append(",")
          .append(csvField(item.ipAddress)).append(",")
          .append(item.alterado).append("\n");
    }
    return sb.toString();
  }

  private String csvField(String value) {
    if (value == null) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private AuditDtos.AuditEventListItem toListItem(AuditEvent event, Map<UUID, String> actorNamesById) {
    AuditDtos.AuditEventListItem item = new AuditDtos.AuditEventListItem();
    item.id = event.getId().toString();
    item.tenantId = event.getTenantId() != null ? event.getTenantId().toString() : null;
    item.actorUserId = event.getActorUserId() != null ? event.getActorUserId().toString() : null;
    item.actorName = event.getActorUserId() != null ? actorNamesById.get(event.getActorUserId()) : null;
    item.actorRole = event.getActorRole();
    item.module = event.getModule();
    item.action = event.getAction();
    item.entityType = event.getEntityType();
    item.entityId = event.getEntityId();
    item.status = event.getStatus();
    item.errorCode = event.getErrorCode();
    item.requestId = event.getRequestId();
    item.sourceChannel = event.getSourceChannel();
    item.ipAddress = event.getIpAddress();
    item.createdAt = event.getCreatedAt() != null ? event.getCreatedAt().toString() : null;
    item.alterado = event.isHasChanges();
    item.camposAlterados = parseStringList(event.getChangedFieldsJson());
    return item;
  }

  private AuditDtos.AuditEventDetail toDetail(AuditEvent event, Map<UUID, String> actorNamesById) {
    AuditDtos.AuditEventDetail detail = new AuditDtos.AuditEventDetail();
    AuditDtos.AuditEventListItem base = toListItem(event, actorNamesById);
    detail.id = base.id;
    detail.tenantId = base.tenantId;
    detail.actorUserId = base.actorUserId;
    detail.actorName = base.actorName;
    detail.actorRole = base.actorRole;
    detail.module = base.module;
    detail.action = base.action;
    detail.entityType = base.entityType;
    detail.entityId = base.entityId;
    detail.status = base.status;
    detail.errorCode = base.errorCode;
    detail.requestId = base.requestId;
    detail.sourceChannel = base.sourceChannel;
    detail.ipAddress = base.ipAddress;
    detail.createdAt = base.createdAt;
    detail.alterado = base.alterado;
    detail.camposAlterados = base.camposAlterados;
    detail.errorMessage = event.getErrorMessage();
    detail.before = parseJson(event.getBeforeJson());
    detail.after = parseJson(event.getAfterJson());
    detail.metadata = parseJson(event.getMetadataJson());
    detail.eventHash = event.getEventHash();
    detail.prevEventHash = event.getPrevEventHash();
    return detail;
  }

  private AuditDtos.Aggregations aggregationsFromPage(List<AuditEvent> pageItems) {
    AuditDtos.Aggregations aggs = new AuditDtos.Aggregations();
    aggs.byModule = topCounts(pageItems, AuditEvent::getModule);
    aggs.byStatus = topCounts(pageItems, AuditEvent::getStatus);
    aggs.byAction = topCounts(pageItems, AuditEvent::getAction);
    return aggs;
  }

  private List<AuditDtos.AggregationItem> topCounts(List<AuditEvent> events, Function<AuditEvent, String> classifier) {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (AuditEvent event : events) {
      String key = classifier.apply(event);
      if (key == null || key.isBlank()) continue;
      counts.put(key, counts.getOrDefault(key, 0L) + 1L);
    }
    return counts.entrySet().stream()
        .map(e -> {
          AuditDtos.AggregationItem item = new AuditDtos.AggregationItem();
          item.key = e.getKey();
          item.count = e.getValue();
          return item;
        })
        .sorted(Comparator.comparingLong((AuditDtos.AggregationItem i) -> i.count).reversed())
        .toList();
  }

  private boolean isChainValidForEvent(UUID tenantId, AuditEvent event) {
    if (event == null) return false;
    if (event.getPrevEventHash() == null || event.getPrevEventHash().isBlank()) return true;
    Specification<AuditEvent> spec = (root, query, cb) -> cb.and(
        cb.equal(root.get("tenantId"), tenantId),
        cb.lessThanOrEqualTo(root.get("createdAt"), event.getCreatedAt()),
        cb.notEqual(root.get("id"), event.getId()));
    List<AuditEvent> prevCandidates = auditEventRepository.findAll(
        spec,
        PageRequest.of(0, 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).getContent();
    if (prevCandidates.isEmpty()) {
      return auditRetentionEventRepository.existsPurgeBoundaryBefore(tenantId, event.getCreatedAt());
    }
    return event.getPrevEventHash().equals(prevCandidates.get(0).getEventHash());
  }

  private List<String> parseStringList(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private Object parseJson(String json) {
    if (json == null || json.isBlank()) return null;
    try {
      return objectMapper.readTree(json);
    } catch (Exception ignored) {
      return json;
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      return "[]";
    }
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      return null;
    }
  }

  private int sanitizeLimit(Integer limit) {
    if (limit == null || limit < 1) return 50;
    return Math.min(limit, 200);
  }

  private List<String> normalizeList(List<String> values) {
    return values.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.trim().toUpperCase()).toList();
  }

  private String encodeCursor(AuditEvent event) {
    String payload = event.getCreatedAt().toString() + "|" + event.getId();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private CursorParts decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) return null;
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\|");
      if (parts.length != 2) return null;
      return new CursorParts(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (Exception ignored) {
      return null;
    }
  }

  private record CursorParts(Instant createdAt, UUID id) {}

  private Map<UUID, String> resolveActorNames(UUID tenantId, List<AuditEvent> events) {
    if (tenantId == null || events == null || events.isEmpty()) return Map.of();
    List<UUID> actorUserIds = events.stream()
        .map(AuditEvent::getActorUserId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    if (actorUserIds.isEmpty()) return Map.of();
    return usuarioRepository.mapNamesByTenantAndIds(tenantId, actorUserIds);
  }
}
