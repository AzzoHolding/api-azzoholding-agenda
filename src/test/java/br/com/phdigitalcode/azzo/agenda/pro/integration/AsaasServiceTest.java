package br.com.phdigitalcode.azzo.agenda.pro.integration;

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
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutIntent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.IntegrationLog;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Payment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Subscription;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WebhookEventLog;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPayment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusSubscription;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutIntentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.IntegrationLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PaymentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SubscriptionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WebhookEventLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.LicenseStatusService;

/**
 * Cobre o porte de {@code infrastructure/payment/AsaasService.java} — foco no
 * {@code processWebhook}, que e a maquina de estados de pagamento/licenca (o caminho de criacao de
 * assinatura depende de chamadas HTTP reais e esta coberto so nas partes puras).
 */
class AsaasServiceTest {

  private static final String TOKEN = "token-do-webhook";

  private AsaasClient asaasClient;
  private TenantRepository tenantRepository;
  private SubscriptionRepository subscriptionRepository;
  private PaymentRepository paymentRepository;
  private ProductRepository productRepository;
  private CheckoutIntentRepository checkoutIntentRepository;
  private CheckoutOrderRepository checkoutOrderRepository;
  private WebhookEventLogRepository webhookEventLogRepository;
  private IntegrationLogRepository integrationLogRepository;
  private ContextoTenant contextoTenant;
  private LicenseStatusService licenseStatusService;

  private UUID tenantId;
  private Tenant tenant;

