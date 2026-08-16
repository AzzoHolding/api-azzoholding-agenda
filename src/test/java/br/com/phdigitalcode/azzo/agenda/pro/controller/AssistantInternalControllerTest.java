package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.phdigitalcode.azzo.agenda.pro.controller.AssistantInternalController.ClienteCreateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.controller.AssistantInternalController.ClienteDto;
import br.com.phdigitalcode.azzo.agenda.pro.controller.AssistantInternalController.NotificacaoCreateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.controller.AssistantInternalController.StatusUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.controller.AssistantInternalController.TenantInfoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.controller.AssistantInternalController.WhatsAppPermissoesResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.TimeSlotResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
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

/**
 * Espelha {@code modules/assistantintegration/api/internal/AssistantInternalResource.java}:
 * contrato de rota/verbo e delegacao pura para os services/repositorios ja portados.
 */
class AssistantInternalControllerTest {

  private final UUID tenantId = UUID.randomUUID();

  private TenantRepository tenantRepository;
  private ServicoService servicoService;
  private ProfissionalService profissionalService;
  private ServicoAgendamentos servicoAgendamentos;
  private AppointmentService appointmentService;
  private NotificationPublisher notificationPublisher;
  private ClienteRepository clienteRepository;
  private ClienteStatsRepository clienteStatsRepository;
  private AgendamentoRepository agendamentoRepository;
  private ServicoRepository servicoRepository;
  private ProfissionalRepository profissionalRepository;
  private TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private ContextoTenant contextoTenant;
  private TenantOperationalSettingsService tenantOperationalSettingsService;
  private AssistantInternalController controller;

  @BeforeEach
  void setUp() {
    tenantRepository = mock(TenantRepository.class);
    servicoService = mock(ServicoService.class);
    profissionalService = mock(ProfissionalService.class);
    servicoAgendamentos = mock(ServicoAgendamentos.class);
    appointmentService = mock(AppointmentService.class);
    notificationPublisher = mock(NotificationPublisher.class);
    clienteRepository = mock(ClienteRepository.class);
    clienteStatsRepository = mock(ClienteStatsRepository.class);
    agendamentoRepository = mock(AgendamentoRepository.class);
    servicoRepository = mock(ServicoRepository.class);
    profissionalRepository = mock(ProfissionalRepository.class);
    tenantWhatsAppConfigRepository = mock(TenantWhatsAppConfigRepository.class);
    contextoTenant = mock(ContextoTenant.class);
    tenantOperationalSettingsService = mock(TenantOperationalSettingsService.class);
    controller = new AssistantInternalController(
        tenantRepository,
        servicoService,
        profissionalService,
        servicoAgendamentos,
        appointmentService,
        notificationPublisher,
        clienteRepository,
        clienteStatsRepository,
        agendamentoRepository,
        servicoRepository,
        profissionalRepository,
        tenantWhatsAppConfigRepository,
        contextoTenant,
        tenantOperationalSettingsService);
  }

  // ─── contrato de classe ─────────────────────────────────────────────────

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(AssistantInternalController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/internal/assistant");
  }

