package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/settings/domain/entity/TenantOperationalSettingsEntity.java}. Tabela
 * {@code tenant_operational_settings} — a PK e o proprio {@code tenant_id} (uma linha por tenant).
 *
 * <p>Os defaults de campo sao os mesmos do original, e o {@link #prePersist()} repete as mesmas
 * normalizacoes defensivas (janela de reativacao, limite de tentativas, JSON de horarios,
 * {@code reminderHours} e {@code d1ReminderHora}).
 */
@Entity
@Table(name = "tenant_operational_settings")
@Getter
@Setter
public class TenantOperationalSettings {

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "email_notifications", nullable = false)
  private boolean emailNotifications = true;

  @Column(name = "sms_notifications", nullable = false)
  private boolean smsNotifications = true;

  @Column(name = "whatsapp_notifications", nullable = false)
  private boolean whatsappNotifications = true;

  /**
   * Horas antes do horario marcado para o lembrete adicional (F03) — usado apenas quando
   * {@code hoursBeforeReminderEnabled=true}.
   */
  @Column(name = "reminder_hours", nullable = false)
  private int reminderHours = 24;

  // Regua de lembretes (F03)

  @Column(name = "d1_reminder_enabled", nullable = false)
  private boolean d1ReminderEnabled = true;

  @Column(name = "d1_reminder_hora", nullable = false, length = 5)
  private String d1ReminderHora = "18:00";

  @Column(name = "hours_before_reminder_enabled", nullable = false)
  private boolean hoursBeforeReminderEnabled = false;

  @Column(name = "reactivation_enabled", nullable = false)
  private boolean reactivationEnabled = true;

  @Column(name = "reactivation_respect_business_hours", nullable = false)
  private boolean reactivationRespectBusinessHours = true;

  @Column(name = "reactivation_send_window_start", nullable = false, length = 5)
  private String reactivationSendWindowStart = "09:00";

  @Column(name = "reactivation_send_window_end", nullable = false, length = 5)
  private String reactivationSendWindowEnd = "19:00";

  @Column(name = "reactivation_max_attempts_enabled", nullable = false)
  private int reactivationMaxAttemptsEnabled = 3;

  @Column(name = "business_hours_json", nullable = false, columnDefinition = "TEXT")
  private String businessHoursJson = "[]";

  // LGPD (V77)

  @Column(name = "lgpd_contact_email", length = 255)
  private String lgpdContactEmail;

  @Column(name = "lgpd_contact_channel", length = 100)
  private String lgpdContactChannel;

  @Column(name = "lgpd_contact_response_sla_days")
  private Integer lgpdContactResponseSlaDays = 15;

  // Feature Flags (V78)

  @Column(name = "asaas_enabled", nullable = false)
  private boolean asaasEnabled = false;

  @Column(name = "minio_enabled", nullable = false)
  private boolean minioEnabled = false;

  @Column(name = "chat_retention_days_completed", nullable = false)
  private int chatRetentionDaysCompleted = 180;

  @Column(name = "chat_retention_days_canceled", nullable = false)
  private int chatRetentionDaysCanceled = 90;

  @Column(name = "chat_retention_days_default", nullable = false)
  private int chatRetentionDaysDefault = 365;

  @Column(name = "audit_retention_days", nullable = false)
  private int auditRetentionDays = 365;

  // Politica de cancelamento (V93)

  @Column(name = "cancellation_policy_enabled", nullable = false)
  private boolean cancellationPolicyEnabled = false;

  @Column(name = "cancellation_min_hours_before", nullable = false)
  private int cancellationMinHoursBefore = 24;

  @Column(name = "cancellation_fee_percent", nullable = false)
  private int cancellationFeePercent = 0;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (reactivationSendWindowStart == null || reactivationSendWindowStart.isBlank()) {
      reactivationSendWindowStart = "09:00";
    }
    if (reactivationSendWindowEnd == null || reactivationSendWindowEnd.isBlank()) {
      reactivationSendWindowEnd = "19:00";
    }
    if (reactivationMaxAttemptsEnabled <= 0 || reactivationMaxAttemptsEnabled > 3) {
      reactivationMaxAttemptsEnabled = 3;
    }
    if (businessHoursJson == null || businessHoursJson.isBlank()) {
      businessHoursJson = "[]";
    }
    if (reminderHours <= 0) reminderHours = 24;
    if (d1ReminderHora == null || d1ReminderHora.isBlank()) d1ReminderHora = "18:00";
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
