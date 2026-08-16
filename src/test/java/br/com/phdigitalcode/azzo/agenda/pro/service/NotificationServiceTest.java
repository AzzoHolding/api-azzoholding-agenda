package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import br.com.phdigitalcode.azzo.agenda.pro.dto.notification.NotificationDtos.NotificationListResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Notification;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NotificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Espelha o comportamento de {@code modules/notifications/application/ServicoNotificacoes.java}. */
class NotificationServiceTest {

  private final UUID tenantId = UUID.randomUUID();

  private NotificationRepository notificationRepository;
  private NotificationPublisher notificationPublisher;
  private AgendamentoRepository agendamentoRepository;
  private ClienteRepository clienteRepository;
  private ProfissionalRepository profissionalRepository;
  private ServicoRepository servicoRepository;
  private ContextoTenant contextoTenant;
  private AuthenticatedUser authenticatedUser;
  private NotificationService service;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    notificationPublisher = mock(NotificationPublisher.class);
    agendamentoRepository = mock(AgendamentoRepository.class);
    clienteRepository = mock(ClienteRepository.class);
    profissionalRepository = mock(ProfissionalRepository.class);
    servicoRepository = mock(ServicoRepository.class);
    contextoTenant = mock(ContextoTenant.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    service =
        new NotificationService(
            notificationRepository,
            notificationPublisher,
            agendamentoRepository,
            clienteRepository,
            profissionalRepository,
            servicoRepository,
            contextoTenant,
            authenticatedUser);
  }

  // ─── registrarCriacaoAgendamento ────────────────────────────────────────

  @Test
  void registrarCriacaoAgendamentoNaoFazNadaSemTenantOuAppointment() {
    service.registrarCriacaoAgendamento(null, UUID.randomUUID(), UUID.randomUUID(), "X");
    service.registrarCriacaoAgendamento(tenantId, null, UUID.randomUUID(), "X");

    org.mockito.Mockito.verifyNoInteractions(notificationPublisher);
  }

  @Test
  void registrarCriacaoAgendamentoIgnoraQuandoJaExisteNotificacaoIgualDuplicada() {
    UUID appointmentId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(java.util.Optional.empty());
    when(notificationRepository.findFirstByTenantIdAndAppointmentIdAndChannelAndDestination(
            tenantId, appointmentId, "APPOINTMENT_CREATED", "sistema"))
        .thenReturn(java.util.Optional.of(new Notification()));

    service.registrarCriacaoAgendamento(tenantId, appointmentId, clientId, null);

    org.mockito.Mockito.verifyNoInteractions(notificationPublisher);
  }

  @Test
  void registrarCriacaoAgendamentoUsaTelefoneDoClienteComoDestino() {
    UUID appointmentId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID professionalId = UUID.randomUUID();

    Cliente cliente = new Cliente();
    cliente.setPhone("(11) 98888-7777");
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(java.util.Optional.of(cliente));
    when(notificationRepository.findFirstByTenantIdAndAppointmentIdAndChannelAndDestination(
            eq(tenantId), eq(appointmentId), eq("APPOINTMENT_CREATED"), eq("11988887777")))
        .thenReturn(java.util.Optional.empty());

    Agendamento appointment = new Agendamento();
    appointment.setId(appointmentId);
    appointment.setTenantId(tenantId);
    appointment.setProfessionalId(professionalId);
    appointment.setDate(LocalDate.of(2026, 3, 10));
    appointment.setStartTime("14:30");
    when(agendamentoRepository.findByIdAndTenantId(appointmentId, tenantId))
        .thenReturn(java.util.Optional.of(appointment));

    service.registrarCriacaoAgendamento(tenantId, appointmentId, clientId, null);

    verify(notificationPublisher)
        .publish(
            eq(tenantId),
            eq(appointmentId),
            eq(professionalId),
            eq("APPOINTMENT_CREATED"),
            eq("11988887777"),
            any(),
            eq(StatusNotification.SENT),
            eq(null),
            any());
  }

