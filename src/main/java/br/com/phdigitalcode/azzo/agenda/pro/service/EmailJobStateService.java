package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobType;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailJobRepository;

/**
 * Espelha {@code modules/email/application/EmailJobStateService.java}.
 *
 * <p>Cada metodo roda na sua propria transacao ({@code REQUIRES_NEW}), exatamente como o original
 * ({@code Transactional.TxType.REQUIRES_NEW}): {@link EmailJobProcessor} e um bean diferente
 * chamando estes metodos (nao ha auto-invocacao), entao a anotacao {@code @Transactional} basta —
 * nao precisa de {@code TransactionTemplate} explicito. Isso garante que {@code markProcessed}/
 * {@code markFailed} persistem mesmo que o restante do processamento do job (ex.: auditoria) falhe
 * depois, e que o snapshot de leitura nao fique preso na mesma transacao do processamento.
 */
@Service
public class EmailJobStateService {

  private final EmailJobRepository emailJobRepository;

  public EmailJobStateService(EmailJobRepository emailJobRepository) {
    this.emailJobRepository = emailJobRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public EmailJobSnapshot loadPendingSnapshot(UUID jobId) {
    EmailJob job = emailJobRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != EmailJobStatus.NEW) return null;
    return new EmailJobSnapshot(
        job.getId(),
        job.getTenantId(),
        job.getUserId(),
        job.getRelatedEntityType(),
        job.getRelatedEntityId(),
        job.getEmailType(),
        job.getRecipientEmail(),
        job.getRecipientName(),
        job.getPayloadJson(),
        job.getFromEmail());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markProcessed(UUID jobId, String providerStatus, String fromEmail) {
    return emailJobRepository.markProcessed(jobId, providerStatus, fromEmail, Instant.now());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markFailed(UUID jobId, String providerStatus, String errorMessage, String fromEmail) {
    return emailJobRepository.markFailed(jobId, providerStatus, errorMessage, fromEmail, Instant.now());
  }

  public record EmailJobSnapshot(
      UUID id,
      UUID tenantId,
      UUID userId,
      String relatedEntityType,
      UUID relatedEntityId,
      EmailJobType emailType,
      String recipientEmail,
      String recipientName,
      String payloadJson,
      String fromEmail) {}
}
