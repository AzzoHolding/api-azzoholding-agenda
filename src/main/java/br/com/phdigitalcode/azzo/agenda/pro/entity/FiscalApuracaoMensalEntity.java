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
 * Espelha {@code modules/fiscal/domain/entity/FiscalApuracaoMensalEntity.java}. Tabela
 * {@code fiscal_apuracao_monthly} — fechamento mensal consolidado.
 *
 * <p><b>Nome da tabela nao acompanha o nome da classe</b> ({@code monthly}, em ingles, com a classe
 * em portugues). Preservado como no original.
 *
 * <p>{@code ano}/{@code mes} sao {@code int} e os totais {@code long} em centavos, tambem como no
 * original. {@code documentosJson} guarda o detalhamento serializado.
 */
@Entity
@Table(name = "fiscal_apuracao_monthly")
@Getter
@Setter
public class FiscalApuracaoMensalEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "ano", nullable = false)
  private int ano;

  @Column(name = "mes", nullable = false)
  private int mes;

  @Column(name = "total_servicos", nullable = false)
  private long totalServicos;

  @Column(name = "total_impostos", nullable = false)
  private long totalImpostos;

  @Column(name = "total_documentos", nullable = false)
  private long totalDocumentos;

  @Column(name = "documentos_json")
  private String documentosJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
