package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;

/**
 * Parte declarativa de {@code modules/scheduling/domain/repository/AgendamentoRepository.java}.
 *
 * <p>As consultas dinamicas/nativas do original (advisory lock, contagem por dia, historico do
 * cliente com filtros opcionais e o relatorio de no-show com cursor) vivem em
 * {@link AgendamentoQueryRepository} — o mesmo desdobramento ja feito em
 * {@link TransacaoRepository} / {@link TransacaoQueryRepository}, porque a clausula {@code where}
 * e montada em runtime.
 */
@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID> {

  Optional<Agendamento> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Equivalente ao {@code list(...)} de {@code AppointmentService.loadActiveAppointments}.
   *
   * <p>{@code CANCELLED}/{@code NO_SHOW} nunca ocuparam o horario de fato; {@code COMPLETED}
   * significa que o atendimento ja foi encerrado, entao o profissional volta a ficar disponivel
   * naquele slot — a exclusao dos tres e do original, nao uma otimizacao nova.
   */
  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.professionalId = :professionalId "
          + "and a.date = :date and a.status not in :excludedStatuses")
  List<Agendamento> listActiveByProfessionalAndDate(
      @Param("tenantId") UUID tenantId,
      @Param("professionalId") UUID professionalId,
      @Param("date") LocalDate date,
      @Param("excludedStatuses") Collection<StatusAgendamento> excludedStatuses);

  /**
   * Espelha {@code findFirstFutureActiveForClient}. A comparacao de {@code startTime} e textual
   * ("HH:mm") tanto aqui quanto no original — a coluna e {@code varchar}, e o formato zero-padded
   * torna a ordem lexicografica igual a cronologica.
   */
  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.clientId = :clientId "
          + "and a.status in :statuses "
          + "and (a.date > :today or (a.date = :today and a.startTime >= :currentTime)) "
          + "order by a.date asc, a.startTime asc")
  List<Agendamento> findFutureActiveForClient(
      @Param("tenantId") UUID tenantId,
      @Param("clientId") UUID clientId,
      @Param("statuses") Collection<StatusAgendamento> statuses,
      @Param("today") LocalDate today,
      @Param("currentTime") String currentTime,
      Limit limit);

  /**
   * Espelha {@code AssistantInternalResource.listarAgendamentosCliente} do original
   * ({@code agendamentoRepository.list("tenantId = ?1 and clientId = ?2 and status <> ?3 order by
   * date asc, startTime asc")}), usado pelo endpoint interno consumido pelo assistente.
   */
  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.clientId = :clientId "
          + "and a.status <> :excludedStatus order by a.date asc, a.startTime asc")
  List<Agendamento> listByTenantAndClientExcludingStatus(
      @Param("tenantId") UUID tenantId,
      @Param("clientId") UUID clientId,
      @Param("excludedStatus") StatusAgendamento excludedStatus,
      Limit limit);

  /**
   * Espelha o {@code findFirstFutureActiveForClient} do original, incluindo o fallback de
   * {@code currentTime} nulo/em branco para "agora" ({@code HH:mm:ss}, segundos e nanos zerados) e
   * a guarda que devolve vazio sem tocar no banco.
   */
  default Optional<Agendamento> findFirstFutureActiveForClient(
      UUID tenantId, UUID clientId, LocalDate today, String currentTime) {
    if (tenantId == null || clientId == null || today == null) return Optional.empty();
    String normalizedTime =
        (currentTime == null || currentTime.isBlank())
            ? java.time.LocalTime.now().withSecond(0).withNano(0).toString()
            : currentTime;
    return findFutureActiveForClient(
            tenantId,
            clientId,
            List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED),
            today,
            normalizedTime,
            Limit.of(1))
        .stream()
        .findFirst();
  }

  /**
   * Mesma consulta de {@link #listActiveByProfessionalAndDate}, mas ignorando um agendamento
   * especifico — o ramo de {@code listarConflitosHorario} usado na edicao e na realocacao, onde o
   * proprio agendamento sendo alterado nao pode contar como conflito de si mesmo.
   */
  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.professionalId = :professionalId "
          + "and a.date = :date and a.id <> :excludeId and a.status not in :excludedStatuses")
  List<Agendamento> listActiveByProfessionalAndDateExcluding(
      @Param("tenantId") UUID tenantId,
      @Param("professionalId") UUID professionalId,
      @Param("date") LocalDate date,
      @Param("excludeId") UUID excludeId,
      @Param("excludedStatuses") Collection<StatusAgendamento> excludedStatuses);

  // ============================================================
  // Listagem da agenda (ServicoAgendamentos.listar)
  // ============================================================

  @Query("select a from Agendamento a where a.tenantId = :tenantId order by a.date, a.startTime")
  List<Agendamento> listByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.date = :date "
          + "order by a.startTime")
  List<Agendamento> listByTenantAndDate(
      @Param("tenantId") UUID tenantId, @Param("date") LocalDate date, Pageable pageable);

  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.professionalId = :professionalId "
          + "order by a.date, a.startTime")
  List<Agendamento> listByTenantAndProfessional(
      @Param("tenantId") UUID tenantId,
      @Param("professionalId") UUID professionalId,
      Pageable pageable);

  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.professionalId = :professionalId "
          + "and a.date = :date order by a.startTime")
  List<Agendamento> listByTenantAndProfessionalAndDate(
      @Param("tenantId") UUID tenantId,
      @Param("professionalId") UUID professionalId,
      @Param("date") LocalDate date,
      Pageable pageable);

  /**
   * Usado pelo heatmap de ocupacao ({@code ServicoRelatorios.heatmap}) — equivalente ao
   * {@code agendamentoRepository.list("tenantId = ?1 and date >= ?2 and date <= ?3 and status <> ?4", ...)}
   * do original.
   */
  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.date >= :from and a.date <= :to "
          + "and a.status <> :excludedStatus order by a.date, a.startTime")
  List<Agendamento> listByTenantAndDateRangeExcludingStatus(
      @Param("tenantId") UUID tenantId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("excludedStatus") StatusAgendamento excludedStatus);

  /** Variante com filtro de profissional do metodo acima. */
  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.professionalId = :professionalId "
          + "and a.date >= :from and a.date <= :to and a.status <> :excludedStatus order by a.date, a.startTime")
  List<Agendamento> listByTenantAndProfessionalAndDateRangeExcludingStatus(
      @Param("tenantId") UUID tenantId,
      @Param("professionalId") UUID professionalId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("excludedStatus") StatusAgendamento excludedStatus);

  /**
   * Candidatos a confirmacao de presenca: {@code PENDING}/{@code CONFIRMED} ate hoje, do mais
   * recente para o mais antigo. O recorte fino da janela (5 min a 6h apos o inicio) fica no
   * service, em memoria, exatamente como no original.
   */
  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.status in :statuses "
          + "and a.date <= :date order by a.date desc, a.startTime desc")
  List<Agendamento> listPendingAttendanceCandidates(
      @Param("tenantId") UUID tenantId,
      @Param("statuses") Collection<StatusAgendamento> statuses,
      @Param("date") LocalDate date,
      Pageable pageable);

  // ============================================================
  // Fechamentos especiais (modulo settings) — agendamentos impactados
  // ============================================================

  /**
   * Espelha a projecao {@code find("select id from Agendamento where status in ?1 and date <= ?2")
   * .project(UUID.class)} de {@code AppointmentNoShowProcessingService}.
   *
   * <p><b>Sem filtro de tenant, de proposito</b> — e uma varredura global de background, igual ao
   * original. Devolve so os ids porque cada candidato e reprocessado numa transacao propria.
   */
  @Query("select a.id from Agendamento a where a.status in :statuses and a.date <= :date")
  List<UUID> listIdsByStatusUpToDate(
      @Param("statuses") Collection<StatusAgendamento> statuses, @Param("date") LocalDate date);

  /**
   * Espelha a projecao {@code find("select id from Agendamento where date = ?1 and status in ?2")
   * .project(UUID.class)} de {@code ReminderProcessingService.sendReminders} (lembrete D-1).
   *
   * <p><b>Sem filtro de tenant</b>, igual ao original — varredura global de background; cada id e
   * reprocessado numa transacao propria.
   */
  @Query("select a.id from Agendamento a where a.date = :date and a.status in :statuses")
  List<UUID> listIdsByDateAndStatus(
      @Param("date") LocalDate date, @Param("statuses") Collection<StatusAgendamento> statuses);

  /**
   * Espelha a projecao {@code find("select id from Agendamento where date in ?1 and status in ?2")
   * .project(UUID.class)} de {@code ReminderProcessingService.sendHoursBeforeReminders}.
   */
  @Query("select a.id from Agendamento a where a.date in :dates and a.status in :statuses")
  List<UUID> listIdsByDatesInAndStatus(
      @Param("dates") Collection<LocalDate> dates, @Param("statuses") Collection<StatusAgendamento> statuses);

  /**
   * Espelha o primeiro ramo de {@code SpecialClosureService.buscarImpactados} (fechamento de dia
   * inteiro). O original monta o HQL concatenando o filtro opcional de profissional; aqui sao dois
   * metodos, e a escolha entre eles fica no {@code default} {@link #listImpactedByClosure}.
   */
  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.date = :date "
          + "and a.status in :statuses")
  List<Agendamento> listActiveByDate(
      @Param("tenantId") UUID tenantId,
      @Param("date") LocalDate date,
      @Param("statuses") Collection<StatusAgendamento> statuses);

  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.date = :date "
          + "and a.status in :statuses and a.professionalId = :professionalId")
  List<Agendamento> listActiveByDateAndProfessional(
      @Param("tenantId") UUID tenantId,
      @Param("date") LocalDate date,
      @Param("statuses") Collection<StatusAgendamento> statuses,
      @Param("professionalId") UUID professionalId);

  /**
   * Fechamento parcial: sobreposicao {@code appointment.start < closure.end AND appointment.end >
   * closure.start}. A comparacao e textual porque {@code start_time}/{@code end_time} sao
   * {@code varchar} "HH:mm" — mesma premissa de {@link #findFutureActiveForClient}.
   */
  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.date = :date "
          + "and a.status in :statuses and a.startTime < :end and a.endTime > :start")
  List<Agendamento> listActiveByDateOverlapping(
      @Param("tenantId") UUID tenantId,
      @Param("date") LocalDate date,
      @Param("statuses") Collection<StatusAgendamento> statuses,
      @Param("start") String start,
      @Param("end") String end);

  @Query(
      "select a from Agendamento a where a.tenantId = :tenantId and a.date = :date "
          + "and a.status in :statuses and a.startTime < :end and a.endTime > :start "
          + "and a.professionalId = :professionalId")
  List<Agendamento> listActiveByDateOverlappingAndProfessional(
      @Param("tenantId") UUID tenantId,
      @Param("date") LocalDate date,
      @Param("statuses") Collection<StatusAgendamento> statuses,
      @Param("start") String start,
      @Param("end") String end,
      @Param("professionalId") UUID professionalId);

  // ============================================================
  // Booking publico (modulo publicbooking) — disponibilidade e criacao
  // ============================================================

  /** Espelha {@code agendamentoRepository.list("tenantId = ?1 and date = ?2")} de {@code ServicoPublicBooking}. */
  List<Agendamento> findByTenantIdAndDate(UUID tenantId, LocalDate date);

  /**
   * Espelha {@code list("tenantId = ?1 and date = ?2 and professionalId = ?3")} de
   * {@code ServicoPublicBooking.obterDisponibilidade}.
   */
  List<Agendamento> findByTenantIdAndDateAndProfessionalId(UUID tenantId, LocalDate date, UUID professionalId);

  /**
   * Espelha o {@code find("tenantId = ?1 and professionalId = ?2 and date = ?3 and startTime = ?4
   * and status <> ?5").firstResult()} de {@code ServicoPublicBooking.criarAgendamentoPublico}
   * (verificacao de conflito de horario).
   */
  Optional<Agendamento> findFirstByTenantIdAndProfessionalIdAndDateAndStartTimeAndStatusNot(
      UUID tenantId, UUID professionalId, LocalDate date, String startTime, StatusAgendamento status);

  /**
   * Forma unificada de {@code SpecialClosureService.buscarImpactados}: {@code PENDING} e
   * {@code CONFIRMED} sao os unicos status considerados ativos, exatamente como no original, e
   * fechamento parcial sem {@code start}/{@code end} devolve lista vazia sem consultar o banco.
   */
  default List<Agendamento> listImpactedByClosure(
      UUID tenantId,
      LocalDate date,
      boolean allDay,
      String startTime,
      String endTime,
      UUID professionalId) {
    List<StatusAgendamento> statusAtivos =
        List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED);
    if (allDay) {
      return professionalId != null
          ? listActiveByDateAndProfessional(tenantId, date, statusAtivos, professionalId)
          : listActiveByDate(tenantId, date, statusAtivos);
    }
    if (startTime == null || endTime == null) return List.of();
    return professionalId != null
        ? listActiveByDateOverlappingAndProfessional(
            tenantId, date, statusAtivos, startTime, endTime, professionalId)
        : listActiveByDateOverlapping(tenantId, date, statusAtivos, startTime, endTime);
  }
}
