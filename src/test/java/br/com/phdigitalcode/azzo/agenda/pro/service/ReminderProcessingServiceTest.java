package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantOperationalSettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AssistantApiClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NotificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantOperationalSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantTelegramConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.CustomerCommunicationChannelResolver.ResolvedChannel;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendCommand;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.CommunicationChannelDispatcher;

/**
 * Cobre {@code modules/scheduling/application/ReminderProcessingService.java} — espelha
 * {@code ReminderProcessingServiceUnitTest} (roteamento de canal) e
 * {@code ReminderProcessingServiceGatingUnitTest} (regua de lembretes) do original, mais o fluxo
 * completo de envio (feliz, falha, opt-out, canal desabilitado, ja enviado).
 *
 * <p>O {@link PlatformTransactionManager} e um mock: o {@code TransactionTemplate} real chama
 * {@code getTransaction}/{@code commit} nele e executa o callback de verdade, entao a logica sob
 * teste roda exatamente como em producao, sem precisar de contexto Spring — mesmo padrao de
 * {@code AppointmentNoShowProcessingServiceTest}.
 */
class ReminderProcessingServiceTest {

  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private AgendamentoRepository agendamentoRepository;
  private TenantRepository tenantRepository;
  private TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private TenantTelegramConfigRepository tenantTelegramConfigRepository;
  private TenantOperationalSettingsRepository tenantOperationalSettingsRepository;
  private ClienteRepository clienteRepository;
  private NotificationRepository notificationRepository;
  private NotificationPublisher notificationPublisher;
  private CommunicationChannelDispatcher communicationChannelDispatcher;
  private CustomerCommunicationChannelResolver customerCommunicationChannelResolver;
  private AssistantApiClient assistantApiClient;
  private ReminderProcessingService service;

  @BeforeEach
  void setUp() {
    agendamentoRepository = mock(AgendamentoRepository.class);
    tenantRepository = mock(TenantRepository.class);
    tenantWhatsAppConfigRepository = mock(TenantWhatsAppConfigRepository.class);
    tenantTelegramConfigRepository = mock(TenantTelegramConfigRepository.class);
    tenantOperationalSettingsRepository = mock(TenantOperationalSettingsRepository.class);
    clienteRepository = mock(ClienteRepository.class);
    notificationRepository = mock(NotificationRepository.class);
    notificationPublisher = mock(NotificationPublisher.class);
    communicationChannelDispatcher = mock(CommunicationChannelDispatcher.class);
    customerCommunicationChannelResolver = mock(CustomerCommunicationChannelResolver.class);
    assistantApiClient = mock(AssistantApiClient.class);
    service =
        new ReminderProcessingService(
            agendamentoRepository,
            tenantRepository,
            tenantWhatsAppConfigRepository,
            tenantTelegramConfigRepository,
            tenantOperationalSettingsRepository,
            clienteRepository,
            notificationRepository,
            notificationPublisher,
            communicationChannelDispatcher,
            customerCommunicationChannelResolver,
            assistantApiClient,
            mock(PlatformTransactionManager.class));
  }

  // ============================================================
  // Roteamento de canal (sendReminderMessage / resolveReminderRoute)
  // ============================================================

  @Test
  void deveEnviarLembretePeloDispatcherNoCanalWhatsApp() {
    when(communicationChannelDispatcher.sendText(any())).thenReturn(ChannelSendResult.sent("provider-1"));
    UUID tenantId = UUID.randomUUID();

    ChannelSendResult result = service.sendReminderMessage(tenantId, ChatChannel.WHATSAPP, "5511999991111", "Lembrete");

    assertThat(result.success()).isTrue();
    verify(communicationChannelDispatcher)
        .sendText(new ChannelSendCommand(tenantId, ChatChannel.WHATSAPP, "5511999991111", "Lembrete"));
  }

  @Test
  void devePropagarFalhaDoDispatcherNoEnvioDoLembrete() {
    when(communicationChannelDispatcher.sendText(any()))
        .thenReturn(ChannelSendResult.failed("WHATSAPP_NOT_ENABLED", "WhatsApp nao habilitado"));

    ChannelSendResult result =
        service.sendReminderMessage(UUID.randomUUID(), ChatChannel.WHATSAPP, "5511999991111", "Lembrete");

    assertThat(result.success()).isFalse();
    assertThat(result.providerErrorCode()).isEqualTo("WHATSAPP_NOT_ENABLED");
  }

