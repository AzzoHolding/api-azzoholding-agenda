package br.com.phdigitalcode.azzo.agenda.pro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Porte de {@code infrastructure/security/StartupSecurityValidator.java} (Quarkus
 * {@code @Observes StartupEvent} -> Spring {@code ApplicationReadyEvent}). Fail-closed em
 * producao (profile {@code prod}) se segredos criticos estiverem com valor default inseguro ou
 * ausentes; apenas loga aviso em dev.
 */
@Component
public class StartupSecurityValidator {

  private static final Logger LOG = LoggerFactory.getLogger(StartupSecurityValidator.class);
  private static final String DEFAULT_ENCRYPTION_KEY = "AzzoAgendaProDefaultKey123456789";
  private static final String DEFAULT_INTERNAL_KEY = "changeme-dev";

  @Value("${app.security.encryption-key:" + DEFAULT_ENCRYPTION_KEY + "}")
  private String encryptionKey;

  @Value("${app.internal.api-key:" + DEFAULT_INTERNAL_KEY + "}")
  private String internalApiKey;

  @Value("${app.lgpd.contact.email:__unset__}")
  private String lgpdContactEmail;

  @Value("${app.meta.app-secret:__unset__}")
  private String metaAppSecret;

  @Value("${app.agenda.debug:false}")
  private boolean agendaDebugEnabled;

  @Value("${spring.profiles.active:dev}")
  private String activeProfile;

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    boolean isProd = activeProfile != null && activeProfile.contains("prod");

    if (DEFAULT_ENCRYPTION_KEY.equals(encryptionKey)) {
      if (isProd) {
        throw new IllegalStateException(
            "SEGURANCA: app.security.encryption-key esta com o valor default inseguro. "
                + "Defina ENCRYPTION_KEY como variavel de ambiente antes de iniciar em producao.");
      }
      LOG.warn("SEGURANCA: app.security.encryption-key esta com o valor default. Nao use em producao.");
    }

    if (DEFAULT_INTERNAL_KEY.equals(internalApiKey)) {
      if (isProd) {
        throw new IllegalStateException(
            "SEGURANCA: app.internal.api-key esta com o valor default inseguro. "
                + "Defina INTERNAL_API_KEY como variavel de ambiente antes de iniciar em producao.");
      }
      LOG.warn("SEGURANCA: app.internal.api-key esta com o valor default. Nao use em producao.");
    }

    if ("__unset__".equals(lgpdContactEmail) || lgpdContactEmail.isBlank()) {
      if (isProd) {
        throw new IllegalStateException(
            "LGPD: app.lgpd.contact.email nao configurado. "
                + "Defina LGPD_CONTACT_EMAIL antes de iniciar em producao.");
      }
      LOG.warn("LGPD: app.lgpd.contact.email nao configurado. Configure antes de ir para producao.");
    }

    boolean metaSecretUnset = "__unset__".equals(metaAppSecret) || metaAppSecret == null || metaAppSecret.isBlank();
    if (isProd && metaSecretUnset) {
      throw new IllegalStateException(
          "SEGURANCA: app.meta.app-secret nao configurado em producao. "
              + "O webhook do WhatsApp exige META_APP_SECRET para validar a assinatura HMAC. "
              + "Defina META_APP_SECRET antes de iniciar.");
    }

    if (isProd && agendaDebugEnabled) {
      throw new IllegalStateException(
          "SEGURANCA/LGPD: app.agenda.debug=true em producao grava PII e payloads em log. "
              + "Defina APP_AGENDA_DEBUG=false em producao.");
    }
  }
}
