package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionStatusResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.MetaAcknowledgeResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WebhookEventLog;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WebhookEventLogRepository;

/**
 * Cobre {@code MetaPlatformService}, que espelha o corpo de negocio de
 * {@code modules/meta/api/publicapi/MetaPlatformPublicResource.java} (deauthorize/data-deletion/status).
 */
class MetaPlatformServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MetaSignedRequestService metaSignedRequestService;
  private WebhookEventLogRepository webhookEventLogRepository;
  private MetaPlatformService service;

  @BeforeEach
  void setUp() {
    metaSignedRequestService = mock(MetaSignedRequestService.class);
    webhookEventLogRepository = mock(WebhookEventLogRepository.class);
    service = new MetaPlatformService(metaSignedRequestService, webhookEventLogRepository);
  }

  @Test
  void deauthorizePersisteEventoERetornaAck() throws Exception {
    JsonNode payload = objectMapper.readTree("{\"user_id\":\"123\"}");
    when(metaSignedRequestService.parseAndValidateSignedRequest("corpo")).thenReturn(payload);
    when(metaSignedRequestService.payloadToJson(payload)).thenReturn("{\"user_id\":\"123\"}");
    when(metaSignedRequestService.sha256(anyString())).thenReturn("hash");

    MetaAcknowledgeResponse response = service.deauthorize("corpo");

    assertThat(response.success).isTrue();
    assertThat(response.userId).isEqualTo("123");
    assertThat(response.message).isEqualTo("Desautorizacao recebida.");
    assertThat(response.receivedAt).isNotNull();

    verify(webhookEventLogRepository).save(any(WebhookEventLog.class));
  }

  @Test
  void deauthorizeConvertBadRequestQuandoSignedRequestInvalido() {
    when(metaSignedRequestService.parseAndValidateSignedRequest("corpo"))
        .thenThrow(new IllegalArgumentException("signed_request invalido."));

    assertThatThrownBy(() -> service.deauthorize("corpo"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Falha ao processar callback de desautorizacao da Meta.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  @Test
  void dataDeletionPersisteEventoERetornaUrlComConfirmationCode() throws Exception {
    JsonNode payload = objectMapper.readTree("{}");
    when(metaSignedRequestService.parseAndValidateSignedRequest("corpo")).thenReturn(payload);
    when(metaSignedRequestService.payloadToJson(payload)).thenReturn("{}");
    when(metaSignedRequestService.sha256(anyString())).thenReturn("hash");

    DataDeletionResponse response = service.dataDeletion("corpo", "https://host/api/v1/public/meta/data-deletion/status/");

    assertThat(response.confirmationCode).startsWith("meta_del_");
    assertThat(response.url).isEqualTo(
        "https://host/api/v1/public/meta/data-deletion/status/" + response.confirmationCode);
    verify(webhookEventLogRepository).save(any(WebhookEventLog.class));
  }

  @Test
  void dataDeletionStatusRetornaPendingQuandoNaoProcessado() {
    WebhookEventLog log = new WebhookEventLog();
    log.setEventType("DATA_DELETION");
    log.setProcessed(false);
    when(webhookEventLogRepository.buscarPorIdempotencyKey("abc")).thenReturn(Optional.of(log));

    DataDeletionStatusResponse response = service.dataDeletionStatus("abc");

    assertThat(response.confirmationCode).isEqualTo("abc");
    assertThat(response.status).isEqualTo("pending");
  }

  @Test
  void dataDeletionStatusRetornaReceivedQuandoProcessadoComCreatedAt() {
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    WebhookEventLog log = new WebhookEventLog();
    log.setEventType("DATA_DELETION");
    log.setProcessed(true);
    log.setCreatedAt(createdAt);
    when(webhookEventLogRepository.buscarPorIdempotencyKey("abc")).thenReturn(Optional.of(log));

    DataDeletionStatusResponse response = service.dataDeletionStatus("abc");

    assertThat(response.status).isEqualTo("received");
    assertThat(response.receivedAt).isEqualTo(createdAt.toString());
  }

  @Test
  void dataDeletionStatusLancaNotFoundQuandoAusente() {
    when(webhookEventLogRepository.buscarPorIdempotencyKey("abc")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.dataDeletionStatus("abc"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Solicitacao de exclusao nao encontrada.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void dataDeletionStatusLancaNotFoundQuandoEventTypeDiferente() {
    WebhookEventLog log = new WebhookEventLog();
    log.setEventType("APP_DEAUTHORIZE");
    when(webhookEventLogRepository.buscarPorIdempotencyKey("abc")).thenReturn(Optional.of(log));

    assertThatThrownBy(() -> service.dataDeletionStatus("abc"))
        .isInstanceOf(ApiClientErrorException.class);
  }
}
