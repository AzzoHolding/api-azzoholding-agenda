package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LicenseEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Payment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Subscription;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusSubscription;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasPendingPaymentCleanupService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LicenseEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PaymentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SubscriptionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;

/**
 * Espelha {@code modules/billing/application/ServicoBilling.java} — a face do billing para o
 * proprio tenant (OWNER/ADMIN): contratar plano, consultar assinatura vigente, listar pagamentos e
 * cancelar a assinatura.
 *
 * <p>Decisoes de porte:
 *
 * <ul>
 *   <li>{@code Instance<BillingPaymentHandler>} (CDI) vira {@code List<BillingPaymentHandler>}
 *       injetada por construtor. Os tres {@code supports} sao mutuamente exclusivos, entao a
 *       ordem nao importa — igual ao CDI, que tambem nao a garante.
 *   <li>{@code WebApplicationException(msg, status)} vira {@link ApiClientErrorException}, que o
 *       {@code GlobalExceptionHandler} devolve com o mesmo codigo HTTP (422/402/404/409).
 *   <li>{@code TraceContext.traceId()} (SkyWalking) vira {@code CorrelatedLogging.traceId()}.
 *   <li>Somente {@link #cancelarAssinaturaAtual} e transacional no original — {@code criarAssinatura}
 *       delega a transacao para o handler, e as duas consultas <b>nao</b> sao {@code readOnly}
 *       porque {@code licenseStatusService.avaliar} escreve o {@code plan_status_id} do tenant.
 * </ul>
 */
