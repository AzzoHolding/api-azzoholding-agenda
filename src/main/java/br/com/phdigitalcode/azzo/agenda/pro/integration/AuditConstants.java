package br.com.phdigitalcode.azzo.agenda.pro.integration;

/**
 * Espelha {@code modules/audit/application/AuditConstants.java}, agora com o modulo {@code audit}
 * portado por completo.
 *
 * <p><b>Nota de correcao (sessao de fechamento do modulo audit)</b>: a versao anterior deste
 * arquivo era um PLACEHOLDER usado pelos dominios fundacionais ({@code security/common},
 * {@code auth}) antes de o modulo {@code audit} existir aqui. Ela havia INVENTADO valores de
 * {@code Module} que nao existem no original ({@code POS}, {@code BILLING}, {@code PACKAGE},
 * {@code MEMBERSHIP}, {@code ONBOARDING}) e um valor de {@code Action}
 * ({@code TERMS_ACCEPTED}) tambem inexistente no original — {@code ServicoOnboarding} sempre usou
 * a string literal {@code "TERMS_ACCEPTED"} diretamente, nos dois lados (Quarkus e Spring), nunca
 * essa constante. Nenhum desses valores inventados era referenciado em nenhum outro lugar do
 * codigo (confirmado via busca antes desta alteracao), entao a troca para o conjunto canonico do
 * original abaixo e segura. Ver {@code MIGRACAO-QUARKUS-SPRING.md}.
 */
public final class AuditConstants {

  private AuditConstants() {}

  public static final class Module {
    public static final String FISCAL = "FISCAL";
    public static final String RBAC = "RBAC";
    public static final String FINANCE = "FINANCE";
    public static final String AUTH = "AUTH";
    public static final String APPOINTMENT = "APPOINTMENT";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String SERVICE = "SERVICE";
    public static final String PROFESSIONAL = "PROFESSIONAL";
    public static final String INVENTORY = "INVENTORY";
    public static final String CHAT = "CHAT";
    public static final String LGPD = "LGPD";
    public static final String SYSTEM = "SYSTEM";
    public static final String WHATSAPP = "WHATSAPP";
    public static final String TENANT = "TENANT";
    public static final String SETTINGS = "SETTINGS";

    private Module() {}
  }

  public static final class Action {
    public static final String BUSINESS_HOURS_UPDATED = "BUSINESS_HOURS_UPDATED";
    public static final String SPECIAL_CLOSURE_CREATED = "SPECIAL_CLOSURE_CREATED";
    public static final String SPECIAL_CLOSURE_UPDATED = "SPECIAL_CLOSURE_UPDATED";
    public static final String SPECIAL_CLOSURE_DELETED = "SPECIAL_CLOSURE_DELETED";
    public static final String APPOINTMENT_CANCELLED_BY_CLOSURE = "APPOINTMENT_CANCELLED_BY_CLOSURE";

    // Reativação WhatsApp
    public static final String REACTIVATION_CYCLE_CREATED = "REACTIVATION_CYCLE_CREATED";
    public static final String REACTIVATION_MESSAGE_SENT = "REACTIVATION_MESSAGE_SENT";
    public static final String REACTIVATION_OPT_OUT = "REACTIVATION_OPT_OUT";
    public static final String REACTIVATION_OPT_IN = "REACTIVATION_OPT_IN";
    public static final String REACTIVATION_CONVERTED = "REACTIVATION_CONVERTED";
    public static final String REACTIVATION_CONFIG_UPDATED = "REACTIVATION_CONFIG_UPDATED";

    private Action() {}
  }

  public static final class Status {
    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";
    public static final String DENIED = "DENIED";

    private Status() {}
  }

  public static final class SourceChannel {
    public static final String API = "API";
    public static final String WEBHOOK = "WEBHOOK";
    public static final String SCHEDULER = "SCHEDULER";
    public static final String SYSTEM = "SYSTEM";

    private SourceChannel() {}
  }

  public static final class TermsEventType {
    public static final String PUBLISHED = "PUBLISHED";
    public static final String DISABLED = "DISABLED";
    public static final String REPLACED_BY = "REPLACED_BY";

    private TermsEventType() {}
  }

  public static final class TermsDocumentType {
    public static final String TERMS_OF_USE = "TERMS_OF_USE";
    public static final String PRIVACY_POLICY = "PRIVACY_POLICY";

    private TermsDocumentType() {}
  }
}
