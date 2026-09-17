package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionEntry;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionRule;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionRuleSet;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServiceCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionCycleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionEntryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionRuleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionRuleSetRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServiceCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransactionCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre o calculo real de comissao de servico: base GROSS vs NET_OF_DISCOUNT, precedencia de
 * regra (SERVICE > SERVICE_CATEGORY > GENERAL), deduplicacao por origem e a politica de estorno.
 */
@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PROFESSIONAL_ID = UUID.randomUUID();
  private static final UUID APPOINTMENT_ID = UUID.randomUUID();
  private static final UUID SERVICE_ID = UUID.randomUUID();
  private static final UUID CATEGORY_ID = UUID.randomUUID();
  private static final UUID RULE_SET_ID = UUID.randomUUID();

  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private AuditService auditService;
  @Mock private ProfissionalRepository profissionalRepository;
  @Mock private ServicoRepository servicoRepository;
  @Mock private ServiceCategoryRepository serviceCategoryRepository;
  @Mock private CommissionRuleSetRepository ruleSetRepository;
  @Mock private CommissionRuleRepository ruleRepository;
  @Mock private CommissionEntryRepository entryRepository;
  @Mock private CommissionCycleRepository cycleRepository;
  @Mock private TransacaoRepository transacaoRepository;
  @Mock private TransactionCategoryRepository transactionCategoryRepository;
  @Mock private ItemEstoqueRepository itemEstoqueRepository;

  private CommissionService commissionService;

  @BeforeEach
  void setUp() {
    commissionService =
        new CommissionService(
            contextoTenant,
            authenticatedUser,
            auditService,
            profissionalRepository,
            servicoRepository,
            serviceCategoryRepository,
            ruleSetRepository,
            ruleRepository,
            entryRepository,
            cycleRepository,
            transacaoRepository,
            transactionCategoryRepository,
            itemEstoqueRepository);
  }

  @Test
  @DisplayName("Base GROSS: 10% sobre R$ 200,00 bruto gera R$ 20,00, ignorando o desconto")
  void comissaoSobreValorBruto() {
    prepararProfissionalERuleSet();
    prepararRegras(regra("GENERAL", null, new BigDecimal("10"), 0L, "GROSS"));

    CommissionEntry salvo =
        capturarEntradaGerada(item(new BigDecimal("200.00"), new BigDecimal("150.00")));

    assertThat(salvo.getBaseAmountCents()).isEqualTo(20000L);
    assertThat(salvo.getPercentAmountCents()).isEqualTo(2000L);
    assertThat(salvo.getFixedAmountCents()).isZero();
    assertThat(salvo.getTotalAmountCents()).isEqualTo(2000L);
    assertThat(salvo.getEntryStatus()).isEqualTo("OPEN");
    assertThat(salvo.getOriginType()).isEqualTo("SERVICE");
  }

  @Test
  @DisplayName("Base NET_OF_DISCOUNT: 10% incide sobre o liquido (R$ 150,00), nao sobre o bruto")
  void comissaoSobreValorLiquido() {
    prepararProfissionalERuleSet();
    prepararRegras(regra("GENERAL", null, new BigDecimal("10"), 0L, "NET_OF_DISCOUNT"));

    CommissionEntry salvo =
        capturarEntradaGerada(item(new BigDecimal("200.00"), new BigDecimal("150.00")));

    assertThat(salvo.getBaseAmountCents()).isEqualTo(15000L);
    assertThat(salvo.getTotalAmountCents()).isEqualTo(1500L);
  }

  @Test
  @DisplayName("Percentual e valor fixo somam no total da entrada")
  void percentualMaisValorFixo() {
    prepararProfissionalERuleSet();
    prepararRegras(regra("GENERAL", null, new BigDecimal("10"), 500L, "GROSS"));

    CommissionEntry salvo =
        capturarEntradaGerada(item(new BigDecimal("200.00"), new BigDecimal("200.00")));

    assertThat(salvo.getPercentAmountCents()).isEqualTo(2000L);
    assertThat(salvo.getFixedAmountCents()).isEqualTo(500L);
    assertThat(salvo.getTotalAmountCents()).isEqualTo(2500L);
  }

  @Test
  @DisplayName("Regra especifica do servico tem precedencia sobre categoria e sobre a regra geral")
  void regraDoServicoVenceCategoriaEGeral() {
    prepararProfissionalERuleSet();
    prepararRegras(
        regra("GENERAL", null, new BigDecimal("5"), 0L, "GROSS"),
        regraCategoria("SERVICE_CATEGORY", "Barba", new BigDecimal("15")),
        regraServico(SERVICE_ID, new BigDecimal("30")));

    CommissionEntry salvo =
        capturarEntradaGerada(item(new BigDecimal("100.00"), new BigDecimal("100.00")));

    assertThat(salvo.getPercentValue()).isEqualByComparingTo("30");
    assertThat(salvo.getTotalAmountCents()).isEqualTo(3000L);
  }

  @Test
  @DisplayName("Sem regra do servico, a regra da categoria vence a regra geral")
  void regraDaCategoriaVenceGeral() {
    prepararProfissionalERuleSet();
    prepararRegras(
        regra("GENERAL", null, new BigDecimal("5"), 0L, "GROSS"),
        regraCategoria("SERVICE_CATEGORY", "Barba", new BigDecimal("15")));
    when(serviceCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(categoria("Barba")));

    CommissionEntry salvo =
        capturarEntradaGerada(item(new BigDecimal("100.00"), new BigDecimal("100.00")));

    assertThat(salvo.getPercentValue()).isEqualByComparingTo("15");
    assertThat(salvo.getTotalAmountCents()).isEqualTo(1500L);
  }

  @Test
  @DisplayName("Nao duplica comissao: item que ja tem entrada nao revertida e ignorado")
  void naoDuplicaComissaoDoMesmoItem() {
    AgendamentoItem agendamentoItem = item(new BigDecimal("100.00"), new BigDecimal("100.00"));
    when(profissionalRepository.findById(PROFESSIONAL_ID)).thenReturn(Optional.of(profissional()));
    when(ruleSetRepository.listActiveProfessionalScoped(TENANT_ID, PROFESSIONAL_ID))
        .thenReturn(List.of(ruleSet()));
    when(entryRepository.findLatestNonReversedByTenantAndOrigin(
            TENANT_ID, "SERVICE", agendamentoItem.getId()))
        .thenReturn(Optional.of(new CommissionEntry()));

    commissionService.registerServiceCommissionsIfApplicable(
        TENANT_ID, APPOINTMENT_ID, PROFESSIONAL_ID, List.of(agendamentoItem), LocalDate.now());

    verify(entryRepository, never()).save(any());
  }

  @Test
  @DisplayName("Comissao total zero nao gera entrada")
  void naoGeraEntradaQuandoTotalZero() {
    prepararProfissionalERuleSet();
    prepararRegras(regra("GENERAL", null, new BigDecimal("10"), 0L, "GROSS"));

    AgendamentoItem agendamentoItem = item(BigDecimal.ZERO, BigDecimal.ZERO);
    when(entryRepository.findLatestNonReversedByTenantAndOrigin(
            TENANT_ID, "SERVICE", agendamentoItem.getId()))
        .thenReturn(Optional.empty());
    when(servicoRepository.findById(SERVICE_ID)).thenReturn(Optional.of(servico()));

    commissionService.registerServiceCommissionsIfApplicable(
        TENANT_ID, APPOINTMENT_ID, PROFESSIONAL_ID, List.of(agendamentoItem), LocalDate.now());

    verify(entryRepository, never()).save(any());
  }

  @Test
  @DisplayName("Sem rule set ativo, nenhuma comissao e registrada")
  void semRuleSetNaoRegistra() {
    when(profissionalRepository.findById(PROFESSIONAL_ID)).thenReturn(Optional.of(profissional()));
    when(ruleSetRepository.listActiveProfessionalScoped(TENANT_ID, PROFESSIONAL_ID)).thenReturn(List.of());
    when(ruleSetRepository.listActiveGlobalScoped(TENANT_ID)).thenReturn(List.of());

    commissionService.registerServiceCommissionsIfApplicable(
        TENANT_ID,
        APPOINTMENT_ID,
        PROFESSIONAL_ID,
        List.of(item(new BigDecimal("100.00"), new BigDecimal("100.00"))),
        LocalDate.now());

    verify(entryRepository, never()).save(any());
  }

  @Test
  @DisplayName("Estorno marca a entrada como REVERSED e concatena o motivo nas notas")
  void estornoMarcaReversed() {
    CommissionEntry entry = entradaAberta();
    CommissionRule rule = regra("GENERAL", null, new BigDecimal("10"), 0L, "GROSS");
    rule.setRefundPolicy("REVERSE_COMMISSION");
    entry.setRuleId(rule.getId());

    when(entryRepository.findByTenantAndOrigin(TENANT_ID, "PRODUCT", APPOINTMENT_ID))
        .thenReturn(Optional.of(entry));
    when(ruleRepository.findById(rule.getId())).thenReturn(Optional.of(rule));

    commissionService.reverseProductCommissionIfApplicable(TENANT_ID, APPOINTMENT_ID, "Transacao editada");

    assertThat(entry.getEntryStatus()).isEqualTo("REVERSED");
    assertThat(entry.getReversedAt()).isNotNull();
    assertThat(entry.getNotes()).isEqualTo("Comissao original | Transacao editada");
  }

  @Test
  @DisplayName("Regra KEEP_COMMISSION nao reverte a entrada")
  void keepCommissionNaoReverte() {
    CommissionEntry entry = entradaAberta();
    CommissionRule rule = regra("GENERAL", null, new BigDecimal("10"), 0L, "GROSS");
    rule.setRefundPolicy("KEEP_COMMISSION");
    entry.setRuleId(rule.getId());

    when(entryRepository.findByTenantAndOrigin(TENANT_ID, "PRODUCT", APPOINTMENT_ID))
        .thenReturn(Optional.of(entry));
    when(ruleRepository.findById(rule.getId())).thenReturn(Optional.of(rule));

    commissionService.reverseProductCommissionIfApplicable(TENANT_ID, APPOINTMENT_ID, "Transacao editada");

    assertThat(entry.getEntryStatus()).isEqualTo("OPEN");
    assertThat(entry.getReversedAt()).isNull();
  }

  /**
   * Venda desfeita com a comissao JA PAGA: a entrada paga fica como esta (o pagamento aconteceu), e
   * nasce um DESCONTO aberto do mesmo valor para o proximo ciclo (analise de 2026-09-16, M4). Antes,
   * o estorno ignorava a comissao paga e nao havia como descontar.
   */
  @Test
  @DisplayName("Entrada PAID nao e revertida: vira desconto no proximo ciclo")
  void entradaPagaViraDescontoNoProximoCiclo() {
    CommissionEntry paga = entradaAberta();
    paga.setEntryStatus("PAID");
    paga.setTotalAmountCents(2000L);
    when(entryRepository.findByTenantAndOrigin(TENANT_ID, "PRODUCT", APPOINTMENT_ID))
        .thenReturn(Optional.of(paga));

    commissionService.reverseProductCommissionIfApplicable(TENANT_ID, APPOINTMENT_ID, "Transacao excluida");

    assertThat(paga.getEntryStatus()).isEqualTo("PAID");
    ArgumentCaptor<CommissionEntry> captor = ArgumentCaptor.forClass(CommissionEntry.class);
    verify(entryRepository).save(captor.capture());
    CommissionEntry desconto = captor.getValue();
    assertThat(desconto.getTotalAmountCents()).isEqualTo(-2000L);
    assertThat(desconto.getEntryStatus()).isEqualTo("OPEN");
    assertThat(desconto.getOriginType()).isEqualTo("MANUAL_ADJUSTMENT");
    assertThat(desconto.getOriginId()).isEqualTo(paga.getId());
    assertThat(desconto.getProfessionalId()).isEqualTo(PROFESSIONAL_ID);
    assertThat(paga.getReversalEntryId()).isEqualTo(desconto.getId());
  }

  @Test
  @DisplayName("Comissao paga ja descontada nao e descontada de novo")
  void descontoDeComissaoPagaNaoDuplica() {
    CommissionEntry paga = entradaAberta();
    paga.setEntryStatus("PAID");
    paga.setTotalAmountCents(2000L);
    paga.setReversalEntryId(UUID.randomUUID());
    when(entryRepository.findByTenantAndOrigin(TENANT_ID, "PRODUCT", APPOINTMENT_ID))
        .thenReturn(Optional.of(paga));

    commissionService.reverseProductCommissionIfApplicable(TENANT_ID, APPOINTMENT_ID, "de novo");

    verify(entryRepository, never()).save(any());
  }

  /**
   * Pagar o ciclo com um profissional de saldo NEGATIVO (desconto maior que o que ganhou): ele nao
   * recebe agora e as entradas dele passam para o proximo ciclo — o desconto nao some.
   */
  @Test
  @DisplayName("Pagar ciclo adia quem ficou com saldo zerado ou negativo")
  void pagarCicloAdiaSaldoNegativo() {
    UUID cycleId = UUID.randomUUID();
    br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionCycle ciclo =
        new br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionCycle();
    ciclo.setId(cycleId);
    ciclo.setTenantId(TENANT_ID);
    ciclo.setStatus("CLOSED");
    ciclo.setTotalAmountCents(3000L - 500L);
    ciclo.setPeriodStart(LocalDate.of(2026, 9, 1));
    ciclo.setPeriodEnd(LocalDate.of(2026, 9, 15));
    UUID devedor = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(cycleRepository.findByTenantIdAndId(TENANT_ID, cycleId)).thenReturn(Optional.of(ciclo));
    when(entryRepository.sumTotalCentsByProfessionalForCycle(TENANT_ID, cycleId))
        .thenReturn(List.of(new Object[] {PROFESSIONAL_ID, 3000L}, new Object[] {devedor, -500L}));
    lenient().when(profissionalRepository.findById(any())).thenReturn(Optional.of(profissional()));
    lenient().when(transactionCategoryRepository.findByTenantAndName(any(), any())).thenReturn(Optional.empty());
    lenient()
        .when(transactionCategoryRepository.save(any()))
        .thenAnswer(inv -> {
          var categoria = (br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory) inv.getArgument(0);
          categoria.setId(UUID.randomUUID());
          return categoria;
        });

    commissionService.payCycle(cycleId, null);

    verify(entryRepository).releaseProfessionalFromCycle(TENANT_ID, cycleId, devedor);
    verify(entryRepository, never()).releaseProfessionalFromCycle(TENANT_ID, cycleId, PROFESSIONAL_ID);
    assertThat(ciclo.getTotalAmountCents()).isEqualTo(3000L);
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private CommissionEntry capturarEntradaGerada(AgendamentoItem agendamentoItem) {
    when(entryRepository.findLatestNonReversedByTenantAndOrigin(
            TENANT_ID, "SERVICE", agendamentoItem.getId()))
        .thenReturn(Optional.empty());
    when(servicoRepository.findById(SERVICE_ID)).thenReturn(Optional.of(servico()));

    commissionService.registerServiceCommissionsIfApplicable(
        TENANT_ID, APPOINTMENT_ID, PROFESSIONAL_ID, List.of(agendamentoItem), LocalDate.of(2026, 3, 20));

    ArgumentCaptor<CommissionEntry> captor = ArgumentCaptor.forClass(CommissionEntry.class);
    verify(entryRepository).save(captor.capture());
    return captor.getValue();
  }

  private void prepararProfissionalERuleSet() {
    when(profissionalRepository.findById(PROFESSIONAL_ID)).thenReturn(Optional.of(profissional()));
    when(ruleSetRepository.listActiveProfessionalScoped(TENANT_ID, PROFESSIONAL_ID))
        .thenReturn(List.of(ruleSet()));
  }

  private void prepararRegras(CommissionRule... regras) {
    when(ruleRepository.listByRuleSet(TENANT_ID, RULE_SET_ID)).thenReturn(List.of(regras));
    lenient().when(serviceCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());
  }

  private Profissional profissional() {
    Profissional p = new Profissional();
    p.setId(PROFESSIONAL_ID);
    p.setTenantId(TENANT_ID);
    p.setName("Ana");
    return p;
  }

  private CommissionRuleSet ruleSet() {
    CommissionRuleSet rs = new CommissionRuleSet();
    rs.setId(RULE_SET_ID);
    rs.setTenantId(TENANT_ID);
    rs.setScopeType("PROFESSIONAL");
    rs.setProfessionalId(PROFESSIONAL_ID);
    rs.setName("Padrao");
    rs.setActive(true);
    return rs;
  }

  private Servico servico() {
    Servico s = new Servico();
    s.setId(SERVICE_ID);
    s.setTenantId(TENANT_ID);
    s.setName("Corte");
    s.setCategoryId(CATEGORY_ID);
    return s;
  }

  private ServiceCategory categoria(String nome) {
    ServiceCategory c = new ServiceCategory();
    c.setId(CATEGORY_ID);
    c.setTenantId(TENANT_ID);
    c.setName(nome);
    return c;
  }

  private AgendamentoItem item(BigDecimal grossAmount, BigDecimal totalPrice) {
    AgendamentoItem item = new AgendamentoItem();
    item.setId(UUID.randomUUID());
    item.setTenantId(TENANT_ID);
    item.setAppointmentId(APPOINTMENT_ID);
    item.setServiceId(SERVICE_ID);
    item.setQuantity(1);
    item.setGrossAmount(grossAmount);
    item.setTotalPrice(totalPrice);
    return item;
  }

  private CommissionRule regra(
      String targetType, UUID targetId, BigDecimal percent, long fixedCents, String baseType) {
    CommissionRule rule = new CommissionRule();
    rule.setId(UUID.randomUUID());
    rule.setTenantId(TENANT_ID);
    rule.setRuleSetId(RULE_SET_ID);
    rule.setTargetType(targetType);
    rule.setTargetId(targetId);
    rule.setPercentValue(percent);
    rule.setFixedAmountCents(fixedCents);
    rule.setPercentBaseType(baseType);
    rule.setRefundPolicy("REVERSE_COMMISSION");
    rule.setActive(true);
    return rule;
  }

  private CommissionRule regraCategoria(String targetType, String targetCode, BigDecimal percent) {
    CommissionRule rule = regra(targetType, null, percent, 0L, "GROSS");
    rule.setTargetCode(targetCode);
    return rule;
  }

  private CommissionRule regraServico(UUID serviceId, BigDecimal percent) {
    return regra("SERVICE", serviceId, percent, 0L, "GROSS");
  }

  private CommissionEntry entradaAberta() {
    CommissionEntry entry = new CommissionEntry();
    entry.setId(UUID.randomUUID());
    entry.setTenantId(TENANT_ID);
    entry.setProfessionalId(PROFESSIONAL_ID);
    entry.setEntryStatus("OPEN");
    entry.setNotes("Comissao original");
    entry.setPercentValue(BigDecimal.TEN);
    return entry;
  }
}
