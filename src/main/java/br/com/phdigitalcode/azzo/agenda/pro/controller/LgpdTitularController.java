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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.LgpdRequestDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoAnonimizacaoTitular;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoLgpdTitular;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/lgpd/api/LgpdTitularResource.java} ({@code @Path("/api/v1/lgpd/requests")},
 * {@code @RolesAllowed({"OWNER"})} de classe).
 */
@RestController
@RequestMapping("/api/v1/lgpd/requests")
@PreAuthorize("hasRole('OWNER')")
public class LgpdTitularController {

  private final ServicoLgpdTitular servicoLgpdTitular;
  private final ServicoAnonimizacaoTitular servicoAnonimizacao;

  public LgpdTitularController(ServicoLgpdTitular servicoLgpdTitular, ServicoAnonimizacaoTitular servicoAnonimizacao) {
    this.servicoLgpdTitular = servicoLgpdTitular;
    this.servicoAnonimizacao = servicoAnonimizacao;
  }

  @PostMapping
  public LgpdRequestDtos.ItemResponse criar(@Valid @RequestBody LgpdRequestDtos.CreateRequest request) {
    return servicoLgpdTitular.criar(request);
  }

  @GetMapping
  public List<LgpdRequestDtos.ItemResponse> listar(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "requestType", required = false) String requestType,
      @RequestParam(name = "limit", required = false) Integer limit) {
    return servicoLgpdTitular.listar(status, requestType, limit);
  }

  @GetMapping("/summary")
  public LgpdRequestDtos.SummaryResponse summary(@RequestParam(name = "alertLimit", required = false) Integer alertLimit) {
    return servicoLgpdTitular.resumirOperacao(alertLimit);
  }

  @GetMapping("/{id}")
  public LgpdRequestDtos.DetailResponse detalhar(@PathVariable("id") UUID id) {
    return servicoLgpdTitular.detalhar(id);
  }

  @GetMapping("/protocol/{protocolCode}")
  public LgpdRequestDtos.DetailResponse detalharPorProtocolo(@PathVariable("protocolCode") String protocolCode) {
    return servicoLgpdTitular.detalharPorProtocolo(protocolCode);
  }

  @PatchMapping("/{id}/status")
  public LgpdRequestDtos.ItemResponse atualizarStatus(
      @PathVariable("id") UUID id, @Valid @RequestBody LgpdRequestDtos.UpdateStatusRequest request) {
    return servicoLgpdTitular.atualizarStatus(id, request);
  }

  @PostMapping("/clientes/{clientId}/anonimizar")
  public ServicoAnonimizacaoTitular.AnonimizacaoResponse anonimizar(@PathVariable("clientId") UUID clientId) {
    return servicoAnonimizacao.anonimizar(clientId);
  }
}
