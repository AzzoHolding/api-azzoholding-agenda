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

/** Uma cobranca em qualquer operadora: Pix, link, cartao online ou ordem para a maquininha (V167). */
@Entity
@Table(name = "cobrancas")
@Getter
@Setter
public class Cobranca {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "conta_id", nullable = false)
  private UUID contaId;

  @Column(name = "terminal_id")
  private UUID terminalId;

  @Column(name = "provedor", nullable = false)
  private String provedor;

  /** COMANDA, SINAL, ASSINATURA, LINK, TESTE. */
  @Column(name = "origem_tipo", nullable = false)
  private String origemTipo;

  @Column(name = "origem_id")
  private UUID origemId;

  /** PIX, CREDITO, DEBITO, LINK. */
  @Column(name = "meio", nullable = false)
  private String meio;

  @Column(name = "parcelas", nullable = false)
  private int parcelas = 1;

  @Column(name = "valor", nullable = false)
  private BigDecimal valor;

  @Column(name = "taxa")
  private BigDecimal taxa;

  @Column(name = "valor_liquido")
  private BigDecimal valorLiquido;

  @Column(name = "previsao_recebimento")
  private LocalDate previsaoRecebimento;

  @Column(name = "status", nullable = false)
  private String status = "CRIADA";

  @Column(name = "id_na_operadora")
  private String idNaOperadora;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "pix_copia_e_cola", columnDefinition = "TEXT")
  private String pixCopiaECola;

  @Column(name = "link_de_pagamento")
  private String linkDePagamento;

  @Column(name = "expira_em")
  private Instant expiraEm;

  @Column(name = "nsu")
  private String nsu;

  @Column(name = "autorizacao")
  private String autorizacao;

  @Column(name = "bandeira")
  private String bandeira;

  @Column(name = "cartao_final4")
  private String cartaoFinal4;

  @Column(name = "valor_estornado", nullable = false)
  private BigDecimal valorEstornado = BigDecimal.ZERO;

  @Column(name = "motivo_recusa")
  private String motivoRecusa;

  @Column(name = "criada_por")
  private UUID criadaPor;

  @Column(name = "paga_em")
  private Instant pagaEm;

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

  /** Ja chegou a um fim: nao muda mais por aviso de "aguardando". */
  public boolean encerrada() {
    return switch (status) {
      case "PAGA", "RECUSADA", "CANCELADA", "EXPIRADA", "ESTORNADA" -> true;
      default -> false;
    };
  }
}
