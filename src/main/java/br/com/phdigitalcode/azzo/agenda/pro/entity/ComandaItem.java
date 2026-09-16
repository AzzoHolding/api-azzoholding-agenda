package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/pos/domain/entity/ComandaItem.java}. Tabela {@code comanda_itens}. */
@Entity
@Table(name = "comanda_itens")
@Getter
@Setter
public class ComandaItem {

  public static final String TIPO_SERVICO = "SERVICO";
  public static final String TIPO_PRODUTO = "PRODUTO";
  public static final String TIPO_PACOTE = "PACOTE";

  /** De onde o item veio: o agendamento trouxe, ou alguem lancou na mao no PDV. */
  public static final String ORIGEM_AGENDAMENTO = "AGENDAMENTO";

  public static final String ORIGEM_MANUAL = "MANUAL";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "comanda_id", nullable = false)
  private UUID comandaId;

  @Column(name = "tipo", nullable = false)
  private String tipo;

  @Column(name = "referencia_id", nullable = false)
  private UUID referenciaId;

  @Column(name = "descricao", nullable = false)
  private String descricao;

  @Column(name = "professional_id")
  private UUID professionalId;

  @Column(name = "quantidade", nullable = false)
  private BigDecimal quantidade = BigDecimal.ONE;

  @Column(name = "preco_unitario", nullable = false)
  private BigDecimal precoUnitario;

  @Column(name = "total", nullable = false)
  private BigDecimal total;

  /**
   * Origem do item (V132). Sem isto, o servico que o agendamento trouxe e o lancado a mao ficavam
   * indistinguiveis — e lancar de novo o mesmo servico cobrava o cliente duas vezes sem que
   * ninguem visse de onde tinha vindo cada linha.
   */
  @Column(name = "origem", nullable = false)
  private String origem = ORIGEM_MANUAL;

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
    if (!(o instanceof ComandaItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
