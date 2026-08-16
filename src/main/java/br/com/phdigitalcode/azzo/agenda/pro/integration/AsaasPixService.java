package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Payment;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPayment;
import br.com.phdigitalcode.azzo.agenda.pro.exception.AsaasException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PaymentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;

/**
 * Espelha {@code infrastructure/payment/AsaasPixService.java}: cobranca avulsa Pix da PLATAFORMA
 * contra o tenant. Nao cria assinatura no Asaas — Pix e sempre pagamento unico, e por isso o
 * {@code subscriptionId} da resposta e sempre nulo.
 *
 * <p>Decisoes de porte (as mesmas ja adotadas em {@link AsaasService}):
 *
 * <ul>
 *   <li>{@code WebApplicationException} (JAX-RS) vira {@link RestClientResponseException}; o corpo
 *       do erro sai de {@code getResponseBodyAsString()}.
 *   <li>{@code TraceContext.traceId()} (SkyWalking) vira {@code CorrelatedLogging.traceId()}.
 *   <li>{@code paymentRepository.persist} so e chamado quando o registro e novo; quando o
 *       pagamento ja existe ele vem <b>managed</b> do repositorio e as mutacoes vao para o banco
 *       por dirty checking no commit, igual ao Panache.
 * </ul>
 *
 * <p>Nao ha necessidade de {@code flush} (armadilha 3): todos os campos que entram na resposta sao
 * atribuidos na mao neste metodo — nada vem do {@code @PrePersist}.
 */
@Service
public class AsaasPixService {

  private static final Logger LOG = LoggerFactory.getLogger(AsaasPixService.class);
  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private final AsaasClient asaasClient;
  private final TenantRepository tenantRepository;
  private final PaymentRepository paymentRepository;
  private final ContextoTenant contextoTenant;
  private final AsaasService asaasService;
  private final String asaasApiKey;
  private final boolean asaasEnabled;

  public AsaasPixService(
      AsaasClient asaasClient,
      TenantRepository tenantRepository,
      PaymentRepository paymentRepository,
      ContextoTenant contextoTenant,
      AsaasService asaasService,
      @Value("${app.asaas.api-key:__unset__}") String asaasApiKey,
      @Value("${app.asaas.enabled:false}") boolean asaasEnabled) {
    this.asaasClient = asaasClient;
    this.tenantRepository = tenantRepository;
    this.paymentRepository = paymentRepository;
    this.contextoTenant = contextoTenant;
    this.asaasService = asaasService;
    this.asaasApiKey = asaasApiKey;
    this.asaasEnabled = asaasEnabled;
  }

  @Transactional
  public BillingDtos.SubscriptionResponse createPixChargeForCurrentTenant(
      BillingDtos.CreateSubscriptionRequest request, String remoteIp) {
    if (!asaasEnabled) throw new IllegalStateException("Integracao Asaas desabilitada");

    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
    if (tenant == null) throw new IllegalArgumentException("Tenant nao encontrado");

    asaasService.ensureCustomerForTenant(tenant, request.cpfCnpj);

    AsaasDtos.CreatePaymentRequest paymentRequest = new AsaasDtos.CreatePaymentRequest();
    BigDecimal amount = resolveAmount(request);
    paymentRequest.customer = tenant.getAsaasCustomerId();
    paymentRequest.billingType = "PIX";
    paymentRequest.value = amount;
    paymentRequest.dueDate =
        request.nextDueDate != null && !request.nextDueDate.isBlank()
            ? request.nextDueDate.trim()
            : LocalDate.now(ZONE_BR).plusDays(1).toString();
    paymentRequest.description =
        request.description != null && !request.description.isBlank()
            ? request.description.trim()
            : "Cobranca Pix Azzo Agenda Pro";
    paymentRequest.externalReference =
        asaasService.buildExternalReference(tenant.getId(), request.productId, "PIX");

    AsaasDtos.PaymentResponse payment =
        createPaymentWithInvalidCustomerRetry(tenant, request, paymentRequest);
    AsaasDtos.PixQrCodeResponse pix = asaasClient.getPixQrCode(requiredApiKey(), payment.id);

    Payment entity =
        paymentRepository.buscarPorAsaasPaymentId(payment.id).orElseGet(Payment::new);
    entity.setTenantId(tenantId);
    entity.setAsaasPaymentId(payment.id);
    entity.setStatus(toPaymentStatus(payment.status, StatusPayment.PENDING));
    entity.setBillingType("PIX");
    entity.setAmountCents(toCents(amount));
    entity.setNetAmountCents(payment.netValue != null ? toCents(payment.netValue) : null);
    entity.setDueDate(parseLocalDate(payment.dueDate));
    entity.setReferenceMonth(formatReferenceMonth(entity.getDueDate()));
    entity.setInvoiceUrl(payment.invoiceUrl);
    entity.setBankSlipUrl(payment.bankSlipUrl);
    entity.setPixQrCode(pix != null ? pix.encodedImage : null);
    entity.setPixPayload(pix != null ? pix.payload : null);
    entity.setExpiresAt(pix != null ? parseInstantOrDate(pix.expirationDate) : null);
    entity.setBoletoIdentificationField(null);
    entity.setBoletoBarCode(null);
    entity.setBoletoNossoNumero(null);
    if (entity.getId() == null) paymentRepository.save(entity);

    BillingDtos.SubscriptionResponse response = new BillingDtos.SubscriptionResponse();
    response.tenantId = tenantId.toString();
    response.customerId = tenant.getAsaasCustomerId();
    response.subscriptionId = null;
    response.status = entity.getStatus() != null ? entity.getStatus().name() : null;
    response.billingType = "PIX";
    response.nextDueDate = entity.getDueDate() != null ? entity.getDueDate().toString() : null;
    response.referenceMonth = entity.getReferenceMonth();
    response.expiresAt = entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null;
    response.amount = NumericUtil.fromCents(entity.getAmountCents());
    response.paymentId = entity.getAsaasPaymentId();
    response.paymentStatus = entity.getStatus() != null ? entity.getStatus().name() : null;
    response.invoiceUrl = entity.getInvoiceUrl();
    response.bankSlipUrl = entity.getBankSlipUrl();
    response.pixQrCodeBase64 = entity.getPixQrCode();
    response.pixPayload = entity.getPixPayload();
    return response;
  }

