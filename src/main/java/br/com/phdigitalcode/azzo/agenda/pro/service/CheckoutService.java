package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;

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
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import br.com.phdigitalcode.azzo.agenda.pro.util.JsonUtil;

/**
 * Espelha {@code modules/billing/application/CheckoutService.java}.
 *
 * <p>Mapeamento das excecoes JAX-RS do original, todas preservando o status HTTP via
 * {@link ApiClientErrorException} (tratada pelo {@code GlobalExceptionHandler}):
 * {@code NotFoundException} -> 404, {@code ForbiddenException} -> 403,
 * {@code ClientErrorException(422|400|409)} -> mesmo status.
 */
@Service
public class CheckoutService {

  private static final Logger LOG = LoggerFactory.getLogger(CheckoutService.class);

  static final StatusCheckout STATUS_PENDING = StatusCheckout.PENDING;
  static final StatusCheckout STATUS_CONFIRMED = StatusCheckout.CONFIRMED;
  static final StatusCheckout STATUS_EXPIRED = StatusCheckout.EXPIRED;
  static final StatusCheckout STATUS_FAILED = StatusCheckout.FAILED;

  private static final int MAX_QUANTITY = 100;

  private final ProductRepository productRepository;
  private final ProductCapabilityRepository productCapabilityRepository;
  private final CheckoutIntentRepository checkoutIntentRepository;
  private final CheckoutOrderRepository checkoutOrderRepository;
  private final AuthenticatedUser authenticatedUser;
  private final JsonUtil jsonUtil;
  private final ContextoTenant contextoTenant;

  public CheckoutService(
      ProductRepository productRepository,
      ProductCapabilityRepository productCapabilityRepository,
      CheckoutIntentRepository checkoutIntentRepository,
      CheckoutOrderRepository checkoutOrderRepository,
      AuthenticatedUser authenticatedUser,
      JsonUtil jsonUtil,
      ContextoTenant contextoTenant) {
    this.productRepository = productRepository;
    this.productCapabilityRepository = productCapabilityRepository;
    this.checkoutIntentRepository = checkoutIntentRepository;
    this.checkoutOrderRepository = checkoutOrderRepository;
    this.authenticatedUser = authenticatedUser;
    this.jsonUtil = jsonUtil;
    this.contextoTenant = contextoTenant;
  }

  /** Planos exclusivos de venda interna nao aparecem na contratacao publica. */
  @Transactional(readOnly = true)
  public List<CheckoutDtos.ProductResponse> listarProdutos() {
    return productRepository.listarContrataveisPublicamente().stream()
        .map(this::toProductResponse)
        .toList();
  }

  /**
   * Regra unica de elegibilidade para contratacao publica: plano ativo, nao-trial e NAO exclusivo
   * de venda interna.
   */
  boolean isContratavelPublicamente(Product product) {
    return product != null
        && product.isActive()
        && !product.isTrial()
        && !product.isExclusivoVendaInterna();
  }

  /**
   * Lista planos exclusivos de venda interna (ativos, nao-trial). Uso exclusivo do fluxo interno
   * autorizado (api-gerenciamento) via endpoint protegido por chave interna.
   */
  @Transactional(readOnly = true)
  public List<CheckoutDtos.ProductResponse> listarPlanosVendaInterna() {
    return productRepository.listarExclusivosVendaInterna().stream()
        .map(this::toProductResponse)
        .toList();
  }

  /**
   * Todos os planos ativos e nao-trial (gerais + exclusivos), com {@code exclusivoVendaInterna}
   * indicando o tipo. Uso exclusivo do fluxo interno autorizado.
   */
  @Transactional(readOnly = true)
  public List<CheckoutDtos.ProductResponse> listarTodosPlanos() {
    return productRepository.listarTodosAtivosNaoTrial().stream()
        .map(this::toProductResponse)
        .toList();
  }

  @Transactional
  public CheckoutDtos.CreateIntentResponse criarIntent(CheckoutDtos.CreateIntentRequest request) {
    validateQuantity(request.quantity);
    UUID tenantId = obterTenantIdClaim();
    UUID userId = obterUserIdClaim();
    validarContextoAutenticado(tenantId, userId);

    UUID productId = parseUuid(request.productId, "productId invalido");
    Product product = productRepository.findById(productId).orElse(null);
    // Garantia de backend: so planos publicamente contrataveis passam. Planos exclusivos de venda
    // interna sao tratados como inexistentes no checkout publico, mesmo que o frontend envie o
    // productId.
    if (!isContratavelPublicamente(product)) {
      throw new ApiClientErrorException("Produto nao encontrado", 404);
    }

    BigDecimal totalCents = calcularTotal(product.getPriceCents(), request.quantity);

    CheckoutIntent intent = new CheckoutIntent();
    intent.setTenantId(tenantId);
    intent.setUserId(userId);
    intent.setProductId(product.getId());
    intent.setProductNameSnapshot(product.getName());
    intent.setCurrencySnapshot(product.getCurrency());
    intent.setCurrency(product.getCurrency());
    intent.setUnitPriceSnapshot(product.getPriceCents());
    intent.setQuantity(request.quantity);
    intent.setTotalPriceSnapshot(totalCents);
    intent.setCalculatedTotal(totalCents);
    intent.setStatus(STATUS_PENDING);
    intent.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
    checkoutIntentRepository.save(intent);

    LOG.info(CorrelatedLogging.context(
        "Checkout intent criada",
        "intentId", intent.getId(),
        "productId", intent.getProductId(),
        "tenantId", intent.getTenantId(),
        "userId", intent.getUserId(),
        "total", totalCents));

    return toCreateIntentResponse(intent);
  }

