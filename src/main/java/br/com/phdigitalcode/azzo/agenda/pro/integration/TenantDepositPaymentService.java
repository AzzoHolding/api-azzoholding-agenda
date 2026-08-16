package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentDepositRepository;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;

/**
 * Espelha {@code infrastructure/payment/TenantDepositPaymentService.java}: gera e persiste o sinal
 * de reserva (F02, {@link AppointmentDeposit}) usando {@link TenantAsaasChargeService} para a
 * cobranca Pix propriamente dita. Usado por {@code ServicoPublicBooking} ao criar um agendamento
 * publico com deposito exigido.
 */
@Service
public class TenantDepositPaymentService {

  private final TenantAsaasChargeService tenantAsaasChargeService;
  private final AppointmentDepositRepository appointmentDepositRepository;

  public TenantDepositPaymentService(
      TenantAsaasChargeService tenantAsaasChargeService,
      AppointmentDepositRepository appointmentDepositRepository) {
    this.tenantAsaasChargeService = tenantAsaasChargeService;
    this.appointmentDepositRepository = appointmentDepositRepository;
  }

  @Transactional
  public AppointmentDeposit criarCobrancaSinal(
      UUID tenantId, UUID appointmentId, Cliente cliente, BigDecimal amount, String description) {
    TenantAsaasChargeService.PixCharge charge = tenantAsaasChargeService.criarCobrancaPix(
        tenantId, cliente, amount, description, "appointment:" + appointmentId);

    AppointmentDeposit deposit = new AppointmentDeposit();
    deposit.setTenantId(tenantId);
    deposit.setAppointmentId(appointmentId);
    deposit.setAsaasPaymentId(charge.asaasPaymentId());
    deposit.setStatus(AppointmentDeposit.STATUS_PENDING);
    deposit.setAmountCents(NumericUtil.toCents(amount));
    deposit.setPixPayload(charge.pixPayload());
    deposit.setExpiresAt(charge.expiresAt());
    appointmentDepositRepository.save(deposit);

    return deposit;
  }
}
