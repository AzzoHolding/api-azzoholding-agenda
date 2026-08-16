package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueErroLinha;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.OrigemMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoImportacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoEstoqueErroLinhaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoEstoqueJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentacaoEstoqueRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Cobre o motor de importacao em massa: leitura da planilha, os tres tipos de processamento, o
 * isolamento transacional por job e a limpeza dos jobs vencidos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessadorImportacaoEstoqueServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID STORAGE_KEY_TENANT = TENANT_ID;
  private static final String STORAGE_KEY = "tenant/" + STORAGE_KEY_TENANT + "/estoque/a.xlsx";

  @Mock private ImportacaoEstoqueJobRepository importacaoEstoqueJobRepository;
  @Mock private ImportacaoEstoqueErroLinhaRepository importacaoEstoqueErroLinhaRepository;
  @Mock private ItemEstoqueRepository itemEstoqueRepository;
  @Mock private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
  @Mock private MinioStorageService minioStorageService;
  @Mock private PlatformTransactionManager transactionManager;

  private EstadoImportacaoEstoque estado;
  private SimpleMeterRegistry meterRegistry;
  private ProcessadorImportacaoEstoqueService service;

  @BeforeEach
  void setUp() {
    estado = new EstadoImportacaoEstoque();
    meterRegistry = new SimpleMeterRegistry();
    service = novoServico(100, 60, 1440);
    when(itemEstoqueRepository.saveAndFlush(any())).thenAnswer(i -> persistirItem(i.getArgument(0)));
    when(itemEstoqueRepository.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  private ProcessadorImportacaoEstoqueService novoServico(
      int limitePorRodada, long ttlSucesso, long ttlFalha) {
    return new ProcessadorImportacaoEstoqueService(
        importacaoEstoqueJobRepository,
        importacaoEstoqueErroLinhaRepository,
        itemEstoqueRepository,
        movimentacaoEstoqueRepository,
        minioStorageService,
        estado,
        meterRegistry,
        transactionManager,
        limitePorRodada,
        ttlSucesso,
        ttlFalha,
        2,
        16,
        false);
  }

  // ── fila ───────────────────────────────────────────────────────────────────

  @Test
  void filaVaziaRegistraExecucaoSemProcessarNada() {
    when(importacaoEstoqueJobRepository.listarIdsPendentes(anyList(), any()))
        .thenReturn(List.of());

    service.processarFilaImportacaoEstoque();

    assertThat(estado.getUltimaExecucaoProcessamento()).isNotNull();
    assertThat(estado.getUltimoTotalProcessados()).isZero();
    assertThat(estado.getUltimoTotalFilaPendente()).isZero();
    assertThat(estado.getUltimoErroProcessamento()).isNull();
    verify(importacaoEstoqueJobRepository, never()).findById(any());
    verifyNoInteractions(transactionManager);
  }

  /** A capacidade limita a pagina pedida ao repositorio, nunca ultrapassa o limite por rodada. */
  @Test
  void capacidadeDaRodadaRespeitaOLimiteConfigurado() {
    service = novoServico(3, 60, 1440);
    when(importacaoEstoqueJobRepository.listarIdsPendentes(anyList(), any())).thenReturn(List.of());

    service.processarFilaImportacaoEstoque();

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(importacaoEstoqueJobRepository).listarIdsPendentes(anyList(), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(3);
  }

  @Test
  void filaConsultaOsTresStatusNaoTerminais() {
    when(importacaoEstoqueJobRepository.listarIdsPendentes(anyList(), any())).thenReturn(List.of());

    service.processarFilaImportacaoEstoque();

    ArgumentCaptor<List<StatusImportacaoEstoque>> captor = ArgumentCaptor.captor();
    verify(importacaoEstoqueJobRepository).listarIdsPendentes(captor.capture(), any());
    assertThat(captor.getValue())
        .containsExactly(
            StatusImportacaoEstoque.RECEBIDO,
            StatusImportacaoEstoque.EM_VALIDACAO,
            StatusImportacaoEstoque.PROCESSANDO);
  }

  /**
   * <b>A armadilha central desta fronteira.</b> Cada job precisa abrir transacao propria — se a
   * anotacao {@code REQUIRES_NEW} tivesse sido portada como auto-invocacao, o proxy do Spring seria
   * ignorado e nenhuma transacao nova seria pedida ao gerenciador.
   */
  @Test
  void cadaJobAbreTransacaoNovaPropria() {
    UUID outro = UUID.randomUUID();
    when(importacaoEstoqueJobRepository.listarIdsPendentes(anyList(), any()))
        .thenReturn(List.of(JOB_ID, outro));
    when(importacaoEstoqueJobRepository.findById(any())).thenReturn(Optional.empty());

    service.processarFilaImportacaoEstoque();

    ArgumentCaptor<TransactionDefinition> captor =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager, times(2)).getTransaction(captor.capture());
    assertThat(captor.getAllValues())
        .allMatch(d -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Uma falha num job nao aborta a rodada: o proximo continua sendo processado. */
  @Test
  void falhaDeUmJobNaoDerrubaOsDemais() {
    UUID outro = UUID.randomUUID();
    ImportacaoEstoqueJob job = job(TipoImportacaoEstoque.ITENS);
    when(importacaoEstoqueJobRepository.listarIdsPendentes(anyList(), any()))
        .thenReturn(List.of(JOB_ID, outro));
    when(importacaoEstoqueJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    when(importacaoEstoqueJobRepository.findById(outro)).thenReturn(Optional.empty());
    when(minioStorageService.baixarArquivo(STORAGE_KEY, TENANT_ID))
        .thenThrow(new IllegalStateException("Storage MinIO indisponivel para download."));

    service.processarFilaImportacaoEstoque();

    assertThat(job.getStatus()).isEqualTo(StatusImportacaoEstoque.FALHOU);
    assertThat(estado.getUltimoTotalProcessados()).isEqualTo(2);
    assertThat(estado.getUltimoErroProcessamento()).isNull();
  }

  @Test
  void falhaNaConsultaDaFilaRegistraErroDetalhadoEPropaga() {
    when(importacaoEstoqueJobRepository.listarIdsPendentes(anyList(), any()))
        .thenThrow(new IllegalStateException("banco fora"));

    assertThatThrownBy(() -> service.processarFilaImportacaoEstoque())
        .isInstanceOf(IllegalStateException.class);

    assertThat(estado.getUltimoErroProcessamento())
        .isEqualTo("java.lang.IllegalStateException: banco fora");
    assertThat(
            meterRegistry
                .counter(
                    "estoque.importacao.scheduler.execucoes",
                    "fase",
                    "processamento",
                    "resultado",
                    "error")
                .count())
        .isEqualTo(1.0);
  }

  // ── ITENS ──────────────────────────────────────────────────────────────────

  @Test
  void itensCriaItemNovoQuandoOSkuNaoExiste() {
    ImportacaoEstoqueJob job = prepararJob(TipoImportacaoEstoque.ITENS,
        linhas(
            List.of("nome", "sku", "unidadeMedida", "estoqueMinimo", "ativo"),
            List.of("Shampoo", "shamp-001", "ml", "500", "true")));
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(TENANT_ID, "SHAMP-001"))
        .thenReturn(Optional.empty());

    service.processarJob(JOB_ID);

    ArgumentCaptor<ItemEstoque> captor = ArgumentCaptor.forClass(ItemEstoque.class);
    verify(itemEstoqueRepository).saveAndFlush(captor.capture());
    ItemEstoque item = captor.getValue();
    assertThat(item.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(item.getNome()).isEqualTo("Shampoo");
    assertThat(item.getSku()).isEqualTo("SHAMP-001");
    assertThat(item.getUnidadeMedida()).isEqualTo("ML");
    assertThat(item.getEstoqueMinimo()).isEqualByComparingTo("500");
    assertThat(item.getSaldoAtual()).isEqualByComparingTo("0");
    assertThat(item.getAtivo()).isTrue();
    assertThat(job.getStatus()).isEqualTo(StatusImportacaoEstoque.CONCLUIDO);
    assertThat(job.getTotalLinhas()).isEqualTo(1);
    assertThat(job.getLinhasProcessadas()).isEqualTo(1);
  }

  /** Upsert: SKU existente e atualizado, nao duplicado — e o saldo atual e preservado. */
  @Test
  void itensAtualizaItemExistentePreservandoSaldo() {
    prepararJob(TipoImportacaoEstoque.ITENS,
        linhas(
            List.of("nome", "sku", "unidadeMedida", "estoqueMinimo", "ativo"),
            List.of("Shampoo Novo", "SHAMP-001", "L", "10", "nao")));
    ItemEstoque existente = item("SHAMP-001", new BigDecimal("42"));
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(TENANT_ID, "SHAMP-001"))
        .thenReturn(Optional.of(existente));

    service.processarJob(JOB_ID);

    assertThat(existente.getNome()).isEqualTo("Shampoo Novo");
    assertThat(existente.getUnidadeMedida()).isEqualTo("L");
    assertThat(existente.getSaldoAtual()).isEqualByComparingTo("42");
    // "nao" nao esta na lista de verdadeiros ("true"/"sim"/"1") e nem e vazio: vira inativo.
    assertThat(existente.getAtivo()).isFalse();
  }

  /** Linha <b>sem</b> SKU sempre cria item novo — o original nunca casa por nome. */
  @Test
  void itensSemSkuSempreCriaItemNovo() {
    prepararJob(TipoImportacaoEstoque.ITENS,
        linhas(
            List.of("nome", "sku", "unidadeMedida", "estoqueMinimo", "ativo"),
            List.of("Toalha", "", "UN", "", "")));

    service.processarJob(JOB_ID);

    ArgumentCaptor<ItemEstoque> captor = ArgumentCaptor.forClass(ItemEstoque.class);
    verify(itemEstoqueRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getSku()).isNull();
    // estoqueMinimo em branco vira zero; ativo em branco vira true.
    assertThat(captor.getValue().getEstoqueMinimo()).isEqualByComparingTo("0");
    assertThat(captor.getValue().getAtivo()).isTrue();
    verify(itemEstoqueRepository, never()).findFirstByTenantIdAndSku(any(), any());
  }

  @Test
  void itensAcusaCamposObrigatoriosEValorInvalidoNaLinhaCerta() {
    ImportacaoEstoqueJob job = prepararJob(TipoImportacaoEstoque.ITENS,
        linhas(
            List.of("nome", "sku", "unidadeMedida", "estoqueMinimo", "ativo"),
            List.of("", "A", "ML", "1", "true"),
            List.of("Cera", "B", "", "1", "true"),
            List.of("Gel", "C", "ML", "-5", "true"),
            List.of("Oleo", "D", "ML", "abc", "true")));

    service.processarJob(JOB_ID);

    List<ImportacaoEstoqueErroLinha> erros = errosSalvos();
    assertThat(erros).hasSize(4);
    assertThat(erros.get(0).getLinha()).isEqualTo(2);
    assertThat(erros.get(0).getColuna()).isEqualTo("nome");
    assertThat(erros.get(0).getCodigoErro()).isEqualTo("CAMPO_OBRIGATORIO");
    assertThat(erros.get(1).getLinha()).isEqualTo(3);
    assertThat(erros.get(1).getColuna()).isEqualTo("unidadeMedida");
    assertThat(erros.get(2).getColuna()).isEqualTo("estoqueMinimo");
    assertThat(erros.get(2).getCodigoErro()).isEqualTo("VALOR_INVALIDO");
    assertThat(erros.get(2).getValorRecebido()).isEqualTo("-5");
    assertThat(erros.get(3).getValorRecebido()).isEqualTo("abc");

    assertThat(job.getStatus()).isEqualTo(StatusImportacaoEstoque.CONCLUIDO_COM_ERROS);
    assertThat(job.getTotalLinhas()).isEqualTo(4);
    assertThat(job.getLinhasComErro()).isEqualTo(4);
    assertThat(job.getLinhasProcessadas()).isZero();
    verify(itemEstoqueRepository, never()).saveAndFlush(any());
  }

  /** Dry run continua validando tudo; so nao escreve. */
  @Test
  void dryRunValidaMasNaoEscreve() {
    ImportacaoEstoqueJob job = prepararJob(TipoImportacaoEstoque.ITENS,
        linhas(
            List.of("nome", "sku", "unidadeMedida", "estoqueMinimo", "ativo"),
            List.of("Shampoo", "SHAMP-001", "ML", "500", "true"),
            List.of("", "X", "ML", "1", "true")));
    job.setDryRun(Boolean.TRUE);

    service.processarJob(JOB_ID);

    verify(itemEstoqueRepository, never()).saveAndFlush(any());
    verify(itemEstoqueRepository, never()).save(any());
    assertThat(errosSalvos()).hasSize(1);
    assertThat(job.getStatus()).isEqualTo(StatusImportacaoEstoque.CONCLUIDO_COM_ERROS);
    assertThat(job.getTotalLinhas()).isEqualTo(2);
  }

  // ── ENTRADAS ───────────────────────────────────────────────────────────────

  @Test
  void entradasSomaSaldoRecalculaCustoMedioECriaMovimentacao() {
    prepararJob(TipoImportacaoEstoque.ENTRADAS,
        linhas(
            List.of("sku", "quantidade", "unidadeMedida", "valorUnitarioPago", "motivo",
                "gerarLancamentoFinanceiro"),
            List.of("SHAMP-001", "100", "ML", "2", "Reposicao", "sim")));
    ItemEstoque existente = item("SHAMP-001", new BigDecimal("100"));
    existente.setCustoMedioUnitario(new BigDecimal("1"));
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(TENANT_ID, "SHAMP-001"))
        .thenReturn(Optional.of(existente));

    service.processarJob(JOB_ID);

    assertThat(existente.getSaldoAtual()).isEqualByComparingTo("200");
    // (100*1 + 100*2) / 200 = 1.5
    assertThat(existente.getCustoMedioUnitario()).isEqualByComparingTo("1.5000");

    ArgumentCaptor<MovimentacaoEstoque> captor =
        ArgumentCaptor.forClass(MovimentacaoEstoque.class);
    verify(movimentacaoEstoqueRepository).save(captor.capture());
    MovimentacaoEstoque mov = captor.getValue();
    assertThat(mov.getTipo()).isEqualTo(TipoMovimentacaoEstoque.ENTRADA);
    assertThat(mov.getOrigem()).isEqualTo(OrigemMovimentacaoEstoque.COMPRA);
    assertThat(mov.getItemEstoqueId()).isEqualTo(ITEM_ID);
    assertThat(mov.getQuantidade()).isEqualByComparingTo("100");
    assertThat(mov.getSaldoAnterior()).isEqualByComparingTo("100");
    assertThat(mov.getSaldoPosterior()).isEqualByComparingTo("200");
    assertThat(mov.getValorTotalMovimentacao()).isEqualByComparingTo("200");
    assertThat(mov.getGerarLancamentoFinanceiro()).isTrue();
  }

  /** Sem valor unitario o custo medio nao e tocado, e o valor total fica nulo. */
  @Test
  void entradasSemValorUnitarioNaoMexeNoCustoMedio() {
    prepararJob(TipoImportacaoEstoque.ENTRADAS,
        linhas(
            List.of("sku", "quantidade", "unidadeMedida", "valorUnitarioPago", "motivo"),
            List.of("SHAMP-001", "10", "ML", "", "Reposicao")));
    ItemEstoque existente = item("SHAMP-001", new BigDecimal("5"));
    existente.setCustoMedioUnitario(new BigDecimal("3"));
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(TENANT_ID, "SHAMP-001"))
        .thenReturn(Optional.of(existente));

    service.processarJob(JOB_ID);

    assertThat(existente.getCustoMedioUnitario()).isEqualByComparingTo("3");
    ArgumentCaptor<MovimentacaoEstoque> captor =
        ArgumentCaptor.forClass(MovimentacaoEstoque.class);
    verify(movimentacaoEstoqueRepository).save(captor.capture());
    assertThat(captor.getValue().getValorUnitarioPago()).isNull();
    assertThat(captor.getValue().getValorTotalMovimentacao()).isNull();
    assertThat(captor.getValue().getGerarLancamentoFinanceiro()).isFalse();
  }

  @Test
  void entradasComQuantidadeNaoPositivaOuSkuInexistenteAcusaErro() {
    ImportacaoEstoqueJob job = prepararJob(TipoImportacaoEstoque.ENTRADAS,
        linhas(
            List.of("sku", "quantidade", "unidadeMedida", "valorUnitarioPago", "motivo"),
            List.of("SHAMP-001", "0", "ML", "1", "Reposicao"),
            List.of("SHAMP-002", "5", "ML", "-1", "Reposicao"),
            List.of("SHAMP-999", "5", "ML", "1", "Reposicao"),
            List.of("SHAMP-001", "5", "ML", "1", "")));
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(any(), any())).thenReturn(Optional.empty());

    service.processarJob(JOB_ID);

    List<ImportacaoEstoqueErroLinha> erros = errosSalvos();
    assertThat(erros).hasSize(4);
    assertThat(erros.get(0).getColuna()).isEqualTo("quantidade");
    assertThat(erros.get(1).getColuna()).isEqualTo("valorUnitarioPago");
    assertThat(erros.get(2).getColuna()).isEqualTo("sku");
    assertThat(erros.get(2).getCodigoErro()).isEqualTo("ITEM_NAO_ENCONTRADO");
    assertThat(erros.get(3).getColuna()).isEqualTo("motivo");
    assertThat(job.getLinhasComErro()).isEqualTo(4);
  }

  /** Virgula decimal e aceita: a planilha brasileira nao precisa ser reformatada. */
  @Test
  void quantidadeComVirgulaDecimalEAceita() {
    prepararJob(TipoImportacaoEstoque.ENTRADAS,
        linhas(
            List.of("sku", "quantidade", "unidadeMedida", "valorUnitarioPago", "motivo"),
            List.of("SHAMP-001", "1,5", "ML", "0,45", "Reposicao")));
    ItemEstoque existente = item("SHAMP-001", BigDecimal.ZERO);
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(TENANT_ID, "SHAMP-001"))
        .thenReturn(Optional.of(existente));

    service.processarJob(JOB_ID);

    assertThat(existente.getSaldoAtual()).isEqualByComparingTo("1.5");
    assertThat(errosSalvos()).isEmpty();
  }

  // ── AJUSTES ────────────────────────────────────────────────────────────────

  /**
   * Assimetria do original: a quantidade entra no saldo <b>com sinal</b>, mas e gravada na
   * movimentacao em valor <b>absoluto</b> — o sinal so sobrevive no saldo.
   */
  @Test
  void ajusteNegativoReduzSaldoMasGravaQuantidadeAbsoluta() {
    prepararJob(TipoImportacaoEstoque.AJUSTES,
        linhas(
            List.of("sku", "quantidade", "motivo", "origem"),
            List.of("SHAMP-001", "-30", "Perda", "MANUAL")));
    ItemEstoque existente = item("SHAMP-001", new BigDecimal("50"));
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(TENANT_ID, "SHAMP-001"))
        .thenReturn(Optional.of(existente));

    service.processarJob(JOB_ID);

    assertThat(existente.getSaldoAtual()).isEqualByComparingTo("20");
    ArgumentCaptor<MovimentacaoEstoque> captor =
        ArgumentCaptor.forClass(MovimentacaoEstoque.class);
    verify(movimentacaoEstoqueRepository).save(captor.capture());
    assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo("30");
    assertThat(captor.getValue().getTipo()).isEqualTo(TipoMovimentacaoEstoque.AJUSTE);
    assertThat(captor.getValue().getOrigem()).isEqualTo(OrigemMovimentacaoEstoque.MANUAL);
    assertThat(captor.getValue().getGerarLancamentoFinanceiro()).isFalse();
  }

  /**
   * A importacao <b>nao</b> aplica a trava de saldo negativo do fluxo normal de movimentacao: o
   * saldo pode ficar negativo sem nenhum erro de linha.
   */
  @Test
  void ajusteNegativoAlemDoSaldoNaoEBloqueado() {
    ImportacaoEstoqueJob job = prepararJob(TipoImportacaoEstoque.AJUSTES,
        linhas(List.of("sku", "quantidade", "motivo"), List.of("SHAMP-001", "-80", "Perda")));
    ItemEstoque existente = item("SHAMP-001", new BigDecimal("10"));
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(TENANT_ID, "SHAMP-001"))
        .thenReturn(Optional.of(existente));

    service.processarJob(JOB_ID);

    assertThat(existente.getSaldoAtual()).isEqualByComparingTo("-70");
    assertThat(errosSalvos()).isEmpty();
    assertThat(job.getStatus()).isEqualTo(StatusImportacaoEstoque.CONCLUIDO);
  }

  @Test
  void ajusteSemOrigemUsaInventarioEOrigemInvalidaAcusaErro() {
    prepararJob(TipoImportacaoEstoque.AJUSTES,
        linhas(
            List.of("sku", "quantidade", "motivo", "origem"),
            List.of("SHAMP-001", "5", "Contagem", ""),
            List.of("SHAMP-001", "5", "Contagem", "DEVOLUCAO")));
    ItemEstoque existente = item("SHAMP-001", BigDecimal.ZERO);
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(TENANT_ID, "SHAMP-001"))
        .thenReturn(Optional.of(existente));

    service.processarJob(JOB_ID);

    ArgumentCaptor<MovimentacaoEstoque> captor =
        ArgumentCaptor.forClass(MovimentacaoEstoque.class);
    verify(movimentacaoEstoqueRepository).save(captor.capture());
    assertThat(captor.getValue().getOrigem()).isEqualTo(OrigemMovimentacaoEstoque.INVENTARIO);
    List<ImportacaoEstoqueErroLinha> erros = errosSalvos();
    assertThat(erros).hasSize(1);
    assertThat(erros.get(0).getColuna()).isEqualTo("origem");
    assertThat(erros.get(0).getValorRecebido()).isEqualTo("DEVOLUCAO");
  }

  /** Quantidade zero em AJUSTES e valida (diferente de ENTRADAS, que exige positivo). */
  @Test
  void ajusteComQuantidadeZeroEValido() {
    prepararJob(TipoImportacaoEstoque.AJUSTES,
        linhas(List.of("sku", "quantidade", "motivo"), List.of("SHAMP-001", "0", "Conferencia")));
    when(itemEstoqueRepository.findFirstByTenantIdAndSku(TENANT_ID, "SHAMP-001"))
        .thenReturn(Optional.of(item("SHAMP-001", new BigDecimal("7"))));

    service.processarJob(JOB_ID);

    assertThat(errosSalvos()).isEmpty();
    verify(movimentacaoEstoqueRepository).save(any());
  }

  // ── finalizacao do job ─────────────────────────────────────────────────────

  @Test
  void jobSemArquivoNoStorageFalhaComContadoresZerados() {
    ImportacaoEstoqueJob job = job(TipoImportacaoEstoque.ITENS);
    job.setArquivoStorageKey(null);
    when(importacaoEstoqueJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    service.processarJob(JOB_ID);

    assertThat(job.getStatus()).isEqualTo(StatusImportacaoEstoque.FALHOU);
    assertThat(job.getTotalLinhas()).isZero();
    assertThat(job.getLinhasProcessadas()).isZero();
    assertThat(job.getLinhasComErro()).isZero();
    assertThat(job.getFinishedAt()).isNotNull();
    verify(importacaoEstoqueErroLinhaRepository, never()).saveAll(any());
  }

  @Test
  void arquivoQueNaoEXlsxFazOJobInteiroFalhar() {
    ImportacaoEstoqueJob job = job(TipoImportacaoEstoque.ITENS);
    when(importacaoEstoqueJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    when(minioStorageService.baixarArquivo(STORAGE_KEY, TENANT_ID))
        .thenReturn("sku,quantidade\nA,1\n".getBytes());

    service.processarJob(JOB_ID);

    assertThat(job.getStatus()).isEqualTo(StatusImportacaoEstoque.FALHOU);
  }

  /**
   * O arquivo e removido e a chave zerada <b>mesmo quando o job falha</b> — e o que torna o job
   * irreprocessavel. Comportamento do original.
   */
  @Test
  void arquivoERemovidoEChaveZeradaMesmoEmFalha() {
    ImportacaoEstoqueJob job = job(TipoImportacaoEstoque.ITENS);
    when(importacaoEstoqueJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    when(minioStorageService.baixarArquivo(STORAGE_KEY, TENANT_ID))
        .thenThrow(new IllegalStateException("indisponivel"));

    service.processarJob(JOB_ID);

    verify(minioStorageService).removerArquivoImportacao(STORAGE_KEY, TENANT_ID);
    assertThat(job.getArquivoStorageKey()).isNull();
    assertThat(job.getStatus()).isEqualTo(StatusImportacaoEstoque.FALHOU);
  }

  /** Falha ao remover do storage e engolida: o job ainda e finalizado e salvo. */
  @Test
  void falhaAoRemoverDoStorageNaoImpedeFinalizacao() {
    ImportacaoEstoqueJob job = prepararJob(TipoImportacaoEstoque.ITENS,
        linhas(
            List.of("nome", "sku", "unidadeMedida", "estoqueMinimo", "ativo"),
            List.of("Shampoo", "SHAMP-001", "ML", "1", "true")));
    org.mockito.Mockito.doThrow(new RuntimeException("boom"))
        .when(minioStorageService)
        .removerArquivoImportacao(any(), any());

    service.processarJob(JOB_ID);

    assertThat(job.getStatus()).isEqualTo(StatusImportacaoEstoque.CONCLUIDO);
    assertThat(job.getArquivoStorageKey()).isNull();
    verify(importacaoEstoqueJobRepository, times(2)).save(job);
  }

  @Test
  void jobInexistenteNaoFazNada() {
    when(importacaoEstoqueJobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    service.processarJob(JOB_ID);

    verify(importacaoEstoqueJobRepository, never()).save(any());
    verifyNoInteractions(minioStorageService);
  }

  // ── limpeza ────────────────────────────────────────────────────────────────

  @Test
  void limpezaSemJobsExpiradosSoRegistraExecucao() {
    when(importacaoEstoqueJobRepository.listarIdsExpirados(
            anyList(), any(), anyList(), any(), any()))
        .thenReturn(List.of());

    service.limparJobsExpiradosImportacaoEstoque();

    assertThat(estado.getUltimaExecucaoLimpeza()).isNotNull();
    assertThat(estado.getUltimoTotalLimpos()).isZero();
    verifyNoInteractions(transactionManager);
  }

  @Test
  void limpezaRemoveArquivoErrosEJob() {
    ImportacaoEstoqueJob job = job(TipoImportacaoEstoque.ITENS);
    job.setStatus(StatusImportacaoEstoque.CONCLUIDO);
    when(importacaoEstoqueJobRepository.listarIdsExpirados(
            anyList(), any(), anyList(), any(), any()))
        .thenReturn(List.of(JOB_ID));
    when(importacaoEstoqueJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    service.limparJobsExpiradosImportacaoEstoque();

    verify(minioStorageService).removerArquivoImportacao(STORAGE_KEY, TENANT_ID);
    verify(importacaoEstoqueErroLinhaRepository).deleteByJobIdAndTenantId(JOB_ID, TENANT_ID);
    verify(importacaoEstoqueJobRepository).delete(job);
    assertThat(estado.getUltimoTotalLimpos()).isEqualTo(1);
  }

  /** Os dois TTLs sao distintos: sucesso expira antes da falha. */
  @Test
  void limpezaUsaTtlsDistintosParaSucessoEFalha() {
    service = novoServico(100, 60, 1440);
    when(importacaoEstoqueJobRepository.listarIdsExpirados(
            anyList(), any(), anyList(), any(), any()))
        .thenReturn(List.of());

    service.limparJobsExpiradosImportacaoEstoque();

    ArgumentCaptor<Instant> sucesso = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> falha = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<List<StatusImportacaoEstoque>> statusSucesso = ArgumentCaptor.captor();
    ArgumentCaptor<List<StatusImportacaoEstoque>> statusFalha = ArgumentCaptor.captor();
    verify(importacaoEstoqueJobRepository)
        .listarIdsExpirados(
            statusSucesso.capture(),
            sucesso.capture(),
            statusFalha.capture(),
            falha.capture(),
            any());
    assertThat(statusSucesso.getValue())
        .containsExactly(
            StatusImportacaoEstoque.CONCLUIDO, StatusImportacaoEstoque.CONCLUIDO_COM_ERROS);
    assertThat(statusFalha.getValue())
        .containsExactly(StatusImportacaoEstoque.FALHOU, StatusImportacaoEstoque.CANCELADO);
    assertThat(falha.getValue()).isBefore(sucesso.getValue());
  }

  @Test
  void limpezaComFalhaRegistraErroEPropaga() {
    when(importacaoEstoqueJobRepository.listarIdsExpirados(
            anyList(), any(), anyList(), any(), any()))
        .thenThrow(new IllegalStateException("banco fora"));

    assertThatThrownBy(() -> service.limparJobsExpiradosImportacaoEstoque())
        .isInstanceOf(IllegalStateException.class);

    assertThat(estado.getUltimoErroLimpeza())
        .isEqualTo("java.lang.IllegalStateException: banco fora");
  }

  /** {@code detalharErro} encadeia as causas, para o health check nao esconder a raiz. */
  @Test
  void erroDetalhadoEncadeiaAsCausas() {
    when(importacaoEstoqueJobRepository.listarIdsPendentes(anyList(), any()))
        .thenThrow(
            new IllegalStateException("nivel 1", new IllegalArgumentException("raiz")));

    assertThatThrownBy(() -> service.processarFilaImportacaoEstoque())
        .isInstanceOf(IllegalStateException.class);

    assertThat(estado.getUltimoErroProcessamento())
        .isEqualTo(
            "java.lang.IllegalStateException: nivel 1 | caused by: "
                + "java.lang.IllegalArgumentException: raiz");
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private ImportacaoEstoqueJob prepararJob(TipoImportacaoEstoque tipo, byte[] planilha) {
    ImportacaoEstoqueJob job = job(tipo);
    when(importacaoEstoqueJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    when(minioStorageService.baixarArquivo(STORAGE_KEY, TENANT_ID)).thenReturn(planilha);
    return job;
  }

  private ImportacaoEstoqueJob job(TipoImportacaoEstoque tipo) {
    ImportacaoEstoqueJob job = new ImportacaoEstoqueJob();
    job.setId(JOB_ID);
    job.setTenantId(TENANT_ID);
    job.setTipoImportacao(tipo);
    job.setStatus(StatusImportacaoEstoque.RECEBIDO);
    job.setDryRun(Boolean.FALSE);
    job.setTotalLinhas(0);
    job.setLinhasProcessadas(0);
    job.setLinhasComErro(0);
    job.setArquivoStorageKey(STORAGE_KEY);
    job.setCreatedAt(Instant.parse("2026-03-01T09:00:00Z"));
    job.setUpdatedAt(Instant.parse("2026-03-01T09:00:00Z"));
    return job;
  }

  private ItemEstoque item(String sku, BigDecimal saldo) {
    ItemEstoque item = new ItemEstoque();
    item.setId(ITEM_ID);
    item.setTenantId(TENANT_ID);
    item.setSku(sku);
    item.setNome("Item " + sku);
    item.setUnidadeMedida("ML");
    item.setSaldoAtual(saldo);
    item.setEstoqueMinimo(BigDecimal.ZERO);
    item.setAtivo(Boolean.TRUE);
    return item;
  }

  private Object persistirItem(Object entidade) {
    if (entidade instanceof ItemEstoque item && item.getId() == null) {
      item.setId(UUID.randomUUID());
    }
    return entidade;
  }

  @SuppressWarnings("unchecked")
  private List<ImportacaoEstoqueErroLinha> errosSalvos() {
    ArgumentCaptor<List<ImportacaoEstoqueErroLinha>> captor = ArgumentCaptor.captor();
    verify(importacaoEstoqueErroLinhaRepository).saveAll(captor.capture());
    return captor.getValue();
  }

  @SafeVarargs
  private static byte[] linhas(List<String>... linhas) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XSSFSheet sheet = workbook.createSheet("dados");
      for (int r = 0; r < linhas.length; r++) {
        Row row = sheet.createRow(r);
        List<String> valores = linhas[r];
        for (int c = 0; c < valores.size(); c++) {
          row.createCell(c).setCellValue(valores.get(c));
        }
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
