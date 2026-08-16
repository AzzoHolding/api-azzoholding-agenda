package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

/**
 * Utilitario de carga de chaves RSA em PEM (PKCS8 para privada, X.509 para publica) — mesmo
 * formato usado pelo {@code smallrye-jwt} do Quarkus original (arquivos {@code privateKey.pem}/
 * {@code publicKey.pem} em dev; env vars {@code JWT_PRIVATE_KEY}/{@code JWT_PUBLIC_KEY} em prod).
 */
public final class PemKeyUtils {

  private PemKeyUtils() {}

  public static PrivateKey loadPrivateKey(String pemLocationOrValue) {
    try {
      String pem = resolvePemContent(pemLocationOrValue);
      byte[] der = decodePem(pem);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("Falha ao carregar chave privada JWT (RSA/PKCS8)", e);
    }
  }

  public static PublicKey loadPublicKey(String pemLocationOrValue) {
    try {
      String pem = resolvePemContent(pemLocationOrValue);
      byte[] der = decodePem(pem);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePublic(new X509EncodedKeySpec(der));
    } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("Falha ao carregar chave publica JWT (RSA/X.509)", e);
    }
  }

  /**
   * Aceita: {@code classpath:arquivo.pem}, caminho de arquivo absoluto, ou o proprio conteudo PEM
   * (usado em producao via env var {@code JWT_PRIVATE_KEY}/{@code JWT_PUBLIC_KEY}).
   */
  private static String resolvePemContent(String locationOrValue) throws IOException {
    if (locationOrValue == null || locationOrValue.isBlank()) {
      throw new IllegalStateException("Chave JWT nao configurada");
    }
    String trimmed = locationOrValue.trim();
    if (trimmed.contains("BEGIN")) {
      return trimmed;
    }
    Resource resource = trimmed.startsWith("classpath:")
        ? new ClassPathResource(trimmed.substring("classpath:".length()))
        : new FileSystemResource(trimmed);
    try (InputStream in = resource.getInputStream()) {
      return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    }
  }

  private static byte[] decodePem(String pem) {
    String cleaned = pem
        .replaceAll("-----BEGIN (.*)-----", "")
        .replaceAll("-----END (.*)-----", "")
        .replaceAll("\\s", "");
    return Base64.getDecoder().decode(cleaned);
  }
}
