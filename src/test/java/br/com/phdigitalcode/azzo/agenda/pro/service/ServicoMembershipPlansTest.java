package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MembershipDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlan;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlanBenefit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MembershipPlanBenefitRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MembershipPlanRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre {@code modules/membership/application/ServicoMembershipPlans.java}: persistencia do plano
 * com tenant, validacao de servico do beneficio e regravacao integral dos beneficios no update.
 */
class ServicoMembershipPlansTest {

  private MembershipPlanRepository membershipPlanRepository;
  private MembershipPlanBenefitRepository membershipPlanBenefitRepository;
  private ServicoRepository servicoRepository;
  private ServicoMembershipPlans service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID serviceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    membershipPlanRepository = mock(MembershipPlanRepository.class);
    membershipPlanBenefitRepository = mock(MembershipPlanBenefitRepository.class);
    servicoRepository = mock(ServicoRepository.class);

    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);

    when(membershipPlanRepository.save(any(MembershipPlan.class)))
        .thenAnswer(
            inv -> {
              MembershipPlan p = inv.getArgument(0);
              if (p.getId() == null) p.setId(UUID.randomUUID());
              return p;
            });
    when(membershipPlanBenefitRepository.save(any(MembershipPlanBenefit.class)))
        .thenAnswer(
            inv -> {
              MembershipPlanBenefit b = inv.getArgument(0);
              if (b.getId() == null) b.setId(UUID.randomUUID());
              return b;
            });
    when(membershipPlanBenefitRepository.findByPlanId(any())).thenReturn(List.of());

    service =
        new ServicoMembershipPlans(
            contextoTenant,
            membershipPlanRepository,
            membershipPlanBenefitRepository,
            servicoRepository);
  }

  private void servicoExiste(String nome) {
    Servico servico = new Servico();
    servico.setId(serviceId);
    servico.setTenantId(tenantId);
    servico.setName(nome);
    when(servicoRepository.findByIdAndTenantId(eq(serviceId), eq(tenantId)))
        .thenReturn(Optional.of(servico));
    when(servicoRepository.findById(eq(serviceId))).thenReturn(Optional.of(servico));
  }

  private MembershipDtos.PlanRequest requestComUmBeneficio(int quantidadeMensal) {
    MembershipDtos.BenefitRequest beneficio = new MembershipDtos.BenefitRequest();
    beneficio.serviceId = serviceId.toString();
    beneficio.quantidadeMensal = quantidadeMensal;

    MembershipDtos.PlanRequest req = new MembershipDtos.PlanRequest();
    req.nome = "Clube do Corte";
    req.descricao = "2 cortes por mes";
    req.precoMensal = new BigDecimal("99.90");
    req.cumulativo = true;
    req.ativo = true;
    req.beneficios = new ArrayList<>(List.of(beneficio));
    return req;
  }

  @Test
  void criarPersistePlanoComTenantEBeneficios() {
    servicoExiste("Corte");

    MembershipDtos.PlanResponse response = service.criar(requestComUmBeneficio(2));

    ArgumentCaptor<MembershipPlan> planoCaptor = ArgumentCaptor.forClass(MembershipPlan.class);
    verify(membershipPlanRepository).save(planoCaptor.capture());
    MembershipPlan plano = planoCaptor.getValue();
    assertThat(plano.getTenantId()).isEqualTo(tenantId);
    assertThat(plano.getNome()).isEqualTo("Clube do Corte");
    assertThat(plano.getPrecoMensal()).isEqualByComparingTo("99.90");
    assertThat(plano.isCumulativo()).isTrue();
    assertThat(plano.isAtivo()).isTrue();

    ArgumentCaptor<MembershipPlanBenefit> beneficioCaptor =
        ArgumentCaptor.forClass(MembershipPlanBenefit.class);
    verify(membershipPlanBenefitRepository).save(beneficioCaptor.capture());
    MembershipPlanBenefit beneficio = beneficioCaptor.getValue();
    assertThat(beneficio.getTenantId()).isEqualTo(tenantId);
    assertThat(beneficio.getPlanId()).isEqualTo(plano.getId());
    assertThat(beneficio.getServiceId()).isEqualTo(serviceId);
    assertThat(beneficio.getQuantidadeMensal()).isEqualTo(2);

    assertThat(response.id).isEqualTo(plano.getId().toString());
    assertThat(response.cumulativo).isTrue();
  }

  @Test
  void criarComServicoDeOutroTenantFalhaCom404() {
    when(servicoRepository.findByIdAndTenantId(eq(serviceId), eq(tenantId)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.criar(requestComUmBeneficio(1)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Servico nao encontrado: " + serviceId)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);

    verify(membershipPlanBenefitRepository, never()).save(any());
  }

  @Test
  void atualizarApagaBeneficiosAntigosAntesDeRegravar() {
    servicoExiste("Corte");
    MembershipPlan existente = new MembershipPlan();
    existente.setId(UUID.randomUUID());
    existente.setTenantId(tenantId);
    existente.setNome("antigo");
    existente.setCumulativo(false);
    when(membershipPlanRepository.findByIdAndTenantId(eq(existente.getId()), eq(tenantId)))
        .thenReturn(Optional.of(existente));

    MembershipDtos.PlanRequest req = requestComUmBeneficio(4);
    req.nome = "Clube Premium";
    req.ativo = false;

    service.atualizar(existente.getId(), req);

    InOrder ordem = inOrder(membershipPlanBenefitRepository);
    ordem.verify(membershipPlanBenefitRepository).deleteByPlanId(existente.getId());
    ordem.verify(membershipPlanBenefitRepository).flush();
    ordem.verify(membershipPlanBenefitRepository).save(any(MembershipPlanBenefit.class));

    assertThat(existente.getNome()).isEqualTo("Clube Premium");
    assertThat(existente.isAtivo()).isFalse();
    assertThat(existente.isCumulativo()).isTrue();
  }

  @Test
  void obterDePlanoInexistenteFalhaCom404() {
    UUID id = UUID.randomUUID();
    when(membershipPlanRepository.findByIdAndTenantId(eq(id), eq(tenantId)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.obter(id))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Plano de assinatura nao encontrado.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void listarResolveNomeDoServicoEDeixaNuloQuandoServicoSumiu() {
    UUID planoId = UUID.randomUUID();
    UUID servicoRemovidoId = UUID.randomUUID();
    MembershipPlan plano = new MembershipPlan();
    plano.setId(planoId);
    plano.setTenantId(tenantId);
    plano.setNome("Clube");
    plano.setPrecoMensal(new BigDecimal("50.00"));
    when(membershipPlanRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId)))
        .thenReturn(List.of(plano));

    servicoExiste("Corte");
    when(servicoRepository.findById(eq(servicoRemovidoId))).thenReturn(Optional.empty());

    MembershipPlanBenefit ok = new MembershipPlanBenefit();
    ok.setServiceId(serviceId);
    ok.setQuantidadeMensal(2);
    MembershipPlanBenefit orfao = new MembershipPlanBenefit();
    orfao.setServiceId(servicoRemovidoId);
    orfao.setQuantidadeMensal(1);
    when(membershipPlanBenefitRepository.findByPlanId(eq(planoId))).thenReturn(List.of(ok, orfao));

    List<MembershipDtos.PlanResponse> planos = service.listar();

    assertThat(planos).hasSize(1);
    assertThat(planos.get(0).createdAt).isNull();
    assertThat(planos.get(0).beneficios).hasSize(2);
    assertThat(planos.get(0).beneficios.get(0).serviceNome).isEqualTo("Corte");
    assertThat(planos.get(0).beneficios.get(0).quantidadeMensal).isEqualTo(2);
    assertThat(planos.get(0).beneficios.get(1).serviceNome).isNull();
  }
}
