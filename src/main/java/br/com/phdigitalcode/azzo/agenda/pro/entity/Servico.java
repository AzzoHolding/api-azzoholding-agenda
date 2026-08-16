package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/services/domain/entity/Servico.java}. Tabela {@code services}.
 *
 * <p>{@code categoryRef} (ManyToOne de conveniencia no original) foi substituido pela coluna
 * escalar {@code categoryId} + resolucao pelo nome via {@code ServiceCategoryRepository} no
 * service, igual ao original faz para popular {@code categoryRef.name} na resposta.
 * {@code profissionais} e {@code @ManyToMany} EAGER (dono do relacionamento) pelo mesmo motivo de
 * {@code Profissional.specialties}.
 */
@Entity
@Table(name = "services")
@Getter
@Setter
public class Servico {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "duration", nullable = false)
  private int duration;

  @Column(name = "price", nullable = false)
  private BigDecimal price;

  @Column(name = "category_id")
  private UUID categoryId;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "service_professionals",
      joinColumns = @JoinColumn(name = "service_id"),
      inverseJoinColumns = @JoinColumn(name = "professional_id"))
  private Set<Profissional> profissionais = new HashSet<>();

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Column(name = "requires_deposit", nullable = false)
  private boolean requiresDeposit = false;

  /** "PERCENTUAL" ou "FIXO" - obrigatorio quando requiresDeposit=true. */
  @Column(name = "deposit_type")
  private String depositType;

  @Column(name = "deposit_value")
  private BigDecimal depositValue;

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
    if (!(o instanceof Servico other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
