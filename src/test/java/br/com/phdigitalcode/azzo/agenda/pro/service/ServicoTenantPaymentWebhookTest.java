package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembership;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembershipBalance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlanBenefit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantPaymentSettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WebhookEventLog;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasDtos;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentDepositRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipBalanceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaPagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MembershipPlanBenefitRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantPaymentSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WebhookEventLogRepository;

/** Cobre {@link ServicoTenantPaymentWebhook} — webhook Asaas da conta do proprio salao. */
@ExtendWith(MockitoExtension.class)
class ServicoTenantPaymentWebhookTest {

  private static final String TOKEN = "token-do-tenant";

  private final UUID tenantId = UUID.randomUUID();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private TenantPaymentSettingsRepository tenantPaymentSettingsRepository;
  @Mock private WebhookEventLogRepository webhookEventLogRepository;
  @Mock private AppointmentDepositRepository appointmentDepositRepository;
  @Mock private AgendamentoRepository agendamentoRepository;
  @Mock private ComandaPagamentoRepository comandaPagamentoRepository;
  @Mock private ClientMembershipRepository clientMembershipRepository;
  @Mock private ClientMembershipBalanceRepository clientMembershipBalanceRepository;
  @Mock private MembershipPlanBenefitRepository membershipPlanBenefitRepository;

  private ServicoTenantPaymentWebhook service;

  @BeforeEach
  void setUp() {
    service =
        new ServicoTenantPaymentWebhook(
            tenantPaymentSettingsRepository,
            webhookEventLogRepository,
            appointmentDepositRepository,
            agendamentoRepository,
            comandaPagamentoRepository,
            clientMembershipRepository,
            clientMembershipBalanceRepository,
            membershipPlanBenefitRepository,
            objectMapper);
  }

