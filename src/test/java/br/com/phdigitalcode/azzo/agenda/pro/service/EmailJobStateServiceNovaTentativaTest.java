package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailJobRepository;

/** Erro passageiro de SMTP nao pode deixar o usuario sem e-mail: o job espera e tenta de novo. */
class EmailJobStateServiceNovaTentativaTest {

  private EmailJobRepository repo;
  private EmailJobStateService service;
  private io.micrometer.core.instrument.simple.SimpleMeterRegistry registry;
  private final UUID jobId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    repo = mock(EmailJobRepository.class);
    service = new EmailJobStateService(repo, registry);
  }

  private EmailJob job(int tentativas, EmailJobStatus status) {
    EmailJob job = new EmailJob();
    job.setId(jobId);
    job.setStatus(status);
    job.setAttempts(tentativas);
    when(repo.findById(jobId)).thenReturn(Optional.of(job));
    return job;
  }

  @Test
  void primeiraFalhaReagendaEmDoisMinutosSemMarcarFailed() {
    job(0, EmailJobStatus.NEW);
    when(repo.reagendar(eq(jobId), anyInt(), any(), anyString(), anyString(), any())).thenReturn(true);
    Instant antes = Instant.now();

    assertThat(service.markFailed(jobId, "ERROR", "smtp fora", null)).isTrue();

    ArgumentCaptor<Instant> proxima = ArgumentCaptor.forClass(Instant.class);
    verify(repo).reagendar(eq(jobId), eq(1), proxima.capture(), eq("ERROR"), eq("smtp fora"), any());
    assertThat(Duration.between(antes, proxima.getValue()))
        .isBetween(Duration.ofMinutes(2).minusSeconds(5), Duration.ofMinutes(2).plusSeconds(5));
    verify(repo, never()).markFailed(any(), any(), any(), any(), any());
  }

  @Test
  void aEsperaCresceEntreAsTentativas() {
    assertThat(EmailJobStateService.esperaApos(1)).isEqualTo(Duration.ofMinutes(2));
    assertThat(EmailJobStateService.esperaApos(2)).isEqualTo(Duration.ofMinutes(10));
    assertThat(EmailJobStateService.esperaApos(3)).isEqualTo(Duration.ofMinutes(30));
    assertThat(EmailJobStateService.esperaApos(4)).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void naUltimaTentativaOJobViraFailedDeVez() {
    job(EmailJobStateService.MAX_TENTATIVAS - 1, EmailJobStatus.NEW);
    when(repo.markFailed(eq(jobId), anyString(), anyString(), any(), any())).thenReturn(true);

    assertThat(service.markFailed(jobId, "ERROR", "smtp fora", null)).isTrue();

    verify(repo).markFailed(eq(jobId), eq("ERROR"), eq("smtp fora"), any(), any());
    verify(repo, never()).reagendar(any(), anyInt(), any(), any(), any(), any());
  }

  @Test
  void jobQueJaNaoEhNewNaoEhReagendado() {
    job(0, EmailJobStatus.PROCESSED);

    service.markFailed(jobId, "ERROR", "x", null);

    verify(repo, never()).reagendar(any(), anyInt(), any(), any(), any(), any());
  }

  @Test
  void jobInexistenteCaiNoCaminhoAntigoDeFailed() {
    when(repo.findById(jobId)).thenReturn(Optional.empty());

    service.markFailed(jobId, "ERROR", "x", null);

    verify(repo).markFailed(eq(jobId), eq("ERROR"), eq("x"), any(), any());
  }

  @Test
  void falhaDefinitivaContaNoMedidorEReagendamentoNao() {
    // O medidor nasce em 0 (o Prometheus precisa ver a serie ANTES da primeira falha).
    assertThat(registry.get("email.jobs.failed").counter().count()).isZero();

    EmailJob job = new EmailJob();
    job.setStatus(EmailJobStatus.NEW);
    job.setAttempts(0);
    when(repo.findById(jobId)).thenReturn(Optional.of(job));
    when(repo.reagendar(eq(jobId), anyInt(), any(), anyString(), anyString(), any())).thenReturn(true);
    service.markFailed(jobId, "ERROR", "smtp fora", null);
    assertThat(registry.get("email.jobs.failed").counter().count()).isZero();

    job.setAttempts(EmailJobStateService.MAX_TENTATIVAS - 1);
    when(repo.markFailed(eq(jobId), anyString(), anyString(), any(), any())).thenReturn(true);
    service.markFailed(jobId, "ERROR", "smtp fora", null);
    assertThat(registry.get("email.jobs.failed").counter().count()).isEqualTo(1.0);
  }

  @Test
  void falhaDefinitivaQueNaoAtualizouNadaNaoConta() {
    EmailJob job = new EmailJob();
    job.setStatus(EmailJobStatus.FAILED);
    when(repo.findById(jobId)).thenReturn(Optional.of(job));
    when(repo.markFailed(eq(jobId), anyString(), anyString(), any(), any())).thenReturn(false);

    service.markFailed(jobId, "ERROR", "x", null);

    assertThat(registry.get("email.jobs.failed").counter().count()).isZero();
  }
}
