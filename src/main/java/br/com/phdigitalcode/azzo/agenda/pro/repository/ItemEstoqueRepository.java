package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

/**
 * Espelha {@code modules/inventory/domain/repository/ItemEstoqueRepository.java} (que no original
 * nao declara metodo proprio — o service monta HQL dinamico direto no Panache).
 *
 * <p>O {@link JpaSpecificationExecutor} cobre a listagem filtrada de
 * {@code ServicoEstoque.listarItens}, que combina busca textual, {@code ativo}, "abaixo do minimo"
 * e cursor — armadilha 8: montar isso como JPQL com {@code :param is null} quebra no PostgreSQL.
 */
@Repository
public interface ItemEstoqueRepository
    extends JpaRepository<ItemEstoque, UUID>, JpaSpecificationExecutor<ItemEstoque> {

  Optional<ItemEstoque> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Le o item com {@code SELECT ... FOR UPDATE}: toda mudanca de saldo passa por aqui.
   *
   * <p>Sem a trava, duas baixas simultaneas do mesmo item liam o mesmo saldo e a segunda gravacao
   * apagava a primeira. Com ela, a segunda espera a primeira terminar e le o saldo ja baixado — e a
   * guarda de idempotencia do consumo (um {@code count} feito depois da trava) passa a ser atomica.
   * O timeout evita que uma transacao presa segure a fila inteira.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
  @Query("select i from ItemEstoque i where i.id = :id and i.tenantId = :tenantId")
  Optional<ItemEstoque> travarPorIdETenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  List<ItemEstoque> findByTenantId(UUID tenantId);

  /** Resolve em lote os itens de uma pagina de movimentacoes/insumos (evita N+1 no mapper). */
  List<ItemEstoque> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

  /**
   * Porte de {@code find("tenantId = ?1 and sku = ?2", ...).firstResult()} da importacao em massa.
   * O SKU ja chega normalizado em maiuscula pelo chamador, como no original.
   */
  Optional<ItemEstoque> findFirstByTenantIdAndSku(UUID tenantId, String sku);
}
