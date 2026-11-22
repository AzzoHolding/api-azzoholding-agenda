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

  /** Piloto dos pagamentos (14/11). Setter: os testes que montam este servico a mao seguem liberados. */
  private LiberacaoDePagamentos liberacao;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setLiberacao(LiberacaoDePagamentos liberacao) {
    this.liberacao = liberacao;
  }

  private void exigirLiberado(UUID tenantId) {
    if (liberacao != null) liberacao.exigir(tenantId);
  }

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

  /** As assinaturas do salao (as 200 mais recentes), com o nome da cliente. */
  @Transactional(readOnly = true)
  public List<MembershipDtos.ClientMembershipResponse> listarDoSalao() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<ClientMembership> lista = clientMembershipRepository.findByTenantIdOrderByCreatedAtDesc(
        tenantId, org.springframework.data.domain.Limit.of(200));
    java.util.Map<UUID, String> nomes = new java.util.HashMap<>();
    clienteRepository.findAllById(lista.stream().map(ClientMembership::getClientId).distinct().toList())
        .forEach(c -> nomes.put(c.getId(), c.getName()));
    return lista.stream().map(m -> {
      MembershipDtos.ClientMembershipResponse r = toResponse(m);
      r.clientNome = nomes.get(m.getClientId());
      return r;
    }).toList();
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
            .orElseThrow(() -> new ApiClientErrorException("Cliente não encontrado.", 404));
    MembershipPlan plano =
        membershipPlanRepository
            .findByIdAndTenantId(UUID.fromString(request.planId), tenantId)
            .orElseThrow(
                () -> new ApiClientErrorException("Plano de assinatura não encontrado.", 404));
    if (!plano.isAtivo()) {
      throw new IllegalArgumentException("Plano de assinatura não está ativo.");
    }

    if (request.customerCpfCnpj != null && !request.customerCpfCnpj.isBlank()) {
      cliente.setCpfCnpj(request.customerCpfCnpj.trim());
    }

    if ("CARTAO".equalsIgnoreCase(request.formaDeCobranca)) {
      exigirLiberado(tenantId);
      return assinarNoCartao(tenantId, clientId, cliente, plano);
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

    auditar(tenantId, "CLUBE_ASSINATURA_CRIADA", membership);
    return toResponse(membership);
  }

  /**
   * Cartao recorrente (Fase 7): a assinatura nasce AGUARDANDO_CARTAO, com o link da operadora. Os
   * saldos ja sao criados, mas so valem quando a primeira mensalidade for paga (a cobertura na
   * comanda exige assinatura ATIVA).
   */
  private MembershipDtos.ClientMembershipResponse assinarNoCartao(
      UUID tenantId, UUID clientId, Cliente cliente, MembershipPlan plano) {
    if (assinaturasNoCartao == null) {
      throw new IllegalArgumentException("Assinatura no cartão indisponível.");
    }
    Instant now = Instant.now();
    ClientMembership membership = new ClientMembership();
    membership.setTenantId(tenantId);
    membership.setClientId(clientId);
    membership.setPlanId(plano.getId());
    membership.setPlanNome(plano.getNome());
    membership.setPrecoMensal(plano.getPrecoMensal());
    membership.setCumulativo(plano.isCumulativo());
    membership.setPeriodStart(now);
    membership.setPeriodEnd(now);
    assinaturasNoCartao.criarNaOperadora(membership, cliente, plano);
    clientMembershipRepository.save(membership);
    criarSaldos(tenantId, membership, plano);
    auditar(tenantId, "CLUBE_ASSINATURA_CRIADA", membership);
    return toResponse(membership);
  }

  private void criarSaldos(UUID tenantId, ClientMembership membership, MembershipPlan plano) {
    for (MembershipPlanBenefit beneficio : membershipPlanBenefitRepository.findByPlanId(plano.getId())) {
      ClientMembershipBalance saldo = new ClientMembershipBalance();
      saldo.setTenantId(tenantId);
      saldo.setMembershipId(membership.getId());
      saldo.setServiceId(beneficio.getServiceId());
      saldo.setServiceNome(servicoRepository.findById(beneficio.getServiceId()).map(Servico::getName).orElse(null));
      saldo.setQuantidadeMensal(beneficio.getQuantidadeMensal());
      clientMembershipBalanceRepository.save(saldo);
    }
  }

  private br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService auditService;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setAuditService(br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService auditService) {
    this.auditService = auditService;
  }

  private void auditar(UUID tenantId, String acao, ClientMembership m) {
    if (auditService == null) return;
    var command = new br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand();
    command.tenantId = tenantId;
    command.module = br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants.Module.SETTINGS;
    command.action = acao;
    command.entityType = "CLIENT_MEMBERSHIP";
    command.entityId = m.getId().toString();
    command.sourceChannel = br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants.SourceChannel.API;
    java.util.Map<String, Object> depois = new java.util.LinkedHashMap<>();
    depois.put("plano", m.getPlanNome());
    depois.put("valorMensal", m.getPrecoMensal());
    depois.put("situacao", m.getStatus());
    depois.put("cobranca", m.getAssinaturaNaOperadora() != null ? "Cartão recorrente" : "Pix (Asaas)");
    depois.put("cancelaAoFimDoPeriodo", m.isCancelAtPeriodEnd());
    command.after = depois;
    auditService.recordSuccess(command);
  }

  private br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDeAssinaturasNoCartao assinaturasNoCartao;

  /** Setter (e nao construtor): os testes que montam este servico a mao seguem sem o cartao. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setAssinaturasNoCartao(br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDeAssinaturasNoCartao assinaturasNoCartao) {
    this.assinaturasNoCartao = assinaturasNoCartao;
  }

  @Transactional
  public MembershipDtos.ClientMembershipResponse cancelar(UUID membershipId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ClientMembership membership =
        clientMembershipRepository
            .findByIdAndTenantId(membershipId, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Assinatura não encontrada.", 404));
    if (ClientMembership.STATUS_CANCELADA.equals(membership.getStatus())) {
      throw new IllegalArgumentException("Assinatura já está cancelada.");
    }

    if (membership.getAsaasSubscriptionId() != null) {
      try {
        String apiKey = tenantAsaasChargeService.resolveApiKeyAtivaOuFalhar(tenantId);
        asaasClient.cancelSubscription(apiKey, membership.getAsaasSubscriptionId());
      } catch (Exception ignored) {
        // melhor esforco: nao bloqueia o cancelamento local por falha no Asaas
      }
    }

    if (membership.getAssinaturaNaOperadora() != null && assinaturasNoCartao != null) {
      assinaturasNoCartao.cancelarNaOperadora(membership);
    }

    // Nao revoga o periodo corrente (ja pago) -- so impede a renovacao. A transicao para
    // CANCELADA de fato acontece quando periodEnd vence (ClientMembershipCancellationScheduler).
    membership.setCancelAtPeriodEnd(true);
    // Sem cartao cadastrado nao ha periodo pago: cancela ja.
    if (ClientMembership.STATUS_AGUARDANDO_CARTAO.equals(membership.getStatus())) {
      membership.setStatus(ClientMembership.STATUS_CANCELADA);
    }
    auditar(tenantId, "CLUBE_ASSINATURA_CANCELADA", membership);

    return toResponse(membership);
  }

  private MembershipDtos.ClientMembershipResponse toResponse(ClientMembership membership) {
    MembershipDtos.ClientMembershipResponse r = new MembershipDtos.ClientMembershipResponse();
    r.id = membership.getId().toString();
    r.clientId = membership.getClientId() != null ? membership.getClientId().toString() : null;
    r.planId = membership.getPlanId() != null ? membership.getPlanId().toString() : null;
    r.planNome = membership.getPlanNome();
    r.precoMensal = membership.getPrecoMensal();
    r.status = membership.getStatus();
    r.periodStart =
        membership.getPeriodStart() != null ? membership.getPeriodStart().toString() : null;
    r.periodEnd = membership.getPeriodEnd() != null ? membership.getPeriodEnd().toString() : null;
    r.cancelAtPeriodEnd = membership.isCancelAtPeriodEnd();
    r.formaDeCobranca = membership.getAssinaturaNaOperadora() != null ? "CARTAO" : "PIX";
    r.linkDoCartao = ClientMembership.STATUS_AGUARDANDO_CARTAO.equals(membership.getStatus())
        ? membership.getLinkDoCartao() : null;
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
