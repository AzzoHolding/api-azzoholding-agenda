package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ComandaDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackageBalance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackagePurchase;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Comanda;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackageItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantLoyaltySettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.TenantAsaasChargeService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentDepositRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientPackageBalanceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientPackagePurchaseRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaPagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentacaoEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicePackageItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicePackageRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantLoyaltySettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransactionCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;

/**
 * Espelha {@code modules/pos/application/ServicoComanda.java}.
 *
 * <p>Diferencas estruturais em relacao ao original, sem mudanca de comportamento observavel:
 *
 * <ul>
 *   <li>{@code jakarta.ws.rs.NotFoundException} vira {@link ApiClientErrorException} com status 404
 *       (o {@code GlobalExceptionHandler} preserva o status, entao o contrato HTTP e identico);
 *   <li>{@code JsonWebToken} injetado vira o bean compartilhado {@link AuthenticatedUser} —
 *       {@code idOuNulo()} tem exatamente a semantica do {@code obterUsuarioId()} original (nunca
 *       lanca, devolve null quando nao ha token ou o subject nao e um UUID);
 *   <li>a paginacao Panache ({@code query.count()} + {@code query.page(...)}) vira {@link Pageable};
 *   <li>os tres metodos de movimentacao de {@code ServicoEstoque} (modulo {@code inventory}) vivem
 *       em {@link EstoqueMovimentacaoService}, que <b>aplica a baixa/devolucao de verdade</b> (foi
 *       placeholder ate a Etapa 16). O restante de {@code ServicoEstoque} — CRUD de itens,
 *       importacao, inventario, fornecedor, pedido de compra, transferencia — continua fora.
 * </ul>
 */
@Service
public class ServicoComanda {

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final ComandaRepository comandaRepository;
  private final ComandaItemRepository comandaItemRepository;
  private final ComandaPagamentoRepository comandaPagamentoRepository;
  private final ServicoRepository servicoRepository;
  private final ItemEstoqueRepository itemEstoqueRepository;
  private final ProfissionalRepository profissionalRepository;
  private final ClienteRepository clienteRepository;
  private final AppointmentDepositRepository appointmentDepositRepository;
  private final TenantAsaasChargeService tenantAsaasChargeService;
  private final AsaasClient asaasClient;
  private final EstoqueMovimentacaoService estoqueMovimentacaoService;
  private final CommissionService commissionService;
  private final TransacaoRepository transacaoRepository;
  private final TransactionCategoryRepository transactionCategoryRepository;
  private final ServicePackageRepository servicePackageRepository;
  private final ServicePackageItemRepository servicePackageItemRepository;
  private final ClientPackagePurchaseRepository clientPackagePurchaseRepository;
  private final ClientPackageBalanceRepository clientPackageBalanceRepository;
  private final TenantLoyaltySettingsRepository tenantLoyaltySettingsRepository;
  private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;

