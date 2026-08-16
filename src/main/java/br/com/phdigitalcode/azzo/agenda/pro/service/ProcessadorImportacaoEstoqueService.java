package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueErroLinha;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.OrigemMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoEstoqueErroLinhaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoEstoqueJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentacaoEstoqueRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Espelha {@code modules/inventory/application/ProcessadorImportacaoEstoqueService.java}: consome a
 * fila de jobs de importacao de estoque e faz a limpeza dos jobs terminais vencidos.
 *
 * <p><b>Isolamento por job (armadilha central desta fronteira).</b> No original cada job roda em
 * {@code @Transactional(REQUIRES_NEW)} — uma falha num job nao pode derrubar a rodada inteira. Em
 * Spring, chamar um metodo {@code @Transactional} da propria classe <b>nao passa pelo proxy</b>: a
 * anotacao seria ignorada em silencio, a rodada viraria uma transacao so e o rollback de um job
 * levaria junto todos os anteriores. Por isso a transacao nova e aberta explicitamente com
 * {@link TransactionTemplate} em {@link #processarJob(UUID)} e {@link #limparJobExpirado(UUID)},
 * mesmo padrao ja usado em {@code AppointmentNoShowProcessingService}.
 *
 * <p><b>Assimetrias do original preservadas:</b>
 *
 * <ol>
 *   <li>O <b>dry run nao valida menos</b>: continua acusando os mesmos erros de linha e ate exige
 *       que o SKU exista (em {@code ENTRADAS}/{@code AJUSTES}). O que ele pula e so a escrita.
 *   <li><b>A fila pendente reportada e sempre 0</b>: a lista de candidatos ja vem limitada pela
 *       capacidade, e {@code pendentesAntes - jobIds.size()} e por construcao zero. O health check
 *       expoe esse valor como {@code lastPendingJobs}.
 *   <li>Um job cujo arquivo ja sumiu do storage vira <b>FALHOU com {@code totalLinhas = 0}</b>, sem
 *       nenhuma linha de erro registrada — o diagnostico fica so no log.
 *   <li>O arquivo e removido do storage e {@code arquivoStorageKey} zerado <b>mesmo quando o job
 *       falha</b>, o que torna o job irreprocessavel. E o comportamento do original.
 *   <li>{@code processarItens} faz <b>upsert por SKU</b>; linha <b>sem</b> SKU sempre cria item
 *       novo, mesmo que ja exista outro com o mesmo nome.
 *   <li>{@code AJUSTES} grava {@code quantidade.abs()} na movimentacao, mas soma o valor
 *       <b>com sinal</b> no saldo — o sinal do ajuste so sobrevive no saldo, nao na movimentacao.
 *   <li>{@code AJUSTES} nao valida saldo negativo nem le
 *       {@code EstoqueConfiguracao.bloquearSaidaSemSaldo}: a importacao <b>ignora</b> a trava que o
 *       fluxo normal de movimentacao aplica.
 *   <li>{@code gerarLancamentoFinanceiro} e apenas gravado na movimentacao — a importacao nao
 *       chama o motor financeiro, entao nenhuma transacao e criada a partir dela.
 *   <li>As colunas {@code unidadeMedida}, {@code categoriaFinanceira}, {@code formaPagamento} e
 *       {@code dataMovimento} existem no modelo de {@code ENTRADAS}/{@code AJUSTES} e sao
 *       <b>lidas de nenhum lugar</b> — o processador nunca as consulta.
 *   <li>Apesar do modelo ser oferecido tambem em CSV, {@code parseXlsx} so entende XLSX: um CSV
 *       enviado de volta faz o job inteiro falhar.
 * </ol>
 */
@Service
public class ProcessadorImportacaoEstoqueService {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessadorImportacaoEstoqueService.class);
  private static final AtomicBoolean VIRTUAL_THREAD_LOGGED = new AtomicBoolean(false);

  private static final List<StatusImportacaoEstoque> STATUS_PENDENTES =
      List.of(
          StatusImportacaoEstoque.RECEBIDO,
          StatusImportacaoEstoque.EM_VALIDACAO,
          StatusImportacaoEstoque.PROCESSANDO);
  private static final List<StatusImportacaoEstoque> STATUS_SUCESSO =
      List.of(StatusImportacaoEstoque.CONCLUIDO, StatusImportacaoEstoque.CONCLUIDO_COM_ERROS);
  private static final List<StatusImportacaoEstoque> STATUS_FALHA =
      List.of(StatusImportacaoEstoque.FALHOU, StatusImportacaoEstoque.CANCELADO);

  private final ImportacaoEstoqueJobRepository importacaoEstoqueJobRepository;
  private final ImportacaoEstoqueErroLinhaRepository importacaoEstoqueErroLinhaRepository;
  private final ItemEstoqueRepository itemEstoqueRepository;
  private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
  private final MinioStorageService minioStorageService;
  private final EstadoImportacaoEstoque estadoImportacaoEstoque;
  private final MeterRegistry meterRegistry;
  private final TransactionTemplate requiresNewTransaction;

  private final int limitePorRodada;
  private final long ttlSucessoMinutos;
  private final long ttlFalhaMinutos;
  private final int cpuMultiplier;
  private final int parallelismMax;
  private final boolean virtualThreadsEnabled;

  public ProcessadorImportacaoEstoqueService(
      ImportacaoEstoqueJobRepository importacaoEstoqueJobRepository,
      ImportacaoEstoqueErroLinhaRepository importacaoEstoqueErroLinhaRepository,
      ItemEstoqueRepository itemEstoqueRepository,
      MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
      MinioStorageService minioStorageService,
      EstadoImportacaoEstoque estadoImportacaoEstoque,
      MeterRegistry meterRegistry,
      PlatformTransactionManager transactionManager,
      @Value("${app.estoque.importacao.limite-por-rodada:100}") int limitePorRodada,
      @Value("${app.estoque.importacao.ttl-sucesso-minutos:60}") long ttlSucessoMinutos,
      @Value("${app.estoque.importacao.ttl-falha-minutos:1440}") long ttlFalhaMinutos,
      @Value("${app.estoque.importacao.cpu-multiplier:2}") int cpuMultiplier,
      @Value("${app.estoque.importacao.parallelism-max:16}") int parallelismMax,
      @Value("${app.estoque.importacao.virtual-threads-enabled:false}")
          boolean virtualThreadsEnabled) {
    this.importacaoEstoqueJobRepository = importacaoEstoqueJobRepository;
    this.importacaoEstoqueErroLinhaRepository = importacaoEstoqueErroLinhaRepository;
    this.itemEstoqueRepository = itemEstoqueRepository;
    this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
    this.minioStorageService = minioStorageService;
    this.estadoImportacaoEstoque = estadoImportacaoEstoque;
    this.meterRegistry = meterRegistry;
    this.requiresNewTransaction = new TransactionTemplate(transactionManager);
    this.requiresNewTransaction.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.limitePorRodada = limitePorRodada;
    this.ttlSucessoMinutos = ttlSucessoMinutos;
    this.ttlFalhaMinutos = ttlFalhaMinutos;
    this.cpuMultiplier = cpuMultiplier;
    this.parallelismMax = parallelismMax;
    this.virtualThreadsEnabled = virtualThreadsEnabled;
  }

  /** Varredura global (todos os tenants), como no original. */
  public void processarFilaImportacaoEstoque() {
    Timer.Sample sample = meterRegistry != null ? Timer.start(meterRegistry) : null;
    Instant execucao = Instant.now();
    try {
      int capacidadeProcessamento = calcularCapacidadeProcessamento();
      logVirtualThreadStatusIfNeeded();
      List<UUID> jobIds =
          importacaoEstoqueJobRepository.listarIdsPendentes(
              STATUS_PENDENTES, PageRequest.ofSize(capacidadeProcessamento));

      int pendentesAntes = jobIds.size();
      LOG.info(
          "ProcessadorImportacaoEstoque iniciado. jobs={} capacidade={} cores={}",
          jobIds.size(),
          capacidadeProcessamento,
          Runtime.getRuntime().availableProcessors());

      if (jobIds.isEmpty()) {
        estadoImportacaoEstoque.registrarProcessamento(execucao, 0, 0);
        registrarMetricas("processamento", "empty", sample, 0);
        LOG.info("ProcessadorImportacaoEstoque finalizado sem jobs pendentes.");
        return;
      }

      for (UUID jobId : jobIds) {
        processarJob(jobId);
      }

      estadoImportacaoEstoque.registrarProcessamento(
          execucao, jobIds.size(), Math.max(pendentesAntes - jobIds.size(), 0));
      registrarMetricas("processamento", "success", sample, jobIds.size());
      LOG.info("ProcessadorImportacaoEstoque finalizado. processados={}", jobIds.size());
    } catch (RuntimeException exception) {
      estadoImportacaoEstoque.registrarFalhaProcessamento(detalharErro(exception));
      registrarMetricas("processamento", "error", sample, 0);
      LOG.error("ProcessadorImportacaoEstoque falhou.", exception);
      throw exception;
    }
  }

  public void limparJobsExpiradosImportacaoEstoque() {
    Timer.Sample sample = meterRegistry != null ? Timer.start(meterRegistry) : null;
    Instant execucao = Instant.now();
    try {
      Instant now = Instant.now();
      Instant limiteSucesso = now.minusSeconds(ttlSucessoMinutos * 60);
      Instant limiteFalha = now.minusSeconds(ttlFalhaMinutos * 60);

      List<UUID> jobIds =
          importacaoEstoqueJobRepository.listarIdsExpirados(
              STATUS_SUCESSO,
              limiteSucesso,
              STATUS_FALHA,
              limiteFalha,
              PageRequest.ofSize(limitePorRodada));

      LOG.info(
          "LimpezaImportacaoEstoque iniciada. jobs={} ttlSucessoMin={} ttlFalhaMin={}",
          jobIds.size(),
          ttlSucessoMinutos,
          ttlFalhaMinutos);

      if (jobIds.isEmpty()) {
        estadoImportacaoEstoque.registrarLimpeza(execucao, 0);
        registrarMetricas("limpeza", "empty", sample, 0);
        LOG.info("LimpezaImportacaoEstoque finalizada sem jobs expirados.");
        return;
      }

      for (UUID jobId : jobIds) {
        limparJobExpirado(jobId);
      }

      estadoImportacaoEstoque.registrarLimpeza(execucao, jobIds.size());
      registrarMetricas("limpeza", "success", sample, jobIds.size());
      LOG.info("LimpezaImportacaoEstoque finalizada. removidos={}", jobIds.size());
    } catch (RuntimeException exception) {
      estadoImportacaoEstoque.registrarFalhaLimpeza(detalharErro(exception));
      registrarMetricas("limpeza", "error", sample, 0);
      LOG.error("LimpezaImportacaoEstoque falhou.", exception);
      throw exception;
    }
  }

  /** Um job por transacao propria — ver a nota de isolamento no javadoc da classe. */
  public void processarJob(UUID jobId) {
    requiresNewTransaction.executeWithoutResult(status -> processarJobNaTransacao(jobId));
  }

  /** Um job por transacao propria — ver a nota de isolamento no javadoc da classe. */
  public void limparJobExpirado(UUID jobId) {
    requiresNewTransaction.executeWithoutResult(status -> limparJobExpiradoNaTransacao(jobId));
  }

  private void processarJobNaTransacao(UUID jobId) {
    ImportacaoEstoqueJob job = importacaoEstoqueJobRepository.findById(jobId).orElse(null);
    if (job == null) return;

    job.setStatus(StatusImportacaoEstoque.PROCESSANDO);
    job.setUpdatedAt(Instant.now());
    importacaoEstoqueJobRepository.save(job);

    List<ImportacaoEstoqueErroLinha> erros = new ArrayList<>();
    int totalLinhas = 0;
    int linhasComErro = 0;

    try {
      if (job.getArquivoStorageKey() == null || job.getArquivoStorageKey().isBlank()) {
        throw new IllegalStateException("Arquivo de importacao nao encontrado no storage.");
      }
      byte[] bytes =
          minioStorageService.baixarArquivo(job.getArquivoStorageKey(), job.getTenantId());
      List<List<String>> linhas = parseXlsx(bytes);
      totalLinhas = Math.max(0, linhas.size() - 1);

      linhasComErro =
          switch (job.getTipoImportacao()) {
            case ITENS -> processarItens(job, linhas, erros);
            case ENTRADAS -> processarEntradas(job, linhas, erros);
            case AJUSTES -> processarAjustes(job, linhas, erros);
          };

      importacaoEstoqueErroLinhaRepository.saveAll(erros);

      job.setTotalLinhas(totalLinhas);
      job.setLinhasProcessadas(totalLinhas - linhasComErro);
      job.setLinhasComErro(linhasComErro);
      job.setStatus(
          linhasComErro > 0
              ? StatusImportacaoEstoque.CONCLUIDO_COM_ERROS
              : StatusImportacaoEstoque.CONCLUIDO);

      LOG.info(
          "importacao_estoque_concluida jobId={} tipo={} total={} processadas={} erros={} dryRun={}",
          jobId,
          job.getTipoImportacao(),
          totalLinhas,
          totalLinhas - linhasComErro,
          linhasComErro,
          job.getDryRun());

    } catch (Exception ex) {
      job.setStatus(StatusImportacaoEstoque.FALHOU);
      job.setTotalLinhas(totalLinhas);
      job.setLinhasProcessadas(0);
      job.setLinhasComErro(0);
      LOG.error(
          "importacao_estoque_falhou jobId={} tipo={}", jobId, job.getTipoImportacao(), ex);
    }

    job.setFinishedAt(Instant.now());
    job.setUpdatedAt(Instant.now());
    try {
      if (job.getArquivoStorageKey() != null && !job.getArquivoStorageKey().isBlank()) {
        minioStorageService.removerArquivoImportacao(
            job.getArquivoStorageKey(), job.getTenantId());
      }
    } catch (Exception ignored) {
      // remover do storage e best-effort
    }
    job.setArquivoStorageKey(null);
    importacaoEstoqueJobRepository.save(job);
  }

  private void limparJobExpiradoNaTransacao(UUID jobId) {
    ImportacaoEstoqueJob job = importacaoEstoqueJobRepository.findById(jobId).orElse(null);
    if (job == null) return;
    try {
      if (job.getArquivoStorageKey() != null && !job.getArquivoStorageKey().isBlank()) {
        minioStorageService.removerArquivoImportacao(
            job.getArquivoStorageKey(), job.getTenantId());
      }
    } catch (Exception ignored) {
      // remover do storage e best-effort
    }
    importacaoEstoqueErroLinhaRepository.deleteByJobIdAndTenantId(job.getId(), job.getTenantId());
    importacaoEstoqueJobRepository.delete(job);
  }

  // ── PROCESSADORES POR TIPO ──────────────────────────────────────────────────

  /** Colunas ITENS: nome(0), sku(1), unidadeMedida(2), estoqueMinimo(3), ativo(4). */
  private int processarItens(
      ImportacaoEstoqueJob job, List<List<String>> linhas, List<ImportacaoEstoqueErroLinha> erros) {

    boolean dryRun = Boolean.TRUE.equals(job.getDryRun());
    int linhasComErro = 0;

    for (int i = 1; i < linhas.size(); i++) {
      int numeroLinha = i + 1;
      List<String> cols = linhas.get(i);

      String nome = col(cols, 0);
      String sku = col(cols, 1);
      String unidadeMedida = col(cols, 2);
      String estoqueMinimoStr = col(cols, 3);
      String ativoStr = col(cols, 4);

      if (nome == null) {
        erros.add(
            erroLinha(job, numeroLinha, "nome", "CAMPO_OBRIGATORIO", "Nome e obrigatorio.", null));
        linhasComErro++;
        continue;
      }
      if (unidadeMedida == null) {
        erros.add(
            erroLinha(
                job,
                numeroLinha,
                "unidadeMedida",
                "CAMPO_OBRIGATORIO",
                "Unidade de medida e obrigatoria.",
                null));
        linhasComErro++;
        continue;
      }

      BigDecimal estoqueMinimo;
      try {
        estoqueMinimo =
            estoqueMinimoStr == null
                ? BigDecimal.ZERO
                : new BigDecimal(estoqueMinimoStr.replace(",", "."));
        if (estoqueMinimo.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
      } catch (NumberFormatException ex) {
        erros.add(
            erroLinha(
                job,
                numeroLinha,
                "estoqueMinimo",
                "VALOR_INVALIDO",
                "Estoque minimo deve ser um numero nao negativo.",
                estoqueMinimoStr));
        linhasComErro++;
        continue;
      }

      boolean ativo =
          ativoStr == null
              || ativoStr.isBlank()
              || "true".equalsIgnoreCase(ativoStr)
              || "sim".equalsIgnoreCase(ativoStr)
              || "1".equals(ativoStr);

      if (!dryRun) {
        ItemEstoque item = null;
        if (sku != null) {
          item =
              itemEstoqueRepository
                  .findFirstByTenantIdAndSku(job.getTenantId(), sku.toUpperCase(Locale.ROOT))
                  .orElse(null);
        }
        if (item == null) {
          item = new ItemEstoque();
          item.setTenantId(job.getTenantId());
          item.setSaldoAtual(BigDecimal.ZERO);
        }
        item.setNome(nome);
        item.setSku(sku != null ? sku.toUpperCase(Locale.ROOT) : null);
        item.setUnidadeMedida(unidadeMedida.toUpperCase(Locale.ROOT));
        item.setEstoqueMinimo(estoqueMinimo);
        item.setAtivo(ativo);
        // saveAndFlush (armadilha 2): duas linhas do mesmo arquivo podem trazer o mesmo SKU, e a
        // segunda precisa enxergar o item que a primeira acabou de inserir.
        itemEstoqueRepository.saveAndFlush(item);
      }
    }

    return linhasComErro;
  }

  /**
   * Colunas ENTRADAS: sku(0), quantidade(1), unidadeMedida(2), valorUnitarioPago(3), motivo(4),
   * gerarLancamentoFinanceiro(5), categoriaFinanceira(6), formaPagamento(7), dataMovimento(8).
   */
  private int processarEntradas(
      ImportacaoEstoqueJob job, List<List<String>> linhas, List<ImportacaoEstoqueErroLinha> erros) {

    boolean dryRun = Boolean.TRUE.equals(job.getDryRun());
    int linhasComErro = 0;

    for (int i = 1; i < linhas.size(); i++) {
      int numeroLinha = i + 1;
      List<String> cols = linhas.get(i);

      String sku = col(cols, 0);
      String quantidadeStr = col(cols, 1);
      String valorUnitarioPagoStr = col(cols, 3);
      String motivo = col(cols, 4);
      String gerarLancamentoStr = col(cols, 5);

      if (sku == null) {
        erros.add(
            erroLinha(job, numeroLinha, "sku", "CAMPO_OBRIGATORIO", "SKU e obrigatorio.", null));
        linhasComErro++;
        continue;
      }
      if (quantidadeStr == null) {
        erros.add(
            erroLinha(
                job,
                numeroLinha,
                "quantidade",
                "CAMPO_OBRIGATORIO",
                "Quantidade e obrigatoria.",
                null));
        linhasComErro++;
        continue;
      }
      if (motivo == null) {
        erros.add(
            erroLinha(
                job, numeroLinha, "motivo", "CAMPO_OBRIGATORIO", "Motivo e obrigatorio.", null));
        linhasComErro++;
        continue;
      }

      BigDecimal quantidade;
      try {
        quantidade = new BigDecimal(quantidadeStr.replace(",", "."));
        if (quantidade.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
      } catch (NumberFormatException ex) {
        erros.add(
            erroLinha(
                job,
                numeroLinha,
                "quantidade",
                "VALOR_INVALIDO",
                "Quantidade deve ser um numero positivo.",
                quantidadeStr));
        linhasComErro++;
        continue;
      }

      BigDecimal valorUnitarioPago = null;
      if (valorUnitarioPagoStr != null) {
        try {
          valorUnitarioPago = new BigDecimal(valorUnitarioPagoStr.replace(",", "."));
          if (valorUnitarioPago.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
          erros.add(
              erroLinha(
                  job,
                  numeroLinha,
                  "valorUnitarioPago",
                  "VALOR_INVALIDO",
                  "Valor unitario pago deve ser um numero nao negativo.",
                  valorUnitarioPagoStr));
          linhasComErro++;
          continue;
        }
      }

      ItemEstoque item =
          itemEstoqueRepository
              .findFirstByTenantIdAndSku(job.getTenantId(), sku.toUpperCase(Locale.ROOT))
              .orElse(null);
      if (item == null) {
        erros.add(
            erroLinha(
                job,
                numeroLinha,
                "sku",
                "ITEM_NAO_ENCONTRADO",
                "Item com este SKU nao encontrado no estoque.",
                sku));
        linhasComErro++;
        continue;
      }

      if (!dryRun) {
        BigDecimal saldoAnterior = item.getSaldoAtual();
        BigDecimal saldoPosterior = saldoAnterior.add(quantidade);

        if (valorUnitarioPago != null && valorUnitarioPago.compareTo(BigDecimal.ZERO) > 0) {
          BigDecimal custoAtual =
              item.getCustoMedioUnitario() != null ? item.getCustoMedioUnitario() : BigDecimal.ZERO;
          BigDecimal totalAnterior = saldoAnterior.multiply(custoAtual);
          BigDecimal totalNovo = quantidade.multiply(valorUnitarioPago);
          item.setCustoMedioUnitario(
              saldoPosterior.compareTo(BigDecimal.ZERO) > 0
                  ? totalAnterior.add(totalNovo).divide(saldoPosterior, 4, RoundingMode.HALF_UP)
                  : valorUnitarioPago);
        }

        item.setSaldoAtual(saldoPosterior);
        itemEstoqueRepository.save(item);

        MovimentacaoEstoque mov = new MovimentacaoEstoque();
        mov.setTenantId(job.getTenantId());
        mov.setItemEstoqueId(item.getId());
        mov.setTipo(TipoMovimentacaoEstoque.ENTRADA);
        mov.setOrigem(OrigemMovimentacaoEstoque.COMPRA);
        mov.setQuantidade(quantidade);
        mov.setSaldoAnterior(saldoAnterior);
        mov.setSaldoPosterior(saldoPosterior);
        mov.setMotivo(motivo);
        mov.setValorUnitarioPago(valorUnitarioPago);
        mov.setValorTotalMovimentacao(
            valorUnitarioPago != null ? valorUnitarioPago.multiply(quantidade) : null);
        mov.setGerarLancamentoFinanceiro(
            "true".equalsIgnoreCase(gerarLancamentoStr) || "sim".equalsIgnoreCase(gerarLancamentoStr));
        movimentacaoEstoqueRepository.save(mov);
      }
    }

    return linhasComErro;
  }

  /** Colunas AJUSTES: sku(0), quantidade(1), motivo(2), origem(3), dataMovimento(4). */
  private int processarAjustes(
      ImportacaoEstoqueJob job, List<List<String>> linhas, List<ImportacaoEstoqueErroLinha> erros) {

    boolean dryRun = Boolean.TRUE.equals(job.getDryRun());
    int linhasComErro = 0;

    for (int i = 1; i < linhas.size(); i++) {
      int numeroLinha = i + 1;
      List<String> cols = linhas.get(i);

      String sku = col(cols, 0);
      String quantidadeStr = col(cols, 1);
      String motivo = col(cols, 2);
      String origemStr = col(cols, 3);

      if (sku == null) {
        erros.add(
            erroLinha(job, numeroLinha, "sku", "CAMPO_OBRIGATORIO", "SKU e obrigatorio.", null));
        linhasComErro++;
        continue;
      }
      if (quantidadeStr == null) {
        erros.add(
            erroLinha(
                job,
                numeroLinha,
                "quantidade",
                "CAMPO_OBRIGATORIO",
                "Quantidade e obrigatoria.",
                null));
        linhasComErro++;
        continue;
      }
      if (motivo == null) {
        erros.add(
            erroLinha(
                job, numeroLinha, "motivo", "CAMPO_OBRIGATORIO", "Motivo e obrigatorio.", null));
        linhasComErro++;
        continue;
      }

      BigDecimal quantidade;
      try {
        quantidade = new BigDecimal(quantidadeStr.replace(",", "."));
      } catch (NumberFormatException ex) {
        erros.add(
            erroLinha(
                job,
                numeroLinha,
                "quantidade",
                "VALOR_INVALIDO",
                "Quantidade deve ser um numero (positivo para adicionar, negativo para remover).",
                quantidadeStr));
        linhasComErro++;
        continue;
      }

      OrigemMovimentacaoEstoque origem;
      try {
        origem =
            origemStr == null || origemStr.isBlank()
                ? OrigemMovimentacaoEstoque.INVENTARIO
                : OrigemMovimentacaoEstoque.valueOf(origemStr.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        erros.add(
            erroLinha(
                job,
                numeroLinha,
                "origem",
                "VALOR_INVALIDO",
                "Origem invalida. Use: MANUAL, COMPRA, SERVICO ou INVENTARIO.",
                origemStr));
        linhasComErro++;
        continue;
      }

      ItemEstoque item =
          itemEstoqueRepository
              .findFirstByTenantIdAndSku(job.getTenantId(), sku.toUpperCase(Locale.ROOT))
              .orElse(null);
      if (item == null) {
        erros.add(
            erroLinha(
                job,
                numeroLinha,
                "sku",
                "ITEM_NAO_ENCONTRADO",
                "Item com este SKU nao encontrado no estoque.",
                sku));
        linhasComErro++;
        continue;
      }

      if (!dryRun) {
        BigDecimal saldoAnterior = item.getSaldoAtual();
        BigDecimal saldoPosterior = saldoAnterior.add(quantidade);
        item.setSaldoAtual(saldoPosterior);
        itemEstoqueRepository.save(item);

        MovimentacaoEstoque mov = new MovimentacaoEstoque();
        mov.setTenantId(job.getTenantId());
        mov.setItemEstoqueId(item.getId());
        mov.setTipo(TipoMovimentacaoEstoque.AJUSTE);
        mov.setOrigem(origem);
        mov.setQuantidade(quantidade.abs());
        mov.setSaldoAnterior(saldoAnterior);
        mov.setSaldoPosterior(saldoPosterior);
        mov.setMotivo(motivo);
        mov.setGerarLancamentoFinanceiro(false);
        movimentacaoEstoqueRepository.save(mov);
      }
    }

    return linhasComErro;
  }

  // ── UTILITARIOS ─────────────────────────────────────────────────────────────

  private List<List<String>> parseXlsx(byte[] bytes) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = workbook.getSheetAt(0);
      List<List<String>> result = new ArrayList<>();
      for (Row row : sheet) {
        List<String> cols = new ArrayList<>();
        int lastCol = row.getLastCellNum();
        for (int c = 0; c < lastCol; c++) {
          cols.add(cellToString(row.getCell(c)));
        }
        result.add(cols);
      }
      return result;
    }
  }

  private String cellToString(Cell cell) {
    if (cell == null) return null;
    String value =
        switch (cell.getCellType()) {
          case STRING -> cell.getStringCellValue();
          case NUMERIC -> {
            double d = cell.getNumericCellValue();
            yield (d == Math.floor(d) && !Double.isInfinite(d))
                ? String.valueOf((long) d)
                : String.valueOf(d);
          }
          case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
          case FORMULA -> {
            try {
              yield String.valueOf((long) cell.getNumericCellValue());
            } catch (Exception e) {
              yield cell.getStringCellValue();
            }
          }
          default -> null;
        };
    return (value == null || value.isBlank()) ? null : value.trim();
  }

  private String col(List<String> cols, int index) {
    if (cols == null || index >= cols.size()) return null;
    String v = cols.get(index);
    return (v == null || v.isBlank()) ? null : v.trim();
  }

  private ImportacaoEstoqueErroLinha erroLinha(
      ImportacaoEstoqueJob job,
      int linha,
      String coluna,
      String codigoErro,
      String mensagem,
      String valorRecebido) {
    ImportacaoEstoqueErroLinha erro = new ImportacaoEstoqueErroLinha();
    erro.setJobId(job.getId());
    erro.setTenantId(job.getTenantId());
    erro.setLinha(linha);
    erro.setColuna(coluna);
    erro.setCodigoErro(codigoErro);
    erro.setMensagem(mensagem);
    erro.setValorRecebido(
        valorRecebido != null && valorRecebido.length() > 255
            ? valorRecebido.substring(0, 255)
            : valorRecebido);
    return erro;
  }

  private int calcularCapacidadeProcessamento() {
    int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
    int multiplicador = Math.max(1, cpuMultiplier);
    int limiteParalelismo = Math.max(1, parallelismMax);
    int capacidadeCpu = Math.max(1, Math.min(limiteParalelismo, cores * multiplicador));
    return Math.max(1, Math.min(limitePorRodada, capacidadeCpu));
  }

  private void logVirtualThreadStatusIfNeeded() {
    if (!VIRTUAL_THREAD_LOGGED.compareAndSet(false, true)) return;
    if (!virtualThreadsEnabled) {
      LOG.info("Virtual threads desabilitadas para importacao de estoque (config).");
      return;
    }
    int javaVersion = Runtime.version().feature();
    if (javaVersion < 21) {
      LOG.warn(
          "Virtual threads solicitadas, mas ambiente em Java {}. Recurso nao sera ativado.",
          javaVersion);
      return;
    }
    LOG.info("Virtual threads habilitadas para importacao de estoque.");
  }

  private void registrarMetricas(String fase, String resultado, Timer.Sample sample, int jobs) {
    if (meterRegistry == null) return;
    meterRegistry
        .counter("estoque.importacao.scheduler.execucoes", "fase", fase, "resultado", resultado)
        .increment();
    meterRegistry
        .counter("estoque.importacao.scheduler.jobs", "fase", fase)
        .increment(Math.max(jobs, 0));
    if (sample != null) {
      sample.stop(
          Timer.builder("estoque.importacao.scheduler.latencia")
              .description(
                  "Latencia das rotinas de processamento/limpeza de importacao de estoque.")
              .tag("fase", fase)
              .tag("resultado", resultado)
              .register(meterRegistry));
    }
  }

  private String detalharErro(Exception exception) {
    StringBuilder builder = new StringBuilder();
    Throwable current = exception;
    while (current != null) {
      if (builder.length() > 0) builder.append(" | caused by: ");
      builder.append(current.getClass().getName());
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        builder.append(": ").append(current.getMessage());
      }
      current = current.getCause();
    }
    return builder.toString();
  }
}
