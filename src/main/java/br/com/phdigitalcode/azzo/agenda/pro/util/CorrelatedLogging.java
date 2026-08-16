package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.util.Locale;

import org.slf4j.MDC;

/**
 * Espelha {@code infrastructure/observability/CorrelatedLogging.java} do Quarkus original.
 *
 * <p>DIFERENCA DELIBERADA: o original le o traceId via {@code org.apache.skywalking.apm.toolkit
 * .trace.TraceContext} (dependencia do toolkit SkyWalking). Como o agente SkyWalking roda fora do
 * processo Maven (via {@code -javaagent}, independente do framework — ver tabela da Etapa 2 do
 * inventario, linha "Log estruturado"), e o toolkit de trace nao faz parte do escopo de
 * seguranca/auth desta etapa, o traceId aqui e lido do MDC (chave {@code traceId}), que e onde o
 * agente SkyWalking (ou um filtro de correlacao futuro) deve publicar o valor. Revisar quando o
 * modulo de observability for portado.
 */
public final class CorrelatedLogging {

  private CorrelatedLogging() {}

  public static String traceId() {
    String traceId = MDC.get("traceId");
    if (traceId == null || traceId.isBlank() || "N/A".equalsIgnoreCase(traceId)) {
      return "N/A";
    }
    return traceId;
  }

  public static String context(Object... keyValues) {
    StringBuilder builder = new StringBuilder("traceId=").append(traceId());
    if (keyValues == null) {
      return builder.toString();
    }
    for (int index = 0; index + 1 < keyValues.length; index += 2) {
      String key = keyValues[index] == null ? "unknown" : keyValues[index].toString();
      Object value = keyValues[index + 1];
      builder.append(' ').append(key).append('=').append(formatValue(key, value));
    }
    return builder.toString();
  }

  public static String sanitize(String value) {
    if (value == null) return null;
    return LogSanitizer.sanitizeLogMessage(maskSecrets(value));
  }

  public static String throwableSummary(Throwable throwable) {
    if (throwable == null) return null;
    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String message = root.getMessage() == null || root.getMessage().isBlank()
        ? root.getClass().getSimpleName()
        : root.getClass().getSimpleName() + ": " + root.getMessage();
    return sanitize(message);
  }

  private static String formatValue(String key, Object value) {
    if (value == null) return "null";
    String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
    String stringValue = value.toString();
    if (stringValue.isBlank()) return "\"\"";
    if (normalizedKey.contains("email")) return LogSanitizer.sanitizeEmail(stringValue);
    if (normalizedKey.contains("phone")) return LogSanitizer.sanitizePhone(stringValue);
    if (normalizedKey.contains("cpf") || normalizedKey.contains("cnpj") || normalizedKey.contains("document")) {
      return maskDocument(stringValue);
    }
    if (normalizedKey.contains("token") || normalizedKey.contains("secret") || normalizedKey.contains("password")) {
      return maskIdentifier(stringValue);
    }
    return sanitize(stringValue);
  }

  private static String maskSecrets(String value) {
    String sanitized = value;
    sanitized = sanitized.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._\\-+/=]+", "$1***");
    sanitized = sanitized.replaceAll("(?i)(token=)[A-Za-z0-9._\\-+/=]+", "$1***");
    sanitized = sanitized.replaceAll("(?i)(password=)[^\\s]+", "$1***");
    return sanitized;
  }

  private static String maskDocument(String value) {
    String digits = value.replaceAll("\\D", "");
    if (digits.length() == 11) return LogSanitizer.sanitizeCpf(digits);
    if (digits.length() == 14) return "**.***.***/****-" + digits.substring(12);
    return maskIdentifier(value);
  }

  private static String maskIdentifier(String value) {
    if (value == null || value.isBlank()) return value;
    String trimmed = value.trim();
    if (trimmed.length() <= 6) return "***";
    return trimmed.substring(0, 3) + "***" + trimmed.substring(trimmed.length() - 3);
  }
}