  public ServicoComanda(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      ComandaRepository comandaRepository,
      ComandaItemRepository comandaItemRepository,
      ComandaPagamentoRepository comandaPagamentoRepository,
      ServicoRepository servicoRepository,
      ItemEstoqueRepository itemEstoqueRepository,
      ProfissionalRepository profissionalRepository,
      ClienteRepository clienteRepository,
      AppointmentDepositRepository appointmentDepositRepository,
      TenantAsaasChargeService tenantAsaasChargeService,
      AsaasClient asaasClient,
      EstoqueMovimentacaoService estoqueMovimentacaoService,
      CommissionService commissionService,
      TransacaoRepository transacaoRepository,
      TransactionCategoryRepository transactionCategoryRepository,
      ServicePackageRepository servicePackageRepository,
      ServicePackageItemRepository servicePackageItemRepository,
      ClientPackagePurchaseRepository clientPackagePurchaseRepository,
      ClientPackageBalanceRepository clientPackageBalanceRepository,
      TenantLoyaltySettingsRepository tenantLoyaltySettingsRepository,
      MovimentacaoEstoqueRepository movimentacaoEstoqueRepository) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.comandaRepository = comandaRepository;
    this.comandaItemRepository = comandaItemRepository;
    this.comandaPagamentoRepository = comandaPagamentoRepository;
    this.servicoRepository = servicoRepository;
    this.itemEstoqueRepository = itemEstoqueRepository;
    this.profissionalRepository = profissionalRepository;
    this.clienteRepository = clienteRepository;
    this.appointmentDepositRepository = appointmentDepositRepository;
    this.tenantAsaasChargeService = tenantAsaasChargeService;
    this.asaasClient = asaasClient;
    this.estoqueMovimentacaoService = estoqueMovimentacaoService;
    this.commissionService = commissionService;
    this.transacaoRepository = transacaoRepository;
    this.transactionCategoryRepository = transactionCategoryRepository;
    this.servicePackageRepository = servicePackageRepository;
    this.servicePackageItemRepository = servicePackageItemRepository;
    this.clientPackagePurchaseRepository = clientPackagePurchaseRepository;
    this.clientPackageBalanceRepository = clientPackageBalanceRepository;
    this.tenantLoyaltySettingsRepository = tenantLoyaltySettingsRepository;
    this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
  }

  @Transactional
  public ComandaDtos.ComandaResponse abrir(ComandaDtos.AbrirComandaRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    Comanda comanda = new Comanda();
    comanda.setTenantId(tenantId);
    comanda.setAppointmentId(parseUuidOrNull(request != null ? request.appointmentId : null));
    comanda.setClientId(parseUuidOrNull(request != null ? request.clientId : null));
    comanda.setAbertaPor(obterUsuarioId());
    comandaRepository.save(comanda);

    return toResponse(comanda, List.of(), List.of());
  }

  @Transactional(readOnly = true)
  public ComandaDtos.ComandaPageResponse listar(String status, int page, int size) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int pageSize = size > 0 ? size : 20;
    Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize);
    Page<Comanda> pagina =
        (status != null && !status.isBlank())
            ? comandaRepository.findByTenantIdAndStatusOrderByOpenedAtDesc(
                tenantId, status, pageable)
            : comandaRepository.findByTenantIdOrderByOpenedAtDesc(tenantId, pageable);

    ComandaDtos.ComandaPageResponse response = new ComandaDtos.ComandaPageResponse();
    response.totalElements = pagina.getTotalElements();
    // O original ecoa o `page` cru do request (nao o clampeado usado na consulta) — preservado.
    response.page = page;
    response.size = pageSize;
    response.content =
        pagina.getContent().stream()
            .map(
                c ->
                    toResponse(
                        c,
                        comandaItemRepository.findByComandaIdOrderByCreatedAt(c.getId()),
                        comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(c.getId())))
            .toList();
    return response;
  }

  @Transactional(readOnly = true)
  public ComandaDtos.ComandaResponse obter(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Comanda comanda = buscarOuFalhar(id, tenantId);
    return toResponse(
        comanda,
        comandaItemRepository.findByComandaIdOrderByCreatedAt(comanda.getId()),
        comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(comanda.getId()));
  }

  @Transactional
  public ComandaDtos.ComandaResponse adicionarItem(
      UUID id, ComandaDtos.AdicionarItemRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Comanda comanda = buscarOuFalhar(id, tenantId);
    exigirAberta(comanda);

    if (ComandaItem.TIPO_PACOTE.equals(request.tipo) && comanda.getClientId() == null) {
      throw new IllegalArgumentException(
          "Item do tipo PACOTE exige comanda com cliente identificado.");
    }

    UUID referenciaId = parseUuidOrThrow(request.referenciaId, "referenciaId invalido.");
    UUID professionalId = parseUuidOrNull(request.professionalId);
    if (professionalId != null
        && profissionalRepository.findByIdAndTenantId(professionalId, tenantId).isEmpty()) {
      throw new IllegalArgumentException("Profissional nao encontrado.");
    }

    ComandaItem item = new ComandaItem();
    item.setTenantId(tenantId);
    item.setComandaId(comanda.getId());
    item.setTipo(request.tipo);
    item.setReferenciaId(referenciaId);
    item.setProfessionalId(professionalId);
    item.setQuantidade(request.quantidade != null ? request.quantidade : BigDecimal.ONE);

    if (ComandaItem.TIPO_SERVICO.equals(request.tipo)) {
      Servico servico =
          servicoRepository
              .findByIdAndTenantId(referenciaId, tenantId)
              .orElseThrow(() -> new ApiClientErrorException("Servico nao encontrado.", 404));
      item.setDescricao(servico.getName());
      item.setPrecoUnitario(
          request.precoUnitario != null ? request.precoUnitario : servico.getPrice());
    } else if (ComandaItem.TIPO_PRODUTO.equals(request.tipo)) {
      ItemEstoque produto =
          itemEstoqueRepository
              .findByIdAndTenantId(referenciaId, tenantId)
              .orElseThrow(
                  () -> new ApiClientErrorException("Produto nao encontrado no estoque.", 404));
      if (request.precoUnitario == null) {
        throw new IllegalArgumentException(
            "Preco de venda e obrigatorio para item do tipo PRODUTO.");
      }
      item.setDescricao(produto.getNome());
      item.setPrecoUnitario(request.precoUnitario);
    } else {
      ServicePackage pacote =
          servicePackageRepository
              .findByIdAndTenantId(referenciaId, tenantId)
              .orElseThrow(() -> new ApiClientErrorException("Pacote nao encontrado.", 404));
      item.setDescricao(pacote.getNome());
      item.setPrecoUnitario(
          request.precoUnitario != null ? request.precoUnitario : pacote.getPreco());
    }

    item.setTotal(
        item.getPrecoUnitario().multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP));
    comandaItemRepository.save(item);
    // O Panache emite o INSERT no persist(); o Spring Data adia ate o fim da transacao. Sem o
    // flush, o recalcular() abaixo releria a comanda sem o item recem-adicionado.
    comandaItemRepository.flush();

    recalcular(comanda);
    return obter(id);
  }

  @Transactional
  public ComandaDtos.ComandaResponse removerItem(UUID id, UUID itemId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Comanda comanda = buscarOuFalhar(id, tenantId);
    exigirAberta(comanda);

    ComandaItem item =
        comandaItemRepository
            .findByIdAndComandaId(itemId, comanda.getId())
            .orElseThrow(
                () -> new ApiClientErrorException("Item nao encontrado na comanda.", 404));
    comandaItemRepository.delete(item);
    comandaItemRepository.flush();

    recalcular(comanda);
    return obter(id);
  }

  @Transactional
  public ComandaDtos.ComandaResponse aplicarDesconto(
      UUID id, ComandaDtos.AplicarDescontoRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Comanda comanda = buscarOuFalhar(id, tenantId);
    exigirAberta(comanda);

    if (request.percentual.compareTo(new BigDecimal("100")) > 0) {
      throw new IllegalArgumentException("Desconto nao pode ser maior que 100%.");
    }
    List<ComandaItem> itens = comandaItemRepository.findByComandaIdOrderByCreatedAt(comanda.getId());
    BigDecimal subtotal = somarItens(itens);
    comanda.setDesconto(NumericUtil.percentOf(subtotal, request.percentual.doubleValue()));
    comanda.setDescontoMotivo(request.motivo.trim());
    comanda.setTotal(NumericUtil.maxZero(NumericUtil.subtract(subtotal, comanda.getDesconto())));

    return toResponse(
        comanda, itens, comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(comanda.getId()));
  }

  @Transactional
  public ComandaDtos.ComandaResponse registrarGorjeta(
      UUID id, ComandaDtos.RegistrarGorjetaRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Comanda comanda = buscarOuFalhar(id, tenantId);
    exigirAberta(comanda);

    UUID professionalId = parseUuidOrThrow(request.professionalId, "professionalId invalido.");
    if (profissionalRepository.findByIdAndTenantId(professionalId, tenantId).isEmpty()) {
      throw new IllegalArgumentException("Profissional nao encontrado.");
    }
    comanda.setGorjeta(request.valor);
    comanda.setGorjetaProfessionalId(professionalId);

    return toResponse(
        comanda,
        comandaItemRepository.findByComandaIdOrderByCreatedAt(comanda.getId()),
        comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(comanda.getId()));
  }

  @Transactional
  public ComandaDtos.ComandaResponse registrarPagamento(
      UUID id, ComandaDtos.RegistrarPagamentoRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Comanda comanda = buscarOuFalhar(id, tenantId);
    exigirAberta(comanda);

    ComandaPagamento pagamento = new ComandaPagamento();
    pagamento.setTenantId(tenantId);
    pagamento.setComandaId(comanda.getId());
    pagamento.setMeio(request.meio);
    pagamento.setValor(request.valor);
    pagamento.setRegistradoPor(obterUsuarioId());

    switch (request.meio) {
      case ComandaPagamento.MEIO_PIX_ASAAS -> registrarPagamentoPix(tenantId, comanda, pagamento);
      case ComandaPagamento.MEIO_CREDITO_SINAL ->
          registrarPagamentoCreditoSinal(tenantId, comanda, pagamento);
      default -> pagamento.setStatus(ComandaPagamento.STATUS_CONFIRMADO);
    }
    if (ComandaPagamento.STATUS_CONFIRMADO.equals(pagamento.getStatus())
        && pagamento.getPaidAt() == null) {
      pagamento.setPaidAt(Instant.now());
    }

    comandaPagamentoRepository.save(pagamento);
    comandaPagamentoRepository.flush();
    return obter(id);
  }

  private void registrarPagamentoPix(UUID tenantId, Comanda comanda, ComandaPagamento pagamento) {
    Cliente cliente = resolverClienteOuFalhar(tenantId, comanda);
    TenantAsaasChargeService.PixCharge charge =
        tenantAsaasChargeService.criarCobrancaPix(
            tenantId, cliente, pagamento.getValor(), "Comanda", "comanda:" + comanda.getId());
    pagamento.setAsaasPaymentId(charge.asaasPaymentId());
    pagamento.setPixPayload(charge.pixPayload());
    pagamento.setStatus(ComandaPagamento.STATUS_PENDENTE);
  }

  private void registrarPagamentoCreditoSinal(
      UUID tenantId, Comanda comanda, ComandaPagamento pagamento) {
    if (comanda.getAppointmentId() == null) {
      throw new IllegalArgumentException(
          "Credito de sinal exige comanda vinculada a um agendamento.");
    }
    AppointmentDeposit deposit =
        appointmentDepositRepository
            .findPaidUnusedByAppointmentId(comanda.getAppointmentId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Nao ha sinal pago disponivel para este agendamento."));
    BigDecimal valorDisponivel = NumericUtil.fromCents(deposit.getAmountCents());
    if (pagamento.getValor().compareTo(valorDisponivel) > 0) {
      throw new IllegalArgumentException(
          "Valor informado excede o sinal pago (" + valorDisponivel + ").");
    }
    deposit.setUsedInComandaId(comanda.getId());
    pagamento.setAppointmentDepositId(deposit.getId());
    pagamento.setStatus(ComandaPagamento.STATUS_CONFIRMADO);
  }

  private Cliente resolverClienteOuFalhar(UUID tenantId, Comanda comanda) {
    if (comanda.getClientId() == null) {
      throw new IllegalArgumentException(
          "Comanda sem cliente identificado: obrigatorio para pagamento via Pix.");
    }
    return clienteRepository
        .findByIdAndTenantId(comanda.getClientId(), tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Cliente nao encontrado.", 404));
  }

  /**
   * Fecha a comanda: exige quitacao exata (soma dos pagamentos CONFIRMADO == total + gorjeta),
   * rateia o desconto proporcionalmente entre os itens, gera uma {@link Transacao} de receita por
   * item (entra no fluxo de caixa/fechamento existente), baixa estoque dos itens PRODUTO e registra
   * comissao de PRODUTO quando ha profissional vinculado.
   *
   * <p>Para itens SERVICO, comissao e consumo de insumo so sao registrados aqui quando a comanda e
   * AVULSA (sem {@code appointmentId}): quando ha agendamento vinculado, esses efeitos ja acontecem
   * via {@code ServicoAgendamentos.atualizarStatus} ao concluir o atendimento — registrar de novo
   * aqui duplicaria comissao e baixa de estoque.
   *
   * <p>Nao transiciona o {@code Agendamento} vinculado (se houver) para COMPLETED — isso duplicaria
   * a receita.
   */
  @Transactional
  public ComandaDtos.ComandaResponse fechar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    // Lock pessimista: fechar lanca efeito colateral irreversivel (receita, baixa de estoque,
    // comissao) — sem isso, duas requisicoes concorrentes na mesma comanda (duplo clique, duas
    // abas) poderiam ambas passar por exigirAberta antes de qualquer uma comitar e duplicar a
    // venda inteira.
    Comanda comanda =
        comandaRepository
            .findByIdAndTenantParaAtualizacao(id, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Comanda nao encontrada.", 404));
    exigirAberta(comanda);

    List<ComandaItem> itens = comandaItemRepository.findByComandaIdOrderByCreatedAt(comanda.getId());
    if (itens.isEmpty()) {
      throw new IllegalArgumentException("Comanda sem itens nao pode ser fechada.");
    }

    List<ComandaPagamento> pagamentos =
        comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(comanda.getId());
    BigDecimal totalConfirmado =
        pagamentos.stream()
            .filter(p -> ComandaPagamento.STATUS_CONFIRMADO.equals(p.getStatus()))
            .map(ComandaPagamento::getValor)
            .reduce(BigDecimal.ZERO, NumericUtil::add);
    // O cliente paga servico + gorjeta juntos no caixa — a quitacao tem que cobrir os dois, mesmo
    // que a gorjeta nao componha o total (que segue sendo so a receita do salao).
    BigDecimal totalComGorjeta = NumericUtil.add(comanda.getTotal(), comanda.getGorjeta());
    if (totalConfirmado.compareTo(totalComGorjeta) != 0) {
      throw new IllegalArgumentException(
          "Comanda nao esta quitada: pago "
              + totalConfirmado
              + ", total com gorjeta "
              + totalComGorjeta
              + ".");
    }

    UUID categoriaVendasId = resolveTransactionCategoryId(tenantId, "VENDAS");
    MetodoPagamento metodoPagamento = resolveMetodoPagamentoRepresentativo(pagamentos);
    List<AlocacaoPagamento> carteiraPagamentos = construirCarteiraPagamentos(pagamentos);
    List<BigDecimal> valoresLiquidosPorItem =
        ratearDesconto(itens, comanda.getSubtotal(), comanda.getTotal());

    for (int i = 0; i < itens.size(); i++) {
      ComandaItem item = itens.get(i);
      BigDecimal valorLiquido = valoresLiquidosPorItem.get(i);
      if (NumericUtil.isZeroOrNegative(valorLiquido)) continue;

      // Rateia a receita do item pelos meios de pagamento reais da comanda (uma Transacao por
      // fatia) — uma comanda paga em mais de um meio (ex.: metade dinheiro, metade cartao) nao
      // pode ter a venda inteira atribuida a um unico meio, senao o caixa concilia errado por meio
      // de pagamento. A primeira fatia serve apenas como chave de dedup de comissao.
      Transacao transacao =
          criarTransacoesVendaRateadas(
                  tenantId, comanda, item, valorLiquido, categoriaVendasId, carteiraPagamentos)
              .get(0);

      if (ComandaItem.TIPO_PRODUTO.equals(item.getTipo())) {
        baixarEstoqueItem(item);
        if (item.getProfessionalId() != null) {
          commissionService.registerProductCommissionIfApplicable(
              tenantId,
              transacao.getId(),
              item.getProfessionalId(),
              item.getReferenciaId(),
              null,
              NumericUtil.toCents(valorLiquido),
              Instant.now(),
              transacao.getDescription());
        }
      } else if (ComandaItem.TIPO_PACOTE.equals(item.getTipo())) {
        criarSaldoDePacoteVendido(tenantId, comanda, item, valorLiquido);
      } else if (ComandaItem.TIPO_SERVICO.equals(item.getTipo())
          && comanda.getAppointmentId() == null) {
        // So para comanda AVULSA: quando ha agendamento vinculado, ServicoAgendamentos ja registra
        // comissao de servico e consome insumo ao concluir — duplicar aqui lancaria em dobro.
        estoqueMovimentacaoService.consumirInsumosPorItemComanda(
            tenantId, item.getId(), item.getReferenciaId());
        if (item.getProfessionalId() != null) {
          commissionService.registerServiceCommissionForComandaItemIfApplicable(
              tenantId,
              comanda.getId(),
              item.getId(),
              item.getProfessionalId(),
              item.getReferenciaId(),
              item.getTotal(),
              valorLiquido,
              Instant.now());
        }
      }
    }

    registrarGorjetaSeAplicavel(tenantId, comanda, metodoPagamento);
    comanda.setPontosFidelidadeCreditados(
        creditarPontosFidelidadeSeAplicavel(tenantId, comanda, itens, valoresLiquidosPorItem));

    comanda.setStatus(Comanda.STATUS_FECHADA);
    comanda.setClosedAt(Instant.now());
    comanda.setFechadaPor(obterUsuarioId());

    return toResponse(comanda, itens, pagamentos);
  }

  /**
   * Credita pontos de fidelidade sobre o valor liquido dos itens elegiveis, se o programa estiver
   * ativo e a comanda tiver cliente identificado. Devolve quantos pontos foram creditados (0 se
   * nenhum) — guardado em {@code comanda.pontosFidelidadeCreditados} para permitir reverter o valor
   * exato num estorno futuro.
   */
  private int creditarPontosFidelidadeSeAplicavel(
      UUID tenantId,
      Comanda comanda,
      List<ComandaItem> itens,
      List<BigDecimal> valoresLiquidosPorItem) {
    if (comanda.getClientId() == null) return 0;
    TenantLoyaltySettings config =
        tenantLoyaltySettingsRepository.findByTenantId(tenantId).orElse(null);
    if (config == null || !config.isAtivo()) return 0;

    BigDecimal valorElegivel = BigDecimal.ZERO;
    for (int i = 0; i < itens.size(); i++) {
      ComandaItem item = itens.get(i);
      boolean elegivel =
          ComandaItem.TIPO_SERVICO.equals(item.getTipo())
              || ComandaItem.TIPO_PACOTE.equals(item.getTipo())
              || (ComandaItem.TIPO_PRODUTO.equals(item.getTipo()) && config.isProdutosContam());
      if (elegivel) {
        valorElegivel = NumericUtil.add(valorElegivel, valoresLiquidosPorItem.get(i));
      }
    }
    if (NumericUtil.isZeroOrNegative(valorElegivel)) return 0;

    int pontos =
        valorElegivel
            .multiply(config.getPontosPorReal())
            .setScale(0, RoundingMode.FLOOR)
            .intValue();
    if (pontos <= 0) return 0;

    Cliente cliente = clienteRepository.findById(comanda.getClientId()).orElse(null);
    if (cliente != null) {
      cliente.setLoyaltyPoints(cliente.getLoyaltyPoints() + pontos);
    }
    return pontos;
  }

  /**
   * Registra a venda de um pacote: cria a compra do cliente e o saldo de sessoes por servico
   * incluido, multiplicando as sessoes do catalogo pela quantidade vendida na comanda.
   */
  private void criarSaldoDePacoteVendido(
      UUID tenantId, Comanda comanda, ComandaItem item, BigDecimal valorLiquido) {
    ClientPackagePurchase compra = new ClientPackagePurchase();
    compra.setTenantId(tenantId);
    compra.setClientId(comanda.getClientId());
    compra.setPackageId(item.getReferenciaId());
    compra.setPackageNome(item.getDescricao());
    compra.setPrecoPago(valorLiquido);
    compra.setComandaId(comanda.getId());
    clientPackagePurchaseRepository.save(compra);

    int multiplicador = item.getQuantidade().setScale(0, RoundingMode.CEILING).intValue();
    for (ServicePackageItem pacoteItem :
        servicePackageItemRepository.findByPackageId(item.getReferenciaId())) {
      ClientPackageBalance saldo = new ClientPackageBalance();
      saldo.setTenantId(tenantId);
      saldo.setPurchaseId(compra.getId());
      saldo.setServiceId(pacoteItem.getServiceId());
      saldo.setServiceNome(
          servicoRepository.findById(pacoteItem.getServiceId()).map(Servico::getName).orElse(null));
      saldo.setSessoesTotais(pacoteItem.getSessoes() * Math.max(multiplicador, 1));
      clientPackageBalanceRepository.save(saldo);
    }
  }

  /**
   * Distribui o desconto total proporcionalmente ao valor de cada item, com a diferenca de
   * arredondamento absorvida pelo ultimo item.
   */
  List<BigDecimal> ratearDesconto(List<ComandaItem> itens, BigDecimal subtotal, BigDecimal total) {
    List<BigDecimal> valores = new ArrayList<>();
    if (NumericUtil.isZeroOrNegative(subtotal)) {
      itens.forEach(item -> valores.add(BigDecimal.ZERO));
      return valores;
    }
    BigDecimal fator = total.divide(subtotal, 6, RoundingMode.HALF_UP);
    BigDecimal acumulado = BigDecimal.ZERO;
    for (int i = 0; i < itens.size(); i++) {
      if (i == itens.size() - 1) {
        valores.add(NumericUtil.maxZero(NumericUtil.subtract(total, acumulado)));
        break;
      }
      BigDecimal valor = itens.get(i).getTotal().multiply(fator).setScale(2, RoundingMode.HALF_UP);
      valores.add(valor);
      acumulado = NumericUtil.add(acumulado, valor);
    }
    return valores;
  }

  /**
   * Fatia de saldo ainda disponivel de um pagamento confirmado, consumida pelo rateio das
   * {@link Transacao} de venda entre os itens (ver {@link #criarTransacoesVendaRateadas}).
   */
  private static final class AlocacaoPagamento {
    final MetodoPagamento metodo;
    BigDecimal restante;

    AlocacaoPagamento(MetodoPagamento metodo, BigDecimal restante) {
      this.metodo = metodo;
      this.restante = restante;
    }
  }

  private List<AlocacaoPagamento> construirCarteiraPagamentos(List<ComandaPagamento> pagamentos) {
    List<AlocacaoPagamento> carteira = new ArrayList<>();
    for (ComandaPagamento pagamento : pagamentos) {
      if (!ComandaPagamento.STATUS_CONFIRMADO.equals(pagamento.getStatus())) continue;
      if (NumericUtil.isZeroOrNegative(pagamento.getValor())) continue;
      carteira.add(
          new AlocacaoPagamento(mapearMetodoPagamento(pagamento.getMeio()), pagamento.getValor()));
    }
    return carteira;
  }

  private MetodoPagamento mapearMetodoPagamento(String meio) {
    return switch (meio) {
      case ComandaPagamento.MEIO_DINHEIRO -> MetodoPagamento.CASH;
      case ComandaPagamento.MEIO_PIX_ASAAS -> MetodoPagamento.PIX;
      case ComandaPagamento.MEIO_CARTAO_CREDITO_EXTERNO -> MetodoPagamento.CREDIT_CARD;
      case ComandaPagamento.MEIO_CARTAO_DEBITO_EXTERNO -> MetodoPagamento.DEBIT_CARD;
      default -> MetodoPagamento.OTHER;
    };
  }

  /**
   * Cria uma {@link Transacao} de venda por fatia do item consumida de cada meio de pagamento
   * confirmado da comanda, na ordem em que os pagamentos foram registrados, ate cobrir o valor
   * liquido do item. <b>Muta</b> a {@code carteira} recebida (consome o saldo restante de cada
   * alocacao), entao deve ser chamada sequencialmente para todos os itens da mesma comanda usando a
   * MESMA lista. Comissao/estoque/pacote continuam calculados uma unica vez por item, com o valor
   * liquido total — o rateio afeta somente quantas Transacao representam esse item e o meio de
   * pagamento de cada uma.
   */
  private List<Transacao> criarTransacoesVendaRateadas(
      UUID tenantId,
      Comanda comanda,
      ComandaItem item,
      BigDecimal valorItem,
      UUID categoriaId,
      List<AlocacaoPagamento> carteira) {
    List<Transacao> criadas = new ArrayList<>();
    BigDecimal restanteItem = valorItem;
    for (AlocacaoPagamento alocacao : carteira) {
      if (restanteItem.compareTo(BigDecimal.ZERO) <= 0) break;
      if (NumericUtil.isZeroOrNegative(alocacao.restante)) continue;

      BigDecimal parcela = alocacao.restante.min(restanteItem);
      criadas.add(criarTransacaoVenda(tenantId, comanda, item, parcela, categoriaId, alocacao.metodo));
      alocacao.restante = NumericUtil.subtract(alocacao.restante, parcela);
      restanteItem = NumericUtil.subtract(restanteItem, parcela);
    }
    if (criadas.isEmpty()) {
      // Guarda de seguranca: nao deveria acontecer (fechar ja exige quitacao exata antes de chegar
      // aqui), mas evita perder a receita do item se a carteira estiver vazia.
      criadas.add(
          criarTransacaoVenda(
              tenantId, comanda, item, valorItem, categoriaId, MetodoPagamento.OTHER));
    }
    return criadas;
  }

  private Transacao criarTransacaoVenda(
      UUID tenantId,
      Comanda comanda,
      ComandaItem item,
      BigDecimal valor,
      UUID categoriaId,
      MetodoPagamento metodo) {
    Transacao transacao = new Transacao();
    transacao.setTenantId(tenantId);
    transacao.setComandaId(comanda.getId());
    transacao.setAppointmentId(comanda.getAppointmentId());
    transacao.setProfessionalId(item.getProfessionalId());
    transacao.setStockItemId(
        ComandaItem.TIPO_PRODUTO.equals(item.getTipo()) ? item.getReferenciaId() : null);
    transacao.setType(TipoTransacao.INCOME);
    transacao.setCategoryId(categoriaId);
    transacao.setDescription("Venda comanda - " + item.getDescricao());
    transacao.setAmount(valor);
    transacao.setPaymentMethod(metodo);
    transacao.setDate(Instant.now());
    transacaoRepository.save(transacao);
    return transacao;
  }

  /**
   * Lanca a gorjeta cobrada junto com o servico: uma receita de entrada (o dinheiro entrou no
   * caixa) e uma despesa de repasse no mesmo valor (o dinheiro e devido ao profissional) — efeito
   * liquido zero na receita do salao, mas com rastro auditavel de quanto e para quem, em vez de um
   * numero solto sem lancamento nenhum.
   */
  private void registrarGorjetaSeAplicavel(
      UUID tenantId, Comanda comanda, MetodoPagamento metodo) {
    if (NumericUtil.isZeroOrNegative(comanda.getGorjeta())
        || comanda.getGorjetaProfessionalId() == null) {
      return;
    }

    UUID categoriaGorjetaId = resolveTransactionCategoryId(tenantId, "GORJETAS");

    Transacao entrada = new Transacao();
    entrada.setTenantId(tenantId);
    entrada.setComandaId(comanda.getId());
    entrada.setAppointmentId(comanda.getAppointmentId());
    entrada.setProfessionalId(comanda.getGorjetaProfessionalId());
    entrada.setType(TipoTransacao.INCOME);
    entrada.setCategoryId(categoriaGorjetaId);
    entrada.setDescription("Gorjeta recebida - comanda");
    entrada.setAmount(comanda.getGorjeta());
    entrada.setPaymentMethod(metodo);
    entrada.setDate(Instant.now());
    transacaoRepository.save(entrada);

    Transacao repasse = new Transacao();
    repasse.setTenantId(tenantId);
    repasse.setComandaId(comanda.getId());
    repasse.setAppointmentId(comanda.getAppointmentId());
    repasse.setProfessionalId(comanda.getGorjetaProfessionalId());
    repasse.setType(TipoTransacao.EXPENSE);
    repasse.setCategoryId(categoriaGorjetaId);
    repasse.setDescription("Repasse de gorjeta ao profissional");
    repasse.setAmount(comanda.getGorjeta());
    repasse.setPaymentMethod(metodo);
    repasse.setDate(Instant.now());
    transacaoRepository.save(repasse);
  }

  private void baixarEstoqueItem(ComandaItem item) {
    estoqueMovimentacaoService.criarMovimentacao(
        item.getReferenciaId(), "SAIDA", item.getQuantidade(), "Venda em comanda");
  }

  private UUID resolveTransactionCategoryId(UUID tenantId, String categoryName) {
    return transactionCategoryRepository
        .findByTenantAndName(tenantId, categoryName)
        .map(TransactionCategory::getId)
        .orElseGet(
            () -> {
              TransactionCategory category = new TransactionCategory();
              category.setTenantId(tenantId);
              category.setName(categoryName);
              transactionCategoryRepository.save(category);
              return category.getId();
            });
  }

  private MetodoPagamento resolveMetodoPagamentoRepresentativo(List<ComandaPagamento> pagamentos) {
    return pagamentos.stream()
        .filter(p -> ComandaPagamento.STATUS_CONFIRMADO.equals(p.getStatus()))
        .findFirst()
        .map(p -> mapearMetodoPagamento(p.getMeio()))
        .orElse(MetodoPagamento.OTHER);
  }

  @Transactional
  public ComandaDtos.ComandaResponse cancelar(
      UUID id, ComandaDtos.CancelarComandaRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Comanda comanda = buscarOuFalhar(id, tenantId);
    exigirAberta(comanda);

    List<ComandaPagamento> pagamentos =
        comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(comanda.getId());
    for (ComandaPagamento pagamento : pagamentos) {
      if (ComandaPagamento.MEIO_PIX_ASAAS.equals(pagamento.getMeio())
          && ComandaPagamento.STATUS_PENDENTE.equals(pagamento.getStatus())
          && pagamento.getAsaasPaymentId() != null) {
        cancelarCobrancaPixMelhorEsforco(tenantId, pagamento);
      }
      if (ComandaPagamento.MEIO_CREDITO_SINAL.equals(pagamento.getMeio())
          && pagamento.getAppointmentDepositId() != null) {
        appointmentDepositRepository
            .findById(pagamento.getAppointmentDepositId())
            .filter(deposit -> comanda.getId().equals(deposit.getUsedInComandaId()))
            .ifPresent(deposit -> deposit.setUsedInComandaId(null));
      }
    }

    comanda.setStatus(Comanda.STATUS_CANCELADA);
    comanda.setCancelMotivo(request.motivo.trim());
    comanda.setClosedAt(Instant.now());

    return toResponse(
        comanda,
        comandaItemRepository.findByComandaIdOrderByCreatedAt(comanda.getId()),
        pagamentos);
  }

  /**
   * Estorna uma comanda ja FECHADA: reverte pagamento (Transacao de venda/gorjeta, com soft delete
   * — mesmo padrao de {@code ServicoFinanceiro.deletar}, preserva o lancamento original para
   * auditoria), devolve estoque de itens PRODUTO e insumo consumido por itens SERVICO, reverte a
   * comissao gerada por cada item e o credito de fidelidade daquela venda (usa o numero exato de
   * pontos guardado no fechamento, nao recalcula), e remove pacote/saldo vendido junto. Nao existe
   * caminho de volta: uma comanda ESTORNADA fica assim para sempre.
   */
  @Transactional
  public ComandaDtos.ComandaResponse estornar(
      UUID id, ComandaDtos.EstornarComandaRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Comanda comanda =
        comandaRepository
            .findByIdAndTenantParaAtualizacao(id, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Comanda nao encontrada.", 404));
    exigirFechada(comanda);
    String motivo = request.motivo.trim();

    List<ComandaItem> itens = comandaItemRepository.findByComandaIdOrderByCreatedAt(comanda.getId());
    List<Transacao> transacoesAtivas =
        transacaoRepository.listarAtivasPorComanda(tenantId, comanda.getId());
    Instant agora = Instant.now();
    UUID usuarioId = obterUsuarioId();

    for (Transacao transacao : transacoesAtivas) {
      transacao.setDeletedAt(agora);
      transacao.setDeletedBy(usuarioId);
      // A comissao de PRODUTO usa a Transacao da venda como chave de origem — tenta reverter para
      // cada uma; a chamada e um no-op seguro quando a transacao nao originou comissao nenhuma
      // (gorjeta, itens sem profissional, etc.).
      commissionService.reverseEntryForOrigin(tenantId, "PRODUCT", transacao.getId(), motivo);
    }

    for (ComandaItem item : itens) {
      if (ComandaItem.TIPO_PRODUTO.equals(item.getTipo())) {
        devolverEstoqueProduto(item, motivo);
      } else if (ComandaItem.TIPO_SERVICO.equals(item.getTipo())) {
        // Comissao de SERVICO avulso usa o proprio item como chave de origem (ver fechar()).
        commissionService.reverseEntryForOrigin(tenantId, "SERVICE", item.getId(), motivo);
        reverterConsumoInsumoItem(tenantId, item, motivo);
      }
    }

    if (comanda.getPontosFidelidadeCreditados() > 0 && comanda.getClientId() != null) {
      clienteRepository
          .findByIdAndTenantId(comanda.getClientId(), tenantId)
          .ifPresent(
              cliente ->
                  cliente.setLoyaltyPoints(
                      Math.max(
                          0,
                          cliente.getLoyaltyPoints() - comanda.getPontosFidelidadeCreditados())));
    }

    List<ClientPackagePurchase> comprasPacote =
        clientPackagePurchaseRepository.findByTenantIdAndComandaId(tenantId, comanda.getId());
    for (ClientPackagePurchase compra : comprasPacote) {
      for (ClientPackageBalance saldo :
          clientPackageBalanceRepository.findByPurchaseId(compra.getId())) {
        clientPackageBalanceRepository.delete(saldo);
      }
      clientPackagePurchaseRepository.delete(compra);
    }

    comanda.setStatus(Comanda.STATUS_ESTORNADA);
    comanda.setEstornadoPor(usuarioId);
    comanda.setEstornadoEm(agora);
    comanda.setEstornoMotivo(motivo);

    return toResponse(
        comanda,
        itens,
        comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(comanda.getId()));
  }

  private void devolverEstoqueProduto(ComandaItem item, String motivo) {
    estoqueMovimentacaoService.criarMovimentacao(
        item.getReferenciaId(), "ENTRADA", item.getQuantidade(), "Estorno de comanda: " + motivo);
  }

  private void reverterConsumoInsumoItem(UUID tenantId, ComandaItem item, String motivo) {
    for (MovimentacaoEstoque movimentacao :
        movimentacaoEstoqueRepository.findByTenantIdAndComandaItemId(tenantId, item.getId())) {
      if (movimentacao.getTipo() != TipoMovimentacaoEstoque.SAIDA) continue;
      estoqueMovimentacaoService.criarMovimentacao(
          movimentacao.getItemEstoqueId(),
          "ENTRADA",
          movimentacao.getQuantidade(),
          "Estorno de comanda: " + motivo);
    }
  }

  private void cancelarCobrancaPixMelhorEsforco(UUID tenantId, ComandaPagamento pagamento) {
    try {
      String apiKey = tenantAsaasChargeService.resolveApiKeyAtivaOuFalhar(tenantId);
      asaasClient.cancelPayment(apiKey, pagamento.getAsaasPaymentId());
    } catch (Exception ignored) {
      // melhor esforco: nao bloqueia o cancelamento da comanda por falha no Asaas
    }
  }

  @Transactional
  public ComandaDtos.ComandaResponse resgatarFidelidade(
      UUID id, ComandaDtos.ResgatarFidelidadeRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Comanda comanda = buscarOuFalhar(id, tenantId);
    exigirAberta(comanda);

    if (comanda.getClientId() == null) {
      throw new IllegalArgumentException(
          "Resgate de fidelidade exige comanda com cliente identificado.");
    }
    TenantLoyaltySettings config =
        tenantLoyaltySettingsRepository.findByTenantId(tenantId).orElse(null);
    if (config == null || !config.isAtivo()) {
      throw new IllegalArgumentException("Programa de fidelidade nao esta ativo.");
    }
    Cliente cliente =
        clienteRepository
            .findByIdAndTenantId(comanda.getClientId(), tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Cliente nao encontrado.", 404));
    if (cliente.getLoyaltyPoints() < request.pontos) {
      throw new IllegalArgumentException(
          "Cliente nao possui pontos suficientes (saldo: " + cliente.getLoyaltyPoints() + ").");
    }

    BigDecimal valorResgate =
        BigDecimal.valueOf(request.pontos)
            .divide(config.getPontosPorResgateReal(), 2, RoundingMode.HALF_UP);
    List<ComandaItem> itens = comandaItemRepository.findByComandaIdOrderByCreatedAt(comanda.getId());
    BigDecimal subtotal = somarItens(itens);
    comanda.setDesconto(
        NumericUtil.maxZero(NumericUtil.add(comanda.getDesconto(), valorResgate)));
    if (comanda.getDesconto().compareTo(subtotal) > 0) comanda.setDesconto(subtotal);
    comanda.setDescontoMotivo(
        concatenarMotivo(
            comanda.getDescontoMotivo(),
            "Resgate de " + request.pontos + " pontos de fidelidade"));
    comanda.setTotal(NumericUtil.maxZero(NumericUtil.subtract(subtotal, comanda.getDesconto())));

    cliente.setLoyaltyPoints(cliente.getLoyaltyPoints() - request.pontos);

    return obter(id);
  }

  private String concatenarMotivo(String motivoAtual, String novoMotivo) {
    if (motivoAtual == null || motivoAtual.isBlank()) return novoMotivo;
    return motivoAtual + "; " + novoMotivo;
  }

  Comanda buscarOuFalhar(UUID id, UUID tenantId) {
    return comandaRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Comanda nao encontrada.", 404));
  }

  void exigirAberta(Comanda comanda) {
    if (!Comanda.STATUS_ABERTA.equals(comanda.getStatus())) {
      throw new IllegalArgumentException("Comanda nao esta aberta.");
    }
  }

  void exigirFechada(Comanda comanda) {
    if (!Comanda.STATUS_FECHADA.equals(comanda.getStatus())) {
      throw new IllegalArgumentException("Comanda nao esta fechada.");
    }
  }

  private void recalcular(Comanda comanda) {
    List<ComandaItem> itens = comandaItemRepository.findByComandaIdOrderByCreatedAt(comanda.getId());
    BigDecimal subtotal = somarItens(itens);
    comanda.setSubtotal(subtotal);
    // Reaplica o desconto ja registrado (em valor, nao percentual) sobre o novo subtotal, mas nunca
    // deixa o desconto exceder o subtotal apos a mudanca de itens.
    if (comanda.getDesconto().compareTo(subtotal) > 0) {
      comanda.setDesconto(subtotal);
    }
    comanda.setTotal(NumericUtil.maxZero(NumericUtil.subtract(subtotal, comanda.getDesconto())));
  }

  private BigDecimal somarItens(List<ComandaItem> itens) {
    BigDecimal subtotal = BigDecimal.ZERO;
    for (ComandaItem item : itens) {
      subtotal = NumericUtil.add(subtotal, item.getTotal());
    }
    return subtotal.setScale(2, RoundingMode.HALF_UP);
  }

  private ComandaDtos.ComandaResponse toResponse(
      Comanda comanda, List<ComandaItem> itens, List<ComandaPagamento> pagamentos) {
    ComandaDtos.ComandaResponse r = new ComandaDtos.ComandaResponse();
    r.id = comanda.getId().toString();
    r.appointmentId =
        comanda.getAppointmentId() != null ? comanda.getAppointmentId().toString() : null;
    r.clientId = comanda.getClientId() != null ? comanda.getClientId().toString() : null;
    r.status = comanda.getStatus();
    r.subtotal = comanda.getSubtotal();
    r.desconto = comanda.getDesconto();
    r.descontoMotivo = comanda.getDescontoMotivo();
    r.gorjeta = comanda.getGorjeta();
    r.gorjetaProfessionalId =
        comanda.getGorjetaProfessionalId() != null
            ? comanda.getGorjetaProfessionalId().toString()
            : null;
    r.total = comanda.getTotal();
    r.cancelMotivo = comanda.getCancelMotivo();
    r.estornoMotivo = comanda.getEstornoMotivo();
    r.estornadoEm = comanda.getEstornadoEm() != null ? comanda.getEstornadoEm().toString() : null;
    r.openedAt = comanda.getOpenedAt() != null ? comanda.getOpenedAt().toString() : null;
    r.closedAt = comanda.getClosedAt() != null ? comanda.getClosedAt().toString() : null;
    r.itens = itens.stream().map(this::toItemResponse).toList();
    r.pagamentos = pagamentos.stream().map(this::toPagamentoResponse).toList();
    return r;
  }

  private ComandaDtos.ComandaItemResponse toItemResponse(ComandaItem item) {
    ComandaDtos.ComandaItemResponse r = new ComandaDtos.ComandaItemResponse();
    r.id = item.getId().toString();
    r.tipo = item.getTipo();
    r.referenciaId = item.getReferenciaId().toString();
    r.descricao = item.getDescricao();
    r.professionalId =
        item.getProfessionalId() != null ? item.getProfessionalId().toString() : null;
    r.quantidade = item.getQuantidade();
    r.precoUnitario = item.getPrecoUnitario();
    r.total = item.getTotal();
    return r;
  }

  private ComandaDtos.ComandaPagamentoResponse toPagamentoResponse(ComandaPagamento pagamento) {
    ComandaDtos.ComandaPagamentoResponse r = new ComandaDtos.ComandaPagamentoResponse();
    r.id = pagamento.getId().toString();
    r.meio = pagamento.getMeio();
    r.valor = pagamento.getValor();
    r.status = pagamento.getStatus();
    r.pixPayload = pagamento.getPixPayload();
    r.paidAt = pagamento.getPaidAt() != null ? pagamento.getPaidAt().toString() : null;
    return r;
  }

  /** Mesma semantica do {@code obterUsuarioId()} original: nunca lanca, devolve null sem token. */
  UUID obterUsuarioId() {
    return authenticatedUser.idOuNulo();
  }

  private UUID parseUuidOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private UUID parseUuidOrThrow(String value, String message) {
    try {
      return UUID.fromString(value.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException(message);
    }
  }
}
