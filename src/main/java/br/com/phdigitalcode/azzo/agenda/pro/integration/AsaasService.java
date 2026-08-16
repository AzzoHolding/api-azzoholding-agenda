package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
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
import br.com.phdigitalcode.azzo.agenda.pro.exception.AsaasException;
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
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import br.com.phdigitalcode.azzo.agenda.pro.util.IntegrationLogSanitizer;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;

/**
 * Espelha {@code infrastructure/payment/AsaasService.java}: assinatura SaaS da PLATAFORMA (cobra o
 * tenant) e o processamento do webhook do Asaas. Distinto de {@link TenantAsaasChargeService}, que
 * usa a conta Asaas do proprio salao para cobrar o cliente final.
 *
 * <p>Decisoes de porte:
 *
 * <ul>
 *   <li>{@code WebApplicationException} (JAX-RS) da chamada HTTP vira
 *       {@link RestClientResponseException}; o corpo do erro sai de
 *       {@code getResponseBodyAsString()} no lugar de {@code response.readEntity(String.class)}.
 *   <li>O {@code WebApplicationException(..., UNAUTHORIZED)} lancado por
 *       {@code validateWebhookToken} vira {@link ApiClientErrorException} com status 401 — mesmo
 *       codigo HTTP na resposta, via {@code GlobalExceptionHandler}.
 *   <li>{@code ensureCustomerForTenant} e {@code @Transactional} no original e e chamado de dentro
 *       de {@code createMonthlySubscriptionForCurrentTenant}. Em Spring a auto-invocacao nao passa
 *       pelo proxy, mas o efeito e o mesmo: a chamada externa ja abriu transacao e o original usa
 *       {@code REQUIRED}, entao os dois casos rodam na mesma transacao.
 *   <li>Entidades lidas por repositorio ficam <b>managed</b> dentro da transacao — as mutacoes de
 *       status de {@code Subscription}/{@code Payment} sao gravadas no commit por dirty checking,
 *       igual ao Panache.
 *   <li>Quando o processamento do webhook falha, o {@code WebhookEventLog} com a mensagem de erro e
 *       perdido no rollback — <b>assimetria do original preservada</b> (o Panache emite o INSERT na
 *       hora, mas o {@code @Transactional} tambem faz rollback dele). Nao "corrigido" aqui.
 * </ul>
 */
@Service
public class AsaasService {

  private static final Logger LOG = LoggerFactory.getLogger(AsaasService.class);
  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");
  private static final Set<String> SUPPORTED_EVENTS =
      Set.of("PAYMENT_CONFIRMED", "PAYMENT_RECEIVED", "PAYMENT_OVERDUE", "SUBSCRIPTION_CANCELLED");

  private final AsaasClient asaasClient;
  private final ObjectMapper objectMapper;
  private final TenantRepository tenantRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PaymentRepository paymentRepository;
  private final ProductRepository productRepository;
  private final CheckoutIntentRepository checkoutIntentRepository;
  private final CheckoutOrderRepository checkoutOrderRepository;
  private final WebhookEventLogRepository webhookEventLogRepository;
  private final IntegrationLogRepository integrationLogRepository;
  private final ContextoTenant contextoTenant;
  private final LicenseStatusService licenseStatusService;
  private final String asaasApiKey;
  private final String webhookToken;
  private final boolean asaasEnabled;

  public AsaasService(
      AsaasClient asaasClient,
      ObjectMapper objectMapper,
      TenantRepository tenantRepository,
      SubscriptionRepository subscriptionRepository,
      PaymentRepository paymentRepository,
      ProductRepository productRepository,
      CheckoutIntentRepository checkoutIntentRepository,
      CheckoutOrderRepository checkoutOrderRepository,
      WebhookEventLogRepository webhookEventLogRepository,
      IntegrationLogRepository integrationLogRepository,
      ContextoTenant contextoTenant,
      LicenseStatusService licenseStatusService,
      @Value("${app.asaas.api-key:__unset__}") String asaasApiKey,
      @Value("${app.asaas.webhook.token:__unset__}") String webhookToken,
      @Value("${app.asaas.enabled:false}") boolean asaasEnabled) {
    this.asaasClient = asaasClient;
    this.objectMapper = objectMapper;
    this.tenantRepository = tenantRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.paymentRepository = paymentRepository;
    this.productRepository = productRepository;
    this.checkoutIntentRepository = checkoutIntentRepository;
    this.checkoutOrderRepository = checkoutOrderRepository;
    this.webhookEventLogRepository = webhookEventLogRepository;
    this.integrationLogRepository = integrationLogRepository;
    this.contextoTenant = contextoTenant;
    this.licenseStatusService = licenseStatusService;
    this.asaasApiKey = asaasApiKey;
    this.webhookToken = webhookToken;
    this.asaasEnabled = asaasEnabled;
  }

