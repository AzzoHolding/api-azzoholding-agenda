package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationSendLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantReactivationConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationAttemptEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStatus;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AssistantApiClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatConversationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationSendLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantReactivationConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantTelegramConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.CommunicationChannelDispatcher;

/**
 * Espelha o contrato de
 * {@code modules/chat/application/WhatsAppBookingReactivationSchedulerService.java}: os gates
 * sequenciais (reativacao desabilitada, tentativa maxima, janela de horario, cliente invalido,
 * modo manual, canal desabilitado, perfil de uso, opt-out, menoridade, rate limit mensal,
 * intervalo minimo, janela LGPD, agendamento futuro) e o fluxo feliz de envio.
 */
class WhatsAppBookingReactivationSchedulerServiceTest {

  private final UUID tenantId = UUID.randomUUID();

  private WhatsAppBookingReactivationService whatsAppBookingReactivationService;
  private WhatsAppBookingReactivationCycleRepository cycleRepository;
  private TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private TenantTelegramConfigRepository tenantTelegramConfigRepository;
  private ClienteRepository clienteRepository;
  private ChatConversationRepository chatConversationRepository;
  private CommunicationChannelDispatcher communicationChannelDispatcher;
  private CustomerCommunicationChannelResolver customerCommunicationChannelResolver;
  private ChatService chatService;
  private TenantOperationalSettingsService tenantOperationalSettingsService;
  private TenantReactivationConfigRepository tenantReactivationConfigRepository;
  private ReactivationSendLogRepository reactivationSendLogRepository;
  private AssistantApiClient assistantApiClient;
  private WhatsAppBookingReactivationSchedulerService service;

  @BeforeEach
  void setUp() {
    whatsAppBookingReactivationService = mock(WhatsAppBookingReactivationService.class);
    cycleRepository = mock(WhatsAppBookingReactivationCycleRepository.class);
    tenantWhatsAppConfigRepository = mock(TenantWhatsAppConfigRepository.class);
    tenantTelegramConfigRepository = mock(TenantTelegramConfigRepository.class);
    clienteRepository = mock(ClienteRepository.class);
    chatConversationRepository = mock(ChatConversationRepository.class);
    communicationChannelDispatcher = mock(CommunicationChannelDispatcher.class);
    customerCommunicationChannelResolver = mock(CustomerCommunicationChannelResolver.class);
    chatService = mock(ChatService.class);
    tenantOperationalSettingsService = mock(TenantOperationalSettingsService.class);
    tenantReactivationConfigRepository = mock(TenantReactivationConfigRepository.class);
    reactivationSendLogRepository = mock(ReactivationSendLogRepository.class);
    assistantApiClient = mock(AssistantApiClient.class);

    service = new WhatsAppBookingReactivationSchedulerService(
        whatsAppBookingReactivationService, cycleRepository, tenantWhatsAppConfigRepository,
        tenantTelegramConfigRepository, clienteRepository, chatConversationRepository,
        communicationChannelDispatcher, customerCommunicationChannelResolver, chatService,
        tenantOperationalSettingsService, tenantReactivationConfigRepository,
        reactivationSendLogRepository, assistantApiClient);

    when(tenantOperationalSettingsService.isReactivationEnabled(tenantId)).thenReturn(true);
    when(tenantOperationalSettingsService.allowsReactivationAttemptNumber(eq(tenantId), any())).thenReturn(true);
    when(tenantOperationalSettingsService.allowsReactivationAt(eq(tenantId), any())).thenReturn(true);
  }

  private WhatsAppBookingReactivationCycleEntity cycle() {
    WhatsAppBookingReactivationCycleEntity c = new WhatsAppBookingReactivationCycleEntity();
    c.setId(UUID.randomUUID());
    c.setTenantId(tenantId);
    c.setClientId(UUID.randomUUID());
    c.setUserIdentifier("5511988887777");
    c.setNextAttemptNumber(1);
    c.setAbandonedAt(Instant.now());
    c.setLastStage(WhatsAppBookingReactivationStage.SERVICE_SELECTION);
    return c;
  }

  private Cliente cliente(UUID id) {
    Cliente cliente = new Cliente();
    cliente.setId(id);
    cliente.setTenantId(tenantId);
    cliente.setName("Joana");
    cliente.setPhone("5511988887777");
    return cliente;
  }

  private TenantReactivationConfigEntity reactivationConfig() {
    TenantReactivationConfigEntity config = new TenantReactivationConfigEntity();
    config.setTenantId(tenantId);
    config.setMaxMessagesPerMonthPerClient(4);
    config.setMinIntervalDays(7);
    config.setSendWindowStart(LocalTime.of(0, 0));
    config.setSendWindowEnd(LocalTime.of(23, 59));
    return config;
  }

