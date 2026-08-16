package br.com.phdigitalcode.azzo.agenda.pro.integration;

import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code infrastructure/notification/CredentialsEmailService.java}. Envio SMTP real via
 * {@link JavaMailSender} (Spring), configurado em {@code config/MailConfig} a partir das mesmas
 * chaves {@code app.mail.*} do original ({@code jakarta.mail.Session}/{@code Transport} puros).
 *
 * <p>Mantido o mesmo comportamento de fallback console: quando {@code app.mail.credentials.enabled}
 * e {@code false}, ou o envio SMTP falha e {@code console-fallback-enabled} e {@code true}, nenhuma
 * excecao sobe — so loga (sem senha nem nome, por LGPD, exatamente como o original).
 */
@Service
public class CredentialsEmailService {

  private static final Logger LOG = LoggerFactory.getLogger(CredentialsEmailService.class);
  private static final String DEFAULT_FROM_FALLBACK = "no-reply@seudominio.com";
  public static final String DELIVERY_STATUS_SMTP_ACCEPTED = "SMTP_ACCEPTED";
  public static final String DELIVERY_STATUS_FALLBACK = "FALLBACK";
  public static final String DELIVERY_STATUS_DISABLED = "DISABLED";
  public static final String DELIVERY_STATUS_CONFIG_ERROR = "CONFIG_ERROR";

  private final JavaMailSender mailSender;
  private final boolean enabled;
  private final String from;
  private final boolean consoleFallbackEnabled;

  public CredentialsEmailService(
      JavaMailSender mailSender,
      @Value("${app.mail.credentials.enabled:false}") boolean enabled,
      @Value("${app.mail.from:no-reply@azzoholding.com.br}") String from,
      @Value("${app.mail.credentials.console-fallback-enabled:true}") boolean consoleFallbackEnabled) {
    this.mailSender = mailSender;
    this.enabled = enabled;
    this.from = from;
    this.consoleFallbackEnabled = consoleFallbackEnabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void sendProfessionalAccess(String toEmail, String professionalName, String loginEmail, String rawPassword) {
    String subject = "Seu acesso ao Azzo Agenda Pro";
    String body = """
        Ola %s,

        Seu acesso foi criado com sucesso.

        Usuario: %s
        Senha: %s

        Recomendamos alterar sua senha no primeiro acesso.
        """.formatted(
        professionalName == null || professionalName.isBlank() ? "profissional" : professionalName.trim(),
        loginEmail == null ? "" : loginEmail.trim(),
        rawPassword == null ? "" : rawPassword);
    sendWithFallback(toEmail, subject, body, "PROFISSIONAL");
  }

  public void sendTemporaryPasswordReset(String toEmail, String professionalName, String loginEmail, String rawPassword) {
    String subject = "Senha temporaria - Azzo Agenda Pro";
    String body = """
        Ola %s,

        Sua senha foi resetada.

        Usuario: %s
        Senha temporaria: %s

        Altere a senha apos entrar no sistema.
        """.formatted(
        professionalName == null || professionalName.isBlank() ? "profissional" : professionalName.trim(),
        loginEmail == null ? "" : loginEmail.trim(),
        rawPassword == null ? "" : rawPassword);
    sendWithFallback(toEmail, subject, body, "RESET");
  }

  public DeliveryResult sendPasswordResetLink(String toEmail, String userName, String resetUrl) {
    String subject = "Redefinicao de senha - Azzo Agenda Pro";
    String body = """
        Ola %s,

        Recebemos uma solicitacao para redefinir sua senha.

        Acesse o link abaixo para cadastrar uma nova senha:
        %s

        Se voce nao solicitou esta alteracao, ignore este email.
        """.formatted(
        userName == null || userName.isBlank() ? "usuario" : userName.trim(),
        resetUrl == null ? "" : resetUrl.trim());
    return sendGenericWithFallback(toEmail, subject, body, "PASSWORD_RESET_LINK");
  }

  public DeliveryResult sendHtmlEmail(
      String toEmail,
      String subject,
      String htmlBody,
      String overrideFromEmail,
      String overrideFromName,
      String replyTo,
      String logPrefix) {
    return sendHtmlWithFallback(toEmail, subject, htmlBody, overrideFromEmail, overrideFromName, replyTo, logPrefix);
  }

  private void sendWithFallback(String toEmail, String subject, String body, String logPrefix) {
    if (!enabled) {
      if (consoleFallbackEnabled) {
        LOG.warn("CREDENCIAIS {} (fallback console) enviadas (nome e usuario omitidos por LGPD)", logPrefix);
      } else {
        LOG.info("Envio de email de credenciais desabilitado. email={}", maskEmail(toEmail));
      }
      return;
    }
    if (toEmail == null || toEmail.isBlank()) {
      throw new IllegalArgumentException("Email do profissional e obrigatorio para envio de credenciais");
    }

    try {
      sendViaSmtp(toEmail.trim(), subject, body, from);
    } catch (RuntimeException e) {
      if (consoleFallbackEnabled) {
        LOG.warn(
            "email_smtp_failed_fallback tipo={} motivo={} traceId={}",
            logPrefix, e.getMessage(), CorrelatedLogging.traceId());
        return;
      }
      throw e;
    }
  }

  private DeliveryResult sendGenericWithFallback(String toEmail, String subject, String body, String logPrefix) {
    return sendHtmlWithFallback(
        toEmail,
        subject,
        "<pre style=\"font-family:Arial,sans-serif;white-space:pre-wrap;\">" + escapeHtml(body) + "</pre>",
        null,
        null,
        null,
        logPrefix);
  }

  private DeliveryResult sendHtmlWithFallback(
      String toEmail,
      String subject,
      String htmlBody,
      String overrideFromEmail,
      String overrideFromName,
      String replyTo,
      String logPrefix) {
    String effectiveFrom = resolveEffectiveFrom();
    if (overrideFromEmail != null && !overrideFromEmail.isBlank()) {
      effectiveFrom = overrideFromEmail.trim();
    }
    if (!enabled) {
      if (consoleFallbackEnabled) {
        LOG.warn("{} (fallback console)", logPrefix);
        return new DeliveryResult(
            DELIVERY_STATUS_FALLBACK, "Envio de email desabilitado; fallback console utilizado.", effectiveFrom);
      } else {
        LOG.info("Envio de email desabilitado.");
        return new DeliveryResult(
            DELIVERY_STATUS_DISABLED, "Envio de email desabilitado pela configuracao.", effectiveFrom);
      }
    }
    if (toEmail == null || toEmail.isBlank()) {
      throw new IllegalArgumentException("Email obrigatorio para envio");
    }
    if (isFallbackFrom(effectiveFrom)) {
      String detail = "MAIL_FROM nao configurado ou usando fallback invalido: " + effectiveFrom;
      LOG.error("MAIL_FROM nao configurado ou usando fallback invalido.");
      return new DeliveryResult(DELIVERY_STATUS_CONFIG_ERROR, detail, effectiveFrom);
    }

    try {
      sendViaSmtpHtml(toEmail.trim(), subject, htmlBody, effectiveFrom, overrideFromName, replyTo);
      return new DeliveryResult(
          DELIVERY_STATUS_SMTP_ACCEPTED,
          "Email aceito pelo relay SMTP. Entrega final depende do provedor/destinatario.",
          effectiveFrom);
    } catch (RuntimeException e) {
      if (consoleFallbackEnabled) {
        LOG.warn(
            "email_smtp_failed_fallback tipo={} motivo={} traceId={}",
            logPrefix, e.getMessage(), CorrelatedLogging.traceId(), e);
        return new DeliveryResult(
            DELIVERY_STATUS_FALLBACK, "Falha SMTP com fallback console: " + detailedMessage(e), effectiveFrom);
      }
      throw e;
    }
  }

  private String resolveEffectiveFrom() {
    return from == null ? DEFAULT_FROM_FALLBACK : from.trim();
  }

  private boolean isFallbackFrom(String effectiveFrom) {
    return effectiveFrom == null
        || effectiveFrom.isBlank()
        || DEFAULT_FROM_FALLBACK.equalsIgnoreCase(effectiveFrom.trim());
  }

  private String detailedMessage(RuntimeException exception) {
    if (exception == null) {
      return "Erro de envio sem exception capturada.";
    }
    StringBuilder builder = new StringBuilder();
    Throwable current = exception;
    int depth = 0;
    while (current != null && depth < 10) {
      if (depth > 0) builder.append(" | caused by: ");
      builder.append(current.getClass().getName());
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        builder.append(": ").append(current.getMessage().trim());
      }
      current = current.getCause();
      depth++;
    }
    return builder.toString();
  }

