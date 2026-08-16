package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditTermsAcceptance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsLifecycleEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsVersion;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditTermsAcceptanceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TermsLifecycleEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TermsVersionRepository;

/** Espelha {@code modules/audit/application/TermsService.java}. */
@Service
public class TermsService {

  private final TermsVersionRepository termsVersionRepository;
  private final TermsLifecycleEventRepository termsLifecycleEventRepository;
  private final AuditTermsAcceptanceRepository termsAcceptanceRepository;
  private final ObjectMapper objectMapper;

  public TermsService(
      TermsVersionRepository termsVersionRepository,
      TermsLifecycleEventRepository termsLifecycleEventRepository,
      AuditTermsAcceptanceRepository termsAcceptanceRepository,
      ObjectMapper objectMapper) {
    this.termsVersionRepository = termsVersionRepository;
    this.termsLifecycleEventRepository = termsLifecycleEventRepository;
    this.termsAcceptanceRepository = termsAcceptanceRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public TermsVersion publishVersion(
      String documentType, String version, String title, String content, UUID publishedBy) {
    if (documentType == null || documentType.isBlank()) throw new IllegalArgumentException("documentType obrigatorio");
    if (version == null || version.isBlank()) throw new IllegalArgumentException("version obrigatoria");
    if (title == null || title.isBlank()) throw new IllegalArgumentException("title obrigatorio");
    if (content == null || content.isBlank()) throw new IllegalArgumentException("content obrigatorio");
    if (termsVersionRepository.findByDocumentTypeAndVersion(documentType.trim(), version.trim()).isPresent()) {
      throw new IllegalArgumentException("Versao de termo ja existe para o documentType informado");
    }

    TermsVersion termsVersion = new TermsVersion();
    termsVersion.setDocumentType(documentType.trim().toUpperCase());
    termsVersion.setVersion(version.trim());
    termsVersion.setTitle(title.trim());
    termsVersion.setContent(content);
    termsVersion.setContentHash(sha256(content));
    termsVersion.setPublishedBy(publishedBy);
    termsVersionRepository.save(termsVersion);

    createLifecycleEvent(termsVersion.getId(), AuditConstants.TermsEventType.PUBLISHED, publishedBy, null);
    return termsVersion;
  }

  @Transactional
  public TermsLifecycleEvent disableVersion(UUID termsVersionId, UUID actorUserId, String reason) {
    TermsVersion termsVersion = termsVersionRepository.findById(termsVersionId)
        .orElseThrow(() -> new IllegalArgumentException("Versao de termo nao encontrada"));

    String metadata = toJson(Map.of("reason", reason == null ? "" : reason.trim()));
    return createLifecycleEvent(termsVersion.getId(), AuditConstants.TermsEventType.DISABLED, actorUserId, metadata);
  }

  @Transactional
  public AuditTermsAcceptance accept(
      UUID tenantId, UUID userId, UUID termsVersionId, String requestId, String ipAddress) {
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatorio");
    if (userId == null) throw new IllegalArgumentException("userId obrigatorio");
    if (termsVersionId == null) throw new IllegalArgumentException("termsVersionId obrigatorio");
    TermsVersion termsVersion = termsVersionRepository.findById(termsVersionId)
        .orElseThrow(() -> new IllegalArgumentException("Versao de termo nao encontrada"));

    AuditTermsAcceptance acceptance = new AuditTermsAcceptance();
    acceptance.setTenantId(tenantId);
    acceptance.setUserId(userId);
    acceptance.setTermsVersionId(termsVersionId);
    acceptance.setRequestId(requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim());
    acceptance.setIpAddress(ipAddress == null || ipAddress.isBlank() ? null : ipAddress.trim());
    acceptance.setAcceptedAt(Instant.now());
    acceptance.setAcceptanceHash(sha256(
        String.join(
            "|",
            tenantId.toString(),
            userId.toString(),
            termsVersionId.toString(),
            acceptance.getRequestId(),
            String.valueOf(acceptance.getAcceptedAt().toEpochMilli()),
            termsVersion.getContentHash())));

    termsAcceptanceRepository.save(acceptance);
    return acceptance;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getAcceptanceProof(UUID acceptanceId) {
    AuditTermsAcceptance acceptance = termsAcceptanceRepository.findById(acceptanceId)
        .orElseThrow(() -> new IllegalArgumentException("Aceite nao encontrado"));
    TermsVersion version = termsVersionRepository.findById(acceptance.getTermsVersionId())
        .orElseThrow(() -> new IllegalArgumentException("Versao de termo nao encontrada para o aceite"));
    return Map.ofEntries(
        Map.entry("acceptanceId", acceptance.getId().toString()),
        Map.entry("tenantId", acceptance.getTenantId().toString()),
        Map.entry("userId", acceptance.getUserId().toString()),
        Map.entry("acceptedAt", acceptance.getAcceptedAt().toString()),
        Map.entry("requestId", acceptance.getRequestId()),
        Map.entry("termsVersionId", version.getId().toString()),
        Map.entry("documentType", version.getDocumentType()),
        Map.entry("version", version.getVersion()),
        Map.entry("title", version.getTitle()),
        Map.entry("contentHash", version.getContentHash()),
        Map.entry("acceptanceHash", acceptance.getAcceptanceHash()));
  }

  @Transactional(readOnly = true)
  public Optional<TermsVersion> getLatestActiveVersion(String documentType) {
    if (documentType == null || documentType.isBlank()) return Optional.empty();
    List<TermsVersion> versions = termsVersionRepository.listByDocumentTypeNewestFirst(documentType);
    for (TermsVersion version : versions) {
      if (!isDisabled(version.getId())) return Optional.of(version);
    }
    return Optional.empty();
  }

  @Transactional(readOnly = true)
  public TermsVersion requireActiveVersion(String documentType, String version) {
    if (documentType == null || documentType.isBlank()) throw new IllegalArgumentException("documentType obrigatorio");
    if (version == null || version.isBlank()) throw new IllegalArgumentException("version obrigatoria");
    TermsVersion termsVersion = termsVersionRepository
        .findByDocumentTypeAndVersion(documentType.trim(), version.trim())
        .orElseThrow(() -> new IllegalArgumentException("Versao de termo nao encontrada para o documentType informado"));
    if (isDisabled(termsVersion.getId())) {
      throw new IllegalArgumentException("Versao de termo desativada para aceite");
    }
    return termsVersion;
  }

  private boolean isDisabled(UUID termsVersionId) {
    return termsLifecycleEventRepository.findLastByTermsVersionId(termsVersionId)
        .map(e -> AuditConstants.TermsEventType.DISABLED.equalsIgnoreCase(e.getEventType()))
        .orElse(false);
  }

  private TermsLifecycleEvent createLifecycleEvent(
      UUID termsVersionId, String eventType, UUID createdBy, String metadataJson) {
    TermsLifecycleEvent event = new TermsLifecycleEvent();
    event.setTermsVersionId(termsVersionId);
    event.setEventType(eventType);
    event.setCreatedBy(createdBy);
    event.setEventMetadataJson(metadataJson);
    termsLifecycleEventRepository.save(event);
    return event;
  }

  private String toJson(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      return null;
    }
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao gerar hash do termo", e);
    }
  }
}
