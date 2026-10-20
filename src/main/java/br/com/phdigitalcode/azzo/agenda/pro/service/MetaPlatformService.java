package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionStatusResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.MetaAcknowledgeResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WebhookEventLog;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MetaEmbeddedSignupGateway;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
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

  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MetaPlatformService.class);

  private static final String PROVIDER_META = "META";
  private static final String EVENT_TYPE_DEAUTHORIZE = "APP_DEAUTHORIZE";
  private static final String EVENT_TYPE_DATA_DELETION = "DATA_DELETION";
  private static final String ONBOARDING_NOT_STARTED = "NOT_STARTED";
  private static final String TOKEN_SOURCE_MANUAL = "MANUAL";

  private final MetaSignedRequestService metaSignedRequestService;
  private final WebhookEventLogRepository webhookEventLogRepository;
  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private final MetaEmbeddedSignupGateway metaEmbeddedSignupGateway;

  public MetaPlatformService(
      MetaSignedRequestService metaSignedRequestService,
      WebhookEventLogRepository webhookEventLogRepository,
      TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository,
      MetaEmbeddedSignupGateway metaEmbeddedSignupGateway) {
    this.metaSignedRequestService = metaSignedRequestService;
    this.webhookEventLogRepository = webhookEventLogRepository;
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
    this.metaEmbeddedSignupGateway = metaEmbeddedSignupGateway;
  }

  @Transactional
  public MetaAcknowledgeResponse deauthorize(String body) {
    JsonNode payload = parseSignedRequest(body, "Falha ao processar callback de desautorizacao da Meta.");
    String metaUserId = text(payload.path("user_id"));
    int desligados = desligarCredenciais(metaUserId);
    persistEvent(EVENT_TYPE_DEAUTHORIZE, payload, "meta-deauthorize-" + UUID.randomUUID(), desligados > 0);
    if (desligados > 0) {
      LOG.info("meta.deauthorize.credencial_removida metaUserId={} saloes={}", metaUserId, desligados);
    } else {
      LOG.warn("meta.deauthorize.pendente_tratamento_manual metaUserId={}", metaUserId);
    }

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
    String metaUserId = text(payload.path("user_id"));
    int desligados = desligarCredenciais(metaUserId);
    persistEvent(EVENT_TYPE_DATA_DELETION, payload, confirmationCode, desligados > 0);
    if (desligados > 0) {
      LOG.info("meta.data_deletion.credencial_removida confirmationCode={} metaUserId={} saloes={}",
          confirmationCode, metaUserId, desligados);
    } else {
      LOG.warn("meta.data_deletion.pendente_tratamento_manual confirmationCode={} metaUserId={}",
          confirmationCode, metaUserId);
    }

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
    response.message = log.isProcessed()
        ? "Solicitacao de exclusao de dados concluida."
        : "Solicitacao de exclusao de dados registrada; em tratamento pela equipe.";
    return response;
  }

  private JsonNode parseSignedRequest(String body, String defaultMessage) {
    try {
      return metaSignedRequestService.parseAndValidateSignedRequest(body);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new ApiClientErrorException(defaultMessage + " " + e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }
  }

  /**
   * SEG-014 (parte 2): o que a Meta pede quando o usuario remove o app ou pede exclusao. O
   * cadastro embutido guardou quem autorizou ({@code meta_user_id}); com ele achamos o salao e
   * apagamos o token, desligamos o WhatsApp e soltamos o vinculo. A revogacao das permissoes na
   * Meta e tentativa: o token ja apagado aqui e a garantia. Devolve quantos saloes foram tratados;
   * zero (cadastro antigo, sem o id, ou cadastro manual) segue para tratamento manual.
   */
  private int desligarCredenciais(String metaUserId) {
    if (metaUserId == null) return 0;
    List<TenantWhatsAppConfig> configs = tenantWhatsAppConfigRepository.findByMetaUserId(metaUserId);
    for (TenantWhatsAppConfig config : configs) {
      config.setWhatsappAccessTokenEnc("");
      config.setWhatsappEnabled(false);
      config.setWhatsappOnboardingStatus(ONBOARDING_NOT_STARTED);
      config.setWhatsappTokenSource(TOKEN_SOURCE_MANUAL);
      config.setEmbeddedSignupLastError(null);
      config.setMetaUserId(null);
      tenantWhatsAppConfigRepository.save(config);
    }
    if (!configs.isEmpty()) {
      try {
        metaEmbeddedSignupGateway.revokeUserPermissions(metaUserId);
      } catch (RuntimeException e) {
        LOG.warn("meta.revogacao_de_permissoes.falhou metaUserId={} motivo={}", metaUserId, e.getMessage());
      }
    }
    return configs.size();
  }

  private void persistEvent(String eventType, JsonNode payload, String idempotencyKey, boolean processed) {
    WebhookEventLog log = new WebhookEventLog();
    log.setProvider(PROVIDER_META);
    log.setIdempotencyKey(idempotencyKey);
    log.setEventType(eventType);
    log.setPayloadJson(metaSignedRequestService.payloadToJson(payload));
    log.setPayloadHash(metaSignedRequestService.sha256(log.getPayloadJson()));
    // Pentest 14/10/2026 (SEG-014): era gravado processed=true na hora e o status respondia
    // "received" — a Meta recebia confirmacao de uma exclusao que nunca aconteceu. Agora so vira
    // processed quando a credencial do salao foi de fato apagada; sem salao ligado ao user_id, o
    // evento fica PENDENTE e gera alerta para tratamento manual.
    log.setProcessed(processed);
    if (processed) log.setProcessedAt(Instant.now());
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
