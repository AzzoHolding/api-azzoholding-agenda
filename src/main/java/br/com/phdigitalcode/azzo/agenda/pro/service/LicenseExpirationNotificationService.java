package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CredentialsEmailService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Espelha o corpo de {@code modules/billing/infrastructure/scheduler/
 * LicenseExpirationNotificationScheduler.java}. O gatilho {@code @Scheduled} fica em
 * {@code scheduler/LicenseExpirationNotificationScheduler}, seguindo o padrao adotado nas etapas
 * anteriores (scheduler fino + service transacional) e evitando que a transacao dependa de
 * auto-invocacao.
 *
 * <p>Avisa os OWNERs do tenant quando faltam 7, 3 ou 1 dia para o pedido confirmado vencer. Cada
 * janela e um dia UTC cheio: {@code now + N dias} truncado para o inicio do dia, ate o dia
 * seguinte. Um tenant cujo vencimento caia em duas janelas (impossivel com 7/3/1) receberia dois
 * e-mails — o original nao deduplica.
 *
 * <p><b>⚠️ DEFEITO PRESERVADO DO ORIGINAL.</b> A consulta de OWNERs filtra {@code u.active = true},
 * mas <b>nao existe</b> campo {@code active} em {@code Usuario} nem coluna {@code active} na tabela
 * {@code users} (ver {@code V1__baseline_unified.sql}). No Quarkus a JPQL estoura ao ser criada, a
 * excecao cai no {@code catch} por tenant e vira um {@code warn} — ou seja, <b>este scheduler nunca
 * enviou um unico e-mail em producao</b>, e o fallback para o e-mail do tenant tambem nunca roda,
 * por estar depois da consulta. O porte mantem a mesma JPQL (via {@link EntityManager}, dinamica,
 * para falhar em runtime e nao na inicializacao) para nao alterar comportamento por conta propria.
 * Basta remover o predicado {@code and u.active = true} para o recurso passar a funcionar —
 * decisao de produto, sinalizada e nao tomada aqui.
 */
@Service
public class LicenseExpirationNotificationService {

  private static final Logger LOG =
      LoggerFactory.getLogger(LicenseExpirationNotificationService.class);
  private static final Set<Integer> ALERT_DAYS = Set.of(7, 3, 1);
  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Sao_Paulo"));

  private final CheckoutOrderRepository checkoutOrderRepository;
  private final TenantRepository tenantRepository;
  private final CredentialsEmailService credentialsEmailService;

  @PersistenceContext private EntityManager entityManager;

  public LicenseExpirationNotificationService(
      CheckoutOrderRepository checkoutOrderRepository,
      TenantRepository tenantRepository,
      CredentialsEmailService credentialsEmailService) {
    this.checkoutOrderRepository = checkoutOrderRepository;
    this.tenantRepository = tenantRepository;
    this.credentialsEmailService = credentialsEmailService;
  }

  @Transactional
  public void notificarVencimentos() {
    if (!credentialsEmailService.isEnabled()) {
      LOG.debug("Email desabilitado; notificacoes de vencimento nao serao enviadas.");
      return;
    }

    Instant now = Instant.now();

    for (int dias : ALERT_DAYS) {
      Instant inicioJanela = now.plus(dias, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      Instant fimJanela = inicioJanela.plus(1, ChronoUnit.DAYS);

      List<CheckoutOrder> orders =
          checkoutOrderRepository.listarVencendoNaJanela(
              StatusCheckout.CONFIRMED, inicioJanela, fimJanela);

      for (CheckoutOrder order : orders) {
        if (order.getTenantId() == null) continue;
        try {
          notificarTenant(order, dias);
        } catch (Exception e) {
          LOG.warn(
              "Falha ao enviar notificacao de vencimento tenantId={} dias={}",
              order.getTenantId(),
              dias,
              e);
        }
      }

      if (!orders.isEmpty()) {
        LOG.info(
            "Notificacoes de vencimento enviadas: {} tenant(s) com {} dia(s) restante(s).",
            orders.size(),
            dias);
      }
    }
  }

  private void notificarTenant(CheckoutOrder order, int diasRestantes) {
    Tenant tenant = tenantRepository.findById(order.getTenantId()).orElse(null);
    if (tenant == null) return;

    // Ver o aviso no javadoc da classe: `u.active` nao existe — esta consulta estoura de proposito,
    // exatamente como no original, e a excecao e engolida pelo catch do chamador.
    List<Usuario> owners =
        entityManager
            .createQuery(
                "SELECT u FROM Usuario u WHERE u.tenantId = :tid AND u.role = :role"
                    + " AND u.active = true",
                Usuario.class)
            .setParameter("tid", order.getTenantId())
            .setParameter("role", PapelUsuario.OWNER)
            .getResultList();

    String vencimento =
        order.getValidUntil() != null ? DATE_FMT.format(order.getValidUntil()) : "—";
    String assunto =
        "Sua licenca "
            + (diasRestantes == 1 ? "vence amanha" : "vence em " + diasRestantes + " dias");
    String html = montarHtmlNotificacao(tenant.getName(), diasRestantes, vencimento);

    for (Usuario owner : owners) {
      if (owner.getEmail() == null || owner.getEmail().isBlank()) continue;
      credentialsEmailService.sendHtmlEmail(
          owner.getEmail(), assunto, html, null, null, null, "LICENSE_EXPIRING_SOON");
    }

    if (owners.isEmpty() && tenant.getEmail() != null && !tenant.getEmail().isBlank()) {
      credentialsEmailService.sendHtmlEmail(
          tenant.getEmail(), assunto, html, null, null, null, "LICENSE_EXPIRING_SOON");
    }
  }

  private String montarHtmlNotificacao(String tenantName, int diasRestantes, String vencimento) {
    String aviso =
        diasRestantes == 1
            ? "Sua licenca vence <strong>amanha, " + vencimento + "</strong>."
            : "Sua licenca vence em <strong>" + diasRestantes + " dias (" + vencimento + ")</strong>.";

    return "<html><body style='font-family:sans-serif;color:#333'>"
        + "<p>Ola, <strong>"
        + escapeHtml(tenantName)
        + "</strong>!</p>"
        + "<p>"
        + aviso
        + " Renove agora para manter o acesso sem interrupcoes.</p>"
        + "<p><a href='https://app.azzoholding.com.br/licenca'"
        + " style='background:#2563eb;color:#fff;padding:10px 20px;border-radius:6px;"
        + "text-decoration:none;display:inline-block;margin-top:8px'>Renovar licenca</a></p>"
        + "<p style='color:#888;font-size:12px'>Azzo Agenda Pro</p>"
        + "</body></html>";
  }

  /** Escapa apenas {@code & < >} — o original nao trata aspas, e o nome so aparece em texto. */
  private static String escapeHtml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
