package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;

/** Espelha {@code modules/scheduling/domain/repository/AgendamentoItemRepository.java}. */
@Repository
public interface AgendamentoItemRepository extends JpaRepository<AgendamentoItem, UUID> {

  List<AgendamentoItem> findByTenantIdAndAppointmentId(UUID tenantId, UUID appointmentId);

  /**
   * Guarda de idempotencia de {@code ServicoAgendamentos.persistItems}: se o agendamento ja tem
   * item gravado, nao grava de novo.
   */
  boolean existsByAppointmentId(UUID appointmentId);
}
