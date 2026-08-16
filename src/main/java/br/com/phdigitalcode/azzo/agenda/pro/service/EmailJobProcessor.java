package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobType;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CredentialsEmailService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.PasswordResetEmailPayload;

/**
 * Espelha {@code modules/email/application/EmailJobProcessor.java}. Sem {@code @Transactional} de
 * classe/metodo: o original tem {@code @Transactional} em {@code processJob}, mas aqui as unicas
 * escritas de estado ({@code markProcessed}/{@code markFailed}) ja rodam em transacoes proprias
 * dentro de {@link EmailJobStateService} ({@code REQUIRES_NEW}) — nao ha nada mais para envolver
 * numa transacao neste metodo (o envio SMTP em si nao e transacional).
 */
@Service
public class EmailJobProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(EmailJobProcessor.class);

  private final CredentialsEmailService credentialsEmailService;
  private final AuditService auditService;
  private final EmailJobStateService emailJobStateService;
  private final EmailTemplateRendererService emailTemplateRendererService;
  private final ObjectMapper objectMapper;

  public EmailJobProcessor(
      CredentialsEmailService credentialsEmailService,
      AuditService auditService,
      EmailJobStateService emailJobStateService,
      EmailTemplateRendererService emailTemplateRendererService,
      ObjectMapper objectMapper) {
    this.credentialsEmailService = credentialsEmailService;
    this.auditService = auditService;
    this.emailJobStateService = emailJobStateService;
    this.emailTemplateRendererService = emailTemplateRendererService;
    this.objectMapper = objectMapper;
  }

  public void processJob(UUID jobId) {
    EmailJobStateService.EmailJobSnapshot job = emailJobStateService.loadPendingSnapshot(jobId);
    if (job == null) return;

    try {
      CredentialsEmailService.DeliveryResult result = dispatch(job);

      if (result != null && result.sent()) {
        boolean updated = emailJobStateService.markProcessed(job.id(), result.status(), result.from());
        if (!updated) {
          LOG.warn("Job de email nao foi marcado como PROCESSED. jobId={}", job.id());
        }
        registrarAuditoriaDispatch(job, result, null);
        return;
      }

      String failureDetail = result != null
          ? result.detail()
          : "Falha no envio do email sem retorno do provider.";
      boolean updated = emailJobStateService.markFailed(
          job.id(),
          result != null ? result.status() : "ERROR",
          failureDetail,
          result != null ? result.from() : null);
      if (!updated) {
        LOG.warn("Job de email nao foi marcado como FAILED. jobId={}", job.id());
      }
      registrarAuditoriaDispatch(job, result, null);
    } catch (Exception e) {
      boolean updated = emailJobStateService.markFailed(job.id(), "ERROR", detailedErrorMessage(e), null);
      if (!updated) {
        LOG.warn("Job de email nao foi marcado como FAILED apos exception. jobId={}", job.id());
      }
      registrarAuditoriaDispatch(job, null, e);
      LOG.error("Falha ao processar job de email jobId={}", job.id(), e);
    }
  }

  private CredentialsEmailService.DeliveryResult dispatch(EmailJobStateService.EmailJobSnapshot job) {
    if (job.emailType() == EmailJobType.PASSWORD_RESET) {
      PasswordResetEmailPayload payload = readPayload(job.payloadJson(), PasswordResetEmailPayload.class);
      EmailTemplateRendererService.RenderedTemplate rendered =
          emailTemplateRendererService.renderPasswordReset(job.recipientName(), payload.resetUrl());
      return credentialsEmailService.sendHtmlEmail(
          job.recipientEmail(),
          rendered.subject(),
          rendered.html(),
          rendered.fromEmail(),
          rendered.fromName(),
          rendered.replyTo(),
          "PASSWORD_RESET_LINK");
    }

    throw new IllegalStateException("Tipo de email nao suportado: " + job.emailType());
  }

  private <T> T readPayload(String payloadJson, Class<T> type) {
    try {
      return objectMapper.readValue(payloadJson, type);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Falha ao desserializar payload de email. type=" + type.getSimpleName(), e);
    }
  }

  private void registrarAuditoriaDispatch(
      EmailJobStateService.EmailJobSnapshot job,
      CredentialsEmailService.DeliveryResult result,
      Exception exception) {
    if (job == null || job.tenantId() == null) return;
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = job.tenantId();
      command.actorUserId = null;
      command.module = AuditConstants.Module.SYSTEM;
      command.action = "AUTH_FORGOT_PASSWORD_EMAIL_DISPATCH";
      command.entityType = "EMAIL_JOB";
      command.entityId = job.id() != null ? job.id().toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.SYSTEM;

      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("jobId", job.id() != null ? job.id().toString() : null);
      metadata.put("userId", job.userId() != null ? job.userId().toString() : null);
      metadata.put("emailType", job.emailType() != null ? job.emailType().name() : null);
      metadata.put("deliveryStatus", result != null ? result.status() : "ERROR");
      metadata.put("deliveryDetail", result != null ? result.detail() : detailedErrorMessage(exception));
      metadata.put("providerStatus", result != null ? result.status() : "ERROR");
      metadata.put("from", result != null ? result.from() : job.fromEmail());
      if (exception != null) {
        metadata.put("exceptionType", exception.getClass().getName());
        metadata.put("exceptionStackTrace", stackTraceOf(exception));
      }
      command.metadata = metadata;

      if (result != null && result.sent()) {
        auditService.recordSuccess(command);
      } else {
        command.errorCode = result != null ? result.status() : "EMAIL_SEND_ERROR";
        command.errorMessage = result != null ? result.detail() : detailedErrorMessage(exception);
        auditService.recordError(command);
      }
    } catch (Exception ignored) {
      // Auditoria nao deve quebrar o processamento da fila.
    }
  }

  private String detailedErrorMessage(Exception exception) {
    if (exception == null) {
      return "Falha no processamento do job de email sem exception capturada.";
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
    String stackTrace = stackTraceOf(exception);
    if (stackTrace != null && !stackTrace.isBlank()) {
      builder.append(System.lineSeparator()).append(stackTrace);
    }
    return builder.toString();
  }

  private String stackTraceOf(Exception exception) {
    if (exception == null) return null;
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    exception.printStackTrace(printWriter);
    printWriter.flush();
    return stringWriter.toString();
  }
}
