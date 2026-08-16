package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.LicenseExpirationNotificationService;

/**
 * Espelha {@code modules/billing/infrastructure/scheduler/
 * LicenseExpirationNotificationScheduler.java}
 * ({@code @Scheduled(cron = "0 0 8 * * ?", timeZone = "America/Sao_Paulo")}).
 *
 * <p>Unico scheduler do projeto com {@code cron} em vez de {@code fixedDelay}: o original dispara
 * em horario fixo (08:00 no fuso de Sao Paulo), nao em intervalo. O {@code ?} no campo de dia da
 * semana e aceito igual pelo Quarkus e pelo Spring.
 *
 * <p>O corpo vive em {@link LicenseExpirationNotificationService} — mesmo padrao de
 * {@link AppointmentNoShowScheduler} e {@link ClientMembershipCancellationScheduler}: assim o
 * {@code @Transactional} passa pelo proxy do Spring em vez de depender de auto-invocacao.
 */
@Component
public class LicenseExpirationNotificationScheduler {

  private static final Logger LOG =
      LoggerFactory.getLogger(LicenseExpirationNotificationScheduler.class);

  private final LicenseExpirationNotificationService licenseExpirationNotificationService;

  public LicenseExpirationNotificationScheduler(
      LicenseExpirationNotificationService licenseExpirationNotificationService) {
    this.licenseExpirationNotificationService = licenseExpirationNotificationService;
  }

  @Scheduled(cron = "0 0 8 * * ?", zone = "America/Sao_Paulo")
  void notificarVencimentos() {
    try {
      licenseExpirationNotificationService.notificarVencimentos();
    } catch (Exception e) {
      LOG.error("LicenseExpirationNotificationScheduler falhou.", e);
      throw e;
    }
  }
}
