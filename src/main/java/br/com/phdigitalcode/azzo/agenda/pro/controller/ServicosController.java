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
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ServicoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RemoveResultResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoService;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/services/api/ServicosResource.java}.
 *
 * <p>Os endpoints {@code /importacoes/*} do original dependem do modulo {@code storage} (MinIO) e
 * do processamento assincrono de importacao — pendentes, ver MIGRACAO-QUARKUS-SPRING.md.
 */
@RestController
@RequestMapping("/api/v1/services")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class ServicosController {

  private final ServicoService servicoService;

  public ServicosController(ServicoService servicoService) {
    this.servicoService = servicoService;
  }

  @GetMapping
  @RequiresPermission("professional:read")
  public List<ServicoResponse> listar() {
    return servicoService.listar();
  }

  @PostMapping
  @RequiresPermission("professional:write")
  public ServicoResponse criar(@Valid @RequestBody ServicoRequest request) {
    return servicoService.criar(request);
  }

  @PutMapping("/{id}")
  @RequiresPermission("professional:write")
  public ServicoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ServicoRequest request) {
    return servicoService.atualizar(id, request);
  }

  @DeleteMapping("/{id}")
  @RequiresPermission("professional:write")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletar(@PathVariable UUID id) {
    servicoService.deletar(id);
  }

  @PostMapping("/remove-selected")
  @RequiresPermission("professional:write")
  public RemoveResultResponse removerSelecionados(@RequestBody(required = false) RemoveSelectedRequest request) {
    RemoveResultResponse response = new RemoveResultResponse();
    response.removedCount = servicoService.deletarSelecionados(request != null ? request.ids : null);
    return response;
  }

  @PostMapping("/remove-all")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("professional:write")
  public RemoveResultResponse removerTodos() {
    RemoveResultResponse response = new RemoveResultResponse();
    response.removedCount = servicoService.deletarTodos();
    return response;
  }
}