  @Test
  void deveUsarCanalTelegramNaConversaMaisRecenteDoCliente() {
    Cliente client = new Cliente();
    client.setId(UUID.randomUUID());
    client.setPhone("5511999991111");
    when(customerCommunicationChannelResolver.resolve(any(), eq(client), any(String[].class)))
        .thenReturn(new ResolvedChannel(ChatChannel.TELEGRAM, "123456789", null));

    ReminderProcessingService.ReminderRoute route = service.resolveReminderRoute(UUID.randomUUID(), client);

    assertThat(route.channel()).isEqualTo(ChatChannel.TELEGRAM);
    assertThat(route.destination()).isEqualTo("123456789");
  }

  @Test
  void deveUsarWhatsAppQuandoNaoHouverConversaAnterior() {
    Cliente client = new Cliente();
    client.setId(UUID.randomUUID());
    client.setPhone("(11) 99999-1111");
    when(customerCommunicationChannelResolver.resolve(any(), eq(client), any(String[].class)))
        .thenReturn(new ResolvedChannel(ChatChannel.WHATSAPP, client.getPhone(), null));

    ReminderProcessingService.ReminderRoute route = service.resolveReminderRoute(UUID.randomUUID(), client);

    assertThat(route.channel()).isEqualTo(ChatChannel.WHATSAPP);
    assertThat(route.destination()).isEqualTo("11999991111");
  }

