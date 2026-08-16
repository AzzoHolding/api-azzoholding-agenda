package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueInventario;

/**
 * Espelha {@code modules/inventory/domain/repository/EstoqueInventarioRepository.java} (que no
 * original nao declara metodo proprio — o service monta HQL dinamico direto no Panache).
 *
 * <p>O {@link JpaSpecificationExecutor} cobre {@code listarInventarios}, que combina busca textual
 * por nome e filtro de status opcionais (armadilha 8).
 */
@Repository
public interface EstoqueInventarioRepository
    extends JpaRepository<EstoqueInventario, UUID>, JpaSpecificationExecutor<EstoqueInventario> {

  Optional<EstoqueInventario> findByIdAndTenantId(UUID id, UUID tenantId);
}
