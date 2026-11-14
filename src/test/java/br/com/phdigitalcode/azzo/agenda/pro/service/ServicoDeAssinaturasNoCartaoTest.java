package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembership;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembershipBalance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlanBenefit;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.SituacaoDaAssinatura;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.SituacaoDeAssinatura;

class ServicoDeAssinaturasNoCartaoTest {

  private final Instant agora = Instant.parse("2026-11-14T12:00:00Z");
  private final UUID servico = UUID.randomUUID();

  private ClientMembership aguardando() {
    ClientMembership m = new ClientMembership();
    m.setStatus(ClientMembership.STATUS_AGUARDANDO_CARTAO);
    m.setPeriodStart(agora.minus(1, ChronoUnit.DAYS));
    m.setPeriodEnd(agora.minus(1, ChronoUnit.DAYS));
    return m;
  }

  private ClientMembershipBalance saldo(int mensal, int usadas) {
    ClientMembershipBalance s = new ClientMembershipBalance();
    s.setServiceId(servico);
    s.setQuantidadeMensal(mensal);
    s.setUsadasNoPeriodo(usadas);
    return s;
  }

  private MembershipPlanBenefit beneficio(int mensal) {
    MembershipPlanBenefit b = new MembershipPlanBenefit();
    b.setServiceId(servico);
    b.setQuantidadeMensal(mensal);
    return b;
  }

  @Test
  void primeiraMensalidadeAtivaComPeriodoComecandoAgora() {
    ClientMembership m = aguardando();
    assertThat(ServicoDeAssinaturasNoCartao.aplicar(m, new SituacaoDaAssinatura(SituacaoDeAssinatura.ATIVA, 1, false),
        List.of(), List.of(), agora)).isTrue();
    assertThat(m.getStatus()).isEqualTo(ClientMembership.STATUS_ATIVA);
    assertThat(m.getPeriodStart()).isEqualTo(agora);
    assertThat(m.getPeriodEnd()).isEqualTo(agora.plus(30, ChronoUnit.DAYS));
    assertThat(m.getCobrancasPagasNaOperadora()).isEqualTo(1);
  }

  @Test
  void cartaoCadastradoSemCobrancaAindaNaoAtiva() {
    ClientMembership m = aguardando();
    assertThat(ServicoDeAssinaturasNoCartao.aplicar(m, new SituacaoDaAssinatura(SituacaoDeAssinatura.ATIVA, 0, false),
        List.of(), List.of(), agora)).isFalse();
    assertThat(m.getStatus()).isEqualTo(ClientMembership.STATUS_AGUARDANDO_CARTAO);
  }

  @Test
  void cadaCobrancaNovaRenovaUmaVezSo() {
    ClientMembership m = aguardando();
    var saldo = saldo(4, 3);
    var situacao = new SituacaoDaAssinatura(SituacaoDeAssinatura.ATIVA, 1, false);
    ServicoDeAssinaturasNoCartao.aplicar(m, situacao, List.of(saldo), List.of(beneficio(4)), agora);
    Instant fim = m.getPeriodEnd();
    // Aviso repetido: nada muda.
    assertThat(ServicoDeAssinaturasNoCartao.aplicar(m, situacao, List.of(saldo), List.of(beneficio(4)), agora)).isFalse();
    assertThat(m.getPeriodEnd()).isEqualTo(fim);
    // Segunda mensalidade: periodo seguinte e saldo usado zerado (plano nao cumulativo).
    ServicoDeAssinaturasNoCartao.aplicar(m, new SituacaoDaAssinatura(SituacaoDeAssinatura.ATIVA, 2, false),
        List.of(saldo), List.of(beneficio(4)), agora);
    assertThat(m.getPeriodStart()).isEqualTo(fim);
    assertThat(m.getPeriodEnd()).isEqualTo(fim.plus(30, ChronoUnit.DAYS));
    assertThat(saldo.getUsadasNoPeriodo()).isZero();
  }

  @Test
  void cumulativoSomaMaisUmaCota() {
    ClientMembership m = aguardando();
    m.setCumulativo(true);
    var saldo = saldo(4, 1);
    ServicoDeAssinaturasNoCartao.aplicar(m, new SituacaoDaAssinatura(SituacaoDeAssinatura.ATIVA, 2, false),
        List.of(saldo), List.of(beneficio(4)), agora);
    // A primeira ativa (saldo cheio); a segunda renova somando a cota.
    assertThat(saldo.getQuantidadeMensal()).isEqualTo(8);
    assertThat(saldo.getUsadasNoPeriodo()).isEqualTo(1);
  }

  @Test
  void atrasoViraInadimplenteECanceladaNaOperadoraCancelaAoFimDoPeriodo() {
    ClientMembership m = aguardando();
    ServicoDeAssinaturasNoCartao.aplicar(m, new SituacaoDaAssinatura(SituacaoDeAssinatura.ATIVA, 1, false), List.of(), List.of(), agora);
    assertThat(ServicoDeAssinaturasNoCartao.aplicar(m, new SituacaoDaAssinatura(SituacaoDeAssinatura.ATIVA, 1, true),
        List.of(), List.of(), agora)).isTrue();
    assertThat(m.getStatus()).isEqualTo(ClientMembership.STATUS_INADIMPLENTE);

    // Paga em atraso: volta a ativa.
    ServicoDeAssinaturasNoCartao.aplicar(m, new SituacaoDaAssinatura(SituacaoDeAssinatura.ATIVA, 2, false), List.of(), List.of(), agora);
    assertThat(m.getStatus()).isEqualTo(ClientMembership.STATUS_ATIVA);

    ServicoDeAssinaturasNoCartao.aplicar(m, new SituacaoDaAssinatura(SituacaoDeAssinatura.CANCELADA, 2, false), List.of(), List.of(), agora);
    assertThat(m.isCancelAtPeriodEnd()).isTrue();
    assertThat(m.getStatus()).isEqualTo(ClientMembership.STATUS_ATIVA);

    ClientMembership semCartao = aguardando();
    ServicoDeAssinaturasNoCartao.aplicar(semCartao, new SituacaoDaAssinatura(SituacaoDeAssinatura.CANCELADA, 0, false),
        List.of(), List.of(), agora);
    assertThat(semCartao.getStatus()).isEqualTo(ClientMembership.STATUS_CANCELADA);
  }

  @Test
  void canceladaNoAzzoNaoVolta() {
    ClientMembership m = aguardando();
    m.setStatus(ClientMembership.STATUS_CANCELADA);
    assertThat(ServicoDeAssinaturasNoCartao.aplicar(m, new SituacaoDaAssinatura(SituacaoDeAssinatura.ATIVA, 3, false),
        List.of(), List.of(), agora)).isFalse();
    assertThat(m.getStatus()).isEqualTo(ClientMembership.STATUS_CANCELADA);
  }
}
