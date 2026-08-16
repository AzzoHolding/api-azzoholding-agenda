package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantReactivationSeedRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationAttemptEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationAttemptStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationAttemptRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;

/**
 * Espelha os cenarios equivalentes de
 * {@code WhatsAppBookingReactivationServiceUnitTest} do original (agora que o porte completo,
 * incluindo {@code syncAfterAssistantTurn}, esta fechado).
 */
class WhatsAppBookingReactivationServiceTest {

  private WhatsAppBookingReactivationCycleRepository cycleRepository;
  private WhatsAppBookingReactivationAttemptRepository attemptRepository;
  private AgendamentoRepository agendamentoRepository;
  private WhatsAppBookingReactivationObservabilityService observabilityService;
  private WhatsAppBookingReactivationService service;

  @BeforeEach
  void setUp() {
    cycleRepository = mock(WhatsAppBookingReactivationCycleRepository.class);
    attemptRepository = mock(WhatsAppBookingReactivationAttemptRepository.class);
    agendamentoRepository = mock(AgendamentoRepository.class);
    observabilityService = mock(WhatsAppBookingReactivationObservabilityService.class);
    when(cycleRepository.findLatestByTenantAndClient(any(), any(), anyList())).thenReturn(Optional.empty());
    when(cycleRepository.findLatestByTenantAndUserIdentifiers(any(), anyList(), anyList()))
        .thenReturn(Optional.empty());
    when(cycleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    service = new WhatsAppBookingReactivationService(
        cycleRepository, attemptRepository, agendamentoRepository, new ObjectMapper(), observabilityService, 30);
  }

  // ─── markClientReplyIfNeeded ────────────────────────────────────────────

  @Test
  void deveMarcarReativadoQuandoJaHouveTentativaEnviada() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setClientId(clientId);
    cycle.setTenantId(tenantId);
    cycle.setUserIdentifier("5511999991111");
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    when(cycleRepository.findLatestByTenantAndClient(eq(tenantId), eq(clientId), anyList()))
        .thenReturn(Optional.of(cycle));

    WhatsAppBookingReactivationAttemptEntity sentAttempt = new WhatsAppBookingReactivationAttemptEntity();
    sentAttempt.setStatus(WhatsAppBookingReactivationAttemptStatus.SENT);
    when(attemptRepository.findLatestSentAttempt(cycle.getId())).thenReturn(Optional.of(sentAttempt));

    service.markClientReplyIfNeeded(tenantId, clientId, "+55 (11) 99999-1111");

    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.REACTIVATED);
    assertThat(cycle.getReactivatedAt()).isNotNull();
    assertThat(cycle.getRespondedAt()).isNotNull();
    assertThat(cycle.getCancelReason()).isEqualTo("CLIENT_REPLIED");
    assertThat(cycle.getNextAttemptAt()).isNull();
  }

  @Test
  void deveCancelarCicloPendenteQuandoClienteResponderAntesDaPrimeiraTentativa() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setClientId(clientId);
    cycle.setTenantId(tenantId);
    cycle.setUserIdentifier("5511999991111");
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    cycle.setAbandonedAt(Instant.now().plusSeconds(1800));
    cycle.setNextAttemptAt(cycle.getAbandonedAt().plusSeconds(172800));
    when(cycleRepository.findLatestByTenantAndClient(eq(tenantId), eq(clientId), anyList()))
        .thenReturn(Optional.of(cycle));
    when(attemptRepository.findLatestSentAttempt(cycle.getId())).thenReturn(Optional.empty());

    service.markClientReplyIfNeeded(tenantId, clientId, "+55 (11) 99999-1111");

    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.CANCELLED);
    assertThat(cycle.getCancelReason()).isEqualTo("CLIENT_REPLIED_BEFORE_ABANDONMENT");
    assertThat(cycle.getRespondedAt()).isNotNull();
  }

  @Test
  void deveReaproveitarCicloPorVarianteDeTelefoneQuandoNaoEncontrarPorCliente() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setClientId(clientId);
    cycle.setTenantId(tenantId);
    cycle.setUserIdentifier("21987599613");
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    when(cycleRepository.findLatestByTenantAndClient(eq(tenantId), eq(clientId), anyList()))
        .thenReturn(Optional.empty());
    when(cycleRepository.findLatestByTenantAndUserIdentifiers(eq(tenantId), anyList(), anyList()))
        .thenReturn(Optional.of(cycle));
    when(attemptRepository.findLatestSentAttempt(cycle.getId())).thenReturn(Optional.empty());

    service.markClientReplyIfNeeded(tenantId, clientId, "5521987599613");

    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.CANCELLED);
  }

  @Test
  void naoFazNadaQuandoNaoHaCicloRelevante() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();

    service.markClientReplyIfNeeded(tenantId, clientId, "5511999991111");

    verify(attemptRepository, never()).findLatestSentAttempt(any());
  }

  @Test
  void naoFazNadaQuandoTenantOuClientIdAusente() {
    service.markClientReplyIfNeeded(null, UUID.randomUUID(), "5511999991111");
    service.markClientReplyIfNeeded(UUID.randomUUID(), null, "5511999991111");

    verify(cycleRepository, never()).findLatestByTenantAndClient(any(), any(), anyList());
  }

  @Test
  void cancelCyclesForManualModeCancelaTodosCiclosAtivosDoCliente() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setTenantId(tenantId);
    conversation.setClientId(clientId);
    conversation.setChannel(ChatChannel.WHATSAPP);

    WhatsAppBookingReactivationCycleEntity cycle1 = new WhatsAppBookingReactivationCycleEntity();
    cycle1.setId(UUID.randomUUID());
    cycle1.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    WhatsAppBookingReactivationCycleEntity cycle2 = new WhatsAppBookingReactivationCycleEntity();
    cycle2.setId(UUID.randomUUID());
    cycle2.setStatus(WhatsAppBookingReactivationStatus.REACTIVATED);
    when(cycleRepository.listActiveByTenantAndClient(tenantId, clientId)).thenReturn(List.of(cycle1, cycle2));

    service.cancelCyclesForManualMode(conversation);

    assertThat(cycle1.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.CANCELLED);
    assertThat(cycle1.getCancelReason()).isEqualTo("MANUAL_MODE");
    assertThat(cycle2.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.CANCELLED);
    assertThat(cycle2.getCancelReason()).isEqualTo("MANUAL_MODE");
  }

  @Test
  void cancelCyclesForManualModeIgnoraConversaSemTenantOuCliente() {
    service.cancelCyclesForManualMode(null);
    service.cancelCyclesForManualMode(new ChatConversationEntity());

    verify(cycleRepository, never()).listActiveByTenantAndClient(any(), any());
  }

  // ─── syncAfterAssistantTurn ─────────────────────────────────────────────

  @Test
  void deveCriarCicloAtivoComProximaTentativaEmD2() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    UUID professionalId = UUID.randomUUID();

    AssistantMessageResponse response = assistantResponse(
        "ASK_TIME",
        "Qual horario voce prefere?",
        mapOf(
            "serviceId", serviceId.toString(),
            "serviceName", "Corte",
            "professionalId", professionalId.toString(),
            "professionalName", "Maria",
            "date", "2026-04-10",
            "time", "14:00"));

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(conversationId, tenantId, clientId),
        client(clientId, tenantId, "Maria Silva", "+55 (11) 99999-1111"),
        "quero as 14h",
        response);

    ArgumentCaptor<WhatsAppBookingReactivationCycleEntity> captor =
        ArgumentCaptor.forClass(WhatsAppBookingReactivationCycleEntity.class);
    verify(cycleRepository).save(captor.capture());
    WhatsAppBookingReactivationCycleEntity cycle = captor.getValue();

    assertThat(cycle.getTenantId()).isEqualTo(tenantId);
    assertThat(cycle.getClientId()).isEqualTo(clientId);
    assertThat(cycle.getConversationId()).isEqualTo(conversationId);
    assertThat(cycle.getUserIdentifier()).isEqualTo("5511999991111");
    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.ACTIVE);
    assertThat(cycle.getLastStage()).isEqualTo(WhatsAppBookingReactivationStage.TIME_SELECTION);
    assertThat(cycle.getLastServiceId()).isEqualTo(serviceId);
    assertThat(cycle.getLastProfessionalId()).isEqualTo(professionalId);
    assertThat(cycle.getLastRequestedDate()).isEqualTo(LocalDate.parse("2026-04-10"));
    assertThat(cycle.getLastRequestedTime()).isEqualTo("14:00");
    assertThat(cycle.getNextAttemptNumber()).isEqualTo(1);
    assertThat(cycle.getAbandonedAt()).isNotNull();
    assertThat(cycle.getNextAttemptAt()).isEqualTo(cycle.getAbandonedAt().plusSeconds(2L * 24L * 60L * 60L));
    assertThat(cycle.getConversationContextJson()).contains("\"stage\":\"TIME_SELECTION\"");
    assertThat(cycle.getConversationContextJson()).contains("\"customerLastMessage\":\"quero as 14h\"");
  }

  @Test
  void deveMarcarConvertidoQuandoFluxoCompletarAposReativacao() {
    UUID tenantId = UUID.randomUUID();
    UUID appointmentId = UUID.randomUUID();
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setTenantId(tenantId);
    cycle.setUserIdentifier("5511999991111");
    cycle.setStatus(WhatsAppBookingReactivationStatus.REACTIVATED);
    cycle.setReactivatedAt(Instant.now().minusSeconds(60));
    when(cycleRepository.findLatestByTenantAndClient(eq(tenantId), any(), anyList())).thenReturn(Optional.of(cycle));

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(UUID.randomUUID(), tenantId, UUID.randomUUID()),
        client(UUID.randomUUID(), tenantId, "Maria Silva", "5511999991111"),
        "sim",
        assistantResponse("COMPLETED", "Agendamento confirmado", mapOf("appointmentId", appointmentId.toString())));

    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.CONVERTED);
    assertThat(cycle.getAppointmentIdCreatedAfterAbandonment()).isEqualTo(appointmentId);
    assertThat(cycle.getConvertedAt()).isNotNull();
    assertThat(cycle.getNextAttemptAt()).isNull();
    verify(cycleRepository, never()).save(any());
  }

  @Test
  void deveCancelarCicloQuandoCompletarSemTerSidoReativado() {
    UUID tenantId = UUID.randomUUID();
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setTenantId(tenantId);
    cycle.setUserIdentifier("5511999991111");
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    when(cycleRepository.findLatestByTenantAndClient(eq(tenantId), any(), anyList())).thenReturn(Optional.of(cycle));

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(UUID.randomUUID(), tenantId, UUID.randomUUID()),
        client(UUID.randomUUID(), tenantId, "Maria Silva", "5511999991111"),
        "sim",
        assistantResponse("COMPLETED", "Agendamento confirmado", mapOf()));

    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.CANCELLED);
    assertThat(cycle.getCancelReason()).isEqualTo("SYSTEM_RULE");
  }

  @Test
  void deveIgnorarEstagiosDeCancelamentoEReagendamento() {
    UUID tenantId = UUID.randomUUID();

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(UUID.randomUUID(), tenantId, UUID.randomUUID()),
        client(UUID.randomUUID(), tenantId, "Maria Silva", "5511999991111"),
        "quero cancelar",
        assistantResponse("ASK_CANCEL_APPOINTMENT", "Confirma o cancelamento?", mapOf()));

    verify(cycleRepository, never()).save(any());
    verify(observabilityService).logCycleSkipped(eq(tenantId), any(), any(), any(), eq("IGNORED_STAGE"));
  }

  @Test
  void naoCriaCicloParaClienteComOptOutRegistrado() {
    UUID tenantId = UUID.randomUUID();
    Cliente cliente = client(UUID.randomUUID(), tenantId, "Maria Silva", "5511999991111");
    cliente.setWhatsappOptOut(true);

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(UUID.randomUUID(), tenantId, cliente.getId()),
        cliente,
        "quero agendar",
        assistantResponse("ASK_SERVICE", "Qual servico?", mapOf()));

    verify(cycleRepository, never()).save(any());
    verify(observabilityService).logCycleSkipped(eq(tenantId), any(), any(), any(), eq("OPT_OUT"));
  }

  @Test
  void naoFazNadaQuandoUserIdentifierVazio() {
    UUID tenantId = UUID.randomUUID();

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(UUID.randomUUID(), tenantId, UUID.randomUUID()),
        client(UUID.randomUUID(), tenantId, "Maria Silva", null),
        "oi",
        assistantResponse("ASK_SERVICE", "Qual servico?", mapOf()));

    verify(cycleRepository, never()).save(any());
    verify(observabilityService).logCycleSkipped(eq(tenantId), eq(null), any(), eq(null), eq("EMPTY_USER_IDENTIFIER"));
  }

  @Test
  void deveReaproveitarCicloCanceladoRecenteSemCriarNovaLinha() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Instant now = Instant.now();

    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setClientId(clientId);
    cycle.setTenantId(tenantId);
    cycle.setUserIdentifier("5511999991111");
    cycle.setStatus(WhatsAppBookingReactivationStatus.CANCELLED);
    cycle.setCancelReason("CLIENT_REPLIED_BEFORE_ABANDONMENT");
    cycle.setAbandonedAt(now.minusSeconds(60));
    cycle.setUpdatedAt(now.minusSeconds(60));
    cycle.setRespondedAt(now.minusSeconds(60));
    cycle.setNextAttemptAt(now.plusSeconds(300));
    cycle.setNextAttemptNumber(2);
    when(cycleRepository.findLatestByTenantAndClient(eq(tenantId), eq(clientId), anyList()))
        .thenReturn(Optional.of(cycle));

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(conversationId, tenantId, clientId),
        client(clientId, tenantId, "Maria Silva", "+55 (11) 99999-1111"),
        "quero continuar",
        assistantResponse("ASK_SERVICE", "Qual servico?", mapOf()));

    verify(cycleRepository, never()).save(any());
    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.ACTIVE);
    assertThat(cycle.getLastStage()).isEqualTo(WhatsAppBookingReactivationStage.SERVICE_SELECTION);
    assertThat(cycle.getConversationId()).isEqualTo(conversationId);
    assertThat(cycle.getNextAttemptNumber()).isEqualTo(1);
    assertThat(cycle.getCancelReason()).isNull();
    assertThat(cycle.getRespondedAt()).isNull();
    assertThat(cycle.getAbandonedAt()).isNotNull();
    assertThat(cycle.getNextAttemptAt()).isEqualTo(cycle.getAbandonedAt().plusSeconds(2L * 24L * 60L * 60L));
  }

  @Test
  void deveCriarNovoCicloQuandoCanceladoForMaisAntigoQueJanelaDe30Minutos() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Instant now = Instant.now();

    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setClientId(clientId);
    cycle.setTenantId(tenantId);
    cycle.setUserIdentifier("5511999991111");
    cycle.setStatus(WhatsAppBookingReactivationStatus.CANCELLED);
    cycle.setCancelReason("CLIENT_REPLIED_BEFORE_ABANDONMENT");
    cycle.setAbandonedAt(now.minusSeconds(31L * 60L));
    cycle.setUpdatedAt(now.minusSeconds(31L * 60L));
    cycle.setRespondedAt(now.minusSeconds(31L * 60L));
    when(cycleRepository.findLatestByTenantAndClient(eq(tenantId), eq(clientId), anyList()))
        .thenReturn(Optional.of(cycle));

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(conversationId, tenantId, clientId),
        client(clientId, tenantId, "Maria Silva", "+55 (11) 99999-1111"),
        "quero continuar",
        assistantResponse("ASK_SERVICE", "Qual servico?", mapOf()));

    ArgumentCaptor<WhatsAppBookingReactivationCycleEntity> captor =
        ArgumentCaptor.forClass(WhatsAppBookingReactivationCycleEntity.class);
    verify(cycleRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(captor.getValue().getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.ACTIVE);
    assertThat(captor.getValue().getLastStage()).isEqualTo(WhatsAppBookingReactivationStage.SERVICE_SELECTION);
    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.CANCELLED);
    assertThat(cycle.getCancelReason()).isEqualTo("CLIENT_REPLIED_BEFORE_ABANDONMENT");
  }

  @Test
  void deveAbrirCicloParaLeadQualificadoMesmoQuandoAssistenteAindaPedirNome() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();

    AssistantMessageResponse response = assistantResponse(
        "ASK_NAME",
        "Oi! Pra comecar, me conta seu nome completo.",
        mapOf(
            "bookingLeadDetected", true,
            "bookingLeadServiceId", serviceId.toString(),
            "bookingLeadServiceName", "Corte",
            "bookingLeadDate", "2026-03-28"));

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(conversationId, tenantId, clientId),
        client(clientId, tenantId, "Maria Silva", "+55 (11) 99999-1111"),
        "quero agendar para hoje um corte de cabelo",
        response);

    ArgumentCaptor<WhatsAppBookingReactivationCycleEntity> captor =
        ArgumentCaptor.forClass(WhatsAppBookingReactivationCycleEntity.class);
    verify(cycleRepository).save(captor.capture());
    assertThat(captor.getValue().getLastStage()).isEqualTo(WhatsAppBookingReactivationStage.SERVICE_SELECTION);
    assertThat(captor.getValue().getLastServiceId()).isEqualTo(serviceId);
    assertThat(captor.getValue().getLastServiceName()).isEqualTo("Corte");
    assertThat(captor.getValue().getLastRequestedDate()).isEqualTo(LocalDate.parse("2026-03-28"));
  }

  @Test
  void devePersistirSinalDeIntervencaoManualNoContextoDoCiclo() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();

    AssistantMessageResponse response = assistantResponse(
        "ASK_SERVICE",
        "Me diga qual servico voce quer agendar.\n\nQuer falar com uma atendente?",
        mapOf(
            "manualInterventionSuggested", true,
            "manualInterventionReason", "STAGE_RETRY_LIMIT",
            "manualInterventionAttempts", 4));

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(UUID.randomUUID(), tenantId, clientId),
        client(clientId, tenantId, "Maria Silva", "+55 (11) 99999-1111"),
        "nao sei",
        response);

    ArgumentCaptor<WhatsAppBookingReactivationCycleEntity> captor =
        ArgumentCaptor.forClass(WhatsAppBookingReactivationCycleEntity.class);
    verify(cycleRepository).save(captor.capture());
    String json = captor.getValue().getConversationContextJson();
    assertThat(json).contains("\"manualInterventionSuggested\":true");
    assertThat(json).contains("\"manualInterventionReason\":\"STAGE_RETRY_LIMIT\"");
    assertThat(json).contains("\"manualInterventionAttempts\":4");
  }

  @Test
  void devePreservarSinaisOperacionaisJaCapturadosQuandoRespostaNaoTrouxerSlotsNovos() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    UUID professionalId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setTenantId(tenantId);
    cycle.setClientId(clientId);
    cycle.setUserIdentifier("5511999991111");
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    cycle.setLastStage(WhatsAppBookingReactivationStage.PROFESSIONAL_SELECTION);
    cycle.setLastServiceId(serviceId);
    cycle.setLastServiceName("Corte feminino");
    cycle.setLastProfessionalId(professionalId);
    cycle.setLastProfessionalName("Ana Paula");
    cycle.setLastRequestedDate(LocalDate.parse("2026-04-10"));
    cycle.setLastRequestedTime("14:00");
    when(cycleRepository.findLatestByTenantAndClient(eq(tenantId), eq(clientId), anyList()))
        .thenReturn(Optional.of(cycle));

    service.syncAfterAssistantTurn(
        tenantId,
        conversation(conversationId, tenantId, clientId),
        client(clientId, tenantId, "Maria Silva", "+55 (11) 99999-1111"),
        "sim",
        assistantResponse("ASK_PROFESSIONAL", "Quem voce prefere?", mapOf()));

    verify(cycleRepository, never()).save(any());
    assertThat(cycle.getConversationId()).isEqualTo(conversationId);
    assertThat(cycle.getLastStage()).isEqualTo(WhatsAppBookingReactivationStage.PROFESSIONAL_SELECTION);
    assertThat(cycle.getLastServiceId()).isEqualTo(serviceId);
    assertThat(cycle.getLastServiceName()).isEqualTo("Corte feminino");
    assertThat(cycle.getLastProfessionalId()).isEqualTo(professionalId);
    assertThat(cycle.getLastProfessionalName()).isEqualTo("Ana Paula");
    assertThat(cycle.getLastRequestedDate()).isEqualTo(LocalDate.parse("2026-04-10"));
    assertThat(cycle.getLastRequestedTime()).isEqualTo("14:00");
    assertThat(cycle.getCustomerLastMessage()).isEqualTo("sim");
  }

  @Test
  void naoFazNadaQuandoArgumentosObrigatoriosAusentes() {
    UUID tenantId = UUID.randomUUID();
    service.syncAfterAssistantTurn(null, conversation(UUID.randomUUID(), tenantId, UUID.randomUUID()),
        client(UUID.randomUUID(), tenantId, "Maria", "5511999991111"), "oi",
        assistantResponse("ASK_SERVICE", "Qual servico?", mapOf()));
    service.syncAfterAssistantTurn(tenantId, null,
        client(UUID.randomUUID(), tenantId, "Maria", "5511999991111"), "oi",
        assistantResponse("ASK_SERVICE", "Qual servico?", mapOf()));
    service.syncAfterAssistantTurn(tenantId, conversation(UUID.randomUUID(), tenantId, UUID.randomUUID()),
        null, "oi", assistantResponse("ASK_SERVICE", "Qual servico?", mapOf()));
    service.syncAfterAssistantTurn(tenantId, conversation(UUID.randomUUID(), tenantId, UUID.randomUUID()),
        client(UUID.randomUUID(), tenantId, "Maria", "5511999991111"), "oi", null);

    verify(cycleRepository, never()).save(any());
  }

  // ─── tentativas ─────────────────────────────────────────────────────────

  @Test
  void deveEvoluirTentativasParaD4D7EExaustao() {
    Instant abandonedAt = Instant.parse("2026-03-27T12:00:00Z");
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setTenantId(UUID.randomUUID());
    cycle.setAbandonedAt(abandonedAt);
    cycle.setNextAttemptNumber(1);
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);

    WhatsAppBookingReactivationAttemptEntity firstAttempt = new WhatsAppBookingReactivationAttemptEntity();
    service.markAttemptSent(cycle, firstAttempt, "wamid-1");

    assertThat(firstAttempt.getStatus()).isEqualTo(WhatsAppBookingReactivationAttemptStatus.SENT);
    assertThat(cycle.getNextAttemptNumber()).isEqualTo(2);
    assertThat(cycle.getNextAttemptAt()).isEqualTo(abandonedAt.plusSeconds(4L * 24L * 60L * 60L));

    WhatsAppBookingReactivationAttemptEntity secondAttempt = new WhatsAppBookingReactivationAttemptEntity();
    service.markAttemptSent(cycle, secondAttempt, "wamid-2");

    assertThat(cycle.getNextAttemptNumber()).isEqualTo(3);
    assertThat(cycle.getNextAttemptAt()).isEqualTo(abandonedAt.plusSeconds(7L * 24L * 60L * 60L));

    WhatsAppBookingReactivationAttemptEntity thirdAttempt = new WhatsAppBookingReactivationAttemptEntity();
    service.markAttemptSent(cycle, thirdAttempt, "wamid-3");

    assertThat(cycle.getNextAttemptNumber()).isEqualTo(4);
    assertThat(cycle.getNextAttemptAt()).isNull();
    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.EXHAUSTED);
  }

  @Test
  void markAttemptFailedTambemAvancaCicloEExaure() {
    Instant abandonedAt = Instant.now();
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setAbandonedAt(abandonedAt);
    cycle.setNextAttemptNumber(3);
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    WhatsAppBookingReactivationAttemptEntity attempt = new WhatsAppBookingReactivationAttemptEntity();

    service.markAttemptFailed(cycle, attempt, "timeout");

    assertThat(attempt.getStatus()).isEqualTo(WhatsAppBookingReactivationAttemptStatus.FAILED);
    assertThat(attempt.getErrorMessage()).isEqualTo("timeout");
    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.EXHAUSTED);
    assertThat(cycle.getNextAttemptAt()).isNull();
  }

  @Test
  void deveCancelarTentativaQuandoDestinoForInvalido() {
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    cycle.setNextAttemptAt(Instant.now());

    WhatsAppBookingReactivationAttemptEntity attempt = new WhatsAppBookingReactivationAttemptEntity();
    attempt.setStatus(WhatsAppBookingReactivationAttemptStatus.PENDING);

    service.markAttemptCancelled(cycle, attempt, "INVALID_DESTINATION");

    assertThat(cycle.getStatus()).isEqualTo(WhatsAppBookingReactivationStatus.CANCELLED);
    assertThat(cycle.getCancelReason()).isEqualTo("INVALID_DESTINATION");
    assertThat(cycle.getNextAttemptAt()).isNull();
    assertThat(attempt.getStatus()).isEqualTo(WhatsAppBookingReactivationAttemptStatus.CANCELLED);
    assertThat(attempt.getErrorMessage()).isEqualTo("INVALID_DESTINATION");
  }

  @Test
  void createAttemptPersisteComProximoNumeroDoCiclo() {
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setTenantId(UUID.randomUUID());
    cycle.setNextAttemptNumber(2);
    Instant scheduledFor = Instant.now().plusSeconds(60);

    WhatsAppBookingReactivationAttemptEntity attempt = service.createAttempt(cycle, scheduledFor);

    assertThat(attempt.getCycleId()).isEqualTo(cycle.getId());
    assertThat(attempt.getTenantId()).isEqualTo(cycle.getTenantId());
    assertThat(attempt.getAttemptNumber()).isEqualTo(2);
    assertThat(attempt.getScheduledFor()).isEqualTo(scheduledFor);
    assertThat(attempt.getStatus()).isEqualTo(WhatsAppBookingReactivationAttemptStatus.PENDING);
    verify(attemptRepository).save(attempt);
  }

  // ─── retomada/mensagem/consulta ─────────────────────────────────────────

  @Test
  void toAssistantSeedRequestMapeiaEstagioDeRetomada() {
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setCustomerName("Maria Silva");
    cycle.setLastStage(WhatsAppBookingReactivationStage.PROFESSIONAL_SELECTION);
    cycle.setLastServiceId(UUID.randomUUID());
    cycle.setLastServiceName("Corte");
    cycle.setAssistantLastPrompt("Quem voce prefere?");

    AssistantReactivationSeedRequest seed = service.toAssistantSeedRequest(cycle);

    assertThat(seed.cycleId).isEqualTo(cycle.getId().toString());
    assertThat(seed.customerName).isEqualTo("Maria Silva");
    assertThat(seed.resumeStage).isEqualTo("ASK_PROFESSIONAL");
    assertThat(seed.serviceName).isEqualTo("Corte");
    assertThat(seed.assistantLastPrompt).isEqualTo("Quem voce prefere?");
  }

  @Test
  void buildOutboundMessageUsaTemplateConfiguradoQuandoInformado() {
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setCustomerName("Maria Silva");
    cycle.setLastStage(WhatsAppBookingReactivationStage.SERVICE_SELECTION);

    String message = service.buildOutboundMessage(cycle, 1, "Oi {nome}, tudo bem?", null, null);

    assertThat(message).isEqualTo("Oi Maria, tudo bem?");
  }

  @Test
  void buildOutboundMessageUsaMensagemPadraoPorEstagioQuandoSemTemplate() {
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setCustomerName("Maria Silva");
    cycle.setLastStage(WhatsAppBookingReactivationStage.FINAL_REVIEW);

    String message = service.buildOutboundMessage(cycle, 3);

    assertThat(message).contains("Maria Silva").contains("ultima mensagem");
  }

  @Test
  void findFutureActiveAppointmentDelegaParaAgendamentoRepository() {
    UUID tenantId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    Optional<Agendamento> expected = Optional.of(new Agendamento());
    when(agendamentoRepository.findFirstFutureActiveForClient(eq(tenantId), eq(clientId), any(), any()))
        .thenReturn(expected);

    assertThat(service.findFutureActiveAppointment(tenantId, clientId)).isSameAs(expected);
  }

  @Test
  void listDueCyclesDelegaParaCycleRepository() {
    Instant now = Instant.now();
    List<WhatsAppBookingReactivationCycleEntity> due = List.of(new WhatsAppBookingReactivationCycleEntity());
    when(cycleRepository.listDue(now)).thenReturn(due);

    assertThat(service.listDueCycles(now)).isSameAs(due);
  }

  @Test
  void deveMascararUserIdentifierNosLogs() {
    when(observabilityService.maskUserIdentifier("+55 (11) 99999-1111")).thenReturn("5511*******11");

    assertThat(service.maskUserIdentifier("+55 (11) 99999-1111")).isEqualTo("5511*******11");
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private static ChatConversationEntity conversation(UUID id, UUID tenantId, UUID clientId) {
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setId(id);
    conversation.setTenantId(tenantId);
    conversation.setClientId(clientId);
    return conversation;
  }

  private static Cliente client(UUID id, UUID tenantId, String name, String phone) {
    Cliente client = new Cliente();
    client.setId(id);
    client.setTenantId(tenantId);
    client.setName(name);
    client.setPhone(phone);
    return client;
  }

  private static AssistantMessageResponse assistantResponse(
      String stage, String reply, LinkedHashMap<String, Object> slots) {
    AssistantMessageResponse response = new AssistantMessageResponse();
    response.stage = AssistantStage.from(stage);
    response.reply = reply;
    response.slots = slots;
    return response;
  }

  private static LinkedHashMap<String, Object> mapOf(Object... keyValues) {
    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }
}
