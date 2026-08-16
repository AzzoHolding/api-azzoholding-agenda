package br.com.phdigitalcode.azzo.agenda.pro.exception;

import org.springframework.http.HttpStatus;

/**
 * Espelha {@code modules/fiscal/domain/fiscal/FiscalStateTransitionException.java}, que no Quarkus
 * estende {@code WebApplicationException} com {@code Response.Status.CONFLICT}.
 *
 * <p>Aqui estende {@link ApiClientErrorException} fixando <b>409</b>, para cair no mesmo handler
 * global das demais falhas de cliente e sair com o mesmo corpo de erro.
 */
public class FiscalStateTransitionException extends ApiClientErrorException {

  public FiscalStateTransitionException(String message) {
    super(message, HttpStatus.CONFLICT.value());
  }
}
