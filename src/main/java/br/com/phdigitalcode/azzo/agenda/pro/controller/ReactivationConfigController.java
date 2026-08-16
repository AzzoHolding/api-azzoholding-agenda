package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.OptOutPagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.ReactivationCyclesPagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.ReactivationMetricsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.TenantReactivationConfigResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.UpdateReactivationConfigRequest;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.ReactivationConfigService;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/chat/api/ReactivationConfigResource.java} — 8 rotas em duas faixas de
 * permissao: leitura ({@code OWNER}+{@code ADMIN}) e escrita ({@code OWNER}). Sem
 * {@code @PreAuthorize} de classe, mesmo padrao adotado em {@link BillingController} pelo mesmo
 * motivo: as duas faixas convivem no recurso.
 */
@RestController
@RequestMapping("/api/v1/reactivation")
public class ReactivationConfigController {

  private final ReactivationConfigService reactivationConfigService;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;

  public ReactivationConfigController(
      ReactivationConfigService reactivationConfigService,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser) {
    this.reactivationConfigService = reactivationConfigService;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/config")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public TenantReactivationConfigResponse getConfig() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return reactivationConfigService.getConfig(tenantId);
  }

  @PutMapping("/config")
  @PreAuthorize("hasRole('OWNER')")
  public TenantReactivationConfigResponse updateConfig(@Valid @RequestBody UpdateReactivationConfigRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return reactivationConfigService.updateConfig(tenantId, authenticatedUser.idOuNulo(), authenticatedUser.roleOuNulo(), request);
  }

  @PostMapping("/customers/{clientId}/opt-out")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Void> optOut(@PathVariable("clientId") UUID clientId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    reactivationConfigService.registrarOptOut(tenantId, clientId, authenticatedUser.idOuNulo(), authenticatedUser.roleOuNulo());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/customers/{clientId}/opt-in")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Void> optIn(@PathVariable("clientId") UUID clientId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    reactivationConfigService.registrarOptIn(tenantId, clientId, authenticatedUser.idOuNulo(), authenticatedUser.roleOuNulo());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/cycles")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ReactivationCyclesPagedResponse listCycles(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return reactivationConfigService.listCycles(tenantId, status, from, to, page, size);
  }

  @PostMapping("/cycles/{cycleId}/cancel")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Void> cancelCycle(@PathVariable("cycleId") UUID cycleId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    reactivationConfigService.cancelCycleById(tenantId, cycleId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/opt-outs")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public OptOutPagedResponse listOptOuts(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return reactivationConfigService.listOptOutsPaged(tenantId, page, size);
  }

  @GetMapping("/metrics")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ReactivationMetricsResponse getMetrics(@RequestParam(name = "period", required = false) String period) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return reactivationConfigService.getMetrics(tenantId, period);
  }
}
