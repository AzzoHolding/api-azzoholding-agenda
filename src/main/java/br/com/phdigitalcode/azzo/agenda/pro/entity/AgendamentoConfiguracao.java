package br.com.phdigitalcode.azzo.agenda.pro.entity;

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
 * Espelha {@code modules/scheduling/domain/entity/AgendamentoConfiguracao.java}. Tabela
 * {@code agendamento_configuracao}.
 *
 * <p>O {@code @ManyToOne Tenant} do original nao foi portado (mesma decisao das demais entidades
 * migradas): ele so existia para o mapeamento, nunca e navegado.
 */
@Entity
@Table(name = "agendamento_configuracao")
@Getter
@Setter
public class AgendamentoConfiguracao {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, unique = true)
  private UUID tenantId;

  @Column(name = "permitir_agendamento_manual_com_conflito", nullable = false)
  private Boolean permitirAgendamentoManualComConflito;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (permitirAgendamentoManualComConflito == null) {
      permitirAgendamentoManualComConflito = Boolean.FALSE;
    }
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AgendamentoConfiguracao other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
