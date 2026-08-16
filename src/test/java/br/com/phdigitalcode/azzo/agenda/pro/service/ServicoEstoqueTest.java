package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ConfiguracaoEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ConfiguracaoEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.DashboardEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ItemEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ItemEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ItemEstoqueUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.MovimentacaoEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.MovimentacaoEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ServicoInsumoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ServicoInsumoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ServicoInsumoUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueConfiguracao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicoInsumo;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.OrigemMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueConfiguracaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueFornecedorRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueInventarioContagemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueInventarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoquePedidoCompraRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueTransferenciaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoEstoqueErroLinhaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoEstoqueJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentacaoEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoInsumoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre a primeira fronteira da superficie HTTP de {@code inventory}: CRUD de itens, listagem de
 * movimentacao, dashboard, configuracao e vinculo servico↔insumo.
 *
 * <p>O motor de movimentacao ({@code criarMovimentacao}) tem cobertura propria em
 * {@link EstoqueMovimentacaoServiceTest}; aqui so se verifica que {@code ServicoEstoque}
 * <b>delega</b> em vez de reimplementar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoEstoqueTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID SERVICE_ID = UUID.randomUUID();
  private static final UUID INSUMO_ID = UUID.randomUUID();
  private static final UUID USUARIO_ID = UUID.randomUUID();

  @Mock private ItemEstoqueRepository itemEstoqueRepository;
  @Mock private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
  @Mock private ImportacaoEstoqueJobRepository importacaoEstoqueJobRepository;
  @Mock private ImportacaoEstoqueErroLinhaRepository importacaoEstoqueErroLinhaRepository;
  @Mock private EstoqueConfiguracaoRepository estoqueConfiguracaoRepository;
  @Mock private ServicoInsumoRepository servicoInsumoRepository;
  @Mock private EstoqueInventarioRepository estoqueInventarioRepository;
  @Mock private EstoqueInventarioContagemRepository estoqueInventarioContagemRepository;
  @Mock private EstoqueFornecedorRepository estoqueFornecedorRepository;
  @Mock private EstoquePedidoCompraRepository estoquePedidoCompraRepository;
  @Mock private EstoqueTransferenciaRepository estoqueTransferenciaRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private EstoqueMovimentacaoService estoqueMovimentacaoService;
  @Mock private MinioStorageService minioStorageService;
  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private AuditService auditService;

  private ServicoEstoque service;

  @BeforeEach
  void setUp() {
    service =
        new ServicoEstoque(
            itemEstoqueRepository,
            movimentacaoEstoqueRepository,
            importacaoEstoqueJobRepository,
            importacaoEstoqueErroLinhaRepository,
            estoqueConfiguracaoRepository,
            servicoInsumoRepository,
            estoqueInventarioRepository,
            estoqueInventarioContagemRepository,
            estoqueFornecedorRepository,
            estoquePedidoCompraRepository,
            estoqueTransferenciaRepository,
            usuarioRepository,
            estoqueMovimentacaoService,
            minioStorageService,
            contextoTenant,
            authenticatedUser,
            auditService);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(authenticatedUser.idOuNulo()).thenReturn(USUARIO_ID);
    when(authenticatedUser.roleOuNulo()).thenReturn("OWNER");
    when(itemEstoqueRepository.saveAndFlush(any())).thenAnswer(i -> persistir(i.getArgument(0)));
    when(itemEstoqueRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(servicoInsumoRepository.saveAndFlush(any())).thenAnswer(i -> persistir(i.getArgument(0)));
    when(servicoInsumoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(estoqueConfiguracaoRepository.saveAndFlush(any()))
        .thenAnswer(i -> persistir(i.getArgument(0)));
    when(estoqueConfiguracaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  // ─── Itens: criacao ──────────────────────────────────────────────────────

  @Test
  void criarItemNormalizaCamposEZeraSaldo() {
    ItemEstoqueRequest request = new ItemEstoqueRequest();
    request.nome = "  Shampoo   Hidratante ";
    request.sku = " sh-001 ";
    request.unidadeMedida = "un";
    request.estoqueMinimo = new BigDecimal("5.0000");

    ItemEstoqueResponse response = service.criarItem(request);

    ArgumentCaptor<ItemEstoque> captor = ArgumentCaptor.forClass(ItemEstoque.class);
    verify(itemEstoqueRepository).saveAndFlush(captor.capture());
    ItemEstoque salvo = captor.getValue();
    assertThat(salvo.getTenantId()).isEqualTo(TENANT_ID);
    // Texto livre: espacos colapsados, acentuacao e caixa preservadas.
    assertThat(salvo.getNome()).isEqualTo("Shampoo Hidratante");
    // Codigo: sobe para maiuscula.
    assertThat(salvo.getSku()).isEqualTo("SH-001");
    assertThat(salvo.getUnidadeMedida()).isEqualTo("UN");
    assertThat(salvo.getSaldoAtual()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(salvo.getAtivo()).isTrue();
    assertThat(response.nome).isEqualTo("Shampoo Hidratante");
    assertThat(response.saldoAtual).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void criarItemRespeitaAtivoFalseExplicito() {
    ItemEstoqueRequest request = requestItemValido();
    request.ativo = Boolean.FALSE;

    service.criarItem(request);

    ArgumentCaptor<ItemEstoque> captor = ArgumentCaptor.forClass(ItemEstoque.class);
    verify(itemEstoqueRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getAtivo()).isFalse();
  }

  @Test
  void criarItemAuditaCriacaoSemBefore() {
    service.criarItem(requestItemValido());

    AuditEventCommand command = capturarAuditoria();
    assertThat(command.action).isEqualTo("STOCK_ITEM_CREATE");
    assertThat(command.entityType).isEqualTo("STOCK_ITEM");
    assertThat(command.tenantId).isEqualTo(TENANT_ID);
    assertThat(command.actorUserId).isEqualTo(USUARIO_ID);
    assertThat(command.before).isNull();
    assertThat(command.entityId).isNotNull();
  }

  @Test
  void criarItemComNomeSoEspacoFalhaCom400() {
    ItemEstoqueRequest request = requestItemValido();
    request.nome = "   ";

    assertThatThrownBy(() -> service.criarItem(request))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Nome do item obrigatorio")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  @Test
  void criarItemComUnidadeDeMedidaSoEspacoFalhaCom400() {
    ItemEstoqueRequest request = requestItemValido();
    request.unidadeMedida = " ";

    assertThatThrownBy(() -> service.criarItem(request))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Unidade de medida obrigatoria");
  }

  // ─── Itens: atualizacao ──────────────────────────────────────────────────

  @Test
  void atualizarItemAplicaSomenteOsCamposInformados() {
    ItemEstoque item = itemExistente();
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    ItemEstoqueUpdateRequest request = new ItemEstoqueUpdateRequest();
    request.estoqueMinimo = new BigDecimal("9.0000");

    service.atualizarItem(ITEM_ID, request);

    assertThat(item.getEstoqueMinimo()).isEqualByComparingTo("9.0000");
    assertThat(item.getNome()).isEqualTo("Shampoo");
    assertThat(item.getUnidadeMedida()).isEqualTo("UN");
    assertThat(item.getAtivo()).isTrue();
  }

  @Test
  void atualizarItemIgnoraNomeEUnidadeEmBrancoMasZeraSkuComStringVazia() {
    ItemEstoque item = itemExistente();
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    ItemEstoqueUpdateRequest request = new ItemEstoqueUpdateRequest();
    request.nome = "  ";
    request.unidadeMedida = "";
    request.sku = "  ";

    service.atualizarItem(ITEM_ID, request);

    // nome/unidade: guarda `!isBlank()` protege o valor atual.
    assertThat(item.getNome()).isEqualTo("Shampoo");
    assertThat(item.getUnidadeMedida()).isEqualTo("UN");
    // sku: guarda so `!= null`, e o normalizador opcional devolve null — assimetria do original.
    assertThat(item.getSku()).isNull();
  }

  @Test
  void atualizarItemAuditaComBeforeEAfter() {
    ItemEstoque item = itemExistente();
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID)).thenReturn(Optional.of(item));

    ItemEstoqueUpdateRequest request = new ItemEstoqueUpdateRequest();
    request.nome = "Shampoo Novo";

    service.atualizarItem(ITEM_ID, request);

    AuditEventCommand command = capturarAuditoria();
    assertThat(command.action).isEqualTo("STOCK_ITEM_UPDATE");
    assertThat(((ItemEstoqueResponse) command.before).nome).isEqualTo("Shampoo");
    assertThat(((ItemEstoqueResponse) command.after).nome).isEqualTo("Shampoo Novo");
  }

  @Test
  void atualizarItemDeOutroTenantFalhaCom404() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.atualizarItem(ITEM_ID, new ItemEstoqueUpdateRequest()))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void buscarItemInexistenteFalhaCom404() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.buscarItem(ITEM_ID))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Item de estoque nao encontrado");
  }

  // ─── Itens: listagem, paginacao e cursor ─────────────────────────────────

  @Test
  void listarItensSemPaginacaoUsaOrdenacaoSemPageable() {
    when(itemEstoqueRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of(itemExistente()));

    List<ItemEstoqueResponse> resposta =
        service.listarItens(null, null, null, null, null, null, null);

    assertThat(resposta).hasSize(1);
    ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
    verify(itemEstoqueRepository).findAll(any(Specification.class), sort.capture());
    assertThat(sort.getValue().toString()).isEqualTo("createdAt: DESC,id: DESC");
    verify(itemEstoqueRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void listarItensComPageELimitConverteParaIndiceBaseZero() {
    when(itemEstoqueRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(itemExistente())));

    service.listarItens(3, 20, null, null, null, null, null);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(itemEstoqueRepository).findAll(any(Specification.class), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
  }

  @Test
  void listarItensComPageOuLimitNaoPositivoCaiParaListaInteira() {
    when(itemEstoqueRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());

    service.listarItens(0, 20, null, null, null, null, null);
    service.listarItens(1, 0, null, null, null, null, null);

    verify(itemEstoqueRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void listarItensComCursorPelaMetadeIgnoraOCursor() {
    when(itemEstoqueRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());

    // So o createdAt, sem o id: o original trata como "sem cursor", nao como erro.
    service.listarItens(null, null, Instant.now().toString(), null, null, null, null);

    verify(itemEstoqueRepository).findAll(any(Specification.class), any(Sort.class));
  }

  @Test
  void listarItensComCursorMalFormadoFalhaCom400() {
    assertThatThrownBy(
            () ->
                service.listarItens(
                    null, null, "ontem-de-tarde", UUID.randomUUID().toString(), null, null, null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Cursor invalido")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  // ─── Movimentacoes ───────────────────────────────────────────────────────

  @Test
  void listarMovimentacoesResolveNomeDoItemEmLote() {
    MovimentacaoEstoque movimentacao = movimentacaoExistente();
    when(movimentacaoEstoqueRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of(movimentacao));
    when(itemEstoqueRepository.findByTenantIdAndIdIn(TENANT_ID, Set.of(ITEM_ID)))
        .thenReturn(List.of(itemExistente()));

    List<MovimentacaoEstoqueResponse> resposta =
        service.listarMovimentacoes(null, null, null, null, null, null);

    assertThat(resposta).hasSize(1);
    assertThat(resposta.getFirst().itemNome).isEqualTo("Shampoo");
    assertThat(resposta.getFirst().tipo).isEqualTo("SAIDA");
    // Uma unica consulta de itens para a pagina inteira, nao uma por linha.
    verify(itemEstoqueRepository).findByTenantIdAndIdIn(TENANT_ID, Set.of(ITEM_ID));
  }

  @Test
  void listarMovimentacoesSemResultadoNaoConsultaItens() {
    when(movimentacaoEstoqueRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());

    assertThat(service.listarMovimentacoes(null, null, null, null, null, null)).isEmpty();

    verify(itemEstoqueRepository, never()).findByTenantIdAndIdIn(any(), any());
  }

  @Test
  void listarMovimentacoesComTipoEmMinusculaNormalizaAntesDoValueOf() {
    when(movimentacaoEstoqueRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());

    assertThat(service.listarMovimentacoes(null, null, null, null, null, " saida "))
        .isEmpty();
  }

  @Test
  void listarMovimentacoesComTipoDesconhecidoPropagaIllegalArgument() {
    // Assimetria do original preservada: aqui o valueOf sobe cru (500), diferente do 400 que o
    // motor de movimentacao devolve ao criar.
    assertThatThrownBy(
            () -> service.listarMovimentacoes(null, null, null, null, null, "TELETRANSPORTE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void criarMovimentacaoDelegaParaOMotorSemRecalcularSaldo() {
    MovimentacaoEstoqueRequest request = new MovimentacaoEstoqueRequest();
    request.itemEstoqueId = ITEM_ID.toString();
    request.tipo = "ENTRADA";
    request.quantidade = new BigDecimal("3");
    request.motivo = "Compra";
    MovimentacaoEstoqueResponse esperado = new MovimentacaoEstoqueResponse();
    when(estoqueMovimentacaoService.criarMovimentacao(request)).thenReturn(esperado);

    assertThat(service.criarMovimentacao(request)).isSameAs(esperado);

    verify(estoqueMovimentacaoService).criarMovimentacao(request);
    verifyNoInteractions(movimentacaoEstoqueRepository);
  }

  // ─── Dashboard ───────────────────────────────────────────────────────────

  @Test
  void dashboardAgregaItensEMovimentacoesDoTenant() {
    ItemEstoque zerado = item("Zerado", "0.0000", "2.0000", "10.00");
    ItemEstoque noMinimo = item("No minimo", "5.0000", "5.0000", "4.00");
    ItemEstoque folgado = item("Folgado", "10.0000", "1.0000", "3.00");
    when(itemEstoqueRepository.findByTenantId(TENANT_ID))
        .thenReturn(List.of(zerado, noMinimo, folgado));
    when(movimentacaoEstoqueRepository.findByTenantId(TENANT_ID))
        .thenReturn(
            List.of(
                movimentacao(TipoMovimentacaoEstoque.SAIDA, "30.00"),
                movimentacao(TipoMovimentacaoEstoque.SAIDA, "12.00"),
                movimentacao(TipoMovimentacaoEstoque.ENTRADA, "500.00")));

    DashboardEstoqueResponse response = service.obterDashboard();

    // `<=`: item exatamente no minimo conta como abaixo do minimo.
    assertThat(response.itensAbaixoMinimo).isEqualTo(2);
    assertThat(response.itensZerados).isEqualTo(1);
    // 0*10 + 5*4 + 10*3 = 50
    assertThat(response.valorEstoqueCustoMedio).isEqualByComparingTo("50.00");
    assertThat(response.rupturaTaxa).isEqualTo(1d / 3d);
    // So as SAIDAs entram em perdasValor; a ENTRADA de 500 fica de fora.
    assertThat(response.perdasValor).isEqualByComparingTo("42.00");
    // O original nunca preenche margemServicos.
    assertThat(response.margemServicos).isEmpty();
    assertThat(response.atualizadoEm).isNotBlank();
  }

  @Test
  void dashboardSemItensNaoDividePorZero() {
    when(itemEstoqueRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
    when(movimentacaoEstoqueRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

    DashboardEstoqueResponse response = service.obterDashboard();

    assertThat(response.rupturaTaxa).isZero();
    assertThat(response.valorEstoqueCustoMedio).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(response.perdasValor).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void dashboardTrataCustoMedioNuloComoZero() {
    when(itemEstoqueRepository.findByTenantId(TENANT_ID))
        .thenReturn(List.of(item("Sem custo", "10.0000", "1.0000", null)));
    when(movimentacaoEstoqueRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

    assertThat(service.obterDashboard().valorEstoqueCustoMedio)
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  // ─── Configuracao ────────────────────────────────────────────────────────

  @Test
  void obterConfiguracoesCriaALinhaNaPrimeiraLeitura() {
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

    ConfiguracaoEstoqueResponse response = service.obterConfiguracoes();

    ArgumentCaptor<EstoqueConfiguracao> captor =
        ArgumentCaptor.forClass(EstoqueConfiguracao.class);
    verify(estoqueConfiguracaoRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    // Defaults vem do @PrePersist da entidade, disparado pelo flush.
    assertThat(response.bloquearSaidaSemSaldo).isTrue();
    assertThat(response.alertaEstoqueMinimoAtivo).isTrue();
    assertThat(response.permitirAjusteNegativoComPermissao).isFalse();
    assertThat(response.diasCoberturaMeta).isEqualTo(15);
  }

  @Test
  void obterConfiguracoesExistenteNaoCriaOutraLinha() {
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracaoExistente()));

    assertThat(service.obterConfiguracoes().diasCoberturaMeta).isEqualTo(30);

    verify(estoqueConfiguracaoRepository, never()).saveAndFlush(any());
  }

  @Test
  void atualizarConfiguracoesAplicaSomenteOsCamposInformados() {
    EstoqueConfiguracao configuracao = configuracaoExistente();
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao));

    ConfiguracaoEstoqueRequest request = new ConfiguracaoEstoqueRequest();
    request.bloquearSaidaSemSaldo = Boolean.FALSE;

    ConfiguracaoEstoqueResponse response = service.atualizarConfiguracoes(request);

    assertThat(response.bloquearSaidaSemSaldo).isFalse();
    assertThat(configuracao.getAlertaEstoqueMinimoAtivo()).isTrue();
    assertThat(configuracao.getDiasCoberturaMeta()).isEqualTo(30);
  }

  @Test
  void atualizarConfiguracoesComDiasCoberturaZeroFalhaCom400() {
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracaoExistente()));

    ConfiguracaoEstoqueRequest request = new ConfiguracaoEstoqueRequest();
    request.diasCoberturaMeta = 0;

    assertThatThrownBy(() -> service.atualizarConfiguracoes(request))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("diasCoberturaMeta deve ser maior que zero")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  @Test
  void atualizarConfiguracoesAudita() {
    EstoqueConfiguracao configuracao = configuracaoExistente();
    when(estoqueConfiguracaoRepository.findByTenantId(TENANT_ID))
        .thenReturn(Optional.of(configuracao));

    ConfiguracaoEstoqueRequest request = new ConfiguracaoEstoqueRequest();
    request.diasCoberturaMeta = 45;

    service.atualizarConfiguracoes(request);

    AuditEventCommand command = capturarAuditoria();
    assertThat(command.action).isEqualTo("STOCK_SETTINGS_UPDATE");
    assertThat(command.entityType).isEqualTo("STOCK_SETTINGS");
    assertThat(((ConfiguracaoEstoqueResponse) command.before).diasCoberturaMeta).isEqualTo(30);
    assertThat(((ConfiguracaoEstoqueResponse) command.after).diasCoberturaMeta).isEqualTo(45);
  }

  // ─── Servico ↔ insumo ────────────────────────────────────────────────────

  @Test
  void listarInsumosPorServicoPreencheDadosDoItem() {
    when(servicoInsumoRepository.findByTenantAndService(TENANT_ID, SERVICE_ID))
        .thenReturn(List.of(insumoExistente()));
    when(itemEstoqueRepository.findByTenantIdAndIdIn(TENANT_ID, Set.of(ITEM_ID)))
        .thenReturn(List.of(itemExistente()));

    List<ServicoInsumoResponse> resposta =
        service.listarInsumosPorServico(SERVICE_ID.toString());

    assertThat(resposta).hasSize(1);
    ServicoInsumoResponse primeiro = resposta.getFirst();
    assertThat(primeiro.itemNome).isEqualTo("Shampoo");
    assertThat(primeiro.itemUnidadeMedida).isEqualTo("UN");
    assertThat(primeiro.saldoAtualItem).isEqualByComparingTo("7.0000");
    assertThat(primeiro.quantidadeConsumo).isEqualByComparingTo("2.0000");
  }

  @Test
  void adicionarInsumoCriaVinculoComPerdaZeroQuandoOmitida() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(itemExistente()));
    when(servicoInsumoRepository.findByTenantServiceAndItem(TENANT_ID, SERVICE_ID, ITEM_ID))
        .thenReturn(Optional.empty());

    ServicoInsumoResponse response = service.adicionarInsumo(requestInsumoValido());

    ArgumentCaptor<ServicoInsumo> captor = ArgumentCaptor.forClass(ServicoInsumo.class);
    verify(servicoInsumoRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getPercentualPerda()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(captor.getValue().getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(response.itemNome).isEqualTo("Shampoo");
  }

  @Test
  void adicionarInsumoJaVinculadoEAtivoFalhaCom409() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(itemExistente()));
    when(servicoInsumoRepository.findByTenantServiceAndItem(TENANT_ID, SERVICE_ID, ITEM_ID))
        .thenReturn(Optional.of(insumoExistente()));

    assertThatThrownBy(() -> service.adicionarInsumo(requestInsumoValido()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("ja esta vinculado ao servico")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(409);
  }

  @Test
  void adicionarInsumoReativaVinculoInativoEmVezDeDuplicar() {
    ServicoInsumo inativo = insumoExistente();
    inativo.setAtivo(Boolean.FALSE);
    inativo.setQuantidadeConsumo(new BigDecimal("1.0000"));
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(itemExistente()));
    when(servicoInsumoRepository.findByTenantServiceAndItem(TENANT_ID, SERVICE_ID, ITEM_ID))
        .thenReturn(Optional.of(inativo));

    ServicoInsumoRequest request = requestInsumoValido();
    request.percentualPerda = new BigDecimal("7.50");

    ServicoInsumoResponse response = service.adicionarInsumo(request);

    assertThat(inativo.getAtivo()).isTrue();
    assertThat(inativo.getQuantidadeConsumo()).isEqualByComparingTo("2.0000");
    assertThat(inativo.getPercentualPerda()).isEqualByComparingTo("7.50");
    assertThat(response.ativo).isTrue();
    verify(servicoInsumoRepository).save(inativo);
    verify(servicoInsumoRepository, never()).saveAndFlush(any());
  }

  @Test
  void adicionarInsumoComItemDeOutroTenantFalhaCom404() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.adicionarInsumo(requestInsumoValido()))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);

    verify(servicoInsumoRepository, never()).saveAndFlush(any());
  }

  @Test
  void atualizarInsumoAplicaSomenteOsCamposInformados() {
    ServicoInsumo insumo = insumoExistente();
    when(servicoInsumoRepository.findByIdAndTenantId(INSUMO_ID, TENANT_ID))
        .thenReturn(Optional.of(insumo));
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(itemExistente()));

    ServicoInsumoUpdateRequest request = new ServicoInsumoUpdateRequest();
    request.quantidadeConsumo = new BigDecimal("4.5000");

    ServicoInsumoResponse response = service.atualizarInsumo(INSUMO_ID.toString(), request);

    assertThat(insumo.getQuantidadeConsumo()).isEqualByComparingTo("4.5000");
    assertThat(insumo.getPercentualPerda()).isEqualByComparingTo("10.00");
    assertThat(response.itemNome).isEqualTo("Shampoo");
  }

  @Test
  void atualizarInsumoInexistenteFalhaCom404() {
    when(servicoInsumoRepository.findByIdAndTenantId(INSUMO_ID, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.atualizarInsumo(INSUMO_ID.toString(), new ServicoInsumoUpdateRequest()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Vinculo servico-insumo nao encontrado");
  }

  @Test
  void removerInsumoDesativaEmVezDeApagar() {
    ServicoInsumo insumo = insumoExistente();
    when(servicoInsumoRepository.findByIdAndTenantId(INSUMO_ID, TENANT_ID))
        .thenReturn(Optional.of(insumo));

    service.removerInsumo(INSUMO_ID.toString());

    assertThat(insumo.getAtivo()).isFalse();
    verify(servicoInsumoRepository).save(insumo);
    verify(servicoInsumoRepository, never()).delete(any());
    verify(servicoInsumoRepository, never()).deleteById(any());
  }

  @Test
  void falhaDeAuditoriaNaoDerrubaAOperacao() {
    org.mockito.Mockito.doThrow(new IllegalStateException("audit fora do ar"))
        .when(auditService)
        .recordSuccess(any());

    assertThat(service.criarItem(requestItemValido()).nome).isEqualTo("Shampoo");
  }

  // ─── Fixtures ────────────────────────────────────────────────────────────

  private ItemEstoqueRequest requestItemValido() {
    ItemEstoqueRequest request = new ItemEstoqueRequest();
    request.nome = "Shampoo";
    request.unidadeMedida = "UN";
    request.estoqueMinimo = new BigDecimal("2.0000");
    return request;
  }

  private ServicoInsumoRequest requestInsumoValido() {
    ServicoInsumoRequest request = new ServicoInsumoRequest();
    request.serviceId = SERVICE_ID.toString();
    request.itemEstoqueId = ITEM_ID.toString();
    request.quantidadeConsumo = new BigDecimal("2.0000");
    return request;
  }

  private ItemEstoque itemExistente() {
    ItemEstoque item = item("Shampoo", "7.0000", "2.0000", "4.00");
    item.setId(ITEM_ID);
    item.setSku("SH-001");
    return item;
  }

  private ItemEstoque item(String nome, String saldo, String minimo, String custoMedio) {
    ItemEstoque item = new ItemEstoque();
    item.setId(UUID.randomUUID());
    item.setTenantId(TENANT_ID);
    item.setNome(nome);
    item.setUnidadeMedida("UN");
    item.setSaldoAtual(new BigDecimal(saldo));
    item.setEstoqueMinimo(new BigDecimal(minimo));
    item.setCustoMedioUnitario(custoMedio == null ? null : new BigDecimal(custoMedio));
    item.setAtivo(Boolean.TRUE);
    item.setCreatedAt(Instant.now());
    item.setUpdatedAt(Instant.now());
    return item;
  }

  private MovimentacaoEstoque movimentacaoExistente() {
    MovimentacaoEstoque movimentacao = movimentacao(TipoMovimentacaoEstoque.SAIDA, "8.00");
    movimentacao.setItemEstoqueId(ITEM_ID);
    return movimentacao;
  }

  private MovimentacaoEstoque movimentacao(TipoMovimentacaoEstoque tipo, String valorTotal) {
    MovimentacaoEstoque movimentacao = new MovimentacaoEstoque();
    movimentacao.setId(UUID.randomUUID());
    movimentacao.setTenantId(TENANT_ID);
    movimentacao.setItemEstoqueId(UUID.randomUUID());
    movimentacao.setTipo(tipo);
    movimentacao.setQuantidade(new BigDecimal("2.0000"));
    movimentacao.setSaldoAnterior(new BigDecimal("9.0000"));
    movimentacao.setSaldoPosterior(new BigDecimal("7.0000"));
    movimentacao.setMotivo("Consumo");
    movimentacao.setOrigem(OrigemMovimentacaoEstoque.MANUAL);
    movimentacao.setValorTotalMovimentacao(new BigDecimal(valorTotal));
    movimentacao.setGerarLancamentoFinanceiro(Boolean.FALSE);
    movimentacao.setCreatedAt(Instant.now());
    return movimentacao;
  }

  private ServicoInsumo insumoExistente() {
    ServicoInsumo insumo = new ServicoInsumo();
    insumo.setId(INSUMO_ID);
    insumo.setTenantId(TENANT_ID);
    insumo.setServiceId(SERVICE_ID);
    insumo.setItemEstoqueId(ITEM_ID);
    insumo.setQuantidadeConsumo(new BigDecimal("2.0000"));
    insumo.setPercentualPerda(new BigDecimal("10.00"));
    insumo.setAtivo(Boolean.TRUE);
    insumo.setCreatedAt(Instant.now());
    insumo.setUpdatedAt(Instant.now());
    return insumo;
  }

  private EstoqueConfiguracao configuracaoExistente() {
    EstoqueConfiguracao configuracao = new EstoqueConfiguracao();
    configuracao.setId(UUID.randomUUID());
    configuracao.setTenantId(TENANT_ID);
    configuracao.setAlertaEstoqueMinimoAtivo(Boolean.TRUE);
    configuracao.setBloquearSaidaSemSaldo(Boolean.TRUE);
    configuracao.setPermitirAjusteNegativoComPermissao(Boolean.FALSE);
    configuracao.setDiasCoberturaMeta(30);
    configuracao.setCreatedAt(Instant.now());
    configuracao.setUpdatedAt(Instant.now());
    return configuracao;
  }

  private AuditEventCommand capturarAuditoria() {
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    return captor.getValue();
  }

  /**
   * Simula o {@code @PrePersist} da entidade, que so roda de verdade com um EntityManager. E o que
   * gera o id usado no payload de auditoria e nos defaults da configuracao.
   */
  private Object persistir(Object entidade) {
    if (entidade instanceof ItemEstoque item) {
      if (item.getId() == null) item.setId(UUID.randomUUID());
      if (item.getCreatedAt() == null) item.setCreatedAt(Instant.now());
      if (item.getUpdatedAt() == null) item.setUpdatedAt(Instant.now());
    } else if (entidade instanceof ServicoInsumo insumo) {
      if (insumo.getId() == null) insumo.setId(UUID.randomUUID());
      if (insumo.getAtivo() == null) insumo.setAtivo(Boolean.TRUE);
      if (insumo.getCreatedAt() == null) insumo.setCreatedAt(Instant.now());
      if (insumo.getUpdatedAt() == null) insumo.setUpdatedAt(Instant.now());
    } else if (entidade instanceof EstoqueConfiguracao configuracao) {
      if (configuracao.getId() == null) configuracao.setId(UUID.randomUUID());
      if (configuracao.getAlertaEstoqueMinimoAtivo() == null) {
        configuracao.setAlertaEstoqueMinimoAtivo(Boolean.TRUE);
      }
      if (configuracao.getBloquearSaidaSemSaldo() == null) {
        configuracao.setBloquearSaidaSemSaldo(Boolean.TRUE);
      }
      if (configuracao.getPermitirAjusteNegativoComPermissao() == null) {
        configuracao.setPermitirAjusteNegativoComPermissao(Boolean.FALSE);
      }
      if (configuracao.getDiasCoberturaMeta() == null) configuracao.setDiasCoberturaMeta(15);
      if (configuracao.getCreatedAt() == null) configuracao.setCreatedAt(Instant.now());
      if (configuracao.getUpdatedAt() == null) configuracao.setUpdatedAt(Instant.now());
    }
    return entidade;
  }
}
