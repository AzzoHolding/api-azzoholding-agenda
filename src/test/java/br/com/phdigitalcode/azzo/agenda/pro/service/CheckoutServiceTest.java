package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.CheckoutDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutIntent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProductCapability;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutIntentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductCapabilityRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.JsonUtil;

/**
 * Cobre {@code modules/billing/application/CheckoutService.java}: elegibilidade do plano, snapshot
 * vs. recalculo de preco, expiracao, idempotencia da confirmacao e ownership da intent.
 */
class CheckoutServiceTest {

  private ProductRepository productRepository;
  private ProductCapabilityRepository productCapabilityRepository;
  private CheckoutIntentRepository checkoutIntentRepository;
  private CheckoutOrderRepository checkoutOrderRepository;
  private AuthenticatedUser authenticatedUser;
  private ContextoTenant contextoTenant;
  private CheckoutService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    productRepository = mock(ProductRepository.class);
    productCapabilityRepository = mock(ProductCapabilityRepository.class);
    checkoutIntentRepository = mock(CheckoutIntentRepository.class);
    checkoutOrderRepository = mock(CheckoutOrderRepository.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    contextoTenant = mock(ContextoTenant.class);

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(productCapabilityRepository.findById(any())).thenReturn(Optional.empty());
    when(checkoutOrderRepository.buscarPorIntentId(any())).thenReturn(Optional.empty());
    when(checkoutIntentRepository.save(any(CheckoutIntent.class)))
        .thenAnswer(
            inv -> {
              CheckoutIntent i = inv.getArgument(0);
              if (i.getId() == null) i.setId(UUID.randomUUID());
              return i;
            });
    when(checkoutOrderRepository.save(any(CheckoutOrder.class)))
        .thenAnswer(
            inv -> {
              CheckoutOrder o = inv.getArgument(0);
              if (o.getId() == null) o.setId(UUID.randomUUID());
              return o;
            });

    service =
        new CheckoutService(
            productRepository,
            productCapabilityRepository,
            checkoutIntentRepository,
            checkoutOrderRepository,
            authenticatedUser,
            new JsonUtil(new ObjectMapper()),
            contextoTenant);
  }

  private Product produto(boolean ativo, boolean trial, boolean exclusivoVendaInterna) {
    Product product = new Product();
    product.setId(productId);
    product.setName("Plano Pro");
    product.setCurrency("BRL");
    product.setPriceCents(new BigDecimal("199.90"));
    product.setValidityMonths(1);
    product.setActive(ativo);
    product.setTrial(trial);
    product.setExclusivoVendaInterna(exclusivoVendaInterna);
    product.setFeaturesJson("[\"Agenda ilimitada\",\"Relatorios\"]");
    when(productRepository.findById(eq(productId))).thenReturn(Optional.of(product));
    return product;
  }

  private CheckoutDtos.CreateIntentRequest intentRequest(int quantity) {
    CheckoutDtos.CreateIntentRequest req = new CheckoutDtos.CreateIntentRequest();
    req.productId = productId.toString();
    req.quantity = quantity;
    return req;
  }

  private CheckoutIntent intentPendente() {
    CheckoutIntent intent = new CheckoutIntent();
    intent.setId(UUID.randomUUID());
    intent.setTenantId(tenantId);
    intent.setUserId(userId);
    intent.setProductId(productId);
    intent.setQuantity(1);
    intent.setStatus(StatusCheckout.PENDING);
    intent.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    when(checkoutIntentRepository.findByIdForUpdate(eq(intent.getId())))
        .thenReturn(Optional.of(intent));
    return intent;
  }

  // ---------------------------------------------------------------- catalogo

  @Test
  void listarProdutosDesserializaFeaturesEResolveMaxProfessionals() {
    Product product = produto(true, false, false);
    when(productRepository.listarContrataveisPublicamente()).thenReturn(List.of(product));
    ProductCapability capability = new ProductCapability();
    capability.setProductId(productId);
    capability.setMaxProfessionals(5);
    when(productCapabilityRepository.findById(eq(productId))).thenReturn(Optional.of(capability));

    List<CheckoutDtos.ProductResponse> produtos = service.listarProdutos();

    assertThat(produtos).hasSize(1);
    assertThat(produtos.get(0).name).isEqualTo("Plano Pro");
    assertThat(produtos.get(0).price).isEqualTo(199.90);
    assertThat(produtos.get(0).maxProfessionals).isEqualTo(5);
    assertThat(produtos.get(0).features).containsExactly("Agenda ilimitada", "Relatorios");
    // validityDays cai para validityMonths * 30 quando validityDays nao esta configurado.
    assertThat(produtos.get(0).validityDays).isEqualTo(30);
  }

