package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentConflictSummaryResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.ManualTimeSlotResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.TimeSlotResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;

/**
 * Espelha {@code modules/scheduling/application/AppointmentService.java} — o motor de sugestao de
 * horarios (slots livres e slots manuais com conflito).
 *
 * <p><b>Divergencia de mapeamento</b>: o original le {@code profissional.workingHours} (associacao
 * {@code @OneToMany} na entidade). A {@link Profissional} migrada nao tem essa colecao — as
 * jornadas sao carregadas por {@link ProfissionalWorkingHourRepository}, como ja fazem os demais
 * modulos migrados. O conjunto de linhas e a ordem resultante sao os mesmos; o algoritmo nao
 * depende da ordem (as faixas passam por {@code mergeRanges}, que ordena).
 */
@Service
public class AppointmentService {

  private static final int DEFAULT_SLOT_STEP_MINUTES = 5;
  private static final int MIN_UNDESIRED_GAP_MINUTES = 15;
  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private final AgendamentoRepository agendamentoRepository;
  private final ProfissionalRepository profissionalRepository;
  private final ProfissionalWorkingHourRepository profissionalWorkingHourRepository;
  private final ClienteRepository clienteRepository;
  private final ServicoRepository servicoRepository;
  private final TenantOperationalSettingsService tenantOperationalSettingsService;
  private final SpecialClosureService specialClosureService;

  public AppointmentService(
      AgendamentoRepository agendamentoRepository,
      ProfissionalRepository profissionalRepository,
      ProfissionalWorkingHourRepository profissionalWorkingHourRepository,
      ClienteRepository clienteRepository,
      ServicoRepository servicoRepository,
      TenantOperationalSettingsService tenantOperationalSettingsService,
      SpecialClosureService specialClosureService) {
    this.agendamentoRepository = agendamentoRepository;
    this.profissionalRepository = profissionalRepository;
    this.profissionalWorkingHourRepository = profissionalWorkingHourRepository;
    this.clienteRepository = clienteRepository;
    this.servicoRepository = servicoRepository;
    this.tenantOperationalSettingsService = tenantOperationalSettingsService;
    this.specialClosureService = specialClosureService;
  }

  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public List<TimeSlotResponse> findAvailableSlots(
      UUID tenantId,
      UUID professionalId,
      LocalDate date,
      int serviceDurationMinutes,
      int bufferMinutes) {
    SchedulingContext context =
        buildSchedulingContext(tenantId, professionalId, date, serviceDurationMinutes, bufferMinutes);

    List<TimeSlotResponse> slots = new ArrayList<>();
    for (TimeRange freeRange : context.freeRanges()) {
      slots.addAll(
          montarSlotsDaLacuna(
              freeRange,
              context.serviceDurationMinutes(),
              context.bufferMinutes(),
              context.busyRanges()));
    }

    // Para o dia atual, remove slots cujo horario de inicio ja passou
    if (date.isEqual(LocalDate.now(ZONE_BR))) {
      LocalTime agora = LocalTime.now(ZONE_BR).truncatedTo(ChronoUnit.MINUTES);
      slots.removeIf(slot -> !slot.startTime.isAfter(agora));
    }

    slots.sort(
        Comparator.comparingInt((TimeSlotResponse item) -> item.optimizationScore)
            .reversed()
            .thenComparing(item -> item.startTime));
    return slots;
  }

