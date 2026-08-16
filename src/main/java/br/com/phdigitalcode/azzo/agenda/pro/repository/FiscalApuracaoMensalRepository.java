package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalApuracaoMensalEntity;

/** Espelha {@code modules/fiscal/domain/repository/FiscalApuracaoMensalRepository.java}. */
@Repository
public interface FiscalApuracaoMensalRepository
    extends JpaRepository<FiscalApuracaoMensalEntity, UUID> {

  Optional<FiscalApuracaoMensalEntity> findByTenantIdAndAnoAndMes(UUID tenantId, int ano, int mes);

  /** Guarda do original: tenant nulo devolve vazio sem consultar. */
  default Optional<FiscalApuracaoMensalEntity> findByTenantAnoMes(UUID tenantId, int ano, int mes) {
    if (tenantId == null) return Optional.empty();
    return findByTenantIdAndAnoAndMes(tenantId, ano, mes);
  }
}