  @Test
  void validityDaysExplicitoVenceValidityMonths() {
    Product product = produto(true, false, false);
    product.setValidityMonths(12);
    product.setValidityDays(45);
    when(productRepository.listarContrataveisPublicamente()).thenReturn(List.of(product));

    assertThat(service.listarProdutos().get(0).validityDays).isEqualTo(45);
  }

  @Test
  void planoExclusivoDeVendaInternaNaoEContratavelPublicamente() {
    assertThat(service.isContratavelPublicamente(produto(true, false, true))).isFalse();
    assertThat(service.isContratavelPublicamente(produto(true, true, false))).isFalse();
    assertThat(service.isContratavelPublicamente(produto(false, false, false))).isFalse();
    assertThat(service.isContratavelPublicamente(produto(true, false, false))).isTrue();
    assertThat(service.isContratavelPublicamente(null)).isFalse();
  }

  // ---------------------------------------------------------------- criar intent

  @Test
  void criarIntentGravaSnapshotDoProdutoEExpiraEmQuinzeMinutos() {
    produto(true, false, false);

    Instant antes = Instant.now();
    CheckoutDtos.CreateIntentResponse response = service.criarIntent(intentRequest(2));

    ArgumentCaptor<CheckoutIntent> captor = ArgumentCaptor.forClass(CheckoutIntent.class);
    verify(checkoutIntentRepository).save(captor.capture());
    CheckoutIntent intent = captor.getValue();
    assertThat(intent.getTenantId()).isEqualTo(tenantId);
    assertThat(intent.getUserId()).isEqualTo(userId);
    assertThat(intent.getProductNameSnapshot()).isEqualTo("Plano Pro");
    assertThat(intent.getCurrencySnapshot()).isEqualTo("BRL");
    assertThat(intent.getUnitPriceSnapshot()).isEqualByComparingTo("199.90");
    assertThat(intent.getTotalPriceSnapshot()).isEqualByComparingTo("399.80");
    assertThat(intent.getStatus()).isEqualTo(StatusCheckout.PENDING);
    assertThat(intent.getExpiresAt()).isBetween(antes, antes.plus(16, ChronoUnit.MINUTES));

    assertThat(response.status).isEqualTo("PENDING");
    assertThat(response.totalPrice).isEqualTo(399.80);
  }

