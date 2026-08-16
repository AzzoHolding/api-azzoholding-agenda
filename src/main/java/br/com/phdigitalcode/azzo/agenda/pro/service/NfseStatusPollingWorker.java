package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porte verbatim de {@code modules/nfse/application/NfseStatusPollingWorker.java} (Fronteira 7).
 *
 * <p>{@code TxType.NOT_SUPPORTED} do original vira {@code Propagation.NOT_SUPPORTED} — o metodo
 * orquestrador em si nao roda dentro de transacao (cada invoice tem a sua propria, via {@link
 * NfseStatusPollingInvoiceProcessor}, {@code REQUIRES_NEW}).
 */
@Service
public class NfseStatusPollingWorker {

  private static final Logger LOG = LoggerFactory.getLogger(NfseStatusPollingWorker.class);

  private final NfseStatusPollingBatchLoader nfseStatusPollingBatchLoader;
  private final NfseStatusPollingInvoiceProcessor nfseStatusPollingInvoiceProcessor;

  public NfseStatusPollingWorker(
      NfseStatusPollingBatchLoader nfseStatusPollingBatchLoader,
      NfseStatusPollingInvoiceProcessor nfseStatusPollingInvoiceProcessor) {
    this.nfseStatusPollingBatchLoader = nfseStatusPollingBatchLoader;
    this.nfseStatusPollingInvoiceProcessor = nfseStatusPollingInvoiceProcessor;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public int processarPendentes(int limit) {
    List<PendingInvoiceRef> pendentes = nfseStatusPollingBatchLoader.listPendingInvoices(Math.max(limit, 1));
    int processados = 0;
    for (PendingInvoiceRef row : pendentes) {
      if (processarInvoice(row)) {
        processados++;
      }
    }
    return processados;
  }

  private boolean processarInvoice(PendingInvoiceRef row) {
    try {
      return nfseStatusPollingInvoiceProcessor.processarInvoice(row.tenantId(), row.id());
    } catch (Exception ex) {
      LOG.warn("Falha ao acionar polling de status NFS-e invoice={} tenant={}", row.id(), row.tenantId(), ex);
      return false;
    }
  }

  public record PendingInvoiceRef(UUID tenantId, UUID id) {}
}
