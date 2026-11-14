package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PagamentosDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cobranca;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ContaDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EventoDeCobranca;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.pixchave.PixNaChaveProvedor;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.AvisoDaOperadora;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Capacidade;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.CobrancaCriada;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Pagador;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.PedidoDeCobranca;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.StatusNaOperadora;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CobrancaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ContaDePagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EventoDeCobrancaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * As cobrancas do salao em qualquer operadora (Pagamentos — Fase 0, V167).
 *
 * <ul>
 *   <li><b>Criar</b>: escolhe a conta (a pedida, ou a padrao do caso de uso), confere se a
 *       operadora faz aquilo, grava a cobranca ANTES de chamar a operadora (a chave de
 *       idempotencia impede cobrar duas vezes no clique duplo) e guarda o que ela devolveu.
 *   <li><b>Aviso</b> (webhook): o token da URL acha a conta; a operadora confere a assinatura e
 *       traduz; o evento e gravado uma vez so (idempotencia) e a cobranca muda de status.
 *   <li><b>Consulta de reserva</b>: o webhook pode se perder. Cobranca esperando ha mais de 20 s e
 *       menos de 2 h e consultada na operadora a cada rodada.
 * </ul>
 *
 * <p>Quem depende do resultado (comanda, sinal, clube) ouve {@link CobrancaMudouDeStatus}. Nesta
 * fase ninguem ouve ainda: os fluxos antigos do Asaas continuam como estavam.
 */
