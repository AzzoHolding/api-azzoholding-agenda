package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobType;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailJobRepository;

/** Cobre {@code EmailJobStateService} (espelha {@code modules/email/application/EmailJobStateService.java}). */
@ExtendWith(MockitoExtension.class)
class EmailJobStateServiceTest {

  @Mock private EmailJobRepository emailJobRepository;

  private EmailJobStateService service;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    service = new EmailJobStateService(emailJobRepository);
  }

  @Test
  void loadPendingSnapshotDevolveNullQuandoJobNaoExiste() {
    UUID jobId = UUID.randomUUID();
    when(emailJobRepository.findById(jobId)).thenReturn(Optional.empty());

    assertThat(service.loadPendingSnapshot(jobId)).isNull();
  }

  @Test
  void loadPendingSnapshotDevolveNullQuandoStatusNaoENew() {
    UUID jobId = UUID.randomUUID();
    EmailJob job = new EmailJob();
    job.setId(jobId);
    job.setStatus(EmailJobStatus.PROCESSED);
    when(emailJobRepository.findById(jobId)).thenReturn(Optional.of(job));

    assertThat(service.loadPendingSnapshot(jobId)).isNull();
  }

  @Test
  void loadPendingSnapshotMapeiaCamposQuandoStatusNew() {
    UUID jobId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    EmailJob job = new EmailJob();
    job.setId(jobId);
    job.setTenantId(tenantId);
    job.setStatus(EmailJobStatus.NEW);
    job.setEmailType(EmailJobType.PASSWORD_RESET);
    job.setRecipientEmail("user@x.com");
    job.setPayloadJson("{}");
    when(emailJobRepository.findById(jobId)).thenReturn(Optional.of(job));

    EmailJobStateService.EmailJobSnapshot snapshot = service.loadPendingSnapshot(jobId);

    assertThat(snapshot).isNotNull();
    assertThat(snapshot.id()).isEqualTo(jobId);
    assertThat(snapshot.tenantId()).isEqualTo(tenantId);
    assertThat(snapshot.emailType()).isEqualTo(EmailJobType.PASSWORD_RESET);
    assertThat(snapshot.recipientEmail()).isEqualTo("user@x.com");
  }

  @Test
  void markProcessedDelegaParaRepositorioComTimestampAtual() {
    UUID jobId = UUID.randomUUID();
    when(emailJobRepository.markProcessed(eq(jobId), eq("SMTP_ACCEPTED"), eq("from@x.com"), any())).thenReturn(true);

    boolean updated = service.markProcessed(jobId, "SMTP_ACCEPTED", "from@x.com");

    assertThat(updated).isTrue();
    verify(emailJobRepository).markProcessed(eq(jobId), eq("SMTP_ACCEPTED"), eq("from@x.com"), any());
  }

  @Test
  void markFailedDelegaParaRepositorioComTimestampAtual() {
    UUID jobId = UUID.randomUUID();
    when(emailJobRepository.markFailed(eq(jobId), eq("ERROR"), eq("falha"), eq(null), any())).thenReturn(true);

    boolean updated = service.markFailed(jobId, "ERROR", "falha", null);

    assertThat(updated).isTrue();
    verify(emailJobRepository).markFailed(eq(jobId), eq("ERROR"), eq("falha"), eq(null), any());
  }
}
