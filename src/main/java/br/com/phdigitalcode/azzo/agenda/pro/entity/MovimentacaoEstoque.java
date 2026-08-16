package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.OrigemMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/inventory/domain/entity/MovimentacaoEstoque.java}. Tabela
 * {@code movimentacoes_estoque}.
 *
 * <p>Os {@code @ManyToOne} para {@code Tenant}/{@code ItemEstoque} do original nao foram mapeados
 * (acesso sempre pelo id escalar), mesma decisao das demais entidades portadas.
 */
@Entity
@Table(name = "movimentacoes_estoque")
@Getter
@Setter
public class MovimentacaoEstoque {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "item_estoque_id", nullable = false)
  private UUID itemEstoqueId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo", nullable = false, length = 20)
  private TipoMovimentacaoEstoque tipo;

  @Column(name = "quantidade", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantidade;

  @Column(name = "saldo_anterior", nullable = false, precision = 19, scale = 4)
  private BigDecimal saldoAnterior;

  @Column(name = "saldo_posterior", nullable = false, precision = 19, scale = 4)
  private BigDecimal saldoPosterior;

  @Column(name = "motivo", nullable = false, length = 255)
  private String motivo;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem", nullable = false, length = 20)
  private OrigemMovimentacaoEstoque origem;

  @Column(name = "valor_unitario_pago", precision = 19, scale = 4)
  private BigDecimal valorUnitarioPago;

  @Column(name = "valor_total_movimentacao", precision = 19, scale = 4)
  private BigDecimal valorTotalMovimentacao;

  @Column(name = "gerar_lancamento_financeiro", nullable = false)
  private Boolean gerarLancamentoFinanceiro;

  @Column(name = "transacao_financeira_id")
  private UUID transacaoFinanceiraId;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Column(name = "comanda_item_id")
  private UUID comandaItemId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (origem == null) origem = OrigemMovimentacaoEstoque.MANUAL;
    if (gerarLancamentoFinanceiro == null) gerarLancamentoFinanceiro = Boolean.FALSE;
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MovimentacaoEstoque other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
