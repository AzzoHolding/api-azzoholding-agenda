package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/settings/application/ServicoTenantPaymentWebhook.java}.
 *
 * <p>Recebe webhooks do Asaas de tenants individuais (conta propria do salao, F00/F01/F02/F10) e
 * registra o evento em {@link WebhookEventLog}. Consumidores de negocio hoje: confirmar o sinal de
 * reserva (F02, {@link AppointmentDeposit}) e confirmar pagamento Pix de comanda (F01,
 * {@link ComandaPagamento}); assinatura (F10) e renovada/marcada inadimplente. Eventos fora desses
 * ficam apenas registrados.
 *
 * <p>Nao confundir com {@code controller/WebhookController} ({@code /webhook/asaas}), que trata a
 * conta da <b>plataforma</b> (billing). Aqui a autenticacao e o {@code webhookToken} da rota, que
 * identifica o tenant.
 *
 * <p>Idempotencia por {@code tenantId:evento:sha256(payload)} — reentrega do mesmo evento e
 * ignorada sem reprocessar.
 */
@Service
public class ServicoTenantPaymentWebhook {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoTenantPaymentWebhook.class);
  private static final Set<String> PAYMENT_CONFIRMED_EVENTS =
      Set.of("PAYMENT_CONFIRMED", "PAYMENT_RECEIVED");
  private static final String PAYMENT_OVERDUE_EVENT = "PAYMENT_OVERDUE";

  private final TenantPaymentSettingsRepository tenantPaymentSettingsRepository;
  private final WebhookEventLogRepository webhookEventLogRepository;
  private final AppointmentDepositRepository appointmentDepositRepository;
  private final AgendamentoRepository agendamentoRepository;
  private final ComandaPagamentoRepository comandaPagamentoRepository;
  private final ClientMembershipRepository clientMembershipRepository;
  private final ClientMembershipBalanceRepository clientMembershipBalanceRepository;
  private final MembershipPlanBenefitRepository membershipPlanBenefitRepository;
  private final ObjectMapper objectMapper;

  public ServicoTenantPaymentWebhook(
      TenantPaymentSettingsRepository tenantPaymentSettingsRepository,
      WebhookEventLogRepository webhookEventLogRepository,
      AppointmentDepositRepository appointmentDepositRepository,
      AgendamentoRepository agendamentoRepository,
      ComandaPagamentoRepository comandaPagamentoRepository,
      ClientMembershipRepository clientMembershipRepository,
      ClientMembershipBalanceRepository clientMembershipBalanceRepository,
      MembershipPlanBenefitRepository membershipPlanBenefitRepository,
      ObjectMapper objectMapper) {
    this.tenantPaymentSettingsRepository = tenantPaymentSettingsRepository;
    this.webhookEventLogRepository = webhookEventLogRepository;
    this.appointmentDepositRepository = appointmentDepositRepository;
    this.agendamentoRepository = agendamentoRepository;
    this.comandaPagamentoRepository = comandaPagamentoRepository;
    this.clientMembershipRepository = clientMembershipRepository;
    this.clientMembershipBalanceRepository = clientMembershipBalanceRepository;
    this.membershipPlanBenefitRepository = membershipPlanBenefitRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void receber(String webhookToken, AsaasDtos.WebhookPayload payload) {
    TenantPaymentSettings config =
        tenantPaymentSettingsRepository
            .findByWebhookToken(webhookToken)
            .orElseThrow(() -> new ApiClientErrorException("Token de webhook invalido", 401));
    if (payload == null || payload.event == null || payload.event.isBlank()) {
      throw new ApiClientErrorException("Payload de webhook invalido", 400);
    }

    String payloadJson = safeJson(payload);
    String payloadHash = sha256(payloadJson);
    String idempotencyKey = config.getTenantId() + ":" + payload.event + ":" + payloadHash;
    if (webhookEventLogRepository.existsIdempotencyKey(idempotencyKey)) {
      LOG.info("Webhook Asaas (tenant) duplicado ignorado key={}", idempotencyKey);
      return;
    }

    WebhookEventLog eventLog = new WebhookEventLog();
    eventLog.setProvider("ASAAS_TENANT");
    eventLog.setIdempotencyKey(idempotencyKey);
    eventLog.setEventType(payload.event);
    eventLog.setTenantId(config.getTenantId());
    eventLog.setExternalPaymentId(payload.payment != null ? payload.payment.id : null);
    eventLog.setExternalSubscriptionId(
        payload.subscription != null
            ? payload.subscription.id
            : payload.payment != null ? payload.payment.subscription : null);
    eventLog.setPayloadHash(payloadHash);
    eventLog.setPayloadJson(payloadJson);
    eventLog.setProcessed(false);
    // saveAndFlush: o Panache emitia o INSERT no persist(), e a unicidade de idempotency_key
    // (indice do banco) precisa ser exercida antes do processamento de negocio.
    WebhookEventLog persistido = webhookEventLogRepository.saveAndFlush(eventLog);

    LOG.info(
        CorrelatedLogging.context(
            "Webhook Asaas (tenant) recebido",
            "tenantId", config.getTenantId(),
            "event", payload.event,
            "idempotencyKey", idempotencyKey));

    if (PAYMENT_CONFIRMED_EVENTS.contains(payload.event) && payload.payment != null) {
      confirmarSinalSePagamentoCorresponder(payload.payment.id);
      confirmarPagamentoComandaSeCorresponder(payload.payment.id);
      renovarAssinaturaSeCorresponder(resolveSubscriptionId(payload));
    } else if (PAYMENT_OVERDUE_EVENT.equals(payload.event)) {
      marcarAssinaturaInadimplenteSeCorresponder(resolveSubscriptionId(payload));
    }
    persistido.setProcessed(true);
  }

  private String resolveSubscriptionId(AsaasDtos.WebhookPayload payload) {
    if (payload.subscription != null && payload.subscription.id != null)
      return payload.subscription.id;
    return payload.payment != null ? payload.payment.subscription : null;
  }

  private void confirmarSinalSePagamentoCorresponder(String asaasPaymentId) {
    AppointmentDeposit deposit =
        appointmentDepositRepository.findByAsaasPaymentIdSeguro(asaasPaymentId).orElse(null);
    if (deposit == null) return;
    if (!AppointmentDeposit.STATUS_PENDING.equals(deposit.getStatus())) return;

    deposit.setStatus(AppointmentDeposit.STATUS_PAID);
    deposit.setPaidAt(Instant.now());

    Agendamento agendamento =
        agendamentoRepository.findById(deposit.getAppointmentId()).orElse(null);
    if (agendamento != null && agendamento.getStatus() == StatusAgendamento.PENDING) {
      agendamento.setStatus(StatusAgendamento.CONFIRMED);
    }

    LOG.info(
        CorrelatedLogging.context(
            "Sinal de reserva confirmado via webhook",
            "tenantId", deposit.getTenantId(),
            "appointmentId", deposit.getAppointmentId(),
            "asaasPaymentId", asaasPaymentId));
  }

  private void confirmarPagamentoComandaSeCorresponder(String asaasPaymentId) {
    ComandaPagamento pagamento =
        comandaPagamentoRepository.findByAsaasPaymentId(asaasPaymentId).orElse(null);
    if (pagamento == null) return;
    if (!ComandaPagamento.STATUS_PENDENTE.equals(pagamento.getStatus())) return;

    pagamento.setStatus(ComandaPagamento.STATUS_CONFIRMADO);
    pagamento.setPaidAt(Instant.now());

    LOG.info(
        CorrelatedLogging.context(
            "Pagamento Pix de comanda confirmado via webhook",
            "tenantId", pagamento.getTenantId(),
            "comandaId", pagamento.getComandaId(),
            "asaasPaymentId", asaasPaymentId));
  }

  /**
   * Renova o periodo da assinatura (F10) quando o Asaas confirma o pagamento do ciclo. Se o plano
   * nao e cumulativo, reseta o saldo usado; se e cumulativo, soma mais uma cota de beneficio ao
   * total disponivel (a sobra do periodo anterior carrega).
   */
  private void renovarAssinaturaSeCorresponder(String asaasSubscriptionId) {
    ClientMembership membership =
        clientMembershipRepository.findByAsaasSubscriptionId(asaasSubscriptionId).orElse(null);
    if (membership == null) return;
    if (ClientMembership.STATUS_CANCELADA.equals(membership.getStatus())) return;

    membership.setStatus(ClientMembership.STATUS_ATIVA);
    membership.setPeriodStart(membership.getPeriodEnd());
    membership.setPeriodEnd(membership.getPeriodStart().plus(30, ChronoUnit.DAYS));

    for (ClientMembershipBalance saldo :
        clientMembershipBalanceRepository.findByMembershipId(membership.getId())) {
      if (membership.isCumulativo()) {
        MembershipPlanBenefit beneficio =
            membership.getPlanId() != null
                ? membershipPlanBenefitRepository.findByPlanId(membership.getPlanId()).stream()
                    .filter(b -> b.getServiceId().equals(saldo.getServiceId()))
                    .findFirst()
                    .orElse(null)
                : null;
        if (beneficio != null)
          saldo.setQuantidadeMensal(saldo.getQuantidadeMensal() + beneficio.getQuantidadeMensal());
      } else {
        saldo.setUsadasNoPeriodo(0);
      }
    }

    LOG.info(
        CorrelatedLogging.context(
            "Assinatura renovada via webhook",
            "tenantId", membership.getTenantId(),
            "membershipId", membership.getId(),
            "asaasSubscriptionId", asaasSubscriptionId));
  }

  private void marcarAssinaturaInadimplenteSeCorresponder(String asaasSubscriptionId) {
    ClientMembership membership =
        clientMembershipRepository.findByAsaasSubscriptionId(asaasSubscriptionId).orElse(null);
    if (membership == null) return;
    if (!ClientMembership.STATUS_ATIVA.equals(membership.getStatus())) return;

    membership.setStatus(ClientMembership.STATUS_INADIMPLENTE);

    LOG.info(
        CorrelatedLogging.context(
            "Assinatura marcada inadimplente via webhook",
            "tenantId", membership.getTenantId(),
            "membershipId", membership.getId(),
            "asaasSubscriptionId", asaasSubscriptionId));
  }

  private String safeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      return "{}";
    }
  }

  private String sha256(String payload) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 nao disponivel", e);
    }
  }
}
