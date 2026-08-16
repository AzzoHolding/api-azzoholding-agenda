package br.com.phdigitalcode.azzo.agenda.pro.exception;

/**
 * PLACEHOLDER — espelha {@code modules/scheduling/application/AppointmentConflictException.java}.
 * Ver nota em {@link AsaasException}. No original o campo {@code details} e tipado como
 * {@code AppointmentConflictDetailsResponse} (DTO do modulo {@code scheduling}, ainda nao
 * migrado); aqui usamos {@code Object} para nao acoplar este dominio fundacional a um modulo de
 * negocio. Restaurar o tipo especifico quando {@code scheduling} for portado.
 */
public class AppointmentConflictException extends RuntimeException {

  private final Object details;

  public AppointmentConflictException(String message, Object details) {
    super(message);
    this.details = details;
  }

  public Object getDetails() {
    return details;
  }
}
