package br.com.phdigitalcode.azzo.agenda.pro.integration;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.BillingPaymentHandler;

/**
 * Porte verbatim de {@code infrastructure/payment/AsaasSubscriptionBillingHandler.java}.
 *
 * <p>Diferente dos outros dois, este normaliza com {@code toUpperCase()} e compara com
 * {@code equals} — ou seja, {@code " credit_card "} e aceito. Assimetria do original, preservada.
 */
@Component
public class AsaasSubscriptionBillingHandler implements BillingPaymentHandler {

  private final AsaasService asaasService;

  public AsaasSubscriptionBillingHandler(AsaasService asaasService) {
    this.asaasService = asaasService;
  }

  @Override
  public boolean supports(String billingType) {
    if (billingType == null) return false;
    String normalized = billingType.trim().toUpperCase();
    return "CREDIT_CARD".equals(normalized);
  }

  @Override
  public BillingDtos.SubscriptionResponse process(
      BillingDtos.CreateSubscriptionRequest request, String remoteIp) {
    return asaasService.createMonthlySubscriptionForCurrentTenant(request, remoteIp);
  }
}
