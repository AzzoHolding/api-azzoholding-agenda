package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.Optional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;

/**
 * Porte verbatim de {@code modules/nfse/domain/provider/NfseProviderAdapter.java}.
 *
 * <p>SPI implementada por {@code AbrasfDirectProviderAdapter} (SOAP puro, sem mTLS de transporte)
 * e {@code SefinNacionalProviderAdapter} (mTLS real contra {@code sefin.nfse.gov.br}) — ambas
 * portadas na Fronteira 5. Este contrato entra na Fronteira 1 porque {@code NfseService} (Fronteira
 * 6) e os testes das fronteiras seguintes ja o referenciam.
 */
public interface NfseProviderAdapter {

  String providerCode();

  AuthorizationResult authorize(NfseInvoiceEntity invoice, String certificatePassword);

  CancellationResult cancel(NfseInvoiceEntity invoice, String reason, String certificatePassword);

  default Optional<StatusQueryResult> queryStatus(NfseInvoiceEntity invoice) {
    return Optional.empty();
  }

  record AuthorizationResult(
      String providerStatusCode,
      String providerStatusMessage,
      String numeroNfse,
      String protocolo,
      String codigoVerificacao,
      String chaveAcessoNfse) {}

  record CancellationResult(String providerStatusCode, String providerStatusMessage) {}

  record StatusQueryResult(
      String providerStatusCode,
      String providerStatusMessage,
      String numeroNfse,
      String protocolo,
      String codigoVerificacao,
      String chaveAcessoNfse,
      boolean authorized,
      boolean rejected,
      boolean pending) {}
}
