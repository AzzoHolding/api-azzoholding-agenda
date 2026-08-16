package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutIntentRepository;

/** Espelha {@code modules/billing/application/CheckoutIntentMaintenanceService.java}. */
@Service
public class CheckoutIntentMaintenanceService {

  private static final Logger LOG =
      LoggerFactory.getLogger(CheckoutIntentMaintenanceService.class);

  private final CheckoutIntentRepository checkoutIntentRepository;

  public CheckoutIntentMaintenanceService(CheckoutIntentRepository checkoutIntentRepository) {
    this.checkoutIntentRepository = checkoutIntentRepository;
  }

  @Transactional
  public long expirePendingIntents() {
    Instant now = Instant.now();
    LOG.info("CheckoutIntentExpiration iniciado. referencia={}", now);
    long updated =
        checkoutIntentRepository.expirarPendentes(
            StatusCheckout.EXPIRED, StatusCheckout.PENDING, now);
    LOG.info("CheckoutIntentExpiration finalizado. expirados={}", updated);
    return updated;
  }
}