@Service
public class ServicoDePagamentos {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoDePagamentos.class);

  /** Publicado quando a cobranca muda de status — a comanda e o sinal reagem a isto (Fase 1). */
  public record CobrancaMudouDeStatus(
      UUID tenantId, UUID cobrancaId, String origemTipo, UUID origemId, String statusAnterior, String statusNovo) {}

  /** O pedido interno: quem chama (PDV, agendamento, teste) monta isto. */
  public record NovaCobranca(
      String origemTipo,
      UUID origemId,
      String meio,
      BigDecimal valor,
      int parcelas,
      String descricao,
      Pagador pagador,
      UUID contaId,
      UUID terminalId,
      String terminalSerial,
      Instant expiraEm,
      String idempotencyKey) {}

  private final CobrancaRepository cobrancas;
  private final ContaDePagamentoRepository contas;
  private final EventoDeCobrancaRepository eventos;
  private final ServicoDeContasDePagamento servicoDeContas;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser usuarioLogado;
  private final AuditService auditService;
  private final ApplicationEventPublisher publicador;
  private final TransactionTemplate transacao;
  private ServicoDeTaxasDePagamento taxasDePagamento;
  private br.com.phdigitalcode.azzo.agenda.pro.security.PermissionService permissoes;

  /** Setter (e nao construtor), pelo mesmo motivo do servico de taxas. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setPermissoes(br.com.phdigitalcode.azzo.agenda.pro.security.PermissionService permissoes) {
    this.permissoes = permissoes;
  }

  /** Setter (e nao construtor): o servico de taxas e opcional nos testes que montam este a mao. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setTaxasDePagamento(ServicoDeTaxasDePagamento taxasDePagamento) {
    this.taxasDePagamento = taxasDePagamento;
  }

  public ServicoDePagamentos(
      CobrancaRepository cobrancas,
      ContaDePagamentoRepository contas,
      EventoDeCobrancaRepository eventos,
      ServicoDeContasDePagamento servicoDeContas,
      ContextoTenant contextoTenant,
      AuthenticatedUser usuarioLogado,
      AuditService auditService,
      ApplicationEventPublisher publicador,
      PlatformTransactionManager transactionManager) {
    this.cobrancas = cobrancas;
    this.contas = contas;
    this.eventos = eventos;
    this.servicoDeContas = servicoDeContas;
    this.contextoTenant = contextoTenant;
    this.usuarioLogado = usuarioLogado;
    this.auditService = auditService;
    this.publicador = publicador;
    this.transacao = new TransactionTemplate(transactionManager);
    this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  // ---- criar ---------------------------------------------------------------------------------

  /**
   * Cria a cobranca na operadora. A linha e gravada (CRIADA) numa transacao propria ANTES da
   * chamada: se a operadora cobrar e a nossa transacao cair depois, o id ainda fica guardado.
   */
  public PagamentosDtos.Cobranca criar(UUID tenantId, NovaCobranca pedido) {
    validar(pedido);
    Optional<Cobranca> repetida = cobrancas.findByTenantIdAndIdempotencyKey(tenantId, pedido.idempotencyKey());
    if (repetida.isPresent()) return paraDto(repetida.get());

    ContaDePagamento conta = conta(tenantId, pedido);
    ProvedorDePagamento provedor = servicoDeContas.provedor(conta.getProvedor());
    exigirCapacidade(provedor, pedido);

    UUID usuario = usuarioLogado.idOuNulo();
    Cobranca cobranca = transacao.execute(s -> {
      Cobranca c = new Cobranca();
      c.setTenantId(tenantId);
      c.setContaId(conta.getId());
      c.setTerminalId(pedido.terminalId());
      c.setProvedor(conta.getProvedor());
      c.setOrigemTipo(pedido.origemTipo());
      c.setOrigemId(pedido.origemId());
      c.setMeio(pedido.meio());
      c.setParcelas(Math.max(1, pedido.parcelas()));
      c.setValor(pedido.valor());
      c.setIdempotencyKey(pedido.idempotencyKey());
      c.setCriadaPor(usuario);
      return cobrancas.save(c);
    });

    try {
      CobrancaCriada criada = provedor.criarCobranca(servicoDeContas.abrir(conta), new PedidoDeCobranca(
          cobranca.getId().toString(), pedido.meio(), pedido.valor(), Math.max(1, pedido.parcelas()),
          pedido.descricao(), pedido.pagador(), pedido.terminalSerial(), pedido.expiraEm()));
      Cobranca salva = transacao.execute(s -> {
        Cobranca c = cobrancas.findById(cobranca.getId()).orElseThrow();
        c.setIdNaOperadora(criada.idNaOperadora());
        c.setStatus(criada.status() == null ? "AGUARDANDO" : criada.status().name());
        c.setPixCopiaECola(criada.pixCopiaECola());
        c.setLinkDePagamento(criada.linkDePagamento());
        c.setExpiraEm(criada.expiraEm());
        return cobrancas.save(c);
      });
      auditar(tenantId, "PAGAMENTO_COBRANCA_CRIADA", salva);
      return paraDto(salva);
    } catch (ProvedorDePagamento.FalhaNaOperadora | ProvedorDePagamento.NaoSuportado e) {
      transacao.executeWithoutResult(s -> cobrancas.findById(cobranca.getId()).ifPresent(c -> {
        c.setStatus("RECUSADA");
        c.setMotivoRecusa(limitar(e.getMessage(), 300));
        cobrancas.save(c);
      }));
      throw new ApiClientErrorException(e.getMessage(), 502);
    }
  }

  /** Cobranca de teste (R$ 1,00) para o dono ver a conta funcionando de ponta a ponta. */
  public PagamentosDtos.Cobranca criarTeste(PagamentosDtos.CobrancaDeTesteRequest pedido) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    if (pedido == null || pedido.contaId == null) throw new ApiClientErrorException("Escolha a conta.", 400);
    String meio = pedido.meio == null ? "PIX" : pedido.meio.trim().toUpperCase();
    Pagador pagador = new Pagador(null, pedido.pagadorNome == null ? "Teste Azzo" : pedido.pagadorNome, null, null,
        pedido.pagadorDocumento);
    return criar(tenantId, new NovaCobranca("TESTE", null, meio, BigDecimal.ONE, 1, "Teste de integração Azzo",
        pagador, UUID.fromString(pedido.contaId), null, null, Instant.now().plus(Duration.ofDays(1)),
        "teste:" + UUID.randomUUID()));
  }

  @Transactional(readOnly = true)
  public PagamentosDtos.Cobranca obter(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return paraDto(cobrancas.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Cobrança não encontrada.", 404)));
  }

  // ---- cancelar e estornar -------------------------------------------------------------------

  public PagamentosDtos.Cobranca cancelar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cobranca cobranca = cobrancas.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Cobrança não encontrada.", 404));
    if (cobranca.encerrada()) throw new ApiClientErrorException("Esta cobrança já foi encerrada.", 409);
    ContaDePagamento conta = contas.findById(cobranca.getContaId()).orElseThrow();
    try {
      servicoDeContas.provedor(conta.getProvedor()).cancelar(servicoDeContas.abrir(conta), cobranca.getIdNaOperadora());
    } catch (ProvedorDePagamento.FalhaNaOperadora | ProvedorDePagamento.NaoSuportado e) {
      throw new ApiClientErrorException(e.getMessage(), 502);
    }
    return paraDto(aplicar(cobranca.getId(), StatusNaOperadora.so(ProvedorDePagamento.Status.CANCELADA), "MANUAL", "CANCELAR",
        "cancelar:" + cobranca.getId(), null));
  }

  /**
   * Pix na chave do salao: o caixa viu o dinheiro no aplicativo do banco e confirma. Sem operadora,
   * e a unica forma de a cobranca ficar paga — por isso so vale para essa conta e fica na auditoria
   * com quem confirmou.
   */
  public PagamentosDtos.Cobranca confirmarRecebimento(UUID id) {
    if (!podeConfirmarPix()) {
      throw new ApiClientErrorException(
          "Seu perfil de acesso não permite confirmar Pix recebido. Peça ao dono do salão.", 403);
    }
    return confirmarRecebimento(contextoTenant.obterTenantIdOuFalhar(), id);
  }

  /** Opcao do perfil de acesso (V173), escolhida pelo dono; o dono confirma sempre. */
  public static final String CONFIRMAR_PIX = "pos:pix-confirm";

  private boolean podeConfirmarPix() {
    if (usuarioLogado.temRole("OWNER")) return true;
    return permissoes != null && permissoes.possuiPermissao(CONFIRMAR_PIX);
  }

  PagamentosDtos.Cobranca confirmarRecebimento(UUID tenantId, UUID id) {
    Cobranca cobranca = cobrancas.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Cobrança não encontrada.", 404));
    if (!PixNaChaveProvedor.CODIGO.equals(cobranca.getProvedor())) {
      throw new ApiClientErrorException("Esta cobrança é confirmada pela operadora, não pelo caixa.", 409);
    }
    if ("PAGA".equals(cobranca.getStatus())) return paraDto(cobranca);
    if (!"CRIADA".equals(cobranca.getStatus()) && !"AGUARDANDO".equals(cobranca.getStatus())) {
      throw new ApiClientErrorException("Esta cobrança já foi encerrada.", 409);
    }
    Cobranca paga = aplicar(cobranca.getId(), StatusNaOperadora.so(ProvedorDePagamento.Status.PAGA), "MANUAL",
        "CONFIRMADO_PELO_CAIXA", "confirmar:" + cobranca.getId(), null);
    auditar(tenantId, "PAGAMENTO_PIX_CONFIRMADO_PELO_CAIXA", paga);
    return paraDto(paga);
  }

  /** Estorno total (valor nulo) ou parcial, na operadora. So cobranca PAGA (ou ja parcialmente estornada). */
  public PagamentosDtos.Cobranca estornar(UUID id, BigDecimal valor) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cobranca cobranca = cobrancas.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Cobrança não encontrada.", 404));
    if (!"PAGA".equals(cobranca.getStatus()) && !"ESTORNO_PARCIAL".equals(cobranca.getStatus())) {
      throw new ApiClientErrorException("Só dá para estornar cobrança paga.", 409);
    }
    BigDecimal restante = cobranca.getValor().subtract(cobranca.getValorEstornado());
    if (valor != null && (valor.signum() <= 0 || valor.compareTo(restante) > 0)) {
      throw new ApiClientErrorException("O estorno precisa ficar entre R$ 0,01 e o que falta estornar.", 400);
    }
    ContaDePagamento conta = contas.findById(cobranca.getContaId()).orElseThrow();
    ProvedorDePagamento provedorDaCobranca = servicoDeContas.provedor(conta.getProvedor());
    boolean parcial = valor != null && valor.compareTo(restante) < 0;
    if (parcial && !provedorDaCobranca.capacidades().contains(Capacidade.ESTORNO_PARCIAL)) {
      throw new ApiClientErrorException(provedorDaCobranca.nome() + " só estorna o valor inteiro pelo Azzo.", 400);
    }
    if (!parcial) valor = null;
    StatusNaOperadora resultado;
    try {
      resultado = servicoDeContas.provedor(conta.getProvedor())
          .estornar(servicoDeContas.abrir(conta), cobranca.getIdNaOperadora(), valor);
    } catch (ProvedorDePagamento.FalhaNaOperadora | ProvedorDePagamento.NaoSuportado e) {
      throw new ApiClientErrorException(e.getMessage(), 502);
    }
    BigDecimal estornado = cobranca.getValorEstornado().add(valor == null ? restante : valor);
    ProvedorDePagamento.Status novo = estornado.compareTo(cobranca.getValor()) >= 0
        ? ProvedorDePagamento.Status.ESTORNADA : ProvedorDePagamento.Status.ESTORNO_PARCIAL;
    StatusNaOperadora status = new StatusNaOperadora(novo, null, null, null, null, null, null, null, null, estornado,
        resultado == null ? null : resultado.motivo());
    Cobranca atualizada = aplicar(cobranca.getId(), status, "MANUAL", "ESTORNAR", "estornar:" + cobranca.getId() + ":" + estornado, null);
    auditar(tenantId, "PAGAMENTO_ESTORNADO", atualizada);
    return paraDto(atualizada);
  }

  // ---- aviso da operadora (webhook) ------------------------------------------------------------

  /**
   * O aviso de uma operadora. O token acha a conta, e a operadora da URL tem de ser a da conta.
   * Aviso repetido (mesmo id de evento) e ignorado. Aviso de cobranca que o Azzo nao criou e
   * guardado e ignorado.
   */
  public void receberAviso(String provedorDaUrl, String token, Map<String, String> cabecalhos, String corpo) {
    ContaDePagamento conta = contas.findByWebhookTokenAndAtivoTrue(token)
        .filter(c -> c.getProvedor().equalsIgnoreCase(provedorDaUrl.replace('-', '_')))
        .orElseThrow(() -> new ApiClientErrorException("Aviso não reconhecido.", 404));
    ProvedorDePagamento provedor = servicoDeContas.provedor(conta.getProvedor());
    AvisoDaOperadora aviso;
    try {
      aviso = provedor.lerAviso(servicoDeContas.abrir(conta), cabecalhos, corpo);
    } catch (ProvedorDePagamento.AvisoInvalido e) {
      LOG.warn("pagamentos_aviso_invalido provedor={} conta_id={} motivo={}", conta.getProvedor(), conta.getId(), e.getMessage());
      throw new ApiClientErrorException("Aviso inválido.", 401);
    }
    String chave = conta.getProvedor() + ":" + aviso.idDoEvento();
    if (eventos.existsByIdempotencyKey(chave)) return;

    // Fase 7: aviso de assinatura no cartao (nao de cobranca) — o clube reconsulta e aplica.
    String assinatura = aviso.idNaOperadora() == null ? provedor.assinaturaDoAviso(servicoDeContas.abrir(conta), cabecalhos, corpo) : null;
    if (assinatura != null) {
      transacao.executeWithoutResult(s -> eventos.save(evento(conta, null, "WEBHOOK", "ASSINATURA", aviso.status(), chave, corpo, true,
          "Assinatura " + assinatura)));
      publicador.publishEvent(new ServicoDeAssinaturasNoCartao.AssinaturaAvisada(conta.getTenantId(), conta.getProvedor(), assinatura));
      return;
    }

    Optional<Cobranca> cobranca = aviso.idNaOperadora() == null ? Optional.empty()
        : cobrancas.findByProvedorAndIdNaOperadora(conta.getProvedor(), aviso.idNaOperadora())
            .filter(c -> c.getTenantId().equals(conta.getTenantId()));
    if (cobranca.isEmpty()) {
      transacao.executeWithoutResult(s -> eventos.save(evento(conta, null, "WEBHOOK", aviso.tipo(), aviso.status(), chave, corpo, true,
          "Cobrança não criada pelo Azzo")));
      return;
    }
    aplicar(cobranca.get().getId(), aviso.status(), "WEBHOOK", aviso.tipo(), chave, corpo);
  }

  // ---- consulta de reserva -------------------------------------------------------------------

  /** Consulta na operadora as cobrancas que esperam resposta (o webhook pode se perder). */
  public int consultarPendentes() {
    Instant agora = Instant.now();
    List<Cobranca> pendentes = cobrancas.pendentesParaConsultar(
        agora.minusSeconds(20), agora.minus(Duration.ofHours(2)), Limit.of(50));
    int atualizadas = 0;
    for (Cobranca c : pendentes) {
      try {
        ContaDePagamento conta = contas.findById(c.getContaId()).orElse(null);
        if (conta == null) continue;
        StatusNaOperadora status = servicoDeContas.provedor(conta.getProvedor())
            .consultar(servicoDeContas.abrir(conta), c.getIdNaOperadora());
        if (status == null || status.status() == null || status.status().name().equals(c.getStatus())) continue;
        aplicar(c.getId(), status, "CONSULTA", "CONSULTA", "consulta:" + c.getId() + ":" + status.status(), null);
        atualizadas++;
      } catch (Exception e) {
        LOG.warn("pagamentos_consulta_falhou cobranca_id={} provedor={}", c.getId(), c.getProvedor(), e);
      }
    }
    return atualizadas;
  }

  // ---- aplicar status ------------------------------------------------------------------------

  /**
   * Muda o status da cobranca e guarda o evento, numa transacao so. Cobranca encerrada nao volta a
   * "aguardando"; estorno so vem depois de paga.
   */
  Cobranca aplicar(UUID cobrancaId, StatusNaOperadora status, String origem, String tipo, String chave, String corpo) {
    CobrancaMudouDeStatus[] mudou = new CobrancaMudouDeStatus[1];
    Cobranca resultado = transacao.execute(s -> {
      if (eventos.existsByIdempotencyKey(chave)) return cobrancas.findById(cobrancaId).orElseThrow();
      Cobranca c = cobrancas.findById(cobrancaId).orElseThrow();
      ContaDePagamento conta = contas.findById(c.getContaId()).orElseThrow();
      String anterior = c.getStatus();
      String novo = status == null || status.status() == null ? anterior : status.status().name();
      if (!transicaoValida(anterior, novo)) {
        eventos.save(evento(conta, c, origem, tipo, status, chave, corpo, true, "Transição ignorada: " + anterior + " → " + novo));
        return c;
      }
      c.setStatus(novo);
      if (status != null) {
        if (status.taxa() != null) c.setTaxa(status.taxa());
        if (status.valorLiquido() != null) c.setValorLiquido(status.valorLiquido());
        if (status.previsaoRecebimento() != null) c.setPrevisaoRecebimento(status.previsaoRecebimento());
        if (status.nsu() != null) c.setNsu(status.nsu());
        if (status.autorizacao() != null) c.setAutorizacao(status.autorizacao());
        if (status.bandeira() != null) c.setBandeira(status.bandeira());
        if (status.cartaoFinal4() != null) c.setCartaoFinal4(ultimos4(status.cartaoFinal4()));
        if (status.valorEstornado() != null) c.setValorEstornado(status.valorEstornado().min(c.getValor()));
        if (status.motivo() != null && ("RECUSADA".equals(novo) || "CANCELADA".equals(novo))) {
          c.setMotivoRecusa(limitar(status.motivo(), 300));
        }
      }
      if ("PAGA".equals(novo) && c.getPagaEm() == null) c.setPagaEm(Instant.now());
      // Fase 5: sem a taxa real da operadora, estima pela tabela da conta (taxa, liquido, previsao).
      if ("PAGA".equals(novo) && taxasDePagamento != null) taxasDePagamento.estimarSeFaltar(c);
      cobrancas.save(c);
      eventos.save(evento(conta, c, origem, tipo, status, chave, corpo, true, null));
      if (!novo.equals(anterior)) {
        mudou[0] = new CobrancaMudouDeStatus(c.getTenantId(), c.getId(), c.getOrigemTipo(), c.getOrigemId(), anterior, novo);
      }
      return c;
    });
    if (mudou[0] != null) {
      LOG.info("pagamentos_cobranca_status cobranca_id={} {} -> {}", mudou[0].cobrancaId(), mudou[0].statusAnterior(), mudou[0].statusNovo());
      publicador.publishEvent(mudou[0]);
    }
    return resultado;
  }

  static boolean transicaoValida(String anterior, String novo) {
    if (anterior.equals(novo)) return true;
    return switch (anterior) {
      case "CRIADA", "AGUARDANDO" -> true;
      case "PAGA" -> "ESTORNADA".equals(novo) || "ESTORNO_PARCIAL".equals(novo);
      case "ESTORNO_PARCIAL" -> "ESTORNADA".equals(novo);
      // Recusada pode ser tentada de novo na maquininha e vir paga; cancelada/expirada/estornada sao fim.
      case "RECUSADA" -> "PAGA".equals(novo);
      default -> false;
    };
  }

  // ---- apoio ---------------------------------------------------------------------------------

  private ContaDePagamento conta(UUID tenantId, NovaCobranca pedido) {
    if (pedido.contaId() != null) {
      return contas.findByIdAndTenantIdAndAtivoTrue(pedido.contaId(), tenantId)
          .orElseThrow(() -> new ApiClientErrorException("Conta de pagamento não encontrada.", 404));
    }
    String caso = switch (pedido.meio()) {
      case "PIX" -> "PIX";
      case "CREDITO", "DEBITO" -> pedido.terminalId() != null || pedido.terminalSerial() != null ? "CARTAO_PRESENCIAL" : "ONLINE";
      default -> "ONLINE";
    };
    return servicoDeContas.padrao(tenantId, caso).orElseThrow(() -> new ApiClientErrorException(
        "Nenhuma conta de pagamento está marcada para este uso. Configure em Configurações → Integrações → Pagamentos.", 400));
  }

  private static void exigirCapacidade(ProvedorDePagamento provedor, NovaCobranca pedido) {
    boolean presencial = pedido.terminalId() != null || pedido.terminalSerial() != null;
    Capacidade precisa = switch (pedido.meio()) {
      case "PIX" -> Capacidade.PIX;
      case "LINK" -> Capacidade.LINK;
      default -> presencial ? Capacidade.MAQUININHA_NUVEM : Capacidade.CARTAO_ONLINE;
    };
    if (!provedor.capacidades().contains(precisa)) {
      throw new ApiClientErrorException("A conta " + provedor.nome() + " não faz este tipo de cobrança pelo Azzo.", 400);
    }
  }

  private static void validar(NovaCobranca pedido) {
    if (pedido == null) throw new ApiClientErrorException("Informe a cobrança.", 400);
    if (!List.of("PIX", "CREDITO", "DEBITO", "LINK").contains(pedido.meio())) {
      throw new ApiClientErrorException("Meio de pagamento desconhecido.", 400);
    }
    if (pedido.valor() == null || pedido.valor().signum() <= 0) throw new ApiClientErrorException("Valor inválido.", 400);
    if (pedido.idempotencyKey() == null || pedido.idempotencyKey().isBlank()) {
      throw new IllegalArgumentException("idempotencyKey obrigatória");
    }
  }

  private EventoDeCobranca evento(ContaDePagamento conta, Cobranca c, String origem, String tipo, StatusNaOperadora status,
      String chave, String corpo, boolean processado, String erro) {
    EventoDeCobranca e = new EventoDeCobranca();
    e.setTenantId(conta.getTenantId());
    e.setContaId(conta.getId());
    e.setCobrancaId(c == null ? null : c.getId());
    e.setProvedor(conta.getProvedor());
    e.setOrigem(origem);
    e.setTipo(limitar(tipo == null ? "?" : tipo, 60));
    e.setStatusInformado(status == null || status.status() == null ? null : status.status().name());
    e.setIdempotencyKey(limitar(chave, 200));
    e.setPayloadHash(sha256(corpo == null ? chave : corpo));
    e.setPayloadJson(corpo);
    e.setProcessado(processado);
    e.setErro(limitar(erro, 500));
    return e;
  }

  private void auditar(UUID tenantId, String acao, Cobranca c) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.FINANCE;
      command.action = acao;
      command.entityType = "PAYMENT_CHARGE";
      command.entityId = c.getId().toString();
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.metadata = Map.of("provedor", c.getProvedor(), "meio", c.getMeio(), "valor", c.getValor(),
          "status", c.getStatus(), "origem", c.getOrigemTipo());
      auditService.recordSuccess(command);
    } catch (RuntimeException e) {
      LOG.error("pagamentos_auditoria_falhou cobranca_id={}", c.getId(), e);
      throw e;
    }
  }

  public static PagamentosDtos.Cobranca paraDto(Cobranca c) {
    PagamentosDtos.Cobranca dto = new PagamentosDtos.Cobranca();
    dto.id = c.getId().toString();
    dto.contaId = c.getContaId().toString();
    dto.provedor = c.getProvedor();
    dto.origemTipo = c.getOrigemTipo();
    dto.origemId = c.getOrigemId() == null ? null : c.getOrigemId().toString();
    dto.meio = c.getMeio();
    dto.parcelas = c.getParcelas();
    dto.valor = c.getValor();
    dto.taxa = c.getTaxa();
    dto.valorLiquido = c.getValorLiquido();
    dto.previsaoRecebimento = c.getPrevisaoRecebimento() == null ? null : c.getPrevisaoRecebimento().toString();
    dto.status = c.getStatus();
    dto.pixCopiaECola = c.getPixCopiaECola();
    dto.linkDePagamento = c.getLinkDePagamento();
    dto.expiraEm = c.getExpiraEm() == null ? null : c.getExpiraEm().toString();
    dto.nsu = c.getNsu();
    dto.autorizacao = c.getAutorizacao();
    dto.bandeira = c.getBandeira();
    dto.cartaoFinal4 = c.getCartaoFinal4();
    dto.valorEstornado = c.getValorEstornado();
    dto.motivoRecusa = c.getMotivoRecusa();
    dto.pagaEm = c.getPagaEm() == null ? null : c.getPagaEm().toString();
    dto.createdAt = c.getCreatedAt() == null ? null : c.getCreatedAt().toString();
    return dto;
  }

  private static String ultimos4(String numero) {
    String digitos = numero.replaceAll("\\D", "");
    return digitos.length() <= 4 ? digitos : digitos.substring(digitos.length() - 4);
  }

  private static String sha256(String valor) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String limitar(String valor, int maximo) {
    return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
  }
}