  /**
   * Um {@code invalid_customer} do Asaas significa que o {@code asaasCustomerId} gravado no tenant
   * aponta para um customer que nao existe mais. O original limpa o id, recria o customer e tenta
   * uma unica vez — comportamento preservado, inclusive o fato de a segunda falha subir com o
   * status HTTP da <b>retentativa</b>.
   */
  private AsaasDtos.PaymentResponse createPaymentWithInvalidCustomerRetry(
      Tenant tenant,
      BillingDtos.CreateSubscriptionRequest request,
      AsaasDtos.CreatePaymentRequest paymentRequest) {
    try {
      return asaasClient.createPayment(requiredApiKey(), paymentRequest);
    } catch (RestClientResponseException e) {
      String body = safeResponseBody(e);
      if (isInvalidCustomerError(body)) {
        LOG.warn(
            "Asaas PIX retornou invalid_customer para tenant={}. Recriando customer e tentando novamente.",
            tenant.getId());
        tenant.setAsaasCustomerId(null);
        asaasService.ensureCustomerForTenant(tenant, request.cpfCnpj);
        paymentRequest.customer = tenant.getAsaasCustomerId();
        try {
          return asaasClient.createPayment(requiredApiKey(), paymentRequest);
        } catch (RestClientResponseException retryEx) {
          LOG.error(
              "asaas_pix_retry_failed tenant={} status={} traceId={}",
              tenant.getId(),
              retryEx.getStatusCode().value(),
              CorrelatedLogging.traceId());
          throw new AsaasException(
              composeAsaasErrorMessage(
                  "Falha ao criar cobranca Pix no Asaas", safeResponseBody(retryEx)),
              retryEx.getStatusCode().value(),
              retryEx);
        }
      }
      LOG.warn(
          "asaas_pix_create_failed tenant={} status={} traceId={}",
          tenant.getId(),
          e.getStatusCode().value(),
          CorrelatedLogging.traceId());
      throw new AsaasException(
          composeAsaasErrorMessage("Falha ao criar cobranca Pix no Asaas", body),
          e.getStatusCode().value(),
          e);
    } catch (Exception e) {
      LOG.error(
          "asaas_pix_unexpected_error tenant={} traceId={}",
          tenant.getId(),
          CorrelatedLogging.traceId(),
          e);
      throw new AsaasException("Falha de integracao com Asaas", 502, e);
    }
  }

  private String requiredApiKey() {
    if (asaasApiKey == null || asaasApiKey.isBlank() || "__unset__".equals(asaasApiKey)) {
      throw new IllegalStateException("ASAAS_API_KEY nao configurada");
    }
    return asaasApiKey.trim();
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

  private Instant parseInstantOrDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value.trim());
    } catch (Exception ignored) {
      try {
        return LocalDate.parse(value.trim()).atStartOfDay(ZONE_BR).toInstant();
      } catch (Exception e) {
        return null;
      }
    }
  }

  private String normalizeStatus(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
  }

  private StatusPayment toPaymentStatus(String raw, StatusPayment fallback) {
    String normalized = normalizeStatus(raw, fallback.name());
    return StatusPayment.fromValue(normalized);
  }

  private boolean isInvalidCustomerError(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) return false;
    return responseBody.contains("\"invalid_customer\"")
        || responseBody.toLowerCase().contains("cliente invalido");
  }

  private String composeAsaasErrorMessage(String base, String responseBody) {
    if (responseBody == null || responseBody.isBlank()) return base;
    return base + ": " + responseBody;
  }

  private String safeResponseBody(RestClientResponseException ex) {
    try {
      return ex != null ? ex.getResponseBodyAsString() : null;
    } catch (Exception ignored) {
      return null;
    }
  }
}
