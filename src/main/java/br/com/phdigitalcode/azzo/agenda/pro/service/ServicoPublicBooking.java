package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PublicBookingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentBookingFunnelEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.BookingFunnelStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.TenantDepositPaymentService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentBookingFunnelEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServiceCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;

/**
 * Espelha {@code modules/publicbooking/application/ServicoPublicBooking.java}. Rotas publicas
 * (sem autenticacao) consumidas pelo booking do frontend a partir do {@code slug} do salao.
 *
 * <p>Excecoes de validacao continuam {@link IllegalArgumentException} (nao
 * {@code ApiClientErrorException}), igual ao original — o {@code GlobalExceptionHandler} ja trata
 * {@code IllegalArgumentException} como erro funcional 400.
 */
@Service
public class ServicoPublicBooking {

  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private final TenantRepository tenantRepository;
  private final ServicoRepository servicoRepository;
  private final ServiceCategoryRepository serviceCategoryRepository;
  private final ProfissionalRepository profissionalRepository;
  private final AgendamentoRepository agendamentoRepository;
  private final AgendamentoItemRepository agendamentoItemRepository;
  private final ClienteRepository clienteRepository;
  private final AppointmentBookingFunnelEventRepository appointmentBookingFunnelEventRepository;
  private final TenantOperationalSettingsService tenantOperationalSettingsService;
  private final SpecialClosureService specialClosureService;
  private final NotificationService notificationService;
  private final TenantDepositPaymentService tenantDepositPaymentService;

  public ServicoPublicBooking(
      TenantRepository tenantRepository,
      ServicoRepository servicoRepository,
      ServiceCategoryRepository serviceCategoryRepository,
      ProfissionalRepository profissionalRepository,
      AgendamentoRepository agendamentoRepository,
      AgendamentoItemRepository agendamentoItemRepository,
      ClienteRepository clienteRepository,
      AppointmentBookingFunnelEventRepository appointmentBookingFunnelEventRepository,
      TenantOperationalSettingsService tenantOperationalSettingsService,
      SpecialClosureService specialClosureService,
      NotificationService notificationService,
      TenantDepositPaymentService tenantDepositPaymentService) {
    this.tenantRepository = tenantRepository;
    this.servicoRepository = servicoRepository;
    this.serviceCategoryRepository = serviceCategoryRepository;
    this.profissionalRepository = profissionalRepository;
    this.agendamentoRepository = agendamentoRepository;
    this.agendamentoItemRepository = agendamentoItemRepository;
    this.clienteRepository = clienteRepository;
    this.appointmentBookingFunnelEventRepository = appointmentBookingFunnelEventRepository;
    this.tenantOperationalSettingsService = tenantOperationalSettingsService;
    this.specialClosureService = specialClosureService;
    this.notificationService = notificationService;
    this.tenantDepositPaymentService = tenantDepositPaymentService;
  }

