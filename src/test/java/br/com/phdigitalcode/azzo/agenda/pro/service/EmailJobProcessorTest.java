package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobType;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CredentialsEmailService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.PasswordResetEmailPayload;

/**
 * Cobre {@code EmailJobProcessor} (espelha {@code modules/email/application/EmailJobProcessor.java}).
 *
 * <p>{@code emailJobStateService} e mockado inteiro (nao e um metodo {@code default}, entao nao
 * ha a armadilha de mock em interface default aqui) — o que importa e provar que o processor
 * despacha, atualiza o estado certo e registra auditoria certa para cada desfecho.
 */
@ExtendWith(MockitoExtension.class)
class EmailJobProcessorTest {

  @Mock private CredentialsEmailService credentialsEmailService;
  @Mock private AuditService auditService;
  @Mock private EmailJobStateService emailJobStateService;
  @Mock private EmailTemplateRendererService emailTemplateRendererService;

  private EmailJobProcessor processor;

  private final UUID jobId = UUID.randomUUID();
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    processor = new EmailJobProcessor(
        credentialsEmailService, auditService, emailJobStateService, emailTemplateRendererService,
        new ObjectMapper());
  }

  private EmailJobStateService.EmailJobSnapshot passwordResetSnapshot() throws Exception {
    String payloadJson =
        new ObjectMapper().writeValueAsString(new PasswordResetEmailPayload("https://x/reset", UUID.randomUUID()));
    return new EmailJobStateService.EmailJobSnapshot(
        jobId, tenantId, UUID.randomUUID(), "PASSWORD_RESET_TOKEN", UUID.randomUUID(),
        EmailJobType.PASSWORD_RESET, "user@x.com", "Fulano", payloadJson, null);
  }

  @Test
  void processJobNaoFazNadaQuandoSnapshotNulo() {
    when(emailJobStateService.loadPendingSnapshot(jobId)).thenReturn(null);

    processor.processJob(jobId);

    verify(credentialsEmailService, never()).sendHtmlEmail(any(), any(), any(), any(), any(), any(), any());
    verify(emailJobStateService, never()).markProcessed(any(), any(), any());
    verify(emailJobStateService, never()).markFailed(any(), any(), any(), any());
  }

  @Test
  void processJobMarcaProcessadoERegistraSucessoQuandoEnvioAceito() throws Exception {
    EmailJobStateService.EmailJobSnapshot snapshot = passwordResetSnapshot();
    when(emailJobStateService.loadPendingSnapshot(jobId)).thenReturn(snapshot);
    EmailTemplateRendererService.RenderedTemplate rendered = new EmailTemplateRendererService.RenderedTemplate(
        br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType.PASSWORD_RESET,
        "Redefinicao de senha", "Assunto", "<html>corpo</html>", "no-reply@azzo.com", "Azzo", null,
        java.util.List.of(), java.util.Map.of());
    when(emailTemplateRendererService.renderPasswordReset("Fulano", "https://x/reset")).thenReturn(rendered);
    CredentialsEmailService.DeliveryResult sentResult = new CredentialsEmailService.DeliveryResult(
        CredentialsEmailService.DELIVERY_STATUS_SMTP_ACCEPTED, "ok", "no-reply@azzo.com");
    when(credentialsEmailService.sendHtmlEmail(
            eq("user@x.com"), eq("Assunto"), eq("<html>corpo</html>"), eq("no-reply@azzo.com"), eq("Azzo"),
            eq(null), eq("PASSWORD_RESET_LINK")))
        .thenReturn(sentResult);
    when(emailJobStateService.markProcessed(jobId, CredentialsEmailService.DELIVERY_STATUS_SMTP_ACCEPTED, "no-reply@azzo.com"))
        .thenReturn(true);

    processor.processJob(jobId);

    verify(emailJobStateService).markProcessed(jobId, CredentialsEmailService.DELIVERY_STATUS_SMTP_ACCEPTED, "no-reply@azzo.com");
    verify(emailJobStateService, never()).markFailed(any(), any(), any(), any());
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    assertThat(captor.getValue().action).isEqualTo("AUTH_FORGOT_PASSWORD_EMAIL_DISPATCH");
    assertThat(captor.getValue().entityId).isEqualTo(jobId.toString());
    verify(auditService, never()).recordError(any());
  }

  @Test
  void processJobMarcaFalhaERegistraErroQuandoEnvioNaoAceito() throws Exception {
    EmailJobStateService.EmailJobSnapshot snapshot = passwordResetSnapshot();
    when(emailJobStateService.loadPendingSnapshot(jobId)).thenReturn(snapshot);
    EmailTemplateRendererService.RenderedTemplate rendered = new EmailTemplateRendererService.RenderedTemplate(
        br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType.PASSWORD_RESET,
        "Redefinicao de senha", "Assunto", "<html>corpo</html>", "no-reply@azzo.com", "Azzo", null,
        java.util.List.of(), java.util.Map.of());
    when(emailTemplateRendererService.renderPasswordReset(anyString(), anyString())).thenReturn(rendered);
    CredentialsEmailService.DeliveryResult failedResult = new CredentialsEmailService.DeliveryResult(
        CredentialsEmailService.DELIVERY_STATUS_CONFIG_ERROR, "MAIL_FROM invalido", "no-reply@azzo.com");
    when(credentialsEmailService.sendHtmlEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(failedResult);
    when(emailJobStateService.markFailed(eq(jobId), eq(CredentialsEmailService.DELIVERY_STATUS_CONFIG_ERROR),
            eq("MAIL_FROM invalido"), eq("no-reply@azzo.com")))
        .thenReturn(true);

    processor.processJob(jobId);

    verify(emailJobStateService).markFailed(jobId, CredentialsEmailService.DELIVERY_STATUS_CONFIG_ERROR,
        "MAIL_FROM invalido", "no-reply@azzo.com");
    verify(emailJobStateService, never()).markProcessed(any(), any(), any());
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordError(captor.capture());
    assertThat(captor.getValue().errorCode).isEqualTo(CredentialsEmailService.DELIVERY_STATUS_CONFIG_ERROR);
  }

  @Test
  void processJobMarcaFalhaQuandoExcecaoEstoura() throws Exception {
    EmailJobStateService.EmailJobSnapshot snapshot = passwordResetSnapshot();
    when(emailJobStateService.loadPendingSnapshot(jobId)).thenReturn(snapshot);
    when(emailTemplateRendererService.renderPasswordReset(anyString(), anyString()))
        .thenThrow(new IllegalStateException("template quebrado"));
    when(emailJobStateService.markFailed(eq(jobId), eq("ERROR"), anyString(), eq(null))).thenReturn(true);

    processor.processJob(jobId);

    verify(emailJobStateService).markFailed(eq(jobId), eq("ERROR"), anyString(), eq(null));
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordError(captor.capture());
    assertThat(captor.getValue().errorCode).isEqualTo("EMAIL_SEND_ERROR");
  }

  @Test
  void processJobMarcaFalhaQuandoTipoDeEmailNaoSuportado() {
    EmailJobStateService.EmailJobSnapshot unsupported = new EmailJobStateService.EmailJobSnapshot(
        jobId, tenantId, UUID.randomUUID(), null, null, EmailJobType.LICENSE_EXPIRING_SOON,
        "user@x.com", "Fulano", "{}", null);
    when(emailJobStateService.loadPendingSnapshot(jobId)).thenReturn(unsupported);
    when(emailJobStateService.markFailed(eq(jobId), eq("ERROR"), anyString(), eq(null))).thenReturn(true);

    processor.processJob(jobId);

    verify(credentialsEmailService, never()).sendHtmlEmail(any(), any(), any(), any(), any(), any(), any());
    verify(emailJobStateService).markFailed(eq(jobId), eq("ERROR"), anyString(), eq(null));
  }

  @Test
  void processJobLogaMasNaoQuebraQuandoUpdateDeEstadoNaoAfetaLinha() throws Exception {
    EmailJobStateService.EmailJobSnapshot snapshot = passwordResetSnapshot();
    when(emailJobStateService.loadPendingSnapshot(jobId)).thenReturn(snapshot);
    EmailTemplateRendererService.RenderedTemplate rendered = new EmailTemplateRendererService.RenderedTemplate(
        br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType.PASSWORD_RESET,
        "Redefinicao de senha", "Assunto", "<html>corpo</html>", "no-reply@azzo.com", "Azzo", null,
        java.util.List.of(), java.util.Map.of());
    when(emailTemplateRendererService.renderPasswordReset(anyString(), anyString())).thenReturn(rendered);
    CredentialsEmailService.DeliveryResult sentResult = new CredentialsEmailService.DeliveryResult(
        CredentialsEmailService.DELIVERY_STATUS_SMTP_ACCEPTED, "ok", "no-reply@azzo.com");
    when(credentialsEmailService.sendHtmlEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn(sentResult);
    when(emailJobStateService.markProcessed(any(), any(), any())).thenReturn(false);

    processor.processJob(jobId);

    verify(auditService).recordSuccess(any());
  }
}
