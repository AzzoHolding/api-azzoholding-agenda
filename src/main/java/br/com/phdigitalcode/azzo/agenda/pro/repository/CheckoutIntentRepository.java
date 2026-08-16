package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutIntent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import jakarta.persistence.LockModeType;

/** Espelha {@code modules/billing/domain/repository/CheckoutIntentRepository.java}. */
@Repository
public interface CheckoutIntentRepository extends JpaRepository<CheckoutIntent, UUID> {

  /**
   * Equivalente ao {@code entityManager.find(..., PESSIMISTIC_WRITE)} do original: serializa a
   * confirmacao da mesma intent, impedindo que duas requisicoes concorrentes gerem dois
   * {@code CheckoutOrder} para o mesmo checkout.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from CheckoutIntent i where i.id = :id")
  Optional<CheckoutIntent> findByIdForUpdate(@Param("id") UUID id);

  List<CheckoutIntent> findByTenantIdAndPaymentReference(UUID tenantId, String paymentReference);

  default Optional<CheckoutIntent> buscarPorReferenciaPagamento(
      UUID tenantId, String paymentReference) {
    if (tenantId == null || paymentReference == null || paymentReference.isBlank()) {
      return Optional.empty();
    }
    return findByTenantIdAndPaymentReference(tenantId, paymentReference).stream().findFirst();
  }

  /**
   * Expiracao em lote (scheduler). E um {@code UPDATE} em massa de propósito: percorrer entidade a
   * entidade custaria uma leitura completa da tabela a cada minuto.
   */
  @Modifying
  @Query("""
      update CheckoutIntent i
      set i.status = :novoStatus, i.updatedAt = :agora
      where i.status = :statusAtual and i.expiresAt < :agora
      """)
  int expirarPendentes(
      @Param("novoStatus") StatusCheckout novoStatus,
      @Param("statusAtual") StatusCheckout statusAtual,
      @Param("agora") Instant agora);
}