  @Transactional
  public void ensureCustomerForTenant(Tenant tenant, String cpfCnpj) {
    if (!asaasEnabled) {
      LOG.info("Asaas desabilitado por configuracao; customer nao sera provisionado");
      return;
    }
    if (tenant == null) throw new IllegalArgumentException("Tenant invalido para billing");
    if (tenant.getAsaasCustomerId() != null && !tenant.getAsaasCustomerId().isBlank()) return;

    AsaasDtos.CreateCustomerRequest req = new AsaasDtos.CreateCustomerRequest();
    req.name = tenant.getName();
    req.email = tenant.getEmail();
    req.phone = onlyDigits(tenant.getPhone());
    req.mobilePhone =
        onlyDigits(tenant.getWhatsapp() != null ? tenant.getWhatsapp() : tenant.getPhone());
    req.cpfCnpj = onlyDigits(cpfCnpj);
    req.externalReference = buildExternalReference(tenant.getId(), null, "CUSTOMER");

    String requestJson = safeJson(req);
    try {
      AsaasDtos.CustomerResponse response = asaasClient.createCustomer(requiredApiKey(), req);
      tenant.setAsaasCustomerId(response.id);
      persistIntegrationLog(
          "OUTBOUND",
          "ASAAS_CREATE_CUSTOMER",
          tenant.getId(),
          response.id,
          requestJson,
          safeJson(response),
          200,
          true,
          null);
      LOG.info("Asaas customer criado tenant={} customer={}", tenant.getId(), response.id);
    } catch (RestClientResponseException e) {
      String body = safeResponseBody(e);
      persistIntegrationLog(
          "OUTBOUND",
          "ASAAS_CREATE_CUSTOMER",
          tenant.getId(),
          null,
          requestJson,
          body,
          e.getStatusCode().value(),
          false,
          e.getMessage());
      LOG.warn(
          "Falha ao criar customer Asaas. traceId={} tenant={} status={} motivo={}",
          CorrelatedLogging.traceId(),
          tenant.getId(),
          e.getStatusCode().value(),
          composeAsaasErrorMessage(null, body));
      throw new AsaasException("Falha ao criar customer no Asaas", e.getStatusCode().value(), e);
    } catch (Exception e) {
      persistIntegrationLog(
          "OUTBOUND",
          "ASAAS_CREATE_CUSTOMER",
          tenant.getId(),
          null,
          requestJson,
          null,
          502,
          false,
          e.getMessage());
      LOG.error(
          "Erro inesperado ao criar customer Asaas. traceId={} tenant={}",
          CorrelatedLogging.traceId(),
          tenant.getId(),
          e);
      throw new AsaasException("Falha de integracao com Asaas", 502, e);
    }
  }

