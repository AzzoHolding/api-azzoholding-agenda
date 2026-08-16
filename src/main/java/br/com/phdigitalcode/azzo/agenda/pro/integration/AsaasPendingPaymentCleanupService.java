package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Payment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Subscription;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPayment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusSubscription;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PaymentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SubscriptionRepository;

/**
 * Espelha {@code infrastructure/payment/AsaasPendingPaymentCleanupService.java}: antes de o tenant
 * contratar um novo plano, as cobrancas PENDENTES antigas sao canceladas — no Asaas quando a
 * integracao esta ligada, e sempre no banco.
 *
 * <p>Fidelidade ao original preservada em tres pontos que parecem descuido e nao sao:
 *
 * <ul>
 *   <li>a falha ao cancelar no Asaas e apenas logada — o pagamento local e cancelado do mesmo
 *       jeito, e a contagem devolvida inclui ele;
 *   <li>a assinatura so e cancelada quando esta {@code PENDING}; {@code ACTIVE} passa intacta;
 *   <li>com {@code app.asaas.enabled=false} nenhuma chamada remota acontece, mas o cancelamento
 *       local ocorre normalmente.
 * </ul>
 *
 * <p>As entidades vem <b>managed</b> dos repositorios dentro da transacao, entao as mutacoes de
 * status sao gravadas no commit por dirty checking — nao ha {@code save()} explicito, igual ao
 * Panache do original.
 */
@Service
public class AsaasPendingPaymentCleanupService {

  private static final Logger LOG =
      LoggerFactory.getLogger(AsaasPendingPaymentCleanupService.class);

  private final PaymentRepository paymentRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final AsaasClient asaasClient;
  private final String asaasApiKey;
  private final boolean asaasEnabled;

  public AsaasPendingPaymentCleanupService(
      PaymentRepository paymentRepository,
      SubscriptionRepository subscriptionRepository,
      AsaasClient asaasClient,
      @Value("${app.asaas.api-key:__unset__}") String asaasApiKey,
      @Value("${app.asaas.enabled:false}") boolean asaasEnabled) {
    this.paymentRepository = paymentRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.asaasClient = asaasClient;
    this.asaasApiKey = asaasApiKey;
    this.asaasEnabled = asaasEnabled;
  }

  @Transactional
  public int closePendingPaymentsForTenant(UUID tenantId) {
    if (tenantId == null) return 0;
    List<Payment> pendentes =
        paymentRepository.findByTenantIdAndStatus(tenantId, StatusPayment.PENDING);
    if (pendentes.isEmpty()) return 0;

    int total = 0;
    for (Payment payment : pendentes) {
      cancelRemotePaymentIfPossible(payment);
      cancelPendingSubscriptionIfNecessary(payment);
      payment.setStatus(StatusPayment.CANCELLED);
      total++;
    }
    return total;
  }

  private void cancelRemotePaymentIfPossible(Payment payment) {
    if (!asaasEnabled) return;
    if (payment == null
        || payment.getAsaasPaymentId() == null
        || payment.getAsaasPaymentId().isBlank()) {
      return;
    }
    try {
      asaasClient.cancelPayment(requiredApiKey(), payment.getAsaasPaymentId());
    } catch (Exception ex) {
      LOG.warn(
          "Falha ao cancelar cobranca pendente no Asaas paymentId={}: {}",
          payment.getAsaasPaymentId(),
          ex.getMessage());
    }
  }

  private void cancelPendingSubscriptionIfNecessary(Payment payment) {
    String asaasSubscriptionId =
        payment != null
                && payment.getAsaasSubscriptionId() != null
                && !payment.getAsaasSubscriptionId().isBlank()
            ? payment.getAsaasSubscriptionId()
            : null;
    if (asaasSubscriptionId == null) return;

    Subscription subscription =
        subscriptionRepository.buscarPorAsaasSubscriptionId(asaasSubscriptionId).orElse(null);
    if (subscription == null || subscription.getStatus() != StatusSubscription.PENDING) return;

    if (asaasEnabled) {
      try {
        asaasClient.cancelSubscription(requiredApiKey(), asaasSubscriptionId);
      } catch (Exception ex) {
        LOG.warn(
            "Falha ao cancelar assinatura pendente no Asaas subscriptionId={}: {}",
            asaasSubscriptionId,
            ex.getMessage());
      }
    }

    subscription.setStatus(StatusSubscription.CANCELLED);
    subscription.setCancelledAt(Instant.now());
  }

  private String requiredApiKey() {
    if (asaasApiKey == null || asaasApiKey.isBlank() || "__unset__".equals(asaasApiKey)) {
      throw new IllegalStateException("ASAAS_API_KEY nao configurada");
    }
    return asaasApiKey.trim();
  }
}
