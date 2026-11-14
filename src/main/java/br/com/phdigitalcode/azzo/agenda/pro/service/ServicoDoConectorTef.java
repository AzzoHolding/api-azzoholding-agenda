package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ConectorTefDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cobranca;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ContaDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TerminalDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Status;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.StatusNaOperadora;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.tef.TefProvedor;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CobrancaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ContaDePagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TerminalDePagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * O lado do Azzo do Azzo Conector (TEF no balcao, Fase 6).
 *
 * <ol>
 *   <li><b>Pareamento:</b> o dono gera um codigo (8 caracteres, 15 minutos, uso unico) para uma
 *       conta TEF; o Conector troca o codigo por um token proprio. Do codigo e do token so o hash
 *       fica no banco; o token aparece uma vez, no Conector.
 *   <li><b>Fila:</b> o Conector pergunta pela proxima venda do terminal (espera ate ~20 s). A venda
 *       entregue e marcada, para nao ir duas vezes.
 *   <li><b>Resultado:</b> o Conector manda aprovado/negado, NSU, bandeira e comprovantes. O Azzo
 *       responde {@code confirmar}: so com {@code true} o Conector confirma a transacao TEF. Se o
 *       caixa cancelou a cobranca enquanto a cliente passava o cartao, a resposta e {@code false} e
 *       o Conector DESFAZ — o dinheiro nao fica preso numa venda que o Azzo nao reconhece.
 *   <li><b>Cancelamento no pinpad:</b> feito pelo operador no Conector; ele avisa e a cobranca vira
 *       estornada (a comanda pode entao ser estornada no Azzo).
 * </ol>
 */
@Service
public class ServicoDoConectorTef {

  /** Piloto dos pagamentos (14/11). Setter: os testes que montam este servico a mao seguem liberados. */
  private LiberacaoDePagamentos liberacao;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setLiberacao(LiberacaoDePagamentos liberacao) {
    this.liberacao = liberacao;
  }

  private void exigirLiberado(UUID tenantId) {
    if (liberacao != null) liberacao.exigir(tenantId);
  }

  private static final Logger LOG = LoggerFactory.getLogger(ServicoDoConectorTef.class);
  private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final Duration PRAZO_DO_CODIGO = Duration.ofMinutes(15);
  /** Venda TEF sem resultado depois disto expira (o caixa tenta de novo). */
  static final Duration PRAZO_DA_VENDA = Duration.ofMinutes(10);

  private final SecureRandom aleatorio = new SecureRandom();
  private final TerminalDePagamentoRepository terminais;
  private final ContaDePagamentoRepository contas;
  private final CobrancaRepository cobrancas;
  private final ServicoDeContasDePagamento servicoDeContas;
  private final ServicoDePagamentos pagamentos;
  private final ContextoTenant contextoTenant;
  private final AuditService auditService;
  private final TransactionTemplate transacao;

  public ServicoDoConectorTef(
      TerminalDePagamentoRepository terminais,
      ContaDePagamentoRepository contas,
      CobrancaRepository cobrancas,
      ServicoDeContasDePagamento servicoDeContas,
      ServicoDePagamentos pagamentos,
      ContextoTenant contextoTenant,
      AuditService auditService,
      PlatformTransactionManager gerenciador) {
    this.terminais = terminais;
    this.contas = contas;
    this.cobrancas = cobrancas;
    this.servicoDeContas = servicoDeContas;
    this.pagamentos = pagamentos;
    this.contextoTenant = contextoTenant;
    this.auditService = auditService;
    this.transacao = new TransactionTemplate(gerenciador);
  }

  // ---- lado do dono ----------------------------------------------------------------------------

