package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ModoDeConciliacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Uma conciliacao finalizada (V158): a seguinte do mesmo mes comeca em {@code endsAt}. */
@Entity
@Table(name = "reconciliation_periods")
@Getter
@Setter
public class PeriodoDeConciliacao {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode", nullable = false, length = 20)
  private ModoDeConciliacao mode;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  /** Fim exclusivo. */
  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "reconciled_count", nullable = false)
  private int reconciledCount;

  @Column(name = "divergent_count", nullable = false)
  private int divergentCount;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