  @Transactional
  public BillingDtos.SubscriptionResponse createMonthlySubscriptionForCurrentTenant(
      BillingDtos.CreateSubscriptionRequest request, String remoteIp) {
    if (!asaasEnabled) {
      throw new IllegalStateException("Integracao Asaas desabilitada");
    }
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
    if (tenant == null) throw new IllegalArgumentException("Tenant nao encontrado");

    ensureCustomerForTenant(tenant, request.cpfCnpj);

    AsaasDtos.CreateSubscriptionRequest asaasRequest = new AsaasDtos.CreateSubscriptionRequest();
    BigDecimal amount = resolveAmount(request);
    asaasRequest.customer = tenant.getAsaasCustomerId();
    asaasRequest.billingType = request.billingType;
    asaasRequest.value = amount;
    asaasRequest.nextDueDate =
        request.nextDueDate != null && !request.nextDueDate.isBlank()
            ? request.nextDueDate.trim()
            : LocalDate.now(ZONE_BR).plusDays(1).toString();
    asaasRequest.cycle = "MONTHLY";
    asaasRequest.description =
        request.description != null && !request.description.isBlank()
            ? request.description.trim()
            : "Assinatura mensal Azzo Agenda Pro";
    asaasRequest.externalReference =
        buildExternalReference(tenant.getId(), request.productId, request.billingType);
    asaasRequest.remoteIp = remoteIp;
    if ("CREDIT_CARD".equals(request.billingType)) {
      if (request.creditCard == null || request.creditCardHolderInfo == null) {
        throw new IllegalArgumentException("Dados de cartao sao obrigatorios para CREDIT_CARD");
      }
      asaasRequest.creditCard = new AsaasDtos.CreditCard();
      asaasRequest.creditCard.holderName = request.creditCard.holderName;
      asaasRequest.creditCard.number = request.creditCard.number;
      asaasRequest.creditCard.expiryMonth = request.creditCard.expiryMonth;
      asaasRequest.creditCard.expiryYear = request.creditCard.expiryYear;
      asaasRequest.creditCard.ccv = request.creditCard.ccv;

      asaasRequest.creditCardHolderInfo = new AsaasDtos.CreditCardHolderInfo();
      asaasRequest.creditCardHolderInfo.name = request.creditCardHolderInfo.name;
      asaasRequest.creditCardHolderInfo.email = request.creditCardHolderInfo.email;
      asaasRequest.creditCardHolderInfo.cpfCnpj = onlyDigits(request.creditCardHolderInfo.cpfCnpj);
      asaasRequest.creditCardHolderInfo.postalCode =
          onlyDigits(request.creditCardHolderInfo.postalCode);
      asaasRequest.creditCardHolderInfo.addressNumber =
          request.creditCardHolderInfo.addressNumber;
      asaasRequest.creditCardHolderInfo.addressComplement =
          request.creditCardHolderInfo.addressComplement;
      asaasRequest.creditCardHolderInfo.phone = onlyDigits(request.creditCardHolderInfo.phone);
    }

    String requestJson = safeJson(sanitizeSubscriptionRequestForLog(asaasRequest));
    AsaasDtos.SubscriptionResponse asaasSubscription;
    try {
      asaasSubscription = asaasClient.createSubscription(requiredApiKey(), asaasRequest);
      persistIntegrationLog(
          "OUTBOUND",
          "ASAAS_CREATE_SUBSCRIPTION",
          tenantId,
          asaasSubscription.id,
          requestJson,
          safeJson(asaasSubscription),
          200,
          true,
          null);
    } catch (RestClientResponseException e) {
      String body = safeResponseBody(e);
      if (isInvalidCustomerError(body)) {
        LOG.warn(
            "Asaas retornou invalid_customer para tenant={}. Recriando customer e tentando novamente.",
            tenantId);
        tenant.setAsaasCustomerId(null);
        ensureCustomerForTenant(tenant, request.cpfCnpj);
        asaasRequest.customer = tenant.getAsaasCustomerId();
        String retryRequestJson = safeJson(sanitizeSubscriptionRequestForLog(asaasRequest));
        try {
          asaasSubscription = asaasClient.createSubscription(requiredApiKey(), asaasRequest);
          persistIntegrationLog(
              "OUTBOUND",
              "ASAAS_CREATE_SUBSCRIPTION_RETRY",
              tenantId,
              asaasSubscription.id,
              retryRequestJson,
              safeJson(asaasSubscription),
              200,
              true,
              null);
        } catch (RestClientResponseException retryEx) {
          String retryBody = safeResponseBody(retryEx);
          persistIntegrationLog(
              "OUTBOUND",
              "ASAAS_CREATE_SUBSCRIPTION_RETRY",
              tenantId,
              null,
              retryRequestJson,
              retryBody,
              retryEx.getStatusCode().value(),
              false,
              retryEx.getMessage());
          LOG.warn(
              "Falha ao criar assinatura Asaas (retry). traceId={} tenant={} status={} motivo={}",
              CorrelatedLogging.traceId(),
              tenantId,
              retryEx.getStatusCode().value(),
              composeAsaasErrorMessage(null, retryBody));
          throw new AsaasException(
              composeAsaasErrorMessage("Falha ao criar assinatura no Asaas", retryBody),
              retryEx.getStatusCode().value(),
              retryEx);
        } catch (Exception retryEx) {
          persistIntegrationLog(
              "OUTBOUND",
              "ASAAS_CREATE_SUBSCRIPTION_RETRY",
              tenantId,
              null,
              retryRequestJson,
              null,
              502,
              false,
              retryEx.getMessage());
          LOG.error(
              "Erro inesperado ao criar assinatura Asaas (retry). traceId={} tenant={}",
              CorrelatedLogging.traceId(),
              tenantId,
              retryEx);
          throw new AsaasException("Falha de integracao com Asaas", 502, retryEx);
        }
      } else {
        persistIntegrationLog(
            "OUTBOUND",
            "ASAAS_CREATE_SUBSCRIPTION",
            tenantId,
            null,
            requestJson,
            body,
            e.getStatusCode().value(),
            false,
            e.getMessage());
        LOG.warn(
            "Falha ao criar assinatura Asaas. traceId={} tenant={} status={} motivo={}",
            CorrelatedLogging.traceId(),
            tenantId,
            e.getStatusCode().value(),
            composeAsaasErrorMessage(null, body));
        throw new AsaasException(
            "Falha ao criar assinatura no Asaas", e.getStatusCode().value(), e);
      }
    } catch (Exception e) {
      persistIntegrationLog(
          "OUTBOUND",
          "ASAAS_CREATE_SUBSCRIPTION",
          tenantId,
          null,
          requestJson,
          null,
          502,
          false,
          e.getMessage());
      LOG.error(
          "Erro inesperado ao criar assinatura Asaas. traceId={} tenant={}",
          CorrelatedLogging.traceId(),
          tenantId,
          e);
      throw new AsaasException("Falha de integracao com Asaas", 502, e);
    }

    Subscription subscription = new Subscription();
    subscription.setTenantId(tenantId);
    subscription.setProductId(parseUuidOrNull(request.productId));
    subscription.setAsaasCustomerId(tenant.getAsaasCustomerId());
    subscription.setAsaasSubscriptionId(asaasSubscription.id);
    subscription.setPlanCode(request.planCode);
    subscription.setBillingType(asaasSubscription.billingType);
    subscription.setStatus(toSubscriptionStatus(asaasSubscription.status, StatusSubscription.PENDING));
    subscription.setValueCents(toCents(amount));
    subscription.setCycle(normalizeStatus(asaasSubscription.cycle, "MONTHLY"));
    subscription.setNextDueDate(parseLocalDate(asaasSubscription.nextDueDate));
    // flush explicito: o upsertPayment abaixo precisa do id gerado e o Panache ja teria emitido o
    // INSERT no persist().
    subscriptionRepository.saveAndFlush(subscription);

    BillingDtos.SubscriptionResponse response = new BillingDtos.SubscriptionResponse();
    response.tenantId = tenantId.toString();
    response.customerId = tenant.getAsaasCustomerId();
    response.subscriptionId = asaasSubscription.id;
    response.status = subscription.getStatus() != null ? subscription.getStatus().name() : null;
    response.billingType = subscription.getBillingType();
    response.nextDueDate =
        subscription.getNextDueDate() != null ? subscription.getNextDueDate().toString() : null;
    response.referenceMonth = formatReferenceMonth(subscription.getNextDueDate());
    response.expiresAt =
        subscription.getNextDueDate() != null
            ? subscription.getNextDueDate().atStartOfDay(ZONE_BR).toInstant().toString()
            : null;
    response.amount = NumericUtil.fromCents(subscription.getValueCents());

    try {
      AsaasDtos.PaymentsResponse paymentsResponse =
          asaasClient.listPayments(requiredApiKey(), asaasSubscription.id, 1, 0);
      AsaasDtos.PaymentResponse firstPayment =
          paymentsResponse != null && paymentsResponse.data != null && !paymentsResponse.data.isEmpty()
              ? paymentsResponse.data.get(0)
              : null;
      persistIntegrationLog(
          "OUTBOUND",
          "ASAAS_LIST_PAYMENTS",
          tenantId,
          asaasSubscription.id,
          null,
          safeJson(paymentsResponse),
          200,
          true,
          null);
      if (firstPayment != null) {
        Payment payment =
            upsertPayment(tenantId, subscription.getId(), asaasSubscription.id, firstPayment);
        response.paymentId = payment.getAsaasPaymentId();
        response.paymentStatus = payment.getStatus() != null ? payment.getStatus().name() : null;
        response.invoiceUrl = payment.getInvoiceUrl();
        response.bankSlipUrl = payment.getBankSlipUrl();
        if ("PIX".equals(payment.getBillingType())) {
          AsaasDtos.PixQrCodeResponse pix =
              asaasClient.getPixQrCode(requiredApiKey(), payment.getAsaasPaymentId());
          payment.setPixQrCode(pix.encodedImage);
          payment.setPixPayload(pix.payload);
          response.pixQrCodeBase64 = pix.encodedImage;
          response.pixPayload = pix.payload;
          persistIntegrationLog(
              "OUTBOUND",
              "ASAAS_GET_PIX_QRCODE",
              tenantId,
              payment.getAsaasPaymentId(),
              null,
              safeJson(pix),
              200,
              true,
              null);
        } else if ("BOLETO".equals(payment.getBillingType())) {
          AsaasDtos.IdentificationFieldResponse identification =
              asaasClient.getIdentificationField(requiredApiKey(), payment.getAsaasPaymentId());
          payment.setBoletoIdentificationField(
              identification != null ? identification.identificationField : null);
          payment.setBoletoBarCode(identification != null ? identification.barCode : null);
          payment.setBoletoNossoNumero(identification != null ? identification.nossoNumero : null);
          response.boletoIdentificationField = payment.getBoletoIdentificationField();
          response.boletoBarCode = payment.getBoletoBarCode();
          response.boletoNossoNumero = payment.getBoletoNossoNumero();
          persistIntegrationLog(
              "OUTBOUND",
              "ASAAS_GET_BOLETO_IDENTIFICATION",
              tenantId,
              payment.getAsaasPaymentId(),
              null,
              safeJson(identification),
              200,
              true,
              null);
        }
      }
    } catch (RestClientResponseException e) {
      persistIntegrationLog(
          "OUTBOUND",
          "ASAAS_FETCH_FIRST_PAYMENT",
          tenantId,
          asaasSubscription.id,
          null,
          safeResponseBody(e),
          e.getStatusCode().value(),
          false,
          e.getMessage());
      LOG.warn(
          "Nao foi possivel carregar cobranca inicial da subscription={}: {}",
          asaasSubscription.id,
          e.getMessage());
    } catch (Exception e) {
      persistIntegrationLog(
          "OUTBOUND",
          "ASAAS_FETCH_FIRST_PAYMENT",
          tenantId,
          asaasSubscription.id,
          null,
          null,
          500,
          false,
          e.getMessage());
      LOG.warn(
          "Falha ao carregar cobranca inicial da subscription={}: {}",
          asaasSubscription.id,
          e.getMessage());
    }

    return response;
  }

