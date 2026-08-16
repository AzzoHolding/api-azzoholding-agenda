package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

  @Mock private ItemEstoqueRepository itemEstoqueRepository;
  @Mock private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
  @Mock private EstoqueConfiguracaoRepository estoqueConfiguracaoRepository;
  @Mock private ServicoInsumoRepository servicoInsumoRepository;
  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private AuditService auditService;

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
            auditService);
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
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));

    MovimentacaoEstoqueRequest request = request("ENTRADA", "5", "Compra de fornecedor");
    request.valorUnitarioPago = new BigDecimal("6.50");
    request.origem = " compra ";
    request.gerarLancamentoFinanceiro = Boolean.TRUE;

    MovimentacaoEstoqueResponse response = service.criarMovimentacao(request);

    assertThat(item.getCustoMedioUnitario()).isEqualByComparingTo("6.50");
    assertThat(item.getSaldoAtual()).isEqualByComparingTo("15");
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
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
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
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
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
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
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
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

    MovimentacaoEstoqueResponse response =
        service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("3"), "Venda em comanda");

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("7");
    MovimentacaoEstoque persistida = movimentacaoPersistida();
    assertThat(persistida.getTipo()).isEqualTo(TipoMovimentacaoEstoque.SAIDA);
    assertThat(persistida.getSaldoAnterior()).isEqualByComparingTo("10");
    assertThat(persistida.getSaldoPosterior()).isEqualByComparingTo("7");
    assertThat(persistida.getOrigem()).isEqualTo(OrigemMovimentacaoEstoque.MANUAL);
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
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));

    service.criarMovimentacao(ITEM_ID, "ENTRADA", new BigDecimal("3"), "Estorno de comanda: erro");

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("7");
    assertThat(movimentacaoPersistida().getTipo()).isEqualTo(TipoMovimentacaoEstoque.ENTRADA);
    // ENTRADA nunca consulta a configuracao de bloqueio.
    verifyNoInteractions(estoqueConfiguracaoRepository);
  }

  @Test
  void saidaSemSaldoEBloqueadaComConflito() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
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
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("1")));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("5"), "Venda"))
        .isInstanceOf(ApiClientErrorException.class);
  }

  @Test
  void saldoNegativoEPermitidoQuandoOBloqueioEstaDesligado() {
    ItemEstoque item = item("1");
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao(false, false)));

    service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("5"), "Venda");

    assertThat(item.getSaldoAtual()).isEqualByComparingTo("-4");
  }

  @Test
  void itemDeOutroTenantDa404() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.criarMovimentacao(ITEM_ID, "SAIDA", new BigDecimal("1"), "Venda"))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void tipoEmBrancoDa400() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("10")));

    assertThatThrownBy(() -> service.criarMovimentacao(ITEM_ID, "  ", new BigDecimal("1"), "Venda"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Tipo de movimentacao obrigatorio.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  @Test
  void tipoDesconhecidoDa400() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
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
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item));

    service.criarMovimentacao(ITEM_ID, "  saida ", new BigDecimal("1"), "Venda");

    assertThat(movimentacaoPersistida().getTipo()).isEqualTo(TipoMovimentacaoEstoque.SAIDA);
  }

  @Test
  void motivoEmBrancoDa400() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("10")));

    assertThatThrownBy(
            () -> service.criarMovimentacao(ITEM_ID, "ENTRADA", new BigDecimal("1"), "   "))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Motivo obrigatorio.");
  }

  @Test
  void motivoEColapsadoEmEspacoSimples() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item("10")));

    service.criarMovimentacao(ITEM_ID, "ENTRADA", new BigDecimal("1"), "  Venda   em   comanda  ");

    assertThat(movimentacaoPersistida().getMotivo()).isEqualTo("Venda em comanda");
  }

  @Test
  void auditaCriacaoComEstadoAntesEDepoisDoItem() {
    ItemEstoque item = item("10");
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
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
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
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
    when(itemEstoqueRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
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
    verify(itemEstoqueRepository, never()).findById(any());
  }

  @Test
  void consumoPorItemComandaCarimbaOComandaItemId() {
    ItemEstoque item = item("10");
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("1", "0")));
    when(itemEstoqueRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
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

  @Test
  void itemInativoEPuladoEmSilencio() {
    ItemEstoque item = item("10");
    item.setAtivo(false);
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("1", "0")));
    when(itemEstoqueRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of(SERVICE_ID));

    verify(movimentacaoEstoqueRepository, never()).saveAndFlush(any());
    assertThat(item.getSaldoAtual()).isEqualByComparingTo("10");
  }

  /** Diferenca deliberada em relacao a {@code criarMovimentacao}: aqui nao ha 409, so o pulo. */
  @Test
  void saldoInsuficienteNoConsumoEPuladoSemErro() {
    ItemEstoque item = item("1");
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("5", "0")));
    when(itemEstoqueRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
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
    when(itemEstoqueRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
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
    when(itemEstoqueRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

    service.consumirInsumosPorAgendamento(
        TENANT_ID, APPOINTMENT_ID, java.util.Arrays.asList(null, SERVICE_ID));

    verify(movimentacaoEstoqueRepository).saveAndFlush(any());
  }

  /** O consumo automatico nao gera evento de auditoria no original. */
  @Test
  void consumoNaoAudita() {
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumo("1", "0")));
    when(itemEstoqueRepository.findById(ITEM_ID)).thenReturn(Optional.of(item("10")));

    service.consumirInsumosPorAgendamento(TENANT_ID, APPOINTMENT_ID, List.of(SERVICE_ID));

    verifyNoInteractions(auditService);
  }
}
