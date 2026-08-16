package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/customers/domain/entity/Cliente.java}. Tabela {@code clients}. */
@Entity
@Table(name = "clients")
@Getter
@Setter
public class Cliente {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "email")
  private String email;

  @Column(name = "phone")
  private String phone;

  @Column(name = "avatar")
  private String avatar;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "notes")
  private String notes;

  @Column(name = "zip_code")
  private String zipCode;

  @Column(name = "street")
  private String street;

  @Column(name = "number")
  private String number;

  @Column(name = "complement")
  private String complement;

  @Column(name = "neighborhood")
  private String neighborhood;

  @Column(name = "city")
  private String city;

  @Column(name = "state")
  private String state;

  @Column(name = "cpf_cnpj")
  private String cpfCnpj;

  @Column(name = "asaas_customer_id")
  private String asaasCustomerId;

  @Column(name = "loyalty_points", nullable = false)
  private int loyaltyPoints = 0;

  @Column(name = "client_type", nullable = false)
  private String clientType = "PF";

  @Column(name = "whatsapp_opt_in", nullable = false)
  private Boolean whatsappOptIn = false;

  @Column(name = "whatsapp_opt_in_at")
  private Instant whatsappOptInAt;

  @Column(name = "whatsapp_opt_out", nullable = false)
  private Boolean whatsappOptOut = false;

  @Column(name = "whatsapp_opt_out_at")
  private Instant whatsappOptOutAt;

  @Column(name = "anonymized_at")
  private Instant anonymizedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Cliente other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
