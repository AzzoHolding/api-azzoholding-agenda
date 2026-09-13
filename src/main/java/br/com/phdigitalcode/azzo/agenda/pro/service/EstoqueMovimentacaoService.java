package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ItemEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.MovimentacaoEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.MovimentacaoEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.TransacaoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.TransacaoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueConfiguracao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicoInsumo;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.OrigemMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueConfiguracaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentacaoEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoInsumoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.EstoqueTextoUtil;

/**
 * Motor de movimentacao de estoque — porte dos metodos de
 * {@code modules/inventory/application/ServicoEstoque.java} que mexem em saldo:
 * {@code criarMovimentacao}, {@code consumirInsumosPorAgendamento} e
 * {@code consumirInsumosPorItemComanda}. O calculo de saldo existe uma vez so, aqui.
 *
 * <p><b>Toda mudanca de saldo trava o item</b> ({@code SELECT ... FOR UPDATE}, via
 * {@link ItemEstoqueRepository#travarPorIdETenant}). O original lia o item sem trava nem versao:
 * duas baixas simultaneas liam o mesmo saldo e uma sumia. A trava tambem torna atomica a guarda de
 * idempotencia do consumo — o {@code count} roda depois dela.
 *
 * <p>Divergencias deliberadas do original, todas corrigindo o que a tela ja prometia:
 *
 * <ul>
 *   <li>{@code AJUSTE} e o <b>saldo final</b> (ver {@link CalculadoraEstoque});
 *   <li>entrada com preco atualiza o custo pelo <b>medio ponderado</b>, e nao pelo ultimo preco;
 *   <li>{@code gerarLancamentoFinanceiro} <b>cria a despesa</b> no financeiro — no original era so
 *       gravado;
 *   <li>consumo automatico pulado (saldo insuficiente, item inativo) e <b>auditado e avisado</b>,
 *       e o alerta de estoque minimo vira notificacao — no original os dois ficavam so no log.
 * </ul>
 */
@Service
public class EstoqueMovimentacaoService {

  private static final Logger LOG = LoggerFactory.getLogger(EstoqueMovimentacaoService.class);
  private static final BigDecimal CEM = new BigDecimal("100");
  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  /** Canal das notificacoes do estoque no sino da aplicacao. */
  static final String CANAL_ALERTA_ESTOQUE = "STOCK_ALERT";

  /** O mesmo item nao repete o aviso de minimo em menos de seis horas. */
  private static final long JANELA_ALERTA_MINIMO_SEGUNDOS = 6 * 60 * 60L;

  /** O mesmo consumo pulado, com a mesma mensagem, nao repete o aviso em menos de uma hora. */
  private static final long JANELA_CONSUMO_PULADO_SEGUNDOS = 60 * 60L;

  static final String CATEGORIA_DESPESA_ESTOQUE = "Compra de estoque";

  private final ItemEstoqueRepository itemEstoqueRepository;
  private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
  private final EstoqueConfiguracaoRepository estoqueConfiguracaoRepository;
  private final ServicoInsumoRepository servicoInsumoRepository;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final AuditService auditService;
  private final ServicoFinanceiro servicoFinanceiro;
  private final NotificationPublisher notificationPublisher;

  public EstoqueMovimentacaoService(
      ItemEstoqueRepository itemEstoqueRepository,
      MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
      EstoqueConfiguracaoRepository estoqueConfiguracaoRepository,
      ServicoInsumoRepository servicoInsumoRepository,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      AuditService auditService,
      ServicoFinanceiro servicoFinanceiro,
      NotificationPublisher notificationPublisher) {
    this.itemEstoqueRepository = itemEstoqueRepository;
    this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
    this.estoqueConfiguracaoRepository = estoqueConfiguracaoRepository;
    this.servicoInsumoRepository = servicoInsumoRepository;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.auditService = auditService;
    this.servicoFinanceiro = servicoFinanceiro;
    this.notificationPublisher = notificationPublisher;
  }

