package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.converter.StatusNotificationConverter;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/notifications/domain/entity/Notification.java}. Tabela
 * {@code notifications}.
 *
 * <p>O original mapeia {@code tenant}/{@code appointment} como {@code @ManyToOne} apenas para
 * navegacao (colunas {@code insertable = false, updatable = false}), nunca usados fora da propria
 * classe. Seguindo o padrao ja adotado nas demais entidades do porte (ex.: {@link Cliente},
 * {@link Agendamento}), essas associacoes viram colunas {@code UUID} simples.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Column(name = "professional_id")
  private UUID professionalId;

  @Column(name = "channel", nullable = false)
  private String channel;

  @Column(name = "destination", nullable = false)
  private String destination;

  @Column(name = "message")
  private String message;

  @Convert(converter = StatusNotificationConverter.class)
  @Column(name = "status", nullable = false)
  private StatusNotification status;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "viewed_at")
  private Instant viewedAt;

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
    if (!(o instanceof Notification other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
