package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueInventarioContagem;

/**
 * Espelha {@code modules/inventory/domain/repository/EstoqueInventarioContagemRepository.java} (sem
 * metodo proprio no original — as tres consultas sao HQL inline no {@code ServicoEstoque}).
 */
@Repository
public interface EstoqueInventarioContagemRepository
    extends JpaRepository<EstoqueInventarioContagem, UUID> {

  /** Guarda de duplicidade de {@code registrarContagemInventario}. */
  long countByInventarioIdAndItemEstoqueIdAndTenantId(
      UUID inventarioId, UUID itemEstoqueId, UUID tenantId);

  List<EstoqueInventarioContagem> findByInventarioIdAndTenantIdOrderByCreatedAtDesc(
      UUID inventarioId, UUID tenantId);

  Optional<EstoqueInventarioContagem> findByIdAndInventarioIdAndTenantId(
      UUID id, UUID inventarioId, UUID tenantId);
}
