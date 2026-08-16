package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueFornecedor;

/**
 * Espelha {@code modules/inventory/domain/repository/EstoqueFornecedorRepository.java} (sem metodo
 * proprio no original).
 */
@Repository
public interface EstoqueFornecedorRepository
    extends JpaRepository<EstoqueFornecedor, UUID>, JpaSpecificationExecutor<EstoqueFornecedor> {

  Optional<EstoqueFornecedor> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Resolve em lote o {@code fornecedorNome} de uma pagina de pedidos (evita N+1 no mapper). */
  List<EstoqueFornecedor> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
