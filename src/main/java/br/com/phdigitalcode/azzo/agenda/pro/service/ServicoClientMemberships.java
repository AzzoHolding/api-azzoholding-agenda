package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/** Espelha {@code modules/membership/application/ServicoClientMemberships.java}. */
@Service
public class ServicoClientMemberships {

  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private final ContextoTenant contextoTenant;
  private final ClientMembershipRepository clientMembershipRepository;
  private final ClientMembershipBalanceRepository clientMembershipBalanceRepository;
  private final MembershipPlanRepository membershipPlanRepository;
  private final MembershipPlanBenefitRepository membershipPlanBenefitRepository;
  private final ClienteRepository clienteRepository;
  private final ServicoRepository servicoRepository;
  private final TenantAsaasChargeService tenantAsaasChargeService;
  private final AsaasClient asaasClient;

  public ServicoClientMemberships(
      ContextoTenant contextoTenant,
      ClientMembershipRepository clientMembershipRepository,
      ClientMembershipBalanceRepository clientMembershipBalanceRepository,
      MembershipPlanRepository membershipPlanRepository,
      MembershipPlanBenefitRepository membershipPlanBenefitRepository,
      ClienteRepository clienteRepository,
      ServicoRepository servicoRepository,
      TenantAsaasChargeService tenantAsaasChargeService,
      AsaasClient asaasClient) {
    this.contextoTenant = contextoTenant;
    this.clientMembershipRepository = clientMembershipRepository;
    this.clientMembershipBalanceRepository = clientMembershipBalanceRepository;
    this.membershipPlanRepository = membershipPlanRepository;
    this.membershipPlanBenefitRepository = membershipPlanBenefitRepository;
    this.clienteRepository = clienteRepository;
    this.servicoRepository = servicoRepository;
    this.tenantAsaasChargeService = tenantAsaasChargeService;
    this.asaasClient = asaasClient;
  }

