package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoMetricaDiariaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentCustomerNoteRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentCustomerNoteResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentCustomerNoteUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentDetailResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentManagementReportResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentSchedulingSettingsRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentSchedulingSettingsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentStatusUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AttendanceRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.ManualTimeSlotResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.NoShowReportResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.PendingAttendanceResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.TimeSlotResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.AppointmentService;
import br.com.phdigitalcode.azzo.agenda.pro.service.AppointmentSettingsService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoAgendamentos;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/scheduling/api/AgendamentosResource.java} — mesmos paths, verbos,
 * parametros, roles e permissoes.
 *
 * <p>Atencao aos verbos: quase toda mutacao usa {@code PATCH} (status, edicao, presenca, notas,
 * realocacao); so a criacao e as notas novas usam {@code POST}, e as configuracoes da agenda usam
 * {@code PUT}.
 *
 * <p>Os dois exports CSV devolvem {@link StreamingResponseBody}: o service materializa as linhas
 * dentro da sua transacao, porque o corpo do stream so roda depois deste metodo retornar, com a
 * sessao JPA ja fechada.
 */
@RestController
@RequestMapping("/api/v1/appointments")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class AgendamentosController {

  private final ServicoAgendamentos servicoAgendamentos;
  private final AppointmentService appointmentService;
  private final AppointmentSettingsService appointmentSettingsService;
  private final ServicoRepository servicoRepository;
  private final ProfissionalRepository profissionalRepository;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;

  public AgendamentosController(
      ServicoAgendamentos servicoAgendamentos,
      AppointmentService appointmentService,
      AppointmentSettingsService appointmentSettingsService,
      ServicoRepository servicoRepository,
      ProfissionalRepository profissionalRepository,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser) {
    this.servicoAgendamentos = servicoAgendamentos;
    this.appointmentService = appointmentService;
    this.appointmentSettingsService = appointmentSettingsService;
    this.servicoRepository = servicoRepository;
    this.profissionalRepository = profissionalRepository;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping
  @RequiresPermission("appointment:read")
  public List<AgendamentoResponse> listar(
      @RequestParam(name = "date", required = false) String date,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "200") int size) {
    return servicoAgendamentos.listar(parseLocalDateNullable(date), page, size);
  }

  @GetMapping("/management-report")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("appointment:read")
  public AppointmentManagementReportResponse listarRelatorioGerencial(
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "professionalId", required = false) String professionalId,
      @RequestParam(name = "serviceId", required = false) String serviceId,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize) {
    return servicoAgendamentos.listarRelatorioGerencial(
        parseLocalDateNullable(from),
        parseLocalDateNullable(to),
        parseUuidNullable(professionalId),
        parseUuidNullable(serviceId),
        status,
        limit,
        page,
        pageSize);
  }

  @GetMapping(value = "/management-report/export", produces = "text/csv")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("appointment:read")
  public ResponseEntity<StreamingResponseBody> exportarRelatorioGerencial(
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "professionalId", required = false) String professionalId,
      @RequestParam(name = "serviceId", required = false) String serviceId,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "limit", required = false) Integer limit) {
    StreamingResponseBody stream =
        servicoAgendamentos.exportarRelatorioGerencialCsv(
            parseLocalDateNullable(from),
            parseLocalDateNullable(to),
            parseUuidNullable(professionalId),
            parseUuidNullable(serviceId),
            status,
            limit);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + buildManagementReportCsvFilename(from, to) + "\"")
        .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
        .body(stream);
  }

  @GetMapping("/my-history")
  @PreAuthorize("hasRole('PROFESSIONAL')")
  @RequiresPermission("appointment:read")
  public AppointmentManagementReportResponse meuHistoricoProducao(
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = authenticatedUser.idOuFalhar();
    Profissional profissional =
        profissionalRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(
                () ->
                    new ApiClientErrorException(
                        "Profissional nao encontrado para o usuario autenticado",
                        HttpStatus.FORBIDDEN.value()));
    return servicoAgendamentos.listarRelatorioGerencial(
        parseLocalDateNullable(from),
        parseLocalDateNullable(to),
        profissional.getId(),
        null,
        status,
        limit,
        page,
        pageSize);
  }

  @GetMapping("/no-show")
  @RequiresPermission("appointment:read")
  public NoShowReportResponse listarNoShows(
      @RequestParam(name = "afterId", required = false) String afterId,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "professionalId", required = false) String professionalId,
      @RequestParam(name = "serviceId", required = false) String serviceId,
      @RequestParam(name = "clientIds", required = false) String clientIds,
      @RequestParam(name = "clientQuery", required = false) String clientQuery,
      @RequestParam(name = "groupBy", required = false) String groupBy) {
    return servicoAgendamentos.listarNoShows(
        parseUuidNullable(afterId),
        limit,
        parseLocalDateNullable(from),
        parseLocalDateNullable(to),
        parseUuidNullable(professionalId),
        parseUuidNullable(serviceId),
        parseUuidListNullable(clientIds),
        clientQuery,
        groupBy);
  }

  @GetMapping(value = "/no-show/export", produces = "text/csv")
  @RequiresPermission("appointment:read")
  public ResponseEntity<StreamingResponseBody> exportarNoShows(
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "professionalId", required = false) String professionalId,
      @RequestParam(name = "serviceId", required = false) String serviceId,
      @RequestParam(name = "clientIds", required = false) String clientIds,
      @RequestParam(name = "clientQuery", required = false) String clientQuery,
      @RequestParam(name = "groupBy", required = false) String groupBy) {
    StreamingResponseBody stream =
        servicoAgendamentos.exportarNoShowsCsv(
            parseLocalDateNullable(from),
            parseLocalDateNullable(to),
            parseUuidNullable(professionalId),
            parseUuidNullable(serviceId),
            parseUuidListNullable(clientIds),
            clientQuery,
            groupBy);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + buildNoShowCsvFilename(from, to) + "\"")
        .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
        .body(stream);
  }

  @GetMapping("/{id}")
  @RequiresPermission("appointment:read")
  public AppointmentDetailResponse obterPorId(@PathVariable UUID id) {
    return servicoAgendamentos.obterDetalhe(id);
  }

  @GetMapping("/metric")
  @RequiresPermission("appointment:read")
  public List<AgendamentoMetricaDiariaResponse> listarMetricasDiarias(
      @RequestParam(name = "mes", required = false) Integer mes,
      @RequestParam(name = "ano", required = false) Integer ano) {
    return servicoAgendamentos.listarMetricasDiarias(mes, ano);
  }

  @PostMapping
  @RequiresPermission("appointment:write")
  public AgendamentoResponse criar(@Valid @RequestBody AgendamentoRequest request) {
    return servicoAgendamentos.criar(request);
  }

  @GetMapping("/available-slots")
  @RequiresPermission("appointment:read")
  public List<TimeSlotResponse> listarHorariosDisponiveis(
      @RequestParam(name = "professionalId", required = false) String professionalId,
      @RequestParam(name = "date", required = false) String date,
      @RequestParam(name = "serviceIds", required = false) String serviceIds,
      @RequestParam(name = "serviceDurationMinutes", defaultValue = "0") int serviceDurationMinutes,
      @RequestParam(name = "bufferMinutes", required = false) Integer bufferMinutes) {
    try {
      UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
      UUID professionalUuid = UUID.fromString(professionalId);
      LocalDate parsedDate = LocalDate.parse(date);
      int effectiveDuration = resolveDuration(tenantId, serviceIds, serviceDurationMinutes);
      int safeBuffer = bufferMinutes == null ? 0 : bufferMinutes;
      return appointmentService.findAvailableSlots(
          tenantId, professionalUuid, parsedDate, effectiveDuration, safeBuffer);
    } catch (IllegalArgumentException | DateTimeParseException | NullPointerException e) {
      throw new ApiClientErrorException(e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }
  }

  @GetMapping("/manual-slots")
  @RequiresPermission("appointment:read")
  public List<ManualTimeSlotResponse> listarHorariosManuais(
      @RequestParam(name = "professionalId", required = false) String professionalId,
      @RequestParam(name = "date", required = false) String date,
      @RequestParam(name = "serviceIds", required = false) String serviceIds,
      @RequestParam(name = "serviceDurationMinutes", defaultValue = "0") int serviceDurationMinutes,
      @RequestParam(name = "bufferMinutes", required = false) Integer bufferMinutes) {
    try {
      UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
      UUID professionalUuid = UUID.fromString(professionalId);
      LocalDate parsedDate = LocalDate.parse(date);
      int effectiveDuration = resolveDuration(tenantId, serviceIds, serviceDurationMinutes);
      int safeBuffer = bufferMinutes == null ? 0 : bufferMinutes;
      boolean includeConflictSlots =
          appointmentSettingsService.allowsManualConflictByTenantId(tenantId);
      return appointmentService.findManualSlots(
          tenantId, professionalUuid, parsedDate, effectiveDuration, safeBuffer, includeConflictSlots);
    } catch (IllegalArgumentException | DateTimeParseException | NullPointerException e) {
      throw new ApiClientErrorException(e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }
  }

  @GetMapping("/settings")
  @RequiresPermission("appointment:read")
  public AppointmentSchedulingSettingsResponse obterConfiguracoesAgenda() {
    return appointmentSettingsService.getSettings();
  }

  @PutMapping("/settings")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("appointment:write")
  public AppointmentSchedulingSettingsResponse atualizarConfiguracoesAgenda(
      @Valid @RequestBody AppointmentSchedulingSettingsRequest request) {
    return appointmentSettingsService.updateSettings(request);
  }

  @GetMapping("/pending-attendance-confirmation")
  @RequiresPermission("appointment:read")
  public List<PendingAttendanceResponse> listarPendentesConfirmacaoPresenca() {
    return servicoAgendamentos.listarPendentesConfirmacaoPresenca();
  }

  @PatchMapping("/{id}/attendance")
  @RequiresPermission("appointment:write")
  public AgendamentoResponse registrarPresenca(
      @PathVariable UUID id, @Valid @RequestBody(required = false) AttendanceRequest request) {
    if (request == null || request.attended == null) {
      throw new ApiClientErrorException(
          "Campo attended e obrigatorio", HttpStatus.BAD_REQUEST.value());
    }
    return servicoAgendamentos.registrarPresenca(id, request.attended);
  }

  @PatchMapping("/{id}")
  @RequiresPermission("appointment:write")
  public AgendamentoResponse atualizar(
      @PathVariable UUID id, @Valid @RequestBody AppointmentUpdateRequest request) {
    return servicoAgendamentos.atualizar(id, request);
  }

  @PatchMapping("/{id}/status")
  @RequiresPermission("appointment:write")
  public AgendamentoResponse atualizarStatus(
      @PathVariable UUID id,
      @RequestParam(name = "value", required = false) String value,
      @RequestBody(required = false) AppointmentStatusUpdateRequest request) {
    return servicoAgendamentos.atualizarStatus(
        id,
        value,
        request != null ? request.paymentMethod : null,
        request != null ? request.conclusionAction : null);
  }

  @PostMapping("/{id}/customer-notes")
  @RequiresPermission("appointment:write")
  public AppointmentCustomerNoteResponse adicionarNotaCliente(
      @PathVariable UUID id, @Valid @RequestBody AppointmentCustomerNoteRequest request) {
    return servicoAgendamentos.adicionarNotaCliente(id, request);
  }

  @PatchMapping("/{id}/customer-notes/{noteId}")
  @RequiresPermission("appointment:write")
  public AppointmentCustomerNoteResponse atualizarNotaCliente(
      @PathVariable UUID id,
      @PathVariable UUID noteId,
      @Valid @RequestBody AppointmentCustomerNoteUpdateRequest request) {
    return servicoAgendamentos.atualizarNotaCliente(id, noteId, request);
  }

  @PatchMapping("/{id}/reassign-professional")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("appointment:write")
  public AgendamentoResponse realocarProfissional(
      @PathVariable UUID id,
      @RequestParam(name = "professionalId", required = false) UUID professionalId) {
    return servicoAgendamentos.realocarProfissional(id, professionalId);
  }

  @DeleteMapping("/{id}/customer-notes/{noteId}")
  @RequiresPermission("appointment:write")
  public void deletarNotaCliente(@PathVariable UUID id, @PathVariable UUID noteId) {
    servicoAgendamentos.deletarNotaCliente(id, noteId);
  }

  @DeleteMapping("/{id}")
  @RequiresPermission("appointment:write")
  public void deletar(@PathVariable UUID id) {
    servicoAgendamentos.deletar(id);
  }

  // ─── HELPERS ──────────────────────────────────────────────────────────────

  private int resolveDuration(UUID tenantId, String serviceIds, int fallbackDuration) {
    if (serviceIds == null || serviceIds.isBlank()) {
      return fallbackDuration;
    }
    List<UUID> ids =
        Arrays.stream(serviceIds.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .map(UUID::fromString)
            .toList();
    if (ids.isEmpty()) return fallbackDuration;
    int duration =
        servicoRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
            .mapToInt(Servico::getDuration)
            .sum();
    return duration > 0 ? duration : fallbackDuration;
  }

  private UUID parseUuidNullable(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new ApiClientErrorException("UUID invalido", HttpStatus.BAD_REQUEST.value());
    }
  }

  private List<UUID> parseUuidListNullable(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return Arrays.stream(value.split(","))
          .map(String::trim)
          .filter(item -> !item.isBlank())
          .map(UUID::fromString)
          .toList();
    } catch (IllegalArgumentException e) {
      throw new ApiClientErrorException("UUID invalido", HttpStatus.BAD_REQUEST.value());
    }
  }

  private LocalDate parseLocalDateNullable(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new ApiClientErrorException("Data invalida", HttpStatus.BAD_REQUEST.value());
    }
  }

  private String buildNoShowCsvFilename(String from, String to) {
    return "no-show-" + buildPeriodSuffix(from, to) + ".csv";
  }

  private String buildManagementReportCsvFilename(String from, String to) {
    return "relatorio-agendamentos-" + buildPeriodSuffix(from, to) + ".csv";
  }

  private String buildPeriodSuffix(String from, String to) {
    return (from != null && !from.isBlank() ? from : "inicio")
        + "-"
        + (to != null && !to.isBlank() ? to : "hoje");
  }
}