  @Test
  void cadaRotaMantemVerboECaminhoDoOriginal() throws NoSuchMethodException {
    assertThat(metodo("obterInfoTenant", String.class).getAnnotation(GetMapping.class).value())
        .containsExactly("/tenant/info");
    assertThat(metodo("obterHorariosFuncionamento", String.class).getAnnotation(GetMapping.class).value())
        .containsExactly("/tenant/business-hours");
    assertThat(metodo("listarServicos", String.class).getAnnotation(GetMapping.class).value())
        .containsExactly("/services");
    assertThat(metodo("listarProfissionais", String.class, String.class)
            .getAnnotation(GetMapping.class).value())
        .containsExactly("/professionals");
    assertThat(metodo(
                "buscarSlotsDisponiveis",
                String.class,
                String.class,
                String.class,
                String.class,
                int.class,
                int.class)
            .getAnnotation(GetMapping.class).value())
        .containsExactly("/available-slots");
    assertThat(metodo("criarAgendamento", String.class, AgendamentoRequest.class)
            .getAnnotation(PostMapping.class).value())
        .containsExactly("/appointments");
    assertThat(metodo("atualizarStatusAgendamento", String.class, String.class, StatusUpdateRequest.class)
            .getAnnotation(PatchMapping.class).value())
        .containsExactly("/appointments/{id}/status");
    assertThat(metodo("listarAgendamentosCliente", String.class, String.class, int.class)
            .getAnnotation(GetMapping.class).value())
        .containsExactly("/clients/{clientId}/appointments");
    assertThat(metodo("buscarClientePorIdentificador", String.class, String.class)
            .getAnnotation(GetMapping.class).value())
        .containsExactly("/clients/search");
    assertThat(metodo("criarCliente", String.class, ClienteCreateRequest.class)
            .getAnnotation(PostMapping.class).value())
        .containsExactly("/clients");
    assertThat(metodo("criarNotificacao", String.class, NotificacaoCreateRequest.class)
            .getAnnotation(PostMapping.class).value())
        .containsExactly("/notifications");
    assertThat(metodo("obterPermissoesWhatsApp", String.class).getAnnotation(GetMapping.class).value())
        .containsExactly("/tenant/whatsapp-permissions");
  }

  private Method metodo(String nome, Class<?>... parametros) throws NoSuchMethodException {
    return AssistantInternalController.class.getDeclaredMethod(nome, parametros);
  }

  // ─── tenant info / business hours ──────────────────────────────────────

  @Test
  void obterInfoTenantRetorna404QuandoTenantNaoExiste() {
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    ResponseEntity<TenantInfoResponse> result = controller.obterInfoTenant(tenantId.toString());

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void obterInfoTenantRetornaNomeEDescricao() {
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setName("Salao Teste");
    tenant.setDescription("Descricao");
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

    ResponseEntity<TenantInfoResponse> result = controller.obterInfoTenant(tenantId.toString());

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().name).isEqualTo("Salao Teste");
    assertThat(result.getBody().description).isEqualTo("Descricao");
  }

  @Test
  void obterHorariosFuncionamentoDelegaParaTenantOperationalSettingsService() {
    List<SalonDtos.BusinessHour> hours = List.of(new SalonDtos.BusinessHour());
    when(tenantOperationalSettingsService.getBusinessHours(tenantId)).thenReturn(hours);

    assertThat(controller.obterHorariosFuncionamento(tenantId.toString())).isSameAs(hours);
  }

  // ─── servicos / profissionais ───────────────────────────────────────────

  @Test
  void listarServicosFiltraApenasAtivosEDefineContextoTenant() {
    ServicoResponse ativo = new ServicoResponse();
    ativo.isActive = true;
    ServicoResponse inativo = new ServicoResponse();
    inativo.isActive = false;
    when(servicoService.listar()).thenReturn(List.of(ativo, inativo));

    List<ServicoResponse> result = controller.listarServicos(tenantId.toString());

    assertThat(result).containsExactly(ativo);
    verify(contextoTenant).definirTenantId(tenantId);
    verify(contextoTenant).limparTenantIdOverride();
  }

  @Test
  void listarServicosLimpaContextoMesmoEmFalha() {
    when(servicoService.listar()).thenThrow(new IllegalStateException("boom"));

    try {
      controller.listarServicos(tenantId.toString());
    } catch (IllegalStateException ignored) {
      // esperado
    }

    verify(contextoTenant).limparTenantIdOverride();
  }

  @Test
  void listarProfissionaisDelegaParaProfissionalServiceComServiceId() {
    List<ProfissionalResponse> response = List.of(new ProfissionalResponse());
    when(profissionalService.listar("srv-1")).thenReturn(response);

    assertThat(controller.listarProfissionais(tenantId.toString(), "srv-1")).isSameAs(response);
    verify(contextoTenant).definirTenantId(tenantId);
    verify(contextoTenant).limparTenantIdOverride();
  }

