package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.AberturaCaixaRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.FechamentoCaixaRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.FechamentoCaixaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoFechamentoCaixa;
import jakarta.validation.Valid;

/** Espelha {@code modules/finance/api/FechamentoCaixaResource.java}. */
@RestController
@RequestMapping("/api/v1/finance/cash-closings")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class FechamentoCaixaController {

  private final ServicoFechamentoCaixa servicoFechamentoCaixa;

  public FechamentoCaixaController(ServicoFechamentoCaixa servicoFechamentoCaixa) {
    this.servicoFechamentoCaixa = servicoFechamentoCaixa;
  }

  @GetMapping
  @RequiresPermission("finance:view")
  public List<FechamentoCaixaResponse> listar() {
    return servicoFechamentoCaixa.listar();
  }

  @GetMapping("/{id}")
  @RequiresPermission("finance:view")
  public FechamentoCaixaResponse buscar(@PathVariable UUID id) {
    return servicoFechamentoCaixa.buscar(id);
  }

  @PostMapping("/open")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("finance:manage")
  public FechamentoCaixaResponse abrir(@Valid @RequestBody(required = false) AberturaCaixaRequest request) {
    return servicoFechamentoCaixa.abrir(request);
  }

  @PostMapping("/{id}/close")
  @RequiresPermission("finance:manage")
  public FechamentoCaixaResponse fechar(
      @PathVariable UUID id, @Valid @RequestBody FechamentoCaixaRequest request) {
    return servicoFechamentoCaixa.fechar(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission("finance:manage")
  public void remover(@PathVariable UUID id) {
    servicoFechamentoCaixa.remover(id);
  }
}
