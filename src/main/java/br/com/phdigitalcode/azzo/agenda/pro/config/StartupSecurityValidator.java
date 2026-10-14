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

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @Value("${app.auth.cookie.same-site:STRICT}")
  private String cookieSameSite;

  @Value("${app.storage.minio.enabled:${MINIO_ENABLED:false}}")
  private boolean minioEnabled;

  @Value("${app.storage.minio.access-key:${MINIO_ACCESS_KEY:minioadmin}}")
  private String minioAccessKey;

  @Value("${app.storage.minio.secret-key:${MINIO_SECRET_KEY:minioadmin123}}")
  private String minioSecretKey;

  /**
   * Pentest 14/10/2026 (SEG-019): a validacao so valia se o nome do perfil CONTIVESSE "prod" — um
   * perfil "producao", "staging" ou esquecido aceitava os segredos padrao. Agora o padrao inseguro
   * so e tolerado nos perfis de desenvolvimento declarados; qualquer outro e tratado como producao.
   */
  static boolean ehAmbienteDeDesenvolvimento(String perfis) {
    if (perfis == null || perfis.isBlank()) return false;
    for (String perfil : perfis.split(",")) {
      String p = perfil.trim().toLowerCase(java.util.Locale.ROOT);
      if (p.equals("dev") || p.equals("test") || p.equals("local")) return true;
    }
    return false;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    boolean isProd = !ehAmbienteDeDesenvolvimento(activeProfile);

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

    // SEG-017: a protecao contra CSRF e o SameSite do cookie de sessao (o CSRF do Spring esta
    // desligado). SameSite=None em producao abriria o envio do cookie a partir de outro site.
    if (isProd && cookieSameSite != null && cookieSameSite.trim().equalsIgnoreCase("none")) {
      throw new IllegalStateException(
          "SEGURANCA: app.auth.cookie.same-site=None em producao desliga a protecao contra CSRF. "
              + "Use STRICT (padrao) ou LAX.");
    }

    // INT-029: MinIO com a credencial padrao. So ALERTA por enquanto: em 14/10/2026 a producao ainda
    // usava minioadmin, e recusar subir derrubaria a API. Trocar por throw depois de rodar
    // relatorio-pentest/minio-trocar-credenciais.sh.
    if (isProd && minioEnabled
        && ("minioadmin".equals(minioAccessKey) || "minioadmin123".equals(minioSecretKey))) {
      LOG.error("SEGURANCA: MinIO com a credencial padrao (minioadmin). Troque MINIO_ROOT_USER/"
          + "MINIO_ROOT_PASSWORD — qualquer processo na rede interna le e grava todos os arquivos.");
    }

    if (isProd && agendaDebugEnabled) {
      throw new IllegalStateException(
          "SEGURANCA/LGPD: app.agenda.debug=true em producao grava PII e payloads em log. "
              + "Defina APP_AGENDA_DEBUG=false em producao.");
    }
  }
}