  @Transactional
  public void processWebhook(String token, AsaasDtos.WebhookPayload payload) {
    validateWebhookToken(token);
    if (!asaasEnabled) {
      LOG.info("Webhook Asaas ignorado porque a integracao esta desabilitada");
      return;
    }
    if (payload == null || payload.event == null || payload.event.isBlank()) {
      throw new IllegalArgumentException("Webhook Asaas invalido");
    }
    if (!SUPPORTED_EVENTS.contains(payload.event)) {
      LOG.info("Webhook Asaas ignorado por evento nao mapeado: {}", payload.event);
      return;
    }

    String payloadJson = safeJson(payload);
    String payloadHash = sha256(payloadJson);
    String idempotencyKey = buildIdempotencyKey(payload, payloadHash);
    if (webhookEventLogRepository.existsIdempotencyKey(idempotencyKey)) {
      LOG.info("Webhook Asaas duplicado ignorado key={}", idempotencyKey);
      return;
    }

    Tenant tenant = resolveTenant(payload);
    UUID tenantId = tenant != null ? tenant.getId() : null;
    if (tenantId == null && payload.payment != null) {
      throw new IllegalArgumentException(
          "Nao foi possivel identificar tenant para o webhook Asaas");
    }
    LOG.info(
        CorrelatedLogging.context(
            "Processamento de webhook Asaas iniciado",
            "event", payload.event,
            "tenantId", tenantId,
            "paymentId", payload.payment != null ? payload.payment.id : null,
            "subscriptionId",
                payload.subscription != null
                    ? payload.subscription.id
                    : payload.payment != null ? payload.payment.subscription : null));

    WebhookEventLog eventLog = new WebhookEventLog();
    eventLog.setProvider("ASAAS");
    eventLog.setIdempotencyKey(idempotencyKey);
    eventLog.setEventType(payload.event);
    eventLog.setTenantId(tenantId);
    eventLog.setExternalPaymentId(payload.payment != null ? payload.payment.id : null);
    eventLog.setExternalSubscriptionId(
        payload.subscription != null
            ? payload.subscription.id
            : payload.payment != null ? payload.payment.subscription : null);
    eventLog.setPayloadHash(payloadHash);
    eventLog.setPayloadJson(payloadJson);
    eventLog.setProcessed(false);
    webhookEventLogRepository.save(eventLog);

    persistIntegrationLog(
        "INBOUND",
        "ASAAS_WEBHOOK",
        tenantId,
        eventLog.getExternalPaymentId(),
        null,
        payloadJson,
        200,
        true,
        null);

    try {
      if (payload.payment != null) {
        Payment payment = upsertPaymentFromWebhook(tenantId, payload.payment);
        if ("PAYMENT_CONFIRMED".equals(payload.event) || "PAYMENT_RECEIVED".equals(payload.event)) {
          activateSubscription(payload.payment.subscription);
          payment.setStatus(toPaymentStatus(payload.payment.status, StatusPayment.RECEIVED));
          registrarLicencaPorPagamento(tenantId, payment, payload.payment.externalReference);
        }
        if ("PAYMENT_OVERDUE".equals(payload.event)) {
          markSubscriptionOverdue(payload.payment.subscription);
          payment.setStatus(toPaymentStatus(payload.payment.status, StatusPayment.OVERDUE));
        }
      }
      if ("SUBSCRIPTION_CANCELLED".equals(payload.event)) {
        String subscriptionId =
            payload.subscription != null
                ? payload.subscription.id
                : payload.payment != null ? payload.payment.subscription : null;
        cancelSubscription(subscriptionId);
      }

      eventLog.setProcessed(true);
      eventLog.setProcessedAt(Instant.now());
      LOG.info(
          CorrelatedLogging.context(
              "Processamento de webhook Asaas concluido",
              "event", payload.event,
              "tenantId", tenantId,
              "paymentId", eventLog.getExternalPaymentId(),
              "subscriptionId", eventLog.getExternalSubscriptionId(),
              "idempotencyKey", idempotencyKey));
    } catch (Exception e) {
      eventLog.setErrorMessage(truncate(e.getMessage(), 500));
      LOG.error(
          CorrelatedLogging.context(
              "Processamento de webhook Asaas falhou",
              "event", payload.event,
              "tenantId", tenantId,
              "paymentId", eventLog.getExternalPaymentId(),
              "subscriptionId", eventLog.getExternalSubscriptionId(),
              "idempotencyKey", idempotencyKey,
              "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw e;
    }
  }

  private void activateSubscription(String asaasSubscriptionId) {
    if (asaasSubscriptionId == null || asaasSubscriptionId.isBlank()) return;
    subscriptionRepository
        .buscarPorAsaasSubscriptionId(asaasSubscriptionId)
        .ifPresent(subscription -> subscription.setStatus(StatusSubscription.ACTIVE));
  }

  private void markSubscriptionOverdue(String asaasSubscriptionId) {
    if (asaasSubscriptionId == null || asaasSubscriptionId.isBlank()) return;
    subscriptionRepository
        .buscarPorAsaasSubscriptionId(asaasSubscriptionId)
        .ifPresent(subscription -> subscription.setStatus(StatusSubscription.OVERDUE));
  }

  private void cancelSubscription(String asaasSubscriptionId) {
    if (asaasSubscriptionId == null || asaasSubscriptionId.isBlank()) return;
    subscriptionRepository
        .buscarPorAsaasSubscriptionId(asaasSubscriptionId)
        .ifPresent(
            subscription -> {
              subscription.setStatus(StatusSubscription.CANCELLED);
              subscription.setCancelledAt(Instant.now());
            });
  }

  private Payment upsertPaymentFromWebhook(UUID tenantId, AsaasDtos.WebhookPayment asaasPayment) {
    Payment payment =
        paymentRepository.buscarPorAsaasPaymentId(asaasPayment.id).orElseGet(Payment::new);
    payment.setTenantId(tenantId);
    payment.setAsaasPaymentId(asaasPayment.id);
    payment.setAsaasSubscriptionId(asaasPayment.subscription);
    payment.setStatus(toPaymentStatus(asaasPayment.status, StatusPayment.PENDING));
    payment.setBillingType(normalizeStatus(asaasPayment.billingType, "UNDEFINED"));
    payment.setAmountCents(toCents(asaasPayment.value));
    payment.setNetAmountCents(asaasPayment.netValue != null ? toCents(asaasPayment.netValue) : null);
    payment.setDueDate(parseLocalDate(asaasPayment.dueDate));
    payment.setReferenceMonth(formatReferenceMonth(payment.getDueDate()));
    payment.setPaidAt(parseInstant(asaasPayment.paymentDate));
    payment.setInvoiceUrl(asaasPayment.invoiceUrl);
    payment.setBankSlipUrl(asaasPayment.bankSlipUrl);
    payment.setExpiresAt(
        payment.getDueDate() != null
            ? payment.getDueDate().atStartOfDay(ZONE_BR).toInstant()
            : null);
    hydratePaymentDetails(payment);

    subscriptionRepository
        .buscarPorAsaasSubscriptionId(asaasPayment.subscription)
        .ifPresent(subscription -> payment.setSubscriptionId(subscription.getId()));

    if (payment.getId() == null) paymentRepository.saveAndFlush(payment);
    return payment;
  }

  private Payment upsertPayment(
      UUID tenantId,
      UUID subscriptionId,
      String asaasSubscriptionId,
      AsaasDtos.PaymentResponse asaasPayment) {
    Payment payment =
        paymentRepository.buscarPorAsaasPaymentId(asaasPayment.id).orElseGet(Payment::new);
    payment.setTenantId(tenantId);
    payment.setSubscriptionId(subscriptionId);
    payment.setAsaasPaymentId(asaasPayment.id);
    payment.setAsaasSubscriptionId(asaasSubscriptionId);
    payment.setStatus(toPaymentStatus(asaasPayment.status, StatusPayment.PENDING));
    payment.setBillingType(normalizeStatus(asaasPayment.billingType, "UNDEFINED"));
    payment.setAmountCents(toCents(asaasPayment.value));
    payment.setNetAmountCents(asaasPayment.netValue != null ? toCents(asaasPayment.netValue) : null);
    payment.setDueDate(parseLocalDate(asaasPayment.dueDate));
    payment.setReferenceMonth(formatReferenceMonth(payment.getDueDate()));
    payment.setPaidAt(parseInstant(asaasPayment.paymentDate));
    payment.setInvoiceUrl(asaasPayment.invoiceUrl);
    payment.setBankSlipUrl(asaasPayment.bankSlipUrl);
    payment.setExpiresAt(
        payment.getDueDate() != null
            ? payment.getDueDate().atStartOfDay(ZONE_BR).toInstant()
            : null);
    if (!"BOLETO".equalsIgnoreCase(payment.getBillingType())) {
      payment.setBoletoIdentificationField(null);
      payment.setBoletoBarCode(null);
      payment.setBoletoNossoNumero(null);
    }
    if (payment.getId() == null) paymentRepository.saveAndFlush(payment);
    return payment;
  }

  private void hydratePaymentDetails(Payment payment) {
    if (payment == null
        || payment.getAsaasPaymentId() == null
        || payment.getAsaasPaymentId().isBlank()) return;

    if ("BOLETO".equalsIgnoreCase(payment.getBillingType())
        && (isBlank(payment.getBoletoIdentificationField()) || isBlank(payment.getBoletoBarCode()))) {
      try {
        AsaasDtos.IdentificationFieldResponse identification =
            asaasClient.getIdentificationField(requiredApiKey(), payment.getAsaasPaymentId());
        if (identification != null) {
          payment.setBoletoIdentificationField(identification.identificationField);
          payment.setBoletoBarCode(identification.barCode);
          payment.setBoletoNossoNumero(identification.nossoNumero);
        }
      } catch (Exception ex) {
        LOG.warn(
            "Nao foi possivel hidratar dados de boleto via webhook paymentId={}: {}",
            payment.getAsaasPaymentId(),
            ex.getMessage());
      }
    }

    if ("PIX".equalsIgnoreCase(payment.getBillingType())
        && (isBlank(payment.getPixQrCode()) || isBlank(payment.getPixPayload()))) {
      try {
        AsaasDtos.PixQrCodeResponse pix =
            asaasClient.getPixQrCode(requiredApiKey(), payment.getAsaasPaymentId());
        if (pix != null) {
          payment.setPixQrCode(pix.encodedImage);
          payment.setPixPayload(pix.payload);
        }
      } catch (Exception ex) {
        LOG.warn(
            "Nao foi possivel hidratar dados de Pix via webhook paymentId={}: {}",
            payment.getAsaasPaymentId(),
            ex.getMessage());
      }
    }
  }

  private Tenant resolveTenant(AsaasDtos.WebhookPayload payload) {
    if (payload == null) return null;
    String customer =
        payload.payment != null && payload.payment.customer != null
            ? payload.payment.customer
            : payload.subscription != null ? payload.subscription.customer : null;
    if (customer != null && !customer.isBlank()) {
      Tenant tenant = tenantRepository.buscarPorAsaasCustomerId(customer).orElse(null);
      if (tenant != null) return tenant;
    }
    String externalReference = payload.payment != null ? payload.payment.externalReference : null;
    if (externalReference != null && !externalReference.isBlank()) {
      UUID extractedTenantId = extractTenantIdFromExternalReference(externalReference);
      if (extractedTenantId != null) {
        Tenant tenant = tenantRepository.findById(extractedTenantId).orElse(null);
        if (tenant != null) return tenant;
      }
    }
    String subscriptionId =
        payload.subscription != null && payload.subscription.id != null
            ? payload.subscription.id
            : payload.payment != null ? payload.payment.subscription : null;
    if (subscriptionId == null || subscriptionId.isBlank()) return null;
    return subscriptionRepository
        .buscarPorAsaasSubscriptionId(subscriptionId)
        .map(subscription -> tenantRepository.findById(subscription.getTenantId()).orElse(null))
        .orElse(null);
  }

  private AsaasDtos.CreateSubscriptionRequest sanitizeSubscriptionRequestForLog(
      AsaasDtos.CreateSubscriptionRequest source) {
    AsaasDtos.CreateSubscriptionRequest copy = new AsaasDtos.CreateSubscriptionRequest();
    copy.customer = source.customer;
    copy.billingType = source.billingType;
    copy.value = source.value;
    copy.nextDueDate = source.nextDueDate;
    copy.cycle = source.cycle;
    copy.description = source.description;
    copy.externalReference = source.externalReference;
    copy.remoteIp = source.remoteIp;
    if (source.creditCard != null) {
      copy.creditCard = new AsaasDtos.CreditCard();
      copy.creditCard.holderName = source.creditCard.holderName;
      copy.creditCard.number = maskCard(source.creditCard.number);
      copy.creditCard.expiryMonth = source.creditCard.expiryMonth;
      copy.creditCard.expiryYear = source.creditCard.expiryYear;
      copy.creditCard.ccv = "***";
    }
    if (source.creditCardHolderInfo != null) {
      copy.creditCardHolderInfo = new AsaasDtos.CreditCardHolderInfo();
      copy.creditCardHolderInfo.name = source.creditCardHolderInfo.name;
      copy.creditCardHolderInfo.email = source.creditCardHolderInfo.email;
      copy.creditCardHolderInfo.cpfCnpj = maskDocument(source.creditCardHolderInfo.cpfCnpj);
      copy.creditCardHolderInfo.postalCode = source.creditCardHolderInfo.postalCode;
      copy.creditCardHolderInfo.addressNumber = source.creditCardHolderInfo.addressNumber;
      copy.creditCardHolderInfo.addressComplement = source.creditCardHolderInfo.addressComplement;
      copy.creditCardHolderInfo.phone = maskPhone(source.creditCardHolderInfo.phone);
    }
    return copy;
  }

  private String maskCard(String card) {
    if (card == null || card.length() < 4) return "****";
    String digits = onlyDigits(card);
    if (digits == null || digits.length() < 4) return "****";
    return "**** **** **** " + digits.substring(digits.length() - 4);
  }

  private String maskDocument(String doc) {
    String digits = onlyDigits(doc);
    if (digits == null || digits.length() < 4) return "***";
    return "***" + digits.substring(digits.length() - 4);
  }

  private String maskPhone(String phone) {
    String digits = onlyDigits(phone);
    if (digits == null || digits.length() < 4) return "***";
    return "***" + digits.substring(digits.length() - 4);
  }

  private void validateWebhookToken(String providedToken) {
    if (webhookToken == null || webhookToken.isBlank() || "__unset__".equals(webhookToken)) {
      throw new ApiClientErrorException("ASAAS_WEBHOOK_TOKEN nao configurado", 401);
    }
    byte[] expected = safeTokenBytes(webhookToken);
    byte[] provided = safeTokenBytes(providedToken);
    if (expected.length == 0 || !MessageDigest.isEqual(expected, provided)) {
      throw new ApiClientErrorException("Token de webhook invalido", 401);
    }
  }

  private byte[] safeTokenBytes(String token) {
    return token == null ? new byte[0] : token.trim().getBytes(StandardCharsets.UTF_8);
  }

  private String buildIdempotencyKey(AsaasDtos.WebhookPayload payload, String payloadHash) {
    String paymentId =
        payload.payment != null ? normalizeStatus(payload.payment.id, "no-payment") : "no-payment";
    String subscriptionId =
        payload.subscription != null && payload.subscription.id != null
            ? payload.subscription.id
            : payload.payment != null && payload.payment.subscription != null
                ? payload.payment.subscription
                : "no-subscription";
    return payload.event + ":" + subscriptionId + ":" + paymentId + ":" + payloadHash;
  }

  private String onlyDigits(String value) {
    if (value == null) return null;
    String digits = value.replaceAll("\\D", "");
    return digits.isBlank() ? null : digits;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String normalizeStatus(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
  }

  private StatusSubscription toSubscriptionStatus(String raw, StatusSubscription fallback) {
    String normalized = normalizeStatus(raw, fallback.name());
    return StatusSubscription.fromValue(normalized);
  }

  private StatusPayment toPaymentStatus(String raw, StatusPayment fallback) {
    String normalized = normalizeStatus(raw, fallback.name());
    return StatusPayment.fromValue(normalized);
  }

  private String safeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      return "{}";
    }
  }

  private String safeResponseBody(RestClientResponseException exception) {
    try {
      return exception != null ? exception.getResponseBodyAsString() : null;
    } catch (Exception e) {
      return null;
    }
  }

  private String composeAsaasErrorMessage(String base, String responseBody) {
    if (responseBody == null || responseBody.isBlank()) return base;
    return base + ": " + truncate(responseBody, 500);
  }

  private boolean isInvalidCustomerError(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) return false;
    return responseBody.contains("\"invalid_customer\"")
        || responseBody.toLowerCase().contains("cliente invalido");
  }

