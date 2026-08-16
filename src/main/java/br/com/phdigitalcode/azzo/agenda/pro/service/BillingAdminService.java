package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutIntent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LicenseEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Payment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Subscription;
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
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Espelha {@code modules/billing/application/BillingAdminService.java} — a face administrativa do
 * billing ({@code ADMIN}): forcar vencimento, liberar licenca, marcar pagamento como recebido,
 * estender trial, listar tenants/pagamentos e os dois relatorios.
 *
 * <p>Decisoes de porte:
 *
 * <ul>
 *   <li>{@code jakarta.ws.rs.NotFoundException} e {@code WebApplicationException(msg, 404)} viram
 *       {@link ApiClientErrorException} com status 404 — mesmo codigo HTTP na resposta.
 *   <li>O original altera entidades gerenciadas e confia no dirty checking do Panache. Aqui as
 *       alteracoes em lote sao gravadas com {@code saveAll}/{@code save} explicitos: o efeito no
 *       banco e o mesmo (a transacao ja esta aberta) e fica verificavel em teste unitario.
 *   <li>{@link #markPaymentAsReceived} chama {@link #releaseLicenseForCurrentTenant} <b>na propria
 *       classe</b>. Sob Spring essa auto-invocacao nao passa pelo proxy, entao o
 *       {@code @Transactional} do metodo interno e ignorado — e isso <b>nao</b> muda o resultado,
 *       porque os dois sao {@code REQUIRED} e o original (CDI) tambem reaproveitaria a transacao
 *       corrente. Nao ha {@code REQUIRES_NEW} envolvido.
 *   <li>As consultas Panache viraram metodos nomeados nos repositorios
 *       ({@code listarNaoVencidosAte}, {@code listarVivasPorStatus},
 *       {@code listarConfirmadosMaisRecentesPrimeiro},
 *       {@code listarAtivosNaoTrialMaisRecentesPrimeiro}), preservando filtros e ordenacoes.
 * </ul>
 *
 * <p><b>⚠️ DEFEITO PRESERVADO DO ORIGINAL — {@link #relatorioLicencas}.</b> O SQL nativo consulta
 * {@code FROM checkout_orders} e seleciona {@code co.billing_type}. Nenhum dos dois existe: a
 * tabela do {@code CheckoutOrder} chama-se {@code orders} (ver {@code @Table} da entidade e
 * {@code V1__baseline_unified.sql}) e {@code orders} nao tem coluna {@code billing_type}. O
 * endpoint {@code GET /api/v1/billing/admin/reports/licencas} portanto <b>falha em producao
 * hoje</b>, no Quarkus, com erro de SQL. O SQL foi portado <b>verbatim</b> para manter paridade —
 * corrigir exigiria decidir de onde tirar o {@code billing_type}, o que e mudanca de produto, nao
 * de migracao. A montagem/contagem do relatorio (que roda sobre o resultado) esta portada e
 * testada.
 */
@Service
public class BillingAdminService {

  private final ContextoTenant contextoTenant;
  private final CheckoutOrderRepository checkoutOrderRepository;
  private final CheckoutIntentRepository checkoutIntentRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PaymentRepository paymentRepository;
  private final ProductRepository productRepository;
  private final TenantRepository tenantRepository;
  private final LicenseStatusService licenseStatusService;
  private final LicenseEventRepository licenseEventRepository;

  @PersistenceContext private EntityManager entityManager;

  public BillingAdminService(
      ContextoTenant contextoTenant,
      CheckoutOrderRepository checkoutOrderRepository,
      CheckoutIntentRepository checkoutIntentRepository,
      SubscriptionRepository subscriptionRepository,
      PaymentRepository paymentRepository,
      ProductRepository productRepository,
      TenantRepository tenantRepository,
      LicenseStatusService licenseStatusService,
      LicenseEventRepository licenseEventRepository) {
    this.contextoTenant = contextoTenant;
    this.checkoutOrderRepository = checkoutOrderRepository;
    this.checkoutIntentRepository = checkoutIntentRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.paymentRepository = paymentRepository;
    this.productRepository = productRepository;
    this.tenantRepository = tenantRepository;
    this.licenseStatusService = licenseStatusService;
    this.licenseEventRepository = licenseEventRepository;
  }

  /** Lista <b>todos</b> os tenants (o nome "active" e do original; nao ha filtro de status). */
  @Transactional(readOnly = true)
  public BillingDtos.AdminTenantListResponse listActiveTenants() {
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT t.id, t.name, t.slug, t.email, t.phone, ps.code
                FROM tenants t
                JOIN plan_status ps ON ps.id = t.plan_status_id
                ORDER BY t.name ASC
                """)
            .getResultList();

    BillingDtos.AdminTenantListResponse response = new BillingDtos.AdminTenantListResponse();
    response.items = new ArrayList<>();
    for (Object[] row : rows) {
      BillingDtos.AdminTenantItemResponse item = new BillingDtos.AdminTenantItemResponse();
      item.tenantId = row[0] != null ? row[0].toString() : null;
      item.name = row[1] != null ? row[1].toString() : null;
      item.slug = row[2] != null ? row[2].toString() : null;
      item.email = row[3] != null ? row[3].toString() : null;
      item.phone = row[4] != null ? row[4].toString() : null;
      item.planStatus = row[5] != null ? row[5].toString() : null;
      response.items.add(item);
    }
    return response;
  }

  @Transactional(readOnly = true)
  public BillingDtos.PaymentsListResponse listPaymentsByTenant(String tenantId) {
    UUID targetTenantId = resolveTargetTenantId(tenantId);
    BillingDtos.PaymentsListResponse response = new BillingDtos.PaymentsListResponse();
    response.items =
        paymentRepository.findByTenantIdOrderByCreatedAtDesc(targetTenantId).stream()
            .map(this::toPaymentItemResponse)
            .toList();
    return response;
  }

  /**
   * Empurra o {@code validUntil} dos pedidos confirmados para o passado e derruba as assinaturas
   * vivas para {@code OVERDUE}. Sem {@code minutesAgo} valido, usa 5 minutos.
   */
  @Transactional
  public BillingDtos.AdminBillingActionResponse forceExpireCurrentTenant(
      String tenantId, Integer minutesAgo) {
    UUID targetTenantId = resolveTargetTenantId(tenantId);
    int delta = minutesAgo != null && minutesAgo > 0 ? minutesAgo : 5;
    Instant expiredAt = Instant.now().minus(delta, ChronoUnit.MINUTES);

    List<CheckoutOrder> vigentes =
        checkoutOrderRepository.listarNaoVencidosAte(
            targetTenantId, StatusCheckout.CONFIRMED, expiredAt);
    vigentes.forEach(order -> order.setValidUntil(expiredAt));
    checkoutOrderRepository.saveAll(vigentes);

    List<Subscription> vivas =
        subscriptionRepository.listarVivasPorStatus(
            targetTenantId, StatusSubscription.ACTIVE, StatusSubscription.PENDING);
    vivas.forEach(sub -> sub.setStatus(StatusSubscription.OVERDUE));
    subscriptionRepository.saveAll(vivas);

    var license = licenseStatusService.avaliar(targetTenantId);
    BillingDtos.AdminBillingActionResponse response = new BillingDtos.AdminBillingActionResponse();
    response.status = "OK";
    response.tenantId = targetTenantId.toString();
    response.licenseStatus = license.planStatus().name();
    response.message = "Plano marcado como vencido para testes.";
    return response;
  }

  /**
   * Cria um par {@code CheckoutIntent} + {@code CheckoutOrder} ja confirmados, com validade
   * calculada a partir do produto (ou do {@code validityDays} informado), e reativa as assinaturas
   * {@code OVERDUE} do tenant.
   *
   * <p>O {@code intent} e gravado com {@code saveAndFlush} porque o {@code order} referencia
   * {@code intent.id} (FK {@code fk_orders_intent}) — no Panache o {@code persist} ja emitia o
   * INSERT; no Spring Data o flush precisa ser explicito (armadilha 3 do inventario).
   */
  @Transactional
  public BillingDtos.AdminBillingActionResponse releaseLicenseForCurrentTenant(
      String tenantId,
      String explicitProductId,
      Integer validityDays,
      String paymentIdToMarkReceived) {
    UUID targetTenantId = resolveTargetTenantId(tenantId);
    Product product = resolveProduct(targetTenantId, explicitProductId);
    if (product == null) {
      throw new IllegalArgumentException("Produto nao encontrado para liberar licenca.");
    }

    if (paymentIdToMarkReceived != null && !paymentIdToMarkReceived.isBlank()) {
      Payment payment = findPaymentByAnyId(targetTenantId, paymentIdToMarkReceived);
      if (payment != null) {
        payment.setStatus(StatusPayment.RECEIVED);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);
        responseActivateSubscriptionIfAny(payment);
      }
    }

    int days =
        validityDays != null && validityDays > 0
            ? validityDays
            : resolveProductValidityDays(product);
    Instant now = Instant.now();
    Instant validUntil = now.plus(days, ChronoUnit.DAYS);

    CheckoutIntent intent = new CheckoutIntent();
    intent.setTenantId(targetTenantId);
    intent.setUserId(null);
    intent.setProductId(product.getId());
    intent.setProductNameSnapshot(product.getName());
    intent.setCurrencySnapshot(product.getCurrency() != null ? product.getCurrency() : "BRL");
    intent.setCurrency(product.getCurrency() != null ? product.getCurrency() : "BRL");
    intent.setUnitPriceSnapshot(product.getPriceCents());
    intent.setQuantity(1);
    intent.setTotalPriceSnapshot(product.getPriceCents());
    intent.setCalculatedTotal(product.getPriceCents());
    intent.setStatus(StatusCheckout.CONFIRMED);
    intent.setExpiresAt(validUntil);
    intent.setPaymentReference("admin-release-" + now.toEpochMilli());
    intent.setConfirmedAt(now);
    CheckoutIntent intentGravada = checkoutIntentRepository.saveAndFlush(intent);

    CheckoutOrder order = new CheckoutOrder();
    order.setIntentId(intentGravada.getId());
    order.setProductId(product.getId());
    order.setTenantId(targetTenantId);
    order.setUserId(null);
    order.setTotal(NumericUtil.toCents(product.getPriceCents()));
    order.setStatus(StatusCheckout.CONFIRMED);
    order.setValidUntil(validUntil);
    checkoutOrderRepository.saveAndFlush(order);

    List<Subscription> atrasadas =
        subscriptionRepository.findByTenantIdAndStatus(targetTenantId, StatusSubscription.OVERDUE);
    atrasadas.forEach(sub -> sub.setStatus(StatusSubscription.ACTIVE));
    subscriptionRepository.saveAll(atrasadas);

    var license = licenseStatusService.avaliar(targetTenantId);
    BillingDtos.AdminBillingActionResponse response = new BillingDtos.AdminBillingActionResponse();
    response.status = "OK";
    response.tenantId = targetTenantId.toString();
    response.licenseStatus = license.planStatus().name();
    response.message = "Licenca liberada manualmente para testes.";
    response.productId = product.getId().toString();
    response.validUntil = validUntil.toString();
    return response;
  }

  /**
   * Marca o pagamento como recebido e, quando consegue resolver o produto, libera a licenca em
   * seguida (o que cria um novo pedido confirmado). Sem produto resolvido, so o pagamento muda.
   */
  @Transactional
  public BillingDtos.AdminBillingActionResponse markPaymentAsReceived(
      String tenantId, String paymentId, Integer validityDays) {
    UUID targetTenantId = resolveTargetTenantId(tenantId);
    Payment payment = findPaymentByAnyId(targetTenantId, paymentId);
    if (payment == null) throw new IllegalArgumentException("Pagamento nao encontrado.");

    payment.setStatus(StatusPayment.RECEIVED);
    payment.setPaidAt(Instant.now());
    paymentRepository.save(payment);
    responseActivateSubscriptionIfAny(payment);

    Product product = resolveProductForPayment(targetTenantId, payment);
    if (product != null) {
      releaseLicenseForCurrentTenant(
          targetTenantId.toString(),
          product.getId().toString(),
          validityDays,
          payment.getAsaasPaymentId());
    }

    var license = licenseStatusService.avaliar(targetTenantId);
    BillingDtos.AdminBillingActionResponse response = new BillingDtos.AdminBillingActionResponse();
    response.status = "OK";
    response.tenantId = targetTenantId.toString();
    response.licenseStatus = license.planStatus().name();
    response.message = "Pagamento marcado como recebido.";
    response.paymentId = payment.getAsaasPaymentId();
    response.productId = product != null ? product.getId().toString() : null;
    return response;
  }

  /**
   * Cascata do original: {@code productId} explicito -> produto da assinatura vigente -> produto do
   * pedido confirmado mais recente -> qualquer plano pago ativo (maior prioridade, mais novo).
   */
  private Product resolveProduct(UUID tenantId, String explicitProductId) {
    UUID productId = parseUuidOrNull(explicitProductId);
    if (productId != null) {
      Product explicit = productRepository.findById(productId).orElse(null);
      if (explicit != null) return explicit;
    }
    Subscription subscription = subscriptionRepository.findCurrentByTenantId(tenantId).orElse(null);
    if (subscription != null) {
      UUID subProductId =
          subscription.getProductId() != null
              ? subscription.getProductId()
              : parseUuidOrNull(subscription.getPlanCode());
      if (subProductId != null) {
        Product fromSub = productRepository.findById(subProductId).orElse(null);
        if (fromSub != null) return fromSub;
      }
    }
    CheckoutOrder order =
        checkoutOrderRepository
            .listarConfirmadosMaisRecentesPrimeiro(tenantId, StatusCheckout.CONFIRMED)
            .stream()
            .findFirst()
            .orElse(null);
    if (order != null && order.getProductId() != null) {
      Product fromOrder = productRepository.findById(order.getProductId()).orElse(null);
      if (fromOrder != null) return fromOrder;
    }
    return productRepository.listarAtivosNaoTrialMaisRecentesPrimeiro().stream()
        .findFirst()
        .orElse(null);
  }

  private Product resolveProductForPayment(UUID tenantId, Payment payment) {
    if (payment == null) return null;
    if (payment.getSubscriptionId() != null) {
      Subscription subscription =
          subscriptionRepository.findById(payment.getSubscriptionId()).orElse(null);
      if (subscription != null) {
        UUID productId =
            subscription.getProductId() != null
                ? subscription.getProductId()
                : parseUuidOrNull(subscription.getPlanCode());
        if (productId != null) {
          Product product = productRepository.findById(productId).orElse(null);
          if (product != null) return product;
        }
      }
    }
    return resolveProduct(tenantId, null);
  }

  /** Nome do original preservado. Reativa a assinatura Asaas ligada ao pagamento, se houver. */
  private void responseActivateSubscriptionIfAny(Payment payment) {
    if (payment == null
        || payment.getAsaasSubscriptionId() == null
        || payment.getAsaasSubscriptionId().isBlank()) {
      return;
    }
    subscriptionRepository
        .buscarPorAsaasSubscriptionId(payment.getAsaasSubscriptionId())
        .ifPresent(
            sub -> {
              sub.setStatus(StatusSubscription.ACTIVE);
              sub.setCancelledAt(null);
              subscriptionRepository.save(sub);
            });
  }

  /**
   * Aceita o id do Asaas ou o id interno, sempre confinado ao tenant alvo — pagamento de outro
   * tenant devolve {@code null} (nao vaza entre tenants).
   */
  private Payment findPaymentByAnyId(UUID tenantId, String paymentId) {
    if (paymentId == null || paymentId.isBlank()) return null;
    Payment byAsaas = paymentRepository.buscarPorAsaasPaymentId(paymentId.trim()).orElse(null);
    if (byAsaas != null && tenantId.equals(byAsaas.getTenantId())) return byAsaas;
    UUID internalId = parseUuidOrNull(paymentId);
    if (internalId == null) return null;
    Payment byId = paymentRepository.findById(internalId).orElse(null);
    if (byId != null && tenantId.equals(byId.getTenantId())) return byId;
    return null;
  }

  private UUID parseUuidOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  /** {@code validityDays} tem precedencia; senao {@code validityMonths * 30} (minimo 1 mes). */
  private int resolveProductValidityDays(Product product) {
    if (product != null && product.getValidityDays() != null && product.getValidityDays() > 0) {
      return product.getValidityDays();
    }
    int months = product != null && product.getValidityMonths() > 0 ? product.getValidityMonths() : 1;
    return months * 30;
  }

  /**
   * Sem {@code tenantId} no corpo, age sobre o tenant do token. Com {@code tenantId}, exige UUID
   * valido (400) e tenant existente (404).
   */
  private UUID resolveTargetTenantId(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return contextoTenant.obterTenantIdOuFalhar();
    }
    UUID parsed;
    try {
      parsed = UUID.fromString(tenantId.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("tenantId invalido.");
    }
    if (tenantRepository.findById(parsed).isEmpty()) {
      throw new ApiClientErrorException("Tenant nao encontrado.", 404);
    }
    return parsed;
  }

  /**
   * Estende o trial vigente. Diferente das demais rotas admin, {@code tenantId} e
   * <b>obrigatorio</b> e nao cai para o tenant do token — e nao passa por
   * {@code resolveTargetTenantId}, entao um UUID bem formado de tenant inexistente resulta em 404
   * de "trial nao encontrado", nao de "tenant nao encontrado". Assimetria do original.
   */
  @Transactional
  public BillingDtos.ExtendTrialResponse extenderTrial(String tenantIdStr, Integer extraDays) {
    if (tenantIdStr == null || tenantIdStr.isBlank()) {
      throw new IllegalArgumentException("tenantId obrigatorio");
    }
    if (extraDays == null || extraDays < 1) {
      throw new IllegalArgumentException("extraDays deve ser >= 1");
    }
    UUID tenantId = UUID.fromString(tenantIdStr.trim());

    CheckoutOrder trialOrder =
        checkoutOrderRepository
            .listarConfirmadosMaisRecentesPrimeiro(tenantId, StatusCheckout.CONFIRMED)
            .stream()
            .filter(
                o -> {
                  if (o.getProductId() == null) return false;
                  Product p = productRepository.findById(o.getProductId()).orElse(null);
                  return p != null && p.isTrial();
                })
            .findFirst()
            .orElseThrow(
                () ->
                    new ApiClientErrorException(
                        "Nenhum trial ativo encontrado para o tenant.", 404));

    Instant novaValidade =
        (trialOrder.getValidUntil() != null ? trialOrder.getValidUntil() : Instant.now())
            .plus(extraDays, ChronoUnit.DAYS);
    trialOrder.setValidUntil(novaValidade);
    checkoutOrderRepository.save(trialOrder);

    licenseStatusService.avaliar(tenantId);

    licenseEventRepository.save(
        LicenseEvent.of(tenantId, "TRIAL_EXTENDED", null, trialOrder.getProductId(), novaValidade));

    BillingDtos.ExtendTrialResponse response = new BillingDtos.ExtendTrialResponse();
    response.tenantId = tenantIdStr;
    response.validUntil = novaValidade.toString();
    response.message = "Trial estendido por " + extraDays + " dia(s).";
    return response;
  }

  /** {@code tenantId} malformado sobe {@code IllegalArgumentException} (400), como no original. */
  @Transactional(readOnly = true)
  public BillingDtos.LicenseHistoryResponse listarHistoricoLicenca(String tenantIdStr) {
    UUID tenantId = UUID.fromString(tenantIdStr.trim());
    BillingDtos.LicenseHistoryResponse response = new BillingDtos.LicenseHistoryResponse();
    response.tenantId = tenantIdStr;
    response.events =
        licenseEventRepository.findByTenant(tenantId).stream()
            .map(
                e -> {
                  BillingDtos.LicenseEventResponse r = new BillingDtos.LicenseEventResponse();
                  r.id = e.getId() != null ? e.getId().toString() : null;
                  r.tenantId = e.getTenantId() != null ? e.getTenantId().toString() : null;
                  r.eventType = e.getEventType();
                  r.actorUserId =
                      e.getActorUserId() != null ? e.getActorUserId().toString() : null;
                  r.productId = e.getProductId() != null ? e.getProductId().toString() : null;
                  r.validUntil = e.getValidUntil() != null ? e.getValidUntil().toString() : null;
                  r.metadata = e.getMetadata();
                  r.createdAt = e.getCreatedAt() != null ? e.getCreatedAt().toString() : null;
                  return r;
                })
            .toList();
    return response;
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

  /**
   * Relatorio de licencas por tenant.
   *
   * <p><b>O SQL abaixo e um porte verbatim e esta quebrado no original</b> — ver o aviso no javadoc
   * da classe ({@code checkout_orders} nao existe; a tabela e {@code orders}, que tambem nao tem
   * {@code billing_type}). Mantido como esta para nao divergir do comportamento atual.
   *
   * <p>A classificacao dos contadores tem duas particularidades do original, preservadas: os ramos
   * sao <b>exclusivos</b> ({@code else if}), entao um status {@code TRIAL_EXPIRED} conta como
   * trial e nao como expirado; e {@code vencido} so promove a "expirado" quando o status nao caiu
   * em nenhum ramo anterior.
   */
  @Transactional(readOnly = true)
  public BillingDtos.LicencasAdminReportResponse relatorioLicencas() {
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  t.id::text,
                  t.name,
                  t.email,
                  ps.code AS plan_status,
                  co.billing_type,
                  co.valid_until::text,
                  COALESCE((co.valid_until::date - CURRENT_DATE)::int, -1) AS dias_restantes,
                  CASE WHEN co.valid_until IS NOT NULL AND co.valid_until < NOW() THEN true ELSE false END AS vencido,
                  t.created_at::text
                FROM tenants t
                JOIN plan_status ps ON ps.id = t.plan_status_id
                LEFT JOIN LATERAL (
                  SELECT o.billing_type, o.valid_until
                  FROM checkout_orders o
                  WHERE o.tenant_id = t.id
                  ORDER BY o.created_at DESC
                  LIMIT 1
                ) co ON true
                ORDER BY co.valid_until ASC NULLS LAST
                """)
            .getResultList();

    BillingDtos.LicencasAdminReportResponse response =
        new BillingDtos.LicencasAdminReportResponse();
    response.items = new ArrayList<>();

    for (Object[] row : rows) {
      BillingDtos.LicencasAdminReportItem item = new BillingDtos.LicencasAdminReportItem();
      item.tenantId = row[0] != null ? row[0].toString() : null;
      item.tenantNome = row[1] != null ? row[1].toString() : null;
      item.tenantEmail = row[2] != null ? row[2].toString() : null;
      item.planStatus = row[3] != null ? row[3].toString() : null;
      item.billingType = row[4] != null ? row[4].toString() : null;
      item.validUntil = row[5] != null ? row[5].toString() : null;
      item.diasRestantes = row[6] != null ? ((Number) row[6]).intValue() : -1;
      item.vencido = Boolean.TRUE.equals(row[7]);
      item.createdAt = row[8] != null ? row[8].toString() : null;
      response.items.add(item);

      String status = item.planStatus != null ? item.planStatus : "";
      if (status.startsWith("TRIAL")) response.trialCount++;
      else if (status.contains("ACTIVE")) response.ativosCount++;
      else if (status.contains("EXPIRED") || item.vencido) response.expiradosCount++;
      else if (status.contains("BLOCKED")) response.bloqueadosCount++;
    }

    response.totalTenants = response.items.size();
    return response;
  }
}
