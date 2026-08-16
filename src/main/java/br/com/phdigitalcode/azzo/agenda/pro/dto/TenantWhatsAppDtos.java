package br.com.phdigitalcode.azzo.agenda.pro.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Espelha {@code modules/tenant/api/dto/TenantWhatsAppDtos.java}. */
public final class TenantWhatsAppDtos {

  private TenantWhatsAppDtos() {}

  // Patch leve: so preferencias operacionais, sem exigir token nem phoneNumberId
  public static class SettingsPatchRequest {
    public Boolean whatsappEnabled;
    public String usageProfile;
    public Boolean canSchedule;
    public Boolean canCancel;
    public Boolean canReschedule;
  }

  public static class UpdateRequest {
    public String accessToken;
    public String phoneNumberId;
    public String businessAccountId;
    public String businessId;
    public String displayPhoneNumber;
    public String webhookVerifyToken;
    public boolean whatsappEnabled;
    // REACTIVE_ONLY | NOTIFICATIONS | COMPLETE
    public String usageProfile;
    public Boolean canSchedule;
    public Boolean canCancel;
    public Boolean canReschedule;
    public String confirmationMessageTemplate;
    public String cancellationMessageTemplate;
    public String reminderMessageTemplate;
  }

  public static class ConfigResponse {
    public String phoneNumberId;
    public String businessAccountId;
    public String businessId;
    public String displayPhoneNumber;
    public String webhookVerifyToken;
    public boolean accessTokenConfigured;
    public boolean webhookVerifyTokenConfigured;
    public boolean whatsappEnabled;
    public String onboardingStatus;
    public String tokenSource;
    public boolean embeddedSignupEnabled;
    public String usageProfile;
    public boolean canSchedule;
    public boolean canCancel;
    public boolean canReschedule;
    public String confirmationMessageTemplate;
    public String cancellationMessageTemplate;
    public String reminderMessageTemplate;
  }

  public static class MessageLogItem {
    public String id;
    public String eventType;
    public String destinationPhone;
    public String messageText;
    public String providerMessageId;
    public String status;
    public String errorMessage;
    public String sentAt;
    public String appointmentId;
  }

  public static class MessageLogResponse {
    public java.util.List<MessageLogItem> items;
    public boolean hasMore;
    public String nextCursorSentAt;
  }

  public static class TestResponse {
    public boolean success;
    public boolean whatsappEnabled;
    public String message;
  }

  public static class EmbeddedSignupCompleteRequest {
    @NotBlank
    public String code;
    @NotNull
    @Valid
    public SetupInfo setupInfo;
  }

  public static class SetupInfo {
    @NotBlank
    public String wabaId;
    @NotBlank
    public String phoneNumberId;
    public String businessId;
    public String phoneNumber;
  }

  public static class EmbeddedSignupStatusResponse {
    public boolean connected;
    public boolean whatsappEnabled;
    public boolean accessTokenConfigured;
    public boolean webhookVerifyTokenConfigured;
    public String webhookVerifyToken;
    public String onboardingStatus;
    public String tokenSource;
    public String phoneNumberId;
    public String businessAccountId;
    public String businessId;
    public String displayPhoneNumber;
    public String lastError;
    public boolean embeddedSignupEnabled;
  }

  public static class ValidateRequest {
    @NotBlank
    public String accessToken;
    @NotBlank
    public String phoneNumberId;
  }

  public static class ValidateResponse {
    public boolean success;
    public String message;
    public String phoneNumberId;
    public String displayPhoneNumber;
    public String verifiedName;
  }

  public static class TestMessageRequest {
    @NotBlank
    public String destinationPhone;
    public String message;
  }

  public static class TestMessageResponse {
    public boolean success;
    public String message;
    public String providerMessageId;
  }
}
