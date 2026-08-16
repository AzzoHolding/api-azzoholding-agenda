package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Porte verbatim de {@code modules/billing/api/dto/BillingDtos.java} — mesmos nomes de campo,
 * mesmas anotacoes de validacao, mesmo formato JSON.
 *
 * <p>Os {@code @Deprecated} de {@code planCode} e {@code amountCents} sao do original e foram
 * mantidos: o backend ainda le os dois (o {@code AsaasService} cai em {@code amountCents} quando
 * {@code amount} vem nulo).
 */
public final class BillingDtos {

  private BillingDtos() {}

  public static class CreateSubscriptionRequest {
    @NotBlank
    public String productId;

    /**
     * @deprecated Use productId (UUID do produto).
     */
    @Deprecated
    public String planCode;

    public String description;

    // Definido pelo backend a partir do plano selecionado.
    public BigDecimal amount;

    /**
     * @deprecated Use amount em reais.
     */
    @Deprecated
    public Long amountCents;

    @NotBlank
    @Pattern(regexp = "PIX|BOLETO|CREDIT_CARD")
    public String billingType;

    public String cpfCnpj;
    public String nextDueDate;

    @Valid
    public CreditCard creditCard;

    @Valid
    public CreditCardHolderInfo creditCardHolderInfo;
  }

  public static class CreditCard {
    @NotBlank public String holderName;
    @NotBlank public String number;
    @NotBlank public String expiryMonth;
    @NotBlank public String expiryYear;
    @NotBlank public String ccv;
  }

  public static class CreditCardHolderInfo {
    @NotBlank public String name;
    @NotBlank public String email;
    @NotBlank public String cpfCnpj;
    @NotBlank public String postalCode;
    @NotBlank public String addressNumber;
    public String addressComplement;
    @NotBlank public String phone;
  }

  public static class SubscriptionResponse {
    public String tenantId;
    public String customerId;
    public String subscriptionId;
    public String status;
    public String billingType;
    public String nextDueDate;
    public String referenceMonth;
    public String expiresAt;
    public BigDecimal amount;
    public String paymentId;
    public String paymentStatus;
    public String invoiceUrl;
    public String bankSlipUrl;
    public String boletoIdentificationField;
    public String boletoBarCode;
    public String boletoNossoNumero;
    public String pixQrCodeBase64;
    public String pixPayload;
  }

  public static class CurrentSubscriptionResponse {
    public String id;
    public String tenantId;
    public String customerId;
    public String subscriptionId;
    public String productId;
    public String planCode;
    public String billingType;
    public String status;
    public String cycle;
    public BigDecimal amount;
    public String nextDueDate;
    public String currentPaymentId;
    public String currentPaymentStatus;
    public String currentPaymentDueDate;
    public String licenseStatus;
    public String paymentLink;
    public String cancelledAt;
    public String createdAt;
    public String updatedAt;
  }

  public static class PaymentItemResponse {
    public String id;
    public String tenantId;
    public String asaasPaymentId;
    public String asaasSubscriptionId;
    public String billingType;
    public String status;
    public BigDecimal amount;
    public BigDecimal netAmount;
    public String dueDate;
    public String referenceMonth;
    public String paidAt;
    public String expiresAt;
    public String invoiceUrl;
    public String bankSlipUrl;
    public String boletoIdentificationField;
    public String boletoBarCode;
    public String boletoNossoNumero;
    public String pixQrCodeBase64;
    public String pixPayload;
    public String createdAt;
    public String updatedAt;
  }

  public static class PaymentsListResponse {
    public List<PaymentItemResponse> items = new ArrayList<>();
  }

  public static class AdminExpireLicenseRequest {
    public String tenantId;
    public Integer minutesAgo;
  }

  public static class AdminReleaseLicenseRequest {
    public String tenantId;
    public String productId;
    public Integer validityDays;
    public String paymentId;
  }

  public static class AdminMarkPaymentReceivedRequest {
    @NotBlank public String tenantId;
    @NotBlank public String paymentId;
    public Integer validityDays;
  }

  public static class AdminTenantItemResponse {
    public String tenantId;
    public String name;
    public String slug;
    public String email;
    public String phone;
    public String planStatus;
  }

  public static class AdminTenantListResponse {
    public List<AdminTenantItemResponse> items = new ArrayList<>();
  }

  public static class AdminBillingActionResponse {
    public String status;
    public String tenantId;
    public String licenseStatus;
    public String message;
    public String paymentId;
    public String productId;
    public String validUntil;
  }

  public static class CancelSubscriptionResponse {
    public String subscriptionId;
    public String status;
    public String cancelledAt;
    public String message;
  }

  public static class ExtendTrialRequest {
    @NotBlank public String tenantId;
    @NotNull public Integer extraDays;
  }

  public static class ExtendTrialResponse {
    public String tenantId;
    public String validUntil;
    public String message;
  }

  public static class LicenseEventResponse {
    public String id;
    public String tenantId;
    public String eventType;
    public String actorUserId;
    public String productId;
    public String validUntil;
    public String metadata;
    public String createdAt;
  }

  public static class LicenseHistoryResponse {
    public String tenantId;
    public List<LicenseEventResponse> events = new ArrayList<>();
  }

  public static class LicencasAdminReportItem {
    public String tenantId;
    public String tenantNome;
    public String tenantEmail;
    public String planStatus;
    public String billingType;
    public String validUntil;
    public int diasRestantes;
    public boolean vencido;
    public String createdAt;
  }

  public static class LicencasAdminReportResponse {
    public int totalTenants;
    public int ativosCount;
    public int trialCount;
    public int expiradosCount;
    public int bloqueadosCount;
    public List<LicencasAdminReportItem> items = new ArrayList<>();
  }
}