  @Test
  void criarIntentDePlanoExclusivoDeVendaInternaResponde404() {
    produto(true, false, true);

    assertThatThrownBy(() -> service.criarIntent(intentRequest(1)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Produto nao encontrado")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void criarIntentComQuantidadeForaDoLimiteResponde400() {
    assertThatThrownBy(() -> service.criarIntent(intentRequest(101)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Quantidade fora do limite permitido")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);

    assertThatThrownBy(() -> service.criarIntent(intentRequest(0)))
        .isInstanceOf(ApiClientErrorException.class);

    verify(checkoutIntentRepository, never()).save(any());
  }

  @Test
  void criarIntentSemUsuarioAutenticadoResponde403() {
    when(authenticatedUser.idOuNulo()).thenReturn(null);

    assertThatThrownBy(() -> service.criarIntent(intentRequest(1)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Checkout requer usuario autenticado com tenant valido")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(403);
  }

  @Test
  void criarIntentSemTenantResolvidoResponde403() {
    when(contextoTenant.obterTenantIdOuFalhar())
        .thenThrow(new IllegalStateException("tenant ausente"));

    assertThatThrownBy(() -> service.criarIntent(intentRequest(1)))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(403);
  }

  // ---------------------------------------------------------------- confirmar

  @Test
  void confirmarIntentRecalculaOPrecoEGeraPedidoComValidade() {
    CheckoutIntent intent = intentPendente();
    intent.setQuantity(2);
    // Snapshot antigo: o catalogo subiu de preco depois da criacao da intent.
    intent.setUnitPriceSnapshot(new BigDecimal("100.00"));
    intent.setTotalPriceSnapshot(new BigDecimal("200.00"));
    Product product = produto(true, false, false);
    product.setValidityDays(30);

    Instant antes = Instant.now();
    CheckoutDtos.ConfirmIntentResponse response = service.confirmarIntent(intent.getId());

    assertThat(intent.getStatus()).isEqualTo(StatusCheckout.CONFIRMED);
    assertThat(intent.getTotalPriceSnapshot()).isEqualByComparingTo("399.80");
    assertThat(intent.getPaymentReference()).startsWith("stub-pay-" + intent.getId());

    ArgumentCaptor<CheckoutOrder> captor = ArgumentCaptor.forClass(CheckoutOrder.class);
    verify(checkoutOrderRepository).save(captor.capture());
    CheckoutOrder order = captor.getValue();
    assertThat(order.getIntentId()).isEqualTo(intent.getId());
    assertThat(order.getTenantId()).isEqualTo(tenantId);
    assertThat(order.getStatus()).isEqualTo(StatusCheckout.CONFIRMED);
    // `total` da order e em centavos do valor ja em reais: 399.80 * 100.
    assertThat(order.getTotal()).isEqualTo(39980L);
    assertThat(order.getValidUntil()).isAfter(antes.plus(29, ChronoUnit.DAYS));

    assertThat(response.status).isEqualTo("CONFIRMED");
    assertThat(response.validUntil).isEqualTo(order.getValidUntil().toString());
  }

  @Test
  void confirmarIntentJaConfirmadaEIdempotenteENaoCriaSegundoPedido() {
    CheckoutIntent intent = intentPendente();
    intent.setStatus(StatusCheckout.CONFIRMED);
    CheckoutOrder existente = new CheckoutOrder();
    existente.setId(UUID.randomUUID());
    existente.setValidUntil(Instant.parse("2027-01-01T00:00:00Z"));
    when(checkoutOrderRepository.buscarPorIntentId(eq(intent.getId())))
        .thenReturn(Optional.of(existente));

    CheckoutDtos.ConfirmIntentResponse response = service.confirmarIntent(intent.getId());

    assertThat(response.status).isEqualTo("CONFIRMED");
    assertThat(response.validUntil).isEqualTo("2027-01-01T00:00:00Z");
    verify(checkoutOrderRepository, never()).save(any());
  }

  @Test
  void confirmarIntentExpiradaMarcaExpiradaEResponde422() {
    CheckoutIntent intent = intentPendente();
    intent.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

    assertThatThrownBy(() -> service.confirmarIntent(intent.getId()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Intent expirada")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(422);

    // A intent fica marcada como EXPIRED mesmo com a excecao — o estado nao volta atras.
    assertThat(intent.getStatus()).isEqualTo(StatusCheckout.EXPIRED);
    assertThat(intent.getFailureReason()).isEqualTo("Intent expirada");
  }

  @Test
  void confirmarIntentEmStatusNaoPendenteResponde409() {
    CheckoutIntent intent = intentPendente();
    intent.setStatus(StatusCheckout.CANCELLED);

    assertThatThrownBy(() -> service.confirmarIntent(intent.getId()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Intent em status invalido para confirmacao")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(409);
  }

  @Test
  void confirmarIntentDeProdutoDesativadoMarcaFailedEResponde422() {
    CheckoutIntent intent = intentPendente();
    produto(false, false, false);

    assertThatThrownBy(() -> service.confirmarIntent(intent.getId()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Produto nao disponivel")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(422);

    assertThat(intent.getStatus()).isEqualTo(StatusCheckout.FAILED);
    assertThat(intent.getFailureReason())
        .isEqualTo("Produto inativo, inexistente ou indisponivel para checkout");
  }

  @Test
  void confirmarIntentDeOutroTenantResponde403() {
    CheckoutIntent intent = intentPendente();
    intent.setTenantId(UUID.randomUUID());

    assertThatThrownBy(() -> service.confirmarIntent(intent.getId()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Intent pertence a outro tenant")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(403);
  }

  @Test
  void confirmarIntentDeOutroUsuarioResponde403() {
    CheckoutIntent intent = intentPendente();
    intent.setUserId(UUID.randomUUID());

    assertThatThrownBy(() -> service.confirmarIntent(intent.getId()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Intent pertence a outro usuario");
  }

  @Test
  void confirmarIntentInexistenteResponde404() {
    UUID id = UUID.randomUUID();
    when(checkoutIntentRepository.findByIdForUpdate(eq(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirmarIntent(id))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Intent nao encontrada")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }
}
