package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentoDePontos;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentoDePontosRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;

/**
 * Os pontos de fidelidade da cliente (plano PLANO-FIDELIDADE-PACOTES.md, etapa 1).
 *
 * <p>Todo movimento passa por aqui: o saldo ({@code clients.loyalty_points}) muda numa instrucao so
 * no banco — o resgate so desconta se houver saldo, entao dois resgates ao mesmo tempo nao gastam os
 * mesmos pontos — e cada movimento fica em {@code movimentos_de_pontos}.
 *
 * <p>Creditos guardam quanto deles sobra e quando vencem (validade definida pelo dono do salao, no
 * momento do credito). O resgate consome primeiro o que vence antes.
 */
@Service
public class ServicoDePontos {

  /** O que fazer quando o estorno tira pontos que a cliente ja gastou. */
  public enum ModoDeEstorno {
    /** Tira o que der e para em zero (o salao perde o que ja foi gasto). */
    ATE_ZERO,
    /** Tira tudo; o saldo pode ficar negativo (o dono aceitou: a divida sai dos proximos pontos). */
    SALDO_NEGATIVO
  }

  /** Com que vencimento os pontos devolvidos voltam (V178, escolha de quem tem a permissao). */
  public enum VencimentoDaDevolucao {
    /** Voltam para os creditos de onde sairam, com o vencimento deles. */
    ORIGINAL,
    /** Voltam como credito novo, com a validade de hoje. */
    NOVO
  }

  /** Setter: os testes que montam o servico a mao devolvem sempre como credito novo. */
  private br.com.phdigitalcode.azzo.agenda.pro.repository.ConsumoDePontosRepository consumos;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setConsumos(br.com.phdigitalcode.azzo.agenda.pro.repository.ConsumoDePontosRepository consumos) {
    this.consumos = consumos;
  }

  private final ClienteRepository clientes;
  private final MovimentoDePontosRepository movimentos;
  private final AuthenticatedUser usuarioLogado;

  public ServicoDePontos(ClienteRepository clientes, MovimentoDePontosRepository movimentos, AuthenticatedUser usuarioLogado) {
    this.clientes = clientes;
    this.movimentos = movimentos;
    this.usuarioLogado = usuarioLogado;
  }

  // ---- valor dos pontos ---------------------------------------------------------------------------

  /** Quanto valem os pontos, em reais, sempre arredondado para baixo (nunca dar centavo que nao foi juntado). */
  public static BigDecimal valorEmReais(int pontos, BigDecimal pontosPorReal) {
    if (pontos <= 0 || pontosPorReal == null || pontosPorReal.signum() <= 0) return BigDecimal.ZERO.setScale(2);
    return BigDecimal.valueOf(pontos).divide(pontosPorReal, 2, RoundingMode.DOWN);
  }

  /** Menor total de pontos cujo valor passa de {@code valorAtual} (para dizer "resgate pelo menos N"). */
  public static int menorTotalQueValeMais(BigDecimal valorAtual, BigDecimal pontosPorReal) {
    BigDecimal proximo = valorAtual.add(new BigDecimal("0.01"));
    return proximo.multiply(pontosPorReal).setScale(0, RoundingMode.CEILING).intValueExact();
  }

  /** Maior total de pontos cujo valor nao passa de {@code valorMaximo}. */
  public static int maiorTotalAte(BigDecimal valorMaximo, BigDecimal pontosPorReal) {
    // valor(p) = floor(p / ppr, 2) <= v  <=>  p < (v + 0,01) * ppr
    BigDecimal limite = valorMaximo.add(new BigDecimal("0.01")).multiply(pontosPorReal);
    BigDecimal inteiro = limite.setScale(0, RoundingMode.CEILING).subtract(BigDecimal.ONE);
    return Math.max(0, inteiro.intValueExact());
  }

  public int saldo(UUID tenantId, UUID clientId) {
    Integer saldo = clientes.saldoDePontos(clientId, tenantId);
    return saldo == null ? 0 : saldo;
  }

  // ---- movimentos ---------------------------------------------------------------------------------

  /** Resgate numa comanda: so desconta se houver saldo. */
  @Transactional
  public void debitarResgate(UUID tenantId, UUID clientId, UUID comandaId, int pontos) {
    if (pontos <= 0) throw new IllegalArgumentException("Informe quantos pontos resgatar.");
    if (clientes.debitarPontosSeHouver(clientId, tenantId, pontos) == 0) {
      throw new IllegalArgumentException("Cliente não possui pontos suficientes (saldo: " + saldo(tenantId, clientId) + ").");
    }
    var usados = consumirCreditos(tenantId, clientId, pontos, null);
    MovimentoDePontos resgate = registrar(tenantId, clientId, comandaId, MovimentoDePontos.RESGATE, -pontos, 0, null, null);
    if (consumos == null) return;
    for (var usado : usados) {
      var c = new br.com.phdigitalcode.azzo.agenda.pro.entity.ConsumoDePontos();
      c.setTenantId(tenantId);
      c.setResgateId(resgate.getId());
      c.setCreditoId(usado.getKey());
      c.setComandaId(comandaId);
      c.setPontos(usado.getValue());
      consumos.save(c);
    }
  }

