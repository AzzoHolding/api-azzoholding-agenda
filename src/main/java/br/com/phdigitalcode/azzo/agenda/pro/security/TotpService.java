package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/** Porte verbatim de {@code modules/security/infrastructure/TotpService.java} (TOTP/2FA, RFC 6238). */
@Service
public class TotpService {

  private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final int DEFAULT_SECRET_BYTES = 20;
  private static final long DEFAULT_STEP_SECONDS = 30L;
  private static final int DEFAULT_DIGITS = 6;
  private static final int DEFAULT_WINDOW = 1;

  private final SecureRandom secureRandom = new SecureRandom();

  public String generateSecret() {
    byte[] random = new byte[DEFAULT_SECRET_BYTES];
    secureRandom.nextBytes(random);
    return base32Encode(random);
  }

  public String buildOtpAuthUri(String issuer, String accountName, String secret) {
    String safeIssuer = normalizeIssuer(issuer);
    String safeAccount = accountName == null || accountName.isBlank() ? "usuario" : accountName.trim();
    String label = urlEncode(safeIssuer + ":" + safeAccount);
    String issuerParam = urlEncode(safeIssuer);
    String secretParam = urlEncode(secret);
    return "otpauth://totp/" + label + "?secret=" + secretParam + "&issuer=" + issuerParam + "&digits=6&period=30";
  }

  public boolean verifyCode(String secretBase32, String code) {
    return verifyCode(secretBase32, code, Instant.now());
  }

  public boolean verifyCode(String secretBase32, String code, Instant now) {
    if (secretBase32 == null || secretBase32.isBlank()) return false;
    String normalizedCode = normalizeCode(code);
    if (normalizedCode == null) return false;
    byte[] secret = base32Decode(secretBase32);
    if (secret.length == 0) return false;
    long counter = now.getEpochSecond() / DEFAULT_STEP_SECONDS;
    for (long i = -DEFAULT_WINDOW; i <= DEFAULT_WINDOW; i++) {
      String expected = hotp(secret, counter + i, DEFAULT_DIGITS);
      if (expected.equals(normalizedCode)) return true;
    }
    return false;
  }

  private String normalizeIssuer(String issuer) {
    if (issuer == null || issuer.isBlank()) return "Azzo Agenda Pro";
    return issuer.trim();
  }

  private String normalizeCode(String code) {
    if (code == null) return null;
    String digits = code.trim().replaceAll("\\s+", "");
    if (!digits.matches("\\d{6}")) return null;
    return digits;
  }

  private String hotp(byte[] secret, long counter, int digits) {
    try {
      byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret, "HmacSHA1"));
      byte[] hash = mac.doFinal(counterBytes);
      int offset = hash[hash.length - 1] & 0x0F;
      int binary =
          ((hash[offset] & 0x7F) << 24)
              | ((hash[offset + 1] & 0xFF) << 16)
              | ((hash[offset + 2] & 0xFF) << 8)
              | (hash[offset + 3] & 0xFF);
      int otp = binary % (int) Math.pow(10, digits);
      return String.format(Locale.ROOT, "%0" + digits + "d", otp);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Falha ao gerar codigo TOTP", e);
    }
  }

  private String base32Encode(byte[] data) {
    StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
    int buffer = 0;
    int bitsLeft = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xFF);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        int index = (buffer >> (bitsLeft - 5)) & 0x1F;
        out.append(BASE32_ALPHABET.charAt(index));
        bitsLeft -= 5;
      }
    }
    if (bitsLeft > 0) {
      int index = (buffer << (5 - bitsLeft)) & 0x1F;
      out.append(BASE32_ALPHABET.charAt(index));
    }
    return out.toString();
  }

  private byte[] base32Decode(String value) {
    String input = value.trim().replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
    if (input.isBlank()) return new byte[0];
    int buffer = 0;
    int bitsLeft = 0;
    ByteBuffer out = ByteBuffer.allocate(input.length() * 5 / 8 + 1);
    for (char c : input.toCharArray()) {
      int idx = BASE32_ALPHABET.indexOf(c);
      if (idx < 0) return new byte[0];
      buffer = (buffer << 5) | idx;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        out.put((byte) ((buffer >> (bitsLeft - 8)) & 0xFF));
        bitsLeft -= 8;
      }
    }
    byte[] decoded = new byte[out.position()];
    out.flip();
    out.get(decoded);
    return decoded;
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
