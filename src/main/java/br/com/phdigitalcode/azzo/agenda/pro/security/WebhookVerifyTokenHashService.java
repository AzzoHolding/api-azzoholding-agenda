package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Service;

/** Espelha {@code modules/security/infrastructure/WebhookVerifyTokenHashService.java}. */
@Service
public class WebhookVerifyTokenHashService {

  public String hash(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) return null;
    String normalized = rawToken.trim();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
      return toHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 nao disponivel", e);
    }
  }

  private String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
