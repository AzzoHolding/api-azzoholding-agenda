package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RecurringTransactionRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.TransacaoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ResumoFinanceiroResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.TransacaoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RecurringTransaction;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RecurringTransactionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository.SummaryTotals;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransactionCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

@ExtendWith(MockitoExtension.class)
class ServicoFinanceiroTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  @Mock private TransacaoRepository transacaoRepository;
  @Mock private TransacaoQueryRepository transacaoQueryRepository;
  @Mock private TransactionCategoryRepository transactionCategoryRepository;
  @Mock private ProductCategoryRepository productCategoryRepository;
  @Mock private RecurringTransactionRepository recurringTransactionRepository;
  @Mock private ProfissionalRepository profissionalRepository;
  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private AuditService auditService;
  @Mock private CommissionService commissionService;

  private ServicoFinanceiro servicoFinanceiro;

  @BeforeEach
  void setUp() {
    servicoFinanceiro =
        new ServicoFinanceiro(
            transacaoRepository,
            transacaoQueryRepository,
            transactionCategoryRepository,
            productCategoryRepository,
            recurringTransactionRepository,
            profissionalRepository,
            contextoTenant,
            authenticatedUser,
            auditService,
            commissionService);
  }

  @Test
  @DisplayName("Resumo devolve saldo = receitas - despesas")
  void resumoCalculaSaldo() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(transacaoQueryRepository.summarizeFiltered(any()))
        .thenReturn(new SummaryTotals(new BigDecimal("1500.00"), new BigDecimal("430.50")));

    ResumoFinanceiroResponse resumo =
        servicoFinanceiro.resumo(null, null, null, null, null, null, null);

    assertThat(resumo.totalIncome).isEqualByComparingTo("1500.00");
    assertThat(resumo.totalExpenses).isEqualByComparingTo("430.50");
    assertThat(resumo.balance).isEqualByComparingTo("1069.50");
  }

  @Test
  @DisplayName("Criar transacao resolve/reaproveita a categoria pelo nome e persiste os valores")
  void criarTransacaoReaproveitaCategoria() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    TransactionCategory categoriaExistente = categoria("Vendas");
    when(transactionCategoryRepository.findByTenantAndName(TENANT_ID, "Vendas"))
        .thenReturn(Optional.of(categoriaExistente));
    stubPersistenciaDeTransacao();

    TransacaoRequest req = new TransacaoRequest();
    req.type = "income";
    req.category = "Vendas";
    req.description = "  Venda de shampoo  ";
    req.amount = new BigDecimal("59.9");
    req.paymentMethod = "pix";
    req.date = "2026-03-20";

    TransacaoResponse response = servicoFinanceiro.criar(req);

    ArgumentCaptor<Transacao> captor = ArgumentCaptor.forClass(Transacao.class);
    verify(transacaoRepository).save(captor.capture());
    Transacao salva = captor.getValue();

    assertThat(salva.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(salva.getType()).isEqualTo(TipoTransacao.INCOME);
    assertThat(salva.getPaymentMethod()).isEqualTo(MetodoPagamento.PIX);
    assertThat(salva.getDescription()).isEqualTo("Venda de shampoo");
    assertThat(salva.getAmount()).isEqualByComparingTo("59.90");
    assertThat(salva.getCategoryId()).isEqualTo(categoriaExistente.getId());
    verify(transactionCategoryRepository, never()).save(any());

    assertThat(response.type).isEqualTo("INCOME");
    assertThat(response.category).isEqualTo("Vendas");
  }

  @Test
  @DisplayName("Sem profissional vinculado, criar transacao nao registra comissao de produto")
  void criarSemProfissionalNaoGeraComissao() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(transactionCategoryRepository.findByTenantAndName(TENANT_ID, "Vendas"))
        .thenReturn(Optional.of(categoria("Vendas")));
    when(productCategoryRepository.findByTenantAndName(TENANT_ID, "Cosmeticos"))
        .thenReturn(Optional.of(productCategory("Cosmeticos")));
    stubPersistenciaDeTransacao();

    TransacaoRequest req = new TransacaoRequest();
    req.type = "INCOME";
    req.category = "Vendas";
    req.description = "Venda";
    req.amount = new BigDecimal("10.00");
    req.paymentMethod = "CASH";
    req.date = "2026-03-20";
    req.productCategory = "Cosmeticos";

    servicoFinanceiro.criar(req);

    verify(commissionService, never())
        .registerProductCommissionIfApplicable(
            any(), any(), any(), any(), anyString(), anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("amount zero ou negativo e rejeitado com a mensagem do original")
  void amountInvalido() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    // A resolucao da categoria acontece antes da validacao do amount no fluxo original.
    when(transactionCategoryRepository.findByTenantAndName(TENANT_ID, "Vendas"))
        .thenReturn(Optional.of(categoria("Vendas")));

    TransacaoRequest req = new TransacaoRequest();
    req.type = "INCOME";
    req.category = "Vendas";
    req.description = "Venda";
    req.amount = BigDecimal.ZERO;
    req.paymentMethod = "CASH";
    req.date = "2026-03-20";

    assertThatThrownBy(() -> servicoFinanceiro.criar(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("amount deve ser maior que zero");
  }

  @Test
  @DisplayName("Deletar aplica soft delete (deletedAt/deletedBy), sem remover a linha")
  void deletarAplicaSoftDelete() {
    UUID transacaoId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();
    Transacao transacao = transacaoExistente(transacaoId);

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(transacaoRepository.findAtivaByIdAndTenant(transacaoId, TENANT_ID))
        .thenReturn(Optional.of(transacao));
    when(authenticatedUser.idOuNulo()).thenReturn(usuarioId);

    servicoFinanceiro.deletar(transacaoId);

    assertThat(transacao.getDeletedAt()).isNotNull();
    assertThat(transacao.getDeletedBy()).isEqualTo(usuarioId);
    verify(transacaoRepository, never()).delete(any());
    verify(transacaoRepository, never()).deleteById(any());
  }

  @Test
  @DisplayName("Transacao inexistente devolve 404 (equivalente ao NotFoundException do JAX-RS)")
  void transacaoInexistenteDevolve404() {
    UUID transacaoId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(transacaoRepository.findAtivaByIdAndTenant(transacaoId, TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicoFinanceiro.deletar(transacaoId))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Transacao nao encontrada")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  @DisplayName("Conciliar alterna o flag e limpa reconciledAt ao desconciliar")
  void conciliarAlterna() {
    UUID transacaoId = UUID.randomUUID();
    Transacao transacao = transacaoExistente(transacaoId);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(transacaoRepository.findAtivaByIdAndTenant(transacaoId, TENANT_ID))
        .thenReturn(Optional.of(transacao));

    servicoFinanceiro.conciliar(transacaoId);
    assertThat(transacao.isReconciled()).isTrue();
    assertThat(transacao.getReconciledAt()).isNotNull();

    servicoFinanceiro.conciliar(transacaoId);
    assertThat(transacao.isReconciled()).isFalse();
    assertThat(transacao.getReconciledAt()).isNull();
  }

  @Test
  @DisplayName("Renomear categoria para um nome ja usado por outra categoria e bloqueado")
  void renomearCategoriaDuplicada() {
    UUID id = UUID.randomUUID();
    TransactionCategory alvo = categoria("Antiga");
    alvo.setId(id);
    TransactionCategory outra = categoria("Nova");

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(transactionCategoryRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(alvo));
    when(transactionCategoryRepository.findByTenantAndName(TENANT_ID, "Nova")).thenReturn(Optional.of(outra));

    assertThatThrownBy(() -> servicoFinanceiro.renomearCategoria(id, "Nova"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Ja existe uma categoria com este nome");

    assertThat(alvo.getName()).isEqualTo("Antiga");
  }

  @Test
  @DisplayName("MONTHLY exige dayOfMonth entre 1 e 28")
  void recorrenteMensalExigeDiaValido() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);

    RecurringTransactionRequest req = new RecurringTransactionRequest();
    req.type = "EXPENSE";
    req.description = "Aluguel";
    req.amount = new BigDecimal("2500.00");
    req.paymentMethod = "PIX";
    req.frequency = "MONTHLY";
    req.dayOfMonth = 31;

    assertThatThrownBy(() -> servicoFinanceiro.criarRecorrente(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Para recorrencia MONTHLY, informe dayOfMonth entre 1 e 28");
  }

  @Test
  @DisplayName("Geracao de recorrentes so cria lancamento no dia do mes configurado")
  void recorrenteMensalSoGeraNoDiaCerto() {
    LocalDate hoje = LocalDate.now(ZONA_BR);
    RecurringTransaction noDia = recorrenteMensal((short) hoje.getDayOfMonth());
    RecurringTransaction outroDia =
        recorrenteMensal((short) (hoje.getDayOfMonth() == 1 ? 2 : hoje.getDayOfMonth() - 1));

    when(recurringTransactionRepository.findByActiveTrue()).thenReturn(List.of(noDia, outroDia));
    when(transacaoRepository.existsByRecurringInPeriod(
            eq(TENANT_ID), eq(noDia.getId()), any(Instant.class), any(Instant.class)))
        .thenReturn(false);

    servicoFinanceiro.gerarLancamentosRecorrentes();

    ArgumentCaptor<Transacao> captor = ArgumentCaptor.forClass(Transacao.class);
    verify(transacaoRepository).save(captor.capture());
    Transacao gerada = captor.getValue();

    assertThat(gerada.getRecurringId()).isEqualTo(noDia.getId());
    assertThat(gerada.getSource()).isEqualTo("RECURRING");
    assertThat(gerada.getAmount()).isEqualByComparingTo("2500.00");
    assertThat(gerada.getDate()).isEqualTo(hoje.atStartOfDay(ZONA_BR).toInstant());
  }

  @Test
  @DisplayName("Geracao de recorrentes e idempotente: nao duplica no mesmo dia")
  void recorrenteEhIdempotente() {
    LocalDate hoje = LocalDate.now(ZONA_BR);
    RecurringTransaction template = recorrenteMensal((short) hoje.getDayOfMonth());

    when(recurringTransactionRepository.findByActiveTrue()).thenReturn(List.of(template));
    when(transacaoRepository.existsByRecurringInPeriod(
            eq(TENANT_ID), eq(template.getId()), any(Instant.class), any(Instant.class)))
        .thenReturn(true);

    servicoFinanceiro.gerarLancamentosRecorrentes();

    verify(transacaoRepository, never()).save(any());
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  /**
   * O {@code @PrePersist} que gera o UUID so roda contra um EntityManager real; com repositorio
   * mockado, o id precisa ser atribuido aqui para que {@code toResponse} consiga montar a resposta.
   */
  private void stubPersistenciaDeTransacao() {
    when(transacaoRepository.save(any(Transacao.class)))
        .thenAnswer(
            invocation -> {
              Transacao t = invocation.getArgument(0);
              if (t.getId() == null) t.setId(UUID.randomUUID());
              return t;
            });
  }

  private br.com.phdigitalcode.azzo.agenda.pro.entity.ProductCategory productCategory(String nome) {
    br.com.phdigitalcode.azzo.agenda.pro.entity.ProductCategory c =
        new br.com.phdigitalcode.azzo.agenda.pro.entity.ProductCategory();
    c.setId(UUID.randomUUID());
    c.setTenantId(TENANT_ID);
    c.setName(nome);
    return c;
  }

  private TransactionCategory categoria(String nome) {
    TransactionCategory c = new TransactionCategory();
    c.setId(UUID.randomUUID());
    c.setTenantId(TENANT_ID);
    c.setName(nome);
    return c;
  }

  private Transacao transacaoExistente(UUID id) {
    Transacao t = new Transacao();
    t.setId(id);
    t.setTenantId(TENANT_ID);
    t.setType(TipoTransacao.INCOME);
    t.setCategoryId(UUID.randomUUID());
    t.setDescription("Receita");
    t.setAmount(new BigDecimal("100.00"));
    t.setPaymentMethod(MetodoPagamento.CASH);
    t.setDate(Instant.now());
    return t;
  }

  private RecurringTransaction recorrenteMensal(short dayOfMonth) {
    RecurringTransaction rt = new RecurringTransaction();
    rt.setId(UUID.randomUUID());
    rt.setTenantId(TENANT_ID);
    rt.setType(TipoTransacao.EXPENSE);
    rt.setCategoryId(UUID.randomUUID());
    rt.setDescription("Aluguel");
    rt.setAmount(new BigDecimal("2500.00"));
    rt.setPaymentMethod(MetodoPagamento.PIX);
    rt.setFrequency("MONTHLY");
    rt.setDayOfMonth(dayOfMonth);
    rt.setActive(true);
    return rt;
  }
}
