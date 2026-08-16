package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppAppointmentNotificationService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;

/**
 * Espelha {@code modules/scheduling/application/AppointmentNoShowProcessingService.java}: marca
 * como {@code NO_SHOW} todo agendamento que continua {@code PENDING}/{@code CONFIRMED} mais de 6
 * horas depois do horario de termino.
 *
 * <p><b>Isolamento por item.</b> No original cada candidato roda em
 * {@code @Transactional(REQUIRES_NEW)}, para que uma falha isolada nao derrube o lote inteiro. Em
 * Spring, chamar um metodo {@code @Transactional} da propria classe nao passa pelo proxy — a
 * anotacao seria ignorada em silencio e o lote voltaria a ser uma transacao so. Por isso a nova
 * transacao e aberta explicitamente com {@link TransactionTemplate} em
 * {@link #markAppointmentAsNoShowIfNeeded(UUID)}, que preserva a semantica sem depender de
 * auto-injecao.
 */
@Service
public class AppointmentNoShowProcessingService {

  private static final Logger LOG =
      LoggerFactory.getLogger(AppointmentNoShowProcessingService.class);
  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private final AgendamentoRepository agendamentoRepository;
  private final WhatsAppAppointmentNotificationService whatsAppNotificationService;
  private final TransactionTemplate requiresNewTransaction;

  public AppointmentNoShowProcessingService(
      AgendamentoRepository agendamentoRepository,
      WhatsAppAppointmentNotificationService whatsAppNotificationService,
      PlatformTransactionManager transactionManager) {
    this.agendamentoRepository = agendamentoRepository;
    this.whatsAppNotificationService = whatsAppNotificationService;
    this.requiresNewTransaction = new TransactionTemplate(transactionManager);
    this.requiresNewTransaction.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Varredura global (todos os tenants), como no original: a listagem de candidatos roda fora de
   * transacao e cada id e reprocessado isoladamente.
   */
  public int markOpenAppointmentsAsNoShow() {
    LocalDate today = LocalDate.now(ZONE_BR);
    List<UUID> appointmentIds =
        agendamentoRepository.listIdsByStatusUpToDate(
            List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED), today);

    LOG.info(
        "AppointmentNoShow iniciado. candidatos={} dataReferencia={}", appointmentIds.size(), today);
    int changed = 0;
    for (UUID appointmentId : appointmentIds) {
      changed += markAppointmentAsNoShowIfNeeded(appointmentId);
    }
    LOG.info(
        "AppointmentNoShow finalizado. atualizados={} candidatos={}", changed, appointmentIds.size());
    return changed;
  }

  /**
   * Reavalia um unico agendamento em transacao propria. O status e relido dentro da transacao
   * porque entre a listagem e o processamento o agendamento pode ter sido concluido ou cancelado.
   */
  public int markAppointmentAsNoShowIfNeeded(UUID appointmentId) {
    if (appointmentId == null) return 0;
    Integer resultado = requiresNewTransaction.execute(status -> marcarSeNecessario(appointmentId));
    return resultado == null ? 0 : resultado;
  }

  private int marcarSeNecessario(UUID appointmentId) {
    Agendamento agendamento = agendamentoRepository.findById(appointmentId).orElse(null);
    if (agendamento == null) return 0;
    if (agendamento.getStatus() != StatusAgendamento.PENDING
        && agendamento.getStatus() != StatusAgendamento.CONFIRMED) {
      return 0;
    }

    Instant now = Instant.now();
    if (!isPastNoShowWindow(agendamento, now)) return 0;

    agendamento.setStatus(StatusAgendamento.NO_SHOW);
    // O original conta com o dirty checking do Panache dentro da transacao; aqui o save explicito
    // deixa a escrita visivel independentemente do estado da sessao.
    agendamentoRepository.save(agendamento);
    LOG.info("AppointmentNoShow marcou appointment={} como NO_SHOW", agendamento.getId());

    try {
      whatsAppNotificationService.sendNoShow(agendamento);
    } catch (Exception e) {
      LOG.warn(
          "AppointmentNoShow falha ao notificar WhatsApp para appointment={}",
          agendamento.getId(),
          e);
    }

    return 1;
  }

  /** Janela fixa de 6 horas apos o {@code endTime}; horario ilegivel apenas loga e ignora. */
  private boolean isPastNoShowWindow(Agendamento agendamento, Instant now) {
    try {
      LocalTime endTime = LocalTime.parse(agendamento.getEndTime());
      LocalDateTime endDateTime = LocalDateTime.of(agendamento.getDate(), endTime);
      Instant noShowDeadline = endDateTime.plusHours(6).atZone(ZONE_BR).toInstant();
      return now.isAfter(noShowDeadline);
    } catch (Exception e) {
      LOG.warn(
          "AppointmentNoShow ignorou appointment={} por horario invalido", agendamento.getId(), e);
      return false;
    }
  }
}
