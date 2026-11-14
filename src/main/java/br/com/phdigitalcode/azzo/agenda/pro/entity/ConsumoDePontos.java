package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** De qual credito saiu um resgate (V178): para devolver os pontos com o vencimento original. */
@Entity
@Table(name = "consumos_de_pontos")
@Getter
@Setter
public class ConsumoDePontos {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "resgate_id", nullable = false)
  private UUID resgateId;

  @Column(name = "credito_id", nullable = false)
  private UUID creditoId;

  @Column(name = "comanda_id")
  private UUID comandaId;

  /** Quanto deste credito o resgate usou e ainda nao voltou. */
  @Column(name = "pontos", nullable = false)
  private int pontos;

  @Column(name = "devolvido", nullable = false)
  private boolean devolvido;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
