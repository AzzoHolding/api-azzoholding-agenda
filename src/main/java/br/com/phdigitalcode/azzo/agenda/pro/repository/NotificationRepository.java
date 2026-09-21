package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Notification;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;

/** Espelha {@code modules/notifications/domain/repository/NotificationRepository.java}. */
@Repository
public interface NotificationRepository
    extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

  /**
   * Apaga o contato e o texto das notificacoes enviadas ao titular anonimizado.
   *
   * Nao ha {@code client_id} aqui: chega-se pelo agendamento. O destino e o telefone ou o e-mail
   * dele, e o texto traz o nome — anonimizar sem passar por aqui deixava o contato guardado.
   * {@code destination} nao aceita nulo, entao recebe o marcador.
   */
  @Modifying
  @Transactional
  @Query(
      value =
          "UPDATE notifications SET destination = '[ANONIMIZADO]', message = NULL, "
              + "error_message = NULL "
              + "WHERE tenant_id = :tenantId AND appointment_id IN "
              + "(SELECT a.id FROM appointments a WHERE a.tenant_id = :tenantId "
              + "AND a.client_id = :clientId)",
      nativeQuery = true)
  int anonimizarPorClienteRaw(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

  Optional<Notification> findByIdAndTenantId(UUID id, UUID tenantId);

  long deleteByIdAndTenantId(UUID id, UUID tenantId);

  long deleteByTenantId(UUID tenantId);

  Optional<Notification> findFirstByTenantIdAndAppointmentIdAndChannelAndDestination(
      UUID tenantId, UUID appointmentId, String channel, String destination);

  @Query(
      "select (count(n) > 0) from Notification n where n.tenantId = :tenantId "
          + "and n.appointmentId = :appointmentId and n.channel in :channels "
          + "and n.status = :status")
  boolean existsSentByAppointmentAndChannelsRaw(
      @Param("tenantId") UUID tenantId,
      @Param("appointmentId") UUID appointmentId,
      @Param("channels") List<String> channels,
      @Param("status") StatusNotification status);

  default boolean existsSentByAppointmentAndChannels(
      UUID tenantId, UUID appointmentId, List<String> channels) {
    return existsSentByAppointmentAndChannelsRaw(
        tenantId, appointmentId, channels, StatusNotification.SENT);
  }

  @Query(
      "select n from Notification n where n.tenantId = :tenantId and n.channel = :channel "
          + "and n.destination = :destination and n.status = :status "
          + "and n.errorMessage = :errorMessage and n.createdAt >= :threshold")
  List<Notification> findRecentByErrorMessage(
      @Param("tenantId") UUID tenantId,
      @Param("channel") String channel,
      @Param("destination") String destination,
      @Param("status") StatusNotification status,
      @Param("errorMessage") String errorMessage,
      @Param("threshold") Instant threshold);

  @Query(
      "select n from Notification n where n.tenantId = :tenantId and n.channel = :channel "
          + "and n.destination = :destination and n.status = :status "
          + "and n.message = :message and n.createdAt >= :threshold")
  List<Notification> findRecentByMessage(
      @Param("tenantId") UUID tenantId,
      @Param("channel") String channel,
      @Param("destination") String destination,
      @Param("status") StatusNotification status,
      @Param("message") String message,
      @Param("threshold") Instant threshold);

  @Modifying
  @Transactional
  @Query(
      "update Notification n set n.viewedAt = :viewedAt "
          + "where n.tenantId = :tenantId and n.viewedAt is null")
  long markAllViewedByTenant(@Param("tenantId") UUID tenantId, @Param("viewedAt") Instant viewedAt);
}
