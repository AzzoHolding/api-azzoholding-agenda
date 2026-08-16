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

/**
 * Espelha {@code modules/inventory/domain/entity/ImportacaoEstoqueErroLinha.java}. Tabela
 * {@code importacao_estoque_erro_linha}.
 *
 * <p>Os {@code @ManyToOne} para {@code Tenant}/{@code ImportacaoEstoqueJob} do original nao foram
 * mapeados (acesso sempre pelo id escalar), mesma decisao das demais entidades portadas.
 */
@Entity
@Table(name = "importacao_estoque_erro_linha")
@Getter
@Setter
public class ImportacaoEstoqueErroLinha {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "linha", nullable = false)
  private Integer linha;

  @Column(name = "coluna", nullable = false, length = 80)
  private String coluna;

  @Column(name = "codigo_erro", nullable = false, length = 120)
  private String codigoErro;

  @Column(name = "mensagem", nullable = false, length = 400)
  private String mensagem;

  @Column(name = "valor_recebido", length = 255)
  private String valorRecebido;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ImportacaoEstoqueErroLinha other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