  /** A forma completa, usada pelo endpoint {@code POST /api/v1/estoque/movimentacoes}. */
  @Transactional
  public MovimentacaoEstoqueResponse criarMovimentacao(MovimentacaoEstoqueRequest request) {
    return registrar(
        UUID.fromString(request.itemEstoqueId),
        request.tipo,
        request.quantidade,
        request.motivo,
        request.origem,
        request.valorUnitarioPago,
        request.gerarLancamentoFinanceiro,
        request.formaPagamento);
  }

  /**
   * A forma que {@code ServicoComanda} usa: venda de produto ({@code SAIDA}) e os estornos
   * ({@code ENTRADA}). Origem {@code VENDA} — no original caia em {@code MANUAL}, e a venda aparecia
   * no painel como perda. Sem preco e sem lancamento financeiro: a receita da venda ja e lancada
   * pelo fechamento da comanda.
   *
   * @param tipo {@code "SAIDA"} (venda) ou {@code "ENTRADA"} (estorno/devolucao)
   */
  @Transactional
  public MovimentacaoEstoqueResponse criarMovimentacao(
      UUID itemEstoqueId, String tipo, BigDecimal quantidade, String motivo) {
    return registrar(
        itemEstoqueId, tipo, quantidade, motivo, OrigemMovimentacaoEstoque.VENDA.name(), null, null, null);
  }

  /**
   * Entrada vinda do recebimento de um pedido de compra. Sem lancamento financeiro: o pedido nao
   * tem forma de pagamento, e quem paga o fornecedor lanca a despesa pelo financeiro.
   */
  @Transactional
  public MovimentacaoEstoqueResponse registrarEntradaDeCompra(
      UUID itemEstoqueId, BigDecimal quantidade, BigDecimal valorUnitario, String motivo) {
    return registrar(
        itemEstoqueId,
        TipoMovimentacaoEstoque.ENTRADA.name(),
        quantidade,
        motivo,
        OrigemMovimentacaoEstoque.COMPRA.name(),
        valorUnitario,
        Boolean.FALSE,
        null);
  }

  /**
   * Aplica a diferenca de uma contagem de inventario ao saldo de AGORA.
   *
   * <p>A diferenca e medida no momento da contagem ({@code contada - esperada}), e nao reaplicada
   * como saldo absoluto: se houve venda ou consumo entre a contagem e o fechamento, sobrescrever
   * pelo numero contado apagaria esse movimento. O saldo nunca fica negativo por inventario — uma
   * prateleira nao tem menos que zero.
   *
   * @return a movimentacao criada, ou {@code null} quando nao havia o que corrigir
   */
  @Transactional
  public MovimentacaoEstoqueResponse ajustarPorInventario(
      UUID itemEstoqueId, BigDecimal diferenca, String motivo) {
    if (diferenca == null || diferenca.signum() == 0) return null;
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ItemEstoque item = travarItemOuFalhar(itemEstoqueId, tenantId);
    BigDecimal saldoAtual = nvl(item.getSaldoAtual());
    BigDecimal saldoFinal = saldoAtual.add(diferenca).max(BigDecimal.ZERO);
    if (saldoFinal.compareTo(saldoAtual) == 0) return null;
    return registrar(
        itemEstoqueId,
        TipoMovimentacaoEstoque.AJUSTE.name(),
        saldoFinal,
        motivo,
        OrigemMovimentacaoEstoque.INVENTARIO.name(),
        null,
        Boolean.FALSE,
        null);
  }

  /**
   * Nucleo compartilhado. Sem {@code @Transactional} de proposito: e chamado por auto-invocacao, que
   * nao passa pelo proxy (armadilha 4). A transacao vem dos metodos publicos acima.
   */
  private MovimentacaoEstoqueResponse registrar(
      UUID itemEstoqueId,
      String tipo,
      BigDecimal quantidade,
      String motivo,
      String origem,
      BigDecimal valorUnitarioPago,
      Boolean gerarLancamentoFinanceiro,
      String formaPagamento) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TipoMovimentacaoEstoque tipoMovimentacao =
        parseTipo(EstoqueTextoUtil.normalizarCodigoObrigatorio(tipo, "Tipo de movimentacao obrigatorio."));
    validarQuantidade(tipoMovimentacao, quantidade);

