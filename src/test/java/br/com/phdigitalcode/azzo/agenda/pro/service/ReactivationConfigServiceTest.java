package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.OptOutPagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.ReactivationCyclesPagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.ReactivationMetricsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.TenantReactivationConfigResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.UpdateReactivationConfigRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationConsentHistoryEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantReactivationConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStatus;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationConsentHistoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationSendLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantReactivationConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;

/**
 * Espelha o contrato de {@code modules/chat/application/ReactivationConfigService.java}: limites
 * LGPD aplicados por clamp (nao rejeicao), opt-out/opt-in idempotentes, cancelamento de ciclo
 * restrito a ACTIVE/REACTIVATED.
 */
class ReactivationConfigServiceTest {

  private final UUID tenantId = UUID.randomUUID();
  private final UUID actorUserId = UUID.randomUUID();

  private TenantReactivationConfigRepository configRepository;
  private ClienteRepository clienteRepository;
  private ReactivationConsentHistoryRepository consentHistoryRepository;
  private WhatsAppBookingReactivationCycleRepository cycleRepository;
  private WhatsAppBookingReactivationService reactivationService;
  private ReactivationSendLogRepository sendLogRepository;
  private AuditService auditService;
  private ReactivationConfigService service;

  @BeforeEach
  void setUp() {
    configRepository = mock(TenantReactivationConfigRepository.class);
    clienteRepository = mock(ClienteRepository.class);
    consentHistoryRepository = mock(ReactivationConsentHistoryRepository.class);
    cycleRepository = mock(WhatsAppBookingReactivationCycleRepository.class);
    reactivationService = mock(WhatsAppBookingReactivationService.class);
    sendLogRepository = mock(ReactivationSendLogRepository.class);
    auditService = mock(AuditService.class);
    service = new ReactivationConfigService(
        configRepository, clienteRepository, consentHistoryRepository, cycleRepository,
        reactivationService, sendLogRepository, auditService);

    when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private Cliente cliente() {
    Cliente c = new Cliente();
    c.setId(UUID.randomUUID());
    c.setTenantId(tenantId);
    c.setName("Joana Silva");
    return c;
  }

  // ---- getConfig / updateConfig -------------------------------------------

  @Test
  void getConfigDevolveDefaultQuandoNaoExisteRegistro() {
    TenantReactivationConfigEntity defaultEntity = new TenantReactivationConfigEntity();
    defaultEntity.setTenantId(tenantId);
    when(configRepository.findByTenantIdOrDefault(tenantId)).thenReturn(defaultEntity);

    TenantReactivationConfigResponse response = service.getConfig(tenantId);

    assertThat(response.tenantId).isEqualTo(tenantId);
    assertThat(response.enabled).isTrue();
    assertThat(response.maxAttempts).isEqualTo(3);
  }

  @Test
  void updateConfigAplicaLimiteLgpdDeMaxMensagensPorMes() {
    when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
    UpdateReactivationConfigRequest request = new UpdateReactivationConfigRequest();
    request.maxMessagesPerMonthPerClient = 999; // acima do teto LGPD -> clamp para 4

    TenantReactivationConfigResponse response = service.updateConfig(tenantId, actorUserId, "OWNER", request);

    assertThat(response.maxMessagesPerMonthPerClient).isEqualTo(4);
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void updateConfigAplicaLimiteLgpdDeIntervaloMinimo() {
    when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
    UpdateReactivationConfigRequest request = new UpdateReactivationConfigRequest();
    request.minIntervalDays = 1; // abaixo do minimo LGPD -> clamp para 7

    TenantReactivationConfigResponse response = service.updateConfig(tenantId, actorUserId, "OWNER", request);

    assertThat(response.minIntervalDays).isEqualTo(7);
  }

  @Test
  void updateConfigIgnoraAbandonmentDelayAbaixoDe15() {
    TenantReactivationConfigEntity existing = new TenantReactivationConfigEntity();
    existing.setTenantId(tenantId);
    existing.setAbandonmentDelayMinutes(30);
    when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

    UpdateReactivationConfigRequest request = new UpdateReactivationConfigRequest();
    request.abandonmentDelayMinutes = 5; // abaixo do minimo -> ignorado, mantem 30

    TenantReactivationConfigResponse response = service.updateConfig(tenantId, actorUserId, "OWNER", request);

    assertThat(response.abandonmentDelayMinutes).isEqualTo(30);
  }

  @Test
  void updateConfigLimpaTemplateEmBrancoParaNull() {
    when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
    UpdateReactivationConfigRequest request = new UpdateReactivationConfigRequest();
    request.templateAttempt1 = "   ";

    TenantReactivationConfigResponse response = service.updateConfig(tenantId, actorUserId, "OWNER", request);

    assertThat(response.templateAttempt1).isNull();
  }

  // ---- registrarOptOut / registrarOptIn -----------------------------------

  @Test
  void registrarOptOutClienteNaoEncontradoLanca404() {
    UUID clientId = UUID.randomUUID();
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.registrarOptOut(tenantId, clientId, actorUserId, "OWNER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Cliente nao encontrado");
  }

  @Test
  void registrarOptOutJaRegistradoLanca409() {
    Cliente cliente = cliente();
    cliente.setWhatsappOptOut(true);
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId)).thenReturn(Optional.of(cliente));

    assertThatThrownBy(() -> service.registrarOptOut(tenantId, cliente.getId(), actorUserId, "OWNER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("ja possui opt-out");
  }

  @Test
  void registrarOptOutComSucessoCancelaCiclosAtivos() {
    Cliente cliente = cliente();
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId)).thenReturn(Optional.of(cliente));
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    when(cycleRepository.listActiveByTenantAndClient(tenantId, cliente.getId())).thenReturn(List.of(cycle));

    service.registrarOptOut(tenantId, cliente.getId(), actorUserId, "OWNER");

    assertThat(cliente.getWhatsappOptOut()).isTrue();
    assertThat(cliente.getWhatsappOptIn()).isFalse();
    verify(clienteRepository).save(cliente);
    verify(consentHistoryRepository).save(any(ReactivationConsentHistoryEntity.class));
    verify(reactivationService).cancelCycle(cycle, "OPT_OUT");
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void registrarOptInClienteNaoEncontradoLanca404() {
    UUID clientId = UUID.randomUUID();
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.registrarOptIn(tenantId, clientId, actorUserId, "OWNER"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void registrarOptInComSucessoLimpaOptOut() {
    Cliente cliente = cliente();
    cliente.setWhatsappOptOut(true);
    cliente.setWhatsappOptOutAt(Instant.now());
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId)).thenReturn(Optional.of(cliente));

    service.registrarOptIn(tenantId, cliente.getId(), actorUserId, "OWNER");

    assertThat(cliente.getWhatsappOptIn()).isTrue();
    assertThat(cliente.getWhatsappOptOut()).isFalse();
    assertThat(cliente.getWhatsappOptOutAt()).isNull();
    verify(consentHistoryRepository).save(any(ReactivationConsentHistoryEntity.class));
  }

  // ---- listOptOutsPaged -----------------------------------------------------

  @Test
  void listOptOutsPagedMascaraNomeEResolveSourceDoHistorico() {
    Cliente cliente = cliente();
    cliente.setWhatsappOptOut(true);
    cliente.setWhatsappOptOutAt(Instant.now());
    Page<Cliente> page = new PageImpl<>(List.of(cliente), PageRequest.of(0, 20), 1);
    when(clienteRepository.findByTenantIdAndWhatsappOptOutTrueOrderByWhatsappOptOutAtDesc(eq(tenantId), any()))
        .thenReturn(page);

    ReactivationConsentHistoryEntity consent = new ReactivationConsentHistoryEntity();
    consent.setClientId(cliente.getId());
    consent.setSource("WHATSAPP_REPLY");
    when(consentHistoryRepository.findByClientIdInAndTenantIdAndActionOrderByCreatedAtDesc(
            List.of(cliente.getId()), tenantId, "OPT_OUT"))
        .thenReturn(List.of(consent));

    OptOutPagedResponse response = service.listOptOutsPaged(tenantId, 0, 20);

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).clientNameMasked).isEqualTo("J**** S****");
    assertThat(response.items.get(0).source).isEqualTo("WHATSAPP_REPLY");
    assertThat(response.totalItems).isEqualTo(1);
  }

  @Test
  void listOptOutsPagedSemHistoricoUsaSourceDefaultOwner() {
    Cliente cliente = cliente();
    Page<Cliente> page = new PageImpl<>(List.of(cliente), PageRequest.of(0, 20), 1);
    when(clienteRepository.findByTenantIdAndWhatsappOptOutTrueOrderByWhatsappOptOutAtDesc(eq(tenantId), any()))
        .thenReturn(page);
    when(consentHistoryRepository.findByClientIdInAndTenantIdAndActionOrderByCreatedAtDesc(any(), any(), any()))
        .thenReturn(List.of());

    OptOutPagedResponse response = service.listOptOutsPaged(tenantId, 0, 20);

    assertThat(response.items.get(0).source).isEqualTo("OWNER");
  }

  // ---- listCycles ------------------------------------------------------------

  @Test
  void listCyclesComStatusInvalidoIgnoraFiltroEmSilencio() {
    when(cycleRepository.countOperational(eq(tenantId), any(), any(), eq(null), eq(null), eq(null))).thenReturn(0L);
    when(cycleRepository.listOperational(eq(tenantId), any(), any(), eq(null), eq(null), eq(null), anyInt(), anyInt()))
        .thenReturn(List.of());

    ReactivationCyclesPagedResponse response = service.listCycles(tenantId, "status-invalido", null, null, 0, 20);

    assertThat(response.items).isEmpty();
    assertThat(response.totalItems).isZero();
  }

  @Test
  void listCyclesMapeiaCamposDoCiclo() {
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setCustomerName("Maria");
    cycle.setLastStage(WhatsAppBookingReactivationStage.SERVICE_SELECTION);
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    cycle.setNextAttemptNumber(2);

    when(cycleRepository.countOperational(any(), any(), any(), any(), any(), any())).thenReturn(1L);
    when(cycleRepository.listOperational(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(List.of(cycle));

    ReactivationCyclesPagedResponse response = service.listCycles(tenantId, "ACTIVE", null, null, 0, 20);

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).clientName).isEqualTo("Maria");
    assertThat(response.items.get(0).lastStage).isEqualTo("SERVICE_SELECTION");
    assertThat(response.items.get(0).status).isEqualTo("ACTIVE");
  }

  // ---- cancelCycleById --------------------------------------------------------

  @Test
  void cancelCycleByIdNaoEncontradoLanca404() {
    UUID cycleId = UUID.randomUUID();
    when(cycleRepository.findById(cycleId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancelCycleById(tenantId, cycleId))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Ciclo nao encontrado");
  }

  @Test
  void cancelCycleByIdDeOutroTenantLanca404() {
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setTenantId(UUID.randomUUID());
    cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

    assertThatThrownBy(() -> service.cancelCycleById(tenantId, cycle.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Ciclo nao encontrado");
    verify(reactivationService, never()).cancelCycle(any(), any());
  }

  @Test
  void cancelCycleByIdEmStatusNaoCancelavelLanca409() {
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    cycle.setId(UUID.randomUUID());
    cycle.setTenantId(tenantId);
    cycle.setStatus(WhatsAppBookingReactivationStatus.EXHAUSTED);
    when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

    assertThatThrownBy(() -> service.cancelCycleById(tenantId, cycle.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("nao pode ser cancelado");
  }

  @Test
  void cancelCycleByIdActiveOuReactivatedEhCancelado() {
    WhatsAppBookingReactivationCycleEntity active = new WhatsAppBookingReactivationCycleEntity();
    active.setId(UUID.randomUUID());
    active.setTenantId(tenantId);
    active.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    when(cycleRepository.findById(active.getId())).thenReturn(Optional.of(active));

    service.cancelCycleById(tenantId, active.getId());

    verify(reactivationService).cancelCycle(active, "MANUAL_CANCEL");
  }

  // ---- getMetrics ---------------------------------------------------------------

  @Test
  void getMetricsCalculaTaxasEAgrupaPorStage() {
    WhatsAppBookingReactivationCycleEntity converted = new WhatsAppBookingReactivationCycleEntity();
    converted.setStatus(WhatsAppBookingReactivationStatus.CONVERTED);
    converted.setReactivatedAt(Instant.now());
    converted.setLastStage(WhatsAppBookingReactivationStage.FINAL_REVIEW);

    WhatsAppBookingReactivationCycleEntity optOut = new WhatsAppBookingReactivationCycleEntity();
    optOut.setStatus(WhatsAppBookingReactivationStatus.CANCELLED);
    optOut.setCancelReason("OPT_OUT");

    when(cycleRepository.findByTenantIdAndCreatedAtBetween(eq(tenantId), any(), any()))
        .thenReturn(List.of(converted, optOut));
    when(sendLogRepository.countByTenantIdAndAttemptNumberAndSentAtBetween(any(), anyInt(), any(), any()))
        .thenReturn(0L);
    when(cycleRepository.countByTenantIdAndReactivatedAtBetweenAndNextAttemptNumberGreaterThan(
            any(), any(), any(), anyInt()))
        .thenReturn(0L);

    ReactivationMetricsResponse metrics = service.getMetrics(tenantId, "2026-06");

    assertThat(metrics.totalCycles).isEqualTo(2);
    assertThat(metrics.converted).isEqualTo(1);
    assertThat(metrics.reactivated).isEqualTo(1);
    assertThat(metrics.optedOut).isEqualTo(1);
    assertThat(metrics.reactivationRate).isEqualTo(0.5);
    assertThat(metrics.byStage).containsEntry("FINAL_REVIEW", 1L);
    assertThat(metrics.byAttempt).hasSize(3);
  }
}
