package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsAcceptance;

/**
 * Cobre os metodos {@code default} de {@link TermsAcceptanceRepository}
 * ({@code hasAccepted}/{@code findLatestByTenantId}) — metodos default nao sao interceptados pelo
 * Mockito por padrao, entao o mock precisa de {@code Answers.CALLS_REAL_METHODS}.
 */
class TermsAcceptanceRepositoryTest {

  @Test
  void hasAcceptedRetornaTrueQuandoExisteAoMenosUmRegistro() {
    TermsAcceptanceRepository repository = mock(TermsAcceptanceRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    when(repository.countByTenantId(tenantId)).thenReturn(2L);

    assertThat(repository.hasAccepted(tenantId)).isTrue();
  }

  @Test
  void hasAcceptedRetornaFalseQuandoNaoHaRegistro() {
    TermsAcceptanceRepository repository = mock(TermsAcceptanceRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    when(repository.countByTenantId(tenantId)).thenReturn(0L);

    assertThat(repository.hasAccepted(tenantId)).isFalse();
  }

  @Test
  void findLatestByTenantIdDelegaParaFindFirstOrderByAcceptedAtDesc() {
    TermsAcceptanceRepository repository = mock(TermsAcceptanceRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    TermsAcceptance latest = new TermsAcceptance();
    when(repository.findFirstByTenantIdOrderByAcceptedAtDesc(tenantId)).thenReturn(Optional.of(latest));

    assertThat(repository.findLatestByTenantId(tenantId)).contains(latest);
  }
}