  /**
   * Pontos de um resgate voltam (resgate desfeito, comanda cancelada ou estornada). ORIGINAL: voltam
   * para os creditos de onde sairam, com o vencimento deles (o que venceu no meio do caminho vence
   * na proxima rotina). NOVO: voltam como credito novo, com a validade de hoje.
   */
  @Transactional
  public void devolverResgate(UUID tenantId, UUID clientId, UUID comandaId, int pontos, Integer validadeDias, String motivo,
      VencimentoDaDevolucao vencimento) {
    if (pontos <= 0) return;
    if (vencimento == VencimentoDaDevolucao.NOVO || consumos == null) {
      creditar(tenantId, clientId, comandaId, pontos, validadeDias, MovimentoDePontos.DEVOLUCAO,
          juntar(motivo, "vencimento novo"));
      return;
    }
    clientes.somarPontos(clientId, tenantId, pontos);
    int devolvidos = 0;
    for (var consumo : consumos.findByTenantIdAndComandaIdAndDevolvidoFalseOrderByCreatedAtDesc(tenantId, comandaId)) {
      if (devolvidos >= pontos) break;
      int volta = Math.min(consumo.getPontos(), pontos - devolvidos);
      var credito = movimentos.findById(consumo.getCreditoId()).orElse(null);
      if (credito == null) continue;
      credito.setPontosRestantes(credito.getPontosRestantes() + volta);
      movimentos.save(credito);
      if (volta == consumo.getPontos()) {
        consumo.setDevolvido(true);
      } else {
        consumo.setPontos(consumo.getPontos() - volta);
      }
      consumos.save(consumo);
      devolvidos += volta;
    }
    // Resgate de antes do historico (sem consumo guardado): o que sobra volta como credito novo.
    int sobra = pontos - devolvidos;
    Instant expira = sobra > 0 && validadeDias != null && validadeDias > 0
        ? Instant.now().plus(validadeDias, ChronoUnit.DAYS) : null;
    registrar(tenantId, clientId, comandaId, MovimentoDePontos.DEVOLUCAO, pontos, sobra, expira,
        juntar(motivo, "vencimento original"));
  }

  private static String juntar(String motivo, String extra) {
    return motivo == null || motivo.isBlank() ? extra : motivo + " (" + extra + ")";
  }

  /** Pontos ganhos com a venda (ao fechar a comanda). */
  @Transactional
  public void creditarVenda(UUID tenantId, UUID clientId, UUID comandaId, int pontos, Integer validadeDias) {
    creditar(tenantId, clientId, comandaId, pontos, validadeDias, MovimentoDePontos.CREDITO, null);
  }

  /**
   * Quantos dos pontos que a venda gerou a cliente ja usou (ou venceram). Venda anterior ao historico:
   * conta como usado o que falta no saldo.
   */
  public int pontosJaUsadosDaVenda(UUID tenantId, UUID clientId, UUID comandaId, int creditados) {
    if (creditados <= 0) return 0;
    return movimentos.findFirstByTenantIdAndComandaIdAndTipo(tenantId, comandaId, MovimentoDePontos.CREDITO)
        .map(credito -> Math.max(0, creditados - credito.getPontosRestantes()))
        .orElseGet(() -> Math.max(0, creditados - Math.max(0, saldo(tenantId, clientId))));
  }

  /** Estorno da venda: tira os pontos que ela gerou. */
  @Transactional
  public void estornarCreditoDaVenda(UUID tenantId, UUID clientId, UUID comandaId, int creditados, ModoDeEstorno modo) {
    if (creditados <= 0) return;
    int saiu;
    if (modo == ModoDeEstorno.SALDO_NEGATIVO) {
      clientes.somarPontos(clientId, tenantId, -creditados);
      saiu = creditados;
    } else {
      int antes = saldo(tenantId, clientId);
      clientes.tirarPontosAteZero(clientId, tenantId, creditados);
      saiu = antes - saldo(tenantId, clientId);
    }
    // Primeiro do credito desta venda; o resto, dos outros creditos.
    consumirCreditos(tenantId, clientId, saiu, comandaId);
    registrar(tenantId, clientId, comandaId, MovimentoDePontos.ESTORNO, -saiu, 0, null,
        saiu < creditados ? "Estorno: " + (creditados - saiu) + " pontos já tinham sido usados" : null);
  }

  // ---- expiracao (validade definida pelo dono do salao) ----------------------------------------------

