package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.PasswordResetToken;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobType;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.EmailJobExecutor;
import br.com.phdigitalcode.azzo.agenda.pro.service.EmailJobProcessor;

/**
 * Espelha {@code modules/email/application/EmailJobService.java}. {@link #enqueuePasswordReset}
 * apenas persiste o {@link EmailJob} ({@code NEW}) dentro da transacao do chamador (fluxo
 * "esqueci minha senha" de {@code AuthServiceImpl}) — o envio real acontece depois, de forma
 * assincrona, quando {@code EmailJobScheduler} chama {@link #processPendingJobsAsync}.
 */
@Service
public class EmailJobService {

  private static final String RELATED_ENTITY_TYPE_PASSWORD_RESET_TOKEN = "PASSWORD_RESET_TOKEN";

  private final EmailJobRepository emailJobRepository;
  private final EmailJobProcessor emailJobProcessor;
  private final EmailJobExecutor emailJobExecutor;
  private final ObjectMapper objectMapper;
  private final int batchSize;

  public EmailJobService(
      EmailJobRepository emailJobRepository,
      EmailJobProcessor emailJobProcessor,
      EmailJobExecutor emailJobExecutor,
      ObjectMapper objectMapper,
      @Value("${app.email.jobs.batch-size:20}") int batchSize) {
    this.emailJobRepository = emailJobRepository;
    this.emailJobProcessor = emailJobProcessor;
    this.emailJobExecutor = emailJobExecutor;
    this.objectMapper = objectMapper;
    this.batchSize = batchSize;
  }

  @Transactional
  public void enqueuePasswordReset(Usuario usuario, PasswordResetToken token, String resetUrl) {
    EmailJob job = new EmailJob();
    job.setTenantId(usuario.getTenantId());
    job.setUserId(usuario.getId());
    job.setRelatedEntityType(RELATED_ENTITY_TYPE_PASSWORD_RESET_TOKEN);
    job.setRelatedEntityId(token.getId());
    job.setEmailType(EmailJobType.PASSWORD_RESET);
    job.setRecipientEmail(usuario.getEmail());
    job.setRecipientName(usuario.getName());
    job.setPayloadJson(toJson(new PasswordResetEmailPayload(resetUrl, token.getId())));
    job.setStatus(EmailJobStatus.NEW);
    emailJobRepository.save(job);
  }

  @Transactional
  public int processPendingJobsAsync(Runnable onComplete) {
    List<EmailJob> jobs = emailJobRepository.findNextNewBatch(Math.max(batchSize, 1));
    if (jobs.isEmpty()) return 0;

    List<UUID> jobIds = jobs.stream().map(EmailJob::getId).toList();
    emailJobExecutor.runBatchAsync(jobIds, emailJobProcessor::processJob, onComplete);
    return jobIds.size();
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao serializar payload do job de email.", e);
    }
  }
}
