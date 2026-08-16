package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantPaymentSettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentDepositRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantPaymentSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/** Cobre {@code modules/scheduling/application/AppointmentDepositMaintenanceService.java}. */
class AppointmentDepositMaintenanceServiceTest {

  private AppointmentDepositRepository appointmentDepositRepository;
  private AgendamentoRepository agendamentoRepository;
  private TenantPaymentSettingsRepository tenantPaymentSettingsRepository;
  private EncryptionService encryptionService;
  private AsaasClient asaasClient;
  private AppointmentDepositMaintenanceService service;

  @BeforeEach
  void setUp() {
    appointmentDepositRepository = mock(AppointmentDepositRepository.class);
    agendamentoRepository = mock(AgendamentoRepository.class);
    tenantPaymentSettingsRepository = mock(TenantPaymentSettingsRepository.class);
    encryptionService = mock(EncryptionService.class);
    asaasClient = mock(AsaasClient.class);
    service =
        new AppointmentDepositMaintenanceService(
            appointmentDepositRepository,
            agendamentoRepository,
            tenantPaymentSettingsRepository,
            encryptionService,
            asaasClient);
  }

  private AppointmentDeposit sinalPendente() {
    AppointmentDeposit deposit = new AppointmentDeposit();
    deposit.setId(UUID.randomUUID());
    deposit.setTenantId(UUID.randomUUID());
    deposit.setAppointmentId(UUID.randomUUID());
    deposit.setAsaasPaymentId("pay_123");
    deposit.setStatus(AppointmentDeposit.STATUS_PENDING);
    deposit.setExpiresAt(Instant.now().minusSeconds(60));
    return deposit;
  }

  private Agendamento agendamento(UUID id, StatusAgendamento status) {
    Agendamento agendamento = new Agendamento();
    agendamento.setId(id);
    agendamento.setDate(LocalDate.now());
    agendamento.setStartTime("09:00");
    agendamento.setEndTime("10:00");
    agendamento.setStatus(status);
    return agendamento;
  }

  private void comSinaisExpirados(AppointmentDeposit... deposits) {
    when(appointmentDepositRepository.findExpiredPending(
            eq(AppointmentDeposit.STATUS_PENDING), any(Instant.class)))
        .thenReturn(List.of(deposits));
  }

  private TenantPaymentSettings settingsComChave(String apiKeyEnc) {
    TenantPaymentSettings settings = new TenantPaymentSettings();
    settings.setApiKeyEnc(apiKeyEnc);
    return settings;
  }

