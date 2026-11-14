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

/** Um movimento de pontos de fidelidade da cliente (V175). O saldo fica em {@code clients.loyalty_points}. */
@Entity
@Table(name = "movimentos_de_pontos")
@Getter
@Setter
public class MovimentoDePontos {

  public static final String SALDO_INICIAL = "SALDO_INICIAL";
  public static final String CREDITO = "CREDITO";
  public static final String RESGATE = "RESGATE";
  public static final String DEVOLUCAO = "DEVOLUCAO";
  public static final String ESTORNO = "ESTORNO";
  public static final String EXPIRACAO = "EXPIRACAO";
  public static final String AJUSTE = "AJUSTE";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "tipo", nullable = false)
  private String tipo;

  /** Positivo entra, negativo sai. */
  @Column(name = "pontos", nullable = false)
  private int pontos;

  @Column(name = "saldo_apos", nullable = false)
  private int saldoApos;

  @Column(name = "comanda_id")
  private UUID comandaId;

  /** So nos creditos: quanto deste credito ainda nao foi usado nem venceu. */
  @Column(name = "pontos_restantes", nullable = false)
  private int pontosRestantes;

  /** Vencimento do credito (validade do salao no momento do credito); vazio = nao vence. */
  @Column(name = "expira_em")
  private Instant expiraEm;

  @Column(name = "motivo")
  private String motivo;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