  /** Cria o terminal TEF_LOCAL e devolve o codigo de pareamento (unica vez que ele aparece). */
  public ConectorTefDtos.CodigoDePareamento gerarPareamento(UUID contaId, String apelido) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    exigirLiberado(tenantId);
    ContaDePagamento conta = contas.findById(contaId).filter(c -> c.getTenantId().equals(tenantId) && c.isAtivo())
        .orElseThrow(() -> new ApiClientErrorException("Conta de pagamento não encontrada.", 404));
    if (!TefProvedor.CODIGO.equals(conta.getProvedor())) {
      throw new ApiClientErrorException("O Azzo Conector é só para a conta de TEF no balcão.", 400);
    }
    String codigo = codigo();
    TerminalDePagamento t = transacao.execute(s -> {
      TerminalDePagamento novo = new TerminalDePagamento();
      novo.setTenantId(tenantId);
      novo.setContaId(conta.getId());
      novo.setTipo("TEF_LOCAL");
      novo.setSerial("CONECTOR-" + codigo().substring(0, 6));
      novo.setApelido(apelido == null || apelido.isBlank() ? "Caixa" : apelido.trim().substring(0, Math.min(60, apelido.trim().length())));
      novo.setCodigoPareamentoHash(hash(normalizar(codigo)));
      novo.setCodigoExpiraEm(Instant.now().plus(PRAZO_DO_CODIGO));
      return terminais.save(novo);
    });
    auditar(tenantId, "PAGAMENTO_CONECTOR_CODIGO_GERADO", t);
    ConectorTefDtos.CodigoDePareamento r = new ConectorTefDtos.CodigoDePareamento();
    r.terminalId = t.getId().toString();
    r.apelido = t.getApelido();
    r.codigo = codigo.substring(0, 4) + "-" + codigo.substring(4);
    r.expiraEm = t.getCodigoExpiraEm().toString();
    return r;
  }

  // ---- lado do Conector (sem login de usuario; autentica pelo token) ---------------------------

  /** Troca o codigo pelo token. Codigo errado, vencido ou ja usado: mesma resposta, sem dica. */
  public ConectorTefDtos.Pareado parear(ConectorTefDtos.PedidoDePareamento pedido) {
    if (pedido == null || pedido.codigo == null || pedido.codigo.isBlank()) {
      throw new ApiClientErrorException("Informe o código de pareamento.", 400);
    }
    String token = HexFormat.of().formatHex(bytes(32));
    TerminalDePagamento t = transacao.execute(s -> {
      TerminalDePagamento achado = terminais.findByCodigoPareamentoHashAndAtivoTrue(hash(normalizar(pedido.codigo)))
          .filter(x -> x.getCodigoExpiraEm() != null && x.getCodigoExpiraEm().isAfter(Instant.now()))
          .orElseThrow(() -> new ApiClientErrorException("Código inválido ou vencido. Gere outro no Azzo.", 401));
      achado.setCodigoPareamentoHash(null);
      achado.setCodigoExpiraEm(null);
      achado.setTokenHash(hash(token));
      achado.setPareadoEm(Instant.now());
      achado.setUltimoContatoEm(Instant.now());
      achado.setNomeDoComputador(limitar(pedido.nomeDoComputador, 80));
      return terminais.save(achado);
    });
    auditar(t.getTenantId(), "PAGAMENTO_CONECTOR_PAREADO", t);
    ConectorTefDtos.Pareado r = new ConectorTefDtos.Pareado();
    r.token = token;
    r.configuracao = configuracao(t);
    return r;
  }

  /** O terminal dono do token (e marca o contato). Token desconhecido: 401. */
  public TerminalDePagamento autenticar(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() < 20) {
      throw new ApiClientErrorException("Conector não autenticado.", 401);
    }
    TerminalDePagamento t = terminais.findByTokenHashAndAtivoTrue(hash(authorization.substring(7).trim()))
        .orElseThrow(() -> new ApiClientErrorException("Conector não autenticado. Pareie de novo.", 401));
    if (t.getUltimoContatoEm() == null || t.getUltimoContatoEm().isBefore(Instant.now().minusSeconds(30))) {
      transacao.executeWithoutResult(s -> terminais.findById(t.getId()).ifPresent(x -> {
        x.setUltimoContatoEm(Instant.now());
        terminais.save(x);
      }));
    }
    return t;
  }

  public ConectorTefDtos.Configuracao configuracao(TerminalDePagamento t) {
    ContaDePagamento conta = contas.findById(t.getContaId()).orElseThrow();
    ProvedorDePagamento.Conta aberta = servicoDeContas.abrir(conta);
    ConectorTefDtos.Configuracao c = new ConectorTefDtos.Configuracao();
    c.terminalId = t.getId().toString();
    c.apelido = t.getApelido();
    c.sistema = TefProvedor.sistema(aberta);
    Map<String, String> parametros = new LinkedHashMap<>();
    for (String chave : List.of("servidorSitef", "empresaSitef", "pdcPaygo")) {
      String v = aberta.credencial(chave);
      if (v != null && !v.isBlank()) parametros.put(chave, v.trim());
    }
    c.parametros = parametros;
    c.ambiente = conta.getAmbiente();
    return c;
  }

  /**
   * A proxima venda da fila, esperando ate {@code esperaMaxima} por uma (o Conector pergunta de
   * novo logo depois). {@code null} se nao chegou nenhuma.
   */
  public ConectorTefDtos.Pedido proximoPedido(TerminalDePagamento t, Duration esperaMaxima) {
    Instant ate = Instant.now().plus(esperaMaxima);
    while (true) {
      ConectorTefDtos.Pedido pedido = transacao.execute(s -> {
        List<Cobranca> fila = cobrancas.filaDoConector(t.getId(), Limit.of(1));
        if (fila.isEmpty()) return null;
        Cobranca c = fila.get(0);
        c.setConectorPegouEm(Instant.now());
        cobrancas.save(c);
        return paraPedido(c);
      });
      if (pedido != null || !Instant.now().isBefore(ate)) return pedido;
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
  }

  /** Situacao da venda: o Conector pergunta enquanto espera o cartao, para abortar se o caixa cancelou. */
  public ConectorTefDtos.Situacao situacao(TerminalDePagamento t, UUID cobrancaId) {
    Cobranca c = daFila(t, cobrancaId);
    ConectorTefDtos.Situacao s = new ConectorTefDtos.Situacao();
    s.status = c.getStatus();
    s.cancelada = "CANCELADA".equals(c.getStatus()) || "EXPIRADA".equals(c.getStatus());
    return s;
  }

  /**
   * O resultado do pinpad. A resposta diz se o Conector CONFIRMA ({@code true}) ou DESFAZ
   * ({@code false}) a transacao TEF. Idempotente: o mesmo resultado de novo devolve a mesma resposta.
   */
  public ConectorTefDtos.RespostaAoResultado resultado(TerminalDePagamento t, UUID cobrancaId, ConectorTefDtos.Resultado r) {
    if (r == null) throw new ApiClientErrorException("Resultado vazio.", 400);
    Cobranca c = daFila(t, cobrancaId);
    ConectorTefDtos.RespostaAoResultado resposta = new ConectorTefDtos.RespostaAoResultado();
    if (r.aprovado) {
      if ("PAGA".equals(c.getStatus())) {
        resposta.confirmar = true; // repetido
        return resposta;
      }
      if (c.encerrada()) {
        LOG.warn("tef_aprovado_em_cobranca_encerrada cobranca_id={} status={} — Conector vai desfazer", c.getId(), c.getStatus());
        resposta.confirmar = false;
        resposta.motivo = "A cobrança foi cancelada no caixa: a transação será desfeita.";
        return resposta;
      }
      guardarComprovantes(c.getId(), r);
      Cobranca depois = pagamentos.aplicar(c.getId(), new StatusNaOperadora(Status.PAGA, c.getValor(), null, null, null,
          limitar(r.nsu, 40), limitar(r.autorizacao, 40), limitar(r.bandeira, 40), r.cartaoFinal4, null, null),
          "CONECTOR", "TEF_APROVADA", "tef:" + c.getId() + ":aprovada", null);
      resposta.confirmar = "PAGA".equals(depois.getStatus());
      if (!resposta.confirmar) resposta.motivo = "A cobrança não estava mais esperando: a transação será desfeita.";
      return resposta;
    }
    pagamentos.aplicar(c.getId(), new StatusNaOperadora(Status.RECUSADA, null, null, null, null, null, null, null, null, null,
        r.motivo == null || r.motivo.isBlank() ? "Negada no pinpad" : r.motivo),
        "CONECTOR", "TEF_NEGADA", "tef:" + c.getId() + ":negada", null);
    resposta.confirmar = false;
    return resposta;
  }

  /** O operador cancelou a venda no pinpad, pelo Conector: a cobranca vira estornada. */
  public void canceladaNoPinpad(TerminalDePagamento t, ConectorTefDtos.CancelamentoNoPinpad pedido) {
    if (pedido == null || ((pedido.cobrancaId == null || pedido.cobrancaId.isBlank()) && (pedido.nsu == null || pedido.nsu.isBlank()))) {
      throw new ApiClientErrorException("Informe a venda (cobrança ou NSU).", 400);
    }
    Cobranca c = pedido.cobrancaId != null && !pedido.cobrancaId.isBlank()
        ? daFila(t, UUID.fromString(pedido.cobrancaId))
        : cobrancas.findFirstByTerminalIdAndNsuOrderByCreatedAtDesc(t.getId(), pedido.nsu.trim())
            .orElseThrow(() -> new ApiClientErrorException("Venda não encontrada neste caixa.", 404));
    if (!"PAGA".equals(c.getStatus()) && !"ESTORNO_PARCIAL".equals(c.getStatus())) {
      if ("ESTORNADA".equals(c.getStatus())) return;
      throw new ApiClientErrorException("Esta venda não está paga no Azzo.", 409);
    }
    pagamentos.aplicar(c.getId(), new StatusNaOperadora(Status.ESTORNADA, null, null, null, null, null, null, null, null,
        c.getValor(), null), "CONECTOR", "TEF_CANCELADA_NO_PINPAD", "tef:" + c.getId() + ":cancelada-no-pinpad", null);
    auditarCobranca(c.getTenantId(), "PAGAMENTO_ESTORNADO", c);
  }

  /** Venda TEF sem resultado ha mais de 10 minutos expira (o caixa pode tentar de novo). */
  public int expirarSemResposta() {
    int n = 0;
    for (Cobranca c : cobrancas.tefSemResposta(Instant.now().minus(PRAZO_DA_VENDA), Limit.of(50))) {
      pagamentos.aplicar(c.getId(), StatusNaOperadora.so(Status.EXPIRADA), "CONSULTA", "TEF_SEM_RESPOSTA",
          "tef:" + c.getId() + ":expirada", null);
      n++;
    }
    return n;
  }

  // ---- apoio -------------------------------------------------------------------------------------

  private Cobranca daFila(TerminalDePagamento t, UUID cobrancaId) {
    return cobrancas.findById(cobrancaId).filter(c -> t.getId().equals(c.getTerminalId()))
        .orElseThrow(() -> new ApiClientErrorException("Venda não encontrada neste caixa.", 404));
  }

  private void guardarComprovantes(UUID cobrancaId, ConectorTefDtos.Resultado r) {
    transacao.executeWithoutResult(s -> cobrancas.findById(cobrancaId).ifPresent(c -> {
      c.setComprovanteCliente(limitar(r.comprovanteCliente, 4000));
      c.setComprovanteLoja(limitar(r.comprovanteLoja, 4000));
      cobrancas.save(c);
    }));
  }

  static ConectorTefDtos.Pedido paraPedido(Cobranca c) {
    ConectorTefDtos.Pedido p = new ConectorTefDtos.Pedido();
    p.cobrancaId = c.getId().toString();
    p.tipo = "VENDA";
    p.meio = c.getMeio();
    p.parcelas = c.getParcelas();
    p.valor = c.getValor();
    p.valorEmCentavos = c.getValor().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    p.criadaEm = c.getCreatedAt() == null ? null : c.getCreatedAt().toString();
    return p;
  }

  private String codigo() {
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < 8; i++) sb.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
    return sb.toString();
  }

  static String normalizar(String codigo) {
    return codigo.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
  }

  private byte[] bytes(int n) {
    byte[] b = new byte[n];
    aleatorio.nextBytes(b);
    return b;
  }

  static String hash(String texto) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void auditar(UUID tenantId, String acao, TerminalDePagamento t) {
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.module = AuditConstants.Module.SETTINGS;
    command.action = acao;
    command.entityType = "PAYMENT_TERMINAL";
    command.entityId = t.getId().toString();
    command.sourceChannel = AuditConstants.SourceChannel.API;
    Map<String, Object> depois = new LinkedHashMap<>();
    depois.put("apelido", t.getApelido());
    depois.put("computador", t.getNomeDoComputador());
    command.after = depois;
    auditService.recordSuccess(command);
  }

  private void auditarCobranca(UUID tenantId, String acao, Cobranca c) {
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.module = AuditConstants.Module.SETTINGS;
    command.action = acao;
    command.entityType = "PAYMENT_CHARGE";
    command.entityId = c.getId().toString();
    command.sourceChannel = AuditConstants.SourceChannel.API;
    command.after = Map.of("valor", c.getValor() == null ? BigDecimal.ZERO : c.getValor(), "origem", "Cancelada no pinpad (Azzo Conector)");
    auditService.recordSuccess(command);
  }

  private static String limitar(String valor, int maximo) {
    if (valor == null) return null;
    String v = valor.trim();
    return v.length() <= maximo ? v : v.substring(0, maximo);
  }
}
