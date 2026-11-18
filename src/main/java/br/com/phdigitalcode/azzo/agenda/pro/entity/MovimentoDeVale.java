package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Extrato do vale-presente (V181), no modelo de movimentos_de_pontos (V175). */
@Entity
@Table(name = "movimentos_de_vale")
@Getter
@Setter
public class MovimentoDeVale {

  public static final String VENDA = "VENDA";
  public static final String USO = "USO";
  public static final String DEVOLUCAO = "DEVOLUCAO";
  public static final String ESTORNO = "ESTORNO";
  public static final String EXPIRACAO = "EXPIRACAO";
  public static final String AJUSTE = "AJUSTE";
  public static final String PRORROGACAO = "PRORROGACAO";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "vale_id", nullable = false)
  private UUID valeId;

  @Column(name = "tipo", nullable = false)
  private String tipo;

  @Column(name = "valor", nullable = false)
  private BigDecimal valor;

  @Column(name = "saldo_apos", nullable = false)
  private BigDecimal saldoApos;

  @Column(name = "comanda_id")
  private UUID comandaId;

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
