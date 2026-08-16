package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Cobre o metodo {@code default} de {@link AuditRetentionEventRepository}
 * ({@code existsPurgeBoundaryBefore}) — metodos default nao sao interceptados pelo Mockito por
 * padrao, entao o mock precisa de {@code Answers.CALLS_REAL_METHODS}.
 */
class AuditRetentionEventRepositoryTest {

  @Test
  void existsPurgeBoundaryBeforeDelegaParaQueryQuandoParametrosValidos() {
    AuditRetentionEventRepository repository = mock(AuditRetentionEventRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    Instant createdAt = Instant.now();
    when(repository.existsPurgeBoundaryBeforeRaw(tenantId, createdAt)).thenReturn(true);

    assertThat(repository.existsPurgeBoundaryBefore(tenantId, createdAt)).isTrue();
  }

  @Test
  void existsPurgeBoundaryBeforeRetornaFalseSemTocarNoBancoQuandoTenantIdNulo() {
    AuditRetentionEventRepository repository = mock(AuditRetentionEventRepository.class, CALLS_REAL_METHODS);

    assertThat(repository.existsPurgeBoundaryBefore(null, Instant.now())).isFalse();
    verify(repository, never()).existsPurgeBoundaryBeforeRaw(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void existsPurgeBoundaryBeforeRetornaFalseSemTocarNoBancoQuandoCreatedAtNulo() {
    AuditRetentionEventRepository repository = mock(AuditRetentionEventRepository.class, CALLS_REAL_METHODS);

    assertThat(repository.existsPurgeBoundaryBefore(UUID.randomUUID(), null)).isFalse();
  }
}
