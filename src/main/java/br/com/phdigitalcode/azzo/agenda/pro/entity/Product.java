package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code domain/entity/Product.java}. Tabela {@code products}.
 *
 * <p>O {@code @OneToOne capability} do original nao foi mapeado: o unico consumidor
 * ({@code CheckoutService.toProductResponse}) ja busca a capability pelo
 * {@code ProductCapabilityRepository}, e a associacao so traria risco de N+1/lazy.
 *
 * <p>O original nao gera o {@code id} no {@code @PrePersist} — {@code products} e catalogo
 * populado por migration/administracao, nunca criado pelo fluxo de checkout. Preservado.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
public class Product {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Column(name = "price_cents", nullable = false)
  private BigDecimal priceCents;

  @Column(name = "validity_months", nullable = false)
  private int validityMonths = 1;

  @Column(name = "validity_days")
  private Integer validityDays;

  @Column(name = "is_trial", nullable = false)
  private boolean isTrial = false;

  @Column(name = "highlight")
  private String highlight;

  @Column(name = "features_json", nullable = false)
  private String featuresJson = "[]";

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "priority", nullable = false)
  private int priority = 0;

  /**
   * Plano exclusivo para venda interna: nao aparece no checkout publico e so pode ser ativado por
   * fluxo interno autorizado (api-gerenciamento), com auditoria.
   */
  @Column(name = "exclusivo_venda_interna", nullable = false)
  private boolean exclusivoVendaInterna = false;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
