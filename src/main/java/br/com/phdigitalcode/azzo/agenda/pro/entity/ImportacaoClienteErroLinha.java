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
 * Espelha {@code modules/customers/domain/entity/ImportacaoClienteErroLinha.java}. Tabela
 * {@code importacao_cliente_erro_linha}.
 *
 * <p>Os {@code @ManyToOne} para {@code Tenant}/{@code ImportacaoClienteJob} do original nao foram
 * mapeados (acesso sempre pelo id escalar), mesma decisao das demais entidades portadas.
 */
@Entity
@Table(name = "importacao_cliente_erro_linha")
@Getter
@Setter
public class ImportacaoClienteErroLinha {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "linha", nullable = false)
  private Integer linha;

  @Column(name = "coluna", length = 100)
  private String coluna;

  @Column(name = "codigo", length = 100)
  private String codigo;

  @Column(name = "mensagem", nullable = false, length = 4000)
  private String mensagem;

  @Column(name = "valor_original", length = 2000)
  private String valorOriginal;

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
    if (!(o instanceof ImportacaoClienteErroLinha other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
