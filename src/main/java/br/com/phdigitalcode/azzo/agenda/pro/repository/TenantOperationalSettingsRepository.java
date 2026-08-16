package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantOperationalSettings;

/**
 * Espelha {@code modules/settings/domain/repository/TenantOperationalSettingsRepository.java}.
 *
 * <p>O {@code findByTenantIdOrCreate} do original <b>insere</b> a linha na primeira leitura, entao
 * todo metodo de service que passa por ele e escrita — nao pode ser {@code readOnly}. Aqui ele fica
 * no {@code TenantOperationalSettingsService} (precisa de {@code saveAndFlush} para que o
 * {@code @PrePersist} rode antes das leituras subsequentes, ver armadilha do
 * {@code persist()} do Panache vs. Spring Data).
 */
@Repository
public interface TenantOperationalSettingsRepository
    extends JpaRepository<TenantOperationalSettings, UUID> {

  /**
   * Equivalente a {@code TenantOperationalSettingsRepository.findByTenantIdOrCreate} do original
   * (Panache) — mesmo corpo ja usado, ate agora de forma privada, em
   * {@code TenantOperationalSettingsService}. Exposto aqui como {@code default} para que
   * {@code ReminderProcessingService} (fora daquele service) tambem consiga reusar sem duplicar a
   * logica de criacao sob demanda. {@code saveAndFlush} porque o {@code persist()} do Panache
   * original emite o INSERT na hora; o {@code save()} do Spring Data adiaria ate o commit.
   */
  default TenantOperationalSettings findByTenantIdOrCreate(UUID tenantId) {
    return findById(tenantId)
        .orElseGet(
            () -> {
              TenantOperationalSettings created = new TenantOperationalSettings();
              created.setTenantId(tenantId);
              return saveAndFlush(created);
            });
  }
}
