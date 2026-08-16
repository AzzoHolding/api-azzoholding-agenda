package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusFechamentoCaixa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/finance/domain/entity/FechamentoCaixa.java}. Tabela {@code cash_closings}. */
@Entity
@Table(name = "cash_closings")
@Getter
@Setter
public class FechamentoCaixa {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private StatusFechamentoCaixa status;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "opened_by")
  private UUID openedBy;

  @Column(name = "opening_notes")
  private String openingNotes;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "closed_by")
  private UUID closedBy;

  @Column(name = "closing_notes")
  private String closingNotes;

  @Column(name = "expected_totals_json", nullable = false)
  private String expectedTotalsJson;

  @Column(name = "counted_totals_json", nullable = false)
  private String countedTotalsJson;

  @Column(name = "difference_totals_json", nullable = false)
  private String differenceTotalsJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (status == null) status = StatusFechamentoCaixa.OPEN;
    if (expectedTotalsJson == null || expectedTotalsJson.isBlank()) expectedTotalsJson = "{}";
    if (countedTotalsJson == null || countedTotalsJson.isBlank()) countedTotalsJson = "{}";
    if (differenceTotalsJson == null || differenceTotalsJson.isBlank()) differenceTotalsJson = "{}";
    if (createdAt == null) createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FechamentoCaixa other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
