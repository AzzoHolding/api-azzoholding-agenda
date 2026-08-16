package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cobre {@code modules/nfse/application/NfseStatusPollingWorker.java} (Fronteira 7). Original sem
 * teste proprio no Quarkus.
 */
@ExtendWith(MockitoExtension.class)
class NfseStatusPollingWorkerTest {

  @Mock private NfseStatusPollingBatchLoader nfseStatusPollingBatchLoader;
  @Mock private NfseStatusPollingInvoiceProcessor nfseStatusPollingInvoiceProcessor;

  private NfseStatusPollingWorker worker;

  @BeforeEach
  void setUp() {
    worker = new NfseStatusPollingWorker(nfseStatusPollingBatchLoader, nfseStatusPollingInvoiceProcessor);
  }

  @Test
  void processarPendentesContaSomenteAsInvoicesProcessadasComSucesso() {
    NfseStatusPollingWorker.PendingInvoiceRef ref1 =
        new NfseStatusPollingWorker.PendingInvoiceRef(UUID.randomUUID(), UUID.randomUUID());
    NfseStatusPollingWorker.PendingInvoiceRef ref2 =
        new NfseStatusPollingWorker.PendingInvoiceRef(UUID.randomUUID(), UUID.randomUUID());
    when(nfseStatusPollingBatchLoader.listPendingInvoices(20)).thenReturn(List.of(ref1, ref2));
    when(nfseStatusPollingInvoiceProcessor.processarInvoice(ref1.tenantId(), ref1.id())).thenReturn(true);
    when(nfseStatusPollingInvoiceProcessor.processarInvoice(ref2.tenantId(), ref2.id())).thenReturn(false);

    int processados = worker.processarPendentes(20);

    assertThat(processados).isEqualTo(1);
  }

  @Test
  void processarPendentesNaoPropagaExcecaoDeUmaInvoiceEContinuaAsOutras() {
    NfseStatusPollingWorker.PendingInvoiceRef ref1 =
        new NfseStatusPollingWorker.PendingInvoiceRef(UUID.randomUUID(), UUID.randomUUID());
    NfseStatusPollingWorker.PendingInvoiceRef ref2 =
        new NfseStatusPollingWorker.PendingInvoiceRef(UUID.randomUUID(), UUID.randomUUID());
    when(nfseStatusPollingBatchLoader.listPendingInvoices(20)).thenReturn(List.of(ref1, ref2));
    when(nfseStatusPollingInvoiceProcessor.processarInvoice(ref1.tenantId(), ref1.id()))
        .thenThrow(new RuntimeException("falha pontual"));
    when(nfseStatusPollingInvoiceProcessor.processarInvoice(ref2.tenantId(), ref2.id())).thenReturn(true);

    int processados = worker.processarPendentes(20);

    assertThat(processados).isEqualTo(1);
  }

  @Test
  void processarPendentesUsaPisoDeUmParaOLimite() {
    when(nfseStatusPollingBatchLoader.listPendingInvoices(1)).thenReturn(List.of());

    worker.processarPendentes(0);

    org.mockito.Mockito.verify(nfseStatusPollingBatchLoader).listPendingInvoices(1);
  }
}
