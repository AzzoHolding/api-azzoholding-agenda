package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoTenantPaymentWebhook;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/settings/api/TenantPaymentWebhookResource.java} —
 * {@code POST /webhook/asaas/tenant/{webhookToken}}.
 *
 * <p>Rota publica ({@code /webhook/*} esta na allowlist do {@code SecurityConfig}, replicando
 * {@code quarkus.http.auth.permission.public.paths}): a autenticacao e o proprio
 * {@code webhookToken} do path, que identifica o tenant e e validado no service — token
 * desconhecido resulta em 401.
 *
 * <p>Nao confundir com {@code WebhookController} ({@code /webhook/asaas}), que e a conta da
 * plataforma.
 */
@RestController
@RequestMapping("/webhook/asaas/tenant")
public class TenantPaymentWebhookController {

  private static final Logger LOG = LoggerFactory.getLogger(TenantPaymentWebhookController.class);

  private final ServicoTenantPaymentWebhook servicoTenantPaymentWebhook;

  public TenantPaymentWebhookController(ServicoTenantPaymentWebhook servicoTenantPaymentWebhook) {
    this.servicoTenantPaymentWebhook = servicoTenantPaymentWebhook;
  }

  @PostMapping("/{webhookToken}")
  public ResponseEntity<Void> receberWebhook(
      @PathVariable String webhookToken, @RequestBody AsaasDtos.WebhookPayload payload) {
    LOG.info(
        CorrelatedLogging.context(
            "Webhook Asaas (tenant) recebido",
            "event", payload != null ? payload.event : null,
            "paymentId",
            payload != null && payload.payment != null ? payload.payment.id : null));
    servicoTenantPaymentWebhook.receber(webhookToken, payload);
    return ResponseEntity.ok().build();
  }
}