  private void sendViaSmtp(String toEmail, String subject, String body, String effectiveFrom) {
    sendViaSmtpHtml(
        toEmail,
        subject,
        "<pre style=\"font-family:Arial,sans-serif;white-space:pre-wrap;\">" + escapeHtml(body) + "</pre>",
        effectiveFrom,
        null,
        null);
  }

  private void sendViaSmtpHtml(
      String toEmail, String subject, String htmlBody, String effectiveFrom, String fromName, String replyTo) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      if (fromName != null && !fromName.isBlank()) {
        helper.setFrom(effectiveFrom, fromName);
      } else {
        helper.setFrom(effectiveFrom);
      }
      if (replyTo != null && !replyTo.isBlank()) {
        helper.setReplyTo(replyTo.trim());
      }
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlBody == null ? "" : htmlBody, true);
      mailSender.send(message);
    } catch (MailException | jakarta.mail.MessagingException | java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException("Falha ao enviar email via SMTP: " + detailedMessage(wrapException(e)), e);
    }
  }

  private String maskEmail(String email) {
    if (email == null || !email.contains("@")) return "***";
    String[] parts = email.split("@");
    String local = parts[0].length() > 1 ? parts[0].charAt(0) + "***" : "***";
    String domain = parts[1].length() > 1 ? parts[1].charAt(0) + "***" : "***";
    return local + "@" + domain;
  }

  private String escapeHtml(String value) {
    if (value == null) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private RuntimeException wrapException(Exception exception) {
    if (exception instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new IllegalStateException(exception.getMessage(), exception);
  }

  public record DeliveryResult(String status, String detail, String from) {
    public boolean sent() {
      return DELIVERY_STATUS_SMTP_ACCEPTED.equals(status);
    }
  }
}
