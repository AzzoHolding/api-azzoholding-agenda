package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PackageDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoPackages;
import jakarta.validation.Valid;

/** Espelha {@code modules/packages/api/PackagesResource.java}. */
@RestController
@RequestMapping("/api/v1/packages")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class PackagesController {

  private final ServicoPackages servicoPackages;

  public PackagesController(ServicoPackages servicoPackages) {
    this.servicoPackages = servicoPackages;
  }

  @GetMapping
  public List<PackageDtos.PackageResponse> listar() {
    return servicoPackages.listar();
  }

  @GetMapping("/{id}")
  public PackageDtos.PackageResponse obter(@PathVariable UUID id) {
    return servicoPackages.obter(id);
  }

  @PostMapping
  @PreAuthorize("hasRole('OWNER')")
  public PackageDtos.PackageResponse criar(@Valid @RequestBody PackageDtos.PackageRequest request) {
    return servicoPackages.criar(request);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  public PackageDtos.PackageResponse atualizar(
      @PathVariable UUID id, @Valid @RequestBody PackageDtos.PackageRequest request) {
    return servicoPackages.atualizar(id, request);
  }
}
