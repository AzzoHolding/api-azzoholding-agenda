package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Porte quase verbatim de {@code modules/security/infrastructure/EncryptionService.java} (AES/GCM). */
@Service
public class EncryptionService {

  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH = 128;

  private final SecureRandom secureRandom = new SecureRandom();
  private final SecretKeySpec keySpec;

  public EncryptionService(@Value("${app.security.encryption-key}") String encryptionKey) {
    byte[] keyBytes = parseKey(encryptionKey);
    this.keySpec = new SecretKeySpec(keyBytes, "AES");
  }

  public String encrypt(String value) {
    if (value == null || value.isBlank()) return "";
    try {
      byte[] iv = new byte[IV_LENGTH];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH, iv));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

      byte[] payload = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
      return Base64.getEncoder().encodeToString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao criptografar valor", e);
    }
  }

  public String decrypt(String encryptedValue) {
    if (encryptedValue == null || encryptedValue.isBlank()) return "";
    try {
      byte[] payload = Base64.getDecoder().decode(encryptedValue);
      if (payload.length <= IV_LENGTH) {
        throw new IllegalArgumentException("Valor criptografado invalido");
      }

      byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
      byte[] cipherBytes = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH, iv));
      byte[] decrypted = cipher.doFinal(cipherBytes);
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao descriptografar valor", e);
    }
  }

  private byte[] parseKey(String configuredKey) {
    if (configuredKey == null || configuredKey.isBlank()) {
      throw new IllegalStateException("Chave de criptografia nao configurada");
    }

    byte[] decoded = tryDecodeBase64(configuredKey);
    if (isValidAesKey(decoded)) return decoded;

    byte[] raw = configuredKey.getBytes(StandardCharsets.UTF_8);
    if (isValidAesKey(raw)) return raw;

    throw new IllegalStateException("Chave de criptografia invalida. Use 16, 24 ou 32 bytes.");
  }

  private byte[] tryDecodeBase64(String value) {
    try {
      return Base64.getDecoder().decode(value);
    } catch (IllegalArgumentException e) {
      return new byte[0];
    }
  }

  private boolean isValidAesKey(byte[] bytes) {
    return bytes.length == 16 || bytes.length == 24 || bytes.length == 32;
  }
}
