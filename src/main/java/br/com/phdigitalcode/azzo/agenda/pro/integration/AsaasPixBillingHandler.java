package br.com.phdigitalcode.azzo.agenda.pro.integration;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.BillingPaymentHandler;

/** Porte verbatim de {@code infrastructure/payment/AsaasPixBillingHandler.java}. */
@Component
public class AsaasPixBillingHandler implements BillingPaymentHandler {

  private final AsaasPixService asaasPixService;

  public AsaasPixBillingHandler(AsaasPixService asaasPixService) {
    this.asaasPixService = asaasPixService;
  }

  @Override
  public boolean supports(String billingType) {
    return billingType != null && "PIX".equalsIgnoreCase(billingType.trim());
  }

  @Override
  public BillingDtos.SubscriptionResponse process(
      BillingDtos.CreateSubscriptionRequest request, String remoteIp) {
    return asaasPixService.createPixChargeForCurrentTenant(request, remoteIp);
  }
}