  /**
   * <b>Nota sobre auto-invocacao</b>: este metodo chama {@link #findAvailableSlots} diretamente, e
   * o proxy do Spring nao intercepta chamadas internas — a anotacao do metodo chamado nao vale ali.
   * Sem efeito pratico aqui: ambos sao {@code SUPPORTS} + {@code readOnly}, entao a chamada interna
   * simplesmente segue no mesmo contexto transacional (ou na ausencia dele) da chamada externa,
   * exatamente como no Quarkus.
   */
  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public List<ManualTimeSlotResponse> findManualSlots(
      UUID tenantId,
      UUID professionalId,
      LocalDate date,
      int serviceDurationMinutes,
      int bufferMinutes,
      boolean includeConflictSlots) {
    SchedulingContext context =
        buildSchedulingContext(tenantId, professionalId, date, serviceDurationMinutes, bufferMinutes);
    Map<String, ManualTimeSlotResponse> slots = new LinkedHashMap<>();

    for (TimeSlotResponse availableSlot :
        findAvailableSlots(tenantId, professionalId, date, serviceDurationMinutes, bufferMinutes)) {
      ManualTimeSlotResponse manualSlot = new ManualTimeSlotResponse();
      manualSlot.startTime = availableSlot.startTime;
      manualSlot.endTime = availableSlot.endTime;
      manualSlot.optimizationScore = availableSlot.optimizationScore;
      manualSlot.conflicting = Boolean.FALSE;
      manualSlot.slotType = "AVAILABLE";
      slots.put(slotKey(availableSlot.startTime, availableSlot.endTime), manualSlot);
    }

    if (includeConflictSlots) {
      for (TimeRange workingRange : context.workingRanges()) {
        if (minutesBetween(workingRange.start, workingRange.end) < context.serviceDurationMinutes()) {
          continue;
        }
        LocalTime latestStart = workingRange.end.minusMinutes(context.serviceDurationMinutes());
        for (LocalTime start = workingRange.start;
            !start.isAfter(latestStart);
            start = start.plusMinutes(DEFAULT_SLOT_STEP_MINUTES)) {
          LocalTime end = start.plusMinutes(context.serviceDurationMinutes());
          String key = slotKey(start, end);
          if (slots.containsKey(key)) continue;

          List<Agendamento> conflicts =
              detectConflictingAppointments(start, end, context.appointments(), context.bufferMinutes());
          if (conflicts.isEmpty()) continue;

          ManualTimeSlotResponse manualSlot = new ManualTimeSlotResponse();
          manualSlot.startTime = start;
          manualSlot.endTime = end;
          manualSlot.optimizationScore = 0;
          manualSlot.conflicting = Boolean.TRUE;
          manualSlot.slotType = "CONFLICT";
          manualSlot.badge = "Conflito";
          manualSlot.conflicts =
              conflicts.stream()
                  .map(appointment -> toConflictSummary(context.tenantId(), appointment))
                  .toList();
          slots.put(key, manualSlot);
        }
      }
    }

    List<ManualTimeSlotResponse> response = new ArrayList<>(slots.values());
    response.sort(
        Comparator.comparing((ManualTimeSlotResponse item) -> Boolean.TRUE.equals(item.conflicting))
            .thenComparing(item -> item.startTime));
    return response;
  }

  private SchedulingContext buildSchedulingContext(
      UUID tenantId,
      UUID professionalId,
      LocalDate date,
      int serviceDurationMinutes,
      int bufferMinutes) {
    validarEntrada(tenantId, professionalId, date, serviceDurationMinutes, bufferMinutes);
    if (specialClosureService.isClosedAt(tenantId, professionalId, date, null, null)) {
      return emptyContext(tenantId, serviceDurationMinutes, bufferMinutes);
    }

    TimeRange salonBusinessRange = obterJanelaFuncionamentoSalao(tenantId, date);
    if (salonBusinessRange == null) {
      return emptyContext(tenantId, serviceDurationMinutes, bufferMinutes);
    }

    Profissional profissional = validarProfissionalDoTenant(tenantId, professionalId);
    List<TimeRange> workingRanges = obterJanelasDeTrabalho(tenantId, profissional, date);
    if (workingRanges.isEmpty()) {
      throw new IllegalStateException("Profissional sem horario de trabalho para a data informada");
    }
    workingRanges = intersectRanges(workingRanges, List.of(salonBusinessRange));
    if (workingRanges.isEmpty()) {
      return emptyContext(tenantId, serviceDurationMinutes, bufferMinutes);
    }

    List<Agendamento> appointments = loadActiveAppointments(tenantId, professionalId, date);
    List<TimeRange> busyRanges = mergeRanges(converterParaFaixasOcupadas(appointments));
    List<TimeRange> blockedRanges = mergeRanges(expandirComBuffer(busyRanges, bufferMinutes));
    List<TimeRange> freeRanges = calculateFreeRanges(workingRanges, blockedRanges);

    return new SchedulingContext(
        tenantId,
        workingRanges,
        appointments,
        busyRanges,
        freeRanges,
        serviceDurationMinutes,
        bufferMinutes);
  }

  private SchedulingContext emptyContext(
      UUID tenantId, int serviceDurationMinutes, int bufferMinutes) {
    return new SchedulingContext(
        tenantId, List.of(), List.of(), List.of(), List.of(), serviceDurationMinutes, bufferMinutes);
  }