  @Test
  void registrarCriacaoAgendamentoUsaEmailQuandoNaoHaTelefone() {
    UUID appointmentId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    Cliente cliente = new Cliente();
    cliente.setEmail("Cliente@Exemplo.com");
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(java.util.Optional.of(cliente));
    when(notificationRepository.findFirstByTenantIdAndAppointmentIdAndChannelAndDestination(
            eq(tenantId), eq(appointmentId), eq("APPOINTMENT_CREATED"), eq("cliente@exemplo.com")))
        .thenReturn(java.util.Optional.empty());

    service.registrarCriacaoAgendamento(tenantId, appointmentId, clientId, null);

    verify(notificationPublisher)
        .publish(
            eq(tenantId),
            eq(appointmentId),
            eq((UUID) null),
            eq("APPOINTMENT_CREATED"),
            eq("cliente@exemplo.com"),
            any(),
            eq(StatusNotification.SENT),
            eq(null),
            any());
  }

  @Test
  void registrarCriacaoAgendamentoUsaSistemaQuandoClienteNuloOuNaoEncontrado() {
    UUID appointmentId = UUID.randomUUID();
    when(notificationRepository.findFirstByTenantIdAndAppointmentIdAndChannelAndDestination(
            eq(tenantId), eq(appointmentId), eq("APPOINTMENT_CREATED"), eq("sistema")))
        .thenReturn(java.util.Optional.empty());

    service.registrarCriacaoAgendamento(tenantId, appointmentId, null, "");

    verify(notificationPublisher)
        .publish(
            eq(tenantId),
            eq(appointmentId),
            eq((UUID) null),
            eq("APPOINTMENT_CREATED"),
            eq("sistema"),
            any(),
            eq(StatusNotification.SENT),
            eq(null),
            any());
  }

  @Test
  void registrarCriacaoAgendamentoUsaMensagemPadraoQuandoAppointmentNaoEncontrado() {
    UUID appointmentId = UUID.randomUUID();
    when(notificationRepository.findFirstByTenantIdAndAppointmentIdAndChannelAndDestination(
            any(), any(), any(), any()))
        .thenReturn(java.util.Optional.empty());
    when(agendamentoRepository.findByIdAndTenantId(appointmentId, tenantId))
        .thenReturn(java.util.Optional.empty());

    service.registrarCriacaoAgendamento(tenantId, appointmentId, null, "CUSTOM_CHANNEL");

    verify(notificationPublisher)
        .publish(
            eq(tenantId),
            eq(appointmentId),
            eq((UUID) null),
            eq("CUSTOM_CHANNEL"),
            eq("sistema"),
            eq("Novo agendamento criado."),
            eq(StatusNotification.SENT),
            eq(null),
            any());
  }

  @Test
  void registrarCriacaoAgendamentoMontaMensagemComServicoEProfissional() {
    UUID appointmentId = UUID.randomUUID();
    UUID professionalId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    when(notificationRepository.findFirstByTenantIdAndAppointmentIdAndChannelAndDestination(
            any(), any(), any(), any()))
        .thenReturn(java.util.Optional.empty());

    Servico servico = new Servico();
    servico.setName("Corte de cabelo");
    AgendamentoItem item = new AgendamentoItem();
    item.setServiceId(serviceId);
    item.setService(servico);

    Agendamento appointment = new Agendamento();
    appointment.setId(appointmentId);
    appointment.setTenantId(tenantId);
    appointment.setProfessionalId(professionalId);
    appointment.setDate(LocalDate.of(2026, 5, 20));
    appointment.setStartTime("09:00");
    appointment.getItems().add(item);
    when(agendamentoRepository.findByIdAndTenantId(appointmentId, tenantId))
        .thenReturn(java.util.Optional.of(appointment));

    Profissional profissional = new Profissional();
    profissional.setName("Joana Silva");
    when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
        .thenReturn(java.util.Optional.of(profissional));

    service.registrarCriacaoAgendamento(tenantId, appointmentId, null, null);

    verify(notificationPublisher)
        .publish(
            eq(tenantId),
            eq(appointmentId),
            eq(professionalId),
            eq("APPOINTMENT_CREATED"),
            eq("sistema"),
            eq("Agendamento criado. Servico: Corte de cabelo, profissional: Joana Silva, data: 2026-05-20, horario: 09:00."),
            eq(StatusNotification.SENT),
            eq(null),
            any());
  }

  // ─── listar ─────────────────────────────────────────────────────────────

