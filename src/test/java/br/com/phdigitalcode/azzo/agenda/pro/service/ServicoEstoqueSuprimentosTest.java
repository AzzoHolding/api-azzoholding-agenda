package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import at.favre.lib.crypto.bcrypt.BCrypt;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.AtualizarContagemInventarioRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.CancelarInventarioRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.FornecedorEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.FornecedorEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioContagemRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioContagemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioEstoquePageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.PedidoCompraEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.PedidoCompraEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.PedidoCompraRecebimentoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.TransferenciaEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.TransferenciaEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueFornecedor;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueInventario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueInventarioContagem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoquePedidoCompra;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueTransferencia;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusInventarioEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPedidoCompraEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusTransferenciaEstoque;
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
 * Cobre a segunda fronteira da superficie HTTP de {@code inventory}: inventario/contagem,
 * fornecedor, pedido de compra e transferencia — os 19 endpoints que dependiam das sete entidades
 * portadas depois da Etapa 17.
 *
 * <p>A fronteira 1 (itens, movimentacoes, dashboard, configuracao, servico↔insumo) tem cobertura
 * propria em {@link ServicoEstoqueTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoEstoqueSuprimentosTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
  private static final UUID USUARIO_ID = UUID.randomUUID();
  private static final UUID INVENTARIO_ID = UUID.randomUUID();
  private static final UUID CONTAGEM_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID FORNECEDOR_ID = UUID.randomUUID();
  private static final UUID PEDIDO_ID = UUID.randomUUID();
  private static final UUID TRANSFERENCIA_ID = UUID.randomUUID();
  private static final String SENHA = "senha-do-dono";
  private static final String SENHA_HASH =
      BCrypt.withDefaults().hashToString(4, SENHA.toCharArray());

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
    when(estoqueInventarioRepository.saveAndFlush(any()))
        .thenAnswer(i -> persistir(i.getArgument(0)));
    when(estoqueInventarioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(estoqueInventarioContagemRepository.saveAndFlush(any()))
        .thenAnswer(i -> persistir(i.getArgument(0)));
    when(estoqueInventarioContagemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(estoqueFornecedorRepository.saveAndFlush(any()))
        .thenAnswer(i -> persistir(i.getArgument(0)));
    when(estoqueFornecedorRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(estoquePedidoCompraRepository.saveAndFlush(any()))
        .thenAnswer(i -> persistir(i.getArgument(0)));
    when(estoquePedidoCompraRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(estoqueTransferenciaRepository.saveAndFlush(any()))
        .thenAnswer(i -> persistir(i.getArgument(0)));
    when(estoqueTransferenciaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  // ─── Inventario: criacao e listagem ──────────────────────────────────────

  @Test
  void criarInventarioGravaEmContagemNaoOStatusDefaultDaEntidade() {
    InventarioEstoqueRequest request = new InventarioEstoqueRequest();
    request.nome = "  Contagem   de   Maio ";
    request.observacao = "  parcial  ";

    InventarioEstoqueResponse response = service.criarInventario(request);

    ArgumentCaptor<EstoqueInventario> captor = ArgumentCaptor.forClass(EstoqueInventario.class);
    verify(estoqueInventarioRepository).saveAndFlush(captor.capture());
    EstoqueInventario salvo = captor.getValue();
    assertThat(salvo.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(salvo.getNome()).isEqualTo("Contagem de Maio");
    assertThat(salvo.getObservacao()).isEqualTo("parcial");
    // O @PrePersist defaulta para ABERTO; o service sobrescreve com EM_CONTAGEM.
    assertThat(salvo.getStatus()).isEqualTo(StatusInventarioEstoque.EM_CONTAGEM);
    assertThat(response.status).isEqualTo("EM_CONTAGEM");
  }

  @Test
  void criarInventarioAuditaSemBefore() {
    service.criarInventario(inventarioRequest());

    AuditEventCommand command = capturarAuditoria();
    assertThat(command.action).isEqualTo("STOCK_INVENTORY_CREATE");
    assertThat(command.entityType).isEqualTo("STOCK_INVENTORY");
    assertThat(command.before).isNull();
    assertThat(command.after).isNotNull();
    assertThat(command.entityId).isNotNull();
  }

  @Test
  void criarInventarioExigeNomeNaoEmBranco() {
    InventarioEstoqueRequest request = new InventarioEstoqueRequest();
    request.nome = "   ";

    assertThatThrownBy(() -> service.criarInventario(request))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(400));
  }

  /**
   * Contraste deliberado com {@code listarMovimentacoes}, onde {@code tipo} desconhecido estoura
   * 500: aqui o {@code valueOf} do original esta dentro de um {@code try/catch} vazio.
   */
  @Test
  void listarInventariosIgnoraStatusDesconhecidoSilenciosamente() {
    when(estoqueInventarioRepository.count(any(Specification.class))).thenReturn(0L);
    when(estoqueInventarioRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    InventarioEstoquePageResponse response =
        service.listarInventarios(null, null, null, "NAO_EXISTE");

    assertThat(response.items).isEmpty();
  }

  @Test
  void listarInventariosSemRegistrosTemUmaPaginaENaoTemProxima() {
    when(estoqueInventarioRepository.count(any(Specification.class))).thenReturn(0L);
    when(estoqueInventarioRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    InventarioEstoquePageResponse response = service.listarInventarios(null, null, null, null);

    assertThat(response.page).isEqualTo(1);
    // totalPages e 1 com zero registros, nao 0 — comportamento do original.
    assertThat(response.totalPages).isEqualTo(1);
    assertThat(response.total).isZero();
    assertThat(response.hasNext).isFalse();
  }

  @Test
  void listarInventariosUsaPaginaUmELimiteVinteQuandoNaoInformados() {
    when(estoqueInventarioRepository.count(any(Specification.class))).thenReturn(45L);
    when(estoqueInventarioRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(inventario(StatusInventarioEstoque.EM_CONTAGEM))));

    InventarioEstoquePageResponse response = service.listarInventarios(null, null, null, null);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(estoqueInventarioRepository).findAll(any(Specification.class), captor.capture());
    Pageable pageable = captor.getValue();
    assertThat(pageable.getPageNumber()).isZero(); // page 1 vira offset base-zero
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort())
        .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    assertThat(response.totalPages).isEqualTo(3); // ceil(45 / 20)
    assertThat(response.hasNext).isTrue();
  }

  @Test
  void buscarInventarioDeOutroTenantE404() {
    when(estoqueInventarioRepository.findByIdAndTenantId(INVENTARIO_ID, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.buscarInventario(INVENTARIO_ID))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(404));
  }

  // ─── Inventario: contagens ───────────────────────────────────────────────

  @Test
  void registrarContagemDevolveOInventarioEGravaSaldoAtualComoEsperado() {
    prepararInventario(StatusInventarioEstoque.EM_CONTAGEM);
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item()));
    when(estoqueInventarioContagemRepository.countByInventarioIdAndItemEstoqueIdAndTenantId(
            INVENTARIO_ID, ITEM_ID, TENANT_ID))
        .thenReturn(0L);

    InventarioEstoqueResponse response =
        service.registrarContagemInventario(INVENTARIO_ID, contagemRequest("9.0000"));

    ArgumentCaptor<EstoqueInventarioContagem> captor =
        ArgumentCaptor.forClass(EstoqueInventarioContagem.class);
    verify(estoqueInventarioContagemRepository).saveAndFlush(captor.capture());
    EstoqueInventarioContagem contagem = captor.getValue();
    assertThat(contagem.getQuantidadeContada()).isEqualByComparingTo("9.0000");
    // Esperada e o saldo do item no momento da contagem.
    assertThat(contagem.getQuantidadeEsperada()).isEqualByComparingTo("7.0000");
    assertThat(contagem.getUsuarioId()).isEqualTo(USUARIO_ID);
    // A resposta e o inventario, nao a contagem — assimetria do original.
    assertThat(response.id).isEqualTo(INVENTARIO_ID.toString());
    assertThat(response.status).isEqualTo("EM_CONTAGEM");
  }

  @Test
  void registrarSegundaContagemDoMesmoItemE409() {
    prepararInventario(StatusInventarioEstoque.EM_CONTAGEM);
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item()));
    when(estoqueInventarioContagemRepository.countByInventarioIdAndItemEstoqueIdAndTenantId(
            INVENTARIO_ID, ITEM_ID, TENANT_ID))
        .thenReturn(1L);

    assertThatThrownBy(
            () -> service.registrarContagemInventario(INVENTARIO_ID, contagemRequest("9.0000")))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(409));
    verify(estoqueInventarioContagemRepository, never()).saveAndFlush(any());
  }

  @Test
  void registrarContagemEmInventarioFechadoE409() {
    prepararInventario(StatusInventarioEstoque.FECHADO);

    assertThatThrownBy(
            () -> service.registrarContagemInventario(INVENTARIO_ID, contagemRequest("9.0000")))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(409));
    verifyNoInteractions(itemEstoqueRepository);
  }

  @Test
  void listarContagensResolveNomeDosItensEmLote() {
    prepararInventario(StatusInventarioEstoque.EM_CONTAGEM);
    EstoqueInventarioContagem contagem = contagemExistente();
    when(estoqueInventarioContagemRepository.findByInventarioIdAndTenantIdOrderByCreatedAtDesc(
            INVENTARIO_ID, TENANT_ID))
        .thenReturn(List.of(contagem));
    when(itemEstoqueRepository.findByTenantIdAndIdIn(eq(TENANT_ID), anyCollection()))
        .thenReturn(List.of(item()));

    List<InventarioContagemResponse> contagens = service.listarContagensInventario(INVENTARIO_ID);

    assertThat(contagens).hasSize(1);
    assertThat(contagens.get(0).itemNome).isEqualTo("Shampoo");
    assertThat(contagens.get(0).itemUnidadeMedida).isEqualTo("UN");
    // diferenca = contada − esperada.
    assertThat(contagens.get(0).diferenca).isEqualByComparingTo("2.0000");
  }

  @Test
  void listarContagensVaziaNemConsultaOsItens() {
    prepararInventario(StatusInventarioEstoque.EM_CONTAGEM);
    when(estoqueInventarioContagemRepository.findByInventarioIdAndTenantIdOrderByCreatedAtDesc(
            INVENTARIO_ID, TENANT_ID))
        .thenReturn(List.of());

    assertThat(service.listarContagensInventario(INVENTARIO_ID)).isEmpty();

    verify(itemEstoqueRepository, never()).findByTenantIdAndIdIn(any(), anyCollection());
  }

  @Test
  void atualizarContagemDevolveAContagemECarimbaUsuarioEUpdatedAt() {
    prepararInventario(StatusInventarioEstoque.EM_CONTAGEM);
    EstoqueInventarioContagem contagem = contagemExistente();
    Instant updatedAtOriginal = contagem.getUpdatedAt();
    when(estoqueInventarioContagemRepository.findByIdAndInventarioIdAndTenantId(
            CONTAGEM_ID, INVENTARIO_ID, TENANT_ID))
        .thenReturn(Optional.of(contagem));
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item()));

    AtualizarContagemInventarioRequest request = new AtualizarContagemInventarioRequest();
    request.quantidadeContada = new BigDecimal("5.0000");
    request.observacao = "  recontado  ";

    InventarioContagemResponse response =
        service.atualizarContagemInventario(INVENTARIO_ID, CONTAGEM_ID, request);

    // A resposta e a contagem, nao o inventario — o inverso de registrarContagemInventario.
    assertThat(response.id).isEqualTo(CONTAGEM_ID.toString());
    assertThat(response.quantidadeContada).isEqualByComparingTo("5.0000");
    assertThat(response.diferenca).isEqualByComparingTo("-2.0000");
    assertThat(response.observacao).isEqualTo("recontado");
    assertThat(contagem.getUsuarioAtualizacaoId()).isEqualTo(USUARIO_ID);
    // A entidade de contagem nao tem @PreUpdate: o carimbo e manual.
    assertThat(contagem.getUpdatedAt()).isAfter(updatedAtOriginal);
  }

  @Test
  void atualizarContagemDeInventarioFinalizadoE409() {
    prepararInventario(StatusInventarioEstoque.CANCELADO);
    AtualizarContagemInventarioRequest request = new AtualizarContagemInventarioRequest();
    request.quantidadeContada = new BigDecimal("5.0000");

    assertThatThrownBy(
            () -> service.atualizarContagemInventario(INVENTARIO_ID, CONTAGEM_ID, request))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(409));
    verify(estoqueInventarioContagemRepository, never()).save(any());
  }

  @Test
  void atualizarContagemInexistenteE404() {
    prepararInventario(StatusInventarioEstoque.EM_CONTAGEM);
    when(estoqueInventarioContagemRepository.findByIdAndInventarioIdAndTenantId(
            CONTAGEM_ID, INVENTARIO_ID, TENANT_ID))
        .thenReturn(Optional.empty());
    AtualizarContagemInventarioRequest request = new AtualizarContagemInventarioRequest();
    request.quantidadeContada = new BigDecimal("5.0000");

    assertThatThrownBy(
            () -> service.atualizarContagemInventario(INVENTARIO_ID, CONTAGEM_ID, request))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(404));
  }

  // ─── Inventario: fechamento e cancelamento ───────────────────────────────

  @Test
  void fecharInventarioCarimbaDataFechamentoEAudita() {
    prepararInventario(StatusInventarioEstoque.EM_CONTAGEM);

    InventarioEstoqueResponse response = service.fecharInventario(INVENTARIO_ID);

    assertThat(response.status).isEqualTo("FECHADO");
    assertThat(response.dataFechamento).isNotNull();
    assertThat(capturarAuditoria().action).isEqualTo("STOCK_INVENTORY_CLOSE");
  }

  @Test
  void fecharInventarioJaFechadoEIdempotenteENaoAudita() {
    prepararInventario(StatusInventarioEstoque.FECHADO);

    assertThat(service.fecharInventario(INVENTARIO_ID).status).isEqualTo("FECHADO");

    verify(estoqueInventarioRepository, never()).save(any());
    verifyNoInteractions(auditService);
  }

  /** Assimetria do original: o fechamento nao bloqueia CANCELADO, so o proprio FECHADO. */
  @Test
  void inventarioCanceladoPodeSerFechado() {
    prepararInventario(StatusInventarioEstoque.CANCELADO);

    assertThat(service.fecharInventario(INVENTARIO_ID).status).isEqualTo("FECHADO");
  }

  @Test
  void cancelarInventarioComSenhaCorretaAuditaComMotivo() {
    prepararUsuario();
    prepararInventario(StatusInventarioEstoque.EM_CONTAGEM);

    InventarioEstoqueResponse response =
        service.cancelarInventario(INVENTARIO_ID, cancelarRequest(SENHA, "erro de contagem"));

    assertThat(response.status).isEqualTo("CANCELADO");
    AuditEventCommand command = capturarAuditoria();
    assertThat(command.action).isEqualTo("STOCK_INVENTORY_CANCEL");
    assertThat(command.after).isInstanceOf(Map.class);
    assertThat(comoMapa(command.after))
        .containsEntry("status", "CANCELADO")
        .containsEntry("motivo", "erro de contagem");
  }

  @Test
  void cancelarInventarioComSenhaIncorretaE401ENaoCarregaOInventario() {
    prepararUsuario();

    assertThatThrownBy(
            () -> service.cancelarInventario(INVENTARIO_ID, cancelarRequest("errada", null)))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(401));
    // A ordem do original: identidade e senha antes de sequer buscar o inventario.
    verify(estoqueInventarioRepository, never()).findByIdAndTenantId(any(), any());
  }

  @Test
  void cancelarInventarioSemUsuarioIdentificadoE404() {
    when(authenticatedUser.idOuNulo()).thenReturn(null);

    assertThatThrownBy(
            () -> service.cancelarInventario(INVENTARIO_ID, cancelarRequest(SENHA, null)))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(404));
    verifyNoInteractions(usuarioRepository);
  }

  @Test
  void cancelarInventarioJaFechadoE409() {
    prepararUsuario();
    prepararInventario(StatusInventarioEstoque.FECHADO);

    assertThatThrownBy(
            () -> service.cancelarInventario(INVENTARIO_ID, cancelarRequest(SENHA, null)))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(409));
  }

  @Test
  void cancelarInventarioJaCanceladoDevolveComoEstaSemAuditar() {
    prepararUsuario();
    prepararInventario(StatusInventarioEstoque.CANCELADO);

    assertThat(service.cancelarInventario(INVENTARIO_ID, cancelarRequest(SENHA, null)).status)
        .isEqualTo("CANCELADO");

    verify(estoqueInventarioRepository, never()).save(any());
    verifyNoInteractions(auditService);
  }

  // ─── Fornecedores ────────────────────────────────────────────────────────

  @Test
  void criarFornecedorDefaultaAtivoEAudita() {
    FornecedorEstoqueRequest request = new FornecedorEstoqueRequest();
    request.nome = "  Distribuidora   Beleza ";
    request.documento = "  12.345.678/0001-90 ";

    FornecedorEstoqueResponse response = service.criarFornecedor(request);

    assertThat(response.nome).isEqualTo("Distribuidora Beleza");
    assertThat(response.documento).isEqualTo("12.345.678/0001-90");
    assertThat(response.ativo).isTrue();
    assertThat(capturarAuditoria().action).isEqualTo("STOCK_SUPPLIER_CREATE");
  }

  @Test
  void criarFornecedorRespeitaAtivoFalseExplicito() {
    FornecedorEstoqueRequest request = fornecedorRequest();
    request.ativo = Boolean.FALSE;

    assertThat(service.criarFornecedor(request).ativo).isFalse();
  }

  /** {@code PUT} de substituicao total: campo ausente no corpo limpa o valor gravado. */
  @Test
  void atualizarFornecedorLimpaCamposAusentesNoCorpo() {
    EstoqueFornecedor existente = fornecedorExistente();
    when(estoqueFornecedorRepository.findByIdAndTenantId(FORNECEDOR_ID, TENANT_ID))
        .thenReturn(Optional.of(existente));

    FornecedorEstoqueRequest request = new FornecedorEstoqueRequest();
    request.nome = "Novo Nome";

    FornecedorEstoqueResponse response = service.atualizarFornecedor(FORNECEDOR_ID, request);

    assertThat(response.nome).isEqualTo("Novo Nome");
    assertThat(response.documento).isNull();
    assertThat(response.email).isNull();
    assertThat(response.telefone).isNull();
    assertThat(response.contato).isNull();
  }

  /** {@code ativo} e a unica excecao a substituicao total: ausente preserva o valor gravado. */
  @Test
  void atualizarFornecedorSemAtivoPreservaOValorGravado() {
    EstoqueFornecedor existente = fornecedorExistente();
    existente.setAtivo(Boolean.FALSE);
    when(estoqueFornecedorRepository.findByIdAndTenantId(FORNECEDOR_ID, TENANT_ID))
        .thenReturn(Optional.of(existente));

    FornecedorEstoqueResponse response =
        service.atualizarFornecedor(FORNECEDOR_ID, fornecedorRequest());

    assertThat(response.ativo).isFalse();
    AuditEventCommand command = capturarAuditoria();
    assertThat(command.action).isEqualTo("STOCK_SUPPLIER_UPDATE");
    assertThat(command.before).isNotNull();
    assertThat(command.after).isNotNull();
  }

  @Test
  void atualizarFornecedorDeOutroTenantE404() {
    when(estoqueFornecedorRepository.findByIdAndTenantId(FORNECEDOR_ID, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.atualizarFornecedor(FORNECEDOR_ID, fornecedorRequest()))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(404));
  }

  // ─── Pedidos de compra ───────────────────────────────────────────────────

  /** O tenant do pedido vem do fornecedor resolvido, nao do contexto — como no original. */
  @Test
  void criarPedidoCompraHerdaTenantDoFornecedorEIniciaPendenteIgualAoTotal() {
    EstoqueFornecedor fornecedor = fornecedorExistente();
    fornecedor.setTenantId(OUTRO_TENANT_ID);
    when(estoqueFornecedorRepository.findByIdAndTenantId(FORNECEDOR_ID, TENANT_ID))
        .thenReturn(Optional.of(fornecedor));

    PedidoCompraEstoqueRequest request = new PedidoCompraEstoqueRequest();
    request.fornecedorId = FORNECEDOR_ID.toString();
    request.valorTotal = new BigDecimal("250.00");
    request.quantidadeItens = 12;

    PedidoCompraEstoqueResponse response = service.criarPedidoCompra(request);

    ArgumentCaptor<EstoquePedidoCompra> captor = ArgumentCaptor.forClass(EstoquePedidoCompra.class);
    verify(estoquePedidoCompraRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(OUTRO_TENANT_ID);
    assertThat(response.status).isEqualTo("RASCUNHO");
    assertThat(response.quantidadeItens).isEqualTo(12);
    assertThat(response.quantidadePendente).isEqualTo(12);
    assertThat(response.fornecedorNome).isEqualTo("Distribuidora");
  }

  @Test
  void listarPedidosCompraResolveOsFornecedoresEmLote() {
    EstoquePedidoCompra pedido = pedidoExistente(10, 10);
    when(estoquePedidoCompraRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of(pedido));
    when(estoqueFornecedorRepository.findByTenantIdAndIdIn(eq(TENANT_ID), anyCollection()))
        .thenReturn(List.of(fornecedorExistente()));

    List<PedidoCompraEstoqueResponse> pedidos =
        service.listarPedidosCompra(null, null, null, null);

    assertThat(pedidos).hasSize(1);
    assertThat(pedidos.get(0).fornecedorNome).isEqualTo("Distribuidora");
  }

  @Test
  void listarPedidosCompraVaziaNemConsultaOsFornecedores() {
    when(estoquePedidoCompraRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());

    assertThat(service.listarPedidosCompra(null, null, null, null)).isEmpty();

    verify(estoqueFornecedorRepository, never()).findByTenantIdAndIdIn(any(), anyCollection());
  }

  @Test
  void receberPedidoParcialmenteMantemPendenteEMudaStatus() {
    prepararPedido(10, 10);

    PedidoCompraEstoqueResponse response =
        service.receberPedidoCompra(PEDIDO_ID, recebimentoRequest(4, "chegou parcial"));

    assertThat(response.quantidadePendente).isEqualTo(6);
    assertThat(response.status).isEqualTo("PARCIALMENTE_RECEBIDO");
    AuditEventCommand command = capturarAuditoria();
    assertThat(command.action).isEqualTo("STOCK_PURCHASE_RECEIVE");
    assertThat(comoMapa(command.metadata)).containsEntry("quantidadeRecebida", 4);
  }

  @Test
  void receberPedidoAteZerarOPendenteMarcaRecebido() {
    prepararPedido(10, 4);

    PedidoCompraEstoqueResponse response =
        service.receberPedidoCompra(PEDIDO_ID, recebimentoRequest(4, null));

    assertThat(response.quantidadePendente).isZero();
    assertThat(response.status).isEqualTo("RECEBIDO");
  }

  /** A {@code observacao} do corpo substitui a do pedido mesmo quando vem nula. */
  @Test
  void receberPedidoComObservacaoNulaApagaAObservacaoGravada() {
    EstoquePedidoCompra pedido = prepararPedido(10, 10);
    pedido.setObservacao("observacao anterior");

    assertThat(service.receberPedidoCompra(PEDIDO_ID, recebimentoRequest(1, null)).observacao)
        .isNull();
  }

  /** A validacao de bean so exige {@code @NotNull}; o zero e barrado no service, com 400. */
  @Test
  void receberQuantidadeNaoPositivaE400() {
    prepararPedido(10, 10);

    assertThatThrownBy(() -> service.receberPedidoCompra(PEDIDO_ID, recebimentoRequest(0, null)))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(400));
    verify(estoquePedidoCompraRepository, never()).save(any());
  }

  @Test
  void receberAcimaDaQuantidadePendenteE409() {
    prepararPedido(10, 3);

    assertThatThrownBy(() -> service.receberPedidoCompra(PEDIDO_ID, recebimentoRequest(4, null)))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(409));
  }

  @Test
  void buscarPedidoCompraDeOutroTenantE404() {
    when(estoquePedidoCompraRepository.findByIdAndTenantId(PEDIDO_ID, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.buscarPedidoCompra(PEDIDO_ID))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(404));
  }

  // ─── Transferencias ──────────────────────────────────────────────────────

  @Test
  void criarTransferenciaNormalizaOrigemDestinoEIniciaRascunho() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item()));

    TransferenciaEstoqueRequest request = new TransferenciaEstoqueRequest();
    request.origem = "  Loja   Centro ";
    request.destino = " Loja Sul  ";
    request.itemEstoqueId = ITEM_ID.toString();
    request.quantidade = new BigDecimal("3.0000");

    TransferenciaEstoqueResponse response = service.criarTransferencia(request);

    assertThat(response.origem).isEqualTo("Loja Centro");
    assertThat(response.destino).isEqualTo("Loja Sul");
    assertThat(response.status).isEqualTo("RASCUNHO");
    assertThat(response.itemNome).isEqualTo("Shampoo");
    assertThat(capturarAuditoria().action).isEqualTo("STOCK_TRANSFER_CREATE");
  }

  @Test
  void criarTransferenciaComItemDeOutroTenantE404() {
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID)).thenReturn(Optional.empty());

    TransferenciaEstoqueRequest request = new TransferenciaEstoqueRequest();
    request.origem = "A";
    request.destino = "B";
    request.itemEstoqueId = ITEM_ID.toString();
    request.quantidade = new BigDecimal("1.0000");

    assertThatThrownBy(() -> service.criarTransferencia(request))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(404));
  }

  @Test
  void enviarTransferenciaPromoveORascunho() {
    prepararTransferencia(StatusTransferenciaEstoque.RASCUNHO);

    assertThat(service.enviarTransferencia(TRANSFERENCIA_ID).status).isEqualTo("ENVIADA");
    assertThat(capturarAuditoria().action).isEqualTo("STOCK_TRANSFER_SEND");
  }

  /** So o rascunho e promovido; qualquer outro status volta intacto e sem auditoria. */
  @Test
  void enviarTransferenciaJaEnviadaDevolveIntactaSemAuditar() {
    prepararTransferencia(StatusTransferenciaEstoque.ENVIADA);

    assertThat(service.enviarTransferencia(TRANSFERENCIA_ID).status).isEqualTo("ENVIADA");

    verify(estoqueTransferenciaRepository, never()).save(any());
    verifyNoInteractions(auditService);
  }

  /**
   * Assimetria do original, preservada: o recebimento nao checa o status anterior — um rascunho vai
   * direto para RECEBIDA, sem passar por ENVIADA.
   */
  @Test
  void receberTransferenciaAceitaRascunhoSemPassarPorEnviada() {
    prepararTransferencia(StatusTransferenciaEstoque.RASCUNHO);

    assertThat(service.receberTransferencia(TRANSFERENCIA_ID).status).isEqualTo("RECEBIDA");
    assertThat(capturarAuditoria().action).isEqualTo("STOCK_TRANSFER_RECEIVE");
  }

  @Test
  void listarTransferenciasSemPaginacaoNaoUsaPageable() {
    when(estoqueTransferenciaRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of(transferencia(StatusTransferenciaEstoque.RASCUNHO)));
    when(itemEstoqueRepository.findByTenantIdAndIdIn(eq(TENANT_ID), anyCollection()))
        .thenReturn(List.of(item()));

    List<TransferenciaEstoqueResponse> transferencias =
        service.listarTransferencias(null, null, null, null);

    assertThat(transferencias).hasSize(1);
    assertThat(transferencias.get(0).itemNome).isEqualTo("Shampoo");
    verify(estoqueTransferenciaRepository, never())
        .findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void cursorPelaMetadeNaoInvalidaAListagem() {
    when(estoqueTransferenciaRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());

    // Só cursorCreatedAt, sem cursorId: equivale a nenhum cursor, nao a 400.
    assertThat(service.listarTransferencias(null, null, Instant.now().toString(), null)).isEmpty();
  }

  @Test
  void cursorMalFormadoE400() {
    assertThatThrownBy(() -> service.listarFornecedores(null, null, "ontem", UUID.randomUUID().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(400));
  }

  // ─── Fixtures ────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private static Map<String, Object> comoMapa(Object payload) {
    assertThat(payload).isInstanceOf(Map.class);
    return (Map<String, Object>) payload;
  }

  private void prepararUsuario() {
    Usuario usuario = new Usuario();
    usuario.setId(USUARIO_ID);
    usuario.setTenantId(TENANT_ID);
    usuario.setPasswordHash(SENHA_HASH);
    when(usuarioRepository.findByIdAndTenantId(USUARIO_ID, TENANT_ID))
        .thenReturn(Optional.of(usuario));
  }

  private EstoqueInventario prepararInventario(StatusInventarioEstoque status) {
    EstoqueInventario inventario = inventario(status);
    when(estoqueInventarioRepository.findByIdAndTenantId(INVENTARIO_ID, TENANT_ID))
        .thenReturn(Optional.of(inventario));
    return inventario;
  }

  private EstoquePedidoCompra prepararPedido(Integer itens, Integer pendente) {
    EstoquePedidoCompra pedido = pedidoExistente(itens, pendente);
    when(estoquePedidoCompraRepository.findByIdAndTenantId(PEDIDO_ID, TENANT_ID))
        .thenReturn(Optional.of(pedido));
    when(estoqueFornecedorRepository.findByIdAndTenantId(FORNECEDOR_ID, TENANT_ID))
        .thenReturn(Optional.of(fornecedorExistente()));
    return pedido;
  }

  private EstoqueTransferencia prepararTransferencia(StatusTransferenciaEstoque status) {
    EstoqueTransferencia transferencia = transferencia(status);
    when(estoqueTransferenciaRepository.findByIdAndTenantId(TRANSFERENCIA_ID, TENANT_ID))
        .thenReturn(Optional.of(transferencia));
    when(itemEstoqueRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
        .thenReturn(Optional.of(item()));
    return transferencia;
  }

  private InventarioEstoqueRequest inventarioRequest() {
    InventarioEstoqueRequest request = new InventarioEstoqueRequest();
    request.nome = "Contagem de Maio";
    return request;
  }

  private InventarioContagemRequest contagemRequest(String quantidade) {
    InventarioContagemRequest request = new InventarioContagemRequest();
    request.itemEstoqueId = ITEM_ID.toString();
    request.quantidadeContada = new BigDecimal(quantidade);
    return request;
  }

  private CancelarInventarioRequest cancelarRequest(String senha, String motivo) {
    CancelarInventarioRequest request = new CancelarInventarioRequest();
    request.senha = senha;
    request.motivo = motivo;
    return request;
  }

  private FornecedorEstoqueRequest fornecedorRequest() {
    FornecedorEstoqueRequest request = new FornecedorEstoqueRequest();
    request.nome = "Distribuidora";
    return request;
  }

  private PedidoCompraRecebimentoRequest recebimentoRequest(Integer quantidade, String observacao) {
    PedidoCompraRecebimentoRequest request = new PedidoCompraRecebimentoRequest();
    request.quantidadeRecebida = quantidade;
    request.observacao = observacao;
    return request;
  }

  private EstoqueInventario inventario(StatusInventarioEstoque status) {
    EstoqueInventario inventario = new EstoqueInventario();
    inventario.setId(INVENTARIO_ID);
    inventario.setTenantId(TENANT_ID);
    inventario.setNome("Contagem de Maio");
    inventario.setStatus(status);
    inventario.setDataAbertura(Instant.now());
    inventario.setCreatedAt(Instant.now());
    inventario.setUpdatedAt(Instant.now());
    return inventario;
  }

  private EstoqueInventarioContagem contagemExistente() {
    EstoqueInventarioContagem contagem = new EstoqueInventarioContagem();
    contagem.setId(CONTAGEM_ID);
    contagem.setTenantId(TENANT_ID);
    contagem.setInventarioId(INVENTARIO_ID);
    contagem.setItemEstoqueId(ITEM_ID);
    contagem.setQuantidadeEsperada(new BigDecimal("7.0000"));
    contagem.setQuantidadeContada(new BigDecimal("9.0000"));
    contagem.setUsuarioId(USUARIO_ID);
    contagem.setCreatedAt(Instant.now().minusSeconds(60));
    contagem.setUpdatedAt(Instant.now().minusSeconds(60));
    return contagem;
  }

  private ItemEstoque item() {
    ItemEstoque item = new ItemEstoque();
    item.setId(ITEM_ID);
    item.setTenantId(TENANT_ID);
    item.setNome("Shampoo");
    item.setUnidadeMedida("UN");
    item.setSaldoAtual(new BigDecimal("7.0000"));
    item.setEstoqueMinimo(new BigDecimal("2.0000"));
    item.setAtivo(Boolean.TRUE);
    item.setCreatedAt(Instant.now());
    item.setUpdatedAt(Instant.now());
    return item;
  }

  private EstoqueFornecedor fornecedorExistente() {
    EstoqueFornecedor fornecedor = new EstoqueFornecedor();
    fornecedor.setId(FORNECEDOR_ID);
    fornecedor.setTenantId(TENANT_ID);
    fornecedor.setNome("Distribuidora");
    fornecedor.setDocumento("12345678000190");
    fornecedor.setEmail("contato@distribuidora.com");
    fornecedor.setTelefone("11999990000");
    fornecedor.setContato("Marcia");
    fornecedor.setAtivo(Boolean.TRUE);
    fornecedor.setCreatedAt(Instant.now());
    fornecedor.setUpdatedAt(Instant.now());
    return fornecedor;
  }

  private EstoquePedidoCompra pedidoExistente(Integer itens, Integer pendente) {
    EstoquePedidoCompra pedido = new EstoquePedidoCompra();
    pedido.setId(PEDIDO_ID);
    pedido.setTenantId(TENANT_ID);
    pedido.setFornecedorId(FORNECEDOR_ID);
    pedido.setStatus(StatusPedidoCompraEstoque.RASCUNHO);
    pedido.setValorTotal(new BigDecimal("250.00"));
    pedido.setQuantidadeItens(itens);
    pedido.setQuantidadePendente(pendente);
    pedido.setCreatedAt(Instant.now());
    pedido.setUpdatedAt(Instant.now());
    return pedido;
  }

  private EstoqueTransferencia transferencia(StatusTransferenciaEstoque status) {
    EstoqueTransferencia transferencia = new EstoqueTransferencia();
    transferencia.setId(TRANSFERENCIA_ID);
    transferencia.setTenantId(TENANT_ID);
    transferencia.setOrigem("Loja Centro");
    transferencia.setDestino("Loja Sul");
    transferencia.setItemEstoqueId(ITEM_ID);
    transferencia.setQuantidade(new BigDecimal("3.0000"));
    transferencia.setStatus(status);
    transferencia.setCreatedAt(Instant.now());
    transferencia.setUpdatedAt(Instant.now());
    return transferencia;
  }

  private AuditEventCommand capturarAuditoria() {
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    return captor.getValue();
  }

  /**
   * Simula o {@code @PrePersist} das entidades, que so roda de verdade com um EntityManager. E o que
   * gera o id usado no payload de auditoria e nos timestamps devolvidos na mesma chamada.
   */
  private Object persistir(Object entidade) {
    Instant agora = Instant.now();
    if (entidade instanceof EstoqueInventario inventario) {
      if (inventario.getId() == null) inventario.setId(UUID.randomUUID());
      if (inventario.getStatus() == null) inventario.setStatus(StatusInventarioEstoque.ABERTO);
      if (inventario.getDataAbertura() == null) inventario.setDataAbertura(agora);
      if (inventario.getCreatedAt() == null) inventario.setCreatedAt(agora);
      if (inventario.getUpdatedAt() == null) inventario.setUpdatedAt(agora);
    } else if (entidade instanceof EstoqueInventarioContagem contagem) {
      if (contagem.getId() == null) contagem.setId(UUID.randomUUID());
      if (contagem.getCreatedAt() == null) contagem.setCreatedAt(agora);
      if (contagem.getUpdatedAt() == null) contagem.setUpdatedAt(agora);
    } else if (entidade instanceof EstoqueFornecedor fornecedor) {
      if (fornecedor.getId() == null) fornecedor.setId(UUID.randomUUID());
      if (fornecedor.getAtivo() == null) fornecedor.setAtivo(Boolean.TRUE);
      if (fornecedor.getCreatedAt() == null) fornecedor.setCreatedAt(agora);
      if (fornecedor.getUpdatedAt() == null) fornecedor.setUpdatedAt(agora);
    } else if (entidade instanceof EstoquePedidoCompra pedido) {
      if (pedido.getId() == null) pedido.setId(UUID.randomUUID());
      if (pedido.getStatus() == null) pedido.setStatus(StatusPedidoCompraEstoque.RASCUNHO);
      if (pedido.getCreatedAt() == null) pedido.setCreatedAt(agora);
      if (pedido.getUpdatedAt() == null) pedido.setUpdatedAt(agora);
    } else if (entidade instanceof EstoqueTransferencia transferencia) {
      if (transferencia.getId() == null) transferencia.setId(UUID.randomUUID());
      if (transferencia.getStatus() == null) {
        transferencia.setStatus(StatusTransferenciaEstoque.RASCUNHO);
      }
      if (transferencia.getCreatedAt() == null) transferencia.setCreatedAt(agora);
      if (transferencia.getUpdatedAt() == null) transferencia.setUpdatedAt(agora);
    }
    return entidade;
  }
}
