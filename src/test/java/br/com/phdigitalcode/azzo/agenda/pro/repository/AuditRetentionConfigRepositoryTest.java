package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/** Espelha {@code modules/audit/domain/repository/AuditRetentionConfigRepository.java}. */
class AuditRetentionConfigRepositoryTest {

  private EntityManager entityManager;
  private Query query;
  private AuditRetentionConfigRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    entityManager = mock(EntityManager.class);
    query = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    repository = new AuditRetentionConfigRepository();
    Field field = AuditRetentionConfigRepository.class.getDeclaredField("entityManager");
    field.setAccessible(true);
    field.set(repository, entityManager);
  }

  @Test
  void retornaValorQuandoLinhaExiste() {
    when(query.getResultStream()).thenReturn(Stream.of(Integer.valueOf(180)));

    assertThat(repository.findRetentionPeriodDays()).contains(180);
  }

  @Test
  void aceitaNumeroDeOutroTipoNumerico() {
    when(query.getResultStream()).thenReturn(Stream.of(Long.valueOf(365L)));

    assertThat(repository.findRetentionPeriodDays()).contains(365);
  }

  @Test
  void retornaVazioQuandoNaoHaLinha() {
    when(query.getResultStream()).thenReturn(Stream.empty());

    assertThat(repository.findRetentionPeriodDays()).isEmpty();
  }

  @Test
  void retornaVazioQuandoValorNaoEhNumericoParseavel() {
    when(query.getResultStream()).thenReturn(Stream.of("abc"));

    assertThat(repository.findRetentionPeriodDays()).isEmpty();
  }

  @Test
  void parseiaStringNumericaValida() {
    when(query.getResultStream()).thenReturn(Stream.of("90"));

    assertThat(repository.findRetentionPeriodDays()).contains(90);
  }
}
