package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;

/**
 * Porte verbatim de {@code modules/nfse/application/NfseStatusPollingBatchLoader.java} (Fronteira
 * 7).
 *
 * <p>{@code REQUIRES_NEW} aqui e chamado de {@link NfseStatusPollingWorker}, um bean diferente —
 * nao e auto-invocacao, entao a anotacao {@code @Transactional} do Spring funciona normalmente
 * (proxy entra em cena), sem precisar de {@code TransactionTemplate}.
 */
@Service
public class NfseStatusPollingBatchLoader {

  private final NfseInvoiceRepository nfseInvoiceRepository;

  public NfseStatusPollingBatchLoader(NfseInvoiceRepository nfseInvoiceRepository) {
    this.nfseInvoiceRepository = nfseInvoiceRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<NfseStatusPollingWorker.PendingInvoiceRef> listPendingInvoices(int limit) {
    return nfseInvoiceRepository.listPendingForStatusPolling(Math.max(limit, 1)).stream()
        .map(
            (NfseInvoiceEntity row) ->
                new NfseStatusPollingWorker.PendingInvoiceRef(row.getTenantId(), row.getId()))
        .toList();
  }
}