  @Test
  void listarUsaEscopoDeProfissionalQuandoUsuarioEhSomenteProfessional() {
    UUID professionalId = UUID.randomUUID();
    when(authenticatedUser.temRole("PROFESSIONAL")).thenReturn(true);
    when(authenticatedUser.temRole("OWNER")).thenReturn(false);
    when(authenticatedUser.temRole("ADMIN")).thenReturn(false);
    UUID userId = UUID.randomUUID();
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    Profissional profissional = new Profissional();
    profissional.setId(professionalId);
    when(profissionalRepository.findByTenantIdAndUserId(tenantId, userId))
        .thenReturn(java.util.Optional.of(profissional));
    when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.listar(null, null, false, false, 10, null, null);

    verify(profissionalRepository).findByTenantIdAndUserId(tenantId, userId);
  }

  @Test
  void listarNaoResolveProfissionalQuandoUsuarioEhOwner() {
    when(authenticatedUser.temRole("PROFESSIONAL")).thenReturn(false);
    when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.listar(null, null, false, false, 10, null, null);

    verify(profissionalRepository, never()).findByTenantIdAndUserId(any(), any());
  }

  @Test
  void listarNormalizaLimitParaFaixaValida() {
    when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    when(authenticatedUser.temRole("PROFESSIONAL")).thenReturn(false);

    service.listar(null, null, false, false, 0, null, null); // <=0 vira 100
    service.listar(null, null, false, false, 999, null, null); // >500 vira 500

    org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
    verify(notificationRepository, times(2)).findAll(any(Specification.class), captor.capture());
    assertThat(captor.getAllValues().get(0).getPageSize()).isEqualTo(101);
    assertThat(captor.getAllValues().get(1).getPageSize()).isEqualTo(501);
  }

