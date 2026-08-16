package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;

/**
 * Cobre o metodo {@code default} {@link TenantWhatsAppConfigRepository#findByTenantIdOrCreate},
 * usado por {@code AssistantInternalController} — espelha
 * {@code TenantWhatsAppConfigRepository.findByTenantIdOrCreate} do original (Panache).
 */
class TenantWhatsAppConfigRepositoryTest {

  @Test
  void retornaConfigExistenteSemPersistirNovo() {
    TenantWhatsAppConfigRepository repository = mock(
        TenantWhatsAppConfigRepository.class, org.mockito.Answers.CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    TenantWhatsAppConfig existing = new TenantWhatsAppConfig();
    existing.setTenantId(tenantId);
    when(repository.findById(tenantId)).thenReturn(Optional.of(existing));

    TenantWhatsAppConfig result = repository.findByTenantIdOrCreate(tenantId);

    assertThat(result).isSameAs(existing);
    verify(repository, never()).save(any());
  }

  @Test
  void criaEPersisteNovoQuandoNaoExiste() {
    TenantWhatsAppConfigRepository repository = mock(
        TenantWhatsAppConfigRepository.class, org.mockito.Answers.CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    when(repository.findById(tenantId)).thenReturn(Optional.empty());
    when(repository.save(any(TenantWhatsAppConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    TenantWhatsAppConfig result = repository.findByTenantIdOrCreate(tenantId);

    assertThat(result.getTenantId()).isEqualTo(tenantId);
    assertThat(result.isCanSchedule()).isTrue();
    assertThat(result.isCanCancel()).isTrue();
    assertThat(result.isCanReschedule()).isTrue();
    verify(repository).save(any(TenantWhatsAppConfig.class));
  }
}
