package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoImportacaoEstoque;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/inventory/domain/entity/ImportacaoEstoqueJob.java}. Tabela
 * {@code importacao_estoque_job}.
 *
 * <p>O {@code @ManyToOne Tenant} do original nao foi mapeado (acesso sempre pelo id escalar), mesma
 * decisao das demais entidades portadas.
 */
@Entity
@Table(name = "importacao_estoque_job")
@Getter
@Setter
public class ImportacaoEstoqueJob {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_importacao", nullable = false, length = 20)
  private TipoImportacaoEstoque tipoImportacao;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private StatusImportacaoEstoque status;

  @Column(name = "dry_run", nullable = false)
  private Boolean dryRun;

  @Column(name = "total_linhas", nullable = false)
  private Integer totalLinhas;

  @Column(name = "linhas_processadas", nullable = false)
  private Integer linhasProcessadas;

  @Column(name = "linhas_com_erro", nullable = false)
  private Integer linhasComErro;

  @Column(name = "arquivo_sha256", length = 128)
  private String arquivoSha256;

  @Column(name = "arquivo_storage_key", length = 500)
  private String arquivoStorageKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (status == null) status = StatusImportacaoEstoque.RECEBIDO;
    if (dryRun == null) dryRun = Boolean.FALSE;
    if (totalLinhas == null) totalLinhas = 0;
    if (linhasProcessadas == null) linhasProcessadas = 0;
    if (linhasComErro == null) linhasComErro = 0;
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
    if (!(o instanceof ImportacaoEstoqueJob other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
