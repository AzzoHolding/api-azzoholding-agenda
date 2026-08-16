package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LicenseEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Payment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Subscription;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PlanStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPayment;
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

/**
 * Cobre {@code modules/billing/application/ServicoBilling.java}: normalizacao do pedido de
 * assinatura, janela de troca de plano, inicio agendado, consulta da assinatura vigente (com
 * fallback para o pedido de checkout) e cancelamento.
 */
class ServicoBillingTest {

  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private BillingPaymentHandler pixHandler;
  private PaymentRepository paymentRepository;
  private ProductRepository productRepository;
  private CheckoutOrderRepository checkoutOrderRepository;
  private SubscriptionRepository subscriptionRepository;
  private LicenseStatusService licenseStatusService;
  private ContextoTenant contextoTenant;
  private AsaasPendingPaymentCleanupService pendingPaymentCleanupService;
  private AsaasService asaasService;
  private LicenseEventRepository licenseEventRepository;
  private ServicoBilling service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    pixHandler = mock(BillingPaymentHandler.class);
    paymentRepository = mock(PaymentRepository.class);
    productRepository = mock(ProductRepository.class);
    checkoutOrderRepository = mock(CheckoutOrderRepository.class);
    subscriptionRepository = mock(SubscriptionRepository.class);
    licenseStatusService = mock(LicenseStatusService.class);
    contextoTenant = mock(ContextoTenant.class);
    pendingPaymentCleanupService = mock(AsaasPendingPaymentCleanupService.class);
    asaasService = mock(AsaasService.class);
    licenseEventRepository = mock(LicenseEventRepository.class);

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(licenseStatusService.avaliar(any()))
        .thenReturn(new LicenseStatusService.LicenseStatus(PlanStatus.ACTIVE, true));
    when(pixHandler.supports("PIX")).thenReturn(true);
    when(pixHandler.process(any(), any())).thenReturn(new BillingDtos.SubscriptionResponse());
    // Metodos default: precisam de stub explicito.
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(any(), any()))
        .thenReturn(Optional.empty());
    when(subscriptionRepository.findCurrentByTenantId(any())).thenReturn(Optional.empty());
    when(paymentRepository.findLatestBySubscriptionId(any())).thenReturn(Optional.empty());
    when(productRepository.findActivePaidByIdentifier(anyString()))
        .thenReturn(Optional.of(plano()));

    service =
        new ServicoBilling(
            List.of(pixHandler),
            paymentRepository,
            productRepository,
            checkoutOrderRepository,
            subscriptionRepository,
            licenseStatusService,
            contextoTenant,
            pendingPaymentCleanupService,
            asaasService,
            licenseEventRepository);
  }

  // ─── criarAssinatura ──────────────────────────────────────────────────────

  @Test
  void requestNuloFalha() {
    assertThatThrownBy(() -> service.criarAssinatura(null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("request obrigatorio");
  }

  @Test
  void semProductIdNemPlanCodeFalha() {
    BillingDtos.CreateSubscriptionRequest request = pedidoPix();
    request.productId = null;
    request.planCode = null;

    assertThatThrownBy(() -> service.criarAssinatura(request, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("productId obrigatorio");
  }

  @Test
  void planCodeLegadoAindaResolveOPlano() {
    BillingDtos.CreateSubscriptionRequest request = pedidoPix();
    request.productId = "   ";
    request.planCode = "pro";

    service.criarAssinatura(request, null);

    verify(productRepository).findActivePaidByIdentifier("pro");
  }

  @Test
  void planoInexistenteOuInativoFalha() {
    when(productRepository.findActivePaidByIdentifier(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.criarAssinatura(pedidoPix(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Plano nao encontrado ou inativo");
  }

  @Test
  void valorEnviadoPeloClienteEhIgnoradoEmFavorDoPrecoDoPlano() {
    BillingDtos.CreateSubscriptionRequest request = pedidoPix();
    request.amount = new BigDecimal("0.01");
    request.amountCents = 1L;

    service.criarAssinatura(request, "203.0.113.9");

    BillingDtos.CreateSubscriptionRequest normalizado = capturarRequestNormalizado();
    assertThat(normalizado.amount).isEqualByComparingTo(new BigDecimal("199.90"));
    assertThat(normalizado.amountCents).isEqualTo(19990L);
    assertThat(normalizado.productId).isEqualTo(productId.toString());
    assertThat(normalizado.planCode).isEqualTo(productId.toString());
    assertThat(normalizado.billingType).isEqualTo("PIX");
  }

  @Test
  void formaDePagamentoSemHandlerFalha() {
    BillingDtos.CreateSubscriptionRequest request = pedidoPix();
    request.billingType = "BOLETO";

    assertThatThrownBy(() -> service.criarAssinatura(request, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Forma de pagamento nao suportada");
  }

  @Test
  void pendenciasDoTenantSaoFechadasAntesDeCobrar() {
    service.criarAssinatura(pedidoPix(), null);

    verify(pendingPaymentCleanupService).closePendingPaymentsForTenant(tenantId);
  }

  @Test
  void falhaDoHandlerEhRepropagada() {
    when(pixHandler.process(any(), any())).thenThrow(new IllegalStateException("asaas fora"));

    assertThatThrownBy(() -> service.criarAssinatura(pedidoPix(), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("asaas fora");
  }

  // ─── janela de troca de plano ─────────────────────────────────────────────

  @Test
  void trocaDePlanoBloqueadaForaDaJanelaDeDezDias() {
    CheckoutOrder vigente = pedidoVigente(Instant.now().plus(30, ChronoUnit.DAYS));
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(eq(tenantId), any()))
        .thenReturn(Optional.of(vigente));
    when(productRepository.findById(productId)).thenReturn(Optional.of(plano()));

    assertThatThrownBy(() -> service.criarAssinatura(pedidoPix(), null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("faltarem 10 dias ou menos")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(422);
    verify(pixHandler, never()).process(any(), any());
  }

  @Test
  void trocaDePlanoLiberadaDentroDaJanela() {
    CheckoutOrder vigente = pedidoVigente(Instant.now().plus(5, ChronoUnit.DAYS));
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(eq(tenantId), any()))
        .thenReturn(Optional.of(vigente));
    when(productRepository.findById(productId)).thenReturn(Optional.of(plano()));

    service.criarAssinatura(pedidoPix(), null);

    verify(pixHandler).process(any(), any());
  }

  @Test
  void trialLiberaTrocaEmQualquerMomento() {
    CheckoutOrder vigente = pedidoVigente(Instant.now().plus(300, ChronoUnit.DAYS));
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(eq(tenantId), any()))
        .thenReturn(Optional.of(vigente));
    Product trial = plano();
    trial.setTrial(true);
    when(productRepository.findById(productId)).thenReturn(Optional.of(trial));

    service.criarAssinatura(pedidoPix(), null);

    verify(pixHandler).process(any(), any());
  }

  @Test
  void pedidoVigenteSemValidUntilBloqueiaATroca() {
    CheckoutOrder vigente = pedidoVigente(null);
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(eq(tenantId), any()))
        .thenReturn(Optional.of(vigente));
    when(productRepository.findById(productId)).thenReturn(Optional.of(plano()));

    assertThatThrownBy(() -> service.criarAssinatura(pedidoPix(), null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("trial ou nos ultimos 10 dias");
  }

  @Test
  void novoPlanoComecaQuandoOAtualVence() {
    Instant validUntil = Instant.now().plus(5, ChronoUnit.DAYS);
    CheckoutOrder vigente = pedidoVigente(validUntil);
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(eq(tenantId), any()))
        .thenReturn(Optional.of(vigente));
    when(productRepository.findById(productId)).thenReturn(Optional.of(plano()));

    service.criarAssinatura(pedidoPix(), null);

    assertThat(capturarRequestNormalizado().nextDueDate)
        .isEqualTo(validUntil.atZone(ZONE_BR).toLocalDate().toString());
  }

  @Test
  void nextDueDateInformadoTemPrecedenciaSobreOAgendamento() {
    Instant validUntil = Instant.now().plus(5, ChronoUnit.DAYS);
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(eq(tenantId), any()))
        .thenReturn(Optional.of(pedidoVigente(validUntil)));
    when(productRepository.findById(productId)).thenReturn(Optional.of(plano()));

    BillingDtos.CreateSubscriptionRequest request = pedidoPix();
    request.nextDueDate = "2030-01-01";

    service.criarAssinatura(request, null);

    assertThat(capturarRequestNormalizado().nextDueDate).isEqualTo("2030-01-01");
  }

  @Test
  void semPlanoVigenteNaoHaDataAgendada() {
    service.criarAssinatura(pedidoPix(), null);

    assertThat(capturarRequestNormalizado().nextDueDate).isNull();
  }

  // ─── obterAssinaturaAtual ─────────────────────────────────────────────────

  @Test
  void semAssinaturaESemPedidoVigenteDevolve402() {
    assertThatThrownBy(() -> service.obterAssinaturaAtual())
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Nenhuma assinatura encontrada para o tenant")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(402);
  }

  @Test
  void semAssinaturaCaiNoPedidoDeCheckoutTrial() {
    CheckoutOrder order = pedidoVigente(Instant.parse("2026-06-01T00:00:00Z"));
    order.setTotal(0L);
    order.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(eq(tenantId), any()))
        .thenReturn(Optional.of(order));
    Product trial = plano();
    trial.setTrial(true);
    when(productRepository.findById(productId)).thenReturn(Optional.of(trial));

    BillingDtos.CurrentSubscriptionResponse response = service.obterAssinaturaAtual();

    assertThat(response.billingType).isEqualTo("TRIAL");
    assertThat(response.status).isEqualTo("TRIAL_ACTIVE");
    assertThat(response.cycle).isEqualTo("TRIAL");
    assertThat(response.planCode).isEqualTo("Plano Pro");
    assertThat(response.licenseStatus).isEqualTo("ACTIVE");
    assertThat(response.nextDueDate).isEqualTo("2026-06-01T00:00:00Z");
    assertThat(response.updatedAt).isEqualTo(response.createdAt);
    assertThat(response.subscriptionId).isNull();
  }

  @Test
  void pedidoDeCheckoutPagoNaoEhTrial() {
    CheckoutOrder order = pedidoVigente(Instant.parse("2026-06-01T00:00:00Z"));
    order.setTotal(19990L);
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(eq(tenantId), any()))
        .thenReturn(Optional.of(order));
    when(productRepository.findById(productId)).thenReturn(Optional.of(plano()));

    BillingDtos.CurrentSubscriptionResponse response = service.obterAssinaturaAtual();

    assertThat(response.billingType).isEqualTo("CHECKOUT");
    assertThat(response.status).isEqualTo("ACTIVE");
    assertThat(response.cycle).isEqualTo("ONCE");
    assertThat(response.amount).isEqualByComparingTo(new BigDecimal("199.90"));
  }

  @Test
  void assinaturaVigenteTrazUltimoPagamento() {
    Subscription subscription = assinatura(StatusSubscription.ACTIVE);
    when(subscriptionRepository.findCurrentByTenantId(tenantId))
        .thenReturn(Optional.of(subscription));
    when(productRepository.findById(productId)).thenReturn(Optional.of(plano()));

    Payment payment = new Payment();
    payment.setAsaasPaymentId("pay_1");
    payment.setStatus(StatusPayment.RECEIVED);
    payment.setDueDate(LocalDate.parse("2026-04-10"));
    when(paymentRepository.findLatestBySubscriptionId(subscription.getId()))
        .thenReturn(Optional.of(payment));

    BillingDtos.CurrentSubscriptionResponse response = service.obterAssinaturaAtual();

    assertThat(response.subscriptionId).isEqualTo("sub_1");
    assertThat(response.status).isEqualTo("ACTIVE");
    assertThat(response.planCode).isEqualTo("Plano Pro");
    assertThat(response.amount).isEqualByComparingTo(new BigDecimal("199.90"));
    assertThat(response.currentPaymentId).isEqualTo("pay_1");
    assertThat(response.currentPaymentStatus).isEqualTo("RECEIVED");
    assertThat(response.currentPaymentDueDate).isEqualTo("2026-04-10");
    assertThat(response.licenseStatus).isEqualTo("ACTIVE");
  }

  @Test
  void semProdutoOPlanCodeDaAssinaturaEhUsado() {
    Subscription subscription = assinatura(StatusSubscription.ACTIVE);
    subscription.setProductId(null);
    subscription.setPlanCode("legado-pro");
    when(subscriptionRepository.findCurrentByTenantId(tenantId))
        .thenReturn(Optional.of(subscription));

    BillingDtos.CurrentSubscriptionResponse response = service.obterAssinaturaAtual();

    assertThat(response.planCode).isEqualTo("legado-pro");
    // planCode nao-UUID nao vira productId.
    assertThat(response.productId).isNull();
  }

  @Test
  void planCodeComUuidViraProductIdNormalizado() {
    Subscription subscription = assinatura(StatusSubscription.ACTIVE);
    subscription.setProductId(null);
    subscription.setPlanCode("  " + productId + "  ");
    when(subscriptionRepository.findCurrentByTenantId(tenantId))
        .thenReturn(Optional.of(subscription));

    assertThat(service.obterAssinaturaAtual().productId).isEqualTo(productId.toString());
  }

  // ─── listarPagamentos ─────────────────────────────────────────────────────

  @Test
  void listaPagamentosDoTenantEmReais() {
    Payment payment = new Payment();
    payment.setTenantId(tenantId);
    payment.setAmountCents(5000L);
    payment.setPixQrCode("qr");
    payment.setPixPayload("payload");
    when(paymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
        .thenReturn(List.of(payment));

    BillingDtos.PaymentsListResponse response = service.listarPagamentos();

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).amount).isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(response.items.get(0).pixQrCodeBase64).isEqualTo("qr");
    assertThat(response.items.get(0).pixPayload).isEqualTo("payload");
  }

  // ─── cancelarAssinaturaAtual ──────────────────────────────────────────────

  @Test
  void cancelarSemAssinaturaDevolve404() {
    assertThatThrownBy(() -> service.cancelarAssinaturaAtual())
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Nenhuma assinatura ativa encontrada.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void cancelarAssinaturaJaCanceladaDevolve409() {
    when(subscriptionRepository.findCurrentByTenantId(tenantId))
        .thenReturn(Optional.of(assinatura(StatusSubscription.CANCELLED)));

    assertThatThrownBy(() -> service.cancelarAssinaturaAtual())
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("A assinatura ja foi cancelada.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(409);
    verify(asaasService, never()).cancelarAssinaturaAtiva(any());
  }

  @Test
  void cancelamentoCancelaNoAsaasRegistraEventoEMantemAcesso() {
    Subscription subscription = assinatura(StatusSubscription.ACTIVE);
    when(subscriptionRepository.findCurrentByTenantId(tenantId))
        .thenReturn(Optional.of(subscription));

    BillingDtos.CancelSubscriptionResponse response = service.cancelarAssinaturaAtual();

    verify(asaasService).cancelarAssinaturaAtiva("sub_1");
    verify(subscriptionRepository).save(subscription);
    verify(licenseStatusService).avaliar(tenantId);
    assertThat(subscription.getStatus()).isEqualTo(StatusSubscription.CANCELLED);
    assertThat(subscription.getCancelledAt()).isNotNull();
    assertThat(response.subscriptionId).isEqualTo("sub_1");
    assertThat(response.status).isEqualTo("CANCELLED");
    assertThat(response.message).contains("acesso permanece ate o vencimento");

    ArgumentCaptor<LicenseEvent> captor = ArgumentCaptor.forClass(LicenseEvent.class);
    verify(licenseEventRepository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("PLAN_CANCELLED");
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(captor.getValue().getProductId()).isEqualTo(productId);
    assertThat(captor.getValue().getValidUntil()).isNull();
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private BillingDtos.CreateSubscriptionRequest pedidoPix() {
    BillingDtos.CreateSubscriptionRequest request = new BillingDtos.CreateSubscriptionRequest();
    request.productId = productId.toString();
    request.billingType = "PIX";
    request.cpfCnpj = "12345678909";
    request.description = "Plano mensal";
    return request;
  }

  private Product plano() {
    Product product = new Product();
    product.setId(productId);
    product.setName("Plano Pro");
    product.setCurrency("BRL");
    product.setPriceCents(new BigDecimal("199.90"));
    product.setValidityMonths(1);
    product.setTrial(false);
    return product;
  }

  private CheckoutOrder pedidoVigente(Instant validUntil) {
    CheckoutOrder order = new CheckoutOrder();
    order.setId(UUID.randomUUID());
    order.setTenantId(tenantId);
    order.setProductId(productId);
    order.setValidUntil(validUntil);
    order.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return order;
  }

  private Subscription assinatura(StatusSubscription status) {
    Subscription subscription = new Subscription();
    subscription.setId(UUID.randomUUID());
    subscription.setTenantId(tenantId);
    subscription.setProductId(productId);
    subscription.setAsaasSubscriptionId("sub_1");
    subscription.setAsaasCustomerId("cus_1");
    subscription.setBillingType("CREDIT_CARD");
    subscription.setStatus(status);
    subscription.setValueCents(19990L);
    subscription.setCycle("MONTHLY");
    return subscription;
  }

  private BillingDtos.CreateSubscriptionRequest capturarRequestNormalizado() {
    ArgumentCaptor<BillingDtos.CreateSubscriptionRequest> captor =
        ArgumentCaptor.forClass(BillingDtos.CreateSubscriptionRequest.class);
    verify(pixHandler).process(captor.capture(), any());
    return captor.getValue();
  }
}