  @Test
  void listarRetornaHasMoreECursorQuandoExcedeLimite() {
    when(authenticatedUser.temRole("PROFESSIONAL")).thenReturn(false);
    Notification n1 = notificacao(Instant.parse("2026-01-03T10:00:00Z"));
    Notification n2 = notificacao(Instant.parse("2026-01-02T10:00:00Z"));
    when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(n1, n2)));

    NotificationListResponse response = service.listar(null, null, false, false, 1, null, null);

    assertThat(response.items).hasSize(1);
    assertThat(response.hasMore).isTrue();
    assertThat(response.nextCursorId).isEqualTo(n1.getId().toString());
    assertThat(response.nextCursorCreatedAt).isEqualTo(n1.getCreatedAt().toString());
  }

  @Test
  void listarRetornaHasMoreFalseQuandoNaoExcedeLimite() {
    when(authenticatedUser.temRole("PROFESSIONAL")).thenReturn(false);
    Notification n1 = notificacao(Instant.now());
    when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(n1)));

    NotificationListResponse response = service.listar(null, null, false, false, 10, null, null);

    assertThat(response.hasMore).isFalse();
    assertThat(response.nextCursorId).isNull();
    assertThat(response.nextCursorCreatedAt).isNull();
  }

  @Test
  void listarMapeiaTodosOsCamposDaResposta() {
    when(authenticatedUser.temRole("PROFESSIONAL")).thenReturn(false);
    Notification n = notificacao(Instant.parse("2026-02-01T08:00:00Z"));
    n.setAppointmentId(UUID.randomUUID());
    n.setProfessionalId(UUID.randomUUID());
    n.setChannel("whatsapp");
    n.setDestination("11999999999");
    n.setMessage("mensagem");
    n.setStatus(StatusNotification.FAILED);
    n.setErrorMessage("erro X");
    n.setSentAt(Instant.parse("2026-02-01T08:00:01Z"));
    n.setViewedAt(Instant.parse("2026-02-01T09:00:00Z"));
    when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(n)));

    NotificationListResponse response = service.listar(null, null, false, false, 10, null, null);

    var item = response.items.get(0);
    assertThat(item.id).isEqualTo(n.getId().toString());
    assertThat(item.tenantId).isEqualTo(n.getTenantId().toString());
    assertThat(item.appointmentId).isEqualTo(n.getAppointmentId().toString());
    assertThat(item.professionalId).isEqualTo(n.getProfessionalId().toString());
    assertThat(item.channel).isEqualTo("whatsapp");
    assertThat(item.destination).isEqualTo("11999999999");
    assertThat(item.message).isEqualTo("mensagem");
    assertThat(item.status).isEqualTo("FAILED");
    assertThat(item.errorMessage).isEqualTo("erro X");
    assertThat(item.sentAt).isEqualTo(n.getSentAt().toString());
    assertThat(item.viewedAt).isEqualTo(n.getViewedAt().toString());
    assertThat(item.viewed).isTrue();
    assertThat(item.createdAt).isEqualTo(n.getCreatedAt().toString());
  }

  // ─── listarMeusAgendamentos ─────────────────────────────────────────────

  @Test
  void listarMeusAgendamentosResolveProfissionalDoUsuarioLogado() {
    UUID userId = UUID.randomUUID();
    UUID professionalId = UUID.randomUUID();
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    Profissional profissional = new Profissional();
    profissional.setId(professionalId);
    when(profissionalRepository.findByTenantIdAndUserId(tenantId, userId))
        .thenReturn(java.util.Optional.of(profissional));
    when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.listarMeusAgendamentos(false, 10, null, null);

    verify(profissionalRepository).findByTenantIdAndUserId(tenantId, userId);
  }

  // ─── remover / removerTodasDoTenant ─────────────────────────────────────

  @Test
  void removerRetornaFalseQuandoIdNulo() {
    assertThat(service.remover(null)).isFalse();
    verify(notificationRepository, never()).deleteByIdAndTenantId(any(), any());
  }

  @Test
  void removerRetornaTrueQuandoRemoveu() {
    UUID id = UUID.randomUUID();
    when(notificationRepository.deleteByIdAndTenantId(id, tenantId)).thenReturn(1L);

    assertThat(service.remover(id)).isTrue();
  }

  @Test
  void removerRetornaFalseQuandoNaoEncontrado() {
    UUID id = UUID.randomUUID();
    when(notificationRepository.deleteByIdAndTenantId(id, tenantId)).thenReturn(0L);

    assertThat(service.remover(id)).isFalse();
  }

  @Test
  void removerTodasDoTenantDelegaParaRepositorio() {
    when(notificationRepository.deleteByTenantId(tenantId)).thenReturn(5L);

    assertThat(service.removerTodasDoTenant()).isEqualTo(5L);
  }

  // ─── marcarComoVisualizada / marcarTodasComoVisualizadas ────────────────

  @Test
  void marcarComoVisualizadaRetornaFalseQuandoIdNulo() {
    assertThat(service.marcarComoVisualizada(null)).isFalse();
  }

  @Test
  void marcarComoVisualizadaRetornaFalseQuandoNaoEncontrada() {
    UUID id = UUID.randomUUID();
    when(notificationRepository.findByIdAndTenantId(id, tenantId)).thenReturn(java.util.Optional.empty());

    assertThat(service.marcarComoVisualizada(id)).isFalse();
  }

  @Test
  void marcarComoVisualizadaRetornaTrueSemAlterarQuandoJaVisualizada() {
    UUID id = UUID.randomUUID();
    Notification n = notificacao(Instant.now());
    Instant viewedAt = Instant.parse("2020-01-01T00:00:00Z");
    n.setViewedAt(viewedAt);
    when(notificationRepository.findByIdAndTenantId(id, tenantId)).thenReturn(java.util.Optional.of(n));

    assertThat(service.marcarComoVisualizada(id)).isTrue();
    assertThat(n.getViewedAt()).isEqualTo(viewedAt);
  }

  @Test
  void marcarComoVisualizadaMarcaEDaTrueQuandoAindaNaoVista() {
    UUID id = UUID.randomUUID();
    Notification n = notificacao(Instant.now());
    when(notificationRepository.findByIdAndTenantId(id, tenantId)).thenReturn(java.util.Optional.of(n));

    assertThat(service.marcarComoVisualizada(id)).isTrue();
    assertThat(n.getViewedAt()).isNotNull();
  }

  @Test
  void marcarTodasComoVisualizadasDelegaParaRepositorio() {
    when(notificationRepository.markAllViewedByTenant(eq(tenantId), any())).thenReturn(4L);

    assertThat(service.marcarTodasComoVisualizadas()).isEqualTo(4L);
  }

  private Notification notificacao(Instant createdAt) {
    Notification n = new Notification();
    n.setId(UUID.randomUUID());
    n.setTenantId(tenantId);
    n.setChannel("APPOINTMENT_CREATED");
    n.setDestination("sistema");
    n.setStatus(StatusNotification.SENT);
    n.setCreatedAt(createdAt);
    return n;
  }
}
