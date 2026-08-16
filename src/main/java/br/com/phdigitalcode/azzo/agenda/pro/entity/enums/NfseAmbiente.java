package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

/**
 * Espelha {@code modules/nfse/domain/nfse/NfseAmbiente.java}. Usado como {@code @Enumerated(STRING)}
 * diretamente nas entidades {@code nfse_configs}/{@code nfse_invoices} — diferente de
 * {@link FiscalDocumentStatus}, que e texto livre parseado via {@code tryParse}.
 */
public enum NfseAmbiente {
  HOMOLOGACAO,
  PRODUCAO
}
