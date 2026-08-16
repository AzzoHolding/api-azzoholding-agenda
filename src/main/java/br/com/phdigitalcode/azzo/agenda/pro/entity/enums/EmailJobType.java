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
  LICENSE_EXPIRING_SOON
}
