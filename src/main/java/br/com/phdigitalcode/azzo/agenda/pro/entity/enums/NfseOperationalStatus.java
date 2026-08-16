package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

/**
 * Espelha {@code modules/nfse/domain/nfse/NfseOperationalStatus.java}. Flags operacionais
 * paralelas ao fluxo de {@link NfseFiscalStatus} (nao e um fluxo em si).
 */
public enum NfseOperationalStatus {
  PENDING_PASSWORD_UNLOCK,
  PROCESSING_PDF,
  PDF_ERROR,
  WAITING_PROVIDER,
  RETRY_SCHEDULED
}
