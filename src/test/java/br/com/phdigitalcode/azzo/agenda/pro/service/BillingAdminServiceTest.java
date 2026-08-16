package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutIntent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LicenseEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Payment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Subscription;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PlanStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPayment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusSubscription;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutIntentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LicenseEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PaymentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SubscriptionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Cobre {@code modules/billing/application/BillingAdminService.java}: resolucao do tenant alvo,
 * forcar vencimento, liberar licenca, marcar pagamento recebido, estender trial, historico e os
 * dois relatorios.
 */
class BillingAdminServiceTest {

  private ContextoTenant contextoTenant;
  private CheckoutOrderRepository checkoutOrderRepository;
  private CheckoutIntentRepository checkoutIntentRepository;
  private SubscriptionRepository subscriptionRepository;
  private PaymentRepository paymentRepository;
  private ProductRepository productRepository;
  private TenantRepository tenantRepository;
  private LicenseStatusService licenseStatusService;
  private LicenseEventRepository licenseEventRepository;
  private EntityManager entityManager;
  private BillingAdminService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    contextoTenant = mock(ContextoTenant.class);
    checkoutOrderRepository = mock(CheckoutOrderRepository.class);
    checkoutIntentRepository = mock(CheckoutIntentRepository.class);
    subscriptionRepository = mock(SubscriptionRepository.class);
    paymentRepository = mock(PaymentRepository.class);
    productRepository = mock(ProductRepository.class);
    tenantRepository = mock(TenantRepository.class);
    licenseStatusService = mock(LicenseStatusService.class);
    licenseEventRepository = mock(LicenseEventRepository.class);
    entityManager = mock(EntityManager.class);

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(licenseStatusService.avaliar(any()))
        .thenReturn(new LicenseStatusService.LicenseStatus(PlanStatus.ACTIVE, true));
    // Metodos default de interface precisam ser stubados explicitamente (nao sao interceptados).
    when(subscriptionRepository.findCurrentByTenantId(any())).thenReturn(Optional.empty());
    when(subscriptionRepository.buscarPorAsaasSubscriptionId(anyString()))
        .thenReturn(Optional.empty());
    when(paymentRepository.buscarPorAsaasPaymentId(anyString())).thenReturn(Optional.empty());
    when(checkoutOrderRepository.listarConfirmadosMaisRecentesPrimeiro(any(), any()))
        .thenReturn(List.of());
    when(checkoutOrderRepository.listarNaoVencidosAte(any(), any(), any())).thenReturn(List.of());
    when(subscriptionRepository.listarVivasPorStatus(any(), any(), any())).thenReturn(List.of());
    when(subscriptionRepository.findByTenantIdAndStatus(any(), any())).thenReturn(List.of());
    when(productRepository.listarAtivosNaoTrialMaisRecentesPrimeiro()).thenReturn(List.of());
    when(checkoutIntentRepository.saveAndFlush(any(CheckoutIntent.class)))
        .thenAnswer(
            inv -> {
              CheckoutIntent i = inv.getArgument(0);
              if (i.getId() == null) i.setId(UUID.randomUUID());
              return i;
            });
    when(checkoutOrderRepository.saveAndFlush(any(CheckoutOrder.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service =
        new BillingAdminService(
            contextoTenant,
            checkoutOrderRepository,
            checkoutIntentRepository,
            subscriptionRepository,
            paymentRepository,
            productRepository,
            tenantRepository,
            licenseStatusService,
            licenseEventRepository);
    ReflectionTestUtils.setField(service, "entityManager", entityManager);
  }

  // ─── resolveTargetTenantId ────────────────────────────────────────────────

  @Test
  void semTenantIdNoCorpoUsaTenantDoToken() {
    when(paymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());

    service.listPaymentsByTenant(null);

    verify(paymentRepository).findByTenantIdOrderByCreatedAtDesc(tenantId);
    verifyNoInteractions(tenantRepository);
  }

  @Test
  void tenantIdMalformadoEhErroDeRequisicao() {
    assertThatThrownBy(() -> service.listPaymentsByTenant("nao-e-uuid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tenantId invalido.");
  }

  @Test
  void tenantIdInexistenteDevolve404() {
    UUID outro = UUID.randomUUID();
    when(tenantRepository.findById(outro)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listPaymentsByTenant(outro.toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Tenant nao encontrado.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  // ─── listActiveTenants / listPaymentsByTenant ─────────────────────────────

  @Test
  void listActiveTenantsMapeiaColunasNaOrdemDoSelect() {
    UUID id = UUID.randomUUID();
    stubNativeQuery(
        List.<Object[]>of(new Object[] {id, "Salao A", "salao-a", "a@x.com", "1199", "ACTIVE"}));

    BillingDtos.AdminTenantListResponse response = service.listActiveTenants();

    assertThat(response.items).hasSize(1);
    BillingDtos.AdminTenantItemResponse item = response.items.get(0);
    assertThat(item.tenantId).isEqualTo(id.toString());
    assertThat(item.name).isEqualTo("Salao A");
    assertThat(item.slug).isEqualTo("salao-a");
    assertThat(item.email).isEqualTo("a@x.com");
    assertThat(item.phone).isEqualTo("1199");
    assertThat(item.planStatus).isEqualTo("ACTIVE");
  }

  @Test
  void listPaymentsByTenantConverteCentavosParaReais() {
    Payment payment = new Payment();
    payment.setId(UUID.randomUUID());
    payment.setTenantId(tenantId);
    payment.setAsaasPaymentId("pay_1");
    payment.setStatus(StatusPayment.RECEIVED);
    payment.setAmountCents(19990L);
    payment.setNetAmountCents(19000L);
    when(paymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
        .thenReturn(List.of(payment));

    BillingDtos.PaymentsListResponse response = service.listPaymentsByTenant(null);

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).amount).isEqualByComparingTo(new BigDecimal("199.90"));
    assertThat(response.items.get(0).netAmount).isEqualByComparingTo(new BigDecimal("190.00"));
    assertThat(response.items.get(0).status).isEqualTo("RECEIVED");
  }

  @Test
  void netAmountNuloContinuaNuloNaResposta() {
    Payment payment = new Payment();
    payment.setTenantId(tenantId);
    payment.setAmountCents(1000L);
    payment.setNetAmountCents(null);
    when(paymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
        .thenReturn(List.of(payment));

    assertThat(service.listPaymentsByTenant(null).items.get(0).netAmount).isNull();
  }

  // ─── forceExpireCurrentTenant ─────────────────────────────────────────────

  @Test
  void forcarVencimentoUsaCincoMinutosQuandoMinutesAgoAusente() {
    CheckoutOrder order = new CheckoutOrder();
    order.setValidUntil(Instant.now().plus(30, ChronoUnit.DAYS));
    when(checkoutOrderRepository.listarNaoVencidosAte(
            eq(tenantId), eq(StatusCheckout.CONFIRMED), any()))
        .thenReturn(List.of(order));

    Instant antes = Instant.now();
    BillingDtos.AdminBillingActionResponse response = service.forceExpireCurrentTenant(null, null);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.licenseStatus).isEqualTo("ACTIVE");
    assertThat(response.message).isEqualTo("Plano marcado como vencido para testes.");
    assertThat(order.getValidUntil()).isBefore(antes);
    assertThat(order.getValidUntil())
        .isAfter(antes.minus(6, ChronoUnit.MINUTES))
        .isBefore(antes.minus(4, ChronoUnit.MINUTES).plusSeconds(1));
    verify(checkoutOrderRepository).saveAll(List.of(order));
  }

  @Test
  void minutesAgoNaoPositivoCaiNoPadraoDeCincoMinutos() {
    ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
    Instant antes = Instant.now();

    service.forceExpireCurrentTenant(null, 0);

    verify(checkoutOrderRepository)
        .listarNaoVencidosAte(eq(tenantId), eq(StatusCheckout.CONFIRMED), limite.capture());
    assertThat(limite.getValue()).isAfter(antes.minus(6, ChronoUnit.MINUTES));
  }

  @Test
  void forcarVencimentoDerrubaAssinaturasVivasParaOverdue() {
    Subscription ativa = new Subscription();
    ativa.setStatus(StatusSubscription.ACTIVE);
    Subscription pendente = new Subscription();
    pendente.setStatus(StatusSubscription.PENDING);
    when(subscriptionRepository.listarVivasPorStatus(
            tenantId, StatusSubscription.ACTIVE, StatusSubscription.PENDING))
        .thenReturn(List.of(ativa, pendente));

    service.forceExpireCurrentTenant(null, 120);

    assertThat(ativa.getStatus()).isEqualTo(StatusSubscription.OVERDUE);
    assertThat(pendente.getStatus()).isEqualTo(StatusSubscription.OVERDUE);
    verify(subscriptionRepository).saveAll(List.of(ativa, pendente));
  }

  // ─── releaseLicenseForCurrentTenant ───────────────────────────────────────

  @Test
  void liberarLicencaSemProdutoResolvivelFalha() {
    assertThatThrownBy(() -> service.releaseLicenseForCurrentTenant(null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Produto nao encontrado para liberar licenca.");
  }

  @Test
  void liberarLicencaCriaIntentConfirmadaEPedidoApontandoParaEla() {
    Product product = plano(new BigDecimal("199.90"), null, 1);
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    Instant antes = Instant.now();
    BillingDtos.AdminBillingActionResponse response =
        service.releaseLicenseForCurrentTenant(null, productId.toString(), null, null);

    ArgumentCaptor<CheckoutIntent> intentCaptor = ArgumentCaptor.forClass(CheckoutIntent.class);
    verify(checkoutIntentRepository).saveAndFlush(intentCaptor.capture());
    CheckoutIntent intent = intentCaptor.getValue();
    assertThat(intent.getTenantId()).isEqualTo(tenantId);
    assertThat(intent.getStatus()).isEqualTo(StatusCheckout.CONFIRMED);
    assertThat(intent.getQuantity()).isEqualTo(1);
    assertThat(intent.getUnitPriceSnapshot()).isEqualByComparingTo(new BigDecimal("199.90"));
    assertThat(intent.getTotalPriceSnapshot()).isEqualByComparingTo(new BigDecimal("199.90"));
    assertThat(intent.getCalculatedTotal()).isEqualByComparingTo(new BigDecimal("199.90"));
    assertThat(intent.getPaymentReference()).startsWith("admin-release-");
    assertThat(intent.getConfirmedAt()).isAfterOrEqualTo(antes);

    ArgumentCaptor<CheckoutOrder> orderCaptor = ArgumentCaptor.forClass(CheckoutOrder.class);
    verify(checkoutOrderRepository).saveAndFlush(orderCaptor.capture());
    CheckoutOrder order = orderCaptor.getValue();
    assertThat(order.getIntentId()).isNotNull().isEqualTo(intent.getId());
    assertThat(order.getStatus()).isEqualTo(StatusCheckout.CONFIRMED);
    assertThat(order.getTotal()).isEqualTo(19990L);
    assertThat(order.getTenantId()).isEqualTo(tenantId);
    assertThat(order.getValidUntil()).isEqualTo(intent.getExpiresAt());

    assertThat(response.productId).isEqualTo(productId.toString());
    assertThat(response.validUntil).isEqualTo(order.getValidUntil().toString());
    assertThat(response.message).isEqualTo("Licenca liberada manualmente para testes.");
  }

  @Test
  void moedaNulaDoProdutoViraBrl() {
    Product product = plano(new BigDecimal("10.00"), null, 1);
    product.setCurrency(null);
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    service.releaseLicenseForCurrentTenant(null, productId.toString(), null, null);

    ArgumentCaptor<CheckoutIntent> captor = ArgumentCaptor.forClass(CheckoutIntent.class);
    verify(checkoutIntentRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getCurrency()).isEqualTo("BRL");
    assertThat(captor.getValue().getCurrencySnapshot()).isEqualTo("BRL");
  }

  @Test
  void validityDaysDoCorpoTemPrecedenciaSobreOProduto() {
    Product product = plano(new BigDecimal("10.00"), 45, 12);
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    Instant antes = Instant.now();
    service.releaseLicenseForCurrentTenant(null, productId.toString(), 3, null);

    assertThat(validUntilGravado())
        .isBetween(antes.plus(3, ChronoUnit.DAYS), antes.plus(3, ChronoUnit.DAYS).plusSeconds(30));
  }

  @Test
  void semValidityDaysUsaValidityDaysDoProduto() {
    Product product = plano(new BigDecimal("10.00"), 45, 12);
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    Instant antes = Instant.now();
    service.releaseLicenseForCurrentTenant(null, productId.toString(), 0, null);

    assertThat(validUntilGravado())
        .isBetween(antes.plus(45, ChronoUnit.DAYS), antes.plus(45, ChronoUnit.DAYS).plusSeconds(30));
  }

  @Test
  void semValidityDaysNoProdutoCaiParaMesesVezesTrinta() {
    Product product = plano(new BigDecimal("10.00"), null, 2);
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    Instant antes = Instant.now();
    service.releaseLicenseForCurrentTenant(null, productId.toString(), null, null);

    assertThat(validUntilGravado())
        .isBetween(antes.plus(60, ChronoUnit.DAYS), antes.plus(60, ChronoUnit.DAYS).plusSeconds(30));
  }

  @Test
  void produtoSemMesesNemDiasVale30Dias() {
    Product product = plano(new BigDecimal("10.00"), 0, 0);
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    Instant antes = Instant.now();
    service.releaseLicenseForCurrentTenant(null, productId.toString(), null, null);

    assertThat(validUntilGravado())
        .isBetween(antes.plus(30, ChronoUnit.DAYS), antes.plus(30, ChronoUnit.DAYS).plusSeconds(30));
  }

  @Test
  void liberarLicencaMarcaPagamentoRecebidoEReativaAssinaturaAsaas() {
    Product product = plano(new BigDecimal("10.00"), 30, 1);
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    Payment payment = new Payment();
    payment.setTenantId(tenantId);
    payment.setAsaasPaymentId("pay_9");
    payment.setAsaasSubscriptionId("sub_9");
    payment.setStatus(StatusPayment.PENDING);
    when(paymentRepository.buscarPorAsaasPaymentId("pay_9")).thenReturn(Optional.of(payment));

    Subscription subscription = new Subscription();
    subscription.setStatus(StatusSubscription.CANCELLED);
    subscription.setCancelledAt(Instant.now());
    when(subscriptionRepository.buscarPorAsaasSubscriptionId("sub_9"))
        .thenReturn(Optional.of(subscription));

    service.releaseLicenseForCurrentTenant(null, productId.toString(), null, "pay_9");

    assertThat(payment.getStatus()).isEqualTo(StatusPayment.RECEIVED);
    assertThat(payment.getPaidAt()).isNotNull();
    assertThat(subscription.getStatus()).isEqualTo(StatusSubscription.ACTIVE);
    assertThat(subscription.getCancelledAt()).isNull();
    verify(paymentRepository).save(payment);
    verify(subscriptionRepository).save(subscription);
  }

  @Test
  void liberarLicencaReativaAssinaturasOverdueDoTenant() {
    Product product = plano(new BigDecimal("10.00"), 30, 1);
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    Subscription atrasada = new Subscription();
    atrasada.setStatus(StatusSubscription.OVERDUE);
    when(subscriptionRepository.findByTenantIdAndStatus(tenantId, StatusSubscription.OVERDUE))
        .thenReturn(List.of(atrasada));

    service.releaseLicenseForCurrentTenant(null, productId.toString(), null, null);

    assertThat(atrasada.getStatus()).isEqualTo(StatusSubscription.ACTIVE);
    verify(subscriptionRepository).saveAll(List.of(atrasada));
  }

  @Test
  void pagamentoDeOutroTenantNaoEhTocado() {
    Product product = plano(new BigDecimal("10.00"), 30, 1);
    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    Payment deOutroTenant = new Payment();
    deOutroTenant.setTenantId(UUID.randomUUID());
    deOutroTenant.setAsaasPaymentId("pay_x");
    deOutroTenant.setStatus(StatusPayment.PENDING);
    when(paymentRepository.buscarPorAsaasPaymentId("pay_x"))
        .thenReturn(Optional.of(deOutroTenant));

    service.releaseLicenseForCurrentTenant(null, productId.toString(), null, "pay_x");

    assertThat(deOutroTenant.getStatus()).isEqualTo(StatusPayment.PENDING);
    verify(paymentRepository, never()).save(any());
  }

  // ─── resolveProduct (cascata) ─────────────────────────────────────────────

  @Test
  void produtoVemDaAssinaturaVigenteQuandoNaoHaProductIdExplicito() {
    Subscription subscription = new Subscription();
    subscription.setProductId(productId);
    when(subscriptionRepository.findCurrentByTenantId(tenantId))
        .thenReturn(Optional.of(subscription));
    when(productRepository.findById(productId))
        .thenReturn(Optional.of(plano(new BigDecimal("10.00"), 30, 1)));

    BillingDtos.AdminBillingActionResponse response =
        service.releaseLicenseForCurrentTenant(null, null, null, null);

    assertThat(response.productId).isEqualTo(productId.toString());
  }

  @Test
  void planCodeComUuidServeDeProductIdNaAssinatura() {
    Subscription subscription = new Subscription();
    subscription.setProductId(null);
    subscription.setPlanCode(productId.toString());
    when(subscriptionRepository.findCurrentByTenantId(tenantId))
        .thenReturn(Optional.of(subscription));
    when(productRepository.findById(productId))
        .thenReturn(Optional.of(plano(new BigDecimal("10.00"), 30, 1)));

    assertThat(service.releaseLicenseForCurrentTenant(null, null, null, null).productId)
        .isEqualTo(productId.toString());
  }

  @Test
  void semAssinaturaProdutoVemDoPedidoConfirmadoMaisRecente() {
    CheckoutOrder order = new CheckoutOrder();
    order.setProductId(productId);
    when(checkoutOrderRepository.listarConfirmadosMaisRecentesPrimeiro(
            tenantId, StatusCheckout.CONFIRMED))
        .thenReturn(List.of(order));
    when(productRepository.findById(productId))
        .thenReturn(Optional.of(plano(new BigDecimal("10.00"), 30, 1)));

    assertThat(service.releaseLicenseForCurrentTenant(null, null, null, null).productId)
        .isEqualTo(productId.toString());
  }

  @Test
  void ultimoFallbackEhQualquerPlanoPagoAtivo() {
    Product fallback = plano(new BigDecimal("10.00"), 30, 1);
    when(productRepository.listarAtivosNaoTrialMaisRecentesPrimeiro())
        .thenReturn(List.of(fallback));

    assertThat(service.releaseLicenseForCurrentTenant(null, null, null, null).productId)
        .isEqualTo(productId.toString());
  }

  // ─── markPaymentAsReceived ────────────────────────────────────────────────

  @Test
  void marcarPagamentoInexistenteFalha() {
    assertThatThrownBy(() -> service.markPaymentAsReceived(null, "pay_0", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Pagamento nao encontrado.");
  }

  @Test
  void marcarPagamentoAceitaIdInternoAlemDoIdAsaas() {
    UUID internalId = UUID.randomUUID();
    Payment payment = new Payment();
    payment.setId(internalId);
    payment.setTenantId(tenantId);
    payment.setStatus(StatusPayment.PENDING);
    when(paymentRepository.buscarPorAsaasPaymentId(internalId.toString()))
        .thenReturn(Optional.empty());
    when(paymentRepository.findById(internalId)).thenReturn(Optional.of(payment));

    BillingDtos.AdminBillingActionResponse response =
        service.markPaymentAsReceived(null, internalId.toString(), null);

    assertThat(payment.getStatus()).isEqualTo(StatusPayment.RECEIVED);
    assertThat(response.message).isEqualTo("Pagamento marcado como recebido.");
    assertThat(response.productId).isNull();
  }

  @Test
  void marcarPagamentoLiberaLicencaQuandoResolveProdutoPelaAssinatura() {
    UUID subscriptionId = UUID.randomUUID();
    Payment payment = new Payment();
    payment.setTenantId(tenantId);
    payment.setAsaasPaymentId("pay_5");
    payment.setSubscriptionId(subscriptionId);
    payment.setStatus(StatusPayment.PENDING);
    when(paymentRepository.buscarPorAsaasPaymentId("pay_5")).thenReturn(Optional.of(payment));

    Subscription subscription = new Subscription();
    subscription.setProductId(productId);
    when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
    when(productRepository.findById(productId))
        .thenReturn(Optional.of(plano(new BigDecimal("10.00"), 30, 1)));
    // A chamada aninhada reserializa o tenant para String e reexecuta resolveTargetTenantId, que
    // desta vez exige o tenant no banco — ver `chamadaAninhadaRevalidaOTenantNoBanco`.
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(new Tenant()));

    BillingDtos.AdminBillingActionResponse response =
        service.markPaymentAsReceived(null, "pay_5", 7);

    assertThat(response.productId).isEqualTo(productId.toString());
    assertThat(response.paymentId).isEqualTo("pay_5");
    verify(checkoutOrderRepository).saveAndFlush(any(CheckoutOrder.class));
  }

  /**
   * Assimetria do original: {@code markPaymentAsReceived} chamado <b>sem</b> {@code tenantId} usa o
   * tenant do token (que nao e revalidado), mas repassa esse tenant como String para
   * {@code releaseLicenseForCurrentTenant} — e ai {@code resolveTargetTenantId} roda de novo e
   * exige o tenant no banco. Tenant do token ausente da tabela vira 404 no meio da operacao.
   */
  @Test
  void chamadaAninhadaRevalidaOTenantNoBanco() {
    UUID subscriptionId = UUID.randomUUID();
    Payment payment = new Payment();
    payment.setTenantId(tenantId);
    payment.setAsaasPaymentId("pay_7");
    payment.setSubscriptionId(subscriptionId);
    payment.setStatus(StatusPayment.PENDING);
    when(paymentRepository.buscarPorAsaasPaymentId("pay_7")).thenReturn(Optional.of(payment));

    Subscription subscription = new Subscription();
    subscription.setProductId(productId);
    when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
    when(productRepository.findById(productId))
        .thenReturn(Optional.of(plano(new BigDecimal("10.00"), 30, 1)));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.markPaymentAsReceived(null, "pay_7", null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Tenant nao encontrado.");
    // O pagamento ja tinha sido marcado antes do 404 — a transacao e quem desfaz.
    assertThat(payment.getStatus()).isEqualTo(StatusPayment.RECEIVED);
  }

  @Test
  void marcarPagamentoSemProdutoResolvivelNaoCriaPedido() {
    Payment payment = new Payment();
    payment.setTenantId(tenantId);
    payment.setAsaasPaymentId("pay_6");
    payment.setStatus(StatusPayment.PENDING);
    when(paymentRepository.buscarPorAsaasPaymentId("pay_6")).thenReturn(Optional.of(payment));

    service.markPaymentAsReceived(null, "pay_6", null);

    verify(checkoutOrderRepository, never()).saveAndFlush(any(CheckoutOrder.class));
    verify(checkoutIntentRepository, never()).saveAndFlush(any(CheckoutIntent.class));
  }

  // ─── extenderTrial ────────────────────────────────────────────────────────

  @Test
  void extenderTrialExigeTenantIdEExtraDias() {
    assertThatThrownBy(() -> service.extenderTrial("  ", 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tenantId obrigatorio");
    assertThatThrownBy(() -> service.extenderTrial(tenantId.toString(), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("extraDays deve ser >= 1");
    assertThatThrownBy(() -> service.extenderTrial(tenantId.toString(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("extraDays deve ser >= 1");
  }

  @Test
  void extenderTrialSemTrialAtivoDevolve404() {
    CheckoutOrder pago = new CheckoutOrder();
    pago.setProductId(productId);
    when(checkoutOrderRepository.listarConfirmadosMaisRecentesPrimeiro(
            tenantId, StatusCheckout.CONFIRMED))
        .thenReturn(List.of(pago));
    when(productRepository.findById(productId))
        .thenReturn(Optional.of(plano(new BigDecimal("10.00"), 30, 1)));

    assertThatThrownBy(() -> service.extenderTrial(tenantId.toString(), 5))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Nenhum trial ativo encontrado para o tenant.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void extenderTrialSomaDiasAoVencimentoAtualERegistraEvento() {
    Instant validUntil = Instant.parse("2026-03-10T12:00:00Z");
    CheckoutOrder trial = new CheckoutOrder();
    trial.setProductId(productId);
    trial.setValidUntil(validUntil);
    when(checkoutOrderRepository.listarConfirmadosMaisRecentesPrimeiro(
            tenantId, StatusCheckout.CONFIRMED))
        .thenReturn(List.of(trial));
    Product produtoTrial = plano(new BigDecimal("0.00"), 7, 1);
    produtoTrial.setTrial(true);
    when(productRepository.findById(productId)).thenReturn(Optional.of(produtoTrial));

    BillingDtos.ExtendTrialResponse response = service.extenderTrial(tenantId.toString(), 5);

    Instant esperado = validUntil.plus(5, ChronoUnit.DAYS);
    assertThat(trial.getValidUntil()).isEqualTo(esperado);
    assertThat(response.validUntil).isEqualTo(esperado.toString());
    assertThat(response.tenantId).isEqualTo(tenantId.toString());
    assertThat(response.message).isEqualTo("Trial estendido por 5 dia(s).");

    ArgumentCaptor<LicenseEvent> eventCaptor = ArgumentCaptor.forClass(LicenseEvent.class);
    verify(licenseEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventType()).isEqualTo("TRIAL_EXTENDED");
    assertThat(eventCaptor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(eventCaptor.getValue().getProductId()).isEqualTo(productId);
    assertThat(eventCaptor.getValue().getValidUntil()).isEqualTo(esperado);
    verify(licenseStatusService).avaliar(tenantId);
  }

  @Test
  void trialSemValidUntilPassaAContarDeAgora() {
    CheckoutOrder trial = new CheckoutOrder();
    trial.setProductId(productId);
    trial.setValidUntil(null);
    when(checkoutOrderRepository.listarConfirmadosMaisRecentesPrimeiro(
            tenantId, StatusCheckout.CONFIRMED))
        .thenReturn(List.of(trial));
    Product produtoTrial = plano(new BigDecimal("0.00"), 7, 1);
    produtoTrial.setTrial(true);
    when(productRepository.findById(productId)).thenReturn(Optional.of(produtoTrial));

    Instant antes = Instant.now();
    service.extenderTrial(tenantId.toString(), 2);

    assertThat(trial.getValidUntil())
        .isBetween(antes.plus(2, ChronoUnit.DAYS), antes.plus(2, ChronoUnit.DAYS).plusSeconds(30));
  }

  @Test
  void extenderTrialIgnoraPedidoSemProduto() {
    CheckoutOrder semProduto = new CheckoutOrder();
    semProduto.setProductId(null);
    when(checkoutOrderRepository.listarConfirmadosMaisRecentesPrimeiro(
            tenantId, StatusCheckout.CONFIRMED))
        .thenReturn(List.of(semProduto));

    assertThatThrownBy(() -> service.extenderTrial(tenantId.toString(), 1))
        .isInstanceOf(ApiClientErrorException.class);
    verify(productRepository, never()).findById(any());
  }

  // ─── listarHistoricoLicenca ───────────────────────────────────────────────

  @Test
  void historicoDeLicencaMapeiaTodosOsCampos() {
    LicenseEvent event = new LicenseEvent();
    event.setId(UUID.randomUUID());
    event.setTenantId(tenantId);
    event.setEventType("PLAN_CANCELLED");
    event.setActorUserId(UUID.randomUUID());
    event.setProductId(productId);
    event.setValidUntil(Instant.parse("2026-04-01T00:00:00Z"));
    event.setMetadata("{\"a\":1}");
    event.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
    when(licenseEventRepository.findByTenant(tenantId)).thenReturn(List.of(event));

    BillingDtos.LicenseHistoryResponse response =
        service.listarHistoricoLicenca(tenantId.toString());

    assertThat(response.tenantId).isEqualTo(tenantId.toString());
    assertThat(response.events).hasSize(1);
    BillingDtos.LicenseEventResponse r = response.events.get(0);
    assertThat(r.eventType).isEqualTo("PLAN_CANCELLED");
    assertThat(r.productId).isEqualTo(productId.toString());
    assertThat(r.validUntil).isEqualTo("2026-04-01T00:00:00Z");
    assertThat(r.metadata).isEqualTo("{\"a\":1}");
    assertThat(r.createdAt).isEqualTo("2026-03-01T00:00:00Z");
  }

  @Test
  void historicoDeLicencaComTenantIdMalformadoFalha() {
    assertThatThrownBy(() -> service.listarHistoricoLicenca("xyz"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ─── relatorioLicencas ────────────────────────────────────────────────────

  @Test
  void relatorioClassificaTenantsEmRamosExclusivos() {
    stubNativeQuery(
        List.of(
            linhaRelatorio("TRIAL_ACTIVE", false),
            linhaRelatorio("TRIAL_EXPIRED", true),
            linhaRelatorio("ACTIVE", false),
            linhaRelatorio("EXPIRED", true),
            linhaRelatorio("BLOCKED", false),
            linhaRelatorio("DESCONHECIDO", true)));

    BillingDtos.LicencasAdminReportResponse response = service.relatorioLicencas();

    assertThat(response.totalTenants).isEqualTo(6);
    // TRIAL_EXPIRED cai no ramo de trial (startsWith vence), nao no de expirado.
    assertThat(response.trialCount).isEqualTo(2);
    assertThat(response.ativosCount).isEqualTo(1);
    // EXPIRED + DESCONHECIDO vencido.
    assertThat(response.expiradosCount).isEqualTo(2);
    assertThat(response.bloqueadosCount).isEqualTo(1);
  }

  @Test
  void relatorioMapeiaCamposDaLinha() {
    stubNativeQuery(
        List.<Object[]>of(
            new Object[] {
              tenantId.toString(),
              "Salao Z",
              "z@x.com",
              "ACTIVE",
              "CREDIT_CARD",
              "2026-05-01T00:00:00Z",
              12,
              false,
              "2025-01-01T00:00:00Z"
            }));

    BillingDtos.LicencasAdminReportItem item = service.relatorioLicencas().items.get(0);

    assertThat(item.tenantId).isEqualTo(tenantId.toString());
    assertThat(item.tenantNome).isEqualTo("Salao Z");
    assertThat(item.tenantEmail).isEqualTo("z@x.com");
    assertThat(item.planStatus).isEqualTo("ACTIVE");
    assertThat(item.billingType).isEqualTo("CREDIT_CARD");
    assertThat(item.validUntil).isEqualTo("2026-05-01T00:00:00Z");
    assertThat(item.diasRestantes).isEqualTo(12);
    assertThat(item.vencido).isFalse();
    assertThat(item.createdAt).isEqualTo("2025-01-01T00:00:00Z");
  }

  @Test
  void diasRestantesNuloViraMenosUm() {
    stubNativeQuery(
        List.<Object[]>of(
            new Object[] {
              tenantId.toString(), "Salao Y", null, "ACTIVE", null, null, null, null, null
            }));

    BillingDtos.LicencasAdminReportItem item = service.relatorioLicencas().items.get(0);

    assertThat(item.diasRestantes).isEqualTo(-1);
    assertThat(item.vencido).isFalse();
    assertThat(item.validUntil).isNull();
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private Product plano(BigDecimal preco, Integer validityDays, int validityMonths) {
    Product product = new Product();
    product.setId(productId);
    product.setName("Plano Pro");
    product.setCurrency("BRL");
    product.setPriceCents(preco);
    product.setValidityDays(validityDays);
    product.setValidityMonths(validityMonths);
    product.setTrial(false);
    return product;
  }

  private Instant validUntilGravado() {
    ArgumentCaptor<CheckoutOrder> captor = ArgumentCaptor.forClass(CheckoutOrder.class);
    verify(checkoutOrderRepository).saveAndFlush(captor.capture());
    return captor.getValue().getValidUntil();
  }

  private Object[] linhaRelatorio(String planStatus, boolean vencido) {
    return new Object[] {
      UUID.randomUUID().toString(),
      "Tenant",
      "t@x.com",
      planStatus,
      "PIX",
      "2026-05-01T00:00:00Z",
      5,
      vencido,
      "2025-01-01T00:00:00Z"
    };
  }

  private void stubNativeQuery(List<Object[]> rows) {
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.getResultList()).thenReturn(rows);
  }

  @Test
  void tenantExistenteEhAceitoComoAlvo() {
    UUID outro = UUID.randomUUID();
    when(tenantRepository.findById(outro)).thenReturn(Optional.of(new Tenant()));
    when(paymentRepository.findByTenantIdOrderByCreatedAtDesc(outro)).thenReturn(List.of());

    assertThat(service.listPaymentsByTenant(outro.toString()).items).isEmpty();
    verify(paymentRepository).findByTenantIdOrderByCreatedAtDesc(outro);
  }
}
