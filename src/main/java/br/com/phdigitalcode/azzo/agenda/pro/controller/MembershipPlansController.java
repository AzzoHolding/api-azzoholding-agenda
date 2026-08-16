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

import br.com.phdigitalcode.azzo.agenda.pro.dto.MembershipDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoClientMemberships;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoMembershipPlans;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/membership/api/MembershipPlansResource.java} — o original tem
 * {@code @Path("/api/v1")} na classe e paths distintos por metodo ({@code /membership-plans} e
 * {@code /memberships/{id}/cancelar}), entao o mapeamento de classe aqui e {@code /api/v1} para
 * manter as URLs identicas.
 *
 * <p>{@code assinar} e {@code listarDoCliente} ({@link ServicoClientMemberships}) sao expostos pelo
 * {@code ClientesResource} do original ({@code /clients/{id}/memberships}) — esses endpoints ainda
 * nao foram portados, ver pendencias do modulo {@code customers}.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class MembershipPlansController {

  private final ServicoMembershipPlans servicoMembershipPlans;
  private final ServicoClientMemberships servicoClientMemberships;

  public MembershipPlansController(
      ServicoMembershipPlans servicoMembershipPlans,
      ServicoClientMemberships servicoClientMemberships) {
    this.servicoMembershipPlans = servicoMembershipPlans;
    this.servicoClientMemberships = servicoClientMemberships;
  }

  @GetMapping("/membership-plans")
  public List<MembershipDtos.PlanResponse> listar() {
    return servicoMembershipPlans.listar();
  }

  @GetMapping("/membership-plans/{id}")
  public MembershipDtos.PlanResponse obter(@PathVariable UUID id) {
    return servicoMembershipPlans.obter(id);
  }

  @PostMapping("/membership-plans")
  @PreAuthorize("hasRole('OWNER')")
  public MembershipDtos.PlanResponse criar(@Valid @RequestBody MembershipDtos.PlanRequest request) {
    return servicoMembershipPlans.criar(request);
  }

  @PatchMapping("/membership-plans/{id}")
  @PreAuthorize("hasRole('OWNER')")
  public MembershipDtos.PlanResponse atualizar(
      @PathVariable UUID id, @Valid @RequestBody MembershipDtos.PlanRequest request) {
    return servicoMembershipPlans.atualizar(id, request);
  }

  @PostMapping("/memberships/{id}/cancelar")
  @PreAuthorize("hasRole('OWNER')")
  public MembershipDtos.ClientMembershipResponse cancelar(@PathVariable UUID id) {
    return servicoClientMemberships.cancelar(id);
  }
}
