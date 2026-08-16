package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicoInsumo;

/**
 * Espelha {@code modules/inventory/domain/repository/ServicoInsumoRepository.java}.
 *
 * <p>Os tres metodos do original estao aqui. Repare na assimetria, <b>preservada</b>:
 * {@code findByTenantAndService} e {@code findByTenantAndItem} filtram {@code ativo = true}, mas
 * {@code findByTenantServiceAndItem} <b>nao</b> — ele existe para detectar duplicidade no cadastro,
 * onde um insumo desativado ainda conta como ja existente.
 */
@Repository
public interface ServicoInsumoRepository extends JpaRepository<ServicoInsumo, UUID> {

  @Query(
      "select i from ServicoInsumo i where i.tenantId = :tenantId and i.serviceId = :serviceId "
          + "and i.ativo = true order by i.createdAt asc")
  List<ServicoInsumo> findByTenantAndService(
      @Param("tenantId") UUID tenantId, @Param("serviceId") UUID serviceId);

  @Query(
      "select i from ServicoInsumo i where i.tenantId = :tenantId "
          + "and i.itemEstoqueId = :itemEstoqueId and i.ativo = true")
  List<ServicoInsumo> findByTenantAndItem(
      @Param("tenantId") UUID tenantId, @Param("itemEstoqueId") UUID itemEstoqueId);

  List<ServicoInsumo> findByTenantIdAndServiceIdAndItemEstoqueId(
      UUID tenantId, UUID serviceId, UUID itemEstoqueId);

  Optional<ServicoInsumo> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Equivalente ao {@code firstResult()} do original: sem match devolve vazio, nao erro. */
  default Optional<ServicoInsumo> findByTenantServiceAndItem(
      UUID tenantId, UUID serviceId, UUID itemEstoqueId) {
    return findByTenantIdAndServiceIdAndItemEstoqueId(tenantId, serviceId, itemEstoqueId).stream()
        .findFirst();
  }
}
