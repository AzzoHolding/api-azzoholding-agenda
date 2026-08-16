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
 * Espelha {@code modules/tenant/domain/entity/WhatsAppMessageLogEntity.java}. Tabela
 * {@code whatsapp_message_log} (migration {@code V73__create_whatsapp_message_log.sql}, ja copiada
 * em etapa anterior).
 */
@Entity
@Table(name = "whatsapp_message_log")
@Getter
@Setter
public class WhatsAppMessageLogEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Column(name = "event_type", nullable = false, length = 60)
  private String eventType;

  @Column(name = "destination_phone", nullable = false, length = 30)
  private String destinationPhone;

  @Column(name = "message_text")
  private String messageText;

  @Column(name = "provider_message_id", length = 120)
  private String providerMessageId;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "SENT";

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (sentAt == null) sentAt = Instant.now();
    if (status == null || status.isBlank()) status = "SENT";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WhatsAppMessageLogEntity other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