  @Test
  void expiraSinalCancelaCobrancaELiberaOHorario() {
    AppointmentDeposit deposit = sinalPendente();
    Agendamento agendamento = agendamento(deposit.getAppointmentId(), StatusAgendamento.PENDING);
    comSinaisExpirados(deposit);
    when(tenantPaymentSettingsRepository.findByTenantId(deposit.getTenantId()))
        .thenReturn(Optional.of(settingsComChave("enc")));
    when(encryptionService.decrypt("enc")).thenReturn("chave-real");
    when(agendamentoRepository.findById(deposit.getAppointmentId()))
        .thenReturn(Optional.of(agendamento));

    assertThat(service.expirarSinaisPendentes()).isEqualTo(1);

    verify(asaasClient).cancelPayment("chave-real", "pay_123");
    assertThat(deposit.getStatus()).isEqualTo(AppointmentDeposit.STATUS_EXPIRED);
    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELLED);
    verify(appointmentDepositRepository).save(deposit);
    verify(agendamentoRepository).save(agendamento);
  }

  @Test
  void agendamentoJaConfirmadoNaoECancelado() {
    AppointmentDeposit deposit = sinalPendente();
    Agendamento agendamento = agendamento(deposit.getAppointmentId(), StatusAgendamento.CONFIRMED);
    comSinaisExpirados(deposit);
    when(tenantPaymentSettingsRepository.findByTenantId(any())).thenReturn(Optional.empty());
    when(agendamentoRepository.findById(deposit.getAppointmentId()))
        .thenReturn(Optional.of(agendamento));

    assertThat(service.expirarSinaisPendentes()).isEqualTo(1);

    assertThat(deposit.getStatus()).isEqualTo(AppointmentDeposit.STATUS_EXPIRED);
    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONFIRMED);
    verify(agendamentoRepository, never()).save(any());
  }

  @Test
  void agendamentoInexistenteNaoImpedeAExpiracao() {
    AppointmentDeposit deposit = sinalPendente();
    comSinaisExpirados(deposit);
    when(tenantPaymentSettingsRepository.findByTenantId(any())).thenReturn(Optional.empty());
    when(agendamentoRepository.findById(deposit.getAppointmentId())).thenReturn(Optional.empty());

    assertThat(service.expirarSinaisPendentes()).isEqualTo(1);
    assertThat(deposit.getStatus()).isEqualTo(AppointmentDeposit.STATUS_EXPIRED);
  }

  @Test
  void tenantSemConfiguracaoDePagamentoNaoChamaOAsaas() {
    AppointmentDeposit deposit = sinalPendente();
    comSinaisExpirados(deposit);
    when(tenantPaymentSettingsRepository.findByTenantId(any())).thenReturn(Optional.empty());
    when(agendamentoRepository.findById(any())).thenReturn(Optional.empty());

    assertThat(service.expirarSinaisPendentes()).isEqualTo(1);

    verifyNoInteractions(asaasClient);
    verifyNoInteractions(encryptionService);
  }

  @Test
  void chaveEmBrancoNaoChamaOAsaas() {
    AppointmentDeposit deposit = sinalPendente();
    comSinaisExpirados(deposit);
    when(tenantPaymentSettingsRepository.findByTenantId(any()))
        .thenReturn(Optional.of(settingsComChave("   ")));
    when(agendamentoRepository.findById(any())).thenReturn(Optional.empty());

    assertThat(service.expirarSinaisPendentes()).isEqualTo(1);

    verifyNoInteractions(asaasClient);
    verifyNoInteractions(encryptionService);
  }

  @Test
  void chaveQueDescriptografaParaVazioNaoChamaOAsaas() {
    AppointmentDeposit deposit = sinalPendente();
    comSinaisExpirados(deposit);
    when(tenantPaymentSettingsRepository.findByTenantId(any()))
        .thenReturn(Optional.of(settingsComChave("enc")));
    when(encryptionService.decrypt("enc")).thenReturn("  ");
    when(agendamentoRepository.findById(any())).thenReturn(Optional.empty());

    assertThat(service.expirarSinaisPendentes()).isEqualTo(1);

    verifyNoInteractions(asaasClient);
  }

  @Test
  void falhaDoAsaasNaoImpedeAExpiracao() {
    AppointmentDeposit deposit = sinalPendente();
    Agendamento agendamento = agendamento(deposit.getAppointmentId(), StatusAgendamento.PENDING);
    comSinaisExpirados(deposit);
    when(tenantPaymentSettingsRepository.findByTenantId(any()))
        .thenReturn(Optional.of(settingsComChave("enc")));
    when(encryptionService.decrypt("enc")).thenReturn("chave-real");
    doThrow(new IllegalStateException("Asaas 500"))
        .when(asaasClient)
        .cancelPayment(anyString(), anyString());
    when(agendamentoRepository.findById(deposit.getAppointmentId()))
        .thenReturn(Optional.of(agendamento));

    assertThat(service.expirarSinaisPendentes()).isEqualTo(1);

    assertThat(deposit.getStatus()).isEqualTo(AppointmentDeposit.STATUS_EXPIRED);
    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELLED);
  }

  /**
   * Isolamento de falha do original: o {@code try/catch} e por sinal, entao um item quebrado nao
   * interrompe o lote — e nao entra na contagem.
   */
  @Test
  void sinalQueQuebraNaoInterrompeOLote() {
    AppointmentDeposit quebrado = sinalPendente();
    AppointmentDeposit bom = sinalPendente();
    comSinaisExpirados(quebrado, bom);
    when(tenantPaymentSettingsRepository.findByTenantId(quebrado.getTenantId()))
        .thenThrow(new IllegalStateException("banco fora"));
    when(tenantPaymentSettingsRepository.findByTenantId(bom.getTenantId()))
        .thenReturn(Optional.empty());
    when(agendamentoRepository.findById(bom.getAppointmentId())).thenReturn(Optional.empty());

    assertThat(service.expirarSinaisPendentes()).isEqualTo(1);

    assertThat(quebrado.getStatus()).isEqualTo(AppointmentDeposit.STATUS_PENDING);
    assertThat(bom.getStatus()).isEqualTo(AppointmentDeposit.STATUS_EXPIRED);
  }

  @Test
  void semSinaisExpiradosNaoFazNada() {
    comSinaisExpirados();

    assertThat(service.expirarSinaisPendentes()).isZero();

    verifyNoInteractions(asaasClient);
    verifyNoInteractions(agendamentoRepository);
    verify(appointmentDepositRepository, never()).save(any());
  }
}
