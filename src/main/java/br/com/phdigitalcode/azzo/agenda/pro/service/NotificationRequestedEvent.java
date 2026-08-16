package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;

/** Espelha {@code modules/notifications/application/NotificationRequestedEvent.java}. */
public record NotificationRequestedEvent(
    UUID tenantId,
    UUID appointmentId,
    UUID professionalId,
    String channel,
    String destination,
    String message,
    StatusNotification status,
    String errorMessage,
    Instant sentAt,
    Long deduplicationWindowSeconds) {
}