  @Transactional
  public CheckoutDtos.ConfirmIntentResponse confirmarIntent(UUID intentId) {
    CheckoutIntent intent =
        checkoutIntentRepository
            .findByIdForUpdate(intentId)
            .orElseThrow(() -> new ApiClientErrorException("Intent nao encontrada", 404));

    validarOwnershipTenant(intent);

    if (STATUS_CONFIRMED.equals(intent.getStatus())) {
      // Idempotente: reconfirmar devolve o pedido ja criado em vez de criar um segundo.
      CheckoutOrder existingOrder =
          checkoutOrderRepository.buscarPorIntentId(intent.getId()).orElse(null);
      return toConfirmIntentResponse(intent.getId(), STATUS_CONFIRMED.name(), existingOrder);
    }

    if (isExpired(intent)) {
      intent.setStatus(STATUS_EXPIRED);
      intent.setFailureReason("Intent expirada");
      LOG.warn(CorrelatedLogging.context("Checkout intent expirada", "intentId", intent.getId()));
      throw new ApiClientErrorException("Intent expirada", 422);
    }

    if (!STATUS_PENDING.equals(intent.getStatus())) {
      throw new ApiClientErrorException("Intent em status invalido para confirmacao", 409);
    }

    Product product = productRepository.findById(intent.getProductId()).orElse(null);
    if (product == null || !product.isActive() || product.isTrial()) {
      intent.setStatus(STATUS_FAILED);
      intent.setFailureReason("Produto inativo, inexistente ou indisponivel para checkout");
      LOG.warn(CorrelatedLogging.context(
          "Falha na confirmacao de checkout",
          "intentId", intent.getId(),
          "tenantId", intent.getTenantId(),
          "productId", intent.getProductId(),
          "reason", "produto_indisponivel"));
      throw new ApiClientErrorException("Produto nao disponivel", 422);
    }

    // O preco e recalculado na confirmacao: o snapshot da criacao nao vale se o catalogo mudou.
    BigDecimal recalculatedTotal = calcularTotal(product.getPriceCents(), intent.getQuantity());
    intent.setCurrency(product.getCurrency());
    intent.setUnitPriceSnapshot(product.getPriceCents());
    intent.setTotalPriceSnapshot(recalculatedTotal);
    intent.setCalculatedTotal(recalculatedTotal);

    try {
      String paymentReference = processarPagamentoStub(intent, recalculatedTotal);
      intent.setPaymentReference(paymentReference);
      intent.setStatus(STATUS_CONFIRMED);
      Instant confirmedAt = Instant.now();
      intent.setConfirmedAt(confirmedAt);

      CheckoutOrder order = new CheckoutOrder();
      order.setIntentId(intent.getId());
      order.setProductId(intent.getProductId());
      order.setTenantId(intent.getTenantId());
      order.setUserId(intent.getUserId());
      order.setTotal(recalculatedTotal.multiply(BigDecimal.valueOf(100)).longValue());
      order.setStatus(STATUS_CONFIRMED);
      order.setValidUntil(calcularValidade(product, confirmedAt));
      checkoutOrderRepository.save(order);

      LOG.info(CorrelatedLogging.context(
          "Checkout intent confirmada",
          "intentId", intent.getId(),
          "tenantId", intent.getTenantId(),
          "orderId", order.getId(),
          "paymentReference", paymentReference));
      return toConfirmIntentResponse(intent.getId(), STATUS_CONFIRMED.name(), order);
    } catch (RuntimeException e) {
      intent.setStatus(STATUS_FAILED);
      intent.setFailureReason("Falha no processamento de pagamento");
      LOG.error(CorrelatedLogging.context(
          "Falha ao confirmar checkout",
          "intentId", intent.getId(),
          "tenantId", intent.getTenantId(),
          "productId", intent.getProductId(),
          "root", CorrelatedLogging.throwableSummary(e)), e);
      throw new ApiClientErrorException("Falha ao processar checkout", 422);
    }
  }

  BigDecimal calcularTotal(BigDecimal unitPriceCents, int quantity) {
    try {
      return unitPriceCents.multiply(BigDecimal.valueOf(quantity));
    } catch (ArithmeticException e) {
      throw new ApiClientErrorException("Total invalido", 422);
    }
  }

  boolean isExpired(CheckoutIntent intent) {
    return intent.getExpiresAt() != null && intent.getExpiresAt().isBefore(Instant.now());
  }

