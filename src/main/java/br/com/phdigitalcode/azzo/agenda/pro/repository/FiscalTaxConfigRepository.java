package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;

/**
 * Espelha {@code modules/fiscal/domain/repository/FiscalTaxConfigRepository.java}.
 *
 * <p>O original guarda {@code tenantId == null} devolvendo {@code Optional.empty()} antes de
 * consultar. Aqui a guarda e dispensavel sem mudar o resultado: {@code tenant_id = null} em SQL
 * nunca casa com linha alguma, entao a consulta ja devolve vazio.
 */
@Repository
public interface FiscalTaxConfigRepository extends JpaRepository<FiscalTaxConfigEntity, UUID> {

  Optional<FiscalTaxConfigEntity> findByTenantId(UUID tenantId);
}
