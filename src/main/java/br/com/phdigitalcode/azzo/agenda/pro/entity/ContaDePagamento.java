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

/** A conta do salao numa operadora de pagamento (V167). As credenciais sao cifradas e nunca saem na API. */
@Entity
@Table(name = "contas_de_pagamento")
@Getter
@Setter
public class ContaDePagamento {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "provedor", nullable = false)
  private String provedor;

  @Column(name = "apelido", nullable = false)
  private String apelido;

  /** SANDBOX ou PROD. */
  @Column(name = "ambiente", nullable = false)
  private String ambiente;

  /** JSON das credenciais, cifrado pelo {@code EncryptionService}. */
  @Column(name = "credenciais_enc", nullable = false, columnDefinition = "TEXT")
  private String credenciaisEnc;

  @Column(name = "credenciais_mascara")
  private String credenciaisMascara;

  @Column(name = "webhook_token", nullable = false)
  private String webhookToken;

  /** PIX, CARTAO_PRESENCIAL, ONLINE — separados por virgula. */
  @Column(name = "padrao_para", nullable = false)
  private String padraoPara = "";

  @Column(name = "ativo", nullable = false)
  private boolean ativo = true;

  @Column(name = "validada_em")
  private Instant validadaEm;

  @Column(name = "ultimo_erro")
  private String ultimoErro;

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
