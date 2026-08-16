package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionStatusResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.MetaAcknowledgeResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WebhookEventLog;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WebhookEventLogRepository;
import org.springframework.http.HttpStatus;

/**
 * Espelha o corpo de negocio de {@code modules/meta/api/publicapi/MetaPlatformPublicResource.java}
 * (deauthorize/data-deletion/status), extraido para a camada de service seguindo a convencao do
 * porte Spring (controllers finos). O endpoint {@code oauthCallback} nao tem contraparte aqui: e
 * so montagem de HTML estatico, sem regra de negocio nem persistencia, e permanece no controller.
 */
@Service
public class MetaPlatformService {

  private static final String PROVIDER_META = "META";
  private static final String EVENT_TYPE_DEAUTHORIZE = "APP_DEAUTHORIZE";
  private static final String EVENT_TYPE_DATA_DELETION = "DATA_DELETION";

  private final MetaSignedRequestService metaSignedRequestService;
  private final WebhookEventLogRepository webhookEventLogRepository;

  public MetaPlatformService(
      MetaSignedRequestService metaSignedRequestService,
      WebhookEventLogRepository webhookEventLogRepository) {
    this.metaSignedRequestService = metaSignedRequestService;
    this.webhookEventLogRepository = webhookEventLogRepository;
  }

  @Transactional
  public MetaAcknowledgeResponse deauthorize(String body) {
    JsonNode payload = parseSignedRequest(body, "Falha ao processar callback de desautorizacao da Meta.");
    persistEvent(EVENT_TYPE_DEAUTHORIZE, payload, "meta-deauthorize-" + UUID.randomUUID());

    MetaAcknowledgeResponse response = new MetaAcknowledgeResponse();
    response.success = true;
    response.message = "Desautorizacao recebida.";
    response.receivedAt = Instant.now().toString();
    response.userId = text(payload.path("user_id"));
    return response;
  }

  @Transactional
  public DataDeletionResponse dataDeletion(String body, String statusBaseUrl) {
    JsonNode payload = parseSignedRequest(body, "Falha ao processar solicitacao de exclusao de dados da Meta.");
    String confirmationCode = "meta_del_" + UUID.randomUUID().toString().replace("-", "");
    persistEvent(EVENT_TYPE_DATA_DELETION, payload, confirmationCode);

    DataDeletionResponse response = new DataDeletionResponse();
    response.url = statusBaseUrl + confirmationCode;
    response.confirmationCode = confirmationCode;
    return response;
  }

  @Transactional(readOnly = true)
  public DataDeletionStatusResponse dataDeletionStatus(String confirmationCode) {
    WebhookEventLog log = webhookEventLogRepository.buscarPorIdempotencyKey(confirmationCode)
        .filter(candidate -> EVENT_TYPE_DATA_DELETION.equals(candidate.getEventType()))
        .orElse(null);
    if (log == null) {
      throw new ApiClientErrorException("Solicitacao de exclusao nao encontrada.", HttpStatus.NOT_FOUND.value());
    }

    DataDeletionStatusResponse response = new DataDeletionStatusResponse();
    response.confirmationCode = confirmationCode;
    response.status = log.isProcessed() ? "received" : "pending";
    response.receivedAt = log.getCreatedAt() != null ? log.getCreatedAt().toString() : null;
    response.message = "Solicitacao de exclusao de dados registrada.";
    return response;
  }

  private JsonNode parseSignedRequest(String body, String defaultMessage) {
    try {
      return metaSignedRequestService.parseAndValidateSignedRequest(body);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new ApiClientErrorException(defaultMessage + " " + e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }
  }

  private void persistEvent(String eventType, JsonNode payload, String idempotencyKey) {
    WebhookEventLog log = new WebhookEventLog();
    log.setProvider(PROVIDER_META);
    log.setIdempotencyKey(idempotencyKey);
    log.setEventType(eventType);
    log.setPayloadJson(metaSignedRequestService.payloadToJson(payload));
    log.setPayloadHash(metaSignedRequestService.sha256(log.getPayloadJson()));
    log.setProcessed(true);
    log.setProcessedAt(Instant.now());
    webhookEventLogRepository.save(log);
  }

  private String text(JsonNode node) {
    if (node == null || node.isNull()) return null;
    String value = node.asText();
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
