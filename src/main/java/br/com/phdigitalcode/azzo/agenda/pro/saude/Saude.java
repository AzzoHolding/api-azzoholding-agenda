package br.com.phdigitalcode.azzo.agenda.pro.saude;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utilitarios do modulo de anamnese e ficha (plano PLANO-ANAMNESE-E-FICHA.md): JSON (Jackson 2, como o resto das
 * classes que guardam JSONB), hash SHA-256 e conversoes de linha do banco.
 */
public final class Saude {

  static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  /** Tipos de alerta (o mesmo CHECK de alertas_do_cliente). */
  public static final List<String> TIPOS_DE_ALERTA = List.of("ALERGIA", "CONTRAINDICACAO", "RESTRICAO", "OBSERVACAO");

  private Saude() {}

  public static String json(Object valor) {
    try {
      return JSON.writeValueAsString(valor);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao gerar JSON.", e);
    }
  }

  public static List<Map<String, Object>> lista(Object jsonb) {
    if (jsonb == null) return new ArrayList<>();
    try {
      return JSON.readValue(jsonb.toString(), new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("JSON invalido no banco.", e);
    }
  }

  public static List<String> textos(Object jsonb) {
    if (jsonb == null) return new ArrayList<>();
    try {
      return JSON.readValue(jsonb.toString(), new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("JSON invalido no banco.", e);
    }
  }

  public static Map<String, Object> mapa(Object jsonb) {
    if (jsonb == null) return new LinkedHashMap<>();
    try {
      return JSON.readValue(jsonb.toString(), new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("JSON invalido no banco.", e);
    }
  }

  public static String sha256(String texto) {
    return sha256(texto == null ? new byte[0] : texto.getBytes(StandardCharsets.UTF_8));
  }

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 indisponivel.", e);
    }
  }

  static String texto(Object v) {
    return v == null ? null : v.toString();
  }

  static String instante(Object v) {
    if (v == null) return null;
    if (v instanceof Timestamp t) return t.toInstant().toString();
    if (v instanceof Instant i) return i.toString();
    if (v instanceof java.time.OffsetDateTime o) return o.toInstant().toString();
    return v.toString();
  }

  static UUID uuid(Object v) {
    if (v == null) return null;
    if (v instanceof UUID u) return u;
    String s = v.toString().trim();
    return s.isEmpty() ? null : UUID.fromString(s);
  }

  static UUID uuidOuNulo(String v) {
    try {
      return v == null || v.isBlank() ? null : UUID.fromString(v.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Identificador inválido.");
    }
  }

  static String limpar(String v, int maximo) {
    if (v == null) return null;
    String t = v.trim();
    if (t.isEmpty()) return null;
    return t.length() > maximo ? t.substring(0, maximo) : t;
  }

  static Map<String, Object> m(Object... pares) {
    Map<String, Object> r = new LinkedHashMap<>();
    for (int i = 0; i + 1 < pares.length; i += 2) r.put(String.valueOf(pares[i]), pares[i + 1]);
    return r;
  }
}
