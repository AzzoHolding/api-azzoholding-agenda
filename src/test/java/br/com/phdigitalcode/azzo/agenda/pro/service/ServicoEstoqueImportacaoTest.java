package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ImportacaoErroLinhaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ImportacaoEstoqueJobResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ImportacaoResultadoArquivoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueErroLinha;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoImportacaoEstoque;
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
 * Cobre a terceira fronteira da superficie HTTP de {@code inventory}: os 7 endpoints de
 * {@code /importacoes*} — registro, consulta, cancelamento e modelo de planilha.
 *
 * <p>O processamento do job tem cobertura propria em
 * {@link ProcessadorImportacaoEstoqueServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoEstoqueImportacaoTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID OUTRO_TENANT = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
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
    when(importacaoEstoqueJobRepository.saveAndFlush(any()))
        .thenAnswer(i -> persistir(i.getArgument(0)));
    when(importacaoEstoqueJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  // ── criarImportacao ────────────────────────────────────────────────────────

  @Test
  void criarImportacaoGravaJobRecebidoComHashChaveEContadoresZerados() {
    ImportacaoEstoqueJobResponse response =
        service.criarImportacao("ITENS", Boolean.TRUE, "abc123", "tenant/x/arquivo.xlsx");

    ArgumentCaptor<ImportacaoEstoqueJob> captor =
        ArgumentCaptor.forClass(ImportacaoEstoqueJob.class);
    verify(importacaoEstoqueJobRepository).saveAndFlush(captor.capture());
    ImportacaoEstoqueJob salvo = captor.getValue();
    assertThat(salvo.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(salvo.getTipoImportacao()).isEqualTo(TipoImportacaoEstoque.ITENS);
    assertThat(salvo.getStatus()).isEqualTo(StatusImportacaoEstoque.RECEBIDO);
    assertThat(salvo.getDryRun()).isTrue();
    assertThat(salvo.getTotalLinhas()).isZero();
    assertThat(salvo.getLinhasProcessadas()).isZero();
    assertThat(salvo.getLinhasComErro()).isZero();
    assertThat(salvo.getArquivoSha256()).isEqualTo("abc123");
    assertThat(salvo.getArquivoStorageKey()).isEqualTo("tenant/x/arquivo.xlsx");

    assertThat(response.jobId).isNotNull();
    assertThat(response.status).isEqualTo("RECEBIDO");
    assertThat(response.dryRun).isTrue();
  }

  /** Tipo ausente ou em branco cai no default silencioso {@code ENTRADAS} (assimetria do original). */
  @Test
  void criarImportacaoSemTipoUsaEntradasComoDefault() {
    assertThat(service.criarImportacao(null, null, null, "k").tipoImportacao).isEqualTo("ENTRADAS");
    assertThat(service.criarImportacao("   ", null, null, "k").tipoImportacao).isEqualTo("ENTRADAS");
  }

  /** {@code dryRun} nulo vira {@code false} explicitamente, nao fica nulo na resposta. */
  @Test
  void criarImportacaoSemDryRunGravaFalse() {
    assertThat(service.criarImportacao("ITENS", null, null, "k").dryRun).isFalse();
  }

  /**
   * Assimetria do original: tipo <b>invalido</b> aqui estoura o {@code valueOf} cru, ao contrario
   * de {@code gerarModeloImportacao}, que devolve 400 com mensagem util.
   */
  @Test
  void criarImportacaoComTipoInvalidoEstouraValueOfCru() {
    assertThatThrownBy(() -> service.criarImportacao("ITEM", null, null, "k"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(importacaoEstoqueJobRepository, never()).saveAndFlush(any());
  }

  @Test
  void criarImportacaoRegistraAuditoriaDeCriacaoSemBefore() {
    service.criarImportacao("AJUSTES", null, "hash", "k");

    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    AuditEventCommand command = captor.getValue();
    assertThat(command.action).isEqualTo("STOCK_IMPORT_CREATE");
    assertThat(command.entityType).isEqualTo("STOCK_IMPORT_JOB");
    assertThat(command.entityId).isNotNull();
    assertThat(command.before).isNull();
    assertThat(command.after).isInstanceOf(ImportacaoEstoqueJobResponse.class);
  }

  // ── listagem e busca ───────────────────────────────────────────────────────

  @Test
  void listarImportacoesMapeiaTodosOsCamposDoJob() {
    ImportacaoEstoqueJob job = job(StatusImportacaoEstoque.CONCLUIDO_COM_ERROS);
    job.setTotalLinhas(10);
    job.setLinhasProcessadas(7);
    job.setLinhasComErro(3);
    job.setFinishedAt(Instant.parse("2026-03-01T10:00:00Z"));
    when(importacaoEstoqueJobRepository.findByTenantIdOrderByCreatedAtDesc(TENANT_ID))
        .thenReturn(List.of(job));

    List<ImportacaoEstoqueJobResponse> resultado = service.listarImportacoes();

    assertThat(resultado).hasSize(1);
    ImportacaoEstoqueJobResponse response = resultado.get(0);
    assertThat(response.jobId).isEqualTo(JOB_ID.toString());
    assertThat(response.status).isEqualTo("CONCLUIDO_COM_ERROS");
    assertThat(response.totalLinhas).isEqualTo(10);
    assertThat(response.linhasProcessadas).isEqualTo(7);
    assertThat(response.linhasComErro).isEqualTo(3);
    assertThat(response.finishedAt).isEqualTo("2026-03-01T10:00:00Z");
  }

  /** Contadores nulos no banco viram 0 na resposta, nunca {@code null}. */
  @Test
  void toImportacaoJobResponseTrocaContadoresNulosPorZero() {
    ImportacaoEstoqueJob job = job(StatusImportacaoEstoque.RECEBIDO);
    job.setTotalLinhas(null);
    job.setLinhasProcessadas(null);
    job.setLinhasComErro(null);
    when(importacaoEstoqueJobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID))
        .thenReturn(Optional.of(job));

    ImportacaoEstoqueJobResponse response = service.buscarImportacao(JOB_ID);

    assertThat(response.totalLinhas).isZero();
    assertThat(response.linhasProcessadas).isZero();
    assertThat(response.linhasComErro).isZero();
  }

  @Test
  void buscarImportacaoDeOutroTenantEQuatrocentosEQuatro() {
    when(importacaoEstoqueJobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.buscarImportacao(JOB_ID))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Job de importacao nao encontrado.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  /** A busca de erros usa o tenant do <b>job</b>, nao o do contexto — o job ja veio filtrado. */
  @Test
  void listarErrosImportacaoConsultaPeloTenantDoJob() {
    ImportacaoEstoqueJob job = job(StatusImportacaoEstoque.CONCLUIDO_COM_ERROS);
    job.setTenantId(OUTRO_TENANT);
    when(importacaoEstoqueJobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID))
        .thenReturn(Optional.of(job));
    ImportacaoEstoqueErroLinha erro = new ImportacaoEstoqueErroLinha();
    erro.setLinha(4);
    erro.setColuna("sku");
    erro.setCodigoErro("ITEM_NAO_ENCONTRADO");
    erro.setMensagem("Item com este SKU nao encontrado no estoque.");
    erro.setValorRecebido("SHAMP-999");
    when(importacaoEstoqueErroLinhaRepository.findByJobIdAndTenantIdOrderByLinhaAsc(
            JOB_ID, OUTRO_TENANT))
        .thenReturn(List.of(erro));

    List<ImportacaoErroLinhaResponse> resultado = service.listarErrosImportacao(JOB_ID);

    assertThat(resultado).hasSize(1);
    assertThat(resultado.get(0).linha).isEqualTo(4);
    assertThat(resultado.get(0).coluna).isEqualTo("sku");
    assertThat(resultado.get(0).codigoErro).isEqualTo("ITEM_NAO_ENCONTRADO");
    assertThat(resultado.get(0).valorRecebido).isEqualTo("SHAMP-999");
  }

  // ── arquivo-resultado ──────────────────────────────────────────────────────

  @Test
  void obterArquivoResultadoDevolveUrlDoProxyEExpiracao() {
    ImportacaoEstoqueJob job = job(StatusImportacaoEstoque.RECEBIDO);
    job.setArquivoStorageKey("tenant/x/arquivo.xlsx");
    when(importacaoEstoqueJobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID))
        .thenReturn(Optional.of(job));
    when(minioStorageService.gerarUrlAssinadaLeitura("tenant/x/arquivo.xlsx", TENANT_ID))
        .thenReturn("http://localhost:8080/api/v1/storage/proxy?key=tenant%2Fx%2Farquivo.xlsx");
    when(minioStorageService.calcularExpiracaoUrlAssinada())
        .thenReturn(Instant.parse("2026-03-01T10:10:00Z"));

    ImportacaoResultadoArquivoResponse response = service.obterArquivoResultado(JOB_ID);

    assertThat(response.downloadUrl).contains("/api/v1/storage/proxy?key=");
    assertThat(response.expiresAt).isEqualTo("2026-03-01T10:10:00Z");
  }

  /**
   * Job ja processado tem a chave zerada pelo processador — o endpoint vira 404 permanente. E a
   * consequencia direta de o original nunca gerar arquivo de saida.
   */
  @Test
  void obterArquivoResultadoDeJobJaProcessadoEQuatrocentosEQuatro() {
    ImportacaoEstoqueJob job = job(StatusImportacaoEstoque.CONCLUIDO);
    job.setArquivoStorageKey(null);
    when(importacaoEstoqueJobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID))
        .thenReturn(Optional.of(job));

    assertThatThrownBy(() -> service.obterArquivoResultado(JOB_ID))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Arquivo de resultado indisponivel para este job.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void obterArquivoResultadoComStorageIndisponivelEQuinhentosETres() {
    ImportacaoEstoqueJob job = job(StatusImportacaoEstoque.RECEBIDO);
    job.setArquivoStorageKey("tenant/x/arquivo.xlsx");
    when(importacaoEstoqueJobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID))
        .thenReturn(Optional.of(job));
    when(minioStorageService.gerarUrlAssinadaLeitura(any(), any()))
        .thenThrow(new IllegalStateException("Storage key fora do escopo do tenant."));

    assertThatThrownBy(() -> service.obterArquivoResultado(JOB_ID))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(503);
  }

  // ── cancelamento ───────────────────────────────────────────────────────────

  @Test
  void cancelarImportacaoMarcaCanceladoComFinishedAtEAuditaAntesEDepois() {
    ImportacaoEstoqueJob job = job(StatusImportacaoEstoque.RECEBIDO);
    when(importacaoEstoqueJobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID))
        .thenReturn(Optional.of(job));

    ImportacaoEstoqueJobResponse response = service.cancelarImportacao(JOB_ID);

    assertThat(response.status).isEqualTo("CANCELADO");
    assertThat(job.getFinishedAt()).isNotNull();
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    assertThat(captor.getValue().action).isEqualTo("STOCK_IMPORT_CANCEL");
    assertThat(((ImportacaoEstoqueJobResponse) captor.getValue().before).status)
        .isEqualTo("RECEBIDO");
    assertThat(((ImportacaoEstoqueJobResponse) captor.getValue().after).status)
        .isEqualTo("CANCELADO");
  }

  @Test
  void cancelarImportacaoEmProcessamentoAindaEPermitido() {
    ImportacaoEstoqueJob job = job(StatusImportacaoEstoque.PROCESSANDO);
    when(importacaoEstoqueJobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID))
        .thenReturn(Optional.of(job));

    assertThat(service.cancelarImportacao(JOB_ID).status).isEqualTo("CANCELADO");
  }

  @Test
  void cancelarImportacaoJaTerminalEConflito() {
    for (StatusImportacaoEstoque terminal :
        List.of(
            StatusImportacaoEstoque.CONCLUIDO,
            StatusImportacaoEstoque.CONCLUIDO_COM_ERROS,
            StatusImportacaoEstoque.FALHOU,
            StatusImportacaoEstoque.CANCELADO)) {
      ImportacaoEstoqueJob job = job(terminal);
      when(importacaoEstoqueJobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID))
          .thenReturn(Optional.of(job));

      assertThatThrownBy(() -> service.cancelarImportacao(JOB_ID))
          .isInstanceOf(ApiClientErrorException.class)
          .hasMessage("Job ja finalizado.")
          .extracting(e -> ((ApiClientErrorException) e).getStatus())
          .isEqualTo(409);
    }
  }

  @Test
  void statusNuloNaoPermiteCancelamento() {
    assertThat(ServicoEstoque.statusPermiteCancelamento(null)).isFalse();
    assertThat(ServicoEstoque.statusPermiteCancelamento(StatusImportacaoEstoque.EM_VALIDACAO))
        .isTrue();
  }

  // ── modelo de importacao ───────────────────────────────────────────────────

  @Test
  void modeloXlsxTrazCabecalhoEExemploDoTipoPedido() throws Exception {
    ServicoEstoque.ModeloImportacaoArquivo modelo = service.gerarModeloImportacao("ITENS", "xlsx");

    assertThat(modelo.nomeArquivo).isEqualTo("modelo-importacao-itens.xlsx");
    assertThat(modelo.contentType)
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(modelo.conteudo))) {
      Sheet sheet = workbook.getSheet("modelo");
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("nome");
      assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("ativo");
      assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("SHAMP-001");
    }
  }

  @Test
  void modeloCsvTemDuasLinhasEContentTypeTextCsv() {
    ServicoEstoque.ModeloImportacaoArquivo modelo = service.gerarModeloImportacao("AJUSTES", "CSV");

    assertThat(modelo.contentType).isEqualTo("text/csv");
    assertThat(modelo.nomeArquivo).isEqualTo("modelo-importacao-ajustes.csv");
    assertThat(new String(modelo.conteudo, StandardCharsets.UTF_8))
        .isEqualTo("sku,quantidade,motivo,origem,dataMovimento\n"
            + "SHAMP-001,25,Ajuste inventario,INVENTARIO,2026-02-28\n");
  }

  /** ENTRADAS tem 9 colunas — 4 delas o processador nunca le. */
  @Test
  void modeloDeEntradasTemAsNoveColunasDoOriginal() {
    ServicoEstoque.ModeloImportacaoArquivo modelo =
        service.gerarModeloImportacao("ENTRADAS", "csv");

    assertThat(new String(modelo.conteudo, StandardCharsets.UTF_8).split("\n")[0])
        .isEqualTo(
            "sku,quantidade,unidadeMedida,valorUnitarioPago,motivo,gerarLancamentoFinanceiro,"
                + "categoriaFinanceira,formaPagamento,dataMovimento");
  }

  @Test
  void formatoAusenteOuEmBrancoCaiEmXlsx() {
    assertThat(service.gerarModeloImportacao("ITENS", null).nomeArquivo).endsWith(".xlsx");
    assertThat(service.gerarModeloImportacao("ITENS", "  ").nomeArquivo).endsWith(".xlsx");
  }

  @Test
  void tipoInvalidoOuAusenteNoModeloEQuatrocentos() {
    for (String invalido : new String[] {null, "", "  ", "itens", "PRODUTOS"}) {
      assertThatThrownBy(() -> service.gerarModeloImportacao(invalido, "xlsx"))
          .isInstanceOf(ApiClientErrorException.class)
          .hasMessage("tipoImportacao invalido. Use ITENS, ENTRADAS ou AJUSTES.")
          .extracting(e -> ((ApiClientErrorException) e).getStatus())
          .isEqualTo(400);
    }
  }

  @Test
  void formatoInvalidoNoModeloEQuatrocentos() {
    assertThatThrownBy(() -> service.gerarModeloImportacao("ITENS", "pdf"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("formato invalido. Use xlsx ou csv.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  /** Gerar modelo nao consulta tenant nem banco — e um endpoint puramente estatico. */
  @Test
  void gerarModeloNaoTocaEmRepositorio() {
    service.gerarModeloImportacao("ITENS", "xlsx");
    verify(importacaoEstoqueJobRepository, never()).findByTenantIdOrderByCreatedAtDesc(any());
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private ImportacaoEstoqueJob job(StatusImportacaoEstoque status) {
    ImportacaoEstoqueJob job = new ImportacaoEstoqueJob();
    job.setId(JOB_ID);
    job.setTenantId(TENANT_ID);
    job.setTipoImportacao(TipoImportacaoEstoque.ENTRADAS);
    job.setStatus(status);
    job.setDryRun(Boolean.FALSE);
    job.setTotalLinhas(0);
    job.setLinhasProcessadas(0);
    job.setLinhasComErro(0);
    job.setCreatedAt(Instant.parse("2026-03-01T09:00:00Z"));
    job.setUpdatedAt(Instant.parse("2026-03-01T09:00:00Z"));
    return job;
  }

  private Object persistir(Object entidade) {
    if (entidade instanceof ImportacaoEstoqueJob job) {
      if (job.getId() == null) job.setId(UUID.randomUUID());
      if (job.getCreatedAt() == null) job.setCreatedAt(Instant.now());
      if (job.getUpdatedAt() == null) job.setUpdatedAt(Instant.now());
    }
    return entidade;
  }
}
