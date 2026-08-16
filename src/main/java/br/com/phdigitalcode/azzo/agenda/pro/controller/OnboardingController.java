package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.OnboardingDtos.AcceptTermsRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.OnboardingDtos.OnboardingStatusResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoOnboarding;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/onboarding/api/OnboardingResource.java} ({@code @Path("/api/v1/onboarding")}).
 *
 * <p>O original resolvia {@code ipAddress}/{@code userAgent} via {@code RequestAuditContext}
 * (bean {@code @RequestScoped} populado por {@code RequestAuditContextFilter}, ambos do modulo
 * {@code audit}, ainda nao portado). Aqui a mesma resolucao (X-Forwarded-For &gt; X-Real-IP &gt;
 * cabecalho {@code Forwarded} &gt; IP remoto da conexao) foi replicada localmente no controller,
 * ja que e so leitura de headers HTTP — nao ha necessidade de esperar o modulo {@code audit}
 * inteiro para portar esse detalhe.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

  private final ServicoOnboarding servicoOnboarding;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;

  public OnboardingController(
      ServicoOnboarding servicoOnboarding, ContextoTenant contextoTenant, AuthenticatedUser authenticatedUser) {
    this.servicoOnboarding = servicoOnboarding;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/status")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public OnboardingStatusResponse obterStatus() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return servicoOnboarding.getStatus(tenantId);
  }

  @PostMapping("/accept-terms")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public void aceitarTermos(@Valid @RequestBody AcceptTermsRequest request, HttpServletRequest servletRequest) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = authenticatedUser.idOuNulo();
    String ipAddress = resolveIpAddress(servletRequest);
    String userAgent = servletRequest.getHeader("User-Agent");
    servicoOnboarding.acceptTerms(tenantId, userId, request, ipAddress, userAgent);
  }

  @PutMapping("/step/{step}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public void atualizarStep(@PathVariable("step") int step) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    servicoOnboarding.updateStep(tenantId, step);
  }

  @PostMapping("/complete")
  @PreAuthorize("hasRole('OWNER')")
  public void concluirOnboarding() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    servicoOnboarding.completeOnboarding(tenantId);
  }

  @PostMapping("/skip")
  @PreAuthorize("hasRole('OWNER')")
  public void ignorarOnboarding() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    servicoOnboarding.skipOnboarding(tenantId);
  }

  private String resolveIpAddress(HttpServletRequest request) {
    String forwardedFor = firstNonBlank(request.getHeader("X-Forwarded-For"), request.getHeader("x-forwarded-for"));
    String firstForwarded = firstIp(forwardedFor);
    if (firstForwarded != null) return firstForwarded;

    String realIp = firstNonBlank(request.getHeader("X-Real-IP"), request.getHeader("x-real-ip"));
    if (realIp != null && !realIp.isBlank()) return realIp.trim();

    String forwarded = request.getHeader("Forwarded");
    if (forwarded != null && !forwarded.isBlank()) {
      String parsed = parseForwardedFor(forwarded);
      if (parsed != null) return parsed;
    }

    String remoteAddr = request.getRemoteAddr();
    return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : null;
  }

  private String firstIp(String forwardedFor) {
    if (forwardedFor == null || forwardedFor.isBlank()) return null;
    String[] values = forwardedFor.split(",");
    if (values.length == 0) return null;
    String first = values[0] != null ? values[0].trim() : null;
    return (first == null || first.isBlank()) ? null : first;
  }

  private String parseForwardedFor(String forwarded) {
    String[] parts = forwarded.split(";");
    for (String part : parts) {
      String trimmed = part != null ? part.trim() : "";
      if (!trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("for=")) continue;
      String value = trimmed.substring(4).trim();
      if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
        value = value.substring(1, value.length() - 1);
      }
      int bracket = value.indexOf(']');
      if (value.startsWith("[") && bracket > 0) {
        value = value.substring(1, bracket);
      } else {
        int colon = value.indexOf(':');
        if (colon > 0 && value.indexOf(':', colon + 1) == -1) {
          value = value.substring(0, colon);
        }
      }
      if (!value.isBlank()) return value;
    }
    return null;
  }

  private String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b;
  }
}
