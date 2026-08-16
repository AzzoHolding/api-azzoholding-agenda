package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PublicBookingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
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

/** Cobre {@code modules/publicbooking/application/ServicoPublicBooking.java}. */
class ServicoPublicBookingTest {

  private TenantRepository tenantRepository;
  private ServicoRepository servicoRepository;
  private ServiceCategoryRepository serviceCategoryRepository;
  private ProfissionalRepository profissionalRepository;
  private AgendamentoRepository agendamentoRepository;
  private AgendamentoItemRepository agendamentoItemRepository;
  private ClienteRepository clienteRepository;
  private AppointmentBookingFunnelEventRepository appointmentBookingFunnelEventRepository;
  private TenantOperationalSettingsService tenantOperationalSettingsService;
  private SpecialClosureService specialClosureService;
  private NotificationService notificationService;
  private TenantDepositPaymentService tenantDepositPaymentService;
  private ServicoPublicBooking service;

  private UUID tenantId;
  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenantRepository = mock(TenantRepository.class);
    servicoRepository = mock(ServicoRepository.class);
    serviceCategoryRepository = mock(ServiceCategoryRepository.class);
    profissionalRepository = mock(ProfissionalRepository.class);
    agendamentoRepository = mock(AgendamentoRepository.class);
    agendamentoItemRepository = mock(AgendamentoItemRepository.class);
    clienteRepository = mock(ClienteRepository.class);
    appointmentBookingFunnelEventRepository = mock(AppointmentBookingFunnelEventRepository.class);
    tenantOperationalSettingsService = mock(TenantOperationalSettingsService.class);
    specialClosureService = mock(SpecialClosureService.class);
    notificationService = mock(NotificationService.class);
    tenantDepositPaymentService = mock(TenantDepositPaymentService.class);

    service = new ServicoPublicBooking(
        tenantRepository, servicoRepository, serviceCategoryRepository, profissionalRepository,
        agendamentoRepository, agendamentoItemRepository, clienteRepository,
        appointmentBookingFunnelEventRepository, tenantOperationalSettingsService,
        specialClosureService, notificationService, tenantDepositPaymentService);

