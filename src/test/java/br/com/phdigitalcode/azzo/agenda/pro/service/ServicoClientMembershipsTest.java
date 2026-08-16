package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MembershipDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembership;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembershipBalance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlan;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlanBenefit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasDtos;
import br.com.phdigitalcode.azzo.agenda.pro.integration.TenantAsaasChargeService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipBalanceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MembershipPlanBenefitRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MembershipPlanRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre {@code modules/membership/application/ServicoClientMemberships.java}: contrato da assinatura
 * criada no Asaas, materializacao dos saldos por beneficio e a semantica do cancelamento (que apenas
 * suspende a renovacao, sem revogar o periodo ja pago).
 */
class ServicoClientMembershipsTest {

  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private ClientMembershipRepository clientMembershipRepository;
  private ClientMembershipBalanceRepository clientMembershipBalanceRepository;
  private MembershipPlanRepository membershipPlanRepository;
  private MembershipPlanBenefitRepository membershipPlanBenefitRepository;
  private ClienteRepository clienteRepository;
  private ServicoRepository servicoRepository;
  private TenantAsaasChargeService tenantAsaasChargeService;
  private AsaasClient asaasClient;
  private ServicoClientMemberships service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID planId = UUID.randomUUID();
  private final UUID serviceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    clientMembershipRepository = mock(ClientMembershipRepository.class);
    clientMembershipBalanceRepository = mock(ClientMembershipBalanceRepository.class);
    membershipPlanRepository = mock(MembershipPlanRepository.class);
    membershipPlanBenefitRepository = mock(MembershipPlanBenefitRepository.class);
    clienteRepository = mock(ClienteRepository.class);
    servicoRepository = mock(ServicoRepository.class);
    tenantAsaasChargeService = mock(TenantAsaasChargeService.class);
    asaasClient = mock(AsaasClient.class);

    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);

    when(clientMembershipRepository.save(any(ClientMembership.class)))
        .thenAnswer(
            inv -> {
              ClientMembership m = inv.getArgument(0);
              if (m.getId() == null) m.setId(UUID.randomUUID());
              return m;
            });
    when(clientMembershipBalanceRepository.findByMembershipId(any())).thenReturn(List.of());
    when(membershipPlanBenefitRepository.findByPlanId(any())).thenReturn(List.of());

    service =
        new ServicoClientMemberships(
            contextoTenant,
            clientMembershipRepository,
            clientMembershipBalanceRepository,
            membershipPlanRepository,
            membershipPlanBenefitRepository,
            clienteRepository,
            servicoRepository,
            tenantAsaasChargeService,
            asaasClient);
  }

  private Cliente clienteExiste() {
    Cliente cliente = new Cliente();
    cliente.setId(clientId);
    cliente.setTenantId(tenantId);
    cliente.setName("Maria");
    cliente.setAsaasCustomerId("cus_123");
    when(clienteRepository.findByIdAndTenantId(eq(clientId), eq(tenantId)))
        .thenReturn(Optional.of(cliente));
    return cliente;
  }

  private MembershipPlan planoExiste(boolean ativo) {
    MembershipPlan plano = new MembershipPlan();
    plano.setId(planId);
    plano.setTenantId(tenantId);
    plano.setNome("Clube do Corte");
    plano.setPrecoMensal(new BigDecimal("99.90"));
    plano.setCumulativo(true);
    plano.setAtivo(ativo);
    when(membershipPlanRepository.findByIdAndTenantId(eq(planId), eq(tenantId)))
        .thenReturn(Optional.of(plano));
    return plano;
  }

  private void asaasResponde(String subscriptionId) {
    when(tenantAsaasChargeService.resolveApiKeyAtivaOuFalhar(eq(tenantId))).thenReturn("key");
    AsaasDtos.SubscriptionResponse subscription = new AsaasDtos.SubscriptionResponse();
    subscription.id = subscriptionId;
    when(asaasClient.createSubscription(anyString(), any())).thenReturn(subscription);
  }

  private MembershipDtos.SubscribeRequest subscribeRequest(String cpfCnpj) {
    MembershipDtos.SubscribeRequest req = new MembershipDtos.SubscribeRequest();
    req.planId = planId.toString();
    req.customerCpfCnpj = cpfCnpj;
    return req;
  }

  @Test
  void assinarCriaSubscriptionPixMensalComVencimentoAmanhaEReferenciaExterna() {
    clienteExiste();
    planoExiste(true);
    asaasResponde("sub_987");

    Instant antes = Instant.now();
    MembershipDtos.ClientMembershipResponse response =
        service.assinar(clientId, subscribeRequest(null));

    ArgumentCaptor<AsaasDtos.CreateSubscriptionRequest> asaasCaptor =
        ArgumentCaptor.forClass(AsaasDtos.CreateSubscriptionRequest.class);
    verify(asaasClient).createSubscription(eq("key"), asaasCaptor.capture());
    AsaasDtos.CreateSubscriptionRequest enviado = asaasCaptor.getValue();
    assertThat(enviado.customer).isEqualTo("cus_123");
    assertThat(enviado.billingType).isEqualTo("PIX");
    assertThat(enviado.cycle).isEqualTo("MONTHLY");
    assertThat(enviado.value).isEqualByComparingTo("99.90");
    assertThat(enviado.nextDueDate).isEqualTo(LocalDate.now(ZONE_BR).plusDays(1).toString());
    assertThat(enviado.description).isEqualTo("Assinatura - Clube do Corte");
    assertThat(enviado.externalReference).isEqualTo("membership:client:" + clientId);

    ArgumentCaptor<ClientMembership> membershipCaptor =
        ArgumentCaptor.forClass(ClientMembership.class);
    verify(clientMembershipRepository).save(membershipCaptor.capture());
    ClientMembership membership = membershipCaptor.getValue();
    assertThat(membership.getTenantId()).isEqualTo(tenantId);
    assertThat(membership.getClientId()).isEqualTo(clientId);
    assertThat(membership.getPlanId()).isEqualTo(planId);
    // Snapshot do plano no momento da assinatura — alteracao posterior do plano nao muda o contrato.
    assertThat(membership.getPlanNome()).isEqualTo("Clube do Corte");
    assertThat(membership.getPrecoMensal()).isEqualByComparingTo("99.90");
    assertThat(membership.isCumulativo()).isTrue();
    assertThat(membership.getAsaasSubscriptionId()).isEqualTo("sub_987");
    assertThat(membership.getStatus()).isEqualTo(ClientMembership.STATUS_ATIVA);
    assertThat(membership.isCancelAtPeriodEnd()).isFalse();
    assertThat(membership.getPeriodStart()).isAfterOrEqualTo(antes);
    assertThat(membership.getPeriodEnd())
        .isEqualTo(membership.getPeriodStart().plus(30, ChronoUnit.DAYS));

    assertThat(response.status).isEqualTo(ClientMembership.STATUS_ATIVA);
    assertThat(response.planNome).isEqualTo("Clube do Corte");
  }

  @Test
  void assinarMaterializaUmSaldoPorBeneficioComNomeDoServico() {
    clienteExiste();
    planoExiste(true);
    asaasResponde("sub_1");

    MembershipPlanBenefit beneficio = new MembershipPlanBenefit();
    beneficio.setServiceId(serviceId);
    beneficio.setQuantidadeMensal(3);
    when(membershipPlanBenefitRepository.findByPlanId(eq(planId))).thenReturn(List.of(beneficio));

    Servico servico = new Servico();
    servico.setId(serviceId);
    servico.setName("Corte");
    when(servicoRepository.findById(eq(serviceId))).thenReturn(Optional.of(servico));

    service.assinar(clientId, subscribeRequest(null));

    ArgumentCaptor<ClientMembershipBalance> saldoCaptor =
        ArgumentCaptor.forClass(ClientMembershipBalance.class);
    verify(clientMembershipBalanceRepository).save(saldoCaptor.capture());
    ClientMembershipBalance saldo = saldoCaptor.getValue();
    assertThat(saldo.getTenantId()).isEqualTo(tenantId);
    assertThat(saldo.getServiceId()).isEqualTo(serviceId);
    assertThat(saldo.getServiceNome()).isEqualTo("Corte");
    assertThat(saldo.getQuantidadeMensal()).isEqualTo(3);
    assertThat(saldo.getUsadasNoPeriodo()).isZero();
  }

  @Test
  void assinarAtualizaCpfCnpjDoClienteComTrimQuandoInformado() {
    Cliente cliente = clienteExiste();
    planoExiste(true);
    asaasResponde("sub_1");

    service.assinar(clientId, subscribeRequest("  12345678909  "));

    assertThat(cliente.getCpfCnpj()).isEqualTo("12345678909");
    verify(tenantAsaasChargeService).ensureAsaasCustomer(eq("key"), eq(cliente));
  }

  @Test
  void assinarComPlanoInativoFalhaAntesDeChamarOAsaas() {
    clienteExiste();
    planoExiste(false);

    assertThatThrownBy(() -> service.assinar(clientId, subscribeRequest(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Plano de assinatura nao esta ativo.");

    verify(asaasClient, never()).createSubscription(anyString(), any());
    verify(clientMembershipRepository, never()).save(any());
  }

  @Test
  void assinarComClienteDeOutroTenantFalhaCom404() {
    when(clienteRepository.findByIdAndTenantId(eq(clientId), eq(tenantId)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.assinar(clientId, subscribeRequest(null)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Cliente nao encontrado.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void cancelarApenasSuspendeRenovacaoSemMudarStatusNemPeriodo() {
    UUID membershipId = UUID.randomUUID();
    Instant periodEnd = Instant.now().plus(20, ChronoUnit.DAYS);
    ClientMembership membership = new ClientMembership();
    membership.setId(membershipId);
    membership.setTenantId(tenantId);
    membership.setClientId(clientId);
    membership.setPlanNome("Clube");
    membership.setPrecoMensal(new BigDecimal("99.90"));
    membership.setStatus(ClientMembership.STATUS_ATIVA);
    membership.setPeriodStart(Instant.now());
    membership.setPeriodEnd(periodEnd);
    membership.setAsaasSubscriptionId("sub_987");
    when(clientMembershipRepository.findByIdAndTenantId(eq(membershipId), eq(tenantId)))
        .thenReturn(Optional.of(membership));
    when(tenantAsaasChargeService.resolveApiKeyAtivaOuFalhar(eq(tenantId))).thenReturn("key");

    MembershipDtos.ClientMembershipResponse response = service.cancelar(membershipId);

    verify(asaasClient).cancelSubscription(eq("key"), eq("sub_987"));
    assertThat(membership.isCancelAtPeriodEnd()).isTrue();
    // O periodo ja pago continua valido — quem faz a transicao final e o scheduler de manutencao.
    assertThat(membership.getStatus()).isEqualTo(ClientMembership.STATUS_ATIVA);
    assertThat(membership.getPeriodEnd()).isEqualTo(periodEnd);
    assertThat(response.cancelAtPeriodEnd).isTrue();
    assertThat(response.status).isEqualTo(ClientMembership.STATUS_ATIVA);
  }

  @Test
  void cancelarSegueEmFrenteQuandoOAsaasFalha() {
    UUID membershipId = UUID.randomUUID();
    ClientMembership membership = new ClientMembership();
    membership.setId(membershipId);
    membership.setTenantId(tenantId);
    membership.setPlanNome("Clube");
    membership.setStatus(ClientMembership.STATUS_ATIVA);
    membership.setAsaasSubscriptionId("sub_987");
    when(clientMembershipRepository.findByIdAndTenantId(eq(membershipId), eq(tenantId)))
        .thenReturn(Optional.of(membership));
    when(tenantAsaasChargeService.resolveApiKeyAtivaOuFalhar(eq(tenantId))).thenReturn("key");
    doThrow(new RuntimeException("Asaas fora do ar"))
        .when(asaasClient)
        .cancelSubscription(anyString(), anyString());

    service.cancelar(membershipId);

    // Melhor esforco: a falha remota nao pode impedir o cancelamento local.
    assertThat(membership.isCancelAtPeriodEnd()).isTrue();
  }

  @Test
  void cancelarAssinaturaJaCanceladaFalha() {
    UUID membershipId = UUID.randomUUID();
    ClientMembership membership = new ClientMembership();
    membership.setId(membershipId);
    membership.setTenantId(tenantId);
    membership.setStatus(ClientMembership.STATUS_CANCELADA);
    when(clientMembershipRepository.findByIdAndTenantId(eq(membershipId), eq(tenantId)))
        .thenReturn(Optional.of(membership));

    assertThatThrownBy(() -> service.cancelar(membershipId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Assinatura ja esta cancelada.");

    verify(asaasClient, never()).cancelSubscription(anyString(), anyString());
  }

  @Test
  void listarDoClienteCalculaDisponiveisComoQuantidadeMensalMenosUsadas() {
    UUID membershipId = UUID.randomUUID();
    ClientMembership membership = new ClientMembership();
    membership.setId(membershipId);
    membership.setTenantId(tenantId);
    membership.setClientId(clientId);
    membership.setPlanId(planId);
    membership.setPlanNome("Clube");
    membership.setPrecoMensal(new BigDecimal("99.90"));
    membership.setStatus(ClientMembership.STATUS_ATIVA);
    when(clientMembershipRepository.findByTenantIdAndClientIdOrderByCreatedAtDesc(
            eq(tenantId), eq(clientId)))
        .thenReturn(List.of(membership));

    ClientMembershipBalance saldo = new ClientMembershipBalance();
    saldo.setServiceId(serviceId);
    saldo.setServiceNome("Corte");
    saldo.setQuantidadeMensal(4);
    saldo.setUsadasNoPeriodo(3);
    when(clientMembershipBalanceRepository.findByMembershipId(eq(membershipId)))
        .thenReturn(List.of(saldo));

    List<MembershipDtos.ClientMembershipResponse> assinaturas = service.listarDoCliente(clientId);

    assertThat(assinaturas).hasSize(1);
    assertThat(assinaturas.get(0).periodStart).isNull();
    assertThat(assinaturas.get(0).saldos).hasSize(1);
    assertThat(assinaturas.get(0).saldos.get(0).quantidadeMensal).isEqualTo(4);
    assertThat(assinaturas.get(0).saldos.get(0).usadasNoPeriodo).isEqualTo(3);
    assertThat(assinaturas.get(0).saldos.get(0).disponiveis).isEqualTo(1);
  }
}
