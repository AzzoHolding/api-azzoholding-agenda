package br.com.phdigitalcode.azzo.agenda.pro.util;

/**
 * Porte verbatim de {@code shared/IntegrationLogSanitizer.java}.
 *
 * <p>Sanitiza payloads antes de persistir em {@code integration_logs}:
 *
 * <ol>
 *   <li>truncamento de payloads acima de 10 KB (evita retencao excessiva de dados);
 *   <li>redacao de CPF, e-mail e telefone usando {@link LogSanitizer}.
 * </ol>
 *
 * <p>LGPD — Lei 13.709/2018, art. 46: medida tecnica para proteger dados pessoais armazenados em
 * logs de integracao com terceiros (Asaas, FISCAL etc.).
 */
public final class IntegrationLogSanitizer {

  private static final int MAX_PAYLOAD_BYTES = 10_240;
  private static final String TRUNCATED_SUFFIX = "...[truncado]";

  private IntegrationLogSanitizer() {}

  /**
   * Sanitiza um payload de log de integracao: trunca acima de 10 KB e redige PII conhecida (CPF,
   * e-mail, telefone).
   */
  public static String sanitize(String payload) {
    if (payload == null) return null;
    String result =
        payload.length() > MAX_PAYLOAD_BYTES
            ? payload.substring(0, MAX_PAYLOAD_BYTES) + TRUNCATED_SUFFIX
            : payload;
    return LogSanitizer.sanitizeLogMessage(result);
  }
}
