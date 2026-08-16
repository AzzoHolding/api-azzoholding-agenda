package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;

import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCertificateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;

/**
 * Porte verbatim de {@code modules/nfse/application/NfseOperationalAlertService.java} (Fronteira
 * 7).
 *
 * <p>Analisa alertas operacionais (certificados expirando, rejeicoes recentes, indisponibilidade
 * de provedor) e registra metricas Micrometer, sem alterar dado nenhum — leitura pura, acionada
 * por {@link br.com.phdigitalcode.azzo.agenda.pro.scheduler.NfseOperationalAlertScheduler}.
 */
@Service
public class NfseOperationalAlertService {

  private static final Logger LOG = LoggerFactory.getLogger(NfseOperationalAlertService.class);

  private final FiscalCertificateRepository fiscalCertificateRepository;
  private final NfseInvoiceRepository nfseInvoiceRepository;
  private final NfseInvoiceEventRepository nfseInvoiceEventRepository;
  private final MeterRegistry meterRegistry;
  private final int certificateExpireDays;
  private final int alertWindowHours;

  public NfseOperationalAlertService(
      FiscalCertificateRepository fiscalCertificateRepository,
      NfseInvoiceRepository nfseInvoiceRepository,
      NfseInvoiceEventRepository nfseInvoiceEventRepository,
      MeterRegistry meterRegistry,
      @Value("${app.nfse.alerts.certificate-expire-days:30}") int certificateExpireDays,
      @Value("${app.nfse.alerts.window-hours:24}") int alertWindowHours) {
    this.fiscalCertificateRepository = fiscalCertificateRepository;
    this.nfseInvoiceRepository = nfseInvoiceRepository;
    this.nfseInvoiceEventRepository = nfseInvoiceEventRepository;
    this.meterRegistry = meterRegistry;
    this.certificateExpireDays = certificateExpireDays;
    this.alertWindowHours = alertWindowHours;
  }

  @Transactional(readOnly = true)
  public void analisarAlertasOperacionaisNfse() {
    Instant now = Instant.now();
    Instant certificateLimit = now.plus(Math.max(certificateExpireDays, 1), ChronoUnit.DAYS);
    Instant threshold = now.minus(Math.max(alertWindowHours, 1), ChronoUnit.HOURS);

    LOG.info("NfseOperationalAlert iniciado. certificateLimit={} threshold={}", certificateLimit, threshold);
    long certificadosExpirando = fiscalCertificateRepository.listActiveExpiringUntil(certificateLimit).size();
    long notasRejeitadas = nfseInvoiceRepository.countRejectedSince(threshold);
    long indisponibilidades = nfseInvoiceEventRepository.countProviderUnavailableSince(threshold);

    registrarMetrica("certificate_expiring", certificadosExpirando);
    registrarMetrica("rejections", notasRejeitadas);
    registrarMetrica("provider_unavailable", indisponibilidades);

    if (certificadosExpirando > 0) {
      LOG.warn(
          "nfse_alert certificate_expiring count={} windowDays={}", certificadosExpirando, certificateExpireDays);
    }
    if (notasRejeitadas > 0) {
      LOG.warn("nfse_alert invoice_rejections count={} windowHours={}", notasRejeitadas, alertWindowHours);
    }
    if (indisponibilidades > 0) {
      LOG.warn("nfse_alert provider_unavailable count={} windowHours={}", indisponibilidades, alertWindowHours);
    }
    LOG.info(
        "NfseOperationalAlert finalizado. certificateExpiring={} rejections={} providerUnavailable={}",
        certificadosExpirando,
        notasRejeitadas,
        indisponibilidades);
  }

  private void registrarMetrica(String alertType, long value) {
    if (meterRegistry == null || value <= 0) return;
    meterRegistry.counter("nfse.alerts.total", "type", alertType).increment(value);
  }
}