  private List<Agendamento> loadActiveAppointments(
      UUID tenantId, UUID professionalId, LocalDate date) {
    // CANCELLED/NO_SHOW nunca ocuparam o horario de fato; COMPLETED significa que o
    // atendimento ja foi encerrado, entao o profissional volta a ficar disponivel
    // naquele slot para um novo agendamento.
    return agendamentoRepository.listActiveByProfessionalAndDate(
        tenantId,
        professionalId,
        date,
        List.of(StatusAgendamento.CANCELLED, StatusAgendamento.NO_SHOW, StatusAgendamento.COMPLETED));
  }

  private void validarEntrada(
      UUID tenantId,
      UUID professionalId,
      LocalDate date,
      int serviceDurationMinutes,
      int bufferMinutes) {
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatorio");
    if (professionalId == null) throw new IllegalArgumentException("professionalId obrigatorio");
    if (date == null) throw new IllegalArgumentException("date obrigatoria");
    if (serviceDurationMinutes <= 0) {
      throw new IllegalArgumentException("serviceDurationMinutes invalido");
    }
    if (bufferMinutes < 0) throw new IllegalArgumentException("bufferMinutes invalido");
  }

  private Profissional validarProfissionalDoTenant(UUID tenantId, UUID professionalId) {
    return profissionalRepository
        .findByIdAndTenantId(professionalId, tenantId)
        .orElseThrow(
            () ->
                new IllegalArgumentException("Profissional nao encontrado para o tenant informado"));
  }

  private List<TimeRange> obterJanelasDeTrabalho(
      UUID tenantId, Profissional profissional, LocalDate date) {
    List<ProfissionalWorkingHour> workingHours =
        profissionalWorkingHourRepository.listByProfessional(tenantId, profissional.getId());
    if (workingHours == null || workingHours.isEmpty()) return List.of();

    int targetDay = date.getDayOfWeek().getValue();
    List<TimeRange> ranges = new ArrayList<>();
    for (ProfissionalWorkingHour item : workingHours) {
      if (item == null || !item.isWorking()) continue;
      if (!matchesDay(item.getDayOfWeek(), targetDay)) continue;
      LocalTime start = item.getStartTime();
      LocalTime end = item.getEndTime();
      if (start == null || end == null || !start.isBefore(end)) continue;
      ranges.add(new TimeRange(start, end));
    }

    return mergeRanges(ranges);
  }

  /**
   * O banco guarda o dia da semana ora no padrao ISO (1=segunda..7=domingo), ora no padrao
   * JavaScript (0=domingo). O original aceita os dois; preservado.
   */
  private boolean matchesDay(int configuredDay, int targetDayIso) {
    if (configuredDay == targetDayIso) return true;
    int targetJs = targetDayIso == DayOfWeek.SUNDAY.getValue() ? 0 : targetDayIso;
    return configuredDay == targetJs;
  }

