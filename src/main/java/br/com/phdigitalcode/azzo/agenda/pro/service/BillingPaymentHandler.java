package br.com.phdigitalcode.azzo.agenda.pro.service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;

/**
 * Porte verbatim de {@code modules/billing/application/BillingPaymentHandler.java}.
 *
 * <p>O original resolve as implementacoes por {@code Instance<BillingPaymentHandler>} do CDI; aqui
 * o {@code ServicoBilling} recebe a {@code List<BillingPaymentHandler>} por construtor — o Spring
 * injeta todos os beans do tipo. Os tres {@code supports} sao mutuamente exclusivos
 * (PIX / BOLETO / CREDIT_CARD), entao a ordem da colecao nao muda o resultado, exatamente como no
 * CDI (que tambem nao garante ordem).
 */
public interface BillingPaymentHandler {

  boolean supports(String billingType);

  BillingDtos.SubscriptionResponse process(
      BillingDtos.CreateSubscriptionRequest request, String remoteIp);
}