  @BeforeEach
  void setUp() {
    asaasClient = mock(AsaasClient.class);
    tenantRepository = mock(TenantRepository.class);
    subscriptionRepository = mock(SubscriptionRepository.class);
    paymentRepository = mock(PaymentRepository.class);
    productRepository = mock(ProductRepository.class);
    checkoutIntentRepository = mock(CheckoutIntentRepository.class);
    checkoutOrderRepository = mock(CheckoutOrderRepository.class);
    webhookEventLogRepository = mock(WebhookEventLogRepository.class);
    integrationLogRepository = mock(IntegrationLogRepository.class);
    contextoTenant = mock(ContextoTenant.class);
    licenseStatusService = mock(LicenseStatusService.class);

    tenantId = UUID.randomUUID();
    tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setAsaasCustomerId("cus_123");

    when(webhookEventLogRepository.existsIdempotencyKey(anyString())).thenCallRealMethod();
    when(webhookEventLogRepository.countByIdempotencyKey(anyString())).thenReturn(0L);
    when(tenantRepository.buscarPorAsaasCustomerId(anyString())).thenCallRealMethod();
    when(tenantRepository.findByAsaasCustomerId("cus_123")).thenReturn(java.util.List.of(tenant));
    when(subscriptionRepository.buscarPorAsaasSubscriptionId(anyString())).thenCallRealMethod();
    when(subscriptionRepository.findByAsaasSubscriptionId(anyString()))
        .thenReturn(java.util.List.of());
    when(paymentRepository.buscarPorAsaasPaymentId(anyString())).thenCallRealMethod();
    when(paymentRepository.findByAsaasPaymentId(anyString())).thenReturn(java.util.List.of());
    when(checkoutIntentRepository.buscarPorReferenciaPagamento(any(), anyString()))
        .thenCallRealMethod();
    when(checkoutIntentRepository.findByTenantIdAndPaymentReference(any(), anyString()))
        .thenReturn(java.util.List.of());
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(any(), any()))
        .thenReturn(Optional.empty());
    when(paymentRepository.saveAndFlush(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(checkoutIntentRepository.saveAndFlush(any(CheckoutIntent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(checkoutOrderRepository.saveAndFlush(any(CheckoutOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private AsaasService service(boolean enabled, String configuredToken) {
    return new AsaasService(
        asaasClient,
        new ObjectMapper(),
        tenantRepository,
        subscriptionRepository,
        paymentRepository,
        productRepository,
        checkoutIntentRepository,
        checkoutOrderRepository,
        webhookEventLogRepository,
        integrationLogRepository,
        contextoTenant,
        licenseStatusService,
        "api-key-da-plataforma",
        configuredToken,
        enabled);
  }

  private AsaasService service() {
    return service(true, TOKEN);
  }

  private AsaasDtos.WebhookPayload payload(String event, String status, String subscriptionId) {
    AsaasDtos.WebhookPayload payload = new AsaasDtos.WebhookPayload();
    payload.event = event;
    payload.payment = new AsaasDtos.WebhookPayment();
    payload.payment.id = "pay_1";
    payload.payment.customer = "cus_123";
    payload.payment.subscription = subscriptionId;
    payload.payment.billingType = "PIX";
    payload.payment.status = status;
    payload.payment.value = new BigDecimal("199.90");
    payload.payment.dueDate = "2026-03-10";
    return payload;
  }

  // ---------------------------------------------------------------- autenticacao do webhook

  @Test
  @DisplayName("token divergente responde 401 e nao grava nada")
  void tokenInvalidoRejeita() {
    assertThatThrownBy(() -> service().processWebhook("outro-token", payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Token de webhook invalido");

    verify(webhookEventLogRepository, never()).save(any());
    verify(integrationLogRepository, never()).save(any());
  }

  @Test
  @DisplayName("token nao configurado (__unset__) responde 401 mesmo com o header vindo igual")
  void tokenNaoConfiguradoRejeita() {
    assertThatThrownBy(() -> service(true, "__unset__").processWebhook("__unset__", payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("ASAAS_WEBHOOK_TOKEN nao configurado");
  }

  @Test
  @DisplayName("token e comparado com trim(), como no original")
  void tokenComEspacosPassa() {
    service().processWebhook("  " + TOKEN + "  ", payload("PAYMENT_OVERDUE", "OVERDUE", "sub_1"));

    verify(webhookEventLogRepository).save(any(WebhookEventLog.class));
  }

  @Test
  @DisplayName("a validacao do token acontece ANTES do gate de integracao desabilitada")
  void tokenValidadoAntesDoGateDesabilitado() {
    assertThatThrownBy(() -> service(false, TOKEN).processWebhook("errado", payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1")))
        .isInstanceOf(ApiClientErrorException.class);
  }

  // ---------------------------------------------------------------- filtros de entrada

  @Test
  @DisplayName("integracao desabilitada ignora o webhook depois de validar o token")
  void integracaoDesabilitadaIgnora() {
    service(false, TOKEN).processWebhook(TOKEN, payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1"));

    verify(webhookEventLogRepository, never()).save(any());
  }

  @Test
  @DisplayName("evento fora da lista suportada e ignorado sem gravar log de evento")
  void eventoNaoSuportadoIgnorado() {
    service().processWebhook(TOKEN, payload("PAYMENT_CREATED", "PENDING", "sub_1"));

    verify(webhookEventLogRepository, never()).save(any());
    verify(integrationLogRepository, never()).save(any());
  }

  @Test
  @DisplayName("payload sem event e recusado")
  void payloadSemEventoRecusado() {
    AsaasDtos.WebhookPayload payload = new AsaasDtos.WebhookPayload();
    payload.event = "  ";

    assertThatThrownBy(() -> service().processWebhook(TOKEN, payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Webhook Asaas invalido");
  }

  @Test
  @DisplayName("evento repetido (mesma idempotencyKey) e descartado sem reprocessar")
  void eventoDuplicadoDescartado() {
    when(webhookEventLogRepository.countByIdempotencyKey(anyString())).thenReturn(1L);

    service().processWebhook(TOKEN, payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1"));

    verify(webhookEventLogRepository, never()).save(any());
    verify(paymentRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("pagamento sem tenant identificavel falha em vez de gravar orfao")
  void tenantNaoIdentificadoFalha() {
    when(tenantRepository.findByAsaasCustomerId(anyString())).thenReturn(java.util.List.of());

    assertThatThrownBy(() -> service().processWebhook(TOKEN, payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Nao foi possivel identificar tenant para o webhook Asaas");

    verify(webhookEventLogRepository, never()).save(any());
  }

  @Test
  @DisplayName("tenant e resolvido pelo externalReference quando o customer nao bate")
  void tenantResolvidoPeloExternalReference() {
    when(tenantRepository.findByAsaasCustomerId(anyString())).thenReturn(java.util.List.of());
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

    AsaasDtos.WebhookPayload payload = payload("PAYMENT_OVERDUE", "OVERDUE", "sub_1");
    payload.payment.externalReference = "t:" + tenantId.toString().replace("-", "") + "|p:none|b:P";

    service().processWebhook(TOKEN, payload);

    ArgumentCaptor<WebhookEventLog> captor = ArgumentCaptor.forClass(WebhookEventLog.class);
    verify(webhookEventLogRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
  }

  // ---------------------------------------------------------------- efeitos por evento

  @Test
  @DisplayName("PAYMENT_CONFIRMED ativa a assinatura e usa o status enviado pela Asaas")
  void pagamentoConfirmadoAtivaAssinatura() {
    Subscription subscription = new Subscription();
    subscription.setId(UUID.randomUUID());
    subscription.setTenantId(tenantId);
    subscription.setStatus(StatusSubscription.PENDING);
    when(subscriptionRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(java.util.List.of(subscription));
    when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

    service().processWebhook(TOKEN, payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1"));

    assertThat(subscription.getStatus()).isEqualTo(StatusSubscription.ACTIVE);
    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).saveAndFlush(captor.capture());
    // O status vem do payload; RECEIVED e apenas o fallback (ver teste abaixo).
    // Comportamento identico ao original: toPaymentStatus(payload.status, RECEIVED).
    assertThat(captor.getValue().getStatus()).isEqualTo(StatusPayment.CONFIRMED);
    assertThat(captor.getValue().getAmountCents()).isEqualTo(19_990L);
    assertThat(captor.getValue().getReferenceMonth()).isEqualTo("2026-03");
  }

  @Test
  @DisplayName("PAYMENT_CONFIRMED sem status no payload cai no fallback RECEIVED")
  void pagamentoConfirmadoSemStatusUsaFallbackRecebido() {
    Subscription subscription = new Subscription();
    subscription.setId(UUID.randomUUID());
    subscription.setTenantId(tenantId);
    subscription.setStatus(StatusSubscription.PENDING);
    when(subscriptionRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(java.util.List.of(subscription));
    when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

    AsaasDtos.WebhookPayload payload = payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1");
    payload.payment.status = null;

    service().processWebhook(TOKEN, payload);

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(StatusPayment.RECEIVED);
  }

  @Test
  @DisplayName("PAYMENT_OVERDUE marca assinatura e pagamento como atrasados")
  void pagamentoVencidoMarcaAtraso() {
    Subscription subscription = new Subscription();
    subscription.setId(UUID.randomUUID());
    subscription.setStatus(StatusSubscription.ACTIVE);
    when(subscriptionRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(java.util.List.of(subscription));

    service().processWebhook(TOKEN, payload("PAYMENT_OVERDUE", "OVERDUE", "sub_1"));

    assertThat(subscription.getStatus()).isEqualTo(StatusSubscription.OVERDUE);
    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(StatusPayment.OVERDUE);
    verify(licenseStatusService, never()).avaliar(any());
  }

  @Test
  @DisplayName("SUBSCRIPTION_CANCELLED cancela a assinatura e carimba cancelledAt")
  void assinaturaCancelada() {
    Subscription subscription = new Subscription();
    subscription.setStatus(StatusSubscription.ACTIVE);
    when(subscriptionRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(java.util.List.of(subscription));

    AsaasDtos.WebhookPayload payload = new AsaasDtos.WebhookPayload();
    payload.event = "SUBSCRIPTION_CANCELLED";
    payload.subscription = new AsaasDtos.WebhookSubscription();
    payload.subscription.id = "sub_1";
    payload.subscription.customer = "cus_123";

    service().processWebhook(TOKEN, payload);

    assertThat(subscription.getStatus()).isEqualTo(StatusSubscription.CANCELLED);
    assertThat(subscription.getCancelledAt()).isNotNull();
  }

  // ---------------------------------------------------------------- licenca por pagamento

  private Subscription assinaturaComProduto(Product product) {
    Subscription subscription = new Subscription();
    subscription.setId(UUID.randomUUID());
    subscription.setTenantId(tenantId);
    subscription.setProductId(product.getId());
    subscription.setStatus(StatusSubscription.PENDING);
    when(subscriptionRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(java.util.List.of(subscription));
    when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
    return subscription;
  }

  private Product produto(Integer validityDays, int validityMonths) {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setName("Plano Pro");
    product.setCurrency("BRL");
    product.setValidityDays(validityDays);
    product.setValidityMonths(validityMonths);
    return product;
  }

  @Test
  @DisplayName("pagamento confirmado gera intent + order CONFIRMED e reavalia a licenca")
  void pagamentoConfirmadoGeraLicenca() {
    Product product = produto(null, 1);
    assinaturaComProduto(product);

    AsaasDtos.WebhookPayload payload = payload("PAYMENT_RECEIVED", "RECEIVED", "sub_1");
    payload.payment.paymentDate = "2026-03-10T12:00:00Z";

    service().processWebhook(TOKEN, payload);

    ArgumentCaptor<CheckoutIntent> intentCaptor = ArgumentCaptor.forClass(CheckoutIntent.class);
    verify(checkoutIntentRepository).saveAndFlush(intentCaptor.capture());
    CheckoutIntent intent = intentCaptor.getValue();
    assertThat(intent.getStatus()).isEqualTo(StatusCheckout.CONFIRMED);
    assertThat(intent.getPaymentReference()).isEqualTo("asaas-payment-pay_1");
    assertThat(intent.getProductNameSnapshot()).isEqualTo("Plano Pro");
    assertThat(intent.getQuantity()).isEqualTo(1);
    assertThat(intent.getCalculatedTotal()).isEqualByComparingTo("199.90");

    ArgumentCaptor<CheckoutOrder> orderCaptor = ArgumentCaptor.forClass(CheckoutOrder.class);
    verify(checkoutOrderRepository).saveAndFlush(orderCaptor.capture());
    CheckoutOrder order = orderCaptor.getValue();
    // order.total e long em CENTAVOS aqui (diferente do fluxo de checkout, onde recebe reais*100).
    assertThat(order.getTotal()).isEqualTo(19_990L);
    assertThat(order.getStatus()).isEqualTo(StatusCheckout.CONFIRMED);
    // validityDays nulo -> validityMonths * 30 = 30 dias a partir do pagamento.
    assertThat(order.getValidUntil())
        .isEqualTo(Instant.parse("2026-03-10T12:00:00Z").plus(30, ChronoUnit.DAYS));

    verify(licenseStatusService).avaliar(tenantId);
  }

  @Test
  @DisplayName("validityDays explicito vence validityMonths * 30")
  void validityDaysVenceValidityMonths() {
    Product product = produto(7, 12);
    assinaturaComProduto(product);

    AsaasDtos.WebhookPayload payload = payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1");
    payload.payment.paymentDate = "2026-03-10T12:00:00Z";

    service().processWebhook(TOKEN, payload);

    ArgumentCaptor<CheckoutOrder> orderCaptor = ArgumentCaptor.forClass(CheckoutOrder.class);
    verify(checkoutOrderRepository).saveAndFlush(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getValidUntil())
        .isEqualTo(Instant.parse("2026-03-10T12:00:00Z").plus(7, ChronoUnit.DAYS));
  }

  @Test
  @DisplayName("renovacao acumula: a nova validade parte do fim do plano vigente, nao do pagamento")
  void renovacaoAcumulaSaldoRestante() {
    Product product = produto(30, 1);
    assinaturaComProduto(product);

    Instant fimDoPlanoAtual = Instant.parse("2026-04-01T00:00:00Z");
    CheckoutOrder vigente = new CheckoutOrder();
    vigente.setValidUntil(fimDoPlanoAtual);
    when(checkoutOrderRepository.buscarPlanoVigenteMaisRecente(eq(tenantId), any()))
        .thenReturn(Optional.of(vigente));

    AsaasDtos.WebhookPayload payload = payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1");
    payload.payment.paymentDate = "2026-03-10T12:00:00Z";

    service().processWebhook(TOKEN, payload);

    ArgumentCaptor<CheckoutOrder> orderCaptor = ArgumentCaptor.forClass(CheckoutOrder.class);
    verify(checkoutOrderRepository).saveAndFlush(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getValidUntil())
        .isEqualTo(fimDoPlanoAtual.plus(30, ChronoUnit.DAYS));
  }

  @Test
  @DisplayName("licenca ja registrada para o mesmo pagamento nao e duplicada")
  void licencaNaoDuplicaParaMesmoPagamento() {
    Product product = produto(30, 1);
    assinaturaComProduto(product);
    when(checkoutIntentRepository.findByTenantIdAndPaymentReference(tenantId, "asaas-payment-pay_1"))
        .thenReturn(java.util.List.of(new CheckoutIntent()));

    service().processWebhook(TOKEN, payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1"));

    verify(checkoutIntentRepository, never()).saveAndFlush(any());
    verify(checkoutOrderRepository, never()).saveAndFlush(any());
    verify(licenseStatusService, never()).avaliar(any());
  }

  @Test
  @DisplayName("sem productId identificavel a licenca nao e gerada, mas o pagamento e gravado")
  void semProdutoNaoGeraLicenca() {
    Subscription subscription = new Subscription();
    subscription.setId(UUID.randomUUID());
    subscription.setTenantId(tenantId);
    when(subscriptionRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(java.util.List.of(subscription));
    when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

    service().processWebhook(TOKEN, payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1"));

    verify(paymentRepository).saveAndFlush(any(Payment.class));
    verify(checkoutOrderRepository, never()).saveAndFlush(any());
    verify(licenseStatusService, never()).avaliar(any());
  }

  @Test
  @DisplayName("productId vem do externalReference quando a assinatura nao aponta produto")
  void produtoVemDoExternalReference() {
    Product product = produto(30, 1);
    Subscription subscription = new Subscription();
    subscription.setId(UUID.randomUUID());
    subscription.setTenantId(tenantId);
    when(subscriptionRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(java.util.List.of(subscription));
    when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

    AsaasDtos.WebhookPayload payload = payload("PAYMENT_CONFIRMED", "CONFIRMED", "sub_1");
    payload.payment.externalReference =
        "t:" + tenantId.toString().replace("-", "")
            + "|p:" + product.getId().toString().replace("-", "")
            + "|b:P|x:abc";

    service().processWebhook(TOKEN, payload);

    verify(checkoutOrderRepository).saveAndFlush(any(CheckoutOrder.class));
    verify(licenseStatusService).avaliar(tenantId);
  }

  // ---------------------------------------------------------------- externalReference

  @Test
  @DisplayName("buildExternalReference usa UUID compactado e uma letra por billingType")
  void externalReferenceFormatado() {
    UUID productId = UUID.randomUUID();
    String reference = service().buildExternalReference(tenantId, productId.toString(), "BOLETO");

    assertThat(reference).startsWith("t:" + tenantId.toString().replace("-", ""));
    assertThat(reference).contains("|p:" + productId.toString().replace("-", ""));
    assertThat(reference).contains("|b:B|x:");
  }

  @Test
  @DisplayName("billingType desconhecido vira 'X' e produto ausente vira 'none'")
  void externalReferenceComFallbacks() {
    String reference = service().buildExternalReference(tenantId, null, "TRANSFERENCIA");

    assertThat(reference).contains("|p:none");
    assertThat(reference).contains("|b:X");
  }

  // ---------------------------------------------------------------- log de integracao

  @Test
  @DisplayName("todo webhook processado grava um IntegrationLog INBOUND")
  void gravaIntegrationLogDoWebhook() {
    service().processWebhook(TOKEN, payload("PAYMENT_OVERDUE", "OVERDUE", "sub_1"));

    ArgumentCaptor<IntegrationLog> captor = ArgumentCaptor.forClass(IntegrationLog.class);
    verify(integrationLogRepository).save(captor.capture());
    IntegrationLog log = captor.getValue();
    assertThat(log.getProvider()).isEqualTo("ASAAS");
    assertThat(log.getDirection()).isEqualTo("INBOUND");
    assertThat(log.getAction()).isEqualTo("ASAAS_WEBHOOK");
    assertThat(log.getTenantId()).isEqualTo(tenantId);
    assertThat(log.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("cancelamento no Asaas fora do ar nao propaga excecao (cancelamento local prossegue)")
  void cancelamentoToleraAsaasForaDoAr() {
    org.mockito.Mockito.doThrow(new RuntimeException("timeout"))
        .when(asaasClient)
        .cancelSubscription(anyString(), eq("sub_1"));

    service().cancelarAssinaturaAtiva("sub_1");

    verify(asaasClient).cancelSubscription(anyString(), eq("sub_1"));
  }

  @Test
  @DisplayName("com Asaas desabilitado o cancelamento nem chama a API")
  void cancelamentoDesabilitadoNaoChamaApi() {
    service(false, TOKEN).cancelarAssinaturaAtiva("sub_1");

    verify(asaasClient, never()).cancelSubscription(anyString(), anyString());
  }

  @Test
  @DisplayName("dueDate alimenta expiresAt no fuso America/Sao_Paulo")
  void expiresAtNoFusoBrasileiro() {
    service().processWebhook(TOKEN, payload("PAYMENT_OVERDUE", "OVERDUE", "sub_1"));

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getExpiresAt())
        .isEqualTo(
            LocalDate.of(2026, 3, 10)
                .atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo"))
                .toInstant());
  }
}
