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

/** Um ranking dos profissionais montado pelo dono (V166). Excluir so desliga ({@code ativo=false}). */
@Entity
@Table(name = "ranking_profissionais")
@Getter
@Setter
public class RankingProfissionais {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "nome", nullable = false)
  private String nome;

  /** PONTOS ou PESO. */
  @Column(name = "modo", nullable = false)
  private String modo;

  /** As regras do dono: [{chave, pontos, aCada, peso}]. */
  @Column(name = "criterios_json", nullable = false, columnDefinition = "TEXT")
  private String criteriosJson;

  @Column(name = "visivel_para_profissional", nullable = false)
  private boolean visivelParaProfissional;

  @Column(name = "ativo", nullable = false)
  private boolean ativo = true;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    Instant agora = Instant.now();
    if (createdAt == null) createdAt = agora;
    updatedAt = agora;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
