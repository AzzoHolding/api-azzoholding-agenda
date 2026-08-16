package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * Espelha {@code modules/settings/domain/entity/TenantSpecialClosureDateEntity.java}. Tabela
 * {@code tenant_special_closure_dates} (campos de fechamento parcial/por profissional vieram em
 * V85).
 *
 * <p><b>LGPD</b>: o campo {@code reason} NUNCA deve aparecer em log nem em payload de auditoria.
 * Em qualquer chamada de log use apenas {@code id} e {@code closureDate}.
 *
 * <p><b>Diferenca em relacao ao original</b>: as associacoes {@code @ManyToOne tenant} e
 * {@code @ManyToOne professional} (esta ultima por {@code @JoinColumns} composto
 * {@code tenant_id + professional_id}) foram descartadas, seguindo a mesma decisao ja tomada para
 * {@code Agendamento} na Etapa 6. O unico consumidor da associacao era
 * {@code SpecialClosureService.toDto}, que preenchia {@code professionalName}; no porte esse nome e
 * resolvido via {@code ProfissionalRepository} filtrando por tenant. Mesmo resultado, consulta a
 * mais.
 */
@Entity
@Table(name = "tenant_special_closure_dates")
@Getter
@Setter
public class TenantSpecialClosureDate {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "closure_date", nullable = false)
  private LocalDate closureDate;

  @Column(name = "reason", length = 160)
  private String reason;

  /** HOLIDAY, VACATION, RECESS, INTERNAL_EVENT, MANUAL. */
  @Column(name = "closure_type", length = 30)
  private String closureType;

  @Column(name = "all_day", nullable = false)
  private boolean allDay = true;

  @Column(name = "start_time")
  private LocalTime startTime;

  @Column(name = "end_time")
  private LocalTime endTime;

  /** Profissional impactado. {@code null} = fechamento do salao inteiro. */
  @Column(name = "professional_id")
  private UUID professionalId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
