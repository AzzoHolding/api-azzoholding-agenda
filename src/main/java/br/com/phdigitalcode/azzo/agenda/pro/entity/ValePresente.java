package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Vale-presente vendido no PDV (V181). O saldo so muda por UPDATE condicional (ValePresenteRepository). */
@Entity
@Table(name = "vales_presente")
@Getter
@Setter
public class ValePresente {

  public static final String STATUS_ATIVO = "ATIVO";
  public static final String STATUS_ESGOTADO = "ESGOTADO";
  public static final String STATUS_VENCIDO = "VENCIDO";
  public static final String STATUS_CANCELADO = "CANCELADO";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "codigo", nullable = false)
  private String codigo;

  @Column(name = "valor_inicial", nullable = false)
  private BigDecimal valorInicial;

  @Column(name = "saldo", nullable = false, updatable = false)
  private BigDecimal saldo;

  @Column(name = "expira_em", nullable = false)
  private LocalDate expiraEm;

  @Column(name = "comprador_client_id")
  private UUID compradorClientId;

  @Column(name = "destinatario_nome")
  private String destinatarioNome;

  @Column(name = "mensagem")
  private String mensagem;

  @Column(name = "comanda_venda_id")
  private UUID comandaVendaId;

  @Column(name = "comanda_item_id")
  private UUID comandaItemId;

  @Column(name = "status", nullable = false, updatable = false)
  private String status = STATUS_ATIVO;

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
