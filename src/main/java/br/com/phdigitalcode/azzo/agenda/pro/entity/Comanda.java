package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
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

/** Espelha {@code modules/pos/domain/entity/Comanda.java}. Tabela {@code comandas}. */
@Entity
@Table(name = "comandas")
@Getter
@Setter
public class Comanda {

  public static final String STATUS_ABERTA = "ABERTA";
  public static final String STATUS_FECHADA = "FECHADA";
  public static final String STATUS_CANCELADA = "CANCELADA";
  public static final String STATUS_ESTORNADA = "ESTORNADA";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Column(name = "client_id")
  private UUID clientId;

  @Column(name = "status", nullable = false)
  private String status = STATUS_ABERTA;

  @Column(name = "subtotal", nullable = false)
  private BigDecimal subtotal = BigDecimal.ZERO;

  @Column(name = "desconto", nullable = false)
  private BigDecimal desconto = BigDecimal.ZERO;

  @Column(name = "desconto_motivo")
  private String descontoMotivo;

  @Column(name = "gorjeta", nullable = false)
  private BigDecimal gorjeta = BigDecimal.ZERO;

  @Column(name = "gorjeta_professional_id")
  private UUID gorjetaProfessionalId;

  /**
   * subtotal menos desconto. A gorjeta NAO compoe o total (e repassada ao profissional, nao e
   * receita do salao).
   */
  @Column(name = "total", nullable = false)
  private BigDecimal total = BigDecimal.ZERO;

  @Column(name = "aberta_por")
  private UUID abertaPor;

  @Column(name = "fechada_por")
  private UUID fechadaPor;

  @Column(name = "cancel_motivo")
  private String cancelMotivo;

  @Column(name = "estornado_por")
  private UUID estornadoPor;

  @Column(name = "estornado_em")
  private Instant estornadoEm;

  @Column(name = "estorno_motivo")
  private String estornoMotivo;

  /**
   * Pontos de fidelidade creditados no fechamento original — guardado para reverter o valor exato
   * no estorno, sem recalcular com regras que podem ter mudado desde a venda.
   */
  @Column(name = "pontos_fidelidade_creditados", nullable = false)
  private int pontosFidelidadeCreditados = 0;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (id == null) id = UUID.randomUUID();
    if (openedAt == null) openedAt = now;
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Comanda other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