  private void validateQuantity(int quantity) {
    if (quantity < 1 || quantity > MAX_QUANTITY) {
      throw new ApiClientErrorException("Quantidade fora do limite permitido", 400);
    }
  }

  private void validarOwnershipTenant(CheckoutIntent intent) {
    UUID tenantId = obterTenantIdClaim();
    UUID userId = obterUserIdClaim();
    validarContextoAutenticado(tenantId, userId);

    if (intent.getTenantId() != null
        && (tenantId == null || !intent.getTenantId().equals(tenantId))) {
      throw new ApiClientErrorException("Intent pertence a outro tenant", 403);
    }
    if (intent.getUserId() != null && (userId == null || !intent.getUserId().equals(userId))) {
      throw new ApiClientErrorException("Intent pertence a outro usuario", 403);
    }
  }

  private String processarPagamentoStub(CheckoutIntent intent, BigDecimal totalCents) {
    // Stub pronto para troca por gateway real (mesmo do original).
    return "stub-pay-" + intent.getId() + "-" + totalCents.longValue();
  }

  private CheckoutDtos.ProductResponse toProductResponse(Product product) {
    CheckoutDtos.ProductResponse response = new CheckoutDtos.ProductResponse();
    ProductCapability capability =
        productCapabilityRepository.findById(product.getId()).orElse(null);
    response.id = product.getId().toString();
    response.name = product.getName();
    response.description = product.getDescription();
    response.currency = product.getCurrency();
    response.price = product.getPriceCents() != null ? product.getPriceCents().doubleValue() : 0.0;
    response.validityDays = resolveValidityDays(product);
    response.validityMonths = product.getValidityMonths();
    response.highlight = product.getHighlight();
    response.maxProfessionals = capability != null ? capability.getMaxProfessionals() : null;
    response.exclusivoVendaInterna = product.isExclusivoVendaInterna();
    response.features =
        jsonUtil.deJsonLista(product.getFeaturesJson(), new TypeReference<List<String>>() {});
    return response;
  }

  private CheckoutDtos.CreateIntentResponse toCreateIntentResponse(CheckoutIntent intent) {
    CheckoutDtos.CreateIntentResponse response = new CheckoutDtos.CreateIntentResponse();
    response.intentId = intent.getId().toString();
    response.productId = intent.getProductId().toString();
    response.productName = intent.getProductNameSnapshot();
    response.quantity = intent.getQuantity();
    response.currency = intent.getCurrencySnapshot();
    response.unitPrice =
        intent.getUnitPriceSnapshot() != null ? intent.getUnitPriceSnapshot().doubleValue() : 0.0;
    response.totalPrice =
        intent.getTotalPriceSnapshot() != null ? intent.getTotalPriceSnapshot().doubleValue() : 0.0;
    response.status = intent.getStatus() != null ? intent.getStatus().name() : null;
    response.expiresAt = intent.getExpiresAt() != null ? intent.getExpiresAt().toString() : null;
    return response;
  }

  private CheckoutDtos.ConfirmIntentResponse toConfirmIntentResponse(
      UUID intentId, String status, CheckoutOrder order) {
    CheckoutDtos.ConfirmIntentResponse response = new CheckoutDtos.ConfirmIntentResponse();
    response.intentId = intentId.toString();
    response.status = status;
    response.validUntil =
        order != null && order.getValidUntil() != null ? order.getValidUntil().toString() : null;
    response.redirectUrl = null;
    return response;
  }

  private Instant calcularValidade(Product product, Instant confirmedAt) {
    int days = resolveValidityDays(product);
    return confirmedAt.atZone(ZoneOffset.UTC).plusDays(days).toInstant();
  }

  private int resolveValidityDays(Product product) {
    if (product != null && product.getValidityDays() != null && product.getValidityDays() > 0) {
      return product.getValidityDays();
    }
    int months = product != null && product.getValidityMonths() > 0 ? product.getValidityMonths() : 1;
    return months * 30;
  }

  /**
   * O original le a claim {@code tenant_id} (com fallback para {@code tid}) direto do
   * {@code JsonWebToken} e, se ambas faltarem, cai no {@code ContextoTenant}. Aqui o
   * {@code ContextoTenant} ja resolve {@code tenant_id}/{@code tid}/{@code X-Tenant-Id} — a
   * cascata equivalente e: contexto de tenant, senao null.
   */
  private UUID obterTenantIdClaim() {
    try {
      return contextoTenant.obterTenantIdOuFalhar();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private UUID obterUserIdClaim() {
    return authenticatedUser.idOuNulo();
  }

  private void validarContextoAutenticado(UUID tenantId, UUID userId) {
    if (tenantId == null || userId == null) {
      LOG.warn(CorrelatedLogging.context(
          "Checkout recusado",
          "tenantId", tenantId,
          "userId", userId,
          "reason", "invalid_auth_context"));
      throw new ApiClientErrorException(
          "Checkout requer usuario autenticado com tenant valido", 403);
    }
  }

  private UUID parseUuid(String value, String message) {
    try {
      return UUID.fromString(value);
    } catch (Exception e) {
      throw new ApiClientErrorException(message, 400);
    }
  }
}
