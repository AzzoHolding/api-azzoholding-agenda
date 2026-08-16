package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.BillingAdminService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoBilling;

/**
 * Cobre {@code modules/billing/api/BillingResource.java}: a unica logica que vive no controller (a
 * extracao do IP do {@code X-Forwarded-For}), a delegacao de cada rota e o contrato de rota/verbo/
 * permissao — que e justamente o que a paridade com o original exige.
 */
class BillingControllerTest {

  private ServicoBilling servicoBilling;
  private BillingAdminService billingAdminService;
  private BillingController controller;

  @BeforeEach
  void setUp() {
    servicoBilling = mock(ServicoBilling.class);
    billingAdminService = mock(BillingAdminService.class);
    controller = new BillingController(servicoBilling, billingAdminService);
  }

  // ─── X-Forwarded-For ──────────────────────────────────────────────────────

  @Test
  void cadeiaDeProxiesUsaApenasOPrimeiroIp() {
    BillingDtos.CreateSubscriptionRequest request = new BillingDtos.CreateSubscriptionRequest();

    controller.createSubscription(request, "203.0.113.9, 70.41.3.18, 150.172.238.178");

    verify(servicoBilling).criarAssinatura(request, "203.0.113.9");
  }

  @Test
  void ipUnicoPassaIntacto() {
    BillingDtos.CreateSubscriptionRequest request = new BillingDtos.CreateSubscriptionRequest();

    controller.createSubscription(request, "203.0.113.9");

    verify(servicoBilling).criarAssinatura(request, "203.0.113.9");
  }

  @Test
  void semHeaderOIpVaiNuloENaoCaiParaOSocket() {
    BillingDtos.CreateSubscriptionRequest request = new BillingDtos.CreateSubscriptionRequest();

    controller.createSubscription(request, null);

    verify(servicoBilling).criarAssinatura(request, null);
  }

  // ─── delegacao ────────────────────────────────────────────────────────────

  @Test
  void rotasDoProprioTenantDelegamParaServicoBilling() {
    controller.getCurrentSubscription();
    controller.getPayments();
    controller.cancelarAssinatura();

    verify(servicoBilling).obterAssinaturaAtual();
    verify(servicoBilling).listarPagamentos();
    verify(servicoBilling).cancelarAssinaturaAtual();
  }

  @Test
  void corpoAusenteNasRotasDeLicencaViraTudoNulo() {
    controller.expireLicenseForTests(null);
    controller.releaseLicenseForTests(null);

    verify(billingAdminService).forceExpireCurrentTenant(null, null);
    verify(billingAdminService).releaseLicenseForCurrentTenant(null, null, null, null);
  }

  @Test
  void corpoPresenteNasRotasDeLicencaEhDesmontadoCampoACampo() {
    BillingDtos.AdminExpireLicenseRequest expire = new BillingDtos.AdminExpireLicenseRequest();
    expire.tenantId = "t1";
    expire.minutesAgo = 30;
    BillingDtos.AdminReleaseLicenseRequest release = new BillingDtos.AdminReleaseLicenseRequest();
    release.tenantId = "t2";
    release.productId = "p2";
    release.validityDays = 15;
    release.paymentId = "pay_2";

    controller.expireLicenseForTests(expire);
    controller.releaseLicenseForTests(release);

    verify(billingAdminService).forceExpireCurrentTenant("t1", 30);
    verify(billingAdminService).releaseLicenseForCurrentTenant("t2", "p2", 15, "pay_2");
  }

