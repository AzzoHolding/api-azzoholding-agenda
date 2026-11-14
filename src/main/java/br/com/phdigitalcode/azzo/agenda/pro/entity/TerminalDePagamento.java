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

/** Maquininha (nuvem) ou pinpad do TEF local, ligada a uma conta de pagamento (V167). */
@Entity
@Table(name = "terminais_de_pagamento")
@Getter
@Setter
public class TerminalDePagamento {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "conta_id", nullable = false)
  private UUID contaId;

  @Column(name = "serial", nullable = false)
  private String serial;

  @Column(name = "modelo")
  private String modelo;

  @Column(name = "apelido", nullable = false)
  private String apelido;

  /** NUVEM ou TEF_LOCAL. */
  @Column(name = "tipo", nullable = false)
  private String tipo = "NUVEM";

  @Column(name = "ativo", nullable = false)
  private boolean ativo = true;

  /** Azzo Conector (TEF_LOCAL, V171): so os hashes do token e do codigo de pareamento. */
  @Column(name = "token_hash")
  private String tokenHash;

  @Column(name = "codigo_pareamento_hash")
  private String codigoPareamentoHash;

  @Column(name = "codigo_expira_em")
  private Instant codigoExpiraEm;

  @Column(name = "pareado_em")
  private Instant pareadoEm;

  @Column(name = "ultimo_contato_em")
  private Instant ultimoContatoEm;

  @Column(name = "nome_do_computador")
  private String nomeDoComputador;

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