  // ─── slots disponiveis ────────────────────────────────────────────────

  @Test
  void buscarSlotsDisponiveisUsaDuracaoFallbackQuandoSemServiceIds() {
    UUID professionalId = UUID.randomUUID();
    List<TimeSlotResponse> slots = List.of(new TimeSlotResponse());
    when(appointmentService.findAvailableSlots(tenantId, professionalId, LocalDate.of(2026, 1, 10), 45, 5))
        .thenReturn(slots);

    List<TimeSlotResponse> result = controller.buscarSlotsDisponiveis(
        tenantId.toString(), professionalId.toString(), "2026-01-10", null, 45, 5);

    assertThat(result).isSameAs(slots);
    verify(contextoTenant).limparTenantIdOverride();
  }

  @Test
  void buscarSlotsDisponiveisSomaDuracaoDosServicosInformados() {
    UUID professionalId = UUID.randomUUID();
    UUID serviceId1 = UUID.randomUUID();
    UUID serviceId2 = UUID.randomUUID();
    Servico s1 = new Servico();
    s1.setTenantId(tenantId);
    s1.setDuration(20);
    Servico s2 = new Servico();
    s2.setTenantId(tenantId);
    s2.setDuration(25);
    when(servicoRepository.findAllById(List.of(serviceId1, serviceId2))).thenReturn(List.of(s1, s2));
    List<TimeSlotResponse> slots = List.of(new TimeSlotResponse());
    when(appointmentService.findAvailableSlots(
            eq(tenantId), eq(professionalId), eq(LocalDate.of(2026, 1, 10)), eq(45), eq(0)))
        .thenReturn(slots);

    List<TimeSlotResponse> result = controller.buscarSlotsDisponiveis(
        tenantId.toString(),
        professionalId.toString(),
        "2026-01-10",
        serviceId1 + "," + serviceId2,
        30,
        0);

    assertThat(result).isSameAs(slots);
  }

  // ─── agendamentos ────────────────────────────────────────────────────

  @Test
  void criarAgendamentoDelegaParaServicoAgendamentos() {
    AgendamentoRequest request = new AgendamentoRequest();
    AgendamentoResponse response = new AgendamentoResponse();
    when(servicoAgendamentos.criar(request)).thenReturn(response);

    assertThat(controller.criarAgendamento(tenantId.toString(), request)).isSameAs(response);
    verify(contextoTenant).definirTenantId(tenantId);
    verify(contextoTenant).limparTenantIdOverride();
  }

  @Test
  void atualizarStatusAgendamentoDelegaERetorna204() {
    UUID appointmentId = UUID.randomUUID();
    StatusUpdateRequest request = new StatusUpdateRequest();
    request.status = "CONFIRMED";

    ResponseEntity<Void> result =
        controller.atualizarStatusAgendamento(appointmentId.toString(), tenantId.toString(), request);

    verify(servicoAgendamentos).atualizarStatus(appointmentId, "CONFIRMED");
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(contextoTenant).limparTenantIdOverride();
  }

