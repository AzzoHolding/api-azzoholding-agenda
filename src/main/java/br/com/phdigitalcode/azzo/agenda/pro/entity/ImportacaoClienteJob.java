package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ModoImportacaoCliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoCliente;
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
 * Espelha {@code modules/customers/domain/entity/ImportacaoClienteJob.java}. Tabela
 * {@code importacao_cliente_job}.
 *
 * <p>O {@code @ManyToOne Tenant} do original nao foi mapeado (acesso sempre pelo id escalar),
 * mesma decisao das demais entidades portadas.
 */
@Entity
@Table(name = "importacao_cliente_job")
@Getter
@Setter
public class ImportacaoClienteJob {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "nome_arquivo", nullable = false, length = 255)
  private String nomeArquivo;

  @Column(name = "arquivo_storage_key", nullable = false, length = 500)
  private String arquivoStorageKey;

  @Column(name = "arquivo_hash_sha256", nullable = false, length = 128)
  private String arquivoHashSha256;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private StatusImportacaoCliente status;

  @Enumerated(EnumType.STRING)
  @Column(name = "modo_importacao", nullable = false, length = 30)
  private ModoImportacaoCliente modoImportacao;

  @Column(name = "dry_run", nullable = false)
  private Boolean dryRun;

  @Column(name = "linhas_recebidas", nullable = false)
  private Integer linhasRecebidas;

  @Column(name = "linhas_processadas", nullable = false)
  private Integer linhasProcessadas;

  @Column(name = "linhas_sucesso", nullable = false)
  private Integer linhasSucesso;

  @Column(name = "linhas_erro", nullable = false)
  private Integer linhasErro;

  @Column(name = "mensagem_resumo", length = 1000)
  private String mensagemResumo;

  @Column(name = "error_message", length = 4000)
  private String errorMessage;

  @Column(name = "requested_by")
  private UUID requestedBy;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (status == null) status = StatusImportacaoCliente.RECEBIDO;
    if (modoImportacao == null) modoImportacao = ModoImportacaoCliente.INSERT_ONLY;
    if (dryRun == null) dryRun = Boolean.FALSE;
    if (linhasRecebidas == null) linhasRecebidas = 0;
    if (linhasProcessadas == null) linhasProcessadas = 0;
    if (linhasSucesso == null) linhasSucesso = 0;
    if (linhasErro == null) linhasErro = 0;
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
    if (!(o instanceof ImportacaoClienteJob other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
