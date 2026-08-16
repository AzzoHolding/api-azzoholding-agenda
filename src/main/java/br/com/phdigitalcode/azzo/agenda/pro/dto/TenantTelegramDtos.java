package br.com.phdigitalcode.azzo.agenda.pro.dto;

import jakarta.validation.constraints.NotBlank;

/** Espelha {@code modules/tenant/api/dto/TenantTelegramDtos.java}. */
public final class TenantTelegramDtos {

  private TenantTelegramDtos() {}

  public static class UpdateRequest {
    public String botToken;
    public String botUsername;
    public String webhookSecretToken;
    public boolean telegramEnabled;
  }

  public static class ConfigResponse {
    public String botUsername;
    public boolean botTokenConfigured;
    public boolean webhookSecretTokenConfigured;
    public String webhookSecretToken;
    public boolean telegramEnabled;
    public String inboundWebhookUrl;
    public WebhookInfo webhook;
  }

  public static class WebhookInfo {
    public boolean configured;
    public String url;
    public boolean hasCustomCertificate;
    public int pendingUpdateCount;
    public String lastErrorDate;
    public String lastErrorMessage;
    public String ipAddress;
  }

  public static class ValidateRequest {
    @NotBlank
    public String botToken;
  }

  public static class ValidateResponse {
    public boolean success;
    public String message;
    public String botUsername;
    public String botDisplayName;
  }

  public static class TestResponse {
    public boolean success;
    public boolean telegramEnabled;
    public String message;
  }

  public static class TestMessageRequest {
    @NotBlank
    public String destinationChatId;
    public String message;
  }

  public static class TestMessageResponse {
    public boolean success;
    public String message;
    public String providerMessageId;
  }
}
