package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoquePedidoCompra;

/**
 * Espelha {@code modules/inventory/domain/repository/EstoquePedidoCompraRepository.java} (sem
 * metodo proprio no original).
 */
@Repository
public interface EstoquePedidoCompraRepository
    extends JpaRepository<EstoquePedidoCompra, UUID>, JpaSpecificationExecutor<EstoquePedidoCompra> {

  Optional<EstoquePedidoCompra> findByIdAndTenantId(UUID id, UUID tenantId);
}
