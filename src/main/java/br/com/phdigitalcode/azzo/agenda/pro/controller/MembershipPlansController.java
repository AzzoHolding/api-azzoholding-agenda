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
 * <p>{@code assinar} e {@code listarDoCliente} ({@link ServicoClientMemberships}) vinham do
 * {@code ClientesResource} do original ({@code /clients/{id}/memberships}); foram portados para ca
 * em 14/11/2026 (Fase 7 dos pagamentos), junto com a lista do salao.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
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

  /** As assinaturas do salao: quem assinou, situacao e, no cartao, o link para cadastrar o cartao. */
  @GetMapping("/memberships")
  @PreAuthorize("hasRole('OWNER')")
  public List<MembershipDtos.ClientMembershipResponse> listarDoSalao() {
    return servicoClientMemberships.listarDoSalao();
  }

  @GetMapping("/clients/{clientId}/memberships")
  @PreAuthorize("hasRole('OWNER')")
  public List<MembershipDtos.ClientMembershipResponse> listarDoCliente(@PathVariable UUID clientId) {
    return servicoClientMemberships.listarDoCliente(clientId);
  }

  /**
   * Assina o plano para a cliente (portado do {@code ClientesResource} original, 14/11/2026).
   * {@code formaDeCobranca}: {@code PIX} (Asaas, como antes) ou {@code CARTAO} (cartao recorrente
   * pela operadora conectada, Fase 7). So o dono, como o cancelamento.
   */
  @PostMapping("/clients/{clientId}/memberships")
  @PreAuthorize("hasRole('OWNER')")
  public MembershipDtos.ClientMembershipResponse assinar(
      @PathVariable UUID clientId, @Valid @RequestBody MembershipDtos.SubscribeRequest request) {
    return servicoClientMemberships.assinar(clientId, request);
  }

  @PostMapping("/memberships/{id}/cancelar")
  @PreAuthorize("hasRole('OWNER')")
  public MembershipDtos.ClientMembershipResponse cancelar(@PathVariable UUID id) {
    return servicoClientMemberships.cancelar(id);
  }
}
