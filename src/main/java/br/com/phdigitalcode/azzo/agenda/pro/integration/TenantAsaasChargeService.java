package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantPaymentSettings;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantPaymentSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code infrastructure/payment/TenantAsaasChargeService.java}: gera cobranca PIX na conta
 * Asaas do proprio TENANT (salao) para cobrar o CLIENTE FINAL — distinto do {@code AsaasPixService}
 * da plataforma (que cobra o tenant pela assinatura SaaS e ainda nao foi migrado, entra com
 * {@code billing}). Reaproveitado por sinal de reserva ({@code AppointmentDeposit}) e pagamento de
 * comanda ({@code ComandaPagamento}).
 *
 * <p>O {@code WebApplicationException} capturado no original (JAX-RS) vira
 * {@link RestClientResponseException} — mesma semantica: erro HTTP da chamada ao Asaas vira
 * {@link IllegalStateException} com a mesma mensagem para o usuario final.
 */
@Service
public class TenantAsaasChargeService {

  private static final Logger LOG = LoggerFactory.getLogger(TenantAsaasChargeService.class);
  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private final AsaasClient asaasClient;
  private final TenantPaymentSettingsRepository tenantPaymentSettingsRepository;
  private final EncryptionService encryptionService;

  public TenantAsaasChargeService(
      AsaasClient asaasClient,
      TenantPaymentSettingsRepository tenantPaymentSettingsRepository,
      EncryptionService encryptionService) {
    this.asaasClient = asaasClient;
    this.tenantPaymentSettingsRepository = tenantPaymentSettingsRepository;
    this.encryptionService = encryptionService;
  }

  public record PixCharge(String asaasPaymentId, String pixPayload, Instant expiresAt) {}

  /**
   * @throws IllegalStateException tenant sem conta Asaas ativa configurada, ou falha na chamada ao
   *     Asaas.
   * @throws IllegalArgumentException cliente sem CPF/CNPJ na primeira cobranca (exigido pelo Asaas).
   */
  public PixCharge criarCobrancaPix(
      UUID tenantId,
      Cliente cliente,
      BigDecimal amount,
      String description,
      String externalReference) {
    String apiKey = resolveApiKeyAtivaOuFalhar(tenantId);
    ensureAsaasCustomer(apiKey, cliente);

    AsaasDtos.CreatePaymentRequest paymentRequest = new AsaasDtos.CreatePaymentRequest();
    paymentRequest.customer = cliente.getAsaasCustomerId();
    paymentRequest.billingType = "PIX";
    paymentRequest.value = amount;
    paymentRequest.dueDate = LocalDate.now(ZONE_BR).plusDays(1).toString();
    paymentRequest.description = description;
    paymentRequest.externalReference = externalReference;

    AsaasDtos.PaymentResponse payment;
    try {
      payment = asaasClient.createPayment(apiKey, paymentRequest);
    } catch (RestClientResponseException e) {
      LOG.warn(
          CorrelatedLogging.context(
              "Falha ao criar cobranca Pix no Asaas do tenant",
              "tenantId", tenantId,
              "externalReference", externalReference,
              "status", e.getStatusCode().value()));
      throw new IllegalStateException(
          "Nao foi possivel gerar a cobranca Pix. Tente novamente em instantes.", e);
    }

    AsaasDtos.PixQrCodeResponse pix = asaasClient.getPixQrCode(apiKey, payment.id);

    LOG.info(
        CorrelatedLogging.context(
            "Cobranca Pix criada na conta do tenant",
            "tenantId", tenantId,
            "externalReference", externalReference,
            "asaasPaymentId", payment.id));

    return new PixCharge(
        payment.id,
        pix != null ? pix.payload : null,
        pix != null ? parseInstantOrDate(pix.expirationDate) : null);
  }

  public String resolveApiKeyAtivaOuFalhar(UUID tenantId) {
    TenantPaymentSettings config =
        tenantPaymentSettingsRepository.findByTenantId(tenantId).orElse(null);
    if (config == null || !config.isAtivo() || trimToNull(config.getApiKeyEnc()) == null) {
      throw new IllegalStateException(
          "O salao ainda nao configurou a conta de recebimento (Asaas) em Configuracoes.");
    }
    String apiKey = trimToNull(encryptionService.decrypt(config.getApiKeyEnc()));
    if (apiKey == null) {
      throw new IllegalStateException(
          "Chave de recebimento do salao invalida. Contate o estabelecimento.");
    }
    return apiKey;
  }

  /** @throws IllegalArgumentException cliente sem CPF/CNPJ na primeira cobranca (exigido pelo Asaas). */
  public void ensureAsaasCustomer(String apiKey, Cliente cliente) {
    if (trimToNull(cliente.getAsaasCustomerId()) != null) return;
    if (trimToNull(cliente.getCpfCnpj()) == null) {
      throw new IllegalArgumentException(
          "CPF/CNPJ do cliente e obrigatorio para gerar a cobranca Pix.");
    }
    AsaasDtos.CreateCustomerRequest request = new AsaasDtos.CreateCustomerRequest();
    request.name = cliente.getName();
    request.email = cliente.getEmail();
    request.mobilePhone = cliente.getPhone();
    request.cpfCnpj = cliente.getCpfCnpj();
    request.externalReference = "client:" + cliente.getId();
    AsaasDtos.CustomerResponse response = asaasClient.createCustomer(apiKey, request);
    cliente.setAsaasCustomerId(response.id);
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

  private String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() || "__unset__".equals(normalized) ? null : normalized;
  }
}
