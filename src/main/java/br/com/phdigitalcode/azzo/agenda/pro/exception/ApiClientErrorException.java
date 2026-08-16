package br.com.phdigitalcode.azzo.agenda.pro.exception;

/**
 * Equivalente Spring de {@code jakarta.ws.rs.ClientErrorException} (JAX-RS): permite que a camada
 * de servico lance uma excecao com mensagem + status HTTP explicitos (ex.: 401, 403, 428, 429),
 * capturada pelo {@link GlobalExceptionHandler} exatamente como o Quarkus original tratava
 * {@code WebApplicationException}/{@code ClientErrorException} (ver risco 1 do inventario).
 */
public class ApiClientErrorException extends RuntimeException {

  private final int status;

  public ApiClientErrorException(String message, int status) {
    super(message);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }
}