  @Test
  void rotasAdministrativasDelegamParaBillingAdminService() {
    BillingDtos.AdminMarkPaymentReceivedRequest marcar =
        new BillingDtos.AdminMarkPaymentReceivedRequest();
    marcar.tenantId = "t3";
    marcar.paymentId = "pay_3";
    marcar.validityDays = 31;
    BillingDtos.ExtendTrialRequest trial = new BillingDtos.ExtendTrialRequest();
    trial.tenantId = "t4";
    trial.extraDays = 3;
    String tenantId = UUID.randomUUID().toString();

    controller.markPaymentAsReceived(marcar);
    controller.listActiveTenants();
    controller.listPaymentsByTenant(tenantId);
    controller.extenderTrial(trial);
    controller.getLicenseHistory(tenantId);
    controller.relatorioLicencas();

    verify(billingAdminService).markPaymentAsReceived("t3", "pay_3", 31);
    verify(billingAdminService).listActiveTenants();
    verify(billingAdminService).listPaymentsByTenant(tenantId);
    verify(billingAdminService).extenderTrial("t4", 3);
    verify(billingAdminService).listarHistoricoLicenca(tenantId);
    verify(billingAdminService).relatorioLicencas();
  }

  @Test
  void respostaDoServiceEhDevolvidaSemTransformacao() {
    BillingDtos.CurrentSubscriptionResponse esperado =
        new BillingDtos.CurrentSubscriptionResponse();
    when(servicoBilling.obterAssinaturaAtual()).thenReturn(esperado);

    assertThat(controller.getCurrentSubscription()).isSameAs(esperado);
  }

  // ─── contrato de rota / verbo / permissao ─────────────────────────────────

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(BillingController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/billing");
    // O original nao tem @RolesAllowed de classe: as permissoes sao por metodo.
    assertThat(BillingController.class.getAnnotation(PreAuthorize.class)).isNull();
  }

  @Test
  void cadaRotaMantemVerboCaminhoEPermissaoDoOriginal() {
    assertGet("getCurrentSubscription", "/subscriptions/current", "hasAnyRole('OWNER', 'ADMIN')");
    assertGet("getPayments", "/payments", "hasAnyRole('OWNER', 'ADMIN')");
    assertGet("listActiveTenants", "/admin/tenants/active", "hasRole('ADMIN')");
    assertGet("listPaymentsByTenant", "/admin/tenants/{tenantId}/payments", "hasRole('ADMIN')");
    assertGet(
        "getLicenseHistory", "/admin/tenants/{tenantId}/license-history", "hasRole('ADMIN')");
    assertGet("relatorioLicencas", "/admin/reports/licencas", "hasRole('ADMIN')");

    assertPost("createSubscription", "/subscriptions", "hasRole('OWNER')");
    assertPost("cancelarAssinatura", "/subscriptions/current/cancelar", "hasRole('OWNER')");
    assertPost("expireLicenseForTests", "/admin/license/expire", "hasRole('ADMIN')");
    assertPost("releaseLicenseForTests", "/admin/license/release", "hasRole('ADMIN')");
    assertPost("markPaymentAsReceived", "/admin/payments/mark-received", "hasRole('ADMIN')");
    assertPost("extenderTrial", "/admin/trial/extend", "hasRole('ADMIN')");
  }

  private void assertGet(String metodo, String caminho, String permissao) {
    Method m = metodo(metodo);
    assertThat(m.getAnnotation(GetMapping.class)).as(metodo).isNotNull();
    assertThat(m.getAnnotation(GetMapping.class).value()).as(metodo).containsExactly(caminho);
    assertThat(m.getAnnotation(PostMapping.class)).as(metodo).isNull();
    assertThat(m.getAnnotation(PreAuthorize.class).value()).as(metodo).isEqualTo(permissao);
  }

  private void assertPost(String metodo, String caminho, String permissao) {
    Method m = metodo(metodo);
    assertThat(m.getAnnotation(PostMapping.class)).as(metodo).isNotNull();
    assertThat(m.getAnnotation(PostMapping.class).value()).as(metodo).containsExactly(caminho);
    assertThat(m.getAnnotation(GetMapping.class)).as(metodo).isNull();
    assertThat(m.getAnnotation(PreAuthorize.class).value()).as(metodo).isEqualTo(permissao);
  }

  private Method metodo(String nome) {
    for (Method m : BillingController.class.getDeclaredMethods()) {
      if (m.getName().equals(nome)) return m;
    }
    throw new AssertionError("Metodo nao encontrado no BillingController: " + nome);
  }
}
