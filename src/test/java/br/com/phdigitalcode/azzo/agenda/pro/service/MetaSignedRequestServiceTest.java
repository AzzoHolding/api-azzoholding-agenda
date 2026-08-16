package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Cobre {@code modules/meta/infrastructure/MetaSignedRequestService.java}. */
class MetaSignedRequestServiceTest {

  private static final String APP_SECRET = "test-app-secret";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MetaSignedRequestService service;

  @BeforeEach
  void setUp() {
    service = new MetaSignedRequestService(APP_SECRET, objectMapper);
  }

  @Test
  void parseAndValidateSignedRequestAceitaAssinaturaValida() {
    String signedRequest = buildSignedRequest("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"123\"}");

    JsonNode payload = service.parseAndValidateSignedRequest(signedRequest);

    assertThat(payload.path("user_id").asText()).isEqualTo("123");
  }

  @Test
  void parseAndValidateSignedRequestAceitaFormEncoded() {
    String signedRequest = buildSignedRequest("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"456\"}");
    String formBody = "signed_request=" + signedRequest + "&outro=valor";

    JsonNode payload = service.parseAndValidateSignedRequest(formBody);

    assertThat(payload.path("user_id").asText()).isEqualTo("456");
  }

  @Test
  void parseAndValidateSignedRequestRejeitaBodyVazio() {
    assertThatThrownBy(() -> service.parseAndValidateSignedRequest(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ausente");
  }

  @Test
  void parseAndValidateSignedRequestRejeitaFormatoInvalido() {
    assertThatThrownBy(() -> service.parseAndValidateSignedRequest("sem-ponto"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalido");
  }

  @Test
  void parseAndValidateSignedRequestRejeitaAlgoritmoDiferente() {
    String payloadSegment = base64UrlEncode("{\"algorithm\":\"MD5\"}".getBytes(StandardCharsets.UTF_8));
    byte[] signature = hmacSha256(payloadSegment, APP_SECRET);
    String signedRequest = base64UrlEncode(signature) + "." + payloadSegment;

    assertThatThrownBy(() -> service.parseAndValidateSignedRequest(signedRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Algoritmo");
  }

  @Test
  void parseAndValidateSignedRequestRejeitaAssinaturaInvalida() {
    String payloadSegment = base64UrlEncode("{\"algorithm\":\"HMAC-SHA256\"}".getBytes(StandardCharsets.UTF_8));
    byte[] signature = hmacSha256(payloadSegment, "outro-secret");
    String signedRequest = base64UrlEncode(signature) + "." + payloadSegment;

    assertThatThrownBy(() -> service.parseAndValidateSignedRequest(signedRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Assinatura");
  }

  @Test
  void parseAndValidateSignedRequestRejeitaPayloadInvalido() {
    String payloadSegment = base64UrlEncode("nao-e-json".getBytes(StandardCharsets.UTF_8));
    byte[] signature = hmacSha256(payloadSegment, APP_SECRET);
    String signedRequest = base64UrlEncode(signature) + "." + payloadSegment;

    assertThatThrownBy(() -> service.parseAndValidateSignedRequest(signedRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Payload");
  }

  @Test
  void parseAndValidateSignedRequestFalhaQuandoSecretNaoConfigurado() {
    MetaSignedRequestService semSecret = new MetaSignedRequestService("__unset__", objectMapper);

    assertThatThrownBy(() -> semSecret.parseAndValidateSignedRequest("qualquer.coisa"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("META_APP_SECRET");
  }

  @Test
  void sha256CalculaHashDeterministico() {
    String hash1 = service.sha256("payload");
    String hash2 = service.sha256("payload");

    assertThat(hash1).isEqualTo(hash2).hasSize(64);
  }

  @Test
  void sha256TrataValorNuloComoStringVazia() {
    assertThat(service.sha256(null)).isEqualTo(service.sha256(""));
  }

  @Test
  void payloadToJsonSerializaNode() throws Exception {
    JsonNode node = objectMapper.readTree("{\"a\":1}");

    assertThat(service.payloadToJson(node)).isEqualTo("{\"a\":1}");
  }

  private String buildSignedRequest(String payloadJson) {
    String payloadSegment = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
    byte[] signature = hmacSha256(payloadSegment, APP_SECRET);
    return base64UrlEncode(signature) + "." + payloadSegment;
  }

  private byte[] hmacSha256(String payloadSegment, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(payloadSegment.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String base64UrlEncode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
