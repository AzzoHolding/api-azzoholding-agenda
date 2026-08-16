package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MembershipDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlan;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlanBenefit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MembershipPlanBenefitRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MembershipPlanRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Espelha {@code modules/membership/application/ServicoMembershipPlans.java}. */
@Service
public class ServicoMembershipPlans {

  private final ContextoTenant contextoTenant;
  private final MembershipPlanRepository membershipPlanRepository;
  private final MembershipPlanBenefitRepository membershipPlanBenefitRepository;
  private final ServicoRepository servicoRepository;

  public ServicoMembershipPlans(
      ContextoTenant contextoTenant,
      MembershipPlanRepository membershipPlanRepository,
      MembershipPlanBenefitRepository membershipPlanBenefitRepository,
      ServicoRepository servicoRepository) {
    this.contextoTenant = contextoTenant;
    this.membershipPlanRepository = membershipPlanRepository;
    this.membershipPlanBenefitRepository = membershipPlanBenefitRepository;
    this.servicoRepository = servicoRepository;
  }

  @Transactional(readOnly = true)
  public List<MembershipDtos.PlanResponse> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return membershipPlanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public MembershipDtos.PlanResponse obter(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return toResponse(buscarOuFalhar(id, tenantId));
  }

  @Transactional
  public MembershipDtos.PlanResponse criar(MembershipDtos.PlanRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    MembershipPlan plano = new MembershipPlan();
    plano.setTenantId(tenantId);
    aplicar(request, plano);
    membershipPlanRepository.save(plano);
    aplicarBeneficios(tenantId, plano.getId(), request.beneficios);
    return toResponse(plano);
  }

  @Transactional
  public MembershipDtos.PlanResponse atualizar(UUID id, MembershipDtos.PlanRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    MembershipPlan plano = buscarOuFalhar(id, tenantId);
    aplicar(request, plano);
    membershipPlanBenefitRepository.deleteByPlanId(plano.getId());
    membershipPlanBenefitRepository.flush();
    aplicarBeneficios(tenantId, plano.getId(), request.beneficios);
    return toResponse(plano);
  }

  MembershipPlan buscarOuFalhar(UUID id, UUID tenantId) {
    return membershipPlanRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Plano de assinatura nao encontrado.", 404));
  }

  private void aplicar(MembershipDtos.PlanRequest request, MembershipPlan plano) {
    plano.setNome(request.nome);
    plano.setDescricao(request.descricao);
    plano.setPrecoMensal(request.precoMensal);
    plano.setCumulativo(request.cumulativo);
    plano.setAtivo(request.ativo);
  }

  private void aplicarBeneficios(
      UUID tenantId, UUID planId, List<MembershipDtos.BenefitRequest> beneficios) {
    for (MembershipDtos.BenefitRequest beneficioRequest : beneficios) {
      UUID serviceId = UUID.fromString(beneficioRequest.serviceId);
      servicoRepository
          .findByIdAndTenantId(serviceId, tenantId)
          .orElseThrow(
              () ->
                  new ApiClientErrorException(
                      "Servico nao encontrado: " + beneficioRequest.serviceId, 404));

      MembershipPlanBenefit beneficio = new MembershipPlanBenefit();
      beneficio.setTenantId(tenantId);
      beneficio.setPlanId(planId);
      beneficio.setServiceId(serviceId);
      beneficio.setQuantidadeMensal(beneficioRequest.quantidadeMensal);
      membershipPlanBenefitRepository.save(beneficio);
    }
  }

  private MembershipDtos.PlanResponse toResponse(MembershipPlan plano) {
    MembershipDtos.PlanResponse r = new MembershipDtos.PlanResponse();
    r.id = plano.getId().toString();
    r.nome = plano.getNome();
    r.descricao = plano.getDescricao();
    r.precoMensal = plano.getPrecoMensal();
    r.cumulativo = plano.isCumulativo();
    r.ativo = plano.isAtivo();
    r.createdAt = plano.getCreatedAt() != null ? plano.getCreatedAt().toString() : null;
    r.beneficios =
        membershipPlanBenefitRepository.findByPlanId(plano.getId()).stream()
            .map(
                beneficio -> {
                  MembershipDtos.BenefitResponse benefitResponse =
                      new MembershipDtos.BenefitResponse();
                  benefitResponse.serviceId = beneficio.getServiceId().toString();
                  benefitResponse.serviceNome =
                      servicoRepository
                          .findById(beneficio.getServiceId())
                          .map(Servico::getName)
                          .orElse(null);
                  benefitResponse.quantidadeMensal = beneficio.getQuantidadeMensal();
                  return benefitResponse;
                })
            .toList();
    return r;
  }
}
