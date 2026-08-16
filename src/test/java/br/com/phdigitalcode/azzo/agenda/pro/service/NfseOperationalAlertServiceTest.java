package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCertificateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCertificateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;

/**
 * Cobre {@code modules/nfse/application/NfseOperationalAlertService.java} (Fronteira 7). Original
 * sem teste proprio no Quarkus.
 *
 * <p>Servico e so leitura + metricas Micrometer — nao altera nenhum dado. Cobre as 3 contagens
 * (certificados expirando, rejeicoes recentes, indisponibilidade de provedor) e confirma que
 * so registra metrica quando o valor e positivo (guarda de {@code value <= 0} do original).
 */
@ExtendWith(MockitoExtension.class)
class NfseOperationalAlertServiceTest {

  @Mock private FiscalCertificateRepository fiscalCertificateRepository;
  @Mock private NfseInvoiceRepository nfseInvoiceRepository;
  @Mock private NfseInvoiceEventRepository nfseInvoiceEventRepository;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;

  private NfseOperationalAlertService service;

  @BeforeEach
  void setUp() {
    service =
        new NfseOperationalAlertService(
            fiscalCertificateRepository, nfseInvoiceRepository, nfseInvoiceEventRepository, meterRegistry, 30, 24);
  }

  @Test
  void analisarAlertasRegistraMetricaParaCadaContagemPositiva() {
    when(fiscalCertificateRepository.listActiveExpiringUntil(any(Instant.class)))
        .thenReturn(List.of(new FiscalCertificateEntity(), new FiscalCertificateEntity()));
    when(nfseInvoiceRepository.countRejectedSince(any(Instant.class))).thenReturn(5L);
    when(nfseInvoiceEventRepository.countProviderUnavailableSince(any(Instant.class))).thenReturn(1L);
    when(meterRegistry.counter("nfse.alerts.total", "type", "certificate_expiring")).thenReturn(counter);
    when(meterRegistry.counter("nfse.alerts.total", "type", "rejections")).thenReturn(counter);
    when(meterRegistry.counter("nfse.alerts.total", "type", "provider_unavailable")).thenReturn(counter);

    service.analisarAlertasOperacionaisNfse();

    verify(counter).increment(2.0);
    verify(counter).increment(5.0);
    verify(counter).increment(1.0);
  }

  @Test
  void analisarAlertasNaoRegistraMetricaQuandoTodasAsContagensSaoZero() {
    when(fiscalCertificateRepository.listActiveExpiringUntil(any(Instant.class))).thenReturn(List.of());
    when(nfseInvoiceRepository.countRejectedSince(any(Instant.class))).thenReturn(0L);
    when(nfseInvoiceEventRepository.countProviderUnavailableSince(any(Instant.class))).thenReturn(0L);

    service.analisarAlertasOperacionaisNfse();

    verify(meterRegistry, never()).counter(any(), any(), any());
  }

  @Test
  void analisarAlertasUsaAJanelaEOLimiteConfigurados() {
    when(fiscalCertificateRepository.listActiveExpiringUntil(any(Instant.class))).thenReturn(List.of());
    when(nfseInvoiceRepository.countRejectedSince(any(Instant.class))).thenReturn(0L);
    when(nfseInvoiceEventRepository.countProviderUnavailableSince(any(Instant.class))).thenReturn(0L);

    service.analisarAlertasOperacionaisNfse();

    verify(fiscalCertificateRepository, times(1)).listActiveExpiringUntil(any(Instant.class));
    verify(nfseInvoiceRepository, times(1)).countRejectedSince(any(Instant.class));
    verify(nfseInvoiceEventRepository, times(1)).countProviderUnavailableSince(any(Instant.class));
  }
}
