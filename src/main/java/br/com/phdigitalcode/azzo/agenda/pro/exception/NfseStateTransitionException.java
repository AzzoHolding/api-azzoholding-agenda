package br.com.phdigitalcode.azzo.agenda.pro.exception;

import org.springframework.http.HttpStatus;

/**
 * Espelha {@code modules/nfse/domain/nfse/NfseStateTransitionException.java}, que no Quarkus
 * estende {@code WebApplicationException} com {@code Response.Status.CONFLICT} — mesmo padrao de
 * {@link FiscalStateTransitionException}.
 */
public class NfseStateTransitionException extends ApiClientErrorException {

  public NfseStateTransitionException(String message) {
    super(message, HttpStatus.CONFLICT.value());
  }
}
