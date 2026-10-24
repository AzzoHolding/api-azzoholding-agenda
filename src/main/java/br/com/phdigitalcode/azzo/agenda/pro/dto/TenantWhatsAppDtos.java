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
    /**
     * O numero foi registrado no Cloud API? <b>Sem isso ele nao envia mensagem.</b>
     *
     * Numero pode estar verificado, com o nome certo, e ainda assim mudo — e por isso a tela
     * precisa dizer isto separado de "conectado".
     */
    public boolean numeroRegistrado;
    /**
     * O PIN de verificacao em duas etapas do numero, em claro.
     *
     * O Azzo define o PIN de um numero novo, e o dono precisa dele para levar o numero para outro
     * provedor: esconder seria prender o cliente ao Azzo por falta de informacao. So OWNER chega
     * aqui — o controller inteiro e `hasRole('OWNER')`.
     */
    public String registrationPin;
    /**
     * Template aprovado para a confirmacao de agendamento.
     *
     * Vazio = a confirmacao nao chega a cliente novo, porque texto livre so e entregue a quem
     * escreveu para o salao nas ultimas 24 horas.
     */
    public String confirmationTemplateName;
    public String confirmationTemplateLanguage;
  }

  /** O template de confirmacao e cadastrado pelo salao: cada um aprova o seu texto na Meta. */
  public static class TemplateDeConfirmacaoRequest {
    public String templateName;
    public String templateLanguage;
  }

  /**
   * Um template na conta do salao, com o estado da analise da Meta.
   *
   * <b>Criar nao e aprovar</b>: ate o status virar APPROVED, esse template nao entrega nada.
   */
  public static class TemplateItem {
    public String finalidade;
    public String nome;
    public String idioma;
    public String status;
    public String motivoRecusa;
    public String corpo;
    /** As variaveis em ordem: e o que liga {{1}} ao nome do cliente no envio. */
    public String variaveis;
  }

  public static class TemplatesResponse {
    public java.util.List<TemplateItem> items = new java.util.ArrayList<>();
  }

  public static class RegistroDoNumeroResponse {
    public boolean success;
    public String message;
    /** O PIN usado, para o dono guardar: sem ele o numero nao migra de provedor. */
    public String registrationPin;
    /** O webhook da conta comercial tambem foi inscrito? Sem isso o salao envia mas nao recebe. */
    public boolean webhookInscrito;
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
    /**
     * Texto livre. <b>So chega se o destinatario escreveu para o salao nas ultimas 24h.</b>
     *
     * Vazio — o caso normal do botao de teste — manda TEMPLATE, que e o unico que a Meta entrega
     * em primeiro contato. Preencher e pedir explicitamente o caminho de texto livre.
     */
    public String message;
    /** Template a enviar. Vazio usa o configurado (`hello_world`, que todo numero novo tem). */
    public String templateName;
    /** Idioma do template, como cadastrado na Meta. Vazio usa o configurado (`en_US`). */
    public String templateLanguage;
  }

  public static class TestMessageResponse {
    public boolean success;
    public String message;
    public String providerMessageId;
  }
}
