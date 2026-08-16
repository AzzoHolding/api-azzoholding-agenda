package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Payment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPayment;

/** Espelha {@code modules/billing/domain/repository/PaymentRepository.java}. */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  List<Payment> findByAsaasPaymentId(String asaasPaymentId);

  /** O original devolve vazio (nao lanca) para id nulo/em branco, e aplica {@code trim()}. */
  default Optional<Payment> buscarPorAsaasPaymentId(String asaasPaymentId) {
    if (asaasPaymentId == null || asaasPaymentId.isBlank()) return Optional.empty();
    return findByAsaasPaymentId(asaasPaymentId.trim()).stream().findFirst();
  }

  @Query("""
      select p from Payment p
      where p.subscriptionId = :subscriptionId
      order by p.dueDate desc nulls last, p.createdAt desc
      """)
  List<Payment> listarPorAssinatura(@Param("subscriptionId") UUID subscriptionId);

  default Optional<Payment> findLatestBySubscriptionId(UUID subscriptionId) {
    if (subscriptionId == null) return Optional.empty();
    return listarPorAssinatura(subscriptionId).stream().findFirst();
  }

  List<Payment> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  /** Pendencias do tenant — base do cleanup de cobrancas antes de contratar um novo plano. */
  List<Payment> findByTenantIdAndStatus(UUID tenantId, StatusPayment status);

  /**
   * Dedup de cobrança: já existe um pagamento PENDENTE, do mesmo tipo e valor, para o mesmo
   * produto, ainda não vencido? Navega {@code subscription.productId}/{@code subscription.planCode}
   * — é por isso que {@code Payment} mantém o {@code @ManyToOne subscription}.
   *
   * <p>"Não vencido" tem duas leituras no original, preservadas: {@code expiresAt} no futuro
   * quando existe; senão, {@code dueDate} de hoje em diante.
   */
  @Query("""
      select count(p) from Payment p
      where p.tenantId = :tenantId
        and upper(p.billingType) = :billingType
        and p.amountCents = :amountCents
        and p.status = :status
        and p.subscription is not null
        and (p.subscription.productId = :productId or upper(p.subscription.planCode) = :productCode)
        and (
          (p.expiresAt is not null and p.expiresAt >= :now)
          or (p.expiresAt is null and p.dueDate is not null and p.dueDate >= :today)
        )
      """)
  long contarPendentesEquivalentes(
      @Param("tenantId") UUID tenantId,
      @Param("billingType") String billingType,
      @Param("amountCents") long amountCents,
      @Param("status") StatusPayment status,
      @Param("productId") UUID productId,
      @Param("productCode") String productCode,
      @Param("now") Instant now,
      @Param("today") LocalDate today);

  default boolean existePagamentoMesmoTipoEValorNaoVencido(
      UUID tenantId, UUID productId, String billingType, long amountCents) {
    if (tenantId == null || productId == null || billingType == null || billingType.isBlank()) {
      return false;
    }
    return contarPendentesEquivalentes(
            tenantId,
            billingType.trim().toUpperCase(),
            amountCents,
            StatusPayment.PENDING,
            productId,
            productId.toString().toUpperCase(),
            Instant.now(),
            LocalDate.now())
        > 0;
  }
}
