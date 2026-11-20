package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

/**
 * Espelha {@code modules/email/domain/entity/EmailJobType.java}.
 *
 * <p>{@code LICENSE_EXPIRING_SOON} existe no original mas nunca e usado para criar um {@code
 * EmailJob} — o alerta de licenca expirando envia direto via {@code CredentialsEmailService},
 * sem passar pela fila. Preservado tal como esta (assimetria do original, nao "consertar").
 */
public enum EmailJobType {
  PASSWORD_RESET,
  EMAIL_VERIFICATION,
  LICENSE_EXPIRING_SOON,
  /** Conta da cliente (area da cliente): confirmar e-mail, senha nova e avisos, com texto proprio. */
  CLIENTE_AVISO
}
