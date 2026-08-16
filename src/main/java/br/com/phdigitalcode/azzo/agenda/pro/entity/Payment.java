package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.converter.StatusPaymentConverter;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPayment;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/billing/domain/entity/Payment.java}. Tabela {@code payments}.
 *
 * <p>O {@code @ManyToOne tenant} do original foi descartado (ninguem navega). O
 * {@code @ManyToOne subscription} <b>foi mantido</b> porque
 * {@code PaymentRepository.existePagamentoMesmoTipoEValorNaoVencido} navega
 * {@code subscription.productId} / {@code subscription.planCode} dentro da propria query.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "subscription_id")
  private UUID subscriptionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_id", insertable = false, updatable = false)
  private Subscription subscription;

  @Column(name = "asaas_payment_id", nullable = false, unique = true)
  private String asaasPaymentId;

  @Column(name = "asaas_subscription_id")
  private String asaasSubscriptionId;

  @Convert(converter = StatusPaymentConverter.class)
  @Column(name = "status", nullable = false)
  private StatusPayment status;

  @Column(name = "billing_type")
  private String billingType;

  @Column(name = "amount_cents", nullable = false)
  private long amountCents;

  @Column(name = "net_amount_cents")
  private Long netAmountCents;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "reference_month")
  private String referenceMonth;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "invoice_url")
  private String invoiceUrl;

  @Column(name = "bank_slip_url")
  private String bankSlipUrl;

  @Column(name = "boleto_identification_field")
  private String boletoIdentificationField;

  @Column(name = "boleto_bar_code")
  private String boletoBarCode;

  @Column(name = "boleto_nosso_numero")
  private String boletoNossoNumero;

  @Column(name = "pix_qr_code")
  private String pixQrCode;

  @Column(name = "pix_payload")
  private String pixPayload;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
