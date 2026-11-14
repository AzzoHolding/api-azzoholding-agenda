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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ClienteRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteDetailResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteIndicadoresResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteListResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.PagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ClienteService;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/customers/api/ClientesResource.java}.
 *
 * <p>ESCOPO PORTADO NESTA ETAPA: CRUD + listagem paginada. Os demais endpoints do resource
 * original dependem de modulos ainda nao migrados e ficam pendentes (registrados no
 * MIGRACAO-QUARKUS-SPRING.md):
 * <ul>
 *   <li>{@code /{id}/appointment-history} — modulo {@code scheduling};</li>
 *   <li>{@code /{id}/memberships} (GET e POST) — modulo {@code membership} (portado depois, em
 *       MembershipPlansController). {@code /{id}/packages} e {@code /{id}/loyalty} foram portados em
 *       14/11/2026 (relatorio de fidelidade e pacotes, Critico 3);</li>
 *   <li>{@code /{id}/avatar} (POST/DELETE/GET) e {@code /importacoes/*} — modulo {@code storage}
 *       (MinIO) + importacao assincrona.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/clients")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
public class ClientesController {

  private final ClienteService clienteService;
  private final br.com.phdigitalcode.azzo.agenda.pro.service.ServicoTenantLoyalty fidelidade;
  private final br.com.phdigitalcode.azzo.agenda.pro.service.ServicoPackages pacotes;

  public ClientesController(ClienteService clienteService,
      br.com.phdigitalcode.azzo.agenda.pro.service.ServicoTenantLoyalty fidelidade,
      br.com.phdigitalcode.azzo.agenda.pro.service.ServicoPackages pacotes) {
    this.clienteService = clienteService;
    this.fidelidade = fidelidade;
    this.pacotes = pacotes;
  }

  /** Pontos da cliente: saldo, valor em reais, o que vence e o extrato (portado em 14/11). */
  @GetMapping("/{id}/loyalty")
  @RequiresPermission("appointment:read")
  public br.com.phdigitalcode.azzo.agenda.pro.dto.TenantLoyaltyDtos.ClientLoyaltyResponse pontos(@PathVariable UUID id) {
    clienteService.exigirFichaCompletaDe(id);
    return fidelidade.obterSaldoCliente(id);
  }

  /** Pacotes da cliente: compras, saldo por servico e os atendimentos feitos (portado em 14/11). */
  @GetMapping("/{id}/packages")
  @RequiresPermission("appointment:read")
  public List<br.com.phdigitalcode.azzo.agenda.pro.dto.PackageDtos.ClientPackagePurchaseResponse> pacotes(@PathVariable UUID id) {
    clienteService.exigirFichaCompletaDe(id);
    return pacotes.listarDoCliente(id);
  }

  @GetMapping
  @RequiresPermission("appointment:read")
  public List<ClienteListResponse> listar() {
    return clienteService.listar();
  }

  @GetMapping("/paged")
  @RequiresPermission("appointment:read")
  public PagedResponse<ClienteListResponse> listarPaginado(
      @RequestParam(name = "page", defaultValue = "0") Integer page,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "size", required = false) Integer size) {
    return clienteService.listarPaginado(Math.max(page == null ? 0 : page, 0), resolvePageSize(limit, size));
  }

  @GetMapping("/{id}")
  @RequiresPermission("appointment:read")
  public ClienteDetailResponse obterPorId(@PathVariable UUID id) {
    return clienteService.obterPorId(id);
  }

  @GetMapping("/{id}/indicators")
  @RequiresPermission("appointment:read")
  public ClienteIndicadoresResponse indicadores(@PathVariable UUID id) {
    return clienteService.indicadores(id);
  }

  @PostMapping
  @RequiresPermission("appointment:write")
  public ClienteListResponse criar(@Valid @RequestBody ClienteRequest request) {
    return clienteService.criar(request);
  }

  @PutMapping("/{id}")
  @RequiresPermission("appointment:write")
  public ClienteListResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ClienteRequest request) {
    return clienteService.atualizar(id, request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("appointment:write")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletar(@PathVariable UUID id) {
    clienteService.deletar(id);
  }

  private int resolvePageSize(Integer limit, Integer size) {
    int resolved = size != null ? size : (limit != null ? limit : 20);
    return Math.min(Math.max(1, resolved), 200);
  }
}
