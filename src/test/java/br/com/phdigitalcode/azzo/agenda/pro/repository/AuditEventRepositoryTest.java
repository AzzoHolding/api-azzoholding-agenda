package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import br.com.phdigitalcode.azzo.agenda.pro.dto.AuditDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;

/**
 * Cobre os metodos {@code default} de {@link AuditEventRepository} — nao interceptados pelo
 * Mockito sem {@code Answers.CALLS_REAL_METHODS} (mesmo padrao ja usado em
 * {@code TermsAcceptanceRepositoryTest}).
 */
class AuditEventRepositoryTest {

  @Test
  void listByTenantAndEntityRetornaVazioSemTocarNoBancoQuandoArgumentoInvalido() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);

    assertThat(repository.listByTenantAndEntity(null, "PAYMENT", "1", 10)).isEmpty();
    assertThat(repository.listByTenantAndEntity(UUID.randomUUID(), null, "1", 10)).isEmpty();
    assertThat(repository.listByTenantAndEntity(UUID.randomUUID(), " ", "1", 10)).isEmpty();
    assertThat(repository.listByTenantAndEntity(UUID.randomUUID(), "PAYMENT", null, 10)).isEmpty();
    assertThat(repository.listByTenantAndEntity(UUID.randomUUID(), "PAYMENT", " ", 10)).isEmpty();
    verify(repository, never()).findByTenantAndEntity(any(), any(), any(), any());
  }

  @Test
  void listByTenantAndEntityNormalizaLimiteInvalidoPara100EDelegaComTrim() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    AuditEvent event = new AuditEvent();
    when(repository.findByTenantAndEntity(tenantId, "PAYMENT", "1", Limit.of(100))).thenReturn(List.of(event));

    List<AuditEvent> result = repository.listByTenantAndEntity(tenantId, " PAYMENT ", " 1 ", 0);

    assertThat(result).containsExactly(event);
  }

  @Test
  void listByTenantAndEntityLimitaATeto200() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    when(repository.findByTenantAndEntity(tenantId, "PAYMENT", "1", Limit.of(200))).thenReturn(List.of());

    repository.listByTenantAndEntity(tenantId, "PAYMENT", "1", 500);

    verify(repository).findByTenantAndEntity(tenantId, "PAYMENT", "1", Limit.of(200));
  }

  @Test
  void findLastByTenantRetornaVazioQuandoTenantIdNulo() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    assertThat(repository.findLastByTenant(null)).isEmpty();
  }

  @Test
  void findLastByTenantRetornaVazioQuandoNaoHaEventos() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    when(repository.findByTenantOrderByCreatedAtDesc(tenantId, Limit.of(1))).thenReturn(List.of());

    assertThat(repository.findLastByTenant(tenantId)).isEmpty();
  }

  @Test
  void findLastByTenantRetornaOMaisRecente() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    AuditEvent event = new AuditEvent();
    when(repository.findByTenantOrderByCreatedAtDesc(tenantId, Limit.of(1))).thenReturn(List.of(event));

    assertThat(repository.findLastByTenant(tenantId)).contains(event);
  }

  @Test
  void findTenantIdsWithEventsBeforeRetornaVazioQuandoCutoffNulo() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    assertThat(repository.findTenantIdsWithEventsBefore(null, 10)).isEmpty();
  }

  @Test
  void findTenantIdsWithEventsBeforeNormalizaMaxTenantsInvalidoPara1000() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    Instant cutoff = Instant.now();
    when(repository.findTenantIdsBefore(cutoff, 1000)).thenReturn(List.of(UUID.randomUUID()));

    List<UUID> result = repository.findTenantIdsWithEventsBefore(cutoff, 0);

    assertThat(result).hasSize(1);
    verify(repository).findTenantIdsBefore(cutoff, 1000);
  }

  @Test
  void findOldestCreatedAtBeforeRetornaVazioQuandoArgumentoNulo() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    assertThat(repository.findOldestCreatedAtBefore(null, Instant.now())).isEmpty();
    assertThat(repository.findOldestCreatedAtBefore(UUID.randomUUID(), null)).isEmpty();
  }

  @Test
  void findOldestCreatedAtBeforeDelegaParaQueryNativa() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    Instant cutoff = Instant.now();
    Instant oldest = cutoff.minusSeconds(100);
    when(repository.findOldestCreatedAtBeforeRaw(tenantId, cutoff)).thenReturn(oldest);

    assertThat(repository.findOldestCreatedAtBefore(tenantId, cutoff)).contains(oldest);
  }

  @Test
  void purgeBeforeByTenantRetornaZeroSemTocarNoBancoQuandoArgumentoNulo() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    assertThat(repository.purgeBeforeByTenant(null, Instant.now())).isEqualTo(0);
    assertThat(repository.purgeBeforeByTenant(UUID.randomUUID(), null)).isEqualTo(0);
    verify(repository, never()).enableRetentionPurgeFlag();
  }

  @Test
  void purgeBeforeByTenantAtivaFlagDeSessaoAntesDoDelete() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    Instant cutoff = Instant.now();
    when(repository.deleteBeforeByTenant(tenantId, cutoff)).thenReturn(7);

    long purged = repository.purgeBeforeByTenant(tenantId, cutoff);

    assertThat(purged).isEqualTo(7L);
    verify(repository).enableRetentionPurgeFlag();
    verify(repository).deleteBeforeByTenant(tenantId, cutoff);
  }

  @Test
  void aggregateFilterOptionsAgrupaValoresDistintosPreservandoOrdem() {
    AuditEventRepository repository = mock(AuditEventRepository.class, CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    List<Object[]> rows = List.of(
        new Object[] {"FINANCE", "SUCCESS", "PAYMENT_CREATED", "PAYMENT", "API"},
        new Object[] {"FINANCE", "ERROR", "PAYMENT_CREATED", "PAYMENT", "API"},
        new Object[] {null, null, null, null, null});
    when(repository.findFilterOptionRows(eq(tenantId), eq(AuditConstants.Module.SYSTEM), any(), any()))
        .thenReturn(rows);

    AuditDtos.AuditFilterOptionsResponse response = repository.aggregateFilterOptions(tenantId, null, null);

    assertThat(response.modules).containsExactly("FINANCE");
    assertThat(response.statuses).containsExactly("SUCCESS", "ERROR");
    assertThat(response.actions).containsExactly("PAYMENT_CREATED");
    assertThat(response.entityTypes).containsExactly("PAYMENT");
    assertThat(response.sourceChannels).containsExactly("API");
  }
}
