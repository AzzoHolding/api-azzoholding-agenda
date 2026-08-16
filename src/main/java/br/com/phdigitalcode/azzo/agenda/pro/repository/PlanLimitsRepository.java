package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Resolve o limite de profissionais do plano ativo do tenant.
 *
 * <p>No Quarkus original essa logica esta em {@code ServicoProfissionais
 * .obterLimiteProfissionaisDoPlanoObrigatorio}, usando as entidades {@code CheckoutOrder} (modulo
 * {@code billing}) e {@code ProductCapability}. Como o modulo {@code billing} ainda nao foi
 * portado, a MESMA regra e expressa aqui em SQL nativo contra as tabelas ja existentes no schema
 * ({@code orders} + {@code product_capabilities}), preservando exatamente o criterio: ultimo
 * pedido confirmado com {@code valid_until} nulo ou no futuro, ordenado por
 * {@code valid_until desc, created_at desc}.
 *
 * <p>ATENCAO (armadilha encontrada na migracao): a coluna {@code orders.status} NAO guarda o nome
 * do enum ({@code 'CONFIRMED'}) — o {@code StatusCheckoutConverter} do Quarkus persiste a
 * <em>descricao</em> em portugues ({@code 'Confirmado'}). A query abaixo aceita as duas grafias
 * para ser robusta a dados legados, mas o valor canonico gravado hoje e {@code 'Confirmado'}.
 * Tambem: a tabela de pedidos se chama {@code orders} (nao {@code checkout_orders}) e a PK de
 * {@code product_capabilities} e {@code product_id} (nao {@code id}).
 *
 * <p>Quando o modulo {@code billing} for migrado, avaliar substituir por consulta via entidades
 * JPA — o comportamento observavel deve permanecer identico.
 */
@Repository
public class PlanLimitsRepository {

  @PersistenceContext
  private EntityManager entityManager;

  /** @return {@code Optional.empty()} se nao existe plano ativo para o tenant. */
  public Optional<UUID> findActivePlanProductId(UUID tenantId, Instant now) {
    if (tenantId == null) return Optional.empty();
    return entityManager
        .createNativeQuery(
            """
            SELECT co.product_id
            FROM orders co
            WHERE co.tenant_id = :tenantId
              AND co.status IN ('Confirmado', 'CONFIRMED')
              AND (co.valid_until IS NULL OR co.valid_until >= :now)
            ORDER BY co.valid_until DESC NULLS FIRST, co.created_at DESC
            LIMIT 1
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("now", now)
        .getResultStream()
        .findFirst()
        .map(value -> value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value)));
  }

  /** @return {@code Optional.empty()} se o plano nao tem limite de profissionais configurado. */
  public Optional<Integer> findMaxProfessionals(UUID productId) {
    if (productId == null) return Optional.empty();
    return entityManager
        .createNativeQuery(
            "SELECT pc.max_professionals FROM product_capabilities pc WHERE pc.product_id = :productId")
        .setParameter("productId", productId)
        .getResultStream()
        .findFirst()
        .filter(java.util.Objects::nonNull)
        .map(value -> ((Number) value).intValue());
  }
}
