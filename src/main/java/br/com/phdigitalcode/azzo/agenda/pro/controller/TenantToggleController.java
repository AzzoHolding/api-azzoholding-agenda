package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ToggleRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ToggleResponse;
import br.com.phdigitalcode.azzo.agenda.pro.service.TenantToggleService;
import jakarta.validation.Valid;

/** Espelha {@code modules/tenant/api/TenantToggleResource.java}. */
@RestController
@RequestMapping("/api/v1/tenant/toggle")
@PreAuthorize("hasRole('OWNER')")
public class TenantToggleController {

  private final TenantToggleService tenantToggleService;

  public TenantToggleController(TenantToggleService tenantToggleService) {
    this.tenantToggleService = tenantToggleService;
  }

  @PatchMapping
  public ToggleResponse aplicar(@Valid @RequestBody ToggleRequest request) {
    return tenantToggleService.aplicar(request);
  }
}
