package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
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
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueConfiguracao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicoInsumo;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.OrigemMovimentacaoEstoque;
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
 * Motor de movimentacao de estoque — porte dos tres metodos de
 * {@code modules/inventory/application/ServicoEstoque.java} que os modulos ja migrados consomem:
 * {@code criarMovimentacao}, {@code consumirInsumosPorAgendamento} e
 * {@code consumirInsumosPorItemComanda} (mais o auxiliar {@code consumirInsumosDeServicos}).
 *
 * <p><b>Substitui o placeholder {@code integration/EstoqueMovimentacaoService}</b>, que apenas
 * logava — com ele no lugar, fechar comanda com item PRODUTO nao dava baixa em
 * {@code itens_estoque} e o estorno nao devolvia. A assinatura publica foi mantida exatamente como
 * estava, entao {@code ServicoComanda} e {@code ServicoAgendamentos} nao precisaram de nenhuma
 * mudanca de logica.
 *
 * <p>A superficie HTTP ({@link ServicoEstoque} / {@code EstoqueController}) <b>delega</b> para ca:
 * {@code POST /api/v1/estoque/movimentacoes} cai em
 * {@link #criarMovimentacao(MovimentacaoEstoqueRequest)}. O calculo de saldo existe uma vez so,
 * como no original.
 */
@Service
public class EstoqueMovimentacaoService {

  private static final Logger LOG = LoggerFactory.getLogger(EstoqueMovimentacaoService.class);
  private static final BigDecimal CEM = new BigDecimal("100");

  private final ItemEstoqueRepository itemEstoqueRepository;
  private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
  private final EstoqueConfiguracaoRepository estoqueConfiguracaoRepository;
  private final ServicoInsumoRepository servicoInsumoRepository;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final AuditService auditService;

  public EstoqueMovimentacaoService(
      ItemEstoqueRepository itemEstoqueRepository,
      MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
      EstoqueConfiguracaoRepository estoqueConfiguracaoRepository,
      ServicoInsumoRepository servicoInsumoRepository,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      AuditService auditService) {
    this.itemEstoqueRepository = itemEstoqueRepository;
    this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
    this.estoqueConfiguracaoRepository = estoqueConfiguracaoRepository;
    this.servicoInsumoRepository = servicoInsumoRepository;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.auditService = auditService;
  }

  /**
   * Porte de {@code ServicoEstoque.criarMovimentacao(MovimentacaoEstoqueRequest)} — a forma
   * completa, usada pelo endpoint {@code POST /api/v1/estoque/movimentacoes}.
   *
   * <p>{@code ServicoEstoque} <b>delega</b> para ca em vez de reimplementar o calculo de saldo: no
   * original essa logica existe uma vez so.
   */
  @Transactional
  public MovimentacaoEstoqueResponse criarMovimentacao(MovimentacaoEstoqueRequest request) {
    return criarMovimentacao(
        UUID.fromString(request.itemEstoqueId),
        request.tipo,
        request.quantidade,
        request.motivo,
        request.origem,
        request.valorUnitarioPago,
        request.gerarLancamentoFinanceiro);
  }

  /**
   * Espelha {@code ServicoEstoque.criarMovimentacao(MovimentacaoEstoqueRequest)} na forma que
   * {@code ServicoComanda} usa: {@code origem} nula (vira {@code MANUAL}), sem
   * {@code valorUnitarioPago} e com {@code gerarLancamentoFinanceiro = false} — a receita da venda
   * ja e lancada pelo proprio fechamento da comanda, gerar de novo aqui duplicaria o caixa.
   *
   * @param tipo {@code "SAIDA"} (venda/consumo) ou {@code "ENTRADA"} (estorno/devolucao)
   */
  @Transactional
  public MovimentacaoEstoqueResponse criarMovimentacao(
      UUID itemEstoqueId, String tipo, BigDecimal quantidade, String motivo) {
    return criarMovimentacao(itemEstoqueId, tipo, quantidade, motivo, null, null, null);
  }

  /**
   * Nucleo compartilhado pelas duas entradas publicas. O tenant vem do {@link ContextoTenant}, como
   * no original.
   *
   * <p>Sem {@code @Transactional} de proposito: e chamado por auto-invocacao, que nao passa pelo
   * proxy (armadilha 4). A transacao vem dos dois metodos publicos acima.
   */
  private MovimentacaoEstoqueResponse criarMovimentacao(
      UUID itemEstoqueId,
      String tipo,
      BigDecimal quantidade,
      String motivo,
      String origem,
      BigDecimal valorUnitarioPago,
      Boolean gerarLancamentoFinanceiro) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ItemEstoque item =
        itemEstoqueRepository
            .findByIdAndTenantId(itemEstoqueId, tenantId)
            .orElseThrow(
                () ->
                    new ApiClientErrorException(
                        "Item de estoque nao encontrado.", HttpStatus.NOT_FOUND.value()));
    ItemEstoqueResponse beforeItem = toItemResponse(item);

    TipoMovimentacaoEstoque tipoMovimentacao =
        parseTipo(EstoqueTextoUtil.normalizarCodigoObrigatorio(tipo, "Tipo de movimentacao obrigatorio."));

    BigDecimal saldoAnterior = nvl(item.getSaldoAtual());
    BigDecimal saldoPosterior =
        CalculadoraEstoque.calcularSaldoPosterior(tipoMovimentacao, saldoAnterior, quantidade);
    if (tipoMovimentacao != TipoMovimentacaoEstoque.ENTRADA
        && saldoPosterior.compareTo(BigDecimal.ZERO) < 0
        && bloqueiaSaidaSemSaldo(tenantId)) {
      throw new ApiClientErrorException(
          "Saldo insuficiente para movimentacao.", HttpStatus.CONFLICT.value());
    }

    // Original: o custo medio so e sobrescrito quando ha valor unitario E o tipo e ENTRADA.
    if (valorUnitarioPago != null && tipoMovimentacao == TipoMovimentacaoEstoque.ENTRADA) {
      item.setCustoMedioUnitario(valorUnitarioPago);
    }
    item.setSaldoAtual(saldoPosterior);

    MovimentacaoEstoque movimentacao = new MovimentacaoEstoque();
    movimentacao.setTenantId(tenantId);
    movimentacao.setItemEstoqueId(item.getId());
    movimentacao.setTipo(tipoMovimentacao);
    movimentacao.setQuantidade(quantidade);
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
        CalculadoraEstoque.calcularValorTotalMovimentacao(valorUnitarioPago, quantidade));
    movimentacao.setGerarLancamentoFinanceiro(
        gerarLancamentoFinanceiro != null ? gerarLancamentoFinanceiro : Boolean.FALSE);
    movimentacao.setUsuarioId(authenticatedUser.idOuNulo());

    // saveAndFlush: o id da movimentacao so existe depois do @PrePersist, e ele vai no payload de
    // auditoria montado logo abaixo (armadilha 2 — o Panache ja teria emitido o INSERT no persist).
    MovimentacaoEstoque persistida = movimentacaoEstoqueRepository.saveAndFlush(movimentacao);
    itemEstoqueRepository.save(item);

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
   * Espelha {@code ServicoEstoque.consumirInsumosPorAgendamento}: baixa os insumos de todos os
   * servicos do agendamento concluido, amarrando cada movimentacao ao {@code appointmentId}.
   * Idempotente por par ({@code appointmentId}, {@code itemEstoqueId}).
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
   * Espelha {@code ServicoEstoque.consumirInsumosPorItemComanda}: consome insumos de um item
   * SERVICO ao fechar a comanda (F01), inclusive comanda avulsa sem agendamento vinculado — caso em
   * que {@link #consumirInsumosPorAgendamento} nunca era chamado. Idempotente por par
   * ({@code comandaItemId}, {@code itemEstoqueId}).
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
   * Porte de {@code ServicoEstoque.consumirInsumosDeServicos}. Tres comportamentos do original que
   * parecem descuido e <b>nao sao</b>, preservados:
   *
   * <ul>
   *   <li>insumo ja movimentado para a mesma ancora e <b>pulado</b> (idempotencia), nao somado;
   *   <li>item inexistente ou inativo e pulado <b>em silencio</b>;
   *   <li>com bloqueio de saida sem saldo ativo, saldo que ficaria negativo tambem e pulado em
   *       silencio — aqui o consumo automatico <b>nao</b> lanca 409, ao contrario de
   *       {@link #criarMovimentacao}. Concluir um atendimento nunca falha por falta de insumo.
   * </ul>
   *
   * <p>Este consumo tambem nao gera evento de auditoria no original — so o alerta de estoque
   * minimo em log.
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
        if (jaExisteMovimentacao.applyAsLong(insumo.getItemEstoqueId()) > 0) continue;

        ItemEstoque item = itemEstoqueRepository.findById(insumo.getItemEstoqueId()).orElse(null);
        if (item == null || !Boolean.TRUE.equals(item.getAtivo())) continue;

        BigDecimal fatorPerda =
            BigDecimal.ONE.add(
                insumo.getPercentualPerda().divide(CEM, 6, RoundingMode.HALF_UP));
        BigDecimal quantidade = insumo.getQuantidadeConsumo().multiply(fatorPerda);
        BigDecimal saldoAnterior = nvl(item.getSaldoAtual());
        BigDecimal saldoPosterior =
            CalculadoraEstoque.calcularSaldoPosterior(
                TipoMovimentacaoEstoque.SAIDA, saldoAnterior, quantidade);

        if (bloquear && saldoPosterior.compareTo(BigDecimal.ZERO) < 0) continue;

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

        if (cfg != null
            && Boolean.TRUE.equals(cfg.getAlertaEstoqueMinimoAtivo())
            && saldoPosterior.compareTo(nvl(item.getEstoqueMinimo())) <= 0) {
          LOG.warn(
              "[ESTOQUE_MINIMO] tenant={} item={} saldo={} minimo={}",
              tenantId,
              insumo.getItemEstoqueId(),
              saldoPosterior,
              nvl(item.getEstoqueMinimo()));
        }
      }
    }
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
      // O original deixa o IllegalArgumentException de valueOf subir (vira 500). Aqui vira 400,
      // que e o status que o proprio original ja usa para os outros codigos invalidos deste metodo.
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
   * movimentacao. A entidade migrada guarda so o {@code itemEstoqueId} (mesma decisao ja tomada em
   * {@code Agendamento}), entao o item resolvido vem por parametro.
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
