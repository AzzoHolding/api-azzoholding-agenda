package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.TimeSlotResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository.ClienteStats;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.AppointmentService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NotificationPublisher;
import br.com.phdigitalcode.azzo.agenda.pro.service.ProfissionalService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoAgendamentos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoService;
import br.com.phdigitalcode.azzo.agenda.pro.service.TenantOperationalSettingsService;
import jakarta.transaction.Transactional;

/**
 * Espelha {@code modules/assistantintegration/api/internal/AssistantInternalResource.java}.
 *
 * <p>Endpoints internos exclusivos para o {@code azzo-assistant-api}, protegidos pelo
 * {@link br.com.phdigitalcode.azzo.agenda.pro.security.InternalApiKeyFilter} (header
 * {@code X-Internal-Api-Key}) e mantidos em {@code permitAll} no {@code SecurityConfig} — mesma
 * infraestrutura ja usada por {@link InternalPlansController}. Todos os endpoints recebem
 * {@code tenantId} via query param, exatamente como no original.
 *
 * <p>Nao usa {@code ClienteService}: o original monta {@code Cliente}/{@code ClienteDto} direto via
 * repositorios, sem passar pelas regras completas do CRUD publico de clientes (dedupe por
 * telefone/e-mail e feito aqui, nao no service), entao a paridade exige preservar esse acesso
 * direto em vez de reusar o service.
 */
@RestController
@RequestMapping("/api/v1/internal/assistant")
public class AssistantInternalController {

  private final TenantRepository tenantRepository;
  private final ServicoService servicoService;
  private final ProfissionalService profissionalService;
  private final ServicoAgendamentos servicoAgendamentos;
  private final AppointmentService appointmentService;
  private final NotificationPublisher notificationPublisher;
  private final ClienteRepository clienteRepository;
  private final ClienteStatsRepository clienteStatsRepository;
  private final AgendamentoRepository agendamentoRepository;
  private final ServicoRepository servicoRepository;
  private final ProfissionalRepository profissionalRepository;
  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private final ContextoTenant contextoTenant;
  private final TenantOperationalSettingsService tenantOperationalSettingsService;

  public AssistantInternalController(
      TenantRepository tenantRepository,
      ServicoService servicoService,
      ProfissionalService profissionalService,
      ServicoAgendamentos servicoAgendamentos,
      AppointmentService appointmentService,
      NotificationPublisher notificationPublisher,
      ClienteRepository clienteRepository,
      ClienteStatsRepository clienteStatsRepository,
      AgendamentoRepository agendamentoRepository,
      ServicoRepository servicoRepository,
      ProfissionalRepository profissionalRepository,
      TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository,
      ContextoTenant contextoTenant,
      TenantOperationalSettingsService tenantOperationalSettingsService) {
    this.tenantRepository = tenantRepository;
    this.servicoService = servicoService;
    this.profissionalService = profissionalService;
    this.servicoAgendamentos = servicoAgendamentos;
    this.appointmentService = appointmentService;
    this.notificationPublisher = notificationPublisher;
    this.clienteRepository = clienteRepository;
    this.clienteStatsRepository = clienteStatsRepository;
    this.agendamentoRepository = agendamentoRepository;
    this.servicoRepository = servicoRepository;
    this.profissionalRepository = profissionalRepository;
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
    this.contextoTenant = contextoTenant;
    this.tenantOperationalSettingsService = tenantOperationalSettingsService;
  }

  // ─── Info do Tenant ───────────────────────────────────────────────────────

