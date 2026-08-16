package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RecurringTransactionRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.TransacaoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.CategoriaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.FluxoCaixaDiarioResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.PagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RecurringTransactionResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ResumoFinanceiroResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.TransacaoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProductCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RecurringTransaction;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RecurringTransactionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository.TransacaoFilter;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransactionCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Espelha {@code modules/finance/application/ServicoFinanceiro.java}.
 *
 * <p>O {@code jakarta.ws.rs.NotFoundException} do original (que o JAX-RS traduz em HTTP 404) vira
 * {@link ApiClientErrorException} com status 404 — o {@code GlobalExceptionHandler} ja trata essa
 * excecao preservando o status, entao o contrato HTTP fica identico.
 *
 * <p>O {@code StreamingOutput} do JAX-RS vira {@link StreamingResponseBody} do Spring MVC. O
 * conteudo gerado (BOM UTF-8, separador {@code ;}, formato de numero BR) e byte a byte o mesmo.
 */
@Service
public class ServicoFinanceiro {

  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  @PersistenceContext private EntityManager entityManager;

  private final TransacaoRepository transacaoRepository;
  private final TransacaoQueryRepository transacaoQueryRepository;
  private final TransactionCategoryRepository transactionCategoryRepository;
  private final ProductCategoryRepository productCategoryRepository;
  private final RecurringTransactionRepository recurringTransactionRepository;
  private final ProfissionalRepository profissionalRepository;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final AuditService auditService;
  private final CommissionService commissionService;

  public ServicoFinanceiro(
      TransacaoRepository transacaoRepository,
      TransacaoQueryRepository transacaoQueryRepository,
      TransactionCategoryRepository transactionCategoryRepository,
      ProductCategoryRepository productCategoryRepository,
      RecurringTransactionRepository recurringTransactionRepository,
      ProfissionalRepository profissionalRepository,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      AuditService auditService,
      CommissionService commissionService) {
    this.transacaoRepository = transacaoRepository;
    this.transacaoQueryRepository = transacaoQueryRepository;
    this.transactionCategoryRepository = transactionCategoryRepository;
    this.productCategoryRepository = productCategoryRepository;
    this.recurringTransactionRepository = recurringTransactionRepository;
    this.profissionalRepository = profissionalRepository;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.auditService = auditService;
    this.commissionService = commissionService;
  }

  // ─── LISTAGEM COM PAGINACAO E FILTROS (F0.3) ─────────────────────────────

  @Transactional(readOnly = true)
  public PagedResponse<TransacaoResponse> listar(
      Instant from,
      Instant to,
      TipoTransacao type,
      UUID categoryId,
      MetodoPagamento paymentMethod,
      UUID professionalId,
      Boolean reconciled,
      int page,
      int limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TransacaoFilter filter =
        new TransacaoFilter(tenantId, from, to, type, categoryId, paymentMethod, professionalId, reconciled);

    long totalCount = transacaoQueryRepository.countFiltered(filter);
    List<TransacaoResponse> items =
        transacaoQueryRepository.listFiltered(filter, page, limit).stream().map(this::toResponse).toList();

    PagedResponse<TransacaoResponse> response = new PagedResponse<>();
    response.items = items;
    response.totalCount = totalCount;
    response.currentPage = page;
    response.totalPages = limit > 0 ? (int) Math.ceil((double) totalCount / limit) : 0;
    return response;
  }

  // ─── RESUMO COM FILTRO DE PERIODO (F0.1) ─────────────────────────────────

  @Transactional(readOnly = true)
  public ResumoFinanceiroResponse resumo(
      Instant from,
      Instant to,
      TipoTransacao type,
      UUID categoryId,
      MetodoPagamento paymentMethod,
      UUID professionalId,
      Boolean reconciled) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TransacaoQueryRepository.SummaryTotals summaryTotals =
        transacaoQueryRepository.summarizeFiltered(
            new TransacaoFilter(
                tenantId, from, to, type, categoryId, paymentMethod, professionalId, reconciled));

