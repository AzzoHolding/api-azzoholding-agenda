package br.com.phdigitalcode.azzo.agenda.pro.integration;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.BillingPaymentHandler;

/** Porte verbatim de {@code infrastructure/payment/AsaasBoletoBillingHandler.java}. */
@Component
public class AsaasBoletoBillingHandler implements BillingPaymentHandler {

  private final AsaasBoletoService asaasBoletoService;

  public AsaasBoletoBillingHandler(AsaasBoletoService asaasBoletoService) {
    this.asaasBoletoService = asaasBoletoService;
  }

  @Override
  public boolean supports(String billingType) {
    return billingType != null && "BOLETO".equalsIgnoreCase(billingType.trim());
  }

  @Override
  public BillingDtos.SubscriptionResponse process(
      BillingDtos.CreateSubscriptionRequest request, String remoteIp) {
    return asaasBoletoService.createBoletoChargeForCurrentTenant(request, remoteIp);
  }
}
