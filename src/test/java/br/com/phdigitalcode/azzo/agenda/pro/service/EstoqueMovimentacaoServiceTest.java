package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.MovimentacaoEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.MovimentacaoEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.TransacaoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.TransacaoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueConfiguracao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicoInsumo;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.OrigemMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueConfiguracaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentacaoEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoInsumoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre o motor de movimentacao de estoque — os tres metodos de
 * {@code modules/inventory/application/ServicoEstoque.java} que substituiram o placeholder
 * {@code integration/EstoqueMovimentacaoService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EstoqueMovimentacaoServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID SERVICE_ID = UUID.randomUUID();
  private static final UUID APPOINTMENT_ID = UUID.randomUUID();
  private static final UUID COMANDA_ITEM_ID = UUID.randomUUID();
  private static final UUID USUARIO_ID = UUID.randomUUID();
  private static final UUID TRANSACAO_ID = UUID.randomUUID();

  @Mock private ItemEstoqueRepository itemEstoqueRepository;
  @Mock private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
  @Mock private EstoqueConfiguracaoRepository estoqueConfiguracaoRepository;
  @Mock private ServicoInsumoRepository servicoInsumoRepository;
  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private AuditService auditService;
  @Mock private ServicoFinanceiro servicoFinanceiro;
  @Mock private NotificationPublisher notificationPublisher;

  private EstoqueMovimentacaoService service;

  @BeforeEach
  void setUp() {
    service =
        new EstoqueMovimentacaoService(
            itemEstoqueRepository,
            movimentacaoEstoqueRepository,
            estoqueConfiguracaoRepository,
            servicoInsumoRepository,
            contextoTenant,
            authenticatedUser,
            auditService,
            servicoFinanceiro,
            notificationPublisher);
    TransacaoResponse transacao = new TransacaoResponse();
    transacao.id = TRANSACAO_ID.toString();
    when(servicoFinanceiro.criar(any())).thenReturn(transacao);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(authenticatedUser.idOuNulo()).thenReturn(USUARIO_ID);
    when(authenticatedUser.roleOuNulo()).thenReturn("OWNER");
    when(movimentacaoEstoqueRepository.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              MovimentacaoEstoque movimentacao = invocation.getArgument(0);
              if (movimentacao.getId() == null) movimentacao.setId(UUID.randomUUID());
              if (movimentacao.getCreatedAt() == null) movimentacao.setCreatedAt(Instant.now());
              return movimentacao;
            });
  }

  private ItemEstoque item(String saldo) {
    ItemEstoque item = new ItemEstoque();
    item.setId(ITEM_ID);
    item.setTenantId(TENANT_ID);
    item.setNome("Shampoo 1L");
    item.setUnidadeMedida("UN");
    item.setSaldoAtual(new BigDecimal(saldo));
    item.setEstoqueMinimo(new BigDecimal("2"));
    item.setAtivo(true);
    return item;
  }

  private ServicoInsumo insumo(String quantidade, String percentualPerda) {
    ServicoInsumo insumo = new ServicoInsumo();
    insumo.setId(UUID.randomUUID());
    insumo.setTenantId(TENANT_ID);
    insumo.setServiceId(SERVICE_ID);
    insumo.setItemEstoqueId(ITEM_ID);
    insumo.setQuantidadeConsumo(new BigDecimal(quantidade));
    insumo.setPercentualPerda(new BigDecimal(percentualPerda));
    insumo.setAtivo(true);
    return insumo;
  }

  private EstoqueConfiguracao configuracao(Boolean bloquear, Boolean alertaMinimo) {
    EstoqueConfiguracao cfg = new EstoqueConfiguracao();
    cfg.setTenantId(TENANT_ID);
    cfg.setBloquearSaidaSemSaldo(bloquear);
    cfg.setAlertaEstoqueMinimoAtivo(alertaMinimo);
    return cfg;
  }

  private MovimentacaoEstoque movimentacaoPersistida() {
    ArgumentCaptor<MovimentacaoEstoque> captor =
        ArgumentCaptor.forClass(MovimentacaoEstoque.class);
    verify(movimentacaoEstoqueRepository).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  // ─── criarMovimentacao(MovimentacaoEstoqueRequest): a forma completa, do endpoint HTTP ────

  @Test
  void entradaComValorUnitarioSobrescreveOCustoMedioDoItem() {
    ItemEstoque item = item("10");
    item.setCustoMedioUnitario(new BigDecimal("4.00"));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));

    MovimentacaoEstoqueRequest request = request("ENTRADA", "5", "Compra de fornecedor");
    request.valorUnitarioPago = new BigDecimal("6.50");
    request.origem = " compra ";
    request.gerarLancamentoFinanceiro = Boolean.TRUE;

    MovimentacaoEstoqueResponse response = service.criarMovimentacao(request);

    // Medio ponderado, e nao o ultimo preco: (10 x 4,00 + 5 x 6,50) / 15 = 4,8333.
    assertThat(item.getCustoMedioUnitario()).isEqualByComparingTo("4.8333");
    assertThat(item.getSaldoAtual()).isEqualByComparingTo("15");
    // O lancamento financeiro vira despesa de verdade, com o total da compra.
    ArgumentCaptor<TransacaoRequest> despesa = ArgumentCaptor.forClass(TransacaoRequest.class);
    verify(servicoFinanceiro).criar(despesa.capture());
    assertThat(despesa.getValue().type).isEqualTo("EXPENSE");
    assertThat(despesa.getValue().amount).isEqualByComparingTo("32.50");
    assertThat(despesa.getValue().category).isEqualTo("Compra de estoque");
    assertThat(despesa.getValue().paymentMethod).isEqualTo("OTHER");
    assertThat(response.transacaoFinanceiraId).isEqualTo(TRANSACAO_ID.toString());
    MovimentacaoEstoque persistida = movimentacaoPersistida();
    assertThat(persistida.getValorUnitarioPago()).isEqualByComparingTo("6.50");
    // 5 x 6,50
    assertThat(persistida.getValorTotalMovimentacao()).isEqualByComparingTo("32.50");
    assertThat(persistida.getOrigem()).isEqualTo(OrigemMovimentacaoEstoque.COMPRA);
    assertThat(persistida.getGerarLancamentoFinanceiro()).isTrue();
    assertThat(response.itemNome).isEqualTo("Shampoo 1L");
  }

  @Test
  void saidaComValorUnitarioNaoMexeNoCustoMedio() {
    ItemEstoque item = item("10");
    item.setCustoMedioUnitario(new BigDecimal("4.00"));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

    MovimentacaoEstoqueRequest request = request("SAIDA", "2", "Perda");
    request.valorUnitarioPago = new BigDecimal("9.99");

    service.criarMovimentacao(request);

    // O original so sobrescreve o custo medio no ramo ENTRADA.
    assertThat(item.getCustoMedioUnitario()).isEqualByComparingTo("4.00");
  }

  @Test
  void requestSemOrigemNemFlagFinanceiroCaiNosDefaults() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("10")));

    service.criarMovimentacao(request("ENTRADA", "1", "Ajuste"));

    MovimentacaoEstoque persistida = movimentacaoPersistida();
    assertThat(persistida.getOrigem()).isEqualTo(OrigemMovimentacaoEstoque.MANUAL);
    assertThat(persistida.getGerarLancamentoFinanceiro()).isFalse();
    assertThat(persistida.getValorUnitarioPago()).isNull();
    assertThat(persistida.getValorTotalMovimentacao()).isNull();
  }

  @Test
  void origemDesconhecidaFalhaCom400() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("10")));

    MovimentacaoEstoqueRequest request = request("ENTRADA", "1", "Ajuste");
    request.origem = "TELEPATIA";

    assertThatThrownBy(() -> service.criarMovimentacao(request))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Origem de movimentacao invalida")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  private MovimentacaoEstoqueRequest request(String tipo, String quantidade, String motivo) {
    MovimentacaoEstoqueRequest request = new MovimentacaoEstoqueRequest();
    request.itemEstoqueId = ITEM_ID.toString();
    request.tipo = tipo;
    request.quantidade = new BigDecimal(quantidade);
    request.motivo = motivo;
    return request;
  }

  // ─── criarMovimentacao ────────────────────────────────────────────────────

  @Test
  void saidaBaixaOSaldoERegistraAMovimentacao() {
    ItemEstoque item = item("10");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

    MovimentacaoEstoqueResponse response =
        service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("3"), "Venda em comanda");

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("7");
    MovimentacaoEstoque persistida = movimentacaoPersistida();
    assertThat(persistida.getTipo()).isEqualTo(TipoMovimentacaoEstoque.SAIDA);
    assertThat(persistida.getSaldoAnterior()).isEqualByComparingTo("10");
    assertThat(persistida.getSaldoPosterior()).isEqualByComparingTo("7");
    // A forma de 4 argumentos e a da comanda: venda, e nao baixa manual — nao conta como perda.
    assertThat(persistida.getOrigem()).isEqualTo(OrigemMovimentacaoEstoque.VENDA);
    assertThat(persistida.getGerarLancamentoFinanceiro()).isFalse();
    assertThat(persistida.getUsuarioId()).isEqualTo(USUARIO_ID);
    assertThat(persistida.getValorTotalMovimentacao()).isNull();
    verify(itemEstoqueRepository).save(item);
    assertThat(response.tipo).isEqualTo("SAIDA");
    assertThat(response.itemNome).isEqualTo("Shampoo 1L");
    assertThat(response.saldoPosterior).isEqualByComparingTo("7");
  }

  @Test
  void entradaDevolveOSaldo() {
    ItemEstoque item = item("4");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));

    service.criarMovimentacao(ITEM_ID, "ENTRADA", new BigDecimal("3"), "Estorno de comanda: erro");

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("7");
    assertThat(movimentacaoPersistida().getTipo()).isEqualTo(TipoMovimentacaoEstoque.ENTRADA);
    // ENTRADA nunca consulta a configuracao de bloqueio.
    verifyNoInteractions(estoqueConfiguracaoRepository);
  }

  @Test
  void saidaSemSaldoEBloqueadaComConflito() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("1")));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao(true, false)));

    assertThatThrownBy(
            () -> service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("5"), "Venda"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Saldo insuficiente para movimentacao.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(409);

    verify(movimentacaoEstoqueRepository, never()).saveAndFlush(any());
  }

  /** Sem linha de configuracao o bloqueio conta como ativo — comportamento do original. */
  @Test
  void semConfiguracaoOBloqueioValeComoAtivo() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("1")));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("5"), "Venda"))
        .isInstanceOf(ApiClientErrorException.class);
  }

  @Test
  void saldoNegativoEPermitidoQuandoOBloqueioEstaDesligado() {
    ItemEstoque item = item("1");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao(false, false)));

    service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("5"), "Venda");

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("-4");
  }

  @Test
  void itemDeOutroTenantDa404() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("1"), "Venda"))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void tipoEmBrancoDa400() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("10")));

    assertThatThrownBy(() -> service.criarMovimentacao(ITEM_ID, "  ", new BigDecimal("1"), "Venda"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Tipo de movimentacao obrigatorio.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  @Test
  void tipoDesconhecidoDa400() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("10")));

    assertThatThrownBy(
            () -> service.criarMovimentacao(ITEM_ID, "TRANSFERENCIA", new BigDecimal("1"), "Venda"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Tipo de movimentacao invalido.");
  }

  /** {@code normalizarCodigoObrigatorio} faz upper + trim: "saida" e um tipo valido. */
  @Test
  void tipoEmMinusculoComEspacoEAceito() {
    ItemEstoque item = item("10");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));

    service.criarMovimentacao(ITEM_ID, "  saida ", new BigDecimal("1"), "Venda");

    assertThat(movimentacaoPersistida().getTipo()).isEqualTo(TipoMovimentacaoEstoque.SAIDA);
  }

  @Test
  void motivoEmBrancoDa400() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("10")));

    assertThatThrownBy(
            () -> service.criarMovimentacao(ITEM_ID, "ENTRADA", new BigDecimal("1"), "   "))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Motivo obrigatorio.");
  }

  @Test
  void motivoEColapsadoEmEspacoSimples() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("10")));

    service.criarMovimentacao(ITEM_ID, "ENTRADA", new BigDecimal("1"), "  Venda   em   comanda  ");

    assertThat(movimentacaoPersistida().getMotivo()).isEqualTo("Venda em comanda");
  }

  @Test
  void auditaCriacaoComEstadoAntesEDepoisDoItem() {
    ItemEstoque item = item("10");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));

    service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("3"), "Venda");

    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    AuditEventCommand command = captor.getValue();
    assertThat(command.module).isEqualTo("INVENTORY");
    assertThat(command.action).isEqualTo("STOCK_MOVEMENT_CREATE");
    assertThat(command.entityType).isEqualTo("STOCK_MOVEMENT");
    assertThat(command.actorUserId).isEqualTo(USUARIO_ID);
    assertThat(command.before).isNull();
    assertThat(command.metadata).isNotNull();
  }

  @Test
  void falhaDaAuditoriaNaoDerrubaAMovimentacao() {
    ItemEstoque item = item("10");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));
    doThrow(new IllegalStateException("audit fora")).when(auditService).recordSuccess(any());

    service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("3"), "Venda");

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("7");
  }

  // ─── consumo de insumo ────────────────────────────────────────────────────

  @Test
  void consumoPorAgendamentoAplicaOFatorDePerda() {
    ItemEstoque item = item("10");
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("2", "10")));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
    when(movimentacaoEstoqueRepository.countByTenantIdAndAppointmentIdAndItemEstoqueId(
            TENANT_ID, APPOINTMENT_ID, ITEM_ID))
        .thenReturn(0L);

    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of(SERVICE_ID));

    // 2 * (1 + 10/100) = 2.2
    MovimentacaoEstoque persistida = movimentacaoPersistida();
    assertThat(persistida.getQuantidade()).isEqualByComparingTo("2.2");
    assertThat(persistida.getOrigem()).isEqualTo(OrigemMovimentacaoEstoque.SERVICO);
    assertThat(persistida.getAppointmentId()).isEqualTo(APPOINTMENT_ID);
    assertThat(persistida.getMotivo()).isEqualTo("Consumo automatico por agendamento");
    assertThat(item.getSaldoAtual()).isEqualByComparingTo("7.8");
  }

  @Test
  void consumoJaRegistradoNaoRepete() {
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("2", "0")));
    when(movimentacaoEstoqueRepository.countByTenantIdAndAppointmentIdAndItemEstoqueId(
            TENANT_ID, APPOINTMENT_ID, ITEM_ID))
        .thenReturn(1L);

    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of(SERVICE_ID));

    verify(movimentacaoEstoqueRepository, never()).saveAndFlush(any());
    verify(itemEstoqueRepository, never()).save(any());
  }

  @Test
  void consumoPorItemComandaCarimbaOComandaItemId() {
    ItemEstoque item = item("10");
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("1", "0")));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));
    when(movimentacaoEstoqueRepository.countByTenantIdAndComandaItemIdAndItemEstoqueId(
            TENANT_ID, COMANDA_ITEM_ID, ITEM_ID))
        .thenReturn(0L);

    service.consumirInsumosPorItemComanda(TENANT_ID, COMANDA_ITEM_ID, SERVICE_ID);

    MovimentacaoEstoque persistida = movimentacaoPersistida();
    assertThat(persistida.getComandaItemId()).isEqualTo(COMANDA_ITEM_ID);
    assertThat(persistida.getMotivo()).isEqualTo("Consumo automatico por item de comanda");
  }

  @Test
  void comandaItemNuloNaoFazNada() {
    service.consumirInsumosPorItemComanda(TENANT_ID, null, SERVICE_ID);
    verifyNoInteractions(servicoInsumoRepository);
  }

  @Test
  void servicoNuloNaoConsomeNada() {
    service.consumirInsumosPorItemComanda(TENANT_ID, COMANDA_ITEM_ID, null);
    verifyNoInteractions(servicoInsumoRepository);
  }

  @Test
  void listaDeServicosVaziaNaoConsultaConfiguracao() {
    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of());
    verifyNoInteractions(estoqueConfiguracaoRepository);
    verifyNoInteractions(servicoInsumoRepository);
  }

  /** Pulado, mas nao em silencio: fica na auditoria e vira aviso no sino. */
  @Test
  void itemInativoEPuladoEAvisado() {
    ItemEstoque item = item("10");
    item.setAtivo(false);
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("1", "0")));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of(SERVICE_ID));

    verify(movimentacaoEstoqueRepository, never()).saveAndFlush(any());
    assertThat(item.getSaldoAtual()).isEqualByComparingTo("10");
    ArgumentCaptor<AuditEventCommand> auditoria = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(auditoria.capture());
    assertThat(auditoria.getValue().action).isEqualTo("STOCK_CONSUMPTION_SKIPPED");
    verify(notificationPublisher)
        .publish(
            eq(TENANT_ID),
            any(),
            any(),
            eq("STOCK_ALERT"),
            eq("estoque:" + ITEM_ID),
            org.mockito.ArgumentMatchers.contains("item esta inativo"),
            eq(StatusNotification.SENT),
            any(),
            any(),
            any());
  }

  /** Diferenca deliberada em relacao a {@code criarMovimentacao}: aqui nao ha 409, so o pulo. */
  @Test
  void saldoInsuficienteNoConsumoEPuladoSemErro() {
    ItemEstoque item = item("1");
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("5", "0")));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao(true, false)));

    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of(SERVICE_ID));

    verify(movimentacaoEstoqueRepository, never()).saveAndFlush(any());
    assertThat(item.getSaldoAtual()).isEqualByComparingTo("1");
  }

  @Test
  void saldoNegativoNoConsumoEPermitidoComBloqueioDesligado() {
    ItemEstoque item = item("1");
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("5", "0")));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao(false, true)));

    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of(SERVICE_ID));

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("-4");
    verify(movimentacaoEstoqueRepository).saveAndFlush(any());
  }

  @Test
  void servicoNuloDentroDaListaEIgnorado() {
    ItemEstoque item = item("10");
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("1", "0")));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    service.consumirInsumosPorAgendamento(
        TENANT_ID, APPOINTMENT_ID, java.util.Arrays.asList(null, SERVICE_ID));

    verify(movimentacaoEstoqueRepository).saveAndFlush(any());
  }

  /** O consumo que DEU CERTO nao gera evento de auditoria, como no original. */
  @Test
  void consumoNaoAudita() {
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("1", "0")));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item("10")));

    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of(SERVICE_ID));

    verifyNoInteractions(auditService);
  }

  /**
   * A trava vem ANTES da conferencia de idempotencia: com a ordem invertida, dois fechamentos
   * simultaneos passavam os dois pelo count e baixavam o insumo duas vezes.
   */
  @Test
  void consumoTravaOItemAntesDeConferirSeJaFoiBaixado() {
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("1", "0")));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item("10")));

    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of(SERVICE_ID));

    org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(itemEstoqueRepository, movimentacaoEstoqueRepository);
    ordem.verify(itemEstoqueRepository).travarPorIdETenant(ITEM_ID, TENANT_ID);
    ordem.verify(movimentacaoEstoqueRepository)
        .countByTenantIdAndAppointmentIdAndItemEstoqueId(TENANT_ID, APPOINTMENT_ID, ITEM_ID);
  }

  // ─── Ajuste: o numero e o saldo final ─────────────────────────────────────

  /** O caso do Raio-X: 500 ml no sistema, 450,5 na prateleira. O backend gravava 49,5. */
  @Test
  void ajusteParaBaixoDefineOSaldoContado() {
    ItemEstoque item = item("500");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    MovimentacaoEstoqueResponse response =
        service.criarMovimentacao(request("AJUSTE", "450.5", "Contagem de setembro"));

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("450.5");
    MovimentacaoEstoque persistida = movimentacaoPersistida();
    assertThat(persistida.getTipo()).isEqualTo(TipoMovimentacaoEstoque.AJUSTE);
    assertThat(persistida.getQuantidade()).isEqualByComparingTo("49.5");
    assertThat(persistida.getSaldoAnterior()).isEqualByComparingTo("500");
    assertThat(response.saldoPosterior).isEqualByComparingTo("450.5");
  }

  @Test
  void ajusteParaCimaTambemExiste() {
    ItemEstoque item = item("3");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    service.criarMovimentacao(request("AJUSTE", "8", "Sobra encontrada"));

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("8");
    assertThat(movimentacaoPersistida().getQuantidade()).isEqualByComparingTo("5");
  }

  @Test
  void ajusteParaZeroEValido() {
    ItemEstoque item = item("4");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    service.criarMovimentacao(request("AJUSTE", "0", "Acabou"));

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("0");
  }

  @Test
  void ajusteIgualAoSaldoAtualE400() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item("10")));

    assertThatThrownBy(() -> service.criarMovimentacao(request("AJUSTE", "10", "Conferido")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("nao ha o que ajustar")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
    verify(movimentacaoEstoqueRepository, never()).saveAndFlush(any());
  }

  @Test
  void entradaOuSaidaComQuantidadeZeroE400() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item("10")));

    assertThatThrownBy(() -> service.criarMovimentacao(request("SAIDA", "0", "Nada")))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  // ─── Lancamento financeiro ────────────────────────────────────────────────

  @Test
  void lancamentoSemValorUnitarioE400ENaoMexeNoSaldo() {
    ItemEstoque item = item("10");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));
    MovimentacaoEstoqueRequest request = request("ENTRADA", "5", "Compra");
    request.gerarLancamentoFinanceiro = Boolean.TRUE;

    assertThatThrownBy(() -> service.criarMovimentacao(request))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
    assertThat(item.getSaldoAtual()).isEqualByComparingTo("10");
    verifyNoInteractions(servicoFinanceiro);
  }

  @Test
  void lancamentoUsaAFormaDePagamentoInformada() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item("0")));
    MovimentacaoEstoqueRequest request = request("ENTRADA", "2", "Compra");
    request.valorUnitarioPago = new BigDecimal("10");
    request.gerarLancamentoFinanceiro = Boolean.TRUE;
    request.formaPagamento = " pix ";

    service.criarMovimentacao(request);

    ArgumentCaptor<TransacaoRequest> despesa = ArgumentCaptor.forClass(TransacaoRequest.class);
    verify(servicoFinanceiro).criar(despesa.capture());
    assertThat(despesa.getValue().paymentMethod).isEqualTo("PIX");
    assertThat(despesa.getValue().amount).isEqualByComparingTo("20.00");
  }

  @Test
  void formaDePagamentoDesconhecidaE400() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item("0")));
    MovimentacaoEstoqueRequest request = request("ENTRADA", "2", "Compra");
    request.valorUnitarioPago = new BigDecimal("10");
    request.gerarLancamentoFinanceiro = Boolean.TRUE;
    request.formaPagamento = "CHEQUE";

    assertThatThrownBy(() -> service.criarMovimentacao(request))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Forma de pagamento invalida");
  }

  @Test
  void semLancamentoNaoChamaOFinanceiro() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item("0")));
    MovimentacaoEstoqueRequest request = request("ENTRADA", "2", "Compra");
    request.valorUnitarioPago = new BigDecimal("10");

    service.criarMovimentacao(request);

    verifyNoInteractions(servicoFinanceiro);
    assertThat(movimentacaoPersistida().getTransacaoFinanceiraId()).isNull();
  }

  /** Saldo zerado nao tem valor a ponderar: o custo e o da propria compra. */
  @Test
  void primeiraEntradaDefineOCustoPeloPrecoPago() {
    ItemEstoque item = item("0");
    item.setCustoMedioUnitario(new BigDecimal("99"));
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));
    MovimentacaoEstoqueRequest request = request("ENTRADA", "4", "Compra");
    request.valorUnitarioPago = new BigDecimal("2.50");

    service.criarMovimentacao(request);

    assertThat(item.getCustoMedioUnitario()).isEqualByComparingTo("2.50");
  }

  // ─── Estoque minimo ───────────────────────────────────────────────────────

  @Test
  void avisaQuandoOSaldoChegaAoMinimo() {
    ItemEstoque item = item("3");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao(false, true)));

    service.criarMovimentacao(request("SAIDA", "1", "Uso interno"));

    verify(notificationPublisher)
        .publish(
            eq(TENANT_ID),
            any(),
            any(),
            eq("STOCK_ALERT"),
            eq("estoque:" + ITEM_ID),
            org.mockito.ArgumentMatchers.contains("Estoque baixo"),
            eq(StatusNotification.SENT),
            any(),
            any(),
            eq(6 * 60 * 60L));
  }

  /** So na travessia: um item que ja estava abaixo nao toca o sino a cada baixa. */
  @Test
  void naoRepeteOAvisoParaQuemJaEstavaAbaixo() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item("1.5")));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao(false, true)));

    service.criarMovimentacao(request("SAIDA", "0.5", "Uso interno"));

    verifyNoInteractions(notificationPublisher);
  }

  @Test
  void alertaDesligadoNaoAvisa() {
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item("3")));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao(false, false)));

    service.criarMovimentacao(request("SAIDA", "2", "Uso interno"));

    verifyNoInteractions(notificationPublisher);
  }

  // ─── Inventario ───────────────────────────────────────────────────────────

  /**
   * A diferenca da contagem vai para o saldo de AGORA: contou-se 2 a menos quando havia 10, e desde
   * entao saiu 1 — o saldo termina em 7, e nao nos 8 contados.
   */
  @Test
  void inventarioAplicaADiferencaAoSaldoAtual() {
    ItemEstoque item = item("9");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    MovimentacaoEstoqueResponse response =
        service.ajustarPorInventario(ITEM_ID, new BigDecimal("-2"), "Inventario: Setembro");

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("7");
    MovimentacaoEstoque persistida = movimentacaoPersistida();
    assertThat(persistida.getOrigem()).isEqualTo(OrigemMovimentacaoEstoque.INVENTARIO);
    assertThat(persistida.getTipo()).isEqualTo(TipoMovimentacaoEstoque.AJUSTE);
    assertThat(response).isNotNull();
  }

  @Test
  void inventarioNuncaDeixaOSaldoNegativo() {
    ItemEstoque item = item("1");
    when(itemEstoqueRepository.travarPorIdETenant(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    service.ajustarPorInventario(ITEM_ID, new BigDecimal("-5"), "Inventario");

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("0");
  }

  @Test
  void inventarioSemDiferencaNaoMovimenta() {
    assertThat(service.ajustarPorInventario(ITEM_ID, BigDecimal.ZERO, "Inventario")).isNull();
    verifyNoInteractions(itemEstoqueRepository);
  }
}