  /** Setter: os testes que montam o servico a mao nao expiram nada. */
  private org.springframework.transaction.support.TransactionTemplate transacao;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setTransacoes(org.springframework.transaction.PlatformTransactionManager transacoes) {
    this.transacao = new org.springframework.transaction.support.TransactionTemplate(transacoes);
    this.transacao.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Vence os creditos com data passada: o que sobrou de cada um sai do saldo e o extrato ganha
   * "Venceu". Um credito por transacao, com o saldo da cliente travado — um resgate ao mesmo tempo
   * espera, e nada e descontado duas vezes.
   *
   * @return quantos creditos venceram
   */
  public int expirarVencidos(int limite) {
    if (transacao == null) return 0;
    Instant agora = Instant.now();
    int vencidos = 0;
    for (UUID id : movimentos.vencidos(agora, org.springframework.data.domain.Limit.of(limite))) {
      Boolean venceu = transacao.execute(s -> expirar(id, agora));
      if (Boolean.TRUE.equals(venceu)) vencidos++;
    }
    return vencidos;
  }

  private boolean expirar(UUID creditoId, Instant agora) {
    MovimentoDePontos credito = movimentos.findById(creditoId).orElse(null);
    if (credito == null || credito.getExpiraEm() == null || credito.getExpiraEm().isAfter(agora)) return false;
    clientes.travarSaldo(credito.getClientId(), credito.getTenantId());
    Integer restantes = movimentos.restantesDe(creditoId);
    if (restantes == null || restantes <= 0) return false;
    int antes = saldo(credito.getTenantId(), credito.getClientId());
    clientes.tirarPontosAteZero(credito.getClientId(), credito.getTenantId(), restantes);
    int saiu = antes - saldo(credito.getTenantId(), credito.getClientId());
    movimentos.zerarRestantes(creditoId);
    String data = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
        .withZone(java.time.ZoneId.of("America/Sao_Paulo")).format(credito.getCreatedAt());
    registrar(credito.getTenantId(), credito.getClientId(), credito.getComandaId(), MovimentoDePontos.EXPIRACAO,
        -saiu, 0, null, "Venceram os pontos ganhos em " + data);
    return true;
  }

  // ---- apoio --------------------------------------------------------------------------------------

  private void creditar(UUID tenantId, UUID clientId, UUID comandaId, int pontos, Integer validadeDias, String tipo, String motivo) {
    if (pontos <= 0) return;
    clientes.somarPontos(clientId, tenantId, pontos);
    // Com saldo negativo (estorno aceito pelo dono), os pontos novos primeiro pagam a divida.
    int saldoNovo = saldo(tenantId, clientId);
    int restantes = Math.max(0, Math.min(pontos, saldoNovo));
    Instant expira = validadeDias == null || validadeDias <= 0 ? null : Instant.now().plus(validadeDias, ChronoUnit.DAYS);
    registrar(tenantId, clientId, comandaId, tipo, pontos, restantes, expira, motivo);
  }

  /**
   * Baixa {@code pontos} dos creditos com saldo; com {@code primeiroDaComanda}, comeca pelo credito
   * dessa venda. Devolve de qual credito saiu quanto (para a devolucao com o vencimento original).
   */
  private java.util.List<java.util.Map.Entry<UUID, Integer>> consumirCreditos(UUID tenantId, UUID clientId, int pontos,
      UUID primeiroDaComanda) {
    java.util.List<java.util.Map.Entry<UUID, Integer>> usados = new java.util.ArrayList<>();
    int falta = pontos;
    if (primeiroDaComanda != null) {
      var daVenda = movimentos.findFirstByTenantIdAndComandaIdAndTipo(tenantId, primeiroDaComanda, MovimentoDePontos.CREDITO);
      if (daVenda.isPresent()) falta = baixar(daVenda.get(), falta, usados);
    }
    for (MovimentoDePontos credito : movimentos.creditosComSaldo(tenantId, clientId)) {
      if (falta <= 0) break;
      falta = baixar(credito, falta, usados);
    }
    return usados;
  }

  private int baixar(MovimentoDePontos credito, int falta, java.util.List<java.util.Map.Entry<UUID, Integer>> usados) {
    int usar = Math.min(falta, credito.getPontosRestantes());
    if (usar <= 0) return falta;
    credito.setPontosRestantes(credito.getPontosRestantes() - usar);
    movimentos.save(credito);
    usados.add(java.util.Map.entry(credito.getId(), usar));
    return falta - usar;
  }

  private MovimentoDePontos registrar(UUID tenantId, UUID clientId, UUID comandaId, String tipo, int pontos, int restantes,
      Instant expira, String motivo) {
    MovimentoDePontos m = new MovimentoDePontos();
    m.setTenantId(tenantId);
    m.setClientId(clientId);
    m.setComandaId(comandaId);
    m.setTipo(tipo);
    m.setPontos(pontos);
    m.setSaldoApos(saldo(tenantId, clientId));
    m.setPontosRestantes(restantes);
    m.setExpiraEm(expira);
    m.setMotivo(motivo == null ? null : motivo.length() > 200 ? motivo.substring(0, 200) : motivo);
    m.setCreatedBy(usuarioLogado == null ? null : usuarioLogado.idOuNulo());
    return movimentos.save(m);
  }
}
