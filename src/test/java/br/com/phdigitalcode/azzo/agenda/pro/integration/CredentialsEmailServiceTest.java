package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Cobre {@code CredentialsEmailService}, agora com envio SMTP real via {@link JavaMailSender}
 * (espelha {@code infrastructure/notification/CredentialsEmailService.java}).
 *
 * <p>{@code mailSender.createMimeMessage()} devolve um {@link MimeMessage} real (nao mockado) —
 * {@link org.springframework.mail.javamail.MimeMessageHelper} precisa de uma instancia funcional
 * para montar destinatario/assunto/corpo, que depois e lido de volta para verificar o conteudo
 * exato que seria enviado.
 */
@ExtendWith(MockitoExtension.class)
class CredentialsEmailServiceTest {

  @Mock private JavaMailSender mailSender;

  @BeforeEach
  void setUp() {
    // lenient: nem todo teste chega a chamar createMimeMessage (ex.: enabled=false).
    org.mockito.Mockito.lenient()
        .when(mailSender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
  }

  private CredentialsEmailService service(boolean enabled, String from, boolean consoleFallback) {
    return new CredentialsEmailService(mailSender, enabled, from, consoleFallback);
  }

  @Test
  void sendHtmlEmailEnviaViaSmtpQuandoHabilitado() throws Exception {
    CredentialsEmailService service = service(true, "no-reply@azzoholding.com.br", true);

    CredentialsEmailService.DeliveryResult result = service.sendHtmlEmail(
        "cliente@x.com", "Assunto teste", "<b>corpo</b>", null, "Azzo", "reply@azzo.com", "TEST");

    assertThat(result.sent()).isTrue();
    assertThat(result.status()).isEqualTo(CredentialsEmailService.DELIVERY_STATUS_SMTP_ACCEPTED);
    assertThat(result.from()).isEqualTo("no-reply@azzoholding.com.br");

    org.mockito.ArgumentCaptor<MimeMessage> captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    MimeMessage sent = captor.getValue();
    assertThat(sent.getSubject()).isEqualTo("Assunto teste");
    assertThat(sent.getAllRecipients()[0].toString()).contains("cliente@x.com");
    assertThat(sent.getFrom()[0].toString()).contains("no-reply@azzoholding.com.br").contains("Azzo");
    assertThat(sent.getReplyTo()[0].toString()).contains("reply@azzo.com");
    assertThat((String) sent.getContent()).contains("<b>corpo</b>");
  }

  @Test
  void sendHtmlEmailNaoEnviaQuandoDesabilitadoEUsaFallbackConsole() {
    CredentialsEmailService service = service(false, "no-reply@azzoholding.com.br", true);

    CredentialsEmailService.DeliveryResult result =
        service.sendHtmlEmail("cliente@x.com", "Assunto", "<p>x</p>", null, null, null, "TEST");

    assertThat(result.sent()).isFalse();
    assertThat(result.status()).isEqualTo(CredentialsEmailService.DELIVERY_STATUS_FALLBACK);
    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void sendHtmlEmailDevolveDisabledQuandoDesabilitadoSemFallback() {
    CredentialsEmailService service = service(false, "no-reply@azzoholding.com.br", false);

    CredentialsEmailService.DeliveryResult result =
        service.sendHtmlEmail("cliente@x.com", "Assunto", "<p>x</p>", null, null, null, "TEST");

    assertThat(result.status()).isEqualTo(CredentialsEmailService.DELIVERY_STATUS_DISABLED);
    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void sendHtmlEmailDevolveConfigErrorQuandoFromNaoConfigurado() {
    CredentialsEmailService service = service(true, "no-reply@seudominio.com", true);

    CredentialsEmailService.DeliveryResult result =
        service.sendHtmlEmail("cliente@x.com", "Assunto", "<p>x</p>", null, null, null, "TEST");

    assertThat(result.status()).isEqualTo(CredentialsEmailService.DELIVERY_STATUS_CONFIG_ERROR);
    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void sendHtmlEmailComFalhaSmtpCaiParaFallbackConsoleQuandoHabilitado() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    org.mockito.Mockito.doThrow(new MailSendException("conexao recusada"))
        .when(mailSender)
        .send(any(MimeMessage.class));
    CredentialsEmailService service = service(true, "no-reply@azzoholding.com.br", true);

    CredentialsEmailService.DeliveryResult result =
        service.sendHtmlEmail("cliente@x.com", "Assunto", "<p>x</p>", null, null, null, "TEST");

    assertThat(result.status()).isEqualTo(CredentialsEmailService.DELIVERY_STATUS_FALLBACK);
    assertThat(result.detail()).contains("Falha SMTP");
  }

  @Test
  void sendHtmlEmailComFalhaSmtpPropagaExcecaoQuandoSemFallback() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    org.mockito.Mockito.doThrow(new MailSendException("conexao recusada"))
        .when(mailSender)
        .send(any(MimeMessage.class));
    CredentialsEmailService service = service(true, "no-reply@azzoholding.com.br", false);

    assertThatThrownBy(() -> service.sendHtmlEmail("cliente@x.com", "Assunto", "<p>x</p>", null, null, null, "TEST"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Falha ao enviar email via SMTP");
  }

  @Test
  void sendTemporaryPasswordResetEnviaSenhaNoCorpoQuandoHabilitado() throws Exception {
    CredentialsEmailService service = service(true, "no-reply@azzoholding.com.br", true);

    service.sendTemporaryPasswordReset("prof@x.com", "Fulano", "prof@x.com", "SenhaTemp123!");

    org.mockito.ArgumentCaptor<MimeMessage> captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    assertThat((String) captor.getValue().getContent()).contains("SenhaTemp123!").contains("Fulano");
  }

  @Test
  void sendProfessionalAccessNaoEnviaQuandoDesabilitado() {
    CredentialsEmailService service = service(false, "no-reply@azzoholding.com.br", true);

    service.sendProfessionalAccess("prof@x.com", "Fulano", "prof@x.com", "Senha123!");

    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void isEnabledReflecteConfiguracao() {
    assertThat(service(true, "x@y.com", true).isEnabled()).isTrue();
    assertThat(service(false, "x@y.com", true).isEnabled()).isFalse();
  }
}