  @Transactional(readOnly = true)
  public List<MembershipDtos.ClientMembershipResponse> listarDoCliente(UUID clientId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return clientMembershipRepository
        .findByTenantIdAndClientIdOrderByCreatedAtDesc(tenantId, clientId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public MembershipDtos.ClientMembershipResponse assinar(
      UUID clientId, MembershipDtos.SubscribeRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente cliente =
        clienteRepository
            .findByIdAndTenantId(clientId, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Cliente nao encontrado.", 404));
    MembershipPlan plano =
        membershipPlanRepository
            .findByIdAndTenantId(UUID.fromString(request.planId), tenantId)
            .orElseThrow(
                () -> new ApiClientErrorException("Plano de assinatura nao encontrado.", 404));
    if (!plano.isAtivo()) {
      throw new IllegalArgumentException("Plano de assinatura nao esta ativo.");
    }

    if (request.customerCpfCnpj != null && !request.customerCpfCnpj.isBlank()) {
      cliente.setCpfCnpj(request.customerCpfCnpj.trim());
    }

    String apiKey = tenantAsaasChargeService.resolveApiKeyAtivaOuFalhar(tenantId);
    tenantAsaasChargeService.ensureAsaasCustomer(apiKey, cliente);

    AsaasDtos.CreateSubscriptionRequest subscriptionRequest =
        new AsaasDtos.CreateSubscriptionRequest();
    subscriptionRequest.customer = cliente.getAsaasCustomerId();
    subscriptionRequest.billingType = "PIX";
    subscriptionRequest.value = plano.getPrecoMensal();
    subscriptionRequest.cycle = "MONTHLY";
    subscriptionRequest.nextDueDate = LocalDate.now(ZONE_BR).plusDays(1).toString();
    subscriptionRequest.description = "Assinatura - " + plano.getNome();
    subscriptionRequest.externalReference = "membership:client:" + clientId;

    AsaasDtos.SubscriptionResponse subscription =
        asaasClient.createSubscription(apiKey, subscriptionRequest);

    Instant now = Instant.now();
    ClientMembership membership = new ClientMembership();
    membership.setTenantId(tenantId);
    membership.setClientId(clientId);
    membership.setPlanId(plano.getId());
    membership.setPlanNome(plano.getNome());
    membership.setPrecoMensal(plano.getPrecoMensal());
    membership.setCumulativo(plano.isCumulativo());
    membership.setAsaasSubscriptionId(subscription.id);
    membership.setStatus(ClientMembership.STATUS_ATIVA);
    membership.setPeriodStart(now);
    membership.setPeriodEnd(now.plus(30, ChronoUnit.DAYS));
    clientMembershipRepository.save(membership);

    for (MembershipPlanBenefit beneficio :
        membershipPlanBenefitRepository.findByPlanId(plano.getId())) {
      ClientMembershipBalance saldo = new ClientMembershipBalance();
      saldo.setTenantId(tenantId);
      saldo.setMembershipId(membership.getId());
      saldo.setServiceId(beneficio.getServiceId());
      saldo.setServiceNome(
          servicoRepository.findById(beneficio.getServiceId()).map(Servico::getName).orElse(null));
      saldo.setQuantidadeMensal(beneficio.getQuantidadeMensal());
      clientMembershipBalanceRepository.save(saldo);
    }

    return toResponse(membership);
  }

  @Transactional
  public MembershipDtos.ClientMembershipResponse cancelar(UUID membershipId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ClientMembership membership =
        clientMembershipRepository
            .findByIdAndTenantId(membershipId, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Assinatura nao encontrada.", 404));
    if (ClientMembership.STATUS_CANCELADA.equals(membership.getStatus())) {
      throw new IllegalArgumentException("Assinatura ja esta cancelada.");
    }

    if (membership.getAsaasSubscriptionId() != null) {
      try {
        String apiKey = tenantAsaasChargeService.resolveApiKeyAtivaOuFalhar(tenantId);
        asaasClient.cancelSubscription(apiKey, membership.getAsaasSubscriptionId());
      } catch (Exception ignored) {
        // melhor esforco: nao bloqueia o cancelamento local por falha no Asaas
      }
    }

    // Nao revoga o periodo corrente (ja pago) -- so impede a renovacao. A transicao para
    // CANCELADA de fato acontece quando periodEnd vence (ClientMembershipCancellationScheduler).
    membership.setCancelAtPeriodEnd(true);

    return toResponse(membership);
  }

  private MembershipDtos.ClientMembershipResponse toResponse(ClientMembership membership) {
    MembershipDtos.ClientMembershipResponse r = new MembershipDtos.ClientMembershipResponse();
    r.id = membership.getId().toString();
    r.planId = membership.getPlanId() != null ? membership.getPlanId().toString() : null;
    r.planNome = membership.getPlanNome();
    r.precoMensal = membership.getPrecoMensal();
    r.status = membership.getStatus();
    r.periodStart =
        membership.getPeriodStart() != null ? membership.getPeriodStart().toString() : null;
    r.periodEnd = membership.getPeriodEnd() != null ? membership.getPeriodEnd().toString() : null;
    r.cancelAtPeriodEnd = membership.isCancelAtPeriodEnd();
    r.saldos =
        clientMembershipBalanceRepository.findByMembershipId(membership.getId()).stream()
            .map(
                saldo -> {
                  MembershipDtos.BalanceResponse balanceResponse =
                      new MembershipDtos.BalanceResponse();
                  balanceResponse.serviceId = saldo.getServiceId().toString();
                  balanceResponse.serviceNome = saldo.getServiceNome();
                  balanceResponse.quantidadeMensal = saldo.getQuantidadeMensal();
                  balanceResponse.usadasNoPeriodo = saldo.getUsadasNoPeriodo();
                  balanceResponse.disponiveis =
                      saldo.getQuantidadeMensal() - saldo.getUsadasNoPeriodo();
                  return balanceResponse;
                })
            .toList();
    return r;
  }
}
