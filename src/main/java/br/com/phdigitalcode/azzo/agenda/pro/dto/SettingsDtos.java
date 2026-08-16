package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Espelha {@code application/dto/contract/SettingsDtos.java} — mesmos nomes de campo, mesmos
 * defaults e mesmas mensagens de validacao do original.
 */
public final class SettingsDtos {

  private SettingsDtos() {}

  public static class NotificationSettings {
    public boolean emailNotifications = true;
    public boolean smsNotifications = true;
    public boolean whatsappNotifications = true;
    public int reminderHours = 24;
  }

  public static class ReactivationSettings {
    public boolean enabled = true;
    public boolean respectBusinessHours = true;
    public String sendWindowStart = "09:00";
    public String sendWindowEnd = "19:00";
    public int maxAttemptsEnabled = 3;
  }

  public static class BusinessHoursDay {
    public String open;
    public String close;
    public boolean enabled;
  }

  public static class SettingsResponse {
    public NotificationSettings notifications = new NotificationSettings();
    public ReactivationSettings reactivation = new ReactivationSettings();
    public Map<String, BusinessHoursDay> businessHours = new LinkedHashMap<>();
  }

  // --- Business Hours (tabela relacional) ---

  public static class BusinessHoursItemRequest {
    @NotBlank(message = "Dia da semana e obrigatorio")
    public String dayOfWeek;

    @Pattern(
        regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
        message = "openTime deve estar no formato HH:mm")
    public String openTime;

    @Pattern(
        regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
        message = "closeTime deve estar no formato HH:mm")
    public String closeTime;

    public boolean enabled = true;

    @Pattern(
        regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
        message = "breakStart deve estar no formato HH:mm")
    public String breakStart;

    @Pattern(
        regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
        message = "breakEnd deve estar no formato HH:mm")
    public String breakEnd;
  }

  public static class BusinessHoursItemResponse {
    public String dayOfWeek;
    public String openTime;
    public String closeTime;
    public boolean enabled;
    /** Inicio da pausa (break). Pode ser null se nao houver pausa. */
    public String breakStart;
    /** Fim da pausa (break). Pode ser null se nao houver pausa. */
    public String breakEnd;
  }

  // --- LGPD ---

  public static class LgpdContactRequest {
    public String lgpdContactEmail;
    public String lgpdContactChannel;
    public Integer lgpdContactResponseSlaDays;
  }

  public static class LgpdContactResponse {
    public String lgpdContactEmail;
    public String lgpdContactChannel;
    public Integer lgpdContactResponseSlaDays;
  }

  // --- Feature Flags ---

  public static class TenantFeatureFlagsRequest {
    public Boolean asaasEnabled;
    public Boolean minioEnabled;
    public Integer chatRetentionDaysCompleted;
    public Integer chatRetentionDaysCanceled;
    public Integer chatRetentionDaysDefault;
    public Integer auditRetentionDays;
  }

  public static class TenantFeatureFlagsResponse {
    public boolean asaasEnabled;
    public boolean minioEnabled;
    public int chatRetentionDaysCompleted;
    public int chatRetentionDaysCanceled;
    public int chatRetentionDaysDefault;
    public int auditRetentionDays;
  }

  // --- Politica de Cancelamento (V93) ---

  public static class CancellationPolicyRequest {
    public Boolean cancellationPolicyEnabled;
    public Integer cancellationMinHoursBefore;
    public Integer cancellationFeePercent;
  }

  public static class CancellationPolicyResponse {
    public boolean cancellationPolicyEnabled;
    public int cancellationMinHoursBefore;
    public int cancellationFeePercent;
  }

  // --- Regua de lembretes (F03 — V120) ---

  public static class ReminderSettingsRequest {
    public Boolean d1Habilitado;
    public String d1Hora;
    public Boolean horasAntesHabilitado;
    public Integer horasAntes;
  }

  public static class ReminderSettingsResponse {
    public boolean d1Habilitado;
    public String d1Hora;
    public boolean horasAntesHabilitado;
    public int horasAntes;
  }

  // --- Email Templates (por tenant) ---

  public static class EmailTemplateRequest {
    public String subjectTemplate;
    public String htmlTemplate;
    public String fromEmail;
    public String fromName;
    public String replyTo;
  }

  public static class EmailTemplateResponse {
    public String type;
    public String label;
    public String subjectTemplate;
    public String htmlTemplate;
    public String fromEmail;
    public String fromName;
    public String replyTo;
    public boolean active;
    public boolean tenantCustomized;
  }

  public static class EmailTemplateListResponse {
    public List<EmailTemplateResponse> templates;
  }
}
