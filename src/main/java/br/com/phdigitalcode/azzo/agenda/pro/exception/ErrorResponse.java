package br.com.phdigitalcode.azzo.agenda.pro.exception;

import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import java.time.Instant;

/** Espelha {@code modules/common/api/ErrorResponse.java}. Contrato JSON de erro preservado. */
public class ErrorResponse {
  public String code;
  public String message;
  public Object details;
  public String path;
  public String timestamp;

  /**
   * Id do trace da requisicao que falhou (null sem tracing). Quem reporta o erro passa este codigo
   * e ele abre, no Grafana, o caminho da requisicao (Tempo) e as linhas de log (Loki).
   */
  public String traceId;

  public ErrorResponse() {}

  public ErrorResponse(String code, String message, Object details, String path) {
    this.code = code;
    this.message = message;
    this.details = details;
    this.path = path;
    this.timestamp = Instant.now().toString();
    String atual = CorrelatedLogging.traceId();
    this.traceId = "N/A".equals(atual) ? null : atual;
  }
}