    boolean lancarNoFinanceiro = Boolean.TRUE.equals(gerarLancamentoFinanceiro);
    if (lancarNoFinanceiro
        && (tipoMovimentacao != TipoMovimentacaoEstoque.ENTRADA
            || valorUnitarioPago == null
            || valorUnitarioPago.signum() <= 0)) {
      throw new ApiClientErrorException(
          "Para lancar no financeiro, registre uma entrada com o valor unitario pago.",
          HttpStatus.BAD_REQUEST.value());
    }

    ItemEstoque item = travarItemOuFalhar(itemEstoqueId, tenantId);
    ItemEstoqueResponse beforeItem = toItemResponse(item);

    BigDecimal saldoAnterior = nvl(item.getSaldoAtual());
    BigDecimal saldoPosterior =
        CalculadoraEstoque.calcularSaldoPosterior(tipoMovimentacao, saldoAnterior, quantidade);

    if (tipoMovimentacao == TipoMovimentacaoEstoque.AJUSTE
        && saldoPosterior.compareTo(saldoAnterior) == 0) {
      throw new ApiClientErrorException(
          "O saldo do item ja e esse — nao ha o que ajustar.", HttpStatus.BAD_REQUEST.value());
    }
    if (tipoMovimentacao == TipoMovimentacaoEstoque.SAIDA
        && saldoPosterior.compareTo(BigDecimal.ZERO) < 0
        && bloqueiaSaidaSemSaldo(tenantId)) {
      throw new ApiClientErrorException(
          "Saldo insuficiente para movimentacao.", HttpStatus.CONFLICT.value());
    }

    // No ajuste o numero digitado e o saldo final; o que se movimentou e a distancia ate ele.
    BigDecimal quantidadeMovimentada =
        tipoMovimentacao == TipoMovimentacaoEstoque.AJUSTE
            ? saldoPosterior.subtract(saldoAnterior).abs()
            : quantidade;

    if (valorUnitarioPago != null && tipoMovimentacao == TipoMovimentacaoEstoque.ENTRADA) {
      item.setCustoMedioUnitario(
          CalculadoraEstoque.calcularCustoMedio(
              saldoAnterior, item.getCustoMedioUnitario(), quantidadeMovimentada, valorUnitarioPago));
    }
    item.setSaldoAtual(saldoPosterior);

    MovimentacaoEstoque movimentacao = new MovimentacaoEstoque();
    movimentacao.setTenantId(tenantId);
    movimentacao.setItemEstoqueId(item.getId());
    movimentacao.setTipo(tipoMovimentacao);
    movimentacao.setQuantidade(quantidadeMovimentada);
    movimentacao.setSaldoAnterior(saldoAnterior);
    movimentacao.setSaldoPosterior(saldoPosterior);
    movimentacao.setMotivo(EstoqueTextoUtil.normalizarTextoLivreObrigatorio(motivo, "Motivo obrigatorio."));
    movimentacao.setOrigem(
        origem == null || origem.isBlank()
            ? OrigemMovimentacaoEstoque.MANUAL
            : parseOrigem(
                EstoqueTextoUtil.normalizarCodigoObrigatorio(origem, "Origem de movimentacao invalida.")));
    movimentacao.setValorUnitarioPago(valorUnitarioPago);
    movimentacao.setValorTotalMovimentacao(
        CalculadoraEstoque.calcularValorTotalMovimentacao(valorUnitarioPago, quantidadeMovimentada));
    movimentacao.setGerarLancamentoFinanceiro(lancarNoFinanceiro);
    movimentacao.setUsuarioId(authenticatedUser.idOuNulo());

    // saveAndFlush: o id da movimentacao so existe depois do @PrePersist, e ele vai no payload de
    // auditoria montado logo abaixo (armadilha 2).
    MovimentacaoEstoque persistida = movimentacaoEstoqueRepository.saveAndFlush(movimentacao);
    itemEstoqueRepository.save(item);

    if (lancarNoFinanceiro) {
      persistida.setTransacaoFinanceiraId(lancarDespesa(item, persistida, formaPagamento));
      movimentacaoEstoqueRepository.save(persistida);
    }

