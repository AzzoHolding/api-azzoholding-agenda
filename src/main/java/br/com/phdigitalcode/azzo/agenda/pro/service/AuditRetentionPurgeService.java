package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionConfigRepository;

/** Espelha {@code modules/audit/application/AuditRetentionPurgeService.java}. */
@Service
public class AuditRetentionPurgeService {

  private static final Logger LOG = LoggerFactory.getLogger(AuditRetentionPurgeService.class);

  private final AuditEventRepository auditEventRepository;
  private final RetentionService retentionService;
  private final AuditRetentionConfigRepository auditRetentionConfigRepository;

  @Value("${app.audit.retention.days:365}")
  private int fallbackRetentionDays;

  @Value("${app.audit.retention.policy-version:v1}")
  private String policyVersion;

  @Value("${app.audit.retention.max-tenants-per-run:500}")
  private int maxTenantsPerRun;

  public AuditRetentionPurgeService(
      AuditEventRepository auditEventRepository,
      RetentionService retentionService,
      AuditRetentionConfigRepository auditRetentionConfigRepository) {
    this.auditEventRepository = auditEventRepository;
    this.retentionService = retentionService;
    this.auditRetentionConfigRepository = auditRetentionConfigRepository;
  }

  public long purgeExpiredAuditEvents() {
    int days = resolveRetentionDays();
    Instant cutoff = Instant.now().minusSeconds(days * 86400L);
    List<UUID> tenantIds = auditEventRepository.findTenantIdsWithEventsBefore(cutoff, maxTenantsPerRun);
    LOG.info("AuditRetentionPurge iniciado. tenants={} cutoff={}", tenantIds.size(), cutoff);

    if (tenantIds.isEmpty()) {
      LOG.info("AuditRetentionPurge finalizado sem tenants elegiveis.");
      return 0;
    }

    String executionId = UUID.randomUUID().toString();
    long totalPurged = 0;
    for (UUID tenantId : tenantIds) {
      totalPurged += purgeTenant(tenantId, cutoff, days, executionId);
    }

    LOG.info(
        "AuditRetentionPurge finalizado. executionId={} removidos={} tenants={}",
        executionId,
        totalPurged,
        tenantIds.size());
    return totalPurged;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long purgeTenant(UUID tenantId, Instant cutoff, int days, String executionId) {
    Instant windowStart = auditEventRepository.findOldestCreatedAtBefore(tenantId, cutoff).orElse(cutoff);
    long purged = auditEventRepository.purgeBeforeByTenant(tenantId, cutoff);
    if (purged <= 0) return 0;

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("scheduler", "AuditRetentionPurgeScheduler");
    metadata.put("cutoff", cutoff.toString());
    metadata.put("tenantId", tenantId.toString());
    retentionService.registerPurgeEvent(
        tenantId,
        policyVersion,
        days,
        windowStart,
        cutoff,
        purged,
        "SCHEDULER",
        executionId,
        metadata);
    return purged;
  }

  private int resolveRetentionDays() {
    Integer fromDatabase = auditRetentionConfigRepository.findRetentionPeriodDays().orElse(null);
    if (fromDatabase != null && fromDatabase > 0) {
      return fromDatabase;
    }
    int fallback = Math.max(fallbackRetentionDays, 1);
    LOG.warn("AuditRetentionPurge usando fallback de {} dias.", fallback);
    return fallback;
  }
}
