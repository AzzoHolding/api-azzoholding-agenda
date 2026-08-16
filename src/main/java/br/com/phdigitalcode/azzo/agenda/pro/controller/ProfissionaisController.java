package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ProfissionalRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.StatusRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalLimitsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtPrincipal;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ProfissionalService;
import jakarta.validation.Valid;

/** Espelha {@code modules/professionals/api/ProfissionaisResource.java}. */
@RestController
@RequestMapping("/api/v1/professionals")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class ProfissionaisController {

  private final ProfissionalService profissionalService;

  public ProfissionaisController(ProfissionalService profissionalService) {
    this.profissionalService = profissionalService;
  }

  @GetMapping
  @RequiresPermission("professional:read")
  public List<ProfissionalResponse> listar(@RequestParam(name = "serviceId", required = false) String serviceId) {
    return profissionalService.listar(serviceId);
  }

  @GetMapping("/limits")
  @RequiresPermission("professional:read")
  public ProfissionalLimitsResponse obterLimites() {
    return profissionalService.obterLimites();
  }

  /**
   * Regra preservada do original: um usuario que e SOMENTE {@code PROFESSIONAL} (sem OWNER/ADMIN)
   * so pode consultar o proprio cadastro — {@code validarAcessoProprio} responde 403 caso
   * contrario.
   */
  @GetMapping("/{id}")
  @RequiresPermission("professional:read")
  public ProfissionalResponse obterPorId(@PathVariable UUID id) {
    Set<String> grupos = rolesDoUsuarioAutenticado();
    boolean isProfessionalOnly =
        grupos.contains("PROFESSIONAL") && !grupos.contains("OWNER") && !grupos.contains("ADMIN");
    if (isProfessionalOnly) {
      profissionalService.validarAcessoProprio(id, userIdAutenticadoOuFalhar());
    }
    return profissionalService.obterPorId(id);
  }

  @PostMapping
  @RequiresPermission("professional:write")
  public ProfissionalResponse criar(@Valid @RequestBody ProfissionalRequest request) {
    return profissionalService.criar(request);
  }

  @PutMapping("/{id}")
  @RequiresPermission("professional:write")
  public ProfissionalResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ProfissionalRequest request) {
    return profissionalService.atualizar(id, request);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("professional:write")
  public ProfissionalResponse toggleStatus(@PathVariable UUID id, @RequestBody StatusRequest body) {
    return profissionalService.toggleStatus(id, body.isActive);
  }

  @DeleteMapping("/{id}")
  @RequiresPermission("professional:write")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletar(@PathVariable UUID id) {
    profissionalService.deletar(id);
  }

  @PostMapping("/{id}/reset-password")
  @RequiresPermission("professional:write")
  public ProfissionalResponse.PasswordResetResponse resetarSenha(@PathVariable UUID id) {
    return profissionalService.resetarSenha(id);
  }

  private Set<String> rolesDoUsuarioAutenticado() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getAuthorities() == null) return Set.of();
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
        .collect(Collectors.toSet());
  }

  private UUID userIdAutenticadoOuFalhar() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal
        && jwtPrincipal.userId() != null) {
      return jwtPrincipal.userId();
    }
    throw new ApiClientErrorException("Nao autenticado", 401);
  }
}
