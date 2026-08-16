package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequest;

/**
 * Cobre os metodos {@code default} de {@link LgpdDataSubjectRequestRepository} — nao
 * interceptados pelo Mockito sem {@code Answers.CALLS_REAL_METHODS}.
 */
class LgpdDataSubjectRequestRepositoryTest {

  private final UUID tenantId = UUID.randomUUID();

  @Test
  void findByTenantAndIdDelegaParaFindByTenantIdAndId() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    UUID id = UUID.randomUUID();
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    when(repository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(entity));

    assertThat(repository.findByTenantAndId(tenantId, id)).contains(entity);
  }

  @Test
  void findByTenantAndProtocolDelegaParaFindByTenantIdAndProtocolCode() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    when(repository.findByTenantIdAndProtocolCode(tenantId, "LGPD-123")).thenReturn(Optional.of(entity));

    assertThat(repository.findByTenantAndProtocol(tenantId, "LGPD-123")).contains(entity);
  }

  @Test
  @SuppressWarnings("unchecked")
  void listByTenantMontaSpecificationENormalizaLimite() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    Page<LgpdDataSubjectRequest> page = new PageImpl<>(List.of(entity));
    when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

    List<LgpdDataSubjectRequest> result = repository.listByTenant(tenantId, "aberto", "acesso", 500);

    assertThat(result).containsExactly(entity);
    verify(repository).findAll(any(Specification.class), any(PageRequest.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void listByTenantUsaLimitePadraoQuandoNulo() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

    repository.listByTenant(tenantId, null, null, null);

    verify(repository).findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 50,
        org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("createdAt"),
            org.springframework.data.domain.Sort.Order.desc("id")))));
  }

  @Test
  void countByTenantAndStatusUppercaseETrim() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    when(repository.countByTenantIdAndStatusIgnoreCase(tenantId, "ABERTO")).thenReturn(5L);

    assertThat(repository.countByTenantAndStatus(tenantId, " aberto ")).isEqualTo(5L);
  }

  @Test
  void countUnassignedActiveDelegaParaQueryRaw() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    when(repository.countUnassignedActiveRaw(tenantId)).thenReturn(3L);

    assertThat(repository.countUnassignedActive(tenantId)).isEqualTo(3L);
  }

  @Test
  void countOverdueInitialResponseDelegaParaQueryRaw() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    Instant cutoff = Instant.now();
    when(repository.countOverdueInitialResponseRaw(tenantId, cutoff)).thenReturn(2L);

    assertThat(repository.countOverdueInitialResponse(tenantId, cutoff)).isEqualTo(2L);
  }

  @Test
  void countOverdueFinalResolutionDelegaParaQueryRaw() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    Instant cutoff = Instant.now();
    when(repository.countOverdueFinalResolutionRaw(tenantId, cutoff)).thenReturn(1L);

    assertThat(repository.countOverdueFinalResolution(tenantId, cutoff)).isEqualTo(1L);
  }

  @Test
  void listOperationalAlertsNormalizaLimiteEDelega() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    Instant cutoff = Instant.now();
    LgpdDataSubjectRequest alert = new LgpdDataSubjectRequest();
    when(repository.listOperationalAlertsRaw(tenantId, cutoff, Limit.of(20))).thenReturn(List.of(alert));

    assertThat(repository.listOperationalAlerts(tenantId, cutoff, null)).containsExactly(alert);
  }

  @Test
  void listOperationalAlertsLimitaATeto100() {
    LgpdDataSubjectRequestRepository repository = mock(LgpdDataSubjectRequestRepository.class, CALLS_REAL_METHODS);
    Instant cutoff = Instant.now();
    when(repository.listOperationalAlertsRaw(tenantId, cutoff, Limit.of(100))).thenReturn(List.of());

    repository.listOperationalAlerts(tenantId, cutoff, 500);

    verify(repository).listOperationalAlertsRaw(tenantId, cutoff, Limit.of(100));
  }
}
