package br.com.phdigitalcode.azzo.agenda.pro.exception;

import java.time.Instant;

/** Espelha {@code modules/common/api/ErrorResponse.java}. Contrato JSON de erro preservado. */
public class ErrorResponse {
  public String code;
  public String message;
  public Object details;
  public String path;
  public String timestamp;

  public ErrorResponse() {}

  public ErrorResponse(String code, String message, Object details, String path) {
    this.code = code;
    this.message = message;
    this.details = details;
    this.path = path;
    this.timestamp = Instant.now().toString();
  }
}
