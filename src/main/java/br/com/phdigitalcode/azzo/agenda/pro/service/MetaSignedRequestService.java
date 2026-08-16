package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Espelha {@code modules/meta/infrastructure/MetaSignedRequestService.java}: parsing e validacao
 * do {@code signed_request} enviado pela Meta nos callbacks de {@code deauthorize}/
 * {@code data-deletion}, alem dos utilitarios de hash usados para persistir o
 * {@code WebhookEventLog}. Porte verbatim da logica HMAC-SHA256/Base64Url.
 */
@Service
public class MetaSignedRequestService {

  private final String appSecret;
  private final ObjectMapper objectMapper;

  public MetaSignedRequestService(
      @Value("${app.meta.app-secret}") String appSecret, ObjectMapper objectMapper) {
    this.appSecret = appSecret;
    this.objectMapper = objectMapper;
  }

  public JsonNode parseAndValidateSignedRequest(String rawBody) {
    ensureAppSecretConfigured();
    String signedRequest = extractSignedRequest(rawBody);
    if (signedRequest == null) {
      throw new IllegalArgumentException("signed_request ausente.");
    }

    String[] parts = signedRequest.split("\\.", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("signed_request invalido.");
    }

    byte[] signature = decodeBase64Url(parts[0]);
    String payloadSegment = parts[1];
    JsonNode payload = parsePayload(payloadSegment);
    validateAlgorithm(payload);
    validateSignature(signature, payloadSegment);
    return payload;
  }

  public String payloadToJson(JsonNode payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao serializar payload da Meta.", e);
    }
  }

  public String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao calcular hash SHA-256.", e);
    }
  }

  private void validateAlgorithm(JsonNode payload) {
    String algorithm = text(payload.path("algorithm"));
    if (algorithm == null || !"HMAC-SHA256".equalsIgnoreCase(algorithm)) {
      throw new IllegalArgumentException("Algoritmo do signed_request invalido.");
    }
  }

  private void validateSignature(byte[] signature, String payloadSegment) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expected = mac.doFinal(payloadSegment.getBytes(StandardCharsets.UTF_8));
      if (!MessageDigest.isEqual(signature, expected)) {
        throw new IllegalArgumentException("Assinatura do signed_request invalida.");
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao validar assinatura da Meta.", e);
    }
  }

  private JsonNode parsePayload(String payloadSegment) {
    try {
      byte[] payloadBytes = decodeBase64Url(payloadSegment);
      return objectMapper.readTree(payloadBytes);
    } catch (Exception e) {
      throw new IllegalArgumentException("Payload do signed_request invalido.", e);
    }
  }

  private byte[] decodeBase64Url(String value) {
    try {
      return Base64.getUrlDecoder().decode(padBase64(value));
    } catch (Exception e) {
      throw new IllegalArgumentException("Base64 URL invalido no signed_request.", e);
    }
  }

  private String padBase64(String value) {
    int remainder = value.length() % 4;
    if (remainder == 0) return value;
    return value + "====".substring(remainder);
  }

  private String extractSignedRequest(String rawBody) {
    if (rawBody == null || rawBody.isBlank()) return null;
    String trimmed = rawBody.trim();
    if (trimmed.startsWith("signed_request=") || trimmed.contains("&")) {
      return parseFormEncoded(trimmed).get("signed_request");
    }
    return trimmed;
  }

  private Map<String, String> parseFormEncoded(String body) {
    Map<String, String> values = new LinkedHashMap<>();
    for (String pair : body.split("&")) {
      if (pair == null || pair.isBlank()) continue;
      String[] parts = pair.split("=", 2);
      String key = urlDecode(parts[0]);
      String value = parts.length > 1 ? urlDecode(parts[1]) : "";
      values.put(key, value);
    }
    return values;
  }

  private String urlDecode(String value) {
    return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private void ensureAppSecretConfigured() {
    if (appSecret == null || appSecret.isBlank() || "__unset__".equalsIgnoreCase(appSecret.trim())) {
      throw new IllegalStateException("META_APP_SECRET nao configurado.");
    }
  }

  private String text(JsonNode node) {
    if (node == null || node.isNull()) return null;
    String value = node.asText();
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
