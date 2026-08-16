package br.com.phdigitalcode.azzo.agenda.pro.config;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Constroi o {@link JavaMailSender} usado por {@code CredentialsEmailService}, espelhando
 * {@code buildSmtpProperties()}/{@code buildAuthenticator()} de
 * {@code infrastructure/notification/CredentialsEmailService.java} (Jakarta Mail puro no
 * original) — mesmas chaves de configuracao ({@code app.mail.*}), mesma logica de SSL-na-porta-465
 * vs. STARTTLS, mesmos timeouts e mesmo mecanismo de autenticacao configuravel.
 */
@Configuration
public class MailConfig {

  @Bean
  public JavaMailSender javaMailSender(
      @Value("${app.mail.host:__unset__}") String host,
      @Value("${app.mail.port:587}") int port,
      @Value("${app.mail.credentials.username:}") String username,
      @Value("${app.mail.credentials.password:}") String password,
      @Value("${app.mail.starttls:REQUIRED}") String startTlsMode,
      @Value("${app.mail.ssl:false}") boolean sslOnConnect,
      @Value("${app.mail.smtp.auth-mechanisms:LOGIN PLAIN}") String smtpAuthMechanisms,
      @Value("${app.mail.smtp.timeout-seconds:20}") int smtpTimeoutSeconds) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(host);
    sender.setPort(port);
    sender.setDefaultEncoding("UTF-8");

    boolean authEnabled = username != null && !username.isBlank();
    if (authEnabled) {
      sender.setUsername(username);
      sender.setPassword(password);
    }

    boolean sslEnabled = sslOnConnect || port == 465;
    boolean startTlsEnabled = !sslEnabled && isStartTlsEnabled(startTlsMode);
    int timeoutMillis = Math.max(smtpTimeoutSeconds, 1) * 1000;

    Properties properties = sender.getJavaMailProperties();
    properties.put("mail.transport.protocol", "smtp");
    properties.put("mail.smtp.auth", Boolean.toString(authEnabled));
    properties.put("mail.smtp.ssl.enable", Boolean.toString(sslEnabled));
    properties.put("mail.smtp.starttls.enable", Boolean.toString(startTlsEnabled));
    properties.put("mail.smtp.starttls.required", Boolean.toString(startTlsEnabled));
    properties.put("mail.smtp.connectiontimeout", Integer.toString(timeoutMillis));
    properties.put("mail.smtp.timeout", Integer.toString(timeoutMillis));
    properties.put("mail.smtp.writetimeout", Integer.toString(timeoutMillis));
    properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
    properties.put("mail.smtp.quitwait", "false");
    if (authEnabled) {
      properties.put(
          "mail.smtp.auth.mechanisms",
          smtpAuthMechanisms == null || smtpAuthMechanisms.isBlank()
              ? "LOGIN PLAIN"
              : smtpAuthMechanisms.trim());
    }
    return sender;
  }

  private boolean isStartTlsEnabled(String startTlsMode) {
    return startTlsMode != null && !"DISABLED".equalsIgnoreCase(startTlsMode.trim());
  }
}
