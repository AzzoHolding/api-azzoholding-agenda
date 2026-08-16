package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentCustomerNote;

/** Espelha {@code modules/scheduling/domain/repository/AppointmentCustomerNoteRepository.java}. */
@Repository
public interface AppointmentCustomerNoteRepository
    extends JpaRepository<AppointmentCustomerNote, UUID> {

  @Query(
      "select n from AppointmentCustomerNote n where n.tenantId = :tenantId "
          + "and n.appointmentId in :appointmentIds order by n.createdAt desc")
  List<AppointmentCustomerNote> findByTenantAndAppointmentIds(
      @Param("tenantId") UUID tenantId, @Param("appointmentIds") Collection<UUID> appointmentIds);

  /**
   * Guarda de entrada do original ({@code listByTenantAndAppointmentIds}): argumento nulo ou lista
   * vazia devolve lista vazia sem tocar no banco. Mantida como {@code default} para nao empurrar a
   * checagem para cada chamador.
   */
  default List<AppointmentCustomerNote> listByTenantAndAppointmentIds(
      UUID tenantId, Collection<UUID> appointmentIds) {
    if (tenantId == null || appointmentIds == null || appointmentIds.isEmpty()) return List.of();
    return findByTenantAndAppointmentIds(tenantId, appointmentIds);
  }

  /** Notas de um agendamento, mais recente primeiro — usada no detalhe e no historico do cliente. */
  List<AppointmentCustomerNote> findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(
      UUID tenantId, UUID appointmentId);

  Optional<AppointmentCustomerNote> findByIdAndTenantIdAndAppointmentId(
      UUID id, UUID tenantId, UUID appointmentId);

  /**
   * Gate de conclusao do atendimento: sem nenhuma nota operacional registrada, o agendamento nao
   * pode ir para {@code COMPLETED}.
   */
  long countByTenantIdAndAppointmentId(UUID tenantId, UUID appointmentId);

  List<AppointmentCustomerNote> findByTenantIdAndClientId(UUID tenantId, UUID clientId);

  /** Guarda de entrada do original ({@code listByTenantAndClient}). */
  default List<AppointmentCustomerNote> listByTenantAndClient(UUID tenantId, UUID clientId) {
    if (tenantId == null || clientId == null) return List.of();
    return findByTenantIdAndClientId(tenantId, clientId);
  }
}