  @GetMapping("/tenant/info")
  public ResponseEntity<TenantInfoResponse> obterInfoTenant(@RequestParam("tenantId") String tenantId) {
    UUID tid = UUID.fromString(tenantId);
    Tenant tenant = tenantRepository.findById(tid).orElse(null);
    if (tenant == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    return ResponseEntity.ok(new TenantInfoResponse(tenant.getName(), tenant.getDescription()));
  }

  /**
   * Retorna os horarios de funcionamento do salao para o assistente virtual. Usa o mesmo
   * armazenamento do painel de configuracoes.
   */
  @GetMapping("/tenant/business-hours")
  public List<SalonDtos.BusinessHour> obterHorariosFuncionamento(@RequestParam("tenantId") String tenantId) {
    UUID tid = UUID.fromString(tenantId);
    return tenantOperationalSettingsService.getBusinessHours(tid);
  }

  // ─── Servicos ─────────────────────────────────────────────────────────────

  @GetMapping("/services")
  public List<ServicoResponse> listarServicos(@RequestParam("tenantId") String tenantId) {
    contextoTenant.definirTenantId(UUID.fromString(tenantId));
    try {
      return servicoService.listar().stream().filter(s -> s.isActive).toList();
    } finally {
      contextoTenant.limparTenantIdOverride();
    }
  }

  // ─── Profissionais ────────────────────────────────────────────────────────

  @GetMapping("/professionals")
  public List<ProfissionalResponse> listarProfissionais(
      @RequestParam("tenantId") String tenantId,
      @RequestParam(value = "serviceId", required = false) String serviceId) {
    contextoTenant.definirTenantId(UUID.fromString(tenantId));
    try {
      return profissionalService.listar(serviceId);
    } finally {
      contextoTenant.limparTenantIdOverride();
    }
  }

  // ─── Slots disponiveis ────────────────────────────────────────────────────

  @GetMapping("/available-slots")
  public List<TimeSlotResponse> buscarSlotsDisponiveis(
      @RequestParam("tenantId") String tenantId,
      @RequestParam("professionalId") String professionalId,
      @RequestParam("date") String date,
      @RequestParam(value = "serviceIds", required = false) String serviceIds,
      @RequestParam(value = "duration", defaultValue = "30") int duration,
      @RequestParam(value = "buffer", defaultValue = "0") int buffer) {
    UUID tid = UUID.fromString(tenantId);
    UUID pid = UUID.fromString(professionalId);
    LocalDate localDate = LocalDate.parse(date);
    contextoTenant.definirTenantId(tid);
    try {
      int effectiveDuration = resolveDuration(tid, serviceIds, duration);
      return appointmentService.findAvailableSlots(tid, pid, localDate, effectiveDuration, buffer);
    } finally {
      contextoTenant.limparTenantIdOverride();
    }
  }

  // ─── Agendamentos ─────────────────────────────────────────────────────────

  @PostMapping("/appointments")
  @Transactional
  public AgendamentoResponse criarAgendamento(
      @RequestParam("tenantId") String tenantId,
      @RequestBody AgendamentoRequest request) {
    contextoTenant.definirTenantId(UUID.fromString(tenantId));
    try {
      return servicoAgendamentos.criar(request);
    } finally {
      contextoTenant.limparTenantIdOverride();
    }
  }

  @PatchMapping("/appointments/{id}/status")
  @Transactional
  public ResponseEntity<Void> atualizarStatusAgendamento(
      @PathVariable("id") String id,
      @RequestParam("tenantId") String tenantId,
      @RequestBody StatusUpdateRequest request) {
    contextoTenant.definirTenantId(UUID.fromString(tenantId));
    try {
      servicoAgendamentos.atualizarStatus(UUID.fromString(id), request.status);
      return ResponseEntity.noContent().build();
    } finally {
      contextoTenant.limparTenantIdOverride();
    }
  }

  @GetMapping("/clients/{clientId}/appointments")
  public List<AgendamentoResponse> listarAgendamentosCliente(
      @PathVariable("clientId") String clientId,
      @RequestParam("tenantId") String tenantId,
      @RequestParam(value = "limit", defaultValue = "10") int limit) {
    UUID tid = UUID.fromString(tenantId);
    UUID cid = UUID.fromString(clientId);
    int normalizedLimit = Math.max(1, Math.min(limit, 50));
    return agendamentoRepository
        .listByTenantAndClientExcludingStatus(tid, cid, StatusAgendamento.CANCELLED, Limit.of(normalizedLimit))
        .stream()
        .map(this::toAgendamentoResponse)
        .toList();
  }

  // ─── Clientes ─────────────────────────────────────────────────────────────

  @GetMapping("/clients/search")
  public ResponseEntity<ClienteDto> buscarClientePorIdentificador(
      @RequestParam("tenantId") String tenantId,
      @RequestParam("identifier") String identifier) {
    UUID tid = UUID.fromString(tenantId);
    Cliente cliente = findClientByIdentifier(tid, identifier);
    if (cliente == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    return ResponseEntity.ok(toClienteDto(cliente));
  }

  @PostMapping("/clients")
  @Transactional
  public ResponseEntity<ClienteDto> criarCliente(
      @RequestParam("tenantId") String tenantId,
      @RequestBody ClienteCreateRequest request) {
    UUID tid = UUID.fromString(tenantId);

    // Verifica se ja existe antes de criar
    if (request.identifier != null) {
      Cliente existing = findClientByIdentifier(tid, request.identifier);
      if (existing != null) {
        return ResponseEntity.ok(toClienteDto(existing));
      }
    }

    Cliente cliente = new Cliente();
    cliente.setTenantId(tid);
    cliente.setName(
        (request.name != null && !request.name.isBlank()) ? request.name.trim() : "Cliente Assistente");
    if (request.phone != null && !request.phone.isBlank()) {
      cliente.setPhone(request.phone.replaceAll("\\D", ""));
    }
    if (request.email != null && !request.email.isBlank()) {
      cliente.setEmail(request.email.toLowerCase(Locale.ROOT));
    }
    clienteRepository.save(cliente);
    return ResponseEntity.status(HttpStatus.CREATED).body(toClienteDto(cliente));
  }

  // ─── Notificacoes ─────────────────────────────────────────────────────────

  @PostMapping("/notifications")
  @Transactional
  public ResponseEntity<Void> criarNotificacao(
      @RequestParam("tenantId") String tenantId,
      @RequestBody NotificacaoCreateRequest request) {
    UUID tid = UUID.fromString(tenantId);

    notificationPublisher.publish(
        tid,
        request.appointmentId != null && !request.appointmentId.isBlank()
            ? UUID.fromString(request.appointmentId)
            : null,
        request.channel,
        request.destination,
        request.message,
        resolveStatusNotification(request.status),
        null,
        Instant.now());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  // ─── Permissoes WhatsApp ──────────────────────────────────────────────────

  @GetMapping("/tenant/whatsapp-permissions")
  @Transactional
  public WhatsAppPermissoesResponse obterPermissoesWhatsApp(@RequestParam("tenantId") String tenantId) {
    UUID tid = UUID.fromString(tenantId);
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tid);
    return new WhatsAppPermissoesResponse(config.isCanSchedule(), config.isCanCancel(), config.isCanReschedule());
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private Cliente findClientByIdentifier(UUID tenantId, String identifier) {
    if (identifier == null || identifier.isBlank()) return null;
    String digits = identifier.replaceAll("\\D", "");
    if (digits.length() >= 10) {
      return clienteRepository.findByTenantAndPhoneDigits(tenantId, digits).orElse(null);
    }
    if (identifier.contains("@")) {
      return clienteRepository
          .findByTenantIdAndEmailIgnoreCase(tenantId, identifier.toLowerCase(Locale.ROOT))
          .orElse(null);
    }
    return null;
  }

  private AgendamentoResponse toAgendamentoResponse(Agendamento ag) {
    AgendamentoResponse r = new AgendamentoResponse();
    r.id = ag.getId().toString();
    r.tenantId = ag.getTenantId() != null ? ag.getTenantId().toString() : null;
    r.clientId = ag.getClientId() != null ? ag.getClientId().toString() : null;
    r.professionalId = ag.getProfessionalId() != null ? ag.getProfessionalId().toString() : null;
    UUID primaryServiceId = ag.resolvePrimaryServiceId();
    r.serviceId = primaryServiceId != null ? primaryServiceId.toString() : null;
    r.date = ag.getDate() != null ? ag.getDate().toString() : null;
    r.startTime = ag.getStartTime();
    r.endTime = ag.getEndTime();
    r.status = ag.getStatus() != null ? ag.getStatus().name() : null;
    r.notes = ag.getNotes();
    r.totalPrice = ag.resolveEffectiveTotalPrice();
    r.createdAt = ag.getCreatedAt() != null ? ag.getCreatedAt().toString() : null;

    if (ag.getClientId() != null && ag.getTenantId() != null) {
      Cliente c = clienteRepository.findByIdAndTenantId(ag.getClientId(), ag.getTenantId()).orElse(null);
      if (c != null) {
        ClienteResponse cr = new ClienteResponse();
        cr.id = c.getId().toString();
        cr.name = c.getName();
        cr.phone = c.getPhone();
        cr.email = c.getEmail();
        r.client = cr;
      }
    }

    if (ag.getProfessionalId() != null && ag.getTenantId() != null) {
      Profissional p =
          profissionalRepository.findByIdAndTenantId(ag.getProfessionalId(), ag.getTenantId()).orElse(null);
      if (p != null) {
        ProfissionalResponse pr = new ProfissionalResponse();
        pr.id = p.getId().toString();
        pr.name = p.getName();
        pr.email = p.getEmail();
        pr.phone = p.getPhone();
        r.professional = pr;
      }
    }

    if (primaryServiceId != null && ag.getTenantId() != null) {
      Servico s = servicoRepository.findByIdAndTenantId(primaryServiceId, ag.getTenantId()).orElse(null);
      if (s != null) {
        ServicoResponse sr = new ServicoResponse();
        sr.id = s.getId().toString();
        sr.name = s.getName();
        sr.duration = s.getDuration();
        sr.price = s.getPrice();
        r.service = sr;
      }
    }

    r.items = ag.getItems().stream().map(item -> {
      AgendamentoResponse.ItemResponse itemResponse = new AgendamentoResponse.ItemResponse();
      itemResponse.serviceId = item.getServiceId() != null ? item.getServiceId().toString() : null;
      itemResponse.quantity = item.getQuantity();
      itemResponse.unitPrice = item.getUnitPrice();
      itemResponse.totalPrice = item.getTotalPrice();
      return itemResponse;
    }).toList();
    return r;
  }

  private ClienteDto toClienteDto(Cliente c) {
    ClienteStats stats = clienteStatsRepository.findStatsByTenantAndClient(c.getTenantId(), c.getId());
    ClienteDto dto = new ClienteDto();
    dto.id = c.getId().toString();
    dto.name = c.getName();
    dto.email = c.getEmail();
    dto.phone = c.getPhone();
    dto.totalVisits = stats.totalVisits();
    return dto;
  }

  private StatusNotification resolveStatusNotification(String status) {
    try {
      return StatusNotification.valueOf(status);
    } catch (Exception ignored) {
      return StatusNotification.SENT;
    }
  }

  private int resolveDuration(UUID tenantId, String serviceIds, int fallbackDuration) {
    if (serviceIds == null || serviceIds.isBlank()) {
      return fallbackDuration;
    }
    List<UUID> ids = Arrays.stream(serviceIds.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .map(UUID::fromString)
        .toList();
    if (ids.isEmpty()) return fallbackDuration;
    int duration = servicoRepository.findAllById(ids).stream()
        .filter(s -> tenantId.equals(s.getTenantId()))
        .mapToInt(Servico::getDuration)
        .sum();
    return duration > 0 ? duration : fallbackDuration;
  }

  // ─── Inner DTOs ──────────────────────────────────────────────────────────

  public static class StatusUpdateRequest {
    public String status;
  }

  public static class ClienteCreateRequest {
    public String name;
    public String phone;
    public String email;
    public String identifier; // usado para deduplicacao
  }

  public static class NotificacaoCreateRequest {
    public String appointmentId;
    public String channel;
    public String destination;
    public String message;
    public String status;
  }

  public static class WhatsAppPermissoesResponse {
    public boolean canSchedule;
    public boolean canCancel;
    public boolean canReschedule;

    public WhatsAppPermissoesResponse() {}

    public WhatsAppPermissoesResponse(boolean canSchedule, boolean canCancel, boolean canReschedule) {
      this.canSchedule = canSchedule;
      this.canCancel = canCancel;
      this.canReschedule = canReschedule;
    }
  }

  public static class ClienteDto {
    public String id;
    public String name;
    public String email;
    public String phone;
    public int totalVisits;
  }

  public static class TenantInfoResponse {
    public String name;
    public String description;

    public TenantInfoResponse() {}

    public TenantInfoResponse(String name, String description) {
      this.name = name;
      this.description = description;
    }
  }
}
