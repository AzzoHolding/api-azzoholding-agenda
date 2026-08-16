package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueTransferencia;

/**
 * Espelha {@code modules/inventory/domain/repository/EstoqueTransferenciaRepository.java} (sem
 * metodo proprio no original).
 */
@Repository
public interface EstoqueTransferenciaRepository
    extends JpaRepository<EstoqueTransferencia, UUID>,
        JpaSpecificationExecutor<EstoqueTransferencia> {

  Optional<EstoqueTransferencia> findByIdAndTenantId(UUID id, UUID tenantId);
}
