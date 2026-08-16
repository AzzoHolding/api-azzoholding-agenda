package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationAttemptEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Espelha {@code modules/chat/application/WhatsAppBookingReactivationObservabilityService.java}. */
@Service
public class WhatsAppBookingReactivationObservabilityService {

  private static final Logger LOG =
      LoggerFactory.getLogger(WhatsAppBookingReactivationObservabilityService.class);

  private final MeterRegistry meterRegistry;

  public WhatsAppBookingReactivationObservabilityService(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void logCycleCreated(WhatsAppBookingReactivationCycleEntity cycle) {
    if (cycle == null) return;
    LOG.info(
        "[Reactivation] Ciclo criado: tenant={} cycle={} client={} user={} stage={}",
        cycle.getTenantId(),
        cycle.getId(),
        cycle.getClientId(),
        maskUserIdentifier(cycle.getUserIdentifier()),
        cycle.getLastStage());
    incrementCounter("whatsapp.reactivation.cycles.total", cycle.getTenantId(), "created", null, null);
  }

  public void logCycleUpdated(WhatsAppBookingReactivationCycleEntity cycle, WhatsAppBookingReactivationStage stage) {
    if (cycle == null) return;
    LOG.info(
        "[Reactivation] Ciclo atualizado: tenant={} cycle={} client={} user={} stage={} status={}",
        cycle.getTenantId(),
        cycle.getId(),
        cycle.getClientId(),
        maskUserIdentifier(cycle.getUserIdentifier()),
        stage,
        cycle.getStatus());
    incrementCounter(
        "whatsapp.reactivation.cycles.total",
        cycle.getTenantId(),
        "updated",
        null,
        stage != null ? stage.name() : null);
  }

  public void logCycleSkipped(
      UUID tenantId, UUID conversationId, UUID clientId, String responseStage, String reason) {
    LOG.info(
        "[Reactivation] Ciclo nao criado/atualizado: tenant={} conversation={} client={} assistantStage={} reason={}",
        tenantId,
        conversationId,
        clientId,
        responseStage,
        safeReason(reason));
    incrementCounter("whatsapp.reactivation.cycles.total", tenantId, "skipped", safeReason(reason), responseStage);
  }

  public void logClientReply(WhatsAppBookingReactivationCycleEntity cycle) {
    if (cycle == null) return;
    LOG.info(
        "[Reactivation] Cliente respondeu ao fluxo de reativacao: tenant={} cycle={} client={} user={}",
        cycle.getTenantId(),
        cycle.getId(),
        cycle.getClientId(),
        maskUserIdentifier(cycle.getUserIdentifier()));
    incrementCounter("whatsapp.reactivation.cycles.total", cycle.getTenantId(), "client_replied", null, null);
  }

  public void logCycleConverted(WhatsAppBookingReactivationCycleEntity cycle) {
    if (cycle == null) return;
    LOG.info(
        "[Reactivation] Ciclo convertido em agendamento: tenant={} cycle={} client={} appointment={} user={}",
        cycle.getTenantId(),
        cycle.getId(),
        cycle.getClientId(),
        cycle.getAppointmentIdCreatedAfterAbandonment(),
        maskUserIdentifier(cycle.getUserIdentifier()));
    incrementCounter("whatsapp.reactivation.cycles.total", cycle.getTenantId(), "converted", null, null);
  }

  public void logCycleCancelled(WhatsAppBookingReactivationCycleEntity cycle, String reason) {
    if (cycle == null) return;
    LOG.info(
        "[Reactivation] Ciclo cancelado: tenant={} cycle={} client={} user={} reason={}",
        cycle.getTenantId(),
        cycle.getId(),
        cycle.getClientId(),
        maskUserIdentifier(cycle.getUserIdentifier()),
        safeReason(reason));
    incrementCounter(
        "whatsapp.reactivation.cycles.total",
        cycle.getTenantId(),
        "cancelled",
        safeReason(reason),
        null);
  }

  public void logAttemptSent(
      WhatsAppBookingReactivationCycleEntity cycle, WhatsAppBookingReactivationAttemptEntity attempt) {
    if (cycle == null || attempt == null) return;
    LOG.info(
        "[Reactivation] Tentativa enviada: tenant={} cycle={} attempt={} user={} stage={}",
        cycle.getTenantId(),
        cycle.getId(),
        attempt.getAttemptNumber(),
        maskUserIdentifier(cycle.getUserIdentifier()),
        cycle.getLastStage());
    incrementCounter(
        "whatsapp.reactivation.attempts.total",
        cycle.getTenantId(),
        "sent",
        null,
        cycle.getLastStage() != null ? cycle.getLastStage().name() : "UNKNOWN");
  }

  public void logAttemptFailed(
      WhatsAppBookingReactivationCycleEntity cycle,
      WhatsAppBookingReactivationAttemptEntity attempt,
      String errorMessage) {
    if (cycle == null || attempt == null) return;
    LOG.warn(
        "[Reactivation] Falha ao enviar tentativa: tenant={} cycle={} attempt={} user={} error={}",
        cycle.getTenantId(),
        cycle.getId(),
        attempt.getAttemptNumber(),
        maskUserIdentifier(cycle.getUserIdentifier()),
        sanitizeForLog(errorMessage));
    incrementCounter(
        "whatsapp.reactivation.attempts.total",
        cycle.getTenantId(),
        "failed",
        sanitizeForMetric(errorMessage),
        cycle.getLastStage() != null ? cycle.getLastStage().name() : "UNKNOWN");
  }

  public void logAttemptCancelled(
      WhatsAppBookingReactivationCycleEntity cycle,
      WhatsAppBookingReactivationAttemptEntity attempt,
      String reason) {
    if (cycle == null || attempt == null) return;
    LOG.info(
        "[Reactivation] Tentativa cancelada: tenant={} cycle={} attempt={} user={} reason={}",
        cycle.getTenantId(),
        cycle.getId(),
        attempt.getAttemptNumber(),
        maskUserIdentifier(cycle.getUserIdentifier()),
        safeReason(reason));
    incrementCounter(
        "whatsapp.reactivation.attempts.total",
        cycle.getTenantId(),
        "cancelled",
        safeReason(reason),
        cycle.getLastStage() != null ? cycle.getLastStage().name() : "UNKNOWN");
  }

  public String maskUserIdentifier(String value) {
    String digits = normalizeDigits(value);
    if (digits.isBlank()) return "desconhecido";
    if (digits.length() <= 4) return "***" + digits;
    int visiblePrefix = Math.min(4, digits.length() - 2);
    int visibleSuffix = Math.min(2, digits.length() - visiblePrefix);
    String prefix = digits.substring(0, visiblePrefix);
    String suffix = digits.substring(digits.length() - visibleSuffix);
    return prefix + "*".repeat(Math.max(digits.length() - visiblePrefix - visibleSuffix, 0)) + suffix;
  }

  private void incrementCounter(String metricName, UUID tenantId, String event, String reason, String stage) {
    if (meterRegistry == null) return;
    try {
      Counter.Builder builder =
          Counter.builder(metricName)
              .tag("tenant_id", tenantId != null ? tenantId.toString() : "unknown")
              .tag("event", event != null ? event : "unknown");
      if (reason != null && !reason.isBlank()) {
        builder.tag("reason", reason);
      }
      if (stage != null && !stage.isBlank()) {
        builder.tag("stage", stage);
      }
      builder.register(meterRegistry).increment();
    } catch (Exception e) {
      LOG.warn("Falha ao registrar metricas tecnicas da reativacao event={} tenant={}", event, tenantId, e);
    }
  }

  private String normalizeDigits(String value) {
    if (value == null) return "";
    return value.replaceAll("\\D", "");
  }

  private String sanitizeForLog(String message) {
    if (message == null || message.isBlank()) return "erro nao especificado";
    return message.length() > 255 ? message.substring(0, 255) : message;
  }

  private String sanitizeForMetric(String message) {
    if (message == null || message.isBlank()) return "unspecified";
    String normalized = message.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    if (normalized.isBlank()) return "unspecified";
    return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
  }

  private String safeReason(String reason) {
    if (reason == null || reason.isBlank()) return "UNKNOWN";
    return reason.trim();
  }
}