  @Test
  void deveFalharQuandoConversaTelegramNaoTiverDestino() {
    Cliente client = new Cliente();
    client.setId(UUID.randomUUID());
    client.setPhone("5511999991111");
    when(customerCommunicationChannelResolver.resolve(any(), eq(client), any(String[].class)))
        .thenThrow(new IllegalArgumentException("Conversa Telegram sem destino configurado."));

    assertThatThrownBy(() -> service.resolveReminderRoute(UUID.randomUUID(), client))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ============================================================
  // Regua D-1 — gate (ReminderProcessingServiceGatingUnitTest)
  // ============================================================

  @Test
  void d1DeveSerBarradoQuandoDesabilitado() {
    Agendamento appointment = agendamentoAmanha();
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setD1ReminderEnabled(false);
    stubGateD1(appointment, settings);

    assertThat(service.processAppointment(appointment.getId())).isZero();
  }

  @Test
  void d1DeveSerBarradoAntesDoHorarioConfigurado() {
    Agendamento appointment = agendamentoAmanha();
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setD1ReminderEnabled(true);
    LocalTime agora = ZonedDateTime.now(ZONE_BR).toLocalTime();
    LocalTime futuro = agora.plusHours(1).isBefore(agora) ? LocalTime.of(23, 59) : agora.plusHours(1).withSecond(0);
    settings.setD1ReminderHora(futuro.toString().substring(0, 5));
    stubGateD1(appointment, settings);

    assertThat(service.processAppointment(appointment.getId())).isZero();
  }

  @Test
  void d1DevePassarDoGateEChegarNoClienteQuandoHabilitadoEHorarioJaPassou() {
    Agendamento appointment = agendamentoAmanha();
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setD1ReminderEnabled(true);
    settings.setD1ReminderHora("00:00"); // sempre ja passou
    stubGateD1(appointment, settings);
    when(clienteRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());

    // Gate passou (chegou ate a busca de cliente) e retornou 0 porque o cliente nao foi encontrado.
    assertThat(service.processAppointment(appointment.getId())).isZero();
    verify(clienteRepository).findByIdAndTenantId(appointment.getClientId(), appointment.getTenantId());
  }

  @Test
  void horasAntesDeveSerBarradoQuandoDesabilitado() {
    Agendamento appointment = agendamentoDaquiA(2);
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setHoursBeforeReminderEnabled(false);
    stubGateHoras(appointment, settings);

    assertThat(service.processHoursBeforeAppointment(appointment.getId())).isZero();
  }

  @Test
  void horasAntesDeveSerBarradoForaDaJanela() {
    Agendamento appointment = agendamentoDaquiA(10); // fora da janela de 2h configurada
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setHoursBeforeReminderEnabled(true);
    settings.setReminderHours(2);
    stubGateHoras(appointment, settings);

    assertThat(service.processHoursBeforeAppointment(appointment.getId())).isZero();
  }

  @Test
  void horasAntesDevePassarDoGateDentroDaJanela() {
    Agendamento appointment = agendamentoDaquiA(1); // dentro da janela de 2h configurada
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setHoursBeforeReminderEnabled(true);
    settings.setReminderHours(2);
    stubGateHoras(appointment, settings);
    when(clienteRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());

    assertThat(service.processHoursBeforeAppointment(appointment.getId())).isZero();
    verify(clienteRepository).findByIdAndTenantId(appointment.getClientId(), appointment.getTenantId());
  }

  @Test
  void horasAntesDeveSerBarradoQuandoHorarioJaPassou() {
    Agendamento appointment = agendamentoDaquiA(-1); // ja passou
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setHoursBeforeReminderEnabled(true);
    settings.setReminderHours(2);
    stubGateHoras(appointment, settings);

    assertThat(service.processHoursBeforeAppointment(appointment.getId())).isZero();
  }

  // ============================================================
  // Gates comuns (ja enviado / tenant inexistente)
  // ============================================================

  @Test
  void d1NaoReenviaQuandoJaFoiEnviado() {
    Agendamento appointment = agendamentoAmanha();
    when(agendamentoRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
    when(notificationRepository.existsSentByAppointmentAndChannels(
            appointment.getTenantId(),
            appointment.getId(),
            List.of("WHATSAPP_REMINDER", "TELEGRAM_REMINDER")))
        .thenReturn(true);

    assertThat(service.processAppointment(appointment.getId())).isZero();
    verifyNoInteractions(tenantRepository, tenantOperationalSettingsRepository);
  }

  @Test
  void d1IgnoraQuandoTenantNaoExiste() {
    Agendamento appointment = agendamentoAmanha();
    when(agendamentoRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
    when(notificationRepository.existsSentByAppointmentAndChannels(any(), any(), anyList())).thenReturn(false);
    when(tenantRepository.findById(appointment.getTenantId())).thenReturn(Optional.empty());

    assertThat(service.processAppointment(appointment.getId())).isZero();
    verifyNoInteractions(tenantOperationalSettingsRepository);
  }

  @Test
  void processAppointmentComAgendamentoInexistenteNaoConta() {
    UUID id = UUID.randomUUID();
    when(agendamentoRepository.findById(id)).thenReturn(Optional.empty());

    assertThat(service.processAppointment(id)).isZero();
    verifyNoInteractions(tenantRepository, tenantOperationalSettingsRepository, clienteRepository);
  }

  // ============================================================
  // Fluxo completo de envio (enviarLembrete)
  // ============================================================

  @Test
  void fluxoFelizEnviaPublicaESemeiaContextoDoAssistente() {
    Agendamento appointment = agendamentoAmanha();
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setD1ReminderEnabled(true);
    settings.setD1ReminderHora("00:00");
    stubGateD1(appointment, settings);

    Cliente client = clienteComTelefone(appointment.getClientId(), appointment.getTenantId());
    when(clienteRepository.findByIdAndTenantId(appointment.getClientId(), appointment.getTenantId()))
        .thenReturn(Optional.of(client));
    when(customerCommunicationChannelResolver.resolve(eq(appointment.getTenantId()), eq(client), any(String[].class)))
        .thenReturn(new ResolvedChannel(ChatChannel.WHATSAPP, client.getPhone(), null));
    TenantWhatsAppConfig whatsAppConfig = enabledWhatsAppConfig();
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(appointment.getTenantId())).thenReturn(whatsAppConfig);
    when(communicationChannelDispatcher.sendText(any())).thenReturn(ChannelSendResult.sent("msg-1"));

    assertThat(service.processAppointment(appointment.getId())).isEqualTo(1);

    verify(notificationPublisher)
        .publish(
            eq(appointment.getTenantId()),
            eq(appointment.getId()),
            eq("WHATSAPP_REMINDER"),
            eq(client.getPhone()),
            any(),
            eq(StatusNotification.SENT),
            eq((String) null),
            any());
    verify(assistantApiClient)
        .seedReminderContext(
            appointment.getTenantId().toString(), client.getPhone(), appointment.getId().toString(), client.getName());
  }

  @Test
  void falhaDoDispatcherPublicaComoFailedENaoSemeiaContexto() {
    Agendamento appointment = agendamentoAmanha();
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setD1ReminderEnabled(true);
    settings.setD1ReminderHora("00:00");
    stubGateD1(appointment, settings);

    Cliente client = clienteComTelefone(appointment.getClientId(), appointment.getTenantId());
    when(clienteRepository.findByIdAndTenantId(appointment.getClientId(), appointment.getTenantId()))
        .thenReturn(Optional.of(client));
    when(customerCommunicationChannelResolver.resolve(eq(appointment.getTenantId()), eq(client), any(String[].class)))
        .thenReturn(new ResolvedChannel(ChatChannel.WHATSAPP, client.getPhone(), null));
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(appointment.getTenantId()))
        .thenReturn(enabledWhatsAppConfig());
    when(communicationChannelDispatcher.sendText(any()))
        .thenReturn(ChannelSendResult.failed("PROVIDER_DOWN", "Provedor indisponivel"));

    assertThat(service.processAppointment(appointment.getId())).isEqualTo(1);

    verify(notificationPublisher)
        .publish(
            eq(appointment.getTenantId()),
            eq(appointment.getId()),
            eq("WHATSAPP_REMINDER"),
            eq(client.getPhone()),
            any(),
            eq(StatusNotification.FAILED),
            eq("Provedor indisponivel"),
            eq((java.time.Instant) null));
    verifyNoInteractions(assistantApiClient);
  }

  @Test
  void ignoraQuandoCanalWhatsAppEstaDesabilitadoParaOTenant() {
    Agendamento appointment = agendamentoAmanha();
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setD1ReminderEnabled(true);
    settings.setD1ReminderHora("00:00");
    stubGateD1(appointment, settings);

    Cliente client = clienteComTelefone(appointment.getClientId(), appointment.getTenantId());
    when(clienteRepository.findByIdAndTenantId(appointment.getClientId(), appointment.getTenantId()))
        .thenReturn(Optional.of(client));
    when(customerCommunicationChannelResolver.resolve(eq(appointment.getTenantId()), eq(client), any(String[].class)))
        .thenReturn(new ResolvedChannel(ChatChannel.WHATSAPP, client.getPhone(), null));
    TenantWhatsAppConfig disabledConfig = new TenantWhatsAppConfig();
    disabledConfig.setWhatsappEnabled(false);
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(appointment.getTenantId())).thenReturn(disabledConfig);

    assertThat(service.processAppointment(appointment.getId())).isZero();
    verifyNoInteractions(communicationChannelDispatcher, notificationPublisher, assistantApiClient);
  }