  @Test
  @DisplayName("token desconhecido devolve 401 sem registrar nada")
  void tokenDesconhecidoDevolve401() {
    when(tenantPaymentSettingsRepository.findByWebhookToken(TOKEN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.receber(TOKEN, payload("PAYMENT_CONFIRMED", "pay_1", null)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Token de webhook invalido")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(401);

    verify(webhookEventLogRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("payload sem evento devolve 400")
  void payloadSemEventoDevolve400() {
    when(tenantPaymentSettingsRepository.findByWebhookToken(TOKEN))
        .thenReturn(Optional.of(config()));

    assertThatThrownBy(() -> service.receber(TOKEN, payload("   ", "pay_1", null)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Payload de webhook invalido")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
    assertThatThrownBy(() -> service.receber(TOKEN, null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Payload de webhook invalido");
  }

  @Test
  @DisplayName("reentrega do mesmo evento e ignorada pela chave de idempotencia")
  void eventoDuplicadoEIgnorado() {
    when(tenantPaymentSettingsRepository.findByWebhookToken(TOKEN))
        .thenReturn(Optional.of(config()));
    when(webhookEventLogRepository.existsIdempotencyKey(anyString())).thenReturn(true);

    service.receber(TOKEN, payload("PAYMENT_CONFIRMED", "pay_1", null));

    verify(webhookEventLogRepository, never()).saveAndFlush(any());
    verify(appointmentDepositRepository, never()).findByAsaasPaymentIdSeguro(any());
  }

  @Test
  @DisplayName("pagamento confirmado marca o sinal como pago e confirma o agendamento")
  void pagamentoConfirmadoConfirmaSinalEAgendamento() {
    prepararEventoNovo();
    UUID appointmentId = UUID.randomUUID();
    AppointmentDeposit deposit = new AppointmentDeposit();
    deposit.setTenantId(tenantId);
    deposit.setAppointmentId(appointmentId);
    deposit.setStatus(AppointmentDeposit.STATUS_PENDING);
    Agendamento agendamento = new Agendamento();
    agendamento.setId(appointmentId);
    agendamento.setStatus(StatusAgendamento.PENDING);
    when(appointmentDepositRepository.findByAsaasPaymentIdSeguro("pay_1"))
        .thenReturn(Optional.of(deposit));
    when(agendamentoRepository.findById(appointmentId)).thenReturn(Optional.of(agendamento));
    when(comandaPagamentoRepository.findByAsaasPaymentId("pay_1")).thenReturn(Optional.empty());
    when(clientMembershipRepository.findByAsaasSubscriptionId(any())).thenReturn(Optional.empty());

    service.receber(TOKEN, payload("PAYMENT_CONFIRMED", "pay_1", null));

    assertThat(deposit.getStatus()).isEqualTo(AppointmentDeposit.STATUS_PAID);
    assertThat(deposit.getPaidAt()).isNotNull();
    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONFIRMED);
  }

  @Test
  @DisplayName("sinal que nao esta pendente e ignorado, sem mexer no agendamento")
  void sinalNaoPendenteEIgnorado() {
    prepararEventoNovo();
    AppointmentDeposit deposit = new AppointmentDeposit();
    deposit.setStatus(AppointmentDeposit.STATUS_EXPIRED);
    when(appointmentDepositRepository.findByAsaasPaymentIdSeguro("pay_1"))
        .thenReturn(Optional.of(deposit));
    when(comandaPagamentoRepository.findByAsaasPaymentId("pay_1")).thenReturn(Optional.empty());
    when(clientMembershipRepository.findByAsaasSubscriptionId(any())).thenReturn(Optional.empty());

    service.receber(TOKEN, payload("PAYMENT_RECEIVED", "pay_1", null));

    assertThat(deposit.getStatus()).isEqualTo(AppointmentDeposit.STATUS_EXPIRED);
    verify(agendamentoRepository, never()).findById(any());
  }

  @Test
  @DisplayName("pagamento Pix de comanda pendente e confirmado")
  void pagamentoDeComandaEConfirmado() {
    prepararEventoNovo();
    ComandaPagamento pagamento = new ComandaPagamento();
    pagamento.setTenantId(tenantId);
    pagamento.setComandaId(UUID.randomUUID());
    pagamento.setStatus(ComandaPagamento.STATUS_PENDENTE);
    when(appointmentDepositRepository.findByAsaasPaymentIdSeguro("pay_1"))
        .thenReturn(Optional.empty());
    when(comandaPagamentoRepository.findByAsaasPaymentId("pay_1"))
        .thenReturn(Optional.of(pagamento));
    when(clientMembershipRepository.findByAsaasSubscriptionId(any())).thenReturn(Optional.empty());

    service.receber(TOKEN, payload("PAYMENT_CONFIRMED", "pay_1", null));

    assertThat(pagamento.getStatus()).isEqualTo(ComandaPagamento.STATUS_CONFIRMADO);
    assertThat(pagamento.getPaidAt()).isNotNull();
  }

  @Test
  @DisplayName("assinatura nao cumulativa: renova o periodo e zera o usado")
  void assinaturaNaoCumulativaZeraUsado() {
    prepararEventoNovo();
    Instant fimAtual = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    ClientMembership membership = membership(fimAtual, false);
    ClientMembershipBalance saldo = new ClientMembershipBalance();
    saldo.setServiceId(UUID.randomUUID());
    saldo.setQuantidadeMensal(4);
    saldo.setUsadasNoPeriodo(3);
    when(appointmentDepositRepository.findByAsaasPaymentIdSeguro("pay_1"))
        .thenReturn(Optional.empty());
    when(comandaPagamentoRepository.findByAsaasPaymentId("pay_1")).thenReturn(Optional.empty());
    when(clientMembershipRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(Optional.of(membership));
    when(clientMembershipBalanceRepository.findByMembershipId(membership.getId()))
        .thenReturn(List.of(saldo));

    service.receber(TOKEN, payload("PAYMENT_CONFIRMED", "pay_1", "sub_1"));

    assertThat(membership.getStatus()).isEqualTo(ClientMembership.STATUS_ATIVA);
    assertThat(membership.getPeriodStart()).isEqualTo(fimAtual);
    assertThat(membership.getPeriodEnd()).isEqualTo(fimAtual.plus(30, ChronoUnit.DAYS));
    assertThat(saldo.getUsadasNoPeriodo()).isZero();
    assertThat(saldo.getQuantidadeMensal()).isEqualTo(4);
  }

  @Test
  @DisplayName("assinatura cumulativa: soma mais uma cota em vez de zerar o usado")
  void assinaturaCumulativaSomaCota() {
    prepararEventoNovo();
    ClientMembership membership = membership(Instant.now(), true);
    UUID serviceId = UUID.randomUUID();
    ClientMembershipBalance saldo = new ClientMembershipBalance();
    saldo.setServiceId(serviceId);
    saldo.setQuantidadeMensal(4);
    saldo.setUsadasNoPeriodo(3);
    MembershipPlanBenefit beneficio = new MembershipPlanBenefit();
    beneficio.setServiceId(serviceId);
    beneficio.setQuantidadeMensal(4);
    when(appointmentDepositRepository.findByAsaasPaymentIdSeguro("pay_1"))
        .thenReturn(Optional.empty());
    when(comandaPagamentoRepository.findByAsaasPaymentId("pay_1")).thenReturn(Optional.empty());
    when(clientMembershipRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(Optional.of(membership));
    when(clientMembershipBalanceRepository.findByMembershipId(membership.getId()))
        .thenReturn(List.of(saldo));
    when(membershipPlanBenefitRepository.findByPlanId(membership.getPlanId()))
        .thenReturn(List.of(beneficio));

    service.receber(TOKEN, payload("PAYMENT_CONFIRMED", "pay_1", "sub_1"));

    assertThat(saldo.getQuantidadeMensal()).isEqualTo(8);
    assertThat(saldo.getUsadasNoPeriodo()).isEqualTo(3);
  }

  @Test
  @DisplayName("assinatura cancelada nao e reativada por pagamento confirmado")
  void assinaturaCanceladaNaoEReativada() {
    prepararEventoNovo();
    ClientMembership membership = membership(Instant.now(), false);
    membership.setStatus(ClientMembership.STATUS_CANCELADA);
    when(appointmentDepositRepository.findByAsaasPaymentIdSeguro("pay_1"))
        .thenReturn(Optional.empty());
    when(comandaPagamentoRepository.findByAsaasPaymentId("pay_1")).thenReturn(Optional.empty());
    when(clientMembershipRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(Optional.of(membership));

    service.receber(TOKEN, payload("PAYMENT_CONFIRMED", "pay_1", "sub_1"));

    assertThat(membership.getStatus()).isEqualTo(ClientMembership.STATUS_CANCELADA);
    verify(clientMembershipBalanceRepository, never()).findByMembershipId(any());
  }

  @Test
  @DisplayName("PAYMENT_OVERDUE marca a assinatura ativa como inadimplente")
  void overdueMarcaInadimplente() {
    prepararEventoNovo();
    ClientMembership membership = membership(Instant.now(), false);
    when(clientMembershipRepository.findByAsaasSubscriptionId("sub_1"))
        .thenReturn(Optional.of(membership));

    service.receber(TOKEN, payload("PAYMENT_OVERDUE", "pay_1", "sub_1"));

    assertThat(membership.getStatus()).isEqualTo(ClientMembership.STATUS_INADIMPLENTE);
    // overdue nao toca em sinal nem em comanda
    verify(appointmentDepositRepository, never()).findByAsaasPaymentIdSeguro(any());
    verify(comandaPagamentoRepository, never()).findByAsaasPaymentId(any());
  }

  @Test
  @DisplayName("evento sem consumidor e apenas registrado, e o log sai marcado como processado")
  void eventoSemConsumidorSoERegistrado() {
    prepararEventoNovo();

    service.receber(TOKEN, payload("PAYMENT_CREATED", "pay_1", null));

    ArgumentCaptor<WebhookEventLog> captor = ArgumentCaptor.forClass(WebhookEventLog.class);
    verify(webhookEventLogRepository).saveAndFlush(captor.capture());
    WebhookEventLog log = captor.getValue();
    assertThat(log.getProvider()).isEqualTo("ASAAS_TENANT");
    assertThat(log.getTenantId()).isEqualTo(tenantId);
    assertThat(log.getEventType()).isEqualTo("PAYMENT_CREATED");
    assertThat(log.getExternalPaymentId()).isEqualTo("pay_1");
    assertThat(log.getIdempotencyKey()).startsWith(tenantId + ":PAYMENT_CREATED:");
    assertThat(log.isProcessed()).isTrue();
    verify(appointmentDepositRepository, never()).findByAsaasPaymentIdSeguro(any());
  }

  // ---------- helpers ----------

  private void prepararEventoNovo() {
    when(tenantPaymentSettingsRepository.findByWebhookToken(TOKEN))
        .thenReturn(Optional.of(config()));
    when(webhookEventLogRepository.existsIdempotencyKey(anyString())).thenReturn(false);
    when(webhookEventLogRepository.saveAndFlush(any(WebhookEventLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private TenantPaymentSettings config() {
    TenantPaymentSettings config = new TenantPaymentSettings();
    config.setTenantId(tenantId);
    config.setWebhookToken(TOKEN);
    return config;
  }

  private ClientMembership membership(Instant periodEnd, boolean cumulativo) {
    ClientMembership membership = new ClientMembership();
    membership.setId(UUID.randomUUID());
    membership.setTenantId(tenantId);
    membership.setPlanId(UUID.randomUUID());
    membership.setStatus(ClientMembership.STATUS_ATIVA);
    membership.setPeriodStart(periodEnd.minus(30, ChronoUnit.DAYS));
    membership.setPeriodEnd(periodEnd);
    membership.setCumulativo(cumulativo);
    return membership;
  }

  private AsaasDtos.WebhookPayload payload(String event, String paymentId, String subscriptionId) {
    AsaasDtos.WebhookPayload payload = new AsaasDtos.WebhookPayload();
    payload.event = event;
    AsaasDtos.WebhookPayment payment = new AsaasDtos.WebhookPayment();
    payment.id = paymentId;
    payment.subscription = subscriptionId;
    payload.payment = payment;
    return payload;
  }
}
