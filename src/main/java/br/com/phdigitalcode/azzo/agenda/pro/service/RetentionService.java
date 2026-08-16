package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditRetentionEvent;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionEventRepository;

/** Espelha {@code modules/audit/application/RetentionService.java}. */
@Service
public class RetentionService {

  private final AuditRetentionEventRepository auditRetentionEventRepository;
  private final ObjectMapper objectMapper;

  public RetentionService(
      AuditRetentionEventRepository auditRetentionEventRepository, ObjectMapper objectMapper) {
    this.auditRetentionEventRepository = auditRetentionEventRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public AuditRetentionEvent registerPurgeEvent(
      UUID tenantId,
      String policyVersion,
      int retentionPeriodDays,
      Instant windowStart,
      Instant windowEnd,
      long affectedRows,
      String executedBy,
      String executionId,
      Map<String, Object> reportMetadata) {
    if (policyVersion == null || policyVersion.isBlank()) throw new IllegalArgumentException("policyVersion obrigatoria");
    if (retentionPeriodDays < 1) throw new IllegalArgumentException("retentionPeriodDays invalido");
    if (windowStart == null || windowEnd == null) throw new IllegalArgumentException("janela de expurgo obrigatoria");
    if (executedBy == null || executedBy.isBlank()) throw new IllegalArgumentException("executedBy obrigatorio");
    if (executionId == null || executionId.isBlank()) throw new IllegalArgumentException("executionId obrigatorio");

    String reportJson = toJson(reportMetadata);
    String evidenceHash = sha256(
        String.join(
            "|",
            String.valueOf(tenantId),
            policyVersion.trim(),
            String.valueOf(retentionPeriodDays),
            String.valueOf(windowStart.toEpochMilli()),
            String.valueOf(windowEnd.toEpochMilli()),
            String.valueOf(affectedRows),
            executedBy.trim(),
            executionId.trim(),
            reportJson == null ? "" : reportJson));

    AuditRetentionEvent event = new AuditRetentionEvent();
    event.setTenantId(tenantId);
    event.setPolicyVersion(policyVersion.trim());
    event.setRetentionPeriodDays(retentionPeriodDays);
    event.setWindowStart(windowStart);
    event.setWindowEnd(windowEnd);
    event.setAffectedRows(affectedRows);
    event.setExecutedBy(executedBy.trim());
    event.setExecutionId(executionId.trim());
    event.setEvidenceHash(evidenceHash);
    auditRetentionEventRepository.save(event);
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
      throw new IllegalStateException("Falha ao gerar hash de retenção", e);
    }
  }
}
