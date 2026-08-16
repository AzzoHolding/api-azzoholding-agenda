package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;

/**
 * Espelha {@code modules/billing/domain/repository/CheckoutOrderRepository.java}.
 *
 * <p>A entidade mapeia a tabela {@code orders} (nao {@code checkout_orders}).
 */
@Repository
public interface CheckoutOrderRepository extends JpaRepository<CheckoutOrder, UUID> {

  List<CheckoutOrder> findByIntentId(UUID intentId);

  default Optional<CheckoutOrder> buscarPorIntentId(UUID intentId) {
    return findByIntentId(intentId).stream().findFirst();
  }

  @Query("""
      select count(o) from CheckoutOrder o
      where o.tenantId = :tenantId and o.status = :status
        and (o.validUntil is null or o.validUntil >= :now)
      """)
  long contarPlanosVigentes(
      @Param("tenantId") UUID tenantId,
      @Param("status") StatusCheckout status,
      @Param("now") Instant now);

  default boolean possuiPlanoVigente(UUID tenantId, Instant now) {
    return contarPlanosVigentes(tenantId, StatusCheckout.CONFIRMED, now) > 0;
  }

  @Query("""
      select o from CheckoutOrder o
      where o.tenantId = :tenantId and o.status = :status
        and (o.validUntil is null or o.validUntil >= :now)
      order by o.createdAt desc
      """)
  List<CheckoutOrder> listarPlanosVigentes(
      @Param("tenantId") UUID tenantId,
      @Param("status") StatusCheckout status,
      @Param("now") Instant now);

  default Optional<CheckoutOrder> buscarPlanoVigenteMaisRecente(UUID tenantId, Instant now) {
    if (tenantId == null || now == null) return Optional.empty();
    return listarPlanosVigentes(tenantId, StatusCheckout.CONFIRMED, now).stream().findFirst();
  }

  @Query("""
      select o from CheckoutOrder o
      where o.status = :status and o.validUntil >= :inicio and o.validUntil < :fim
      """)
  List<CheckoutOrder> listarVencendoNaJanela(
      @Param("status") StatusCheckout status,
      @Param("inicio") Instant inicio,
      @Param("fim") Instant fim);

  /**
   * Pedidos que ainda nao venceram em relacao a {@code limite} — usado pelo
   * {@code forceExpireCurrentTenant} do admin para empurrar o {@code validUntil} para o passado.
   *
   * <p>Diferente de {@link #listarPlanosVigentes}: aqui a comparacao e estritamente {@code >} e o
   * instante de corte e o "vencido em" calculado, nao {@code now}.
   */
  @Query("""
      select o from CheckoutOrder o
      where o.tenantId = :tenantId and o.status = :status
        and (o.validUntil is null or o.validUntil > :limite)
      """)
  List<CheckoutOrder> listarNaoVencidosAte(
      @Param("tenantId") UUID tenantId,
      @Param("status") StatusCheckout status,
      @Param("limite") Instant limite);

  /**
   * Todos os pedidos confirmados do tenant, do mais novo para o mais antigo, <b>sem</b> filtro de
   * vigencia — e assim que o admin resolve o produto de referencia e localiza o trial a estender.
   */
  @Query("""
      select o from CheckoutOrder o
      where o.tenantId = :tenantId and o.status = :status
      order by o.createdAt desc
      """)
  List<CheckoutOrder> listarConfirmadosMaisRecentesPrimeiro(
      @Param("tenantId") UUID tenantId, @Param("status") StatusCheckout status);
}