    avisarSeCruzouOMinimo(tenantId, item, saldoAnterior, saldoPosterior);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("itemBefore", beforeItem);
    metadata.put("itemAfter", toItemResponse(item));
    MovimentacaoEstoqueResponse response = toMovimentacaoResponse(persistida, item);
    auditarEstoque(
        tenantId,
        "STOCK_MOVEMENT_CREATE",
        "STOCK_MOVEMENT",
        persistida.getId(),
        null,
        response,
        metadata);
    return response;
  }

  /**
   * Cria a despesa da compra no financeiro, na mesma transacao: se o lancamento falhar, a entrada
   * tambem nao acontece — melhor um erro na tela do que um caixa menor que a realidade.
   */
  private UUID lancarDespesa(ItemEstoque item, MovimentacaoEstoque movimentacao, String formaPagamento) {
    TransacaoRequest request = new TransacaoRequest();
    request.type = "EXPENSE";
    request.category = CATEGORIA_DESPESA_ESTOQUE;
    String descricao =
        "Compra de estoque: "
            + item.getNome()
            + " ("
            + movimentacao.getQuantidade().stripTrailingZeros().toPlainString()
            + " "
            + item.getUnidadeMedida()
            + ")";
    request.description = descricao.length() > 500 ? descricao.substring(0, 500) : descricao;
    request.amount = movimentacao.getValorTotalMovimentacao().setScale(2, RoundingMode.HALF_UP);
    request.paymentMethod = normalizarFormaPagamento(formaPagamento);
    request.date = LocalDate.now(ZONA_BR).toString();
    request.productId = item.getId() != null ? item.getId().toString() : null;
    TransacaoResponse transacao = servicoFinanceiro.criar(request);
    return transacao != null && transacao.id != null ? UUID.fromString(transacao.id) : null;
  }

  private String normalizarFormaPagamento(String formaPagamento) {
    if (formaPagamento == null || formaPagamento.isBlank()) return MetodoPagamento.OTHER.name();
    String codigo = formaPagamento.trim().toUpperCase(Locale.ROOT);
    try {
      return MetodoPagamento.valueOf(codigo).name();
    } catch (IllegalArgumentException e) {
      throw new ApiClientErrorException(
          "Forma de pagamento invalida. Use CASH, CREDIT_CARD, DEBIT_CARD, PIX ou OTHER.",
          HttpStatus.BAD_REQUEST.value());
    }
  }

  private static void validarQuantidade(TipoMovimentacaoEstoque tipo, BigDecimal quantidade) {
    if (quantidade == null || quantidade.signum() < 0) {
      throw new ApiClientErrorException(
          "Quantidade invalida — informe um numero positivo.", HttpStatus.BAD_REQUEST.value());
    }
    if (tipo != TipoMovimentacaoEstoque.AJUSTE && quantidade.signum() == 0) {
      throw new ApiClientErrorException(
          "A quantidade de entrada ou saida precisa ser maior que zero.",
          HttpStatus.BAD_REQUEST.value());
    }
  }

  /**
   * Baixa os insumos de todos os servicos do agendamento concluido, amarrando cada movimentacao ao
   * {@code appointmentId}. Idempotente por par ({@code appointmentId}, {@code itemEstoqueId}).
   */
  @Transactional
  public void consumirInsumosPorAgendamento(
      UUID tenantId, UUID appointmentId, List<UUID> serviceIds) {
    consumirInsumosDeServicos(
        tenantId,
        serviceIds,
        itemEstoqueId ->
            movimentacaoEstoqueRepository.countByTenantIdAndAppointmentIdAndItemEstoqueId(
                tenantId, appointmentId, itemEstoqueId),
        movimentacao -> movimentacao.setAppointmentId(appointmentId),
        "Consumo automatico por agendamento");
  }

  /**
   * Consome insumos de um item SERVICO ao fechar a comanda (F01), inclusive comanda avulsa sem
   * agendamento. Idempotente por par ({@code comandaItemId}, {@code itemEstoqueId}).
   */
  @Transactional
  public void consumirInsumosPorItemComanda(UUID tenantId, UUID comandaItemId, UUID serviceId) {
    if (comandaItemId == null) return;
    consumirInsumosDeServicos(
        tenantId,
        serviceId == null ? List.of() : List.of(serviceId),
        itemEstoqueId ->
            movimentacaoEstoqueRepository.countByTenantIdAndComandaItemIdAndItemEstoqueId(
                tenantId, comandaItemId, itemEstoqueId),
        movimentacao -> movimentacao.setComandaItemId(comandaItemId),
        "Consumo automatico por item de comanda");
  }

  /**
   * Concluir um atendimento <b>nunca falha</b> por falta de insumo — isso vem do original e continua
   * valendo. O que mudou: o insumo pulado (saldo insuficiente com o bloqueio ligado, ou item inativo)
   * deixa rastro na auditoria e vira aviso no sino, porque um consumo que nao baixou e exatamente a
   * divergencia que o inventario depois vai encontrar sem explicacao.
   *
   * <p>Ordem importa: primeiro trava o item, depois confere se ja houve consumo. Com a conferencia
   * antes da trava, dois fechamentos simultaneos da mesma comanda passavam os dois pelo {@code count}.
   */
  private void consumirInsumosDeServicos(
      UUID tenantId,
      List<UUID> serviceIds,
      ToLongFunction<UUID> jaExisteMovimentacao,
      Consumer<MovimentacaoEstoque> origemStamp,
      String motivo) {
    if (serviceIds == null || serviceIds.isEmpty()) return;

    EstoqueConfiguracao cfg =
        estoqueConfiguracaoRepository.findByTenantId(tenantId).orElse(null);
    boolean bloquear =
        cfg == null || cfg.getBloquearSaidaSemSaldo() == null || cfg.getBloquearSaidaSemSaldo();

    for (UUID serviceId : serviceIds) {
      if (serviceId == null) continue;
      List<ServicoInsumo> insumos =
          servicoInsumoRepository.findByTenantAndService(tenantId, serviceId);
      for (ServicoInsumo insumo : insumos) {
        ItemEstoque item =
            itemEstoqueRepository.travarPorIdETenant(insumo.getItemEstoqueId(), tenantId).orElse(null);
        if (jaExisteMovimentacao.applyAsLong(insumo.getItemEstoqueId()) > 0) continue;
        if (item == null) continue;

        BigDecimal fatorPerda =
            BigDecimal.ONE.add(
                insumo.getPercentualPerda().divide(CEM, 6, RoundingMode.HALF_UP));
        BigDecimal quantidade = insumo.getQuantidadeConsumo().multiply(fatorPerda);

        if (!Boolean.TRUE.equals(item.getAtivo())) {
          registrarConsumoPulado(tenantId, item, quantidade, motivo, "o item esta inativo");
          continue;
        }

        BigDecimal saldoAnterior = nvl(item.getSaldoAtual());
        BigDecimal saldoPosterior =
            CalculadoraEstoque.calcularSaldoPosterior(
                TipoMovimentacaoEstoque.SAIDA, saldoAnterior, quantidade);

        if (bloquear && saldoPosterior.compareTo(BigDecimal.ZERO) < 0) {
          registrarConsumoPulado(tenantId, item, quantidade, motivo, "o saldo nao era suficiente");
          continue;
        }

        item.setSaldoAtual(saldoPosterior);
        itemEstoqueRepository.save(item);

        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque();
        movimentacao.setTenantId(tenantId);
        movimentacao.setItemEstoqueId(insumo.getItemEstoqueId());
        movimentacao.setTipo(TipoMovimentacaoEstoque.SAIDA);
        movimentacao.setQuantidade(quantidade);
        movimentacao.setSaldoAnterior(saldoAnterior);
        movimentacao.setSaldoPosterior(saldoPosterior);
        movimentacao.setMotivo(motivo);
        movimentacao.setOrigem(OrigemMovimentacaoEstoque.SERVICO);
        movimentacao.setGerarLancamentoFinanceiro(Boolean.FALSE);
        origemStamp.accept(movimentacao);
        // Flush imediato: a guarda de idempotencia do proximo insumo do mesmo item e um count no
        // banco, e o Spring Data so emitiria o INSERT no commit (armadilha 2).
        movimentacaoEstoqueRepository.saveAndFlush(movimentacao);

        avisarSeCruzouOMinimo(tenantId, item, saldoAnterior, saldoPosterior, cfg);
      }
    }
  }

  private void registrarConsumoPulado(
      UUID tenantId, ItemEstoque item, BigDecimal quantidade, String motivo, String porque) {
    String mensagem =
        "O consumo de "
            + formatar(quantidade)
            + " "
            + item.getUnidadeMedida()
            + " de "
            + item.getNome()
            + " nao foi baixado do estoque: "
            + porque
            + " (saldo "
            + formatar(nvl(item.getSaldoAtual()))
            + ").";
    LOG.warn(
        "[ESTOQUE_CONSUMO_PULADO] tenant={} item={} quantidade={} motivo={}",
        tenantId,
        item.getId(),
        quantidade,
        porque);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("quantidade", quantidade);
    metadata.put("saldoAtual", item.getSaldoAtual());
    metadata.put("origem", motivo);
    metadata.put("motivo", porque);
    auditarEstoque(
        tenantId, "STOCK_CONSUMPTION_SKIPPED", "STOCK_ITEM", item.getId(), null, null, metadata);
    notificar(tenantId, item, mensagem, JANELA_CONSUMO_PULADO_SEGUNDOS);
  }

  private void avisarSeCruzouOMinimo(
      UUID tenantId, ItemEstoque item, BigDecimal saldoAnterior, BigDecimal saldoPosterior) {
    // So consulta a configuracao quando ha travessia possivel: entrada nunca desce ao minimo.
    BigDecimal minimo = nvl(item.getEstoqueMinimo());
    if (minimo.signum() <= 0
        || saldoAnterior.compareTo(minimo) <= 0
        || saldoPosterior.compareTo(minimo) > 0) {
      return;
    }
    avisarSeCruzouOMinimo(
        tenantId,
        item,
        saldoAnterior,
        saldoPosterior,
        estoqueConfiguracaoRepository.findByTenantId(tenantId).orElse(null));
  }

  /**
   * Avisa quando o saldo ACABA de chegar ao minimo — so na travessia, e nao a cada baixa de um item
   * que ja estava abaixo, que viraria um sino tocando o dia inteiro. Sem configuracao, o alerta vale
   * como ligado (e o padrao da entidade).
   */
  private void avisarSeCruzouOMinimo(
      UUID tenantId,
      ItemEstoque item,
      BigDecimal saldoAnterior,
      BigDecimal saldoPosterior,
      EstoqueConfiguracao cfg) {
    boolean alertaAtivo = cfg == null || !Boolean.FALSE.equals(cfg.getAlertaEstoqueMinimoAtivo());
    BigDecimal minimo = nvl(item.getEstoqueMinimo());
    if (!alertaAtivo || minimo.signum() <= 0) return;
    if (saldoAnterior.compareTo(minimo) <= 0 || saldoPosterior.compareTo(minimo) > 0) return;

    LOG.warn(
        "[ESTOQUE_MINIMO] tenant={} item={} saldo={} minimo={}",
        tenantId,
        item.getId(),
        saldoPosterior,
        minimo);
    notificar(
        tenantId,
        item,
        "Estoque baixo: "
            + item.getNome()
            + " chegou a "
            + formatar(saldoPosterior)
            + " "
            + item.getUnidadeMedida()
            + " (minimo "
            + formatar(minimo)
            + "). Hora de repor.",
        JANELA_ALERTA_MINIMO_SEGUNDOS);
  }

  /** Aviso no sino. Falhar aqui nunca derruba a movimentacao — o saldo e o que importa. */
  private void notificar(UUID tenantId, ItemEstoque item, String mensagem, long janelaSegundos) {
    try {
      notificationPublisher.publish(
          tenantId,
          null,
          null,
          CANAL_ALERTA_ESTOQUE,
          "estoque:" + item.getId(),
          mensagem,
          StatusNotification.SENT,
          null,
          Instant.now(),
          janelaSegundos);
    } catch (RuntimeException e) {
      LOG.warn("[ESTOQUE_AVISO_FALHOU] tenant={} item={}", tenantId, item.getId(), e);
    }
  }

  private static String formatar(BigDecimal valor) {
    return valor.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }

  private ItemEstoque travarItemOuFalhar(UUID itemEstoqueId, UUID tenantId) {
    return itemEstoqueRepository
        .travarPorIdETenant(itemEstoqueId, tenantId)
        .orElseThrow(
            () ->
                new ApiClientErrorException(
                    "Item de estoque nao encontrado.", HttpStatus.NOT_FOUND.value()));
  }

  /** Sem linha de configuracao o bloqueio e considerado <b>ativo</b>, como no original. */
  private boolean bloqueiaSaidaSemSaldo(UUID tenantId) {
    EstoqueConfiguracao cfg = estoqueConfiguracaoRepository.findByTenantId(tenantId).orElse(null);
    return cfg == null || cfg.getBloquearSaidaSemSaldo() == null || cfg.getBloquearSaidaSemSaldo();
  }

  private TipoMovimentacaoEstoque parseTipo(String codigo) {
    try {
      return TipoMovimentacaoEstoque.valueOf(codigo);
    } catch (IllegalArgumentException e) {
      // O original deixa o IllegalArgumentException de valueOf subir (vira 500). Aqui vira 400.
      throw new ApiClientErrorException(
          "Tipo de movimentacao invalido.", HttpStatus.BAD_REQUEST.value());
    }
  }

  /** Mesma divergencia deliberada de {@link #parseTipo}: 400 onde o original daria 500. */
  private OrigemMovimentacaoEstoque parseOrigem(String codigo) {
    try {
      return OrigemMovimentacaoEstoque.valueOf(codigo);
    } catch (IllegalArgumentException e) {
      throw new ApiClientErrorException(
          "Origem de movimentacao invalida.", HttpStatus.BAD_REQUEST.value());
    }
  }

  private void auditarEstoque(
      UUID tenantId,
      String action,
      String entityType,
      UUID entityId,
      Object before,
      Object after,
      Object metadata) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = authenticatedUser.idOuNulo();
      command.actorRole = authenticatedUser.roleOuNulo();
      command.module = AuditConstants.Module.INVENTORY;
      command.action = action;
      command.entityType = entityType;
      command.entityId = entityId != null ? entityId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      command.metadata = metadata;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve bloquear fluxo principal.
    }
  }

  static ItemEstoqueResponse toItemResponse(ItemEstoque entity) {
    ItemEstoqueResponse response = new ItemEstoqueResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.nome = entity.getNome();
    response.sku = entity.getSku();
    response.unidadeMedida = entity.getUnidadeMedida();
    response.saldoAtual = entity.getSaldoAtual();
    response.estoqueMinimo = entity.getEstoqueMinimo();
    response.custoMedioUnitario = entity.getCustoMedioUnitario();
    response.ativo = entity.getAtivo();
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  /**
   * O original preenche {@code itemNome} a partir da {@code @ManyToOne ItemEstoque} da entidade de
   * movimentacao. A entidade migrada guarda so o {@code itemEstoqueId}, entao o item resolvido vem
   * por parametro.
   */
  static MovimentacaoEstoqueResponse toMovimentacaoResponse(
      MovimentacaoEstoque entity, ItemEstoque item) {
    MovimentacaoEstoqueResponse response = new MovimentacaoEstoqueResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.itemEstoqueId =
        entity.getItemEstoqueId() != null ? entity.getItemEstoqueId().toString() : null;
    response.tipo = entity.getTipo() != null ? entity.getTipo().name() : null;
    response.quantidade = entity.getQuantidade();
    response.saldoAnterior = entity.getSaldoAnterior();
    response.saldoPosterior = entity.getSaldoPosterior();
    response.motivo = entity.getMotivo();
    response.origem = entity.getOrigem() != null ? entity.getOrigem().name() : null;
    response.valorUnitarioPago = entity.getValorUnitarioPago();
    response.valorTotalMovimentacao = entity.getValorTotalMovimentacao();
    response.gerarLancamentoFinanceiro = entity.getGerarLancamentoFinanceiro();
    response.transacaoFinanceiraId =
        entity.getTransacaoFinanceiraId() != null
            ? entity.getTransacaoFinanceiraId().toString()
            : null;
    response.usuarioId = entity.getUsuarioId() != null ? entity.getUsuarioId().toString() : null;
    response.appointmentId =
        entity.getAppointmentId() != null ? entity.getAppointmentId().toString() : null;
    if (item != null) response.itemNome = item.getNome();
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    return response;
  }

  static BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
