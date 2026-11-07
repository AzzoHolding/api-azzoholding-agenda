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

  /**
   * O provedor sabe dizer se um envio sem resposta gerou nota? Se sim, a nota sem resposta fica
   * {@code PENDING} e o proximo "Emitir" consulta antes de reenviar ({@link #consultarEmissao}).
   * Se nao, nao ha como saber pelo sistema: a nota volta para {@code REJECTED} com o aviso de
   * conferir no portal da prefeitura antes de emitir de novo.
   */
  default boolean consultaEmissaoAposFalha() {
    return false;
  }

  /**
   * Procura no provedor a nota gerada pelo envio ja assinado da {@code invoice}. Vazio quando o
   * provedor nao recebeu o envio (e entao reenviar e seguro).
   */
  default Optional<AuthorizationResult> consultarEmissao(NfseInvoiceEntity invoice, String certificatePassword) {
    return Optional.empty();
  }

  record AuthorizationResult(
      String providerStatusCode,
      String providerStatusMessage,
      String numeroNfse,
      String protocolo,
      String codigoVerificacao,
      String chaveAcessoNfse,
      String xmlNfse) {

    /** Provedores que nao devolvem o XML da NFS-e (ABRASF, teste). */
    AuthorizationResult(
        String providerStatusCode,
        String providerStatusMessage,
        String numeroNfse,
        String protocolo,
        String codigoVerificacao,
        String chaveAcessoNfse) {
      this(providerStatusCode, providerStatusMessage, numeroNfse, protocolo, codigoVerificacao, chaveAcessoNfse, null);
    }
  }

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
