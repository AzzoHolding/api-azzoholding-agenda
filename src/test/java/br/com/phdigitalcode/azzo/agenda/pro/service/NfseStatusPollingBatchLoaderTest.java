package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;

/**
 * Cobre {@code modules/nfse/application/NfseStatusPollingBatchLoader.java} (Fronteira 7). Original
 * sem teste proprio no Quarkus.
 */
@ExtendWith(MockitoExtension.class)
class NfseStatusPollingBatchLoaderTest {

  @Mock private NfseInvoiceRepository nfseInvoiceRepository;

  @Test
  void listPendingInvoicesMapeiaTenantEIdDeCadaInvoicePendente() {
    NfseInvoiceEntity invoice1 = new NfseInvoiceEntity();
    invoice1.setId(UUID.randomUUID());
    invoice1.setTenantId(UUID.randomUUID());
    NfseInvoiceEntity invoice2 = new NfseInvoiceEntity();
    invoice2.setId(UUID.randomUUID());
    invoice2.setTenantId(UUID.randomUUID());
    when(nfseInvoiceRepository.listPendingForStatusPolling(20)).thenReturn(List.of(invoice1, invoice2));

    NfseStatusPollingBatchLoader loader = new NfseStatusPollingBatchLoader(nfseInvoiceRepository);
    List<NfseStatusPollingWorker.PendingInvoiceRef> pendentes = loader.listPendingInvoices(20);

    assertThat(pendentes)
        .containsExactly(
            new NfseStatusPollingWorker.PendingInvoiceRef(invoice1.getTenantId(), invoice1.getId()),
            new NfseStatusPollingWorker.PendingInvoiceRef(invoice2.getTenantId(), invoice2.getId()));
  }

  @Test
  void listPendingInvoicesUsaPisoDeUmParaOLimite() {
    when(nfseInvoiceRepository.listPendingForStatusPolling(1)).thenReturn(List.of());

    NfseStatusPollingBatchLoader loader = new NfseStatusPollingBatchLoader(nfseInvoiceRepository);
    loader.listPendingInvoices(0);

    org.mockito.Mockito.verify(nfseInvoiceRepository).listPendingForStatusPolling(1);
  }
}
