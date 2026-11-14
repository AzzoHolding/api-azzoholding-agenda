package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembership;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembershipBalance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ContaDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlan;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlanBenefit;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.SituacaoDaAssinatura;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.SituacaoDeAssinatura;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipBalanceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ContaDePagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MembershipPlanBenefitRepository;

/**
 * Clube de assinaturas no cartao recorrente (Fase 7 dos pagamentos).
 *
 * <p>O Azzo cria a assinatura na operadora conectada (a conta que tiver assinatura no cartao: a
 * padrao de "online" ou, sem ela, a primeira que oferecer). A cliente recebe o link e cadastra o
 * cartao na pagina da operadora. Cada mensalidade paga renova o periodo e os saldos — uma vez por
 * cobranca: o Azzo guarda quantas ja aplicou e compara com o que a operadora diz, entao aviso
 * repetido ou consulta repetida nao renovam duas vezes.
 */
@Service
public class ServicoDeAssinaturasNoCartao {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoDeAssinaturasNoCartao.class);

  /** A operadora avisou algo sobre uma assinatura: reconsultar e aplicar. */
  public record AssinaturaAvisada(UUID tenantId, String provedor, String assinaturaNaOperadora) {}

  private final ClientMembershipRepository memberships;
  private final ClientMembershipBalanceRepository saldos;
  private final MembershipPlanBenefitRepository beneficios;
  private final ContaDePagamentoRepository contas;
  private final ServicoDeContasDePagamento servicoDeContas;
  private final TransactionTemplate transacao;
  private final String paginaDeRetorno;

  public ServicoDeAssinaturasNoCartao(
      ClientMembershipRepository memberships,
      ClientMembershipBalanceRepository saldos,
      MembershipPlanBenefitRepository beneficios,
      ContaDePagamentoRepository contas,
      ServicoDeContasDePagamento servicoDeContas,
      PlatformTransactionManager gerenciador,
      @Value("${app.public.booking.base-url:https://azzoholding.com.br}") String paginaDeRetorno) {
    this.memberships = memberships;
    this.saldos = saldos;
    this.beneficios = beneficios;
    this.contas = contas;
    this.servicoDeContas = servicoDeContas;
    this.transacao = new TransactionTemplate(gerenciador);
    this.paginaDeRetorno = paginaDeRetorno;
  }

  /** A conta que cobra assinatura no cartao, ou erro explicando onde configurar. */
  public ContaDePagamento contaDoCartao(UUID tenantId) {
    return servicoDeContas.padrao(tenantId, "ONLINE")
        .filter(c -> recorrente(c.getProvedor()))
        .or(() -> contas.findByTenantIdAndAtivoTrueOrderByCreatedAtAsc(tenantId).stream()
            .filter(c -> recorrente(c.getProvedor())).findFirst())
        .orElseThrow(() -> new ApiClientErrorException(
            "Nenhuma conta conectada cobra assinatura no cartão. Conecte o Mercado Pago em Configurações → Integrações → Pagamentos.",
            400));
  }

  /**
   * Cria a assinatura na operadora para a assinatura do Azzo ja montada (sem salvar). Ela fica
   * AGUARDANDO_CARTAO, com o link da operadora para a cliente.
   */
  public void criarNaOperadora(ClientMembership membership, Cliente cliente, MembershipPlan plano) {
    ContaDePagamento conta = contaDoCartao(membership.getTenantId());
    ProvedorDePagamento provedor = servicoDeContas.provedor(conta.getProvedor());
    if (membership.getId() == null) membership.setId(UUID.randomUUID());
    ProvedorDePagamento.AssinaturaCriada criada;
    try {
      criada = provedor.criarAssinatura(servicoDeContas.abrir(conta), new ProvedorDePagamento.PedidoDeAssinatura(
          membership.getId().toString(), "Assinatura - " + plano.getNome(), plano.getPrecoMensal(), cliente.getEmail(),
          paginaDeRetorno));
    } catch (ProvedorDePagamento.FalhaNaOperadora e) {
      throw new ApiClientErrorException(e.getMessage(), 502);
    }
    membership.setContaPagamentoId(conta.getId());
    membership.setProvedorRecorrencia(conta.getProvedor());
    membership.setAssinaturaNaOperadora(criada.idNaOperadora());
    membership.setLinkDoCartao(criada.linkDoCartao());
    membership.setStatus(ClientMembership.STATUS_AGUARDANDO_CARTAO);
  }

  /** Cancela na operadora (melhor esforco: o cancelamento local nao espera a operadora). */
  public void cancelarNaOperadora(ClientMembership membership) {
    if (membership.getAssinaturaNaOperadora() == null || membership.getContaPagamentoId() == null) return;
    try {
      ContaDePagamento conta = contas.findById(membership.getContaPagamentoId()).orElse(null);
      if (conta == null) return;
      servicoDeContas.provedor(conta.getProvedor()).cancelarAssinatura(servicoDeContas.abrir(conta), membership.getAssinaturaNaOperadora());
    } catch (RuntimeException e) {
      LOG.warn("clube_cancelar_na_operadora_falhou membership_id={}", membership.getId(), e);
    }
  }

  @EventListener
  public void aoAviso(AssinaturaAvisada aviso) {
    memberships.findByProvedorRecorrenciaAndAssinaturaNaOperadora(aviso.provedor(), aviso.assinaturaNaOperadora())
        .filter(m -> m.getTenantId().equals(aviso.tenantId()))
        .ifPresent(m -> sincronizar(m.getId()));
  }

  /** Consulta as que pedem (esperando cartao ou com o periodo vencendo). Devolve quantas mudaram. */
  public int consultarPendentes() {
    int mudaram = 0;
    for (ClientMembership m : memberships.cartaoRecorrenteParaConsultar(Instant.now().plus(2, ChronoUnit.HOURS), Limit.of(50))) {
      try {
        if (sincronizar(m.getId())) mudaram++;
      } catch (RuntimeException e) {
        LOG.warn("clube_consulta_falhou membership_id={}", m.getId(), e);
      }
    }
    return mudaram;
  }

  /** Consulta a operadora e aplica. {@code true} se algo mudou. */
  public boolean sincronizar(UUID membershipId) {
    ClientMembership atual = memberships.findById(membershipId).orElse(null);
    if (atual == null || atual.getAssinaturaNaOperadora() == null || atual.getContaPagamentoId() == null) return false;
    ContaDePagamento conta = contas.findById(atual.getContaPagamentoId()).orElse(null);
    if (conta == null) return false;
    SituacaoDaAssinatura situacao = servicoDeContas.provedor(conta.getProvedor())
        .consultarAssinatura(servicoDeContas.abrir(conta), atual.getAssinaturaNaOperadora());
    Boolean mudou = transacao.execute(s -> {
      ClientMembership m = memberships.findById(membershipId).orElseThrow();
      return aplicar(m, situacao, saldos.findByMembershipId(m.getId()), beneficiosDoPlano(m), Instant.now());
    });
    return Boolean.TRUE.equals(mudou);
  }

  private List<MembershipPlanBenefit> beneficiosDoPlano(ClientMembership m) {
    return m.getPlanId() == null ? List.of() : beneficios.findByPlanId(m.getPlanId());
  }

  /**
   * A regra, sem banco (testada direto): cada cobranca paga nova ativa (a primeira) ou renova o
   * periodo; cancelada na operadora vira "cancela ao fim do periodo"; pausada ou em atraso vira
   * inadimplente. Cancelada no Azzo nao volta.
   */
  static boolean aplicar(ClientMembership m, SituacaoDaAssinatura s, List<ClientMembershipBalance> saldosDaAssinatura,
      List<MembershipPlanBenefit> beneficiosDoPlano, Instant agora) {
    if (ClientMembership.STATUS_CANCELADA.equals(m.getStatus())) return false;
    boolean mudou = false;
    int novas = s.cobrancasPagas() - m.getCobrancasPagasNaOperadora();
    for (int i = 0; i < novas; i++) {
      if (ClientMembership.STATUS_AGUARDANDO_CARTAO.equals(m.getStatus())) {
        // Primeira mensalidade: o periodo comeca agora, com os saldos cheios.
        m.setPeriodStart(agora);
        m.setPeriodEnd(agora.plus(30, ChronoUnit.DAYS));
      } else {
        m.setPeriodStart(m.getPeriodEnd());
        m.setPeriodEnd(m.getPeriodStart().plus(30, ChronoUnit.DAYS));
        renovarSaldos(m, saldosDaAssinatura, beneficiosDoPlano);
      }
      m.setStatus(ClientMembership.STATUS_ATIVA);
      mudou = true;
    }
    if (novas > 0) m.setCobrancasPagasNaOperadora(s.cobrancasPagas());

    if (s.situacao() == SituacaoDeAssinatura.CANCELADA && !m.isCancelAtPeriodEnd()) {
      m.setCancelAtPeriodEnd(true);
      if (ClientMembership.STATUS_AGUARDANDO_CARTAO.equals(m.getStatus())) {
        m.setStatus(ClientMembership.STATUS_CANCELADA);
      }
      mudou = true;
    } else if ((s.situacao() == SituacaoDeAssinatura.PAUSADA || s.emAtraso())
        && ClientMembership.STATUS_ATIVA.equals(m.getStatus()) && novas <= 0) {
      m.setStatus(ClientMembership.STATUS_INADIMPLENTE);
      mudou = true;
    }
    return mudou;
  }

  /** Mesma regra da renovacao pelo Asaas: cumulativo soma mais uma cota; senao zera o usado. */
  static void renovarSaldos(ClientMembership m, List<ClientMembershipBalance> saldosDaAssinatura,
      List<MembershipPlanBenefit> beneficiosDoPlano) {
    for (ClientMembershipBalance saldo : saldosDaAssinatura) {
      if (m.isCumulativo()) {
        beneficiosDoPlano.stream().filter(b -> b.getServiceId().equals(saldo.getServiceId())).findFirst()
            .ifPresent(b -> saldo.setQuantidadeMensal(saldo.getQuantidadeMensal() + b.getQuantidadeMensal()));
      } else {
        saldo.setUsadasNoPeriodo(0);
      }
    }
  }

  private boolean recorrente(String provedor) {
    try {
      return servicoDeContas.provedor(provedor).capacidades().contains(ProvedorDePagamento.Capacidade.RECORRENTE);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
