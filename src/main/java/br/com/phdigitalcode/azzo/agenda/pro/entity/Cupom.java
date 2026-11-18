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

/** Cupom de desconto do salao (V180). Codigo unico por salao, sem diferenca de maiusculas. */
@Entity
@Table(name = "cupons")
@Getter
@Setter
public class Cupom {

  public static final String TIPO_PERCENTUAL = "PERCENTUAL";
  public static final String TIPO_VALOR = "VALOR";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "codigo", nullable = false)
  private String codigo;

  @Column(name = "descricao")
  private String descricao;

  @Column(name = "tipo", nullable = false)
  private String tipo;

  @Column(name = "valor", nullable = false)
  private BigDecimal valor;

  @Column(name = "inicio")
  private LocalDate inicio;

  @Column(name = "fim")
  private LocalDate fim;

  @Column(name = "limite_total")
  private Integer limiteTotal;

  @Column(name = "limite_por_cliente")
  private Integer limitePorCliente;

  @Column(name = "valor_minimo")
  private BigDecimal valorMinimo;

  /** So muda por UPDATE condicional (CupomRepository.reservarUso / liberarUso). */
  @Column(name = "usos", nullable = false, insertable = false, updatable = false)
  private int usos;

  @Column(name = "ativo", nullable = false)
  private boolean ativo = true;

  /** Lote do gerador (V180): codigo de uso unico gerado em quantidade. Nulo no cupom avulso. */
  @Column(name = "lote_id")
  private UUID loteId;

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
