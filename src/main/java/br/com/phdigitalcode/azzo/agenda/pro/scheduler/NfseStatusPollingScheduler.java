package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.NfseStatusPollingWorker;

/**
 * Espelha {@code modules/nfse/infrastructure/scheduler/NfseStatusPollingScheduler.java}
 * ({@code @Scheduled(delayed = "${app.nfse.status-polling.delayed:45s}", every =
 * "${app.nfse.status-polling.every:30s}", concurrentExecution = SKIP)}).
 *
 * <p>Os dois intervalos sao configuraveis no original via {@code @ConfigProperty} — aqui viram
 * {@code fixedDelayString}/{@code initialDelayString} resolvidos por property placeholder, com o
 * mesmo default (30s/45s), em formato ISO-8601 (unico formato que {@code @Scheduled} aceita de
 * forma confiavel neste projeto — os demais schedulers usam literais {@code PT..}).
 */
@Component
public class NfseStatusPollingScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(NfseStatusPollingScheduler.class);

  private final NfseStatusPollingWorker nfseStatusPollingWorker;
  private final int batchSize;

  public NfseStatusPollingScheduler(
      NfseStatusPollingWorker nfseStatusPollingWorker,
      @Value("${app.nfse.status-polling.batch-size:20}") int batchSize) {
    this.nfseStatusPollingWorker = nfseStatusPollingWorker;
    this.batchSize = batchSize;
  }

  @Scheduled(
      fixedDelayString = "${app.nfse.status-polling.every:PT30S}",
      initialDelayString = "${app.nfse.status-polling.delayed:PT45S}")
  void processarStatusPendentes() {
    int processados = nfseStatusPollingWorker.processarPendentes(Math.max(batchSize, 1));
    if (processados > 0) {
      LOG.info("NFS-e status polling processou {} nota(s).", processados);
    }
  }
}
