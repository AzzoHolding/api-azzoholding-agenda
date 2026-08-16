package br.com.phdigitalcode.azzo.agenda.pro.exception;

/**
 * PLACEHOLDER — espelha {@code modules/fiscal/infrastructure/fiscal/FiscalProviderException.java}.
 * Ver nota em {@link AsaasException}. Mover para {@code integration/fiscal} quando o modulo
 * {@code fiscal}/{@code nfse} for migrado.
 */
public class FiscalProviderException extends RuntimeException {

  private final int statusCode;

  public FiscalProviderException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  public FiscalProviderException(String message, int statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
