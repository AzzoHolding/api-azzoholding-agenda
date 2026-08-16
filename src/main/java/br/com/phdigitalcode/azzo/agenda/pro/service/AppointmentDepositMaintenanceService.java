package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantPaymentSettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentDepositRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantPaymentSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/scheduling/application/AppointmentDepositMaintenanceService.java}: expira
 * sinais de reserva (F02) nao pagos dentro do prazo, cancela a cobranca na conta Asaas do tenant
 * (melhor esforco) e libera o horario cancelando o agendamento associado.
 *
 * <p><b>Uma unica transacao para o lote inteiro</b>, como no original — aqui nao ha
 * {@code REQUIRES_NEW} por item. O isolamento de falha vem do {@code try/catch} em volta de cada
 * {@code expirarUm}: um sinal que estoure excecao e apenas logado e o laco segue. Consequencia
 * herdada: se o commit final falhar, o lote inteiro volta atras — mas as chamadas ja feitas ao
 * Asaas nao sao desfeitas. Comportamento do original, preservado.
 */
@Service
public class AppointmentDepositMaintenanceService {

  private static final Logger LOG =
      LoggerFactory.getLogger(AppointmentDepositMaintenanceService.class);

  private final AppointmentDepositRepository appointmentDepositRepository;
  private final AgendamentoRepository agendamentoRepository;
  private final TenantPaymentSettingsRepository tenantPaymentSettingsRepository;
  private final EncryptionService encryptionService;
  private final AsaasClient asaasClient;

  public AppointmentDepositMaintenanceService(
      AppointmentDepositRepository appointmentDepositRepository,
      AgendamentoRepository agendamentoRepository,
      TenantPaymentSettingsRepository tenantPaymentSettingsRepository,
      EncryptionService encryptionService,
      AsaasClient asaasClient) {
    this.appointmentDepositRepository = appointmentDepositRepository;
    this.agendamentoRepository = agendamentoRepository;
    this.tenantPaymentSettingsRepository = tenantPaymentSettingsRepository;
    this.encryptionService = encryptionService;
    this.asaasClient = asaasClient;
  }

  @Transactional
  public int expirarSinaisPendentes() {
    List<AppointmentDeposit> expirados =
        appointmentDepositRepository.findExpiredPending(
            AppointmentDeposit.STATUS_PENDING, Instant.now());
    int total = 0;
    for (AppointmentDeposit deposit : expirados) {
      try {
        expirarUm(deposit);
        total++;
      } catch (Exception e) {
        LOG.error(
            CorrelatedLogging.context(
                "Falha ao expirar sinal de reserva",
                "appointmentDepositId", deposit.getId(),
                "appointmentId", deposit.getAppointmentId()),
            e);
      }
    }
    return total;
  }

  private void expirarUm(AppointmentDeposit deposit) {
    cancelarNoAsaasMelhorEsforco(deposit);

    deposit.setStatus(AppointmentDeposit.STATUS_EXPIRED);
    appointmentDepositRepository.save(deposit);

    Agendamento agendamento =
        agendamentoRepository.findById(deposit.getAppointmentId()).orElse(null);
    if (agendamento != null && agendamento.getStatus() == StatusAgendamento.PENDING) {
      agendamento.setStatus(StatusAgendamento.CANCELLED);
      agendamentoRepository.save(agendamento);
    }

    LOG.info(
        CorrelatedLogging.context(
            "Sinal de reserva expirado sem pagamento",
            "tenantId", deposit.getTenantId(),
            "appointmentId", deposit.getAppointmentId(),
            "asaasPaymentId", deposit.getAsaasPaymentId()));
  }

  /**
   * Tenant sem chave Asaas configurada apenas nao tem a cobranca cancelada — o sinal expira do
   * mesmo jeito. Falha do Asaas tambem nao interrompe a expiracao (a mensagem da excecao nao vai
   * para o log, so o id da cobranca, como no original).
   */
  private void cancelarNoAsaasMelhorEsforco(AppointmentDeposit deposit) {
    TenantPaymentSettings config =
        tenantPaymentSettingsRepository.findByTenantId(deposit.getTenantId()).orElse(null);
    if (config == null || trimToNull(config.getApiKeyEnc()) == null) return;
    String apiKey = trimToNull(encryptionService.decrypt(config.getApiKeyEnc()));
    if (apiKey == null) return;
    try {
      asaasClient.cancelPayment(apiKey, deposit.getAsaasPaymentId());
    } catch (Exception e) {
      LOG.warn(
          CorrelatedLogging.context(
              "Nao foi possivel cancelar a cobranca do sinal no Asaas (melhor esforco)",
              "asaasPaymentId", deposit.getAsaasPaymentId()));
    }
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }
}
