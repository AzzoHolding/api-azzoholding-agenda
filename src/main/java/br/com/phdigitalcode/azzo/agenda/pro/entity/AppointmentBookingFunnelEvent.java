package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.BookingFunnelStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/scheduling/domain/entity/AppointmentBookingFunnelEvent.java}. Tabela
 * {@code appointment_booking_funnel_events}.
 *
 * <p><b>Assimetria preservada do original</b>: esta e a unica entidade de {@code scheduling} sem
 * {@code @PrePersist} — {@code id}, {@code occurredAt} e {@code createdAt} sao preenchidos
 * explicitamente por quem registra o evento ({@code ServicoAgendamentos}), nao pela entidade. Nao
 * foi "corrigido" aqui: adicionar o callback mudaria o valor gravado em {@code occurred_at} nos
 * casos em que o chamador informa um instante passado.
 */
@Entity
@Table(name = "appointment_booking_funnel_events")
@Getter
@Setter
public class AppointmentBookingFunnelEvent {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "stage", nullable = false)
  private BookingFunnelStage stage;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AppointmentBookingFunnelEvent other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
