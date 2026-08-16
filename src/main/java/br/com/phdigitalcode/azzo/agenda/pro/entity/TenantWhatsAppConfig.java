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
 * Espelha {@code domain/entity/TenantWhatsAppConfig.java}. Tabela {@code tenant_whatsapp_config}.
 *
 * <p>NOTA DE ESCOPO: apenas os campos usados pelo endpoint de toggle
 * ({@code PATCH /api/v1/tenant/toggle}) e pela integracao real de WhatsApp foram portados. A
 * integracao de envio/recebimento de mensagens (modulo {@code chat}/{@code messaging}) ainda nao
 * foi migrada — esta entidade existe aqui apenas para persistir as flags de configuracao.
 */
@Entity
@Table(name = "tenant_whatsapp_config")
@Getter
@Setter
public class TenantWhatsAppConfig {

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "whatsapp_access_token_enc", nullable = false)
  private String whatsappAccessTokenEnc = "";

  @Column(name = "whatsapp_phone_number_id")
  private String whatsappPhoneNumberId;

  @Column(name = "whatsapp_business_account_id")
  private String whatsappBusinessAccountId;

  @Column(name = "meta_business_id")
  private String metaBusinessId;

  @Column(name = "display_phone_number")
  private String displayPhoneNumber;

  @Column(name = "whatsapp_webhook_verify_token")
  private String whatsappWebhookVerifyTokenEnc;

  @Column(name = "whatsapp_webhook_verify_token_hash")
  private String whatsappWebhookVerifyTokenHash;

  @Column(name = "whatsapp_enabled", nullable = false)
  private boolean whatsappEnabled = false;

  @Column(name = "whatsapp_onboarding_status", nullable = false)
  private String whatsappOnboardingStatus = "NOT_STARTED";

  @Column(name = "whatsapp_token_source", nullable = false)
  private String whatsappTokenSource = "MANUAL";

  @Column(name = "embedded_signup_completed_at")
  private Instant embeddedSignupCompletedAt;

  @Column(name = "embedded_signup_last_error", length = 500)
  private String embeddedSignupLastError;

  /** REACTIVE_ONLY, NOTIFICATIONS ou COMPLETE — ver {@code ServicoTenantToggle} original. */
  @Column(name = "whatsapp_usage_profile", nullable = false)
  private String whatsappUsageProfile = "COMPLETE";

  @Column(name = "can_schedule", nullable = false)
  private boolean canSchedule = true;

  @Column(name = "can_cancel", nullable = false)
  private boolean canCancel = true;

  @Column(name = "can_reschedule", nullable = false)
  private boolean canReschedule = true;

  @Column(name = "confirmation_message_template")
  private String confirmationMessageTemplate;

  @Column(name = "cancellation_message_template")
  private String cancellationMessageTemplate;

  @Column(name = "reminder_message_template")
  private String reminderMessageTemplate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (whatsappAccessTokenEnc == null) whatsappAccessTokenEnc = "";
    if (whatsappOnboardingStatus == null || whatsappOnboardingStatus.isBlank()) whatsappOnboardingStatus = "NOT_STARTED";
    if (whatsappTokenSource == null || whatsappTokenSource.isBlank()) whatsappTokenSource = "MANUAL";
    if (whatsappUsageProfile == null || whatsappUsageProfile.isBlank()) whatsappUsageProfile = "COMPLETE";
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  /** Espelha {@code isProactiveAllowed()} do original. */
  public boolean isProactiveAllowed() {
    return "NOTIFICATIONS".equals(whatsappUsageProfile) || "COMPLETE".equals(whatsappUsageProfile);
  }

  /** Espelha {@code isReactivationAllowed()} do original. */
  public boolean isReactivationAllowed() {
    return "COMPLETE".equals(whatsappUsageProfile);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TenantWhatsAppConfig other)) return false;
    return tenantId != null && tenantId.equals(other.tenantId);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
