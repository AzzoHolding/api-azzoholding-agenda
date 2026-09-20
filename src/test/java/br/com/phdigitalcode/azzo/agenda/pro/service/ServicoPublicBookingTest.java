package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PublicBookingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
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

  private br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository
      profissionalWorkingHourRepository;
  private br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoQueryRepository
      agendamentoQueryRepository;

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
    profissionalWorkingHourRepository =
        mock(br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository.class);
    agendamentoQueryRepository =
        mock(br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoQueryRepository.class);

    service = new ServicoPublicBooking(
        tenantRepository, servicoRepository, serviceCategoryRepository, profissionalRepository,
        agendamentoRepository, agendamentoItemRepository, clienteRepository,
        appointmentBookingFunnelEventRepository, tenantOperationalSettingsService,
        specialClosureService, notificationService, tenantDepositPaymentService,
        profissionalWorkingHourRepository, agendamentoQueryRepository);

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

  // ---- quem nao aceita agendamento fica fora do link publico ----

  @Test
  void listarProfissionaisAtivosDeixaDeForaQuemNaoRecebeAgendamento() {
    Profissional ana = profissionalAtivo(UUID.randomUUID());
    Profissional recepcao = profissionalAtivo(UUID.randomUUID());
    recepcao.setAcceptsAppointments(false);
    when(profissionalRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(ana, recepcao));

    assertThat(service.listarProfissionaisAtivos("salao-teste"))
        .extracting(p -> p.id)
        .containsExactly(ana.getId().toString());
  }

  /**
   * A rota nao pede login: quem tem o link ve a resposta inteira. Ate 2026-09-20 ela devolvia
   * e-mail, telefone, taxa de comissao e ids internos do profissional.
   */
  @Test
  void oLinkPublicoNaoExpoeDadoPessoalNemComercialDoProfissional() {
    Profissional ana = profissionalAtivo(UUID.randomUUID());
    ana.setName("Ana");
    ana.setEmail("ana@salao.test");
    ana.setPhone("11999990000");
    ana.setCommissionRate(new BigDecimal("40.00"));
    ana.setAvatar("https://exemplo/ana.png");
    when(profissionalRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(ana));

    var publicos = service.listarProfissionaisAtivos("salao-teste");

    assertThat(publicos).hasSize(1);
    var p = publicos.get(0);
    assertThat(p.name).isEqualTo("Ana");
    assertThat(p.avatar).isEqualTo("https://exemplo/ana.png");
    // O que a resposta publica NAO pode ter: so estes quatro campos existem no DTO.
    assertThat(p.getClass().getFields())
        .extracting(java.lang.reflect.Field::getName)
        .containsExactlyInAnyOrder("id", "name", "avatar", "specialties");
  }

  @Test
  void oLinkPublicoNaoExpoeOIdInternoDoSalaoNoServico() {
    Servico corte = servicoAtivo(UUID.randomUUID(), 30, new BigDecimal("80.00"));
    Profissional ana = profissionalAtivo(UUID.randomUUID());
    corte.setProfissionais(Set.of(ana));
    when(servicoRepository.findByTenantId(tenantId)).thenReturn(List.of(corte));
    when(profissionalRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(ana));

    var publicos = service.listarServicosAtivos("salao-teste");

    assertThat(publicos).hasSize(1);
    assertThat(publicos.get(0).getClass().getFields())
        .extracting(java.lang.reflect.Field::getName)
        .containsExactlyInAnyOrder(
            "id", "name", "description", "duration", "price", "category",
            "requiresDeposit", "depositType", "depositValue");
  }

  @Test
  void listarServicosAtivosDeixaDeForaServicoSoDeQuemNaoRecebeAgendamento() {
    Profissional recepcao = profissionalAtivo(UUID.randomUUID());
    recepcao.setAcceptsAppointments(false);
    Servico soDaRecepcao = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    soDaRecepcao.setProfissionais(Set.of(recepcao));
    when(servicoRepository.findByTenantId(tenantId)).thenReturn(List.of(soDaRecepcao));

    assertThat(service.listarServicosAtivos("salao-teste")).isEmpty();
  }

  // ---- listarServicosAtivos ----

  @Test
  void listarServicosAtivosFiltraServicosSemProfissionalVinculado() {
    Servico comProfissional = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    comProfissional.setProfissionais(Set.of(profissionalAtivo(UUID.randomUUID())));
    Servico semProfissional = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    when(servicoRepository.findByTenantId(tenantId)).thenReturn(List.of(comProfissional, semProfissional));

    var result =
        service.listarServicosAtivos("salao-teste");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id).isEqualTo(comProfissional.getId().toString());
  }

  // ---- listarProfissionaisAtivos ----

  @Test
  void listarProfissionaisAtivosSemServicoRetornaTodosAtivos() {
    Profissional p1 = profissionalAtivo(UUID.randomUUID());
    when(profissionalRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(p1));

    var result =
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

    var result =
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
    // Ja existe 09:45-10:15: o 10:00 pedido SOBREPOE, mesmo sem comecar no mesmo minuto.
    Agendamento existente = new Agendamento();
    existente.setStatus(StatusAgendamento.CONFIRMED);
    existente.setStartTime("09:45");
    existente.setEndTime("10:15");
    when(agendamentoRepository.findByTenantIdAndDateAndProfessionalId(eq(tenantId), any(), eq(professionalId)))
        .thenReturn(List.of(existente));

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

  // ─── Analise de 2026-09-16: A3 (sobreposicao, passado, jornada) e A4 (cadastro) ───

  /** Stubs do caminho feliz ate a checagem de conflito. */
  private Profissional stubsAteConflito(UUID professionalId, Servico servico) {
    when(servicoRepository.findByIdAndTenantId(servico.getId(), tenantId)).thenReturn(Optional.of(servico));
    when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any())).thenReturn(true);
    when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any())).thenReturn(false);
    Profissional profissional = profissionalAtivo(professionalId);
    when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
        .thenReturn(Optional.of(profissional));
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
    return profissional;
  }

  @Test
  void agendamentoPublicoNoPassadoEhRecusado() {
    UUID professionalId = UUID.randomUUID();
    Servico servico = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    stubsAteConflito(professionalId, servico);

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, servico.getId());
    request.date = LocalDate.now().minusDays(1).toString();

    assertThatThrownBy(() -> service.criarAgendamentoPublico("salao-teste", request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ja passou");
    verify(agendamentoRepository, org.mockito.Mockito.never()).save(any(Agendamento.class));
  }

  /** Terca de folga: a jornada so tem segunda. O link aceitava marcar no dia de folga. */
  @Test
  void agendamentoPublicoForaDaJornadaDoProfissionalEhRecusado() {
    UUID professionalId = UUID.randomUUID();
    Servico servico = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    stubsAteConflito(professionalId, servico);
    LocalDate diaDoPedido = LocalDate.now().plusDays(5);
    br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour folga =
        new br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour();
    folga.setDayOfWeek(diaDoPedido.getDayOfWeek().getValue());
    folga.setWorking(false);
    when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
        .thenReturn(List.of(folga));

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, servico.getId());

    assertThatThrownBy(() -> service.criarAgendamentoPublico("salao-teste", request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nao atende neste horario");
  }

  /** Quem esta no link nao tem login: nao pode trocar nome, e-mail e CPF de um cliente existente. */
  @Test
  void clienteExistenteNaoEhReescritoPeloLink() {
    UUID professionalId = UUID.randomUUID();
    Servico servico = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    stubsAteConflito(professionalId, servico);
    Cliente existente = new Cliente();
    existente.setId(UUID.randomUUID());
    existente.setTenantId(tenantId);
    existente.setName("Marina Alves");
    existente.setPhone("11999998888");
    existente.setEmail("marina@cliente.test");
    existente.setCpfCnpj("52998224725");
    when(clienteRepository.findByTenantIdOrderByName(tenantId)).thenReturn(List.of(existente));

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, servico.getId());
    request.customerName = "Outra Pessoa";
    request.customerEmail = "golpe@exemplo.test";
    request.customerCpfCnpj = "11111111111";

    service.criarAgendamentoPublico("salao-teste", request);

    assertThat(existente.getName()).isEqualTo("Marina Alves");
    assertThat(existente.getEmail()).isEqualTo("marina@cliente.test");
    assertThat(existente.getCpfCnpj()).isEqualTo("52998224725");
    verify(clienteRepository, org.mockito.Mockito.never()).save(existente);
  }

  /** O que estava em branco no cadastro pode ser completado. */
  @Test
  void clienteExistenteGanhaSoOQueFaltava() {
    UUID professionalId = UUID.randomUUID();
    Servico servico = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    stubsAteConflito(professionalId, servico);
    Cliente existente = new Cliente();
    existente.setId(UUID.randomUUID());
    existente.setTenantId(tenantId);
    existente.setName("Marina Alves");
    existente.setPhone("11999998888");
    when(clienteRepository.findByTenantIdOrderByName(tenantId)).thenReturn(List.of(existente));

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, servico.getId());
    request.customerName = "Outra Pessoa";
    request.customerEmail = "marina@cliente.test";

    service.criarAgendamentoPublico("salao-teste", request);

    assertThat(existente.getName()).isEqualTo("Marina Alves");
    assertThat(existente.getEmail()).isEqualTo("marina@cliente.test");
  }

  /** Duas pessoas no link ao mesmo tempo: o lock serializa antes de conferir o conflito. */
  @Test
  void criacaoPublicaTravaOProfissionalNoDia() {
    UUID professionalId = UUID.randomUUID();
    Servico servico = servicoAtivo(UUID.randomUUID(), 30, BigDecimal.TEN);
    stubsAteConflito(professionalId, servico);
    when(clienteRepository.findByTenantIdOrderByName(tenantId)).thenReturn(List.of());

    PublicBookingDtos.PublicAppointmentRequest request = requestValido(professionalId, servico.getId());
    service.criarAgendamentoPublico("salao-teste", request);

    verify(agendamentoQueryRepository)
        .lockProfessionalDateForWrite(eq(tenantId), eq(professionalId), eq(LocalDate.parse(request.date)));
  }

  // ─── A colecao de itens do agendamento (2026-09-17, jornada de usuario) ────

  /**
   * `Agendamento.items` e `orphanRemoval = true`: o Hibernate recusa no flush uma colecao de
   * orfaos que deixou de ser a MESMA instancia. Trocar a lista quebrava todo agendamento feito
   * pelo link publico — o cliente lia "Ocorreu um erro inesperado" e nada era gravado.
   */
  @Test
  void criarAgendamentoPublicoPreencheOsItensSemTrocarAColecao() {
    UUID professionalId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    Servico servico = servicoAtivo(serviceId, 30, BigDecimal.TEN);
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(servico));
    when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any())).thenReturn(true);
    when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any())).thenReturn(false);
    when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
        .thenReturn(Optional.of(profissionalAtivo(professionalId)));
    when(agendamentoRepository.findFirstByTenantIdAndProfessionalIdAndDateAndStartTimeAndStatusNot(
            any(), any(), any(), anyString(), any())).thenReturn(Optional.empty());
    when(clienteRepository.findByTenantIdOrderByName(tenantId)).thenReturn(List.of());
    when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> {
      Cliente c = invocation.getArgument(0);
      if (c.getId() == null) c.setId(UUID.randomUUID());
      return c;
    });
    List<Object> colecoesDosItens = new ArrayList<>();
    when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> {
      Agendamento a = invocation.getArgument(0);
      if (a.getId() == null) a.setId(UUID.randomUUID());
      colecoesDosItens.add(a.getItems());
      return a;
    });
    when(agendamentoItemRepository.existsByAppointmentId(any())).thenReturn(false);
    when(agendamentoItemRepository.save(any(AgendamentoItem.class))).thenAnswer(invocation -> {
      AgendamentoItem item = invocation.getArgument(0);
      if (item.getId() == null) item.setId(UUID.randomUUID());
      return item;
    });

    PublicBookingDtos.PublicAppointmentResponse response =
        service.criarAgendamentoPublico("salao-teste", requestValido(professionalId, serviceId));

    assertThat(response.appointmentId).isNotBlank();
    ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
    verify(agendamentoRepository, atLeastOnce()).save(captor.capture());
    Agendamento salvo = captor.getValue();
    assertThat(salvo.getItems()).hasSize(1);
    assertThat(salvo.getItems().get(0).getServiceId()).isEqualTo(serviceId);
    // A instancia da colecao e a mesma do inicio ao fim (o que o orphanRemoval exige).
    assertThat(colecoesDosItens).isNotEmpty();
    assertThat(colecoesDosItens).allMatch(c -> c == salvo.getItems());
  }
}
