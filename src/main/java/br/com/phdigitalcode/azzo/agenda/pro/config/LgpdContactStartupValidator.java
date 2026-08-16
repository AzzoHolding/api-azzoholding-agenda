package br.com.phdigitalcode.azzo.agenda.pro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Espelha {@code modules/lgpd/infrastructure/LgpdContactStartupValidator.java} (Quarkus
 * {@code @Observes StartupEvent} -> Spring {@code ApplicationReadyEvent}).
 *
 * <p>Le as variaveis de ambiente <b>brutas</b> ({@code LGPD_CONTACT_EMAIL}/
 * {@code LGPD_CONTACT_CHANNEL}/{@code LGPD_CONTACT_RESPONSE_SLA}), nao as propriedades
 * {@code app.lgpd.contact.*} (que tem fallback default e por isso mascarariam a ausencia). Falha
 * fail-closed em <b>qualquer</b> ambiente (nao so em producao) se qualquer uma das tres estiver
 * ausente/em branco/"unset" — igual ao original, sem distincao de profile.
 *
 * <p>Isso e intencionalmente uma segunda checagem, mais estrita, sobreposta a
 * {@link StartupSecurityValidator} (que ja valida só {@code app.lgpd.contact.email}, e so falha em
 * producao) — a mesma duplicacao de gates existe no Quarkus original entre
 * {@code infrastructure/security/StartupSecurityValidator} e
 * {@code modules/lgpd/infrastructure/LgpdContactStartupValidator}, preservada aqui por fidelidade.
 */
@Component
public class LgpdContactStartupValidator {

  private static final Logger LOG = LoggerFactory.getLogger(LgpdContactStartupValidator.class);
  private static final String ERROR_MESSAGE =
      "LGPD contact configuration missing. Configure LGPD_CONTACT_EMAIL, LGPD_CONTACT_CHANNEL and LGPD_CONTACT_RESPONSE_SLA.";

  @Value("${LGPD_CONTACT_EMAIL:}")
  private String lgpdContactEmail;

  @Value("${LGPD_CONTACT_CHANNEL:}")
  private String lgpdContactChannel;

  @Value("${LGPD_CONTACT_RESPONSE_SLA:}")
  private String lgpdContactResponseSla;

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    if (isInvalid(lgpdContactEmail) || isInvalid(lgpdContactChannel) || isInvalid(lgpdContactResponseSla)) {
      LOG.error(ERROR_MESSAGE);
      throw new IllegalStateException(ERROR_MESSAGE);
    }
  }

  private boolean isInvalid(String value) {
    if (value == null) return true;
    String normalized = value.trim();
    if (normalized.isBlank()) return true;
    return "unset".equalsIgnoreCase(normalized) || "__unset__".equalsIgnoreCase(normalized);
  }
}