  @Test
  void respeitaOptOutDoWhatsAppENaoEnvia() {
    Agendamento appointment = agendamentoAmanha();
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setD1ReminderEnabled(true);
    settings.setD1ReminderHora("00:00");
    stubGateD1(appointment, settings);

    Cliente client = clienteComTelefone(appointment.getClientId(), appointment.getTenantId());
    client.setWhatsappOptOut(true);
    when(clienteRepository.findByIdAndTenantId(appointment.getClientId(), appointment.getTenantId()))
        .thenReturn(Optional.of(client));
    when(customerCommunicationChannelResolver.resolve(eq(appointment.getTenantId()), eq(client), any(String[].class)))
        .thenReturn(new ResolvedChannel(ChatChannel.WHATSAPP, client.getPhone(), null));
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(appointment.getTenantId()))
        .thenReturn(enabledWhatsAppConfig());

    assertThat(service.processAppointment(appointment.getId())).isZero();
    verifyNoInteractions(communicationChannelDispatcher, notificationPublisher);
  }

  @Test
  void telegramNaoConsultaOptOutDeWhatsApp() {
    Agendamento appointment = agendamentoAmanha();
    TenantOperationalSettings settings = settings(appointment.getTenantId());
    settings.setD1ReminderEnabled(true);
    settings.setD1ReminderHora("00:00");
    stubGateD1(appointment, settings);

    Cliente client = clienteComTelefone(appointment.getClientId(), appointment.getTenantId());
    client.setWhatsappOptOut(true); // irrelevante para Telegram
    when(clienteRepository.findByIdAndTenantId(appointment.getClientId(), appointment.getTenantId()))
        .thenReturn(Optional.of(client));
    when(customerCommunicationChannelResolver.resolve(eq(appointment.getTenantId()), eq(client), any(String[].class)))
        .thenReturn(new ResolvedChannel(ChatChannel.TELEGRAM, "999888777", null));
    TenantTelegramConfig telegramConfig = new TenantTelegramConfig();
    telegramConfig.setTelegramEnabled(true);
    when(tenantTelegramConfigRepository.findByTenantId(appointment.getTenantId())).thenReturn(telegramConfig);
    when(communicationChannelDispatcher.sendText(any())).thenReturn(ChannelSendResult.sent("tg-1"));

    assertThat(service.processAppointment(appointment.getId())).isEqualTo(1);

    verify(notificationPublisher)
        .publish(
            eq(appointment.getTenantId()),
            eq(appointment.getId()),
            eq("TELEGRAM_REMINDER"),
            eq("999888777"),
            any(),
            eq(StatusNotification.SENT),
            eq((String) null),
            any());
  }

  // ============================================================
  // Orquestracao (sendReminders / sendHoursBeforeReminders)
  // ============================================================

  @Test
  void sendRemindersSomaOProcessamentoDeCadaCandidato() {
    Agendamento appointment1 = agendamentoAmanha();
    Agendamento appointment2 = agendamentoAmanha();
    when(agendamentoRepository.listIdsByDateAndStatus(
            eq(LocalDate.now().plusDays(1)),
            eq(List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED))))
        .thenReturn(List.of(appointment1.getId(), appointment2.getId()));
    when(agendamentoRepository.findById(appointment1.getId())).thenReturn(Optional.of(appointment1));
    when(agendamentoRepository.findById(appointment2.getId())).thenReturn(Optional.of(appointment2));
    when(notificationRepository.existsSentByAppointmentAndChannels(any(), any(), anyList())).thenReturn(true);

    assertThat(service.sendReminders()).isZero();
    verify(agendamentoRepository, times(2)).findById(any());
  }

  @Test
  void sendHoursBeforeRemindersConsultaHojeEAmanhaEmSaoPaulo() {
    when(agendamentoRepository.listIdsByDatesInAndStatus(
            eq(List.of(LocalDate.now(ZONE_BR), LocalDate.now(ZONE_BR).plusDays(1))),
            eq(List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED))))
        .thenReturn(List.of());

    assertThat(service.sendHoursBeforeReminders()).isZero();
    verify(agendamentoRepository, never()).findById(any());
  }

  // ============================================================
  // Helpers
  // ============================================================

  private void stubGateD1(Agendamento appointment, TenantOperationalSettings settings) {
    when(agendamentoRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
    when(notificationRepository.existsSentByAppointmentAndChannels(any(), any(), anyList())).thenReturn(false);
    when(tenantRepository.findById(appointment.getTenantId())).thenReturn(Optional.of(new Tenant()));
    when(tenantOperationalSettingsRepository.findByTenantIdOrCreate(appointment.getTenantId())).thenReturn(settings);
  }

  private void stubGateHoras(Agendamento appointment, TenantOperationalSettings settings) {
    when(agendamentoRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
    when(notificationRepository.existsSentByAppointmentAndChannels(any(), any(), anyList())).thenReturn(false);
    when(tenantRepository.findById(appointment.getTenantId())).thenReturn(Optional.of(new Tenant()));
    when(tenantOperationalSettingsRepository.findByTenantIdOrCreate(appointment.getTenantId())).thenReturn(settings);
  }

  private Agendamento agendamentoAmanha() {
    Agendamento appointment = new Agendamento();
    appointment.setId(UUID.randomUUID());
    appointment.setTenantId(UUID.randomUUID());
    appointment.setClientId(UUID.randomUUID());
    appointment.setDate(LocalDate.now(ZONE_BR).plusDays(1));
    appointment.setStartTime("10:00");
    appointment.setStatus(StatusAgendamento.PENDING);
    return appointment;
  }

  private Agendamento agendamentoDaquiA(int horas) {
    ZonedDateTime alvo = ZonedDateTime.now(ZONE_BR).plusHours(horas);
    Agendamento appointment = new Agendamento();
    appointment.setId(UUID.randomUUID());
    appointment.setTenantId(UUID.randomUUID());
    appointment.setClientId(UUID.randomUUID());
    appointment.setDate(alvo.toLocalDate());
    appointment.setStartTime(LocalTime.of(alvo.getHour(), alvo.getMinute()).toString());
    appointment.setStatus(StatusAgendamento.PENDING);
    return appointment;
  }

  private TenantOperationalSettings settings(UUID tenantId) {
    TenantOperationalSettings settings = new TenantOperationalSettings();
    settings.setTenantId(tenantId);
    return settings;
  }

  private Cliente clienteComTelefone(UUID id, UUID tenantId) {
    Cliente client = new Cliente();
    client.setId(id);
    client.setTenantId(tenantId);
    client.setName("Maria Souza");
    client.setPhone("11999991111");
    client.setWhatsappOptOut(false);
    return client;
  }

  private TenantWhatsAppConfig enabledWhatsAppConfig() {
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setWhatsappEnabled(true);
    config.setCanSchedule(true);
    config.setCanCancel(true);
    config.setCanReschedule(true);
    return config;
  }
}
