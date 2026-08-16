package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RemoveSelectedRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.SpecialtyCreateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.SpecialtyUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RemoveResultResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.SpecialtyResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.SpecialtyService;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/services/api/SpecialtiesResource.java}. Os endpoints
 * {@code /importacoes/*} ficam pendentes (modulo {@code storage}/importacao assincrona).
 */
@RestController
@RequestMapping("/api/v1/specialties")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class SpecialtiesController {

  private final SpecialtyService specialtyService;

  public SpecialtiesController(SpecialtyService specialtyService) {
    this.specialtyService = specialtyService;
  }

  @GetMapping
  @RequiresPermission("professional:read")
  public List<SpecialtyResponse> listar() {
    return specialtyService.listar();
  }

  @PostMapping
  @RequiresPermission("professional:write")
  public SpecialtyResponse criar(@Valid @RequestBody SpecialtyCreateRequest request) {
    return specialtyService.criar(request);
  }

  @PutMapping("/{id}")
  @RequiresPermission("professional:write")
  public SpecialtyResponse atualizar(@PathVariable UUID id, @Valid @RequestBody SpecialtyUpdateRequest request) {
    return specialtyService.atualizar(id, request);
  }

  @DeleteMapping("/{id}")
  @RequiresPermission("professional:write")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletar(@PathVariable UUID id) {
    specialtyService.deletar(id);
  }

  @PostMapping("/remove-selected")
  @RequiresPermission("professional:write")
  public RemoveResultResponse removerSelecionadas(@RequestBody(required = false) RemoveSelectedRequest request) {
    RemoveResultResponse response = new RemoveResultResponse();
    response.removedCount = specialtyService.deletarSelecionadas(request != null ? request.ids : null);
    return response;
  }

  @PostMapping("/remove-all")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("professional:write")
  public RemoveResultResponse removerTodas() {
    RemoveResultResponse response = new RemoveResultResponse();
    response.removedCount = specialtyService.deletarTodas();
    return response;
  }
}
