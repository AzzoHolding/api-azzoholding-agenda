package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.BillingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.BillingAdminService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoBilling;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/billing/api/BillingResource.java} — 12 rotas em tres faixas de permissao:
 * leitura do proprio tenant ({@code OWNER}+{@code ADMIN}), escrita do proprio tenant
 * ({@code OWNER}) e administracao ({@code ADMIN}).
 *
 * <p>Nao ha {@code @PreAuthorize} de classe justamente porque as tres faixas convivem: cada metodo
 * declara a sua, igual ao {@code @RolesAllowed} por metodo do original.
 *
 * <p>Os tres corpos <b>sem</b> {@code @Valid} no original ({@code /admin/license/expire},
 * {@code /admin/license/release}) tambem sao opcionais — o resource trata {@code request == null}
 * campo a campo. Isso foi preservado com {@code @RequestBody(required = false)}.
 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

  private final ServicoBilling servicoBilling;
  private final BillingAdminService billingAdminService;

  public BillingController(ServicoBilling servicoBilling, BillingAdminService billingAdminService) {
    this.servicoBilling = servicoBilling;
    this.billingAdminService = billingAdminService;
  }

  @GetMapping("/subscriptions/current")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public BillingDtos.CurrentSubscriptionResponse getCurrentSubscription() {
    return servicoBilling.obterAssinaturaAtual();
  }

  @GetMapping("/payments")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public BillingDtos.PaymentsListResponse getPayments() {
    return servicoBilling.listarPagamentos();
  }

  /**
   * O IP do cliente sai do {@code X-Forwarded-For}: quando ha cadeia de proxies, so o primeiro
   * elemento (o cliente real) e usado. Sem o header, segue {@code null} — o original nao cai para
   * o IP remoto do socket aqui.
   */
  @PostMapping("/subscriptions")
  @PreAuthorize("hasRole('OWNER')")
  public BillingDtos.SubscriptionResponse createSubscription(
      @Valid @RequestBody BillingDtos.CreateSubscriptionRequest request,
      @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {
    String remoteIp = forwardedFor;
    if (remoteIp != null && remoteIp.contains(",")) {
      remoteIp = remoteIp.substring(0, remoteIp.indexOf(',')).trim();
    }
    return servicoBilling.criarAssinatura(request, remoteIp);
  }

  @PostMapping("/admin/license/expire")
  @PreAuthorize("hasRole('ADMIN')")
  public BillingDtos.AdminBillingActionResponse expireLicenseForTests(
      @RequestBody(required = false) BillingDtos.AdminExpireLicenseRequest request) {
    return billingAdminService.forceExpireCurrentTenant(
        request != null ? request.tenantId : null, request != null ? request.minutesAgo : null);
  }

  @PostMapping("/admin/license/release")
  @PreAuthorize("hasRole('ADMIN')")
  public BillingDtos.AdminBillingActionResponse releaseLicenseForTests(
      @RequestBody(required = false) BillingDtos.AdminReleaseLicenseRequest request) {
    return billingAdminService.releaseLicenseForCurrentTenant(
        request != null ? request.tenantId : null,
        request != null ? request.productId : null,
        request != null ? request.validityDays : null,
        request != null ? request.paymentId : null);
  }

  @PostMapping("/admin/payments/mark-received")
  @PreAuthorize("hasRole('ADMIN')")
  public BillingDtos.AdminBillingActionResponse markPaymentAsReceived(
      @Valid @RequestBody BillingDtos.AdminMarkPaymentReceivedRequest request) {
    return billingAdminService.markPaymentAsReceived(
        request.tenantId, request.paymentId, request.validityDays);
  }

  @GetMapping("/admin/tenants/active")
  @PreAuthorize("hasRole('ADMIN')")
  public BillingDtos.AdminTenantListResponse listActiveTenants() {
    return billingAdminService.listActiveTenants();
  }

  @GetMapping("/admin/tenants/{tenantId}/payments")
  @PreAuthorize("hasRole('ADMIN')")
  public BillingDtos.PaymentsListResponse listPaymentsByTenant(@PathVariable String tenantId) {
    return billingAdminService.listPaymentsByTenant(tenantId);
  }

  /** Rota em portugues no original ({@code /cancelar}), preservada. */
  @PostMapping("/subscriptions/current/cancelar")
  @PreAuthorize("hasRole('OWNER')")
  public BillingDtos.CancelSubscriptionResponse cancelarAssinatura() {
    return servicoBilling.cancelarAssinaturaAtual();
  }

  @PostMapping("/admin/trial/extend")
  @PreAuthorize("hasRole('ADMIN')")
  public BillingDtos.ExtendTrialResponse extenderTrial(
      @Valid @RequestBody BillingDtos.ExtendTrialRequest request) {
    return billingAdminService.extenderTrial(request.tenantId, request.extraDays);
  }

  @GetMapping("/admin/tenants/{tenantId}/license-history")
  @PreAuthorize("hasRole('ADMIN')")
  public BillingDtos.LicenseHistoryResponse getLicenseHistory(@PathVariable String tenantId) {
    return billingAdminService.listarHistoricoLicenca(tenantId);
  }

  @GetMapping("/admin/reports/licencas")
  @PreAuthorize("hasRole('ADMIN')")
  public BillingDtos.LicencasAdminReportResponse relatorioLicencas() {
    return billingAdminService.relatorioLicencas();
  }
}
