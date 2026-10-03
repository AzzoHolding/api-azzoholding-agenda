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

  /** Tentativas de envio antes de o job virar FAILED de vez. */
  public static final int MAX_TENTATIVAS = 5;

  /**
   * Espera ate a proxima tentativa, depois da falha de numero {@code tentativa} (1, 2, ...):
   * 2 min, 10 min, 30 min, 2 h. Nao ha espera depois da ultima.
   */
  static java.time.Duration esperaApos(int tentativa) {
    return switch (tentativa) {
      case 1 -> java.time.Duration.ofMinutes(2);
      case 2 -> java.time.Duration.ofMinutes(10);
      case 3 -> java.time.Duration.ofMinutes(30);
      default -> java.time.Duration.ofHours(2);
    };
  }

  /**
   * Registra uma falha de envio. Com tentativas sobrando, o job continua NEW e e reagendado com
   * espera crescente (erro passageiro de SMTP nao deixa o usuario sem e-mail); na ultima tentativa
   * vira FAILED. Devolve se o job foi atualizado.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markFailed(UUID jobId, String providerStatus, String errorMessage, String fromEmail) {
    Instant agora = Instant.now();
    EmailJob job = emailJobRepository.findById(jobId).orElse(null);
    int tentativa = (job == null ? 0 : job.getAttempts()) + 1;
    if (job != null && job.getStatus() == EmailJobStatus.NEW && tentativa < MAX_TENTATIVAS) {
      return emailJobRepository.reagendar(
          jobId, tentativa, agora.plus(esperaApos(tentativa)), providerStatus, errorMessage, fromEmail);
    }
    return emailJobRepository.markFailed(jobId, providerStatus, errorMessage, fromEmail, agora);
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
