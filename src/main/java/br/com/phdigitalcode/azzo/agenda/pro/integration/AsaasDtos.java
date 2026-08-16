package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Porte verbatim de {@code infrastructure/payment/AsaasDtos.java} — mesmos nomes de campo, porque
 * sao o contrato JSON da API do Asaas.
 *
 * <p>Apenas os tipos consumidos pelos modulos ja migrados estao aqui. Os payloads de webhook
 * ({@code WebhookPayload}/{@code WebhookPayment}/{@code WebhookSubscription}) entram junto com o
 * modulo {@code billing}.
 */
public final class AsaasDtos {

  private AsaasDtos() {}

  public static class CreateCustomerRequest {
    public String name;
    public String email;
    public String phone;
    public String mobilePhone;
    public String cpfCnpj;
    public String externalReference;
    public Boolean notificationDisabled = false;
  }

  public static class CustomerResponse {
    public String id;
    public String name;
    public String email;
    public String phone;
    public String mobilePhone;
    public String cpfCnpj;
  }

  public static class CreateSubscriptionRequest {
    public String customer;
    public String billingType;
    public BigDecimal value;
    public String nextDueDate;
    public String cycle = "MONTHLY";
    public String description;
    public String externalReference;
    public CreditCard creditCard;
    public CreditCardHolderInfo creditCardHolderInfo;
    public String remoteIp;
  }

  public static class CreatePaymentRequest {
    public String customer;
    public String billingType;
    public BigDecimal value;
    public String dueDate;
    public String description;
    public String externalReference;
  }

  public static class CreditCard {
    public String holderName;
    public String number;
    public String expiryMonth;
    public String expiryYear;
    public String ccv;
  }

  public static class CreditCardHolderInfo {
    public String name;
    public String email;
    public String cpfCnpj;
    public String postalCode;
    public String addressNumber;
    public String addressComplement;
    public String phone;
  }

  public static class SubscriptionResponse {
    public String id;
    public String customer;
    public String billingType;
    public BigDecimal value;
    public String cycle;
    public String status;
    public String nextDueDate;
    public String description;
  }

  public static class PaymentsResponse {
    public List<PaymentResponse> data;
  }

  public static class PaymentResponse {
    public String id;
    public String customer;
    public String subscription;
    public String billingType;
    public String status;
    public BigDecimal value;
    public BigDecimal netValue;
    public String dueDate;
    public String paymentDate;
    public String invoiceUrl;
    public String bankSlipUrl;
  }

  public static class PixQrCodeResponse {
    public String encodedImage;
    public String payload;
    public String expirationDate;
  }

  public static class IdentificationFieldResponse {
    public String identificationField;
    public String nossoNumero;
    public String barCode;
  }

  public static class WebhookPayload {
    public String event;
    public WebhookPayment payment;
    public WebhookSubscription subscription;
  }

  public static class WebhookPayment {
    public String id;
    public String customer;
    public String subscription;
    public String externalReference;
    public String billingType;
    public String status;
    public BigDecimal value;
    public BigDecimal netValue;
    public String dueDate;
    public String paymentDate;
    public String invoiceUrl;
    public String bankSlipUrl;
  }

  public static class WebhookSubscription {
    public String id;
    public String customer;
    public String status;
    public BigDecimal value;
    public String cycle;
    public String nextDueDate;
  }
}