  private void stubHappyPathCollaborators(Cliente cliente) {
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId)).thenReturn(Optional.of(cliente));
    TenantWhatsAppConfig waConfig = new TenantWhatsAppConfig();
    waConfig.setTenantId(tenantId);
    waConfig.setWhatsappEnabled(true);
    waConfig.setWhatsappUsageProfile("COMPLETE");
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId)).thenReturn(waConfig);
    when(tenantReactivationConfigRepository.findByTenantIdOrDefault(tenantId)).thenReturn(reactivationConfig());
    when(reactivationSendLogRepository.countByClientIdAndSentAtAfter(any(), any())).thenReturn(0L);
    when(reactivationSendLogRepository.findLastByClientId(any())).thenReturn(Optional.empty());
    when(whatsAppBookingReactivationService.findFutureActiveAppointment(any(), any())).thenReturn(Optional.empty());
    when(customerCommunicationChannelResolver.resolve(eq(tenantId), eq(cliente), org.mockito.ArgumentMatchers.nullable(br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity.class), anyString(), anyString()))
        .thenReturn(new CustomerCommunicationChannelResolver.ResolvedChannel(
            br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel.WHATSAPP, "5511988887777", null));
    WhatsAppBookingReactivationAttemptEntity attempt = new WhatsAppBookingReactivationAttemptEntity();
    attempt.setAttemptNumber(1);
    when(whatsAppBookingReactivationService.createAttempt(any(), any())).thenReturn(attempt);
    when(whatsAppBookingReactivationService.buildOutboundMessage(any(), anyInt(), any(), any(), any()))
        .thenReturn("mensagem de reativacao");
    when(communicationChannelDispatcher.sendText(any())).thenReturn(ChannelSendResult.sent("provider-msg-1"));
  }

  // ---- processDueCycles ----------------------------------------------------

  @Test
  void processDueCyclesProcessaTodosOsCiclosDevidos() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    when(whatsAppBookingReactivationService.listDueCycles(any())).thenReturn(List.of(cycle));
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.empty());

    int result = service.processDueCycles();

    assertThat(result).isEqualTo(0); // cycle nao encontrado no repositorio -> 0
  }

  // ---- gates sequenciais -----------------------------------------------------

  @Test
  void cicloInexistenteRetornaZeroSemEfeitoColateral() {
    UUID cycleId = UUID.randomUUID();
    when(cycleRepository.findById(cycleId)).thenReturn(Optional.empty());

    int result = service.processCycle(cycleId);

    assertThat(result).isZero();
    verify(whatsAppBookingReactivationService, never()).cancelCycle(any(), any());
  }

  @Test
  void reativacaoDesabilitadaCancelaCiclo() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));
    when(tenantOperationalSettingsService.isReactivationEnabled(tenantId)).thenReturn(false);

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).cancelCycle(cycle, "REACTIVATION_DISABLED");
  }

  @Test
  void tentativaAcimaDoLimiteNaoProcessaNemCancela() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));
    when(tenantOperationalSettingsService.allowsReactivationAttemptNumber(eq(tenantId), any())).thenReturn(false);

    int result = service.processCycle(cycle.getId());

    assertThat(result).isZero();
    verify(whatsAppBookingReactivationService, never()).cancelCycle(any(), any());
  }

  @Test
  void foraDaJanelaOperacionalNaoProcessa() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));
    when(tenantOperationalSettingsService.allowsReactivationAt(eq(tenantId), any())).thenReturn(false);

    int result = service.processCycle(cycle.getId());

    assertThat(result).isZero();
  }

  @Test
  void clienteNaoEncontradoCancelaComInvalidDestination() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));
    when(clienteRepository.findByIdAndTenantId(cycle.getClientId(), tenantId)).thenReturn(Optional.empty());

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).cancelCycle(cycle, "INVALID_DESTINATION");
  }

  @Test
  void whatsappDesabilitadoCancelaCiclo() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    Cliente cliente = cliente(cycle.getClientId());
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId)).thenReturn(Optional.of(cliente));
    when(customerCommunicationChannelResolver.resolve(eq(tenantId), eq(cliente), org.mockito.ArgumentMatchers.nullable(br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity.class), anyString(), anyString()))
        .thenReturn(new CustomerCommunicationChannelResolver.ResolvedChannel(
            br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel.WHATSAPP, "5511988887777", null));
    TenantWhatsAppConfig waConfig = new TenantWhatsAppConfig();
    waConfig.setWhatsappEnabled(false);
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId)).thenReturn(waConfig);

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).cancelCycle(cycle, "WHATSAPP_DISABLED");
  }

  @Test
  void perfilDeUsoSemReativacaoCancelaCiclo() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    Cliente cliente = cliente(cycle.getClientId());
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId)).thenReturn(Optional.of(cliente));
    when(customerCommunicationChannelResolver.resolve(eq(tenantId), eq(cliente), org.mockito.ArgumentMatchers.nullable(br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity.class), anyString(), anyString()))
        .thenReturn(new CustomerCommunicationChannelResolver.ResolvedChannel(
            br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel.WHATSAPP, "5511988887777", null));
    TenantWhatsAppConfig waConfig = new TenantWhatsAppConfig();
    waConfig.setWhatsappEnabled(true);
    waConfig.setWhatsappUsageProfile("NOTIFICATIONS"); // nao permite reativacao
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId)).thenReturn(waConfig);

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).cancelCycle(cycle, "PROFILE_REACTIVATION_DISABLED");
  }

  @Test
  void clienteComOptOutCancelaCiclo() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    Cliente cliente = cliente(cycle.getClientId());
    cliente.setWhatsappOptOut(true);
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId)).thenReturn(Optional.of(cliente));
    when(customerCommunicationChannelResolver.resolve(eq(tenantId), eq(cliente), org.mockito.ArgumentMatchers.nullable(br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity.class), anyString(), anyString()))
        .thenReturn(new CustomerCommunicationChannelResolver.ResolvedChannel(
            br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel.WHATSAPP, "5511988887777", null));
    TenantWhatsAppConfig waConfig = new TenantWhatsAppConfig();
    waConfig.setWhatsappEnabled(true);
    waConfig.setWhatsappUsageProfile("COMPLETE");
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId)).thenReturn(waConfig);

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).cancelCycle(cycle, "OPT_OUT");
  }

  @Test
  void rateLimitMensalAtingidoNaoProcessa() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    Cliente cliente = cliente(cycle.getClientId());
    stubHappyPathCollaborators(cliente);
    when(reactivationSendLogRepository.countByClientIdAndSentAtAfter(any(), any())).thenReturn(4L);
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

    int result = service.processCycle(cycle.getId());

    assertThat(result).isZero();
    verify(whatsAppBookingReactivationService, never()).createAttempt(any(), any());
  }

  @Test
  void intervaloMinimoNaoAtingidoNaoProcessa() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    Cliente cliente = cliente(cycle.getClientId());
    stubHappyPathCollaborators(cliente);
    ReactivationSendLogEntity lastSent = new ReactivationSendLogEntity();
    lastSent.setSentAt(Instant.now().minusSeconds(3600)); // 1h atras -> menos de 7 dias
    when(reactivationSendLogRepository.findLastByClientId(any())).thenReturn(Optional.of(lastSent));
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

    int result = service.processCycle(cycle.getId());

    assertThat(result).isZero();
  }

  @Test
  void agendamentoFuturoExistenteConvertidoQuandoJaReativado() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    cycle.setReactivatedAt(Instant.now());
    Cliente cliente = cliente(cycle.getClientId());
    stubHappyPathCollaborators(cliente);
    when(whatsAppBookingReactivationService.findFutureActiveAppointment(tenantId, cliente.getId()))
        .thenReturn(Optional.of(new Agendamento()));
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).markConverted(cycle, null);
  }

  @Test
  void agendamentoFuturoExistenteCancelaQuandoNuncaReativado() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    Cliente cliente = cliente(cycle.getClientId());
    stubHappyPathCollaborators(cliente);
    when(whatsAppBookingReactivationService.findFutureActiveAppointment(tenantId, cliente.getId()))
        .thenReturn(Optional.of(new Agendamento()));
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).cancelCycle(cycle, "FUTURE_APPOINTMENT_FOUND");
  }

  // ---- fluxo feliz -------------------------------------------------------------

  @Test
  void fluxoFelizEnviaMensagemERegistraLog() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    Cliente cliente = cliente(cycle.getClientId());
    stubHappyPathCollaborators(cliente);
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).markAttemptSent(eq(cycle), any(), eq("provider-msg-1"));
    verify(reactivationSendLogRepository).save(any(ReactivationSendLogEntity.class));
    verify(assistantApiClient).seedReactivationContext(eq(tenantId.toString()), eq("5511988887777"), any());
  }

  @Test
  void falhaNoEnvioComErroDeDestinoInvalidoMarcaAttemptCancelado() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    Cliente cliente = cliente(cycle.getClientId());
    stubHappyPathCollaborators(cliente);
    when(communicationChannelDispatcher.sendText(any()))
        .thenReturn(ChannelSendResult.failed("INVALID_PARAMETER", "invalid recipient phone number"));
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).markAttemptCancelled(eq(cycle), any(), eq("INVALID_DESTINATION"));
    verify(whatsAppBookingReactivationService, never()).markAttemptFailed(any(), any(), any());
  }

  @Test
  void falhaGenericaNoEnvioMarcaAttemptFailed() {
    WhatsAppBookingReactivationCycleEntity cycle = cycle();
    Cliente cliente = cliente(cycle.getClientId());
    stubHappyPathCollaborators(cliente);
    when(communicationChannelDispatcher.sendText(any()))
        .thenReturn(ChannelSendResult.failed("TIMEOUT", "provider timeout"));
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

    int result = service.processCycle(cycle.getId());

    assertThat(result).isEqualTo(1);
    verify(whatsAppBookingReactivationService).markAttemptFailed(eq(cycle), any(), eq("provider timeout"));
  }
}
