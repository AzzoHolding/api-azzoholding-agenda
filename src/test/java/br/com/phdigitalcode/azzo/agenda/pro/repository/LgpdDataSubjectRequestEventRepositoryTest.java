package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequestEvent;

/** Cobre o metodo {@code default} de {@link LgpdDataSubjectRequestEventRepository}. */
class LgpdDataSubjectRequestEventRepositoryTest {

  @Test
  void listByRequestIdDelegaParaFindByRequestIdOrderByCreatedAtDescIdDesc() {
    LgpdDataSubjectRequestEventRepository repository =
        mock(LgpdDataSubjectRequestEventRepository.class, CALLS_REAL_METHODS);
    UUID requestId = UUID.randomUUID();
    LgpdDataSubjectRequestEvent event = new LgpdDataSubjectRequestEvent();
    when(repository.findByRequestIdOrderByCreatedAtDescIdDesc(requestId)).thenReturn(List.of(event));

    assertThat(repository.listByRequestId(requestId)).containsExactly(event);
  }
}