  @Test
  void listarAgendamentosClienteNormalizaLimiteEMapeiaResposta() {
    UUID clientId = UUID.randomUUID();
    Agendamento agendamento = new Agendamento();
    agendamento.setId(UUID.randomUUID());
    agendamento.setTenantId(tenantId);
    agendamento.setClientId(clientId);
    agendamento.setStatus(StatusAgendamento.CONFIRMED);
    agendamento.setDate(LocalDate.of(2026, 2, 1));
    agendamento.setStartTime("10:00");
    agendamento.setEndTime("10:30");
    agendamento.setCreatedAt(Instant.now());
    when(agendamentoRepository.listByTenantAndClientExcludingStatus(
            eq(tenantId), eq(clientId), eq(StatusAgendamento.CANCELLED), eq(Limit.of(50))))
        .thenReturn(List.of(agendamento));

    List<AgendamentoResponse> result =
        controller.listarAgendamentosCliente(clientId.toString(), tenantId.toString(), 999);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id).isEqualTo(agendamento.getId().toString());
    assertThat(result.get(0).status).isEqualTo("CONFIRMED");
    verify(agendamentoRepository)
        .listByTenantAndClientExcludingStatus(tenantId, clientId, StatusAgendamento.CANCELLED, Limit.of(50));
  }

  // ─── clientes ────────────────────────────────────────────────────────

  @Test
  void buscarClientePorIdentificadorRetorna404QuandoNaoEncontrado() {
    when(clienteRepository.findByTenantAndPhoneDigits(tenantId, "11987654321")).thenReturn(Optional.empty());

    ResponseEntity<ClienteDto> result =
        controller.buscarClientePorIdentificador(tenantId.toString(), "11987654321");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void buscarClientePorIdentificadorUsaTelefoneQuandoDigitosSuficientes() {
    Cliente cliente = clienteComStats();
    when(clienteRepository.findByTenantAndPhoneDigits(tenantId, "11987654321"))
        .thenReturn(Optional.of(cliente));
    when(clienteStatsRepository.findStatsByTenantAndClient(tenantId, cliente.getId()))
        .thenReturn(new ClienteStats(3, BigDecimal.TEN, null));

    ResponseEntity<ClienteDto> result =
        controller.buscarClientePorIdentificador(tenantId.toString(), "(11) 98765-4321");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().totalVisits).isEqualTo(3);
  }

  @Test
  void buscarClientePorIdentificadorUsaEmailQuandoContemArroba() {
    Cliente cliente = clienteComStats();
    when(clienteRepository.findByTenantIdAndEmailIgnoreCase(tenantId, "cliente@teste.com"))
        .thenReturn(Optional.of(cliente));
    when(clienteStatsRepository.findStatsByTenantAndClient(tenantId, cliente.getId()))
        .thenReturn(ClienteStats.EMPTY);

    ResponseEntity<ClienteDto> result =
        controller.buscarClientePorIdentificador(tenantId.toString(), "cliente@teste.com");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void criarClienteRetornaExistenteQuandoJaEncontradoPorIdentificador() {
    Cliente existente = clienteComStats();
    when(clienteRepository.findByTenantAndPhoneDigits(tenantId, "11987654321"))
        .thenReturn(Optional.of(existente));
    when(clienteStatsRepository.findStatsByTenantAndClient(tenantId, existente.getId()))
        .thenReturn(ClienteStats.EMPTY);
    ClienteCreateRequest request = new ClienteCreateRequest();
    request.identifier = "(11) 98765-4321";
    request.name = "Novo Nome";

    ResponseEntity<ClienteDto> result = controller.criarCliente(tenantId.toString(), request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().id).isEqualTo(existente.getId().toString());
    verify(clienteRepository, never()).save(any());
  }

  @Test
  void criarClientePersisteNovoQuandoNaoEncontrado() {
    when(clienteRepository.findByTenantAndPhoneDigits(any(), any())).thenReturn(Optional.empty());
    when(clienteRepository.save(any(Cliente.class))).thenAnswer(this::simulatePrePersist);
    when(clienteStatsRepository.findStatsByTenantAndClient(eq(tenantId), any()))
        .thenReturn(ClienteStats.EMPTY);
    ClienteCreateRequest request = new ClienteCreateRequest();
    request.identifier = "11999998888";
    request.name = "  Fulano  ";
    request.phone = "(11) 99999-8888";
    request.email = "Fulano@Teste.COM";

    ResponseEntity<ClienteDto> result = controller.criarCliente(tenantId.toString(), request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody().name).isEqualTo("Fulano");
    assertThat(result.getBody().email).isEqualTo("fulano@teste.com");
    assertThat(result.getBody().phone).isEqualTo("11999998888");
    verify(clienteRepository).save(any(Cliente.class));
  }

  @Test
  void criarClienteUsaNomePadraoQuandoNomeEmBranco() {
    when(clienteRepository.findByTenantAndPhoneDigits(any(), any())).thenReturn(Optional.empty());
    when(clienteRepository.save(any(Cliente.class))).thenAnswer(this::simulatePrePersist);
    when(clienteStatsRepository.findStatsByTenantAndClient(eq(tenantId), any()))
        .thenReturn(ClienteStats.EMPTY);
    ClienteCreateRequest request = new ClienteCreateRequest();

    ResponseEntity<ClienteDto> result = controller.criarCliente(tenantId.toString(), request);

    assertThat(result.getBody().name).isEqualTo("Cliente Assistente");
  }

  /** Simula o {@code @PrePersist} de {@link Cliente}, que so roda de verdade via Hibernate. */
  private Cliente simulatePrePersist(org.mockito.invocation.InvocationOnMock invocation) {
    Cliente cliente = invocation.getArgument(0);
    if (cliente.getId() == null) {
      cliente.setId(UUID.randomUUID());
    }
    return cliente;
  }

  private Cliente clienteComStats() {
    Cliente cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setTenantId(tenantId);
    cliente.setName("Cliente Existente");
    cliente.setPhone("11987654321");
    cliente.setEmail("cliente@teste.com");
    return cliente;
  }

  // ─── notificacoes ────────────────────────────────────────────────────

  @Test
  void criarNotificacaoPublicaComStatusResolvidoERetorna201() {
    NotificacaoCreateRequest request = new NotificacaoCreateRequest();
    request.appointmentId = null;
    request.channel = "WHATSAPP";
    request.destination = "11999998888";
    request.message = "Ola";
    request.status = "SENT";

    ResponseEntity<Void> result = controller.criarNotificacao(tenantId.toString(), request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(notificationPublisher).publish(
        eq(tenantId),
        isNull(),
        eq("WHATSAPP"),
        eq("11999998888"),
        eq("Ola"),
        eq(StatusNotification.SENT),
        isNull(),
        any(Instant.class));
  }

  @Test
  void criarNotificacaoResolveStatusInvalidoComoSent() {
    NotificacaoCreateRequest request = new NotificacaoCreateRequest();
    request.channel = "WHATSAPP";
    request.destination = "11999998888";
    request.message = "Ola";
    request.status = "STATUS_INEXISTENTE";

    controller.criarNotificacao(tenantId.toString(), request);

    verify(notificationPublisher).publish(
        eq(tenantId),
        isNull(),
        eq("WHATSAPP"),
        eq("11999998888"),
        eq("Ola"),
        eq(StatusNotification.SENT),
        isNull(),
        any(Instant.class));
  }

  @Test
  void criarNotificacaoResolveAppointmentIdQuandoInformado() {
    UUID appointmentId = UUID.randomUUID();
    NotificacaoCreateRequest request = new NotificacaoCreateRequest();
    request.appointmentId = appointmentId.toString();
    request.channel = "WHATSAPP";
    request.destination = "11999998888";
    request.message = "Ola";
    request.status = "SENT";

    controller.criarNotificacao(tenantId.toString(), request);

    verify(notificationPublisher).publish(
        eq(tenantId),
        eq(appointmentId),
        eq("WHATSAPP"),
        eq("11999998888"),
        eq("Ola"),
        eq(StatusNotification.SENT),
        isNull(),
        any(Instant.class));
  }

  // ─── permissoes whatsapp ─────────────────────────────────────────────

  @Test
  void obterPermissoesWhatsAppDelegaParaFindByTenantIdOrCreate() {
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(tenantId);
    config.setCanSchedule(true);
    config.setCanCancel(false);
    config.setCanReschedule(true);
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId)).thenReturn(config);

    WhatsAppPermissoesResponse result = controller.obterPermissoesWhatsApp(tenantId.toString());

    assertThat(result.canSchedule).isTrue();
    assertThat(result.canCancel).isFalse();
    assertThat(result.canReschedule).isTrue();
  }
}