    tenantId = UUID.randomUUID();
    tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setName("Salao Teste");
    when(tenantRepository.findBySlug("salao-teste")).thenReturn(Optional.of(tenant));
  }

  private Profissional profissionalAtivo(UUID id) {
    Profissional p = new Profissional();
    p.setId(id);
    p.setTenantId(tenantId);
    p.setName("Fulano");
    p.setActive(true);
    p.setCommissionRate(BigDecimal.ZERO);
    return p;
  }

  private Servico servicoAtivo(UUID id, int duration, BigDecimal price) {
    Servico s = new Servico();
    s.setId(id);
    s.setTenantId(tenantId);
    s.setName("Corte");
    s.setDuration(duration);
    s.setPrice(price);
    s.setActive(true);
    return s;
  }

  // ---- obterTenantPorSlug (via qualquer metodo publico) ----

  @Test
  void lancaExcecaoQuandoSalaoNaoEncontrado() {
    when(tenantRepository.findBySlug("inexistente")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listarServicosAtivos("inexistente"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Salao nao encontrado");
  }

  // ---- listarServicosAtivos ----

  @Test
  void listarServicosAtivosFiltraServicosSemProfissionalVinculado() {
    Servico comProfissional = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    comProfissional.setProfissionais(Set.of(profissionalAtivo(UUID.randomUUID())));
    Servico semProfissional = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    when(servicoRepository.findByTenantId(tenantId)).thenReturn(List.of(comProfissional, semProfissional));

    List<br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse> result =
        service.listarServicosAtivos("salao-teste");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id).isEqualTo(comProfissional.getId().toString());
  }

  // ---- listarProfissionaisAtivos ----

  @Test
  void listarProfissionaisAtivosSemServicoRetornaTodosAtivos() {
    Profissional p1 = profissionalAtivo(UUID.randomUUID());
    when(profissionalRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(p1));

    List<br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse> result =
        service.listarProfissionaisAtivos("salao-teste");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id).isEqualTo(p1.getId().toString());
  }

  @Test
  void listarProfissionaisAtivosFiltraPorServicoSelecionado() {
    Profissional p1 = profissionalAtivo(UUID.randomUUID());
    Profissional p2 = profissionalAtivo(UUID.randomUUID());
    when(profissionalRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(p1, p2));

    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 30, BigDecimal.TEN);
    servico.setProfissionais(Set.of(p1));
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));

    List<br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse> result =
        service.listarProfissionaisAtivos("salao-teste", serviceId.toString());

    assertThat(result).extracting(r -> r.id).containsExactly(p1.getId().toString());
  }

  // ---- obterDisponibilidade ----

  @Test
  void obterDisponibilidadeLancaQuandoDataInvalida() {
    assertThatThrownBy(() -> service.obterDisponibilidade("salao-teste", "data-invalida", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Data invalida");
  }

  @Test
  void obterDisponibilidadeLancaQuandoServicoNaoInformado() {
    assertThatThrownBy(() -> service.obterDisponibilidade("salao-teste", "2026-01-01", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Servico nao encontrado");
  }

  @Test
  void obterDisponibilidadeRetornaVazioQuandoFechado() {
    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 30, BigDecimal.TEN);
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));
    when(agendamentoRepository.findByTenantIdAndDate(eq(tenantId), any())).thenReturn(List.of());
    when(specialClosureService.isClosedAt(eq(tenantId), eq(null), any(), eq(null), eq(null))).thenReturn(true);

    PublicBookingDtos.AvailabilityResponse response =
        service.obterDisponibilidade("salao-teste", "2026-01-01", serviceId.toString(), null, null);

    assertThat(response.slots).isEmpty();
  }

  @Test
  void obterDisponibilidadeGeraSlotsRespeitandoJanelaEConflitos() {
    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 60, BigDecimal.TEN);
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));

    LocalDate futureDate = LocalDate.now().plusDays(10);
    Agendamento existente = new Agendamento();
    existente.setStartTime("09:00");
    existente.setEndTime("10:00");
    existente.setStatus(StatusAgendamento.CONFIRMED);
    when(agendamentoRepository.findByTenantIdAndDate(tenantId, futureDate)).thenReturn(List.of(existente));
    when(specialClosureService.isClosedAt(eq(tenantId), eq(null), eq(futureDate), eq(null), eq(null)))
        .thenReturn(false);

    SalonDtos.BusinessHour businessHour = new SalonDtos.BusinessHour();
    businessHour.enabled = true;
    businessHour.open = "09:00";
    businessHour.close = "11:00";
    when(tenantOperationalSettingsService.getBusinessHourForDate(tenantId, futureDate)).thenReturn(businessHour);

    PublicBookingDtos.AvailabilityResponse response =
        service.obterDisponibilidade("salao-teste", futureDate.toString(), serviceId.toString(), null, null);

    assertThat(response.slots).isNotEmpty();
    PublicBookingDtos.AvailabilitySlot slotConflitante =
        response.slots.stream().filter(s -> s.time.equals("09:00")).findFirst().orElseThrow();
    assertThat(slotConflitante.available).isFalse();
    PublicBookingDtos.AvailabilitySlot slotLivre =
        response.slots.stream().filter(s -> s.time.equals("10:00")).findFirst().orElseThrow();
    assertThat(slotLivre.available).isTrue();
  }

  // ---- criarAgendamentoPublico ----

  private PublicBookingDtos.PublicAppointmentRequest requestValido(UUID professionalId, UUID serviceId) {
    PublicBookingDtos.PublicAppointmentRequest request = new PublicBookingDtos.PublicAppointmentRequest();
    request.customerName = "Cliente Teste";
    request.customerPhone = "11999998888";
    request.professionalId = professionalId.toString();
    request.serviceId = serviceId.toString();
    request.date = LocalDate.now().plusDays(5).toString();
    request.startTime = "10:00";
    return request;
  }

  @Test
  void criarAgendamentoPublicoLancaQuandoForaDoHorarioDeFuncionamento() {
    UUID professionalId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 30, BigDecimal.TEN);
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));
    when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any())).thenReturn(false);

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, serviceId);

    assertThatThrownBy(() -> service.criarAgendamentoPublico("salao-teste", request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("horario de funcionamento");
  }

  @Test
  void criarAgendamentoPublicoLancaQuandoHorarioIndisponivel() {
    UUID professionalId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 30, BigDecimal.TEN);
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));
    when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any())).thenReturn(true);
    when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any())).thenReturn(false);
    Profissional profissional = profissionalAtivo(professionalId);
    when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
        .thenReturn(Optional.of(profissional));
    when(agendamentoRepository.findFirstByTenantIdAndProfessionalIdAndDateAndStartTimeAndStatusNot(
            eq(tenantId), eq(professionalId), any(), eq("10:00"), eq(StatusAgendamento.CANCELLED)))
        .thenReturn(Optional.of(new Agendamento()));

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, serviceId);

    assertThatThrownBy(() -> service.criarAgendamentoPublico("salao-teste", request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Horario indisponivel");
  }

  @Test
  void criarAgendamentoPublicoCriaClienteNovoQuandoTelefoneNaoExiste() {
    UUID professionalId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 30, BigDecimal.TEN);
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));
    when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any())).thenReturn(true);
    when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any())).thenReturn(false);
    Profissional profissional = profissionalAtivo(professionalId);
    when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
        .thenReturn(Optional.of(profissional));
    when(agendamentoRepository.findFirstByTenantIdAndProfessionalIdAndDateAndStartTimeAndStatusNot(
            any(), any(), any(), anyString(), any())).thenReturn(Optional.empty());
    when(clienteRepository.findByTenantIdOrderByName(tenantId)).thenReturn(List.of());
    when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> {
      Cliente c = invocation.getArgument(0);
      if (c.getId() == null) c.setId(UUID.randomUUID());
      return c;
    });
    when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> {
      Agendamento a = invocation.getArgument(0);
      if (a.getId() == null) a.setId(UUID.randomUUID());
      return a;
    });

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, serviceId);

    PublicBookingDtos.PublicAppointmentResponse response = service.criarAgendamentoPublico("salao-teste", request);

    assertThat(response.appointmentId).isNotBlank();
    assertThat(response.status).isEqualTo("PENDING");
    assertThat(response.depositRequired).isFalse();
    verify(clienteRepository).save(any(Cliente.class));
    verify(notificationService).registrarCriacaoAgendamento(eq(tenantId), any(), any(), eq("APPOINTMENT_CREATED"));
  }

  @Test
  void criarAgendamentoPublicoLancaQuandoTelefoneInvalido() {
    UUID professionalId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 30, BigDecimal.TEN);
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));
    when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any())).thenReturn(true);
    when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any())).thenReturn(false);
    Profissional profissional = profissionalAtivo(professionalId);
    when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
        .thenReturn(Optional.of(profissional));
    when(agendamentoRepository.findFirstByTenantIdAndProfessionalIdAndDateAndStartTimeAndStatusNot(
            any(), any(), any(), anyString(), any())).thenReturn(Optional.empty());

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, serviceId);
    request.customerPhone = "123";

    assertThatThrownBy(() -> service.criarAgendamentoPublico("salao-teste", request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Telefone");
  }

  @Test
  void criarAgendamentoPublicoExigeCpfCnpjQuandoSinalObrigatorio() {
    UUID professionalId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 30, BigDecimal.TEN);
    servico.setRequiresDeposit(true);
    servico.setDepositType("FIXO");
    servico.setDepositValue(BigDecimal.valueOf(20));
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));
    when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any())).thenReturn(true);
    when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any())).thenReturn(false);
    Profissional profissional = profissionalAtivo(professionalId);
    when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
        .thenReturn(Optional.of(profissional));
    when(agendamentoRepository.findFirstByTenantIdAndProfessionalIdAndDateAndStartTimeAndStatusNot(
            any(), any(), any(), anyString(), any())).thenReturn(Optional.empty());
    when(clienteRepository.findByTenantIdOrderByName(tenantId)).thenReturn(List.of());
    when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> {
      Cliente c = invocation.getArgument(0);
      if (c.getId() == null) c.setId(UUID.randomUUID());
      return c;
    });
    when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> {
      Agendamento a = invocation.getArgument(0);
      if (a.getId() == null) a.setId(UUID.randomUUID());
      return a;
    });

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, serviceId);
    request.customerCpfCnpj = null;

    assertThatThrownBy(() -> service.criarAgendamentoPublico("salao-teste", request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sinal");
  }

  @Test
  void criarAgendamentoPublicoGeraDepositoPixQuandoSinalObrigatorioComCpf() {
    UUID professionalId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 30, BigDecimal.valueOf(100));
    servico.setRequiresDeposit(true);
    servico.setDepositType("FIXO");
    servico.setDepositValue(BigDecimal.valueOf(20));
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));
    when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any())).thenReturn(true);
    when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any())).thenReturn(false);
    Profissional profissional = profissionalAtivo(professionalId);
    when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
        .thenReturn(Optional.of(profissional));
    when(agendamentoRepository.findFirstByTenantIdAndProfessionalIdAndDateAndStartTimeAndStatusNot(
            any(), any(), any(), anyString(), any())).thenReturn(Optional.empty());
    when(clienteRepository.findByTenantIdOrderByName(tenantId)).thenReturn(List.of());
    when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> {
      Cliente c = invocation.getArgument(0);
      if (c.getId() == null) c.setId(UUID.randomUUID());
      return c;
    });
    when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> {
      Agendamento a = invocation.getArgument(0);
      if (a.getId() == null) a.setId(UUID.randomUUID());
      return a;
    });

    AppointmentDeposit deposit = new AppointmentDeposit();
    deposit.setPixPayload("pix-payload");
    deposit.setExpiresAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(tenantDepositPaymentService.criarCobrancaSinal(eq(tenantId), any(), any(), any(), anyString()))
        .thenReturn(deposit);

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, serviceId);
    request.customerCpfCnpj = "12345678900";

    PublicBookingDtos.PublicAppointmentResponse response = service.criarAgendamentoPublico("salao-teste", request);

    assertThat(response.depositRequired).isTrue();
    assertThat(response.depositValue).isEqualByComparingTo(BigDecimal.valueOf(20));
    assertThat(response.depositPixPayload).isEqualTo("pix-payload");
    assertThat(response.depositExpiresAt).isEqualTo("2026-01-01T00:00:00Z");
  }

  // ---- registrarEventoFunil ----

  @Test
  void registrarEventoFunilPersisteEventoValido() {
    PublicBookingDtos.BookingFunnelEventRequest request = new PublicBookingDtos.BookingFunnelEventRequest();
    request.sessionId = UUID.randomUUID().toString();
    request.stage = "SERVICE_SELECTION";

    when(appointmentBookingFunnelEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    PublicBookingDtos.BookingFunnelEventResponse response = service.registrarEventoFunil("salao-teste", request);

    assertThat(response.sessionId).isEqualTo(request.sessionId);
    assertThat(response.stage).isEqualTo("SERVICE_SELECTION");
    assertThat(response.recordedAt).isNotBlank();
  }

  @Test
  void registrarEventoFunilLancaQuandoStageInvalido() {
    PublicBookingDtos.BookingFunnelEventRequest request = new PublicBookingDtos.BookingFunnelEventRequest();
    request.sessionId = UUID.randomUUID().toString();
    request.stage = "ETAPA_INEXISTENTE";

    assertThatThrownBy(() -> service.registrarEventoFunil("salao-teste", request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void registrarEventoFunilLancaQuandoPayloadNulo() {
    assertThatThrownBy(() -> service.registrarEventoFunil("salao-teste", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- listarDatasIndisponiveis ----

  @Test
  void listarDatasIndisponiveisDelegaAoSpecialClosureService() {
    LocalDate from = LocalDate.now();
    LocalDate to = from.plusDays(30);
    List<LocalDate> esperado = List.of(from.plusDays(5));
    when(specialClosureService.listarDatasIndisponiveis(tenantId, from, to)).thenReturn(esperado);

    List<LocalDate> result = service.listarDatasIndisponiveis("salao-teste", from, to);

    assertThat(result).isEqualTo(esperado);
  }
}
