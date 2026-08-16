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

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantOperationalSettings;

/**
 * Cobre o metodo {@code default} {@link TenantOperationalSettingsRepository#findByTenantIdOrCreate},
 * usado por {@code ReminderProcessingService} — espelha
 * {@code TenantOperationalSettingsRepository.findByTenantIdOrCreate} do original (Panache). Mesmo
 * padrao de {@code TenantWhatsAppConfigRepositoryTest}: metodos {@code default} nao sao
 * interceptados pelo Mockito sem {@link org.mockito.Answers#CALLS_REAL_METHODS}.
 */
class TenantOperationalSettingsRepositoryTest {

  @Test
  void retornaConfiguracaoExistenteSemPersistirNova() {
    TenantOperationalSettingsRepository repository =
        mock(TenantOperationalSettingsRepository.class, org.mockito.Answers.CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    TenantOperationalSettings existing = new TenantOperationalSettings();
    existing.setTenantId(tenantId);
    when(repository.findById(tenantId)).thenReturn(Optional.of(existing));

    TenantOperationalSettings result = repository.findByTenantIdOrCreate(tenantId);

    assertThat(result).isSameAs(existing);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void criaEPersisteComSaveAndFlushQuandoNaoExiste() {
    TenantOperationalSettingsRepository repository =
        mock(TenantOperationalSettingsRepository.class, org.mockito.Answers.CALLS_REAL_METHODS);
    UUID tenantId = UUID.randomUUID();
    when(repository.findById(tenantId)).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any(TenantOperationalSettings.class))).thenAnswer(inv -> inv.getArgument(0));

    TenantOperationalSettings result = repository.findByTenantIdOrCreate(tenantId);

    assertThat(result.getTenantId()).isEqualTo(tenantId);
    assertThat(result.isD1ReminderEnabled()).isTrue();
    verify(repository).saveAndFlush(any(TenantOperationalSettings.class));
  }
}
