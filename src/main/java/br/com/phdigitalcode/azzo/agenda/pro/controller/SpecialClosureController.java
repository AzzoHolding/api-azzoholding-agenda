package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SpecialClosureDto;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SpecialClosureImpactDto;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.SpecialClosureService;

/**
 * Espelha {@code modules/settings/api/SpecialClosureResource.java} — {@code /api/v1/salon/closures}.
 *
 * <p>O contrato de status e o ponto sensivel aqui e foi replicado literalmente:
 *
 * <ul>
 *   <li>{@code POST /} devolve <b>201</b> quando o fechamento foi criado e <b>409</b> quando ha
 *       agendamentos impactados — nos dois casos com o mesmo corpo
 *       ({@link SpecialClosureImpactDto}). O 409 <b>nao</b> e erro: e a lista de impactados para o
 *       frontend decidir se confirma.
 *   <li>{@code POST /confirm} devolve sempre <b>201</b>.
 *   <li>{@code PUT /{id}} e {@code DELETE /{id}} devolvem <b>204</b> sem corpo.
 * </ul>
 *
 * <p>Nenhum dos tres endpoints de escrita usa {@code @Valid} no original — a validacao e feita a
 * mao no service ({@code validarTipo}/{@code validarDatas}), com mensagens proprias. Mantido.
 */
@RestController
@RequestMapping("/api/v1/salon/closures")
@PreAuthorize("hasRole('OWNER')")
public class SpecialClosureController {

  private final SpecialClosureService specialClosureService;
  private final ContextoTenant contextoTenant;

  public SpecialClosureController(
      SpecialClosureService specialClosureService, ContextoTenant contextoTenant) {
    this.specialClosureService = specialClosureService;
    this.contextoTenant = contextoTenant;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
  public ResponseEntity<List<SpecialClosureDto>> listar(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID professionalId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return ResponseEntity.ok(specialClosureService.listar(tenantId, from, to, professionalId));
  }

  @PostMapping
  public ResponseEntity<SpecialClosureImpactDto> criar(@RequestBody SpecialClosureDto dto) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    SpecialClosureImpactDto result = specialClosureService.criar(tenantId, dto);
    if (result.created) {
      return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
    // HTTP 409 Conflict — ha agendamentos impactados; frontend decide se confirma
    return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
  }

  @PostMapping("/confirm")
  public ResponseEntity<SpecialClosureImpactDto> confirmar(@RequestBody SpecialClosureDto dto) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    SpecialClosureImpactDto result = specialClosureService.confirmar(tenantId, dto);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @PutMapping("/{id}")
  public ResponseEntity<Void> editar(
      @PathVariable UUID id, @RequestBody SpecialClosureDto dto) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    specialClosureService.editar(tenantId, id, dto);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> remover(@PathVariable UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    specialClosureService.remover(tenantId, id);
    return ResponseEntity.noContent().build();
  }
}
