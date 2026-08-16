package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.PasswordResetToken;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobType;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.EmailJobExecutor;
import br.com.phdigitalcode.azzo.agenda.pro.service.EmailJobProcessor;

/** Cobre {@code integration/EmailJobService.java} (espelha {@code modules/email/application/EmailJobService.java}). */
@ExtendWith(MockitoExtension.class)
class EmailJobServiceTest {

  @Mock private EmailJobRepository emailJobRepository;
  @Mock private EmailJobProcessor emailJobProcessor;
  @Mock private EmailJobExecutor emailJobExecutor;

  private EmailJobService service;

  @BeforeEach
  void setUp() {
    service = new EmailJobService(emailJobRepository, emailJobProcessor, emailJobExecutor, new ObjectMapper(), 20);
  }

  @Test
  void enqueuePasswordResetPersisteJobNewComPayloadCorreto() {
    Usuario usuario = new Usuario();
    usuario.setId(UUID.randomUUID());
    usuario.setTenantId(UUID.randomUUID());
    usuario.setEmail("user@x.com");
    usuario.setName("Fulano");

    PasswordResetToken token = new PasswordResetToken();
    token.setId(UUID.randomUUID());

    service.enqueuePasswordReset(usuario, token, "https://app/reset?token=abc");

    ArgumentCaptor<EmailJob> captor = ArgumentCaptor.forClass(EmailJob.class);
    verify(emailJobRepository).save(captor.capture());
    EmailJob saved = captor.getValue();
    assertThat(saved.getTenantId()).isEqualTo(usuario.getTenantId());
    assertThat(saved.getUserId()).isEqualTo(usuario.getId());
    assertThat(saved.getRelatedEntityType()).isEqualTo("PASSWORD_RESET_TOKEN");
    assertThat(saved.getRelatedEntityId()).isEqualTo(token.getId());
    assertThat(saved.getEmailType()).isEqualTo(EmailJobType.PASSWORD_RESET);
    assertThat(saved.getRecipientEmail()).isEqualTo("user@x.com");
    assertThat(saved.getRecipientName()).isEqualTo("Fulano");
    assertThat(saved.getStatus()).isEqualTo(EmailJobStatus.NEW);
    assertThat(saved.getPayloadJson())
        .contains("https://app/reset?token=abc")
        .contains(token.getId().toString());
  }

  @Test
  void processPendingJobsAsyncDevolveZeroQuandoNaoHaJobsNovos() {
    when(emailJobRepository.findNextNewBatch(20)).thenReturn(List.of());

    int processed = service.processPendingJobsAsync(() -> {});

    assertThat(processed).isZero();
    verify(emailJobExecutor, never()).runBatchAsync(any(), any(), any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void processPendingJobsAsyncDespachaBatchParaOExecutor() {
    EmailJob job1 = jobWithId();
    EmailJob job2 = jobWithId();
    when(emailJobRepository.findNextNewBatch(20)).thenReturn(List.of(job1, job2));
    Runnable onComplete = () -> {};

    int processed = service.processPendingJobsAsync(onComplete);

    assertThat(processed).isEqualTo(2);
    ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Consumer<UUID>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(emailJobExecutor).runBatchAsync(idsCaptor.capture(), consumerCaptor.capture(), eq(onComplete));
    assertThat(idsCaptor.getValue()).containsExactly(job1.getId(), job2.getId());

    // O consumer passado deve delegar exatamente para emailJobProcessor::processJob.
    UUID jobId = job1.getId();
    consumerCaptor.getValue().accept(jobId);
    verify(emailJobProcessor).processJob(jobId);
  }

  private EmailJob jobWithId() {
    EmailJob job = new EmailJob();
    job.setId(UUID.randomUUID());
    return job;
  }
}
