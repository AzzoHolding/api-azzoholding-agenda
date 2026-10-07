package br.com.phdigitalcode.azzo.agenda.pro.repository;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;

/**
 * Espelha {@code modules/scheduling/domain/repository/AppointmentDepositRepository.java}.
 *
 * <p>O original devolve {@code null} (e nao lanca) para argumento nulo/em branco; aqui isso vira
 * {@code Optional.empty()} atraves dos {@code default} que fazem o guard antes de consultar.
 */
@Repository
public interface AppointmentDepositRepository extends JpaRepository<AppointmentDeposit, UUID> {

  Optional<AppointmentDeposit> findByAsaasPaymentId(String asaasPaymentId);

  default Optional<AppointmentDeposit> findByAsaasPaymentIdSeguro(String asaasPaymentId) {
    if (asaasPaymentId == null || asaasPaymentId.isBlank()) return Optional.empty();
    return findByAsaasPaymentId(asaasPaymentId.trim());
  }

  List<AppointmentDeposit> findByAppointmentId(UUID appointmentId);

  default Optional<AppointmentDeposit> findFirstByAppointmentId(UUID appointmentId) {
    if (appointmentId == null) return Optional.empty();
    return findByAppointmentId(appointmentId).stream().findFirst();
  }

  @Query("""
      select d from AppointmentDeposit d
      where d.status = :statusPending
        and d.expiresAt is not null
        and d.expiresAt < :now
      """)
  List<AppointmentDeposit> findExpiredPending(
      @Param("statusPending") String statusPending, @Param("now") Instant now);

  @Query("""
      select d from AppointmentDeposit d
      where d.appointmentId = :appointmentId
        and d.status = :statusPaid
        and d.usedInComandaId is null
      """)
  List<AppointmentDeposit> findPaidUnusedByAppointment(
      @Param("appointmentId") UUID appointmentId, @Param("statusPaid") String statusPaid);

  /**
   * O mesmo sinal livre, mas TRAVADO ({@code SELECT ... FOR UPDATE}) para quem vai CONSUMI-LO
   * (achado BNC-002, auditoria de 2026-10-06).
   *
   * <p>Sem a trava, duas comandas do mesmo agendamento pagando ao mesmo tempo com credito de sinal
   * liam o mesmo sinal livre e abatiam os dois: o salao recebia menos do que a soma dos
   * fechamentos. Com ela, a segunda espera a primeira terminar e, ao reavaliar o
   * {@code usedInComandaId is null} (READ COMMITTED), nao encontra mais o sinal. O indice unico
   * {@code uq_comanda_pagamentos_sinal} (V144) e a rede de seguranca por baixo.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select d from AppointmentDeposit d
      where d.appointmentId = :appointmentId
        and d.status = :statusPaid
        and d.usedInComandaId is null
      """)
  List<AppointmentDeposit> findPaidUnusedByAppointmentParaConsumo(
      @Param("appointmentId") UUID appointmentId, @Param("statusPaid") String statusPaid);

  /** Como {@link #findPaidUnusedByAppointmentId}, travando o sinal para consumi-lo. */
  default Optional<AppointmentDeposit> findPaidUnusedByAppointmentIdParaConsumo(UUID appointmentId) {
    if (appointmentId == null) return Optional.empty();
    return findPaidUnusedByAppointmentParaConsumo(appointmentId, AppointmentDeposit.STATUS_PAID)
        .stream()
        .findFirst();
  }

  /** Sinal ja pago e ainda nao consumido por nenhuma comanda. */
  default Optional<AppointmentDeposit> findPaidUnusedByAppointmentId(UUID appointmentId) {
    if (appointmentId == null) return Optional.empty();
    return findPaidUnusedByAppointment(appointmentId, AppointmentDeposit.STATUS_PAID).stream()
        .findFirst();
  }
}
