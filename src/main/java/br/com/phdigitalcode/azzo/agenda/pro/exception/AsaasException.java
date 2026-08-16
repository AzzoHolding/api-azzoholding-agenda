package br.com.phdigitalcode.azzo.agenda.pro.exception;

/**
 * PLACEHOLDER — espelha {@code infrastructure/payment/AsaasException.java} do Quarkus original.
 *
 * <p>Mantido aqui (fora do modulo {@code billing}, ainda nao migrado) apenas para preservar a
 * ordem de precedencia de excecoes do {@link GlobalExceptionHandler}
 * (ver risco 1 do inventario: ConstraintViolationException > AsaasException > FiscalProviderException
 * > CnpjApiIndisponivelException > AppointmentConflictException > erro generico). Quando o modulo
 * {@code billing}/pagamentos (integracao Asaas) for portado, mover esta classe para
 * {@code integration/payment} e remover este placeholder.
 */
public class AsaasException extends RuntimeException {

  private final int statusCode;

  public AsaasException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  public AsaasException(String message, int statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