@Service
public class ServicoBilling {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoBilling.class);
  private static final long PLAN_CHANGE_WINDOW_DAYS = 10L;
  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private final List<BillingPaymentHandler> paymentHandlers;
  private final PaymentRepository paymentRepository;
  private final ProductRepository productRepository;
  private final CheckoutOrderRepository checkoutOrderRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final LicenseStatusService licenseStatusService;
  private final ContextoTenant contextoTenant;
  private final AsaasPendingPaymentCleanupService pendingPaymentCleanupService;
  private final AsaasService asaasService;
  private final LicenseEventRepository licenseEventRepository;

  public ServicoBilling(
      List<BillingPaymentHandler> paymentHandlers,
      PaymentRepository paymentRepository,
      ProductRepository productRepository,
      CheckoutOrderRepository checkoutOrderRepository,
      SubscriptionRepository subscriptionRepository,
      LicenseStatusService licenseStatusService,
      ContextoTenant contextoTenant,
      AsaasPendingPaymentCleanupService pendingPaymentCleanupService,
      AsaasService asaasService,
      LicenseEventRepository licenseEventRepository) {
    this.paymentHandlers = paymentHandlers;
    this.paymentRepository = paymentRepository;
    this.productRepository = productRepository;
    this.checkoutOrderRepository = checkoutOrderRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.licenseStatusService = licenseStatusService;
    this.contextoTenant = contextoTenant;
    this.pendingPaymentCleanupService = pendingPaymentCleanupService;
    this.asaasService = asaasService;
    this.licenseEventRepository = licenseEventRepository;
  }

  /**
   * O valor cobrado <b>nunca</b> vem do cliente: o request enviado ao handler e reconstruido a
   * partir do {@code Product} resolvido no banco. O que o corpo original controla e apenas forma de
   * pagamento, documento, descricao, vencimento e dados de cartao.
   */
  public BillingDtos.SubscriptionResponse criarAssinatura(
      BillingDtos.CreateSubscriptionRequest request, String remoteIp) {
    Product plano = resolvePlanoAtivo(request);
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    validarJanelaTrocaPlano(tenantId);
    LocalDate scheduledStartDate = resolveScheduledStartDate(tenantId);

    BillingDtos.CreateSubscriptionRequest normalizedRequest =
        new BillingDtos.CreateSubscriptionRequest();
    normalizedRequest.productId = plano.getId().toString();
    normalizedRequest.planCode = plano.getId().toString();
    normalizedRequest.description = request.description;
    normalizedRequest.amount = plano.getPriceCents();
    normalizedRequest.amountCents = NumericUtil.toCents(plano.getPriceCents());
    normalizedRequest.billingType = request.billingType;
    normalizedRequest.cpfCnpj = request.cpfCnpj;
    normalizedRequest.nextDueDate =
        request.nextDueDate != null && !request.nextDueDate.isBlank()
            ? request.nextDueDate
            : scheduledStartDate != null ? scheduledStartDate.toString() : null;
    normalizedRequest.creditCard = request.creditCard;
    normalizedRequest.creditCardHolderInfo = request.creditCardHolderInfo;

    pendingPaymentCleanupService.closePendingPaymentsForTenant(tenantId);

    BillingPaymentHandler handler =
        paymentHandlers.stream()
            .filter(h -> h.supports(normalizedRequest.billingType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Forma de pagamento nao suportada"));
    LOG.info(
        CorrelatedLogging.context(
            "Criacao de assinatura iniciada",
            "tenantId", tenantId,
            "productId", plano.getId(),
            "billingType", normalizedRequest.billingType,
            "scheduledStartDate", normalizedRequest.nextDueDate,
            "remoteIp", remoteIp));
    try {
      BillingDtos.SubscriptionResponse response = handler.process(normalizedRequest, remoteIp);
      LOG.info(
          CorrelatedLogging.context(
              "Criacao de assinatura concluida",
              "tenantId", tenantId,
              "productId", plano.getId(),
              "subscriptionId", response != null ? response.subscriptionId : null,
              "status", response != null ? response.status : null));
      return response;
    } catch (RuntimeException exception) {
      LOG.error(
          CorrelatedLogging.context(
              "Criacao de assinatura falhou",
              "tenantId", tenantId,
              "productId", plano.getId(),
              "billingType", normalizedRequest.billingType,
              "root", CorrelatedLogging.throwableSummary(exception)),
          exception);
      throw exception;
    }
  }

  /**
   * Troca de plano so e liberada no trial ou nos ultimos 10 dias de vigencia. Sem plano vigente
   * (tenant novo ou expirado) passa direto.
   *
   * <p>Assimetria do original preservada: pedido vigente <b>sem</b> {@code validUntil} bloqueia a
   * troca (422), embora {@code buscarPlanoVigenteMaisRecente} trate {@code validUntil} nulo como
   * "vigente para sempre".
   */
  private void validarJanelaTrocaPlano(UUID tenantId) {
    if (tenantId == null) return;
    Instant now = Instant.now();
    CheckoutOrder vigente =
        checkoutOrderRepository.buscarPlanoVigenteMaisRecente(tenantId, now).orElse(null);
    if (vigente == null) {
      // Sem plano vigente (novo tenant ou expirado): permite contratar normalmente.
      return;
    }

    Product produtoVigente =
        vigente.getProductId() != null
            ? productRepository.findById(vigente.getProductId()).orElse(null)
            : null;
    if (produtoVigente != null && produtoVigente.isTrial()) {
      return;
    }

    if (vigente.getValidUntil() == null) {
      LOG.warn(
          CorrelatedLogging.context(
              "Troca de plano bloqueada", "tenantId", tenantId, "reason", "valid_until_missing"));
      throw new ApiClientErrorException(
          "Troca de plano so e permitida no trial ou nos ultimos 10 dias antes do vencimento.",
          422);
    }

    long remainingDays = ChronoUnit.DAYS.between(now, vigente.getValidUntil());
    if (remainingDays > PLAN_CHANGE_WINDOW_DAYS) {
      LOG.warn(
          CorrelatedLogging.context(
              "Troca de plano bloqueada",
              "tenantId", tenantId,
              "remainingDays", remainingDays,
              "reason", "plan_change_window"));
      throw new ApiClientErrorException(
          "Troca de plano liberada apenas quando faltarem 10 dias ou menos para o vencimento.",
          422);
    }
  }

  /** O novo plano comeca a valer quando o atual vence — nunca antes. */
  private LocalDate resolveScheduledStartDate(UUID tenantId) {
    if (tenantId == null) return null;
    Instant now = Instant.now();
    return checkoutOrderRepository
        .buscarPlanoVigenteMaisRecente(tenantId, now)
        .map(CheckoutOrder::getValidUntil)
        .filter(validUntil -> validUntil != null && validUntil.isAfter(now))
        .map(validUntil -> validUntil.atZone(ZONE_BR).toLocalDate())
        .orElse(null);
  }

  /**
   * Tenant sem {@code Subscription} cai no pedido de checkout vigente (trial ou compra avulsa); sem
   * nenhum dos dois, {@code 402 Payment Required} — e esse status que o frontend usa para mandar o
   * usuario para a tela de licenca.
   */
  public BillingDtos.CurrentSubscriptionResponse obterAssinaturaAtual() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String licenseStatus = licenseStatusService.avaliar(tenantId).planStatus().name();
    Subscription subscription =
        subscriptionRepository.findCurrentByTenantId(tenantId).orElse(null);
    if (subscription == null) {
      LOG.warn(
          CorrelatedLogging.context(
              "Consulta de assinatura sem registro",
              "tenantId", tenantId,
              "traceId", CorrelatedLogging.traceId()));
      CheckoutOrder order =
          checkoutOrderRepository
              .buscarPlanoVigenteMaisRecente(tenantId, Instant.now())
              .orElseThrow(
                  () ->
                      new ApiClientErrorException(
                          "Nenhuma assinatura encontrada para o tenant", 402));
      Product product =
          order.getProductId() != null
              ? productRepository.findById(order.getProductId()).orElse(null)
              : null;
      BillingDtos.CurrentSubscriptionResponse response =
          toCurrentSubscriptionFromCheckoutOrder(order, product);
      response.licenseStatus = licenseStatus;
      return response;
    }

    BillingDtos.CurrentSubscriptionResponse response =
        new BillingDtos.CurrentSubscriptionResponse();
    Product product =
        subscription.getProductId() != null
            ? productRepository.findById(subscription.getProductId()).orElse(null)
            : null;
    Payment currentPayment =
        paymentRepository.findLatestBySubscriptionId(subscription.getId()).orElse(null);
    response.id = subscription.getId() != null ? subscription.getId().toString() : null;
    response.tenantId =
        subscription.getTenantId() != null ? subscription.getTenantId().toString() : null;
    response.customerId = subscription.getAsaasCustomerId();
    response.subscriptionId = subscription.getAsaasSubscriptionId();
    response.productId =
        subscription.getProductId() != null
            ? subscription.getProductId().toString()
            : normalizeProductId(subscription.getPlanCode());
    response.planCode = resolvePlanCode(subscription, product);
    response.billingType = subscription.getBillingType();
    response.status = subscription.getStatus() != null ? subscription.getStatus().name() : null;
    response.cycle = subscription.getCycle();
    response.amount = NumericUtil.fromCents(subscription.getValueCents());
    response.nextDueDate =
        subscription.getNextDueDate() != null ? subscription.getNextDueDate().toString() : null;
    response.currentPaymentId =
        currentPayment != null ? currentPayment.getAsaasPaymentId() : null;
    response.currentPaymentStatus =
        currentPayment != null && currentPayment.getStatus() != null
            ? currentPayment.getStatus().name()
            : null;
    response.currentPaymentDueDate =
        currentPayment != null && currentPayment.getDueDate() != null
            ? currentPayment.getDueDate().toString()
            : null;
    response.licenseStatus = licenseStatus;
    response.paymentLink = subscription.getPaymentLink();
    response.cancelledAt =
        subscription.getCancelledAt() != null ? subscription.getCancelledAt().toString() : null;
    response.createdAt =
        subscription.getCreatedAt() != null ? subscription.getCreatedAt().toString() : null;
    response.updatedAt =
        subscription.getUpdatedAt() != null ? subscription.getUpdatedAt().toString() : null;
    return response;
  }

  @Transactional(readOnly = true)
  public BillingDtos.PaymentsListResponse listarPagamentos() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    BillingDtos.PaymentsListResponse response = new BillingDtos.PaymentsListResponse();
    response.items =
        paymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .map(this::toPaymentItemResponse)
            .toList();
    return response;
  }

  /**
   * Cascata de resolucao do plano: {@code productId}, depois o legado {@code planCode}. Sempre
   * restrito a plano <b>pago e ativo</b> — trial nao pode ser "contratado" por aqui.
   */
  private Product resolvePlanoAtivo(BillingDtos.CreateSubscriptionRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request obrigatorio");
    }

    String rawProductId = request.productId;
    if ((rawProductId == null || rawProductId.isBlank())
        && request.planCode != null
        && !request.planCode.isBlank()) {
      // Compatibilidade temporaria com payload legado.
      rawProductId = request.planCode;
    }

    if (rawProductId == null || rawProductId.isBlank()) {
      throw new IllegalArgumentException("productId obrigatorio");
    }

    Product product = productRepository.findActivePaidByIdentifier(rawProductId).orElse(null);
    if (product == null) {
      throw new IllegalArgumentException(
          "Plano nao encontrado ou inativo (use productId/planCode de plano pago ativo)");
    }

    return product;
  }

  private BillingDtos.PaymentItemResponse toPaymentItemResponse(Payment payment) {
    BillingDtos.PaymentItemResponse item = new BillingDtos.PaymentItemResponse();
    item.id = payment.getId() != null ? payment.getId().toString() : null;
    item.tenantId = payment.getTenantId() != null ? payment.getTenantId().toString() : null;
    item.asaasPaymentId = payment.getAsaasPaymentId();
    item.asaasSubscriptionId = payment.getAsaasSubscriptionId();
    item.billingType = payment.getBillingType();
    item.status = payment.getStatus() != null ? payment.getStatus().name() : null;
    item.amount = NumericUtil.fromCents(payment.getAmountCents());
    item.netAmount =
        payment.getNetAmountCents() != null
            ? NumericUtil.fromCents(payment.getNetAmountCents())
            : null;
    item.dueDate = payment.getDueDate() != null ? payment.getDueDate().toString() : null;
    item.referenceMonth = payment.getReferenceMonth();
    item.paidAt = payment.getPaidAt() != null ? payment.getPaidAt().toString() : null;
    item.expiresAt = payment.getExpiresAt() != null ? payment.getExpiresAt().toString() : null;
    item.invoiceUrl = payment.getInvoiceUrl();
    item.bankSlipUrl = payment.getBankSlipUrl();
    item.boletoIdentificationField = payment.getBoletoIdentificationField();
    item.boletoBarCode = payment.getBoletoBarCode();
    item.boletoNossoNumero = payment.getBoletoNossoNumero();
    item.pixQrCodeBase64 = payment.getPixQrCode();
    item.pixPayload = payment.getPixPayload();
    item.createdAt = payment.getCreatedAt() != null ? payment.getCreatedAt().toString() : null;
    item.updatedAt = payment.getUpdatedAt() != null ? payment.getUpdatedAt().toString() : null;
    return item;
  }

  private String normalizeProductId(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim()).toString();
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private BillingDtos.CurrentSubscriptionResponse toCurrentSubscriptionFromCheckoutOrder(
      CheckoutOrder order, Product product) {
    boolean trial = product != null && product.isTrial();
    BillingDtos.CurrentSubscriptionResponse response =
        new BillingDtos.CurrentSubscriptionResponse();
    response.id = order.getId() != null ? order.getId().toString() : null;
    response.tenantId = order.getTenantId() != null ? order.getTenantId().toString() : null;
    response.customerId = null;
    response.subscriptionId = null;
    response.productId = order.getProductId() != null ? order.getProductId().toString() : null;
    response.planCode =
        product != null && product.getName() != null && !product.getName().isBlank()
            ? product.getName()
            : response.productId;
    response.billingType = trial ? "TRIAL" : "CHECKOUT";
    response.status = trial ? "TRIAL_ACTIVE" : "ACTIVE";
    response.cycle = trial ? "TRIAL" : "ONCE";
    response.amount = NumericUtil.fromCents(order.getTotal());
    response.nextDueDate =
        order.getValidUntil() != null ? order.getValidUntil().toString() : null;
    response.currentPaymentId = null;
    response.currentPaymentStatus = null;
    response.currentPaymentDueDate = null;
    response.paymentLink = null;
    response.cancelledAt = null;
    response.createdAt = order.getCreatedAt() != null ? order.getCreatedAt().toString() : null;
    response.updatedAt = response.createdAt;
    return response;
  }

  private String resolvePlanCode(Subscription subscription, Product product) {
    if (product != null && product.getName() != null && !product.getName().isBlank()) {
      return product.getName();
    }
    return subscription != null ? subscription.getPlanCode() : null;
  }

  // ─── Cancelamento ────────────────────────────────────────────────────────

  /**
   * Cancela no Asaas e no banco. O acesso <b>nao</b> e cortado na hora: o pedido de checkout
   * vigente segue valendo ate o {@code validUntil}, e e por isso que {@code licenseStatusService}
   * e reavaliado logo em seguida (para sincronizar o {@code plan_status_id} do tenant).
   */
  @Transactional
  public BillingDtos.CancelSubscriptionResponse cancelarAssinaturaAtual() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Subscription subscription =
        subscriptionRepository
            .findCurrentByTenantId(tenantId)
            .orElseThrow(
                () -> new ApiClientErrorException("Nenhuma assinatura ativa encontrada.", 404));

    if (StatusSubscription.CANCELLED.equals(subscription.getStatus())) {
      LOG.warn(
          CorrelatedLogging.context(
              "Cancelamento recusado",
              "tenantId", tenantId,
              "subscriptionId", subscription.getAsaasSubscriptionId(),
              "reason", "already_cancelled"));
      throw new ApiClientErrorException("A assinatura ja foi cancelada.", 409);
    }

    asaasService.cancelarAssinaturaAtiva(subscription.getAsaasSubscriptionId());

    subscription.setStatus(StatusSubscription.CANCELLED);
    subscription.setCancelledAt(Instant.now());
    subscriptionRepository.save(subscription);

    licenseStatusService.avaliar(tenantId);

    licenseEventRepository.save(
        LicenseEvent.of(tenantId, "PLAN_CANCELLED", null, subscription.getProductId(), null));

    BillingDtos.CancelSubscriptionResponse response =
        new BillingDtos.CancelSubscriptionResponse();
    response.subscriptionId = subscription.getAsaasSubscriptionId();
    response.status = subscription.getStatus().name();
    response.cancelledAt = subscription.getCancelledAt().toString();
    response.message =
        "Assinatura cancelada. Seu acesso permanece ate o vencimento do periodo atual.";
    LOG.info(
        CorrelatedLogging.context(
            "Cancelamento de assinatura concluido",
            "tenantId", tenantId,
            "subscriptionId", subscription.getAsaasSubscriptionId(),
            "cancelledAt", response.cancelledAt));
    return response;
  }
}