  private LocalTime parseTimeOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalTime.parse(value.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private List<TimeRange> converterParaFaixasOcupadas(List<Agendamento> appointments) {
    if (appointments == null || appointments.isEmpty()) return List.of();

    List<TimeRange> busy = new ArrayList<>(appointments.size());
    for (Agendamento appointment : appointments) {
      if (appointment == null) continue;
      LocalTime start = parseTimeOrNull(appointment.getStartTime());
      LocalTime end = parseTimeOrNull(appointment.getEndTime());
      if (start == null || end == null || !start.isBefore(end)) continue;

      busy.add(new TimeRange(start, end));
    }
    return busy;
  }

  private List<TimeRange> expandirComBuffer(List<TimeRange> ranges, int bufferMinutes) {
    if (ranges == null || ranges.isEmpty() || bufferMinutes <= 0) {
      return ranges == null ? List.of() : ranges;
    }
    List<TimeRange> expanded = new ArrayList<>(ranges.size());
    for (TimeRange range : ranges) {
      expanded.add(
          new TimeRange(
              range.start.minusMinutes(bufferMinutes), range.end.plusMinutes(bufferMinutes)));
    }
    return expanded;
  }

  private List<TimeRange> mergeRanges(List<TimeRange> ranges) {
    if (ranges == null || ranges.isEmpty()) return List.of();

    List<TimeRange> ordered = new ArrayList<>(ranges);
    ordered.sort(Comparator.comparing(range -> range.start));

    List<TimeRange> merged = new ArrayList<>();
    TimeRange current = ordered.get(0);
    for (int i = 1; i < ordered.size(); i++) {
      TimeRange next = ordered.get(i);
      if (!next.start.isAfter(current.end)) {
        current = new TimeRange(current.start, maxTime(current.end, next.end));
      } else {
        merged.add(current);
        current = next;
      }
    }
    merged.add(current);
    return merged;
  }

  private List<TimeRange> intersectRanges(List<TimeRange> left, List<TimeRange> right) {
    if (left == null || left.isEmpty() || right == null || right.isEmpty()) return List.of();

    List<TimeRange> intersections = new ArrayList<>();
    for (TimeRange leftRange : left) {
      for (TimeRange rightRange : right) {
        LocalTime start = maxTime(leftRange.start, rightRange.start);
        LocalTime end = minTime(leftRange.end, rightRange.end);
        if (start.isBefore(end)) {
          intersections.add(new TimeRange(start, end));
        }
      }
    }
    return mergeRanges(intersections);
  }

  private LocalTime maxTime(LocalTime a, LocalTime b) {
    return a.isAfter(b) ? a : b;
  }

  private LocalTime minTime(LocalTime a, LocalTime b) {
    return a.isBefore(b) ? a : b;
  }

  private List<TimeRange> calculateFreeRanges(
      List<TimeRange> workingRanges, List<TimeRange> busyRanges) {
    List<TimeRange> freeRanges = new ArrayList<>();
    for (TimeRange working : workingRanges) {
      LocalTime cursor = working.start;
      for (TimeRange busy : busyRanges) {
        if (!busy.end.isAfter(working.start) || !busy.start.isBefore(working.end)) continue;

        LocalTime clampedBusyStart = maxTime(busy.start, working.start);
        LocalTime clampedBusyEnd = minTime(busy.end, working.end);

        if (cursor.isBefore(clampedBusyStart)) {
          freeRanges.add(new TimeRange(cursor, clampedBusyStart));
        }
        if (cursor.isBefore(clampedBusyEnd)) {
          cursor = clampedBusyEnd;
        }
        if (!cursor.isBefore(working.end)) break;
      }
      if (cursor.isBefore(working.end)) {
        freeRanges.add(new TimeRange(cursor, working.end));
      }
    }
    return freeRanges;
  }

  private TimeRange obterJanelaFuncionamentoSalao(UUID tenantId, LocalDate date) {
    SalonDtos.BusinessHour businessHour =
        tenantOperationalSettingsService.getBusinessHourForDate(tenantId, date);
    if (businessHour == null || !businessHour.enabled) return null;

    LocalTime open = parseTimeOrNull(businessHour.open);
    LocalTime close = parseTimeOrNull(businessHour.close);
    if (open == null || close == null || !open.isBefore(close)) return null;
    return new TimeRange(open, close);
  }

  private List<TimeSlotResponse> montarSlotsDaLacuna(
      TimeRange freeRange,
      int serviceDurationMinutes,
      int bufferMinutes,
      List<TimeRange> busyRanges) {
    List<TimeSlotResponse> slots = new ArrayList<>();
    if (minutesBetween(freeRange.start, freeRange.end) < serviceDurationMinutes) return slots;

    LocalTime latestStart = freeRange.end.minusMinutes(serviceDurationMinutes);
    for (LocalTime start = freeRange.start;
        !start.isAfter(latestStart);
        start = start.plusMinutes(DEFAULT_SLOT_STEP_MINUTES)) {
      LocalTime end = start.plusMinutes(serviceDurationMinutes);
      if (detectConflict(start, end, busyRanges, bufferMinutes)) continue;
      if (geraLacunaRuim(freeRange.start, start) || geraLacunaRuim(end, freeRange.end)) continue;

      TimeSlotResponse slot = new TimeSlotResponse();
      slot.startTime = start;
      slot.endTime = end;
      slot.optimizationScore = calculateOptimizationScore(freeRange, start, end);
      slots.add(slot);
    }
    return slots;
  }

  private List<Agendamento> detectConflictingAppointments(
      LocalTime newStart, LocalTime newEnd, List<Agendamento> appointments, int bufferMinutes) {
    if (appointments == null || appointments.isEmpty()) return List.of();
    LocalTime bufferedStart = newStart.minusMinutes(bufferMinutes);
    LocalTime bufferedEnd = newEnd.plusMinutes(bufferMinutes);

    return appointments.stream()
        .filter(
            appointment -> {
              LocalTime start = parseTimeOrNull(appointment.getStartTime());
              LocalTime end = parseTimeOrNull(appointment.getEndTime());
              if (start == null || end == null) return false;
              return bufferedStart.isBefore(end) && bufferedEnd.isAfter(start);
            })
        .sorted(Comparator.comparing(appointment -> parseTimeOrNull(appointment.getStartTime())))
        .toList();
  }

  private boolean detectConflict(
      LocalTime newStart, LocalTime newEnd, List<TimeRange> busyRanges, int bufferMinutes) {
    if (busyRanges == null || busyRanges.isEmpty()) return false;
    LocalTime bufferedStart = newStart.minusMinutes(bufferMinutes);
    LocalTime bufferedEnd = newEnd.plusMinutes(bufferMinutes);
    for (TimeRange busy : busyRanges) {
      if (bufferedStart.isBefore(busy.end) && bufferedEnd.isAfter(busy.start)) {
        return true;
      }
    }
    return false;
  }

  private boolean geraLacunaRuim(LocalTime gapStart, LocalTime gapEnd) {
    long gapMinutes = minutesBetween(gapStart, gapEnd);
    return gapMinutes > 0 && gapMinutes < MIN_UNDESIRED_GAP_MINUTES;
  }

  private int calculateOptimizationScore(
      TimeRange freeRange, LocalTime slotStart, LocalTime slotEnd) {
    long leftGap = minutesBetween(freeRange.start, slotStart);
    long rightGap = minutesBetween(slotEnd, freeRange.end);

    int score = 100;
    if (leftGap == 0 && rightGap == 0) return 300;
    if (leftGap == 0 || rightGap == 0) score += 80;
    if (leftGap >= MIN_UNDESIRED_GAP_MINUTES && rightGap >= MIN_UNDESIRED_GAP_MINUTES) score += 20;
    return score;
  }

  /**
   * O original tentava primeiro a associacao ja carregada ({@code appointment.client},
   * {@code item.service}) e so caia no repositorio quando ela vinha nula. Como {@link Agendamento}
   * migrado nao tem {@code client}, o cliente e sempre resolvido pelo repositorio — mesmo resultado,
   * uma consulta a mais. O servico continua vindo da associacao de {@link AgendamentoItem}, que foi
   * preservada.
   */
  private AppointmentConflictSummaryResponse toConflictSummary(
      UUID tenantId, Agendamento appointment) {
    AppointmentConflictSummaryResponse response = new AppointmentConflictSummaryResponse();
    response.appointmentId = appointment.getId() != null ? appointment.getId().toString() : null;
    response.clientId = appointment.getClientId() != null ? appointment.getClientId().toString() : null;
    response.startTime = appointment.getStartTime();
    response.endTime = appointment.getEndTime();
    response.status = appointment.getStatus() != null ? appointment.getStatus().name() : null;

    Cliente client =
        appointment.getClientId() != null
            ? clienteRepository.findByIdAndTenantId(appointment.getClientId(), tenantId).orElse(null)
            : null;
    response.clientName = client != null ? client.getName() : null;

    AgendamentoItem primaryItem = appointment.resolvePrimaryItem();
    UUID serviceId =
        primaryItem != null ? primaryItem.getServiceId() : appointment.resolvePrimaryServiceId();
    response.serviceId = serviceId != null ? serviceId.toString() : null;
    Servico service =
        primaryItem != null && primaryItem.getService() != null
            ? primaryItem.getService()
            : (serviceId != null
                ? servicoRepository.findByIdAndTenantId(serviceId, tenantId).orElse(null)
                : null);
    response.serviceName = service != null ? service.getName() : null;
    return response;
  }

  private String slotKey(LocalTime start, LocalTime end) {
    return start + "|" + end;
  }

  private long minutesBetween(LocalTime start, LocalTime end) {
    return ChronoUnit.MINUTES.between(start, end);
  }

  private record SchedulingContext(
      UUID tenantId,
      List<TimeRange> workingRanges,
      List<Agendamento> appointments,
      List<TimeRange> busyRanges,
      List<TimeRange> freeRanges,
      int serviceDurationMinutes,
      int bufferMinutes) {}

  private static final class TimeRange {
    private final LocalTime start;
    private final LocalTime end;

    private TimeRange(LocalTime start, LocalTime end) {
      this.start = start;
      this.end = end;
    }
  }
}
