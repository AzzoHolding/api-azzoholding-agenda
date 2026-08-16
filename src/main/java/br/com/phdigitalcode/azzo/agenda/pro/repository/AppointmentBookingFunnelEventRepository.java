package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentBookingFunnelEvent;

/**
 * Espelha {@code modules/scheduling/domain/repository/AppointmentBookingFunnelEventRepository.java}
 * — no original tambem nao ha nenhum metodo proprio, so o CRUD herdado.
 */
@Repository
public interface AppointmentBookingFunnelEventRepository
    extends JpaRepository<AppointmentBookingFunnelEvent, UUID> {}