  private void persistIntegrationLog(
      String direction,
      String action,
      UUID tenantId,
      String externalReference,
      String requestPayload,
      String responsePayload,
      Integer httpStatus,
      boolean success,
      String errorMessage) {
    IntegrationLog log = new IntegrationLog();
    log.setProvider("ASAAS");
    log.setDirection(direction);
    log.setAction(action);
    log.setTenantId(tenantId);
    log.setExternalReference(externalReference);
    log.setRequestPayload(IntegrationLogSanitizer.sanitize(requestPayload));
    log.setResponsePayload(IntegrationLogSanitizer.sanitize(responsePayload));
    log.setHttpStatus(httpStatus);
    log.setSuccess(success);
    log.setErrorMessage(truncate(errorMessage, 500));
    integrationLogRepository.save(log);
  }

  private String truncate(String value, int maxLength) {
    if (value == null) return null;
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private long toCents(BigDecimal value) {
    if (value == null) return 0L;
    return value.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
  }

  private BigDecimal centsToBigDecimal(Long cents) {
    if (cents == null) return BigDecimal.ZERO;
    return BigDecimal.valueOf(cents, 2);
  }

  private BigDecimal resolveAmount(BillingDtos.CreateSubscriptionRequest request) {
    if (request == null) return BigDecimal.ZERO;
    if (request.amount != null) {
      return NumericUtil.normalize(request.amount);
    }
    return centsToBigDecimal(request.amountCents);
  }

  private LocalDate parseLocalDate(String value) {
    if (value == null || value.isBlank()) return null;
    return LocalDate.parse(value.trim());
  }

  private String formatReferenceMonth(LocalDate dueDate) {
    if (dueDate == null) return null;
    return String.format("%04d-%02d", dueDate.getYear(), dueDate.getMonthValue());
  }

  private Instant parseInstant(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value.trim());
    } catch (Exception ignored) {
      return parseLocalDate(value).atStartOfDay(ZONE_BR).toInstant();
    }
  }

  private String sha256(String payload) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest =
          md.digest(Objects.requireNonNullElse(payload, "").getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception e) {
      return UUID.randomUUID().toString();
    }
  }

  private String requiredApiKey() {
    if (asaasApiKey == null || asaasApiKey.isBlank() || "__unset__".equals(asaasApiKey)) {
      throw new IllegalStateException("ASAAS_API_KEY nao configurada");
    }
    return asaasApiKey.trim();
  }

  private UUID parseUuidOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public String buildExternalReference(UUID tenantId, String productId, String billingType) {
    String tenantPart = compactUuid(tenantId);
    String productPart = compactUuid(parseUuidOrNull(productId));
    String billingPart = compactBillingType(billingType);
    String tsPart = Long.toString(Instant.now().toEpochMilli(), 36);
    return "t:" + tenantPart + "|p:" + productPart + "|b:" + billingPart + "|x:" + tsPart;
  }

  private UUID extractTenantIdFromExternalReference(String externalReference) {
    if (externalReference == null || externalReference.isBlank()) return null;
    String normalized = externalReference.trim();
    try {
      return UUID.fromString(normalized);
    } catch (IllegalArgumentException ignored) {
      // Fallback para formato estruturado.
    }
    String[] parts = normalized.split("\\|");
    for (String part : parts) {
      if (part == null) continue;
      String token = part.trim();
      String value = null;
      if (token.startsWith("tenant:")) {
        value = token.substring("tenant:".length()).trim();
      } else if (token.startsWith("t:")) {
        value = token.substring("t:".length()).trim();
      }
      if (value == null || value.isBlank()) continue;
      try {
        return parseUuidFlexible(value);
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    return null;
  }

  private UUID extractProductIdFromExternalReference(String externalReference) {
    if (externalReference == null || externalReference.isBlank()) return null;
    String normalized = externalReference.trim();
    String[] parts = normalized.split("\\|");
    for (String part : parts) {
      if (part == null) continue;
      String token = part.trim();
      String value = null;
      if (token.startsWith("product:")) {
        value = token.substring("product:".length()).trim();
      } else if (token.startsWith("p:")) {
        value = token.substring("p:".length()).trim();
      }
      if (value == null || value.isBlank()) continue;
      return parseUuidFlexible(value);
    }
    return null;
  }

  private UUID parseUuidFlexible(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    UUID parsed = parseUuidOrNull(normalized);
    if (parsed != null) return parsed;
    String hex = normalized.replace("-", "");
    if (hex.length() != 32) return null;
    String formatted =
        hex.substring(0, 8)
            + "-"
            + hex.substring(8, 12)
            + "-"
            + hex.substring(12, 16)
            + "-"
            + hex.substring(16, 20)
            + "-"
            + hex.substring(20);
    return parseUuidOrNull(formatted);
  }

  private String compactUuid(UUID value) {
    if (value == null) return "none";
    return value.toString().replace("-", "");
  }

  private String compactBillingType(String billingType) {
    String normalized = normalizeExternalReferencePart(billingType, "UNDEFINED").toUpperCase();
    return switch (normalized) {
      case "BOLETO" -> "B";
      case "PIX" -> "P";
      case "CREDIT_CARD" -> "C";
      case "CUSTOMER" -> "U";
      default -> "X";
    };
  }

  private String normalizeExternalReferencePart(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value.trim().replace("|", "_").replace(":", "_");
  }

  private void registrarLicencaPorPagamento(
      UUID tenantId, Payment payment, String externalReference) {
    if (tenantId == null
        || payment == null
        || payment.getAsaasPaymentId() == null
        || payment.getAsaasPaymentId().isBlank()) return;

    String paymentReference = "asaas-payment-" + payment.getAsaasPaymentId();
    if (checkoutIntentRepository.buscarPorReferenciaPagamento(tenantId, paymentReference).isPresent()) {
      return;
    }

    Subscription subscription =
        payment.getSubscriptionId() != null
            ? subscriptionRepository.findById(payment.getSubscriptionId()).orElse(null)
            : null;
    UUID productId = null;
    if (subscription != null) {
      productId =
          subscription.getProductId() != null
              ? subscription.getProductId()
              : parseUuidOrNull(subscription.getPlanCode());
    }
    if (productId == null) {
      productId = extractProductIdFromExternalReference(externalReference);
    }
    if (productId == null) {
      LOG.warn(
          "Nao foi possivel identificar productId para gerar licenca tenant={} payment={} externalReference={}",
          tenantId,
          payment.getAsaasPaymentId(),
          externalReference);
      return;
    }

    Product product = productRepository.findById(productId).orElse(null);
    if (product == null) {
      LOG.warn("Produto da subscription nao encontrado tenant={} productId={}", tenantId, productId);
      return;
    }

    Instant base = payment.getPaidAt() != null ? payment.getPaidAt() : Instant.now();
    Instant carryOverBase =
        checkoutOrderRepository
            .buscarPlanoVigenteMaisRecente(tenantId, Instant.now())
            .map(CheckoutOrder::getValidUntil)
            .orElse(null);
    if (carryOverBase != null && carryOverBase.isAfter(base)) {
      base = carryOverBase;
    }
    int validityDays = resolveProductValidityDays(product);
    Instant validUntil = base.atZone(ZoneOffset.UTC).plusDays(validityDays).toInstant();

    CheckoutIntent intent = new CheckoutIntent();
    intent.setTenantId(tenantId);
    intent.setUserId(null);
    intent.setProductId(product.getId());
    intent.setProductNameSnapshot(product.getName());
    intent.setCurrencySnapshot(product.getCurrency());
    intent.setCurrency(product.getCurrency());
    intent.setUnitPriceSnapshot(NumericUtil.fromCents(payment.getAmountCents()));
    intent.setQuantity(1);
    intent.setTotalPriceSnapshot(NumericUtil.fromCents(payment.getAmountCents()));
    intent.setCalculatedTotal(NumericUtil.fromCents(payment.getAmountCents()));
    intent.setStatus(StatusCheckout.CONFIRMED);
    intent.setExpiresAt(validUntil);
    intent.setPaymentReference(paymentReference);
    intent.setConfirmedAt(base);
    // flush explicito: o CheckoutOrder abaixo referencia intent.id, que o Panache ja teria
    // materializado no persist().
    checkoutIntentRepository.saveAndFlush(intent);

    CheckoutOrder order = new CheckoutOrder();
    order.setIntentId(intent.getId());
    order.setProductId(product.getId());
    order.setTenantId(tenantId);
    order.setUserId(null);
    order.setTotal(payment.getAmountCents());
    order.setStatus(StatusCheckout.CONFIRMED);
    order.setValidUntil(validUntil);
    // flush explicito: licenseStatusService.avaliar() consulta os pedidos vigentes do tenant e
    // precisa enxergar este.
    checkoutOrderRepository.saveAndFlush(order);

    licenseStatusService.avaliar(tenantId);
  }

  private int resolveProductValidityDays(Product product) {
    if (product != null && product.getValidityDays() != null && product.getValidityDays() > 0) {
      return product.getValidityDays();
    }
    int months = product != null && product.getValidityMonths() > 0 ? product.getValidityMonths() : 1;
    return months * 30;
  }

  public void cancelarAssinaturaAtiva(String asaasSubscriptionId) {
    if (!asaasEnabled) {
      LOG.info("Asaas desabilitado; cancelamento local da subscription={}", asaasSubscriptionId);
      return;
    }
    if (asaasSubscriptionId == null || asaasSubscriptionId.isBlank()) return;
    try {
      asaasClient.cancelSubscription(requiredApiKey(), asaasSubscriptionId);
      LOG.info("Subscription cancelada no Asaas: {}", asaasSubscriptionId);
    } catch (Exception e) {
      LOG.warn(
          "Falha ao cancelar subscription no Asaas: {} — cancelamento local prosseguira",
          asaasSubscriptionId,
          e);
    }
  }
}