    ResumoFinanceiroResponse r = new ResumoFinanceiroResponse();
    r.totalIncome = summaryTotals.totalIncome();
    r.totalExpenses = summaryTotals.totalExpenses();
    r.balance = NumericUtil.subtract(r.totalIncome, r.totalExpenses);
    return r;
  }

  // ─── CATEGORIAS (F0.4 / F1.5) ────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<CategoriaResponse> listarCategorias() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Map<UUID, Long> transactionCountByCategoryId = mapTransactionCountByCategoryId(tenantId);
    return transactionCategoryRepository.findByTenantIdOrderByName(tenantId).stream()
        .map(
            c -> {
              CategoriaResponse r = new CategoriaResponse();
              r.id = c.getId().toString();
              r.name = c.getName();
              r.transactionCount = transactionCountByCategoryId.getOrDefault(c.getId(), 0L);
              return r;
            })
        .toList();
  }

  @Transactional
  public CategoriaResponse criarCategoria(String nome) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TransactionCategory cat = resolveTransactionCategory(tenantId, nome != null ? nome.trim() : null);
    CategoriaResponse r = new CategoriaResponse();
    r.id = cat.getId().toString();
    r.name = cat.getName();
    return r;
  }

  @Transactional
  public CategoriaResponse renomearCategoria(UUID id, String novoNome) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    TransactionCategory cat =
        transactionCategoryRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Categoria nao encontrada", 404));

    String normalized = normalizeNullable(novoNome);
    if (normalized == null) throw new IllegalArgumentException("Nome da categoria e obrigatorio");

    // Unicidade dentro do tenant (ignorando case)
    TransactionCategory existente =
        transactionCategoryRepository.findByTenantAndName(tenantId, normalized).orElse(null);
    if (existente != null && !existente.getId().equals(id)) {
      throw new IllegalArgumentException("Ja existe uma categoria com este nome");
    }

    cat.setName(normalized);
    CategoriaResponse r = new CategoriaResponse();
    r.id = cat.getId().toString();
    r.name = cat.getName();
    r.transactionCount = countTransactionsByCategory(tenantId, cat.getId());
    return r;
  }

  @Transactional
  public void excluirCategoria(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    TransactionCategory cat =
        transactionCategoryRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Categoria nao encontrada", 404));

    long vinculadas = countTransactionsByCategory(tenantId, id);
    if (vinculadas > 0) {
      throw new IllegalStateException(
          "Nao e possivel excluir: existem " + vinculadas + " lancamento(s) vinculado(s) a esta categoria");
    }

    transactionCategoryRepository.delete(cat);
  }

  // ─── CRIAR / ATUALIZAR / DELETAR ─────────────────────────────────────────

  @Transactional
  public TransacaoResponse criar(TransacaoRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    Transacao t = new Transacao();
    t.setTenantId(tenantId);
    t.setAppointmentId(parseUuidNullable(req.appointmentId));
    t.setProfessionalId(parseUuidNullable(req.professionalId));
    t.setStockItemId(parseUuidNullable(req.productId));
    ProductCategory resolvedProductCategoryEntity = resolveProductCategory(tenantId, req.productCategory);
    t.setProductCategoryId(
        resolvedProductCategoryEntity != null ? resolvedProductCategoryEntity.getId() : null);
    t.setProductCategoryRef(resolvedProductCategoryEntity);
    t.setType(resolveTipoTransacao(req.type));
    TransactionCategory resolvedCategoryEntity = resolveTransactionCategory(tenantId, req.category);
    t.setCategoryId(resolvedCategoryEntity.getId());
    t.setCategoryRef(resolvedCategoryEntity);
    t.setDescription(normalizeNullable(req.description));
    t.setAmount(resolveAmount(req));
    t.setPaymentMethod(resolveMetodoPagamento(req.paymentMethod));
    t.setDate(DataUtil.parseInstantISO(req.date));

    transacaoRepository.save(t);
    String resolvedProductCategory =
        t.getProductCategoryRef() != null
            ? t.getProductCategoryRef().getName()
            : normalizeNullable(req.productCategory);
    if (t.getType() == TipoTransacao.INCOME
        && t.getProfessionalId() != null
        && (t.getStockItemId() != null || resolvedProductCategory != null)) {
      commissionService.registerProductCommissionIfApplicable(
          tenantId,
          t.getId(),
          t.getProfessionalId(),
          t.getStockItemId(),
          resolvedProductCategory,
          NumericUtil.toCents(t.getAmount()),
          t.getDate(),
          t.getDescription());
    }
    registrarAuditoriaFinanceiro(
        tenantId,
        "FINANCE_TRANSACTION_CREATE",
        null,
        buildAuditPayload(req),
        t.getId() != null ? t.getId().toString() : null);
    return toResponse(t);
  }

  @Transactional
  public TransacaoResponse atualizar(UUID id, TransacaoRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    Transacao t =
        transacaoRepository
            .findAtivaByIdAndTenant(id, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Transacao nao encontrada", 404));

    // Snapshot para auditoria e deteccao de mudancas que afetam a comissao.
    Map<String, Object> before = buildAuditPayloadFromTransacao(t);

    UUID newProfessionalId = parseUuidNullable(req.professionalId);
    UUID newStockItemId = parseUuidNullable(req.productId);
    BigDecimal newAmount = resolveAmount(req);
    TipoTransacao newType = resolveTipoTransacao(req.type);

    boolean commissionAffected =
        t.getType() == TipoTransacao.INCOME
            && t.getProfessionalId() != null
            && (t.getStockItemId() != null || t.getProductCategoryId() != null)
            && (!Objects.equals(t.getAmount(), newAmount)
                || !Objects.equals(t.getProfessionalId(), newProfessionalId)
                || !Objects.equals(t.getStockItemId(), newStockItemId)
                || t.getType() != newType);

    if (commissionAffected) {
      commissionService.reverseProductCommissionIfApplicable(tenantId, id, "Transacao editada");
    }

    t.setType(newType);
    TransactionCategory resolvedCategory = resolveTransactionCategory(tenantId, req.category);
    t.setCategoryId(resolvedCategory.getId());
    t.setCategoryRef(resolvedCategory);
    t.setDescription(normalizeNullable(req.description));
    t.setAmount(newAmount);
    t.setPaymentMethod(resolveMetodoPagamento(req.paymentMethod));
    t.setDate(DataUtil.parseInstantISO(req.date));
    t.setProfessionalId(newProfessionalId);
    t.setStockItemId(newStockItemId);
    ProductCategory resolvedProductCategory = resolveProductCategory(tenantId, req.productCategory);
    t.setProductCategoryId(resolvedProductCategory != null ? resolvedProductCategory.getId() : null);
    t.setProductCategoryRef(resolvedProductCategory);

    String resolvedProductCategoryName =
        t.getProductCategoryRef() != null
            ? t.getProductCategoryRef().getName()
            : normalizeNullable(req.productCategory);
    if (t.getType() == TipoTransacao.INCOME
        && t.getProfessionalId() != null
        && (t.getStockItemId() != null || resolvedProductCategoryName != null)) {
      commissionService.registerProductCommissionIfApplicable(
          tenantId,
          t.getId(),
          t.getProfessionalId(),
          t.getStockItemId(),
          resolvedProductCategoryName,
          NumericUtil.toCents(t.getAmount()),
          t.getDate(),
          t.getDescription());
    }

    registrarAuditoriaFinanceiro(
        tenantId, "FINANCE_TRANSACTION_UPDATE", before, buildAuditPayload(req), id.toString());
    return toResponse(t);
  }

  @Transactional
  public void deletar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Transacao t =
        transacaoRepository
            .findAtivaByIdAndTenant(id, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Transacao nao encontrada", 404));

    Map<String, Object> before = buildAuditPayloadFromTransacao(t);

    // Soft delete (F2.3)
    t.setDeletedAt(Instant.now());
    t.setDeletedBy(authenticatedUser.idOuNulo());

    if (t.getType() == TipoTransacao.INCOME
        && t.getProfessionalId() != null
        && (t.getStockItemId() != null || t.getProductCategoryId() != null)) {
      commissionService.reverseProductCommissionIfApplicable(tenantId, id, "Transacao excluida");
    }
    registrarAuditoriaFinanceiro(tenantId, "FINANCE_TRANSACTION_DELETE", before, null, id.toString());
  }

  // ─── CONCILIACAO (F2.1) ──────────────────────────────────────────────────

  @Transactional
  public TransacaoResponse conciliar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Transacao t =
        transacaoRepository
            .findAtivaByIdAndTenant(id, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Transacao nao encontrada", 404));

    t.setReconciled(!t.isReconciled());
    t.setReconciledAt(t.isReconciled() ? Instant.now() : null);
    return toResponse(t);
  }

  // ─── EXPORTACAO CSV (F1.2) ───────────────────────────────────────────────

  @Transactional(readOnly = true)
  public StreamingResponseBody exportarCsv(
      Instant from,
      Instant to,
      TipoTransacao type,
      UUID categoryId,
      MetodoPagamento paymentMethod,
      UUID professionalId,
      Boolean reconciled) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<Transacao> transacoes =
        transacaoQueryRepository.listFiltered(
            new TransacaoFilter(
                tenantId, from, to, type, categoryId, paymentMethod, professionalId, reconciled),
            0,
            0);

    // Materializa as linhas ainda dentro da transacao/sessao — o StreamingResponseBody so e
    // executado depois que o controller retorna, quando a sessao JPA ja esta fechada.
    List<String> linhas = new ArrayList<>(transacoes.size());
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZONA_BR);
    for (Transacao t : transacoes) {
      String data = t.getDate() != null ? fmt.format(t.getDate()) : "";
      String tipo = t.getType() != null ? (t.getType() == TipoTransacao.INCOME ? "Entrada" : "Saida") : "";
      String categoria = t.getCategoryRef() != null ? escapeCsv(t.getCategoryRef().getName()) : "";
      String descricao = escapeCsv(t.getDescription());
      // Formato BR: virgula decimal, ponto milhar -> "1.234,56"
      String valor =
          String.format("%,.2f", t.getAmount().doubleValue())
              .replace('.', '#')
              .replace(',', '.')
              .replace('#', ',');
      String pagamento = t.getPaymentMethod() != null ? translatePaymentMethod(t.getPaymentMethod()) : "";
      String profissional = resolveProfessionalCsvCell(tenantId, t.getProfessionalId());
      String conciliado = t.isReconciled() ? "Sim" : "Nao";
      linhas.add(
          String.join(
              ";", data, tipo, categoria, descricao, valor, pagamento, profissional, conciliado));
    }

    return output -> {
      // BOM UTF-8 para compatibilidade com Excel BR
      output.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
      try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
        writer.println("Data;Tipo;Categoria;Descricao;Valor (R$);Forma de Pagamento;Profissional;Conciliado");
        for (String linha : linhas) {
          writer.println(linha);
        }
        writer.flush();
      }
    };
  }

  /**
   * O original le {@code t.professional.name} (associacao {@code @ManyToOne}) e cai no id quando a
   * associacao e nula. Aqui a entidade guarda so o id, entao o nome vem do repositorio — mesmo
   * resultado, com o mesmo fallback para o id.
   */
  private String resolveProfessionalCsvCell(UUID tenantId, UUID professionalId) {
    if (professionalId == null) return "";
    return profissionalRepository
        .findByIdAndTenantId(professionalId, tenantId)
        .map(Profissional::getName)
        .map(this::escapeCsv)
        .orElse(professionalId.toString());
  }

  private String escapeCsv(String value) {
    if (value == null) return "";
    String v = value.replace("\"", "\"\"");
    if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
      return "\"" + v + "\"";
    }
    return v;
  }

  private String translatePaymentMethod(MetodoPagamento method) {
    return switch (method) {
      case PIX -> "Pix";
      case CREDIT_CARD -> "Cartao de Credito";
      case DEBIT_CARD -> "Cartao de Debito";
      case CASH -> "Dinheiro";
      case OTHER -> "Outro";
    };
  }

  // ─── FLUXO DE CAIXA (F1.4) ───────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<FluxoCaixaDiarioResponse> fluxoDeCaixa(LocalDate from, LocalDate to) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  gs.d::date AS metric_date,
                  COALESCE(c.total_income,   0) AS total_income,
                  COALESCE(c.total_expenses, 0) AS total_expenses,
                  COALESCE(c.balance,        0) AS balance
                FROM generate_series(CAST(:from AS date), CAST(:to AS date), '1 day'::interval) AS gs(d)
                LEFT JOIN mv_finance_cashflow_daily c
                  ON c.metric_date = gs.d AND c.tenant_id = :tenantId
                ORDER BY gs.d
                """)
            .setParameter("from", from)
            .setParameter("to", to)
            .setParameter("tenantId", tenantId)
            .getResultList();

    List<FluxoCaixaDiarioResponse> result = new ArrayList<>();
    for (Object[] row : rows) {
      FluxoCaixaDiarioResponse item = new FluxoCaixaDiarioResponse();
      item.date = row[0].toString();
      item.income = NumericUtil.normalize((BigDecimal) row[1]);
      item.expenses = NumericUtil.normalize((BigDecimal) row[2]);
      item.balance = NumericUtil.normalize((BigDecimal) row[3]);
      result.add(item);
    }
    return result;
  }

  // ─── RECORRENTES (F2.2) ──────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<RecurringTransactionResponse> listarRecorrentes() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return recurringTransactionRepository
        .findByTenantIdAndActiveTrueOrderByCreatedAtDesc(tenantId)
        .stream()
        .map(this::toRecurringResponse)
        .toList();
  }

  @Transactional
  public RecurringTransactionResponse criarRecorrente(RecurringTransactionRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    if ("MONTHLY".equals(req.frequency)
        && (req.dayOfMonth == null || req.dayOfMonth < 1 || req.dayOfMonth > 28)) {
      throw new IllegalArgumentException("Para recorrencia MONTHLY, informe dayOfMonth entre 1 e 28");
    }
    if ("WEEKLY".equals(req.frequency)
        && (req.dayOfWeek == null || req.dayOfWeek < 0 || req.dayOfWeek > 6)) {
      throw new IllegalArgumentException("Para recorrencia WEEKLY, informe dayOfWeek entre 0 e 6");
    }

    RecurringTransaction rt = new RecurringTransaction();
    rt.setTenantId(tenantId);
    rt.setType(resolveTipoTransacao(req.type));
    rt.setCategoryId(parseUuidNullable(req.categoryId));
    rt.setDescription(req.description.trim());
    rt.setAmount(req.amount);
    rt.setPaymentMethod(resolveMetodoPagamento(req.paymentMethod));
    rt.setFrequency(req.frequency);
    rt.setDayOfMonth(req.dayOfMonth != null ? req.dayOfMonth.shortValue() : null);
    rt.setDayOfWeek(req.dayOfWeek != null ? req.dayOfWeek.shortValue() : null);
    recurringTransactionRepository.save(rt);
    return toRecurringResponse(rt);
  }

  @Transactional
  public void desativarRecorrente(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    RecurringTransaction rt =
        recurringTransactionRepository
            .findByIdAndTenantId(id, tenantId)
            .filter(RecurringTransaction::isActive)
            .orElseThrow(() -> new ApiClientErrorException("Recorrencia nao encontrada", 404));
    rt.setActive(false);
  }

  /**
   * Chamado pelo scheduler diariamente. Para cada template ativo, verifica se hoje e o dia de
   * geracao; se sim, cria a transacao se ainda nao existe.
   */
  @Transactional
  public void gerarLancamentosRecorrentes() {
    LocalDate hoje = LocalDate.now(ZONA_BR);
    int diaMes = hoje.getDayOfMonth();
    int diaSemana = hoje.getDayOfWeek().getValue() % 7; // 0=dom ... 6=sab (ISO: 1=seg -> ajuste)

    Instant inicioDia = hoje.atStartOfDay(ZONA_BR).toInstant();
    Instant fimDia = hoje.plusDays(1).atStartOfDay(ZONA_BR).toInstant();

    for (RecurringTransaction rt : recurringTransactionRepository.findByActiveTrue()) {
      boolean deveGerar = false;
      if ("MONTHLY".equals(rt.getFrequency())
          && rt.getDayOfMonth() != null
          && rt.getDayOfMonth() == diaMes) {
        deveGerar = true;
      } else if ("WEEKLY".equals(rt.getFrequency())
          && rt.getDayOfWeek() != null
          && rt.getDayOfWeek() == diaSemana) {
        deveGerar = true;
      }
      if (!deveGerar) continue;

      // Idempotencia: nao gera dois lancamentos do mesmo template no mesmo dia.
      if (transacaoRepository.existsByRecurringInPeriod(rt.getTenantId(), rt.getId(), inicioDia, fimDia)) {
        continue;
      }

      Transacao t = new Transacao();
      t.setTenantId(rt.getTenantId());
      t.setType(rt.getType());
      t.setCategoryId(
          rt.getCategoryId() != null
              ? rt.getCategoryId()
              : resolveTransactionCategory(rt.getTenantId(), "Outros").getId());
      t.setDescription(rt.getDescription());
      t.setAmount(rt.getAmount());
      t.setPaymentMethod(rt.getPaymentMethod());
      t.setDate(inicioDia);
      t.setSource("RECURRING");
      t.setRecurringId(rt.getId());
      transacaoRepository.save(t);
    }
  }

  private RecurringTransactionResponse toRecurringResponse(RecurringTransaction rt) {
    RecurringTransactionResponse r = new RecurringTransactionResponse();
    r.id = rt.getId().toString();
    r.type = rt.getType() != null ? rt.getType().name() : null;
    r.categoryId = rt.getCategoryId() != null ? rt.getCategoryId().toString() : null;
    r.description = rt.getDescription();
    r.amount = rt.getAmount();
    r.paymentMethod = rt.getPaymentMethod() != null ? rt.getPaymentMethod().name() : null;
    r.frequency = rt.getFrequency();
    r.dayOfMonth = rt.getDayOfMonth() != null ? rt.getDayOfMonth().intValue() : null;
    r.dayOfWeek = rt.getDayOfWeek() != null ? rt.getDayOfWeek().intValue() : null;
    r.active = rt.isActive();
    r.createdAt = rt.getCreatedAt() != null ? rt.getCreatedAt().toString() : null;
    if (rt.getCategoryId() != null) {
      r.categoryName =
          transactionCategoryRepository
              .findById(rt.getCategoryId())
              .map(TransactionCategory::getName)
              .orElse(null);
    }
    return r;
  }

  // ─── UTILITARIOS PRIVADOS ────────────────────────────────────────────────

  private void registrarAuditoriaFinanceiro(
      UUID tenantId, String action, Object before, Object after, String entityId) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.FINANCE;
      command.action = action;
      command.entityType = "TRANSACTION";
      command.entityId = entityId;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve quebrar o fluxo principal.
    }
  }

  TransacaoResponse toResponse(Transacao t) {
    TransacaoResponse r = new TransacaoResponse();
    r.id = t.getId().toString();
    r.tenantId = t.getTenantId().toString();
    r.appointmentId = t.getAppointmentId() != null ? t.getAppointmentId().toString() : null;
    r.professionalId = t.getProfessionalId() != null ? t.getProfessionalId().toString() : null;
    r.productId = t.getStockItemId() != null ? t.getStockItemId().toString() : null;
    r.productCategory = t.getProductCategoryRef() != null ? t.getProductCategoryRef().getName() : null;
    r.type = t.getType() != null ? t.getType().name() : null;
    r.category = t.getCategoryRef() != null ? t.getCategoryRef().getName() : null;
    r.description = t.getDescription();
    r.amount = t.getAmount();
    r.paymentMethod = t.getPaymentMethod() != null ? t.getPaymentMethod().name() : null;
    r.date = t.getDate() != null ? t.getDate().toString() : null;
    r.createdAt = t.getCreatedAt() != null ? t.getCreatedAt().toString() : null;
    r.reconciled = t.isReconciled();
    r.reconciledAt = t.getReconciledAt() != null ? t.getReconciledAt().toString() : null;
    r.source = t.getSource();
    r.recurringId = t.getRecurringId() != null ? t.getRecurringId().toString() : null;
    return r;
  }

  private UUID parseUuidNullable(String value) {
    if (value == null || value.isBlank()) return null;
    return UUID.fromString(value.trim());
  }

  private BigDecimal resolveAmount(TransacaoRequest req) {
    BigDecimal value = NumericUtil.normalize(req.amount);
    if (NumericUtil.isZeroOrNegative(value)) {
      throw new IllegalArgumentException("amount deve ser maior que zero");
    }
    return value;
  }

  private TipoTransacao resolveTipoTransacao(String value) {
    String normalized = normalizeNullable(value);
    if (normalized == null) throw new IllegalArgumentException("type e obrigatorio");
    try {
      return TipoTransacao.valueOf(normalized.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("type invalido");
    }
  }

  private MetodoPagamento resolveMetodoPagamento(String value) {
    String normalized = normalizeNullable(value);
    if (normalized == null) throw new IllegalArgumentException("paymentMethod e obrigatorio");
    try {
      return MetodoPagamento.valueOf(normalized.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("paymentMethod invalido");
    }
  }

  private String normalizeNullable(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private TransactionCategory resolveTransactionCategory(UUID tenantId, String categoryName) {
    String normalized = normalizeNullable(categoryName);
    if (normalized == null) throw new IllegalArgumentException("Categoria da transacao obrigatoria");
    return transactionCategoryRepository
        .findByTenantAndName(tenantId, normalized)
        .orElseGet(
            () -> {
              TransactionCategory category = new TransactionCategory();
              category.setTenantId(tenantId);
              category.setName(normalized);
              return transactionCategoryRepository.save(category);
            });
  }

  private ProductCategory resolveProductCategory(UUID tenantId, String categoryName) {
    String normalized = normalizeNullable(categoryName);
    if (normalized == null) return null;
    return productCategoryRepository
        .findByTenantAndName(tenantId, normalized)
        .orElseGet(
            () -> {
              ProductCategory category = new ProductCategory();
              category.setTenantId(tenantId);
              category.setName(normalized);
              return productCategoryRepository.save(category);
            });
  }

  /**
   * O original conta com {@code transacaoRepository.count("tenantId = ?1 and categoryId = ?2")} —
   * <b>sem</b> filtro de soft delete, ao contrario do {@code mapTransactionCountByCategoryId}.
   * Comportamento preservado como esta (a assimetria existe no original).
   */
  private long countTransactionsByCategory(UUID tenantId, UUID categoryId) {
    Object count =
        entityManager
            .createQuery("select count(t) from Transacao t where t.tenantId = :tenantId and t.categoryId = :categoryId")
            .setParameter("tenantId", tenantId)
            .setParameter("categoryId", categoryId)
            .getSingleResult();
    return count instanceof Number number ? number.longValue() : 0L;
  }

  private Map<UUID, Long> mapTransactionCountByCategoryId(UUID tenantId) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  category_id,
                  COUNT(*)::bigint
                FROM transactions
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                  AND category_id IS NOT NULL
                GROUP BY category_id
                """)
            .setParameter("tenantId", tenantId)
            .getResultList();

    Map<UUID, Long> counts = new HashMap<>();
    for (Object[] row : rows) {
      if (row == null || row.length < 2 || row[0] == null) continue;
      UUID categoryId = row[0] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row[0]));
      long count =
          row[1] instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(row[1]));
      counts.put(categoryId, count);
    }
    return counts;
  }

  private Map<String, Object> buildAuditPayloadFromTransacao(Transacao t) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", t.getType() != null ? t.getType().name() : null);
    payload.put("category", t.getCategoryRef() != null ? t.getCategoryRef().getName() : null);
    payload.put("description", t.getDescription());
    payload.put("amount", t.getAmount());
    payload.put("paymentMethod", t.getPaymentMethod() != null ? t.getPaymentMethod().name() : null);
    payload.put("date", t.getDate() != null ? t.getDate().toString() : null);
    if (t.getProfessionalId() != null) payload.put("professionalId", t.getProfessionalId().toString());
    if (t.getStockItemId() != null) payload.put("productId", t.getStockItemId().toString());
    if (t.getProductCategoryRef() != null) {
      payload.put("productCategory", t.getProductCategoryRef().getName());
    }
    return payload;
  }

  private Map<String, Object> buildAuditPayload(TransacaoRequest req) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", req.type);
    payload.put("category", req.category);
    payload.put("description", req.description);
    payload.put("amount", NumericUtil.normalize(req.amount));
    payload.put("paymentMethod", req.paymentMethod);
    payload.put("date", req.date);
    if (req.professionalId != null && !req.professionalId.isBlank()) {
      payload.put("professionalId", req.professionalId);
    }
    if (req.productId != null && !req.productId.isBlank()) payload.put("productId", req.productId);
    if (req.productCategory != null && !req.productCategory.isBlank()) {
      payload.put("productCategory", req.productCategory);
    }
    return payload;
  }
}