  @Transactional(readOnly = true)
  public List<ServicoResponse> listarServicosAtivos(String slug) {
    Tenant tenant = obterTenantPorSlug(slug);
    return servicoRepository.findByTenantId(tenant.getId()).stream()
        .filter(s -> s.isActive() && !s.getProfissionais().isEmpty())
        .map(this::toServicoResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ProfissionalResponse> listarProfissionaisAtivos(String slug) {
    return listarProfissionaisAtivos(slug, null, null);
  }

  @Transactional(readOnly = true)
  public List<ProfissionalResponse> listarProfissionaisAtivos(String slug, String serviceId) {
    return listarProfissionaisAtivos(slug, serviceId, null);
  }

  @Transactional(readOnly = true)
  public List<ProfissionalResponse> listarProfissionaisAtivos(String slug, String serviceId, String serviceIds) {
    Tenant tenant = obterTenantPorSlug(slug);
    List<Profissional> ativos = profissionalRepository.findByTenantIdAndIsActiveTrue(tenant.getId());
    List<Servico> selectedServices = resolveSelectedServices(tenant.getId(), serviceId, serviceIds);
    if (selectedServices.isEmpty()) {
      return ativos.stream().map(this::toProfissionalResponse).toList();
    }

    Set<UUID> allowedIds = ativos.stream().map(Profissional::getId).collect(Collectors.toSet());
    for (Servico servico : selectedServices) {
      if (servico.getProfissionais() == null || servico.getProfissionais().isEmpty()) continue;
      Set<UUID> serviceAllowedIds = servico.getProfissionais().stream()
          .filter(Profissional::isActive)
          .map(Profissional::getId)
          .collect(Collectors.toSet());
      allowedIds.retainAll(serviceAllowedIds);
    }

    return ativos.stream()
        .filter(profissional -> allowedIds.contains(profissional.getId()))
        .map(this::toProfissionalResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public PublicBookingDtos.AvailabilityResponse obterDisponibilidade(
      String slug, String date, String serviceId, String serviceIds, String professionalId) {
    Tenant tenant = obterTenantPorSlug(slug);
    LocalDate data = DataUtil.parseDataISO(date);
    if (data == null) throw new IllegalArgumentException("Data invalida");

    List<Servico> selectedServices = resolveSelectedServices(tenant.getId(), serviceId, serviceIds);
    if (selectedServices.isEmpty()) throw new IllegalArgumentException("Servico nao encontrado");

    UUID profissionalUuid = professionalId == null || professionalId.isBlank() ? null : UUID.fromString(professionalId);
    if (profissionalUuid != null) {
      Profissional profissional = profissionalRepository
          .findByIdAndTenantIdAndIsActiveTrue(profissionalUuid, tenant.getId())
          .orElse(null);
      if (profissional == null) throw new IllegalArgumentException("Profissional nao encontrado");
      for (Servico servico : selectedServices) {
        if (servico.getProfissionais() != null
            && !servico.getProfissionais().isEmpty()
            && servico.getProfissionais().stream().noneMatch(p -> p.getId().equals(profissionalUuid))) {
          throw new IllegalArgumentException("Profissional nao atende o servico selecionado");
        }
      }
    }

    List<Agendamento> agendamentosDia = profissionalUuid == null
        ? agendamentoRepository.findByTenantIdAndDate(tenant.getId(), data)
        : agendamentoRepository.findByTenantIdAndDateAndProfessionalId(tenant.getId(), data, profissionalUuid);

    List<Agendamento> ativos = agendamentosDia.stream()
        .filter(a -> a.getStatus() != StatusAgendamento.CANCELLED)
        .toList();

    PublicBookingDtos.AvailabilityResponse resp = new PublicBookingDtos.AvailabilityResponse();
    resp.date = data.toString();
    // Usa isClosedAt para verificar fechamentos all_day e parciais (inclui profissional quando informado)
    if (specialClosureService.isClosedAt(tenant.getId(), profissionalUuid, data, null, null)) {
      return resp;
    }
    LocalTime[] janela = obterJanelaDia(tenant.getId(), data);
    if (janela == null) {
      return resp;
    }
    LocalTime cursor = janela[0];
    LocalTime fim = janela[1];
    long totalDuration = selectedServices.stream().mapToLong(Servico::getDuration).sum();
    // Para o dia atual, nao exibe horarios que ja passaram
    LocalTime agora = data.isEqual(LocalDate.now(ZONE_BR)) ? LocalTime.now(ZONE_BR) : null;
    while (!cursor.plusMinutes(totalDuration).isAfter(fim)) {
      if (agora == null || cursor.isAfter(agora)) {
        PublicBookingDtos.AvailabilitySlot slot = new PublicBookingDtos.AvailabilitySlot();
        slot.time = cursor.format(TIME_FMT);
        LocalTime slotStart = cursor;
        LocalTime slotEnd = cursor.plusMinutes(totalDuration);
        slot.available = ativos.stream().noneMatch(a -> overlaps(a, slotStart, slotEnd));
        resp.slots.add(slot);
      }
      cursor = cursor.plusMinutes(30);
    }
    return resp;
  }

  @Transactional
  public PublicBookingDtos.PublicAppointmentResponse criarAgendamentoPublico(
      String slug, PublicBookingDtos.PublicAppointmentRequest request) {
    Tenant tenant = obterTenantPorSlug(slug);

    UUID professionalUuid = UUID.fromString(request.professionalId);
    LocalDate data = DataUtil.parseDataISO(request.date);
    if (data == null) throw new IllegalArgumentException("Data invalida");
    LocalTime startTime = parseTimeOrThrow(request.startTime);
    String normalizedStartTime = startTime.format(TIME_FMT);
    List<ResolvedPublicItem> selectedItems = resolveRequestItems(tenant.getId(), request);
    long totalDuration = selectedItems.stream()
        .mapToLong(item -> (long) item.service().getDuration() * item.quantity())
        .sum();
    LocalTime endTime = startTime.plusMinutes(totalDuration);
    if (!tenantOperationalSettingsService.isBusinessOpenAt(tenant.getId(), data, startTime, endTime)) {
      throw new IllegalArgumentException("Salao fechado ou fora do horario de funcionamento informado");
    }
    // Verificar fechamentos especiais (all_day e parciais, incluindo do profissional)
    if (specialClosureService.isClosedAt(tenant.getId(), professionalUuid, data, startTime, endTime)) {
      throw new IllegalArgumentException("Salao ou profissional com fechamento especial no horario informado");
    }

    Profissional profissional = profissionalRepository
        .findByIdAndTenantIdAndIsActiveTrue(professionalUuid, tenant.getId())
        .orElse(null);
    if (profissional == null) throw new IllegalArgumentException("Profissional nao encontrado");
    for (ResolvedPublicItem item : selectedItems) {
      Servico servico = item.service();
      if (servico.getProfissionais() != null
          && !servico.getProfissionais().isEmpty()
          && servico.getProfissionais().stream().noneMatch(p -> p.getId().equals(professionalUuid))) {
        throw new IllegalArgumentException("Profissional nao atende o servico selecionado");
      }
    }

    boolean conflito = agendamentoRepository
        .findFirstByTenantIdAndProfessionalIdAndDateAndStartTimeAndStatusNot(
            tenant.getId(), professionalUuid, data, normalizedStartTime, StatusAgendamento.CANCELLED)
        .isPresent();
    if (conflito) throw new IllegalArgumentException("Horario indisponivel");

    String normalizedPhone = normalizePhone(request.customerPhone);
    if (normalizedPhone == null) throw new IllegalArgumentException("Telefone do cliente invalido");

    // O original faz clienteRepository.list("tenantId = ?1", tenant.id) (ordem nao especificada
    // pelo banco) e compara telefone normalizado sem variantes de DDI — nao usa o
    // findByTenantAndPhoneDigits (que expande variantes 55/sem-55) porque aquele existe para outro
    // fluxo. Aqui reusa-se findByTenantIdOrderByName (ordenado por nome) por nao existir no Spring
    // um equivalente exato "sem order by" — a ordem e irrelevante para a correcao (primeiro match
    // de telefone exato), so muda em qual duplicata seria escolhida se houvesse mais de um cliente
    // com o mesmo telefone, caso que ja seria uma inconsistencia de dados no original.
    Cliente cliente = clienteRepository
        .findByTenantIdOrderByName(tenant.getId())
        .stream()
        .filter(c -> Objects.equals(normalizePhone(c.getPhone()), normalizedPhone))
        .findFirst()
        .orElse(null);
    if (cliente == null) {
      cliente = new Cliente();
      cliente.setTenantId(tenant.getId());
      cliente.setName(request.customerName);
      cliente.setPhone(normalizedPhone);
      cliente.setEmail(request.customerEmail);
      cliente = clienteRepository.save(cliente);
    } else {
      cliente.setName(request.customerName);
      cliente.setPhone(normalizedPhone);
      cliente.setEmail(request.customerEmail);
      cliente = clienteRepository.save(cliente);
    }

    Agendamento agendamento = new Agendamento();
    agendamento.setTenantId(tenant.getId());
    agendamento.setClientId(cliente.getId());
    agendamento.setProfessionalId(professionalUuid);
    agendamento.setDate(data);
    agendamento.setStartTime(normalizedStartTime);
    agendamento.setEndTime(endTime.format(TIME_FMT));
    agendamento.setStatus(StatusAgendamento.PENDING);
    agendamento = agendamentoRepository.save(agendamento);
    persistItems(agendamento, selectedItems);
    notificationService.registrarCriacaoAgendamento(
        tenant.getId(), agendamento.getId(), cliente.getId(), "APPOINTMENT_CREATED");

    PublicBookingDtos.PublicAppointmentResponse resp = new PublicBookingDtos.PublicAppointmentResponse();
    resp.appointmentId = agendamento.getId().toString();
    resp.status = agendamento.getStatus().name();
    resp.message = "Agendamento criado com sucesso.";

    BigDecimal depositAmount = calcularValorSinal(selectedItems);
    if (NumericUtil.isPositive(depositAmount)) {
      if (cliente.getName() != null) cliente.setName(cliente.getName().trim());
      String cpfCnpj = request.customerCpfCnpj != null ? request.customerCpfCnpj.trim() : null;
      if (cpfCnpj == null || cpfCnpj.isBlank()) {
        throw new IllegalArgumentException(
            "Este agendamento exige sinal: informe o CPF/CNPJ do cliente para gerar o Pix.");
      }
      cliente.setCpfCnpj(cpfCnpj);
      clienteRepository.save(cliente);

      AppointmentDeposit deposit = tenantDepositPaymentService.criarCobrancaSinal(
          tenant.getId(), agendamento.getId(), cliente, depositAmount,
          "Sinal de reserva - " + tenant.getName());

      resp.depositRequired = true;
      resp.depositValue = depositAmount;
      resp.depositPixPayload = deposit.getPixPayload();
      resp.depositExpiresAt = deposit.getExpiresAt() != null ? deposit.getExpiresAt().toString() : null;
    }

    return resp;
  }

  private BigDecimal calcularValorSinal(List<ResolvedPublicItem> items) {
    BigDecimal total = BigDecimal.ZERO;
    for (ResolvedPublicItem item : items) {
      Servico servico = item.service();
      if (!servico.isRequiresDeposit() || servico.getDepositValue() == null) continue;
      BigDecimal itemTotal;
      if ("PERCENTUAL".equals(servico.getDepositType())) {
        BigDecimal base = NumericUtil.multiply(servico.getPrice(), item.quantity());
        itemTotal = NumericUtil.percentOf(base, servico.getDepositValue().doubleValue());
      } else {
        itemTotal = NumericUtil.multiply(servico.getDepositValue(), item.quantity());
      }
      total = NumericUtil.add(total, itemTotal);
    }
    return total;
  }

  @Transactional
  public PublicBookingDtos.BookingFunnelEventResponse registrarEventoFunil(
      String slug, PublicBookingDtos.BookingFunnelEventRequest request) {
    if (request == null) throw new IllegalArgumentException("Payload do evento de funil obrigatorio.");
    Tenant tenant = obterTenantPorSlug(slug);

    UUID sessionId = parseUuidOrThrow(request.sessionId, "sessionId invalido.");
    BookingFunnelStage stage = BookingFunnelStage.from(request.stage);
    Instant occurredAt = parseInstantOrNow(request.occurredAt);

    AppointmentBookingFunnelEvent event = new AppointmentBookingFunnelEvent();
    event.setId(UUID.randomUUID());
    event.setTenantId(tenant.getId());
    event.setSessionId(sessionId);
    event.setStage(stage);
    event.setOccurredAt(occurredAt);
    event.setCreatedAt(Instant.now());
    event = appointmentBookingFunnelEventRepository.save(event);

    PublicBookingDtos.BookingFunnelEventResponse response = new PublicBookingDtos.BookingFunnelEventResponse();
    response.sessionId = sessionId.toString();
    response.stage = stage.name();
    response.recordedAt = event.getCreatedAt().toString();
    return response;
  }

  /**
   * Lista datas indisponiveis (fechamentos especiais all_day do salao). Retorna apenas datas, sem
   * motivo nem professionalId — seguro para o calendario publico.
   */
  @Transactional(readOnly = true)
  public List<LocalDate> listarDatasIndisponiveis(String slug, LocalDate from, LocalDate to) {
    Tenant tenant = obterTenantPorSlug(slug);
    return specialClosureService.listarDatasIndisponiveis(tenant.getId(), from, to);
  }

  private Tenant obterTenantPorSlug(String slug) {
    return tenantRepository.findBySlug(slug)
        .orElseThrow(() -> new IllegalArgumentException("Salao nao encontrado"));
  }

  private void persistItems(Agendamento appointment, List<ResolvedPublicItem> items) {
    if (appointment == null || appointment.getId() == null || items == null || items.isEmpty()) return;
    boolean exists = agendamentoItemRepository.existsByAppointmentId(appointment.getId());
    if (exists) return;

    List<AgendamentoItem> persistedItems = new ArrayList<>();
    for (ResolvedPublicItem resolvedItem : items) {
      AgendamentoItem item = new AgendamentoItem();
      item.setTenantId(appointment.getTenantId());
      item.setAppointmentId(appointment.getId());
      item.setServiceId(resolvedItem.service().getId());
      item.setService(resolvedItem.service());
      item.setQuantity(resolvedItem.quantity());
      item.setUnitPrice(resolvedItem.service().getPrice());
      item.setGrossAmount(NumericUtil.multiply(resolvedItem.service().getPrice(), resolvedItem.quantity()));
      item.setDiscountAmount(NumericUtil.zero());
      item.setTotalPrice(item.getGrossAmount());
      item = agendamentoItemRepository.save(item);
      persistedItems.add(item);
    }
    appointment.setItems(persistedItems);
  }

  private List<Servico> resolveSelectedServices(UUID tenantId, String serviceId, String serviceIds) {
    List<UUID> ids = parseServiceIds(serviceId, serviceIds);
    if (ids.isEmpty()) return List.of();

    List<Servico> services = new ArrayList<>();
    for (UUID id : ids) {
      Servico servico = servicoRepository.findByIdAndTenantId(id, tenantId).orElse(null);
      if (servico == null || !servico.isActive()) {
        throw new IllegalArgumentException("Servico nao encontrado");
      }
      services.add(servico);
    }
    return services;
  }

  private List<ResolvedPublicItem> resolveRequestItems(UUID tenantId, PublicBookingDtos.PublicAppointmentRequest request) {
    List<PublicBookingDtos.ItemRequest> requestedItems =
        request.items != null && !request.items.isEmpty() ? request.items : fallbackSingleItem(request);

    List<ResolvedPublicItem> resolvedItems = new ArrayList<>();
    for (PublicBookingDtos.ItemRequest item : requestedItems) {
      if (item == null || item.serviceId == null || item.serviceId.isBlank()) {
        throw new IllegalArgumentException("Item de agendamento invalido");
      }
      UUID serviceId = UUID.fromString(item.serviceId);
      Servico servico = servicoRepository.findByIdAndTenantId(serviceId, tenantId)
          .filter(Servico::isActive)
          .orElse(null);
      if (servico == null) throw new IllegalArgumentException("Servico nao encontrado");
      int quantity = item.quantity <= 0 ? 1 : item.quantity;
      resolvedItems.add(new ResolvedPublicItem(servico, quantity));
    }
    return resolvedItems;
  }

  private List<PublicBookingDtos.ItemRequest> fallbackSingleItem(PublicBookingDtos.PublicAppointmentRequest request) {
    if (request.serviceId == null || request.serviceId.isBlank()) {
      throw new IllegalArgumentException("Agendamento deve informar ao menos um item");
    }
    PublicBookingDtos.ItemRequest item = new PublicBookingDtos.ItemRequest();
    item.serviceId = request.serviceId;
    item.quantity = 1;
    return List.of(item);
  }

  private List<UUID> parseServiceIds(String serviceId, String serviceIds) {
    if (serviceIds != null && !serviceIds.isBlank()) {
      return Arrays.stream(serviceIds.split(","))
          .map(String::trim)
          .filter(value -> !value.isBlank())
          .map(UUID::fromString)
          .distinct()
          .toList();
    }
    if (serviceId == null || serviceId.isBlank()) return List.of();
    return List.of(UUID.fromString(serviceId));
  }

  private LocalTime[] obterJanelaDia(UUID tenantId, LocalDate data) {
    SalonDtos.BusinessHour businessHour = tenantOperationalSettingsService.getBusinessHourForDate(tenantId, data);
    if (businessHour == null || !businessHour.enabled) {
      return null;
    }
    return new LocalTime[] {LocalTime.parse(businessHour.open), LocalTime.parse(businessHour.close)};
  }

  private boolean overlaps(Agendamento agendamento, LocalTime slotStart, LocalTime slotEnd) {
    try {
      LocalTime existingStart = LocalTime.parse(agendamento.getStartTime());
      LocalTime existingEnd = LocalTime.parse(agendamento.getEndTime());
      return existingStart.isBefore(slotEnd) && existingEnd.isAfter(slotStart);
    } catch (Exception e) {
      return false;
    }
  }

  private String normalizePhone(String phone) {
    if (phone == null) return null;
    String digits = phone.replaceAll("\\D", "");
    return digits.length() < 10 ? null : digits;
  }

  private LocalTime parseTimeOrThrow(String value) {
    try {
      return LocalTime.parse(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Horario inicial invalido");
    }
  }

  private UUID parseUuidOrThrow(String value, String message) {
    try {
      return UUID.fromString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException(message);
    }
  }

  private Instant parseInstantOrNow(String value) {
    if (value == null || value.isBlank()) return Instant.now();
    try {
      return Instant.parse(value);
    } catch (Exception e) {
      return Instant.now();
    }
  }

  private ServicoResponse toServicoResponse(Servico s) {
    ServicoResponse r = new ServicoResponse();
    r.id = s.getId().toString();
    r.tenantId = s.getTenantId().toString();
    r.name = s.getName();
    r.description = s.getDescription();
    r.duration = s.getDuration();
    r.price = s.getPrice();
    r.category = resolveCategoryName(s.getCategoryId());
    r.professionalIds = new ArrayList<>(s.getProfissionais().stream().map(Profissional::getId).toList());
    r.isActive = s.isActive();
    r.createdAt = s.getCreatedAt() != null ? s.getCreatedAt().toString() : null;
    r.requiresDeposit = s.isRequiresDeposit();
    r.depositType = s.getDepositType();
    r.depositValue = s.getDepositValue();
    return r;
  }

  private String resolveCategoryName(UUID categoryId) {
    if (categoryId == null) return null;
    return serviceCategoryRepository.findById(categoryId).map(c -> c.getName()).orElse(null);
  }

  private ProfissionalResponse toProfissionalResponse(Profissional p) {
    ProfissionalResponse r = new ProfissionalResponse();
    r.id = p.getId().toString();
    r.tenantId = p.getTenantId().toString();
    r.userId = p.getUserId() != null ? p.getUserId().toString() : null;
    r.name = p.getName();
    r.email = p.getEmail();
    r.phone = p.getPhone();
    r.avatar = p.getAvatar();
    r.commissionRate = p.getCommissionRate();
    r.isActive = p.isActive();
    r.createdAt = p.getCreatedAt() != null ? p.getCreatedAt().toString() : null;
    return r;
  }

  private record ResolvedPublicItem(Servico service, int quantity) {}
}
