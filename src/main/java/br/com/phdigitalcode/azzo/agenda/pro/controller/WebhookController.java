package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasDtos;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/billing/api/webhook/WebhookResource.java}.
 *
 * <p>{@code /webhook/**} esta em {@code permitAll} no {@code SecurityConfig} porque o Asaas nao
 * envia JWT: a autenticacao e o header {@code asaas-access-token}, validado dentro de
 * {@link AsaasService#processWebhook} em tempo constante. O header e opcional na assinatura do
 * metodo de proposito — quando ausente, quem responde 401 e a validacao do service, nao o Spring
 * (que responderia 400), preservando o status do original.
 */
@RestController
@RequestMapping("/webhook/asaas")
public class WebhookController {

  private static final Logger LOG = LoggerFactory.getLogger(WebhookController.class);

  private final AsaasService asaasService;

  public WebhookController(AsaasService asaasService) {
    this.asaasService = asaasService;
  }

  @PostMapping
  public ResponseEntity<Void> receiveAsaasWebhook(
      @RequestHeader(name = "asaas-access-token", required = false) String webhookToken,
      @RequestBody(required = false) AsaasDtos.WebhookPayload payload) {
    LOG.info(
        CorrelatedLogging.context(
            "Webhook Asaas recebido",
            "event", payload != null ? payload.event : null,
            "paymentId", payload != null && payload.payment != null ? payload.payment.id : null,
            "subscriptionId",
                payload != null && payload.subscription != null
                    ? payload.subscription.id
                    : payload != null && payload.payment != null
                        ? payload.payment.subscription
                        : null,
            "tokenProvided", webhookToken != null && !webhookToken.isBlank()));
    asaasService.processWebhook(webhookToken, payload);
    return ResponseEntity.ok().build();
  }
}
