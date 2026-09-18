package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ComandaDtos;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackageBalance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackagePurchase;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Comanda;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantLoyaltySettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantOperationalSettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.TenantAsaasChargeService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentDepositRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientPackageBalanceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientPackagePurchaseRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaPagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentacaoEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicePackageItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicePackageRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantLoyaltySettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantOperationalSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransactionCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre {@code modules/pos/application/ServicoComanda.java}: montagem de itens, rateio de desconto,
 * exigencia de quitacao exata no fechamento, rateio da receita entre os meios de pagamento reais,
 * gorjeta como par receita/despesa, fidelidade e o estorno.
 */
class ServicoComandaTest {

  private ComandaRepository comandaRepository;
  private ComandaItemRepository comandaItemRepository;
  private ComandaPagamentoRepository comandaPagamentoRepository;
  private ServicoRepository servicoRepository;
  private ItemEstoqueRepository itemEstoqueRepository;
  private ProfissionalRepository profissionalRepository;
  private ClienteRepository clienteRepository;
  private AppointmentDepositRepository appointmentDepositRepository;
  private TenantAsaasChargeService tenantAsaasChargeService;
  private AsaasClient asaasClient;
  private EstoqueMovimentacaoService estoqueMovimentacaoService;
  private CommissionService commissionService;
  private TransacaoRepository transacaoRepository;
  private TransactionCategoryRepository transactionCategoryRepository;
  private ServicePackageRepository servicePackageRepository;
  private ServicePackageItemRepository servicePackageItemRepository;
  private ClientPackagePurchaseRepository clientPackagePurchaseRepository;
  private ClientPackageBalanceRepository clientPackageBalanceRepository;
  private TenantLoyaltySettingsRepository tenantLoyaltySettingsRepository;
  private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
  private AuditService auditService;
  private TenantOperationalSettingsRepository tenantOperationalSettingsRepository;
  private AgendamentoRepository agendamentoRepository;
  private TravaFinanceira travaFinanceira;
  private br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipRepository clientMembershipRepository;
  private br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipBalanceRepository
      clientMembershipBalanceRepository;
  private AuthenticatedUser authenticatedUser;
  private TenantOperationalSettings configuracoes;
  private ServicoComanda service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID usuarioId = UUID.randomUUID();
  private final UUID comandaId = UUID.randomUUID();
  private final UUID professionalId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID serviceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    comandaRepository = mock(ComandaRepository.class);
    comandaItemRepository = mock(ComandaItemRepository.class);
    comandaPagamentoRepository = mock(ComandaPagamentoRepository.class);
    servicoRepository = mock(ServicoRepository.class);
    itemEstoqueRepository = mock(ItemEstoqueRepository.class);
    profissionalRepository = mock(ProfissionalRepository.class);
    clienteRepository = mock(ClienteRepository.class);
    appointmentDepositRepository = mock(AppointmentDepositRepository.class);
    tenantAsaasChargeService = mock(TenantAsaasChargeService.class);
    asaasClient = mock(AsaasClient.class);
    estoqueMovimentacaoService = mock(EstoqueMovimentacaoService.class);
    commissionService = mock(CommissionService.class);
    transacaoRepository = mock(TransacaoRepository.class);
    transactionCategoryRepository = mock(TransactionCategoryRepository.class);
    servicePackageRepository = mock(ServicePackageRepository.class);
    servicePackageItemRepository = mock(ServicePackageItemRepository.class);
    clientPackagePurchaseRepository = mock(ClientPackagePurchaseRepository.class);
    clientPackageBalanceRepository = mock(ClientPackageBalanceRepository.class);
    tenantLoyaltySettingsRepository = mock(TenantLoyaltySettingsRepository.class);
    movimentacaoEstoqueRepository = mock(MovimentacaoEstoqueRepository.class);
    auditService = mock(AuditService.class);
    tenantOperationalSettingsRepository = mock(TenantOperationalSettingsRepository.class);
    agendamentoRepository = mock(AgendamentoRepository.class);
    travaFinanceira = mock(TravaFinanceira.class);
    clientMembershipRepository =
        mock(br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipRepository.class);
    clientMembershipBalanceRepository =
        mock(br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipBalanceRepository.class);

    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    authenticatedUser = mock(AuthenticatedUser.class);
    when(authenticatedUser.idOuNulo()).thenReturn(usuarioId);

    // Sem teto configurado (100%) e sem papel nenhum: e o comportamento de antes da V131, que e o
    // que a maioria dos testes daqui exercita.
    configuracoes = new TenantOperationalSettings();
    configuracoes.setTenantId(tenantId);
    when(tenantOperationalSettingsRepository.findByTenantIdOrCreate(any()))
        .thenReturn(configuracoes);
    when(agendamentoRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());

    when(comandaItemRepository.findByComandaIdOrderByCreatedAt(any())).thenReturn(List.of());
    when(comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(any())).thenReturn(List.of());
    when(tenantLoyaltySettingsRepository.findByTenantId(any())).thenReturn(Optional.empty());
    when(clientPackagePurchaseRepository.findByTenantIdAndComandaId(any(), any()))
        .thenReturn(List.of());
    when(movimentacaoEstoqueRepository.findByTenantIdAndComandaItemId(any(), any()))
        .thenReturn(List.of());
    when(comandaItemRepository.save(any(ComandaItem.class)))
        .thenAnswer(
            inv -> {
              ComandaItem i = inv.getArgument(0);
              if (i.getId() == null) i.setId(UUID.randomUUID());
              return i;
            });
    when(comandaPagamentoRepository.save(any(ComandaPagamento.class)))
        .thenAnswer(
            inv -> {
              ComandaPagamento p = inv.getArgument(0);
              if (p.getId() == null) p.setId(UUID.randomUUID());
              return p;
            });
    when(transacaoRepository.save(any(Transacao.class)))
        .thenAnswer(
            inv -> {
              Transacao t = inv.getArgument(0);
              if (t.getId() == null) t.setId(UUID.randomUUID());
              return t;
            });
    when(clientPackagePurchaseRepository.save(any(ClientPackagePurchase.class)))
        .thenAnswer(
            inv -> {
              ClientPackagePurchase c = inv.getArgument(0);
              if (c.getId() == null) c.setId(UUID.randomUUID());
              return c;
            });
    when(transactionCategoryRepository.findByTenantAndName(any(), anyString()))
        .thenAnswer(
            inv -> {
              TransactionCategory category = new TransactionCategory();
              category.setId(UUID.randomUUID());
              category.setTenantId(tenantId);
              category.setName(inv.getArgument(1));
              return Optional.of(category);
            });

    service =
        new ServicoComanda(
            contextoTenant,
            authenticatedUser,
            comandaRepository,
            comandaItemRepository,
            comandaPagamentoRepository,
            servicoRepository,
            itemEstoqueRepository,
            profissionalRepository,
            clienteRepository,
            appointmentDepositRepository,
            tenantAsaasChargeService,
            asaasClient,
            estoqueMovimentacaoService,
            commissionService,
            transacaoRepository,
            transactionCategoryRepository,
            servicePackageRepository,
            servicePackageItemRepository,
            clientPackagePurchaseRepository,
            clientPackageBalanceRepository,
            tenantLoyaltySettingsRepository,
            movimentacaoEstoqueRepository,
            tenantOperationalSettingsRepository,
            agendamentoRepository,
            auditService,
            travaFinanceira,
            clientMembershipRepository,
            clientMembershipBalanceRepository);
  }

  // ---------------------------------------------------------------- helpers

  private Comanda comanda(String status) {
    Comanda comanda = new Comanda();
    comanda.setId(comandaId);
    comanda.setTenantId(tenantId);
    comanda.setStatus(status);
    comanda.setOpenedAt(Instant.now());
    when(comandaRepository.findByIdAndTenantId(eq(comandaId), eq(tenantId)))
        .thenReturn(Optional.of(comanda));
    when(comandaRepository.findByIdAndTenantParaAtualizacao(eq(comandaId), eq(tenantId)))
        .thenReturn(Optional.of(comanda));
    return comanda;
  }

  private ComandaItem item(String tipo, String total, UUID professional) {
    ComandaItem item = new ComandaItem();
    item.setId(UUID.randomUUID());
    item.setTenantId(tenantId);
    item.setComandaId(comandaId);
    item.setTipo(tipo);
    item.setReferenciaId(serviceId);
    item.setDescricao("Corte");
    item.setProfessionalId(professional);
    item.setQuantidade(BigDecimal.ONE);
    item.setPrecoUnitario(new BigDecimal(total));
    item.setTotal(new BigDecimal(total));
    return item;
  }

  private ComandaPagamento pagamento(String meio, String valor, String status) {
    ComandaPagamento pagamento = new ComandaPagamento();
    pagamento.setId(UUID.randomUUID());
    pagamento.setTenantId(tenantId);
    pagamento.setComandaId(comandaId);
    pagamento.setMeio(meio);
    pagamento.setValor(new BigDecimal(valor));
    pagamento.setStatus(status);
    return pagamento;
  }

  private void itensDaComanda(ComandaItem... itens) {
    when(comandaItemRepository.findByComandaIdOrderByCreatedAt(eq(comandaId)))
        .thenReturn(List.of(itens));
  }

  private void pagamentosDaComanda(ComandaPagamento... pagamentos) {
    when(comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(eq(comandaId)))
        .thenReturn(List.of(pagamentos));
  }

  private List<Transacao> transacoesSalvas() {
    ArgumentCaptor<Transacao> captor = ArgumentCaptor.forClass(Transacao.class);
    verify(transacaoRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return captor.getAllValues();
  }

  // ---------------------------------------------------------------- rateio

  @Test
  void ratearDescontoDistribuiProporcionalmenteComSobraNoUltimoItem() {
    ComandaItem a = item(ComandaItem.TIPO_SERVICO, "100.00", null);
    ComandaItem b = item(ComandaItem.TIPO_SERVICO, "50.00", null);
    ComandaItem c = item(ComandaItem.TIPO_SERVICO, "50.00", null);

    // subtotal 200, total 180 (10% de desconto)
    List<BigDecimal> valores =
        service.ratearDesconto(
            List.of(a, b, c), new BigDecimal("200.00"), new BigDecimal("180.00"));

    assertThat(valores.get(0)).isEqualByComparingTo("90.00");
    assertThat(valores.get(1)).isEqualByComparingTo("45.00");
    assertThat(valores.get(2)).isEqualByComparingTo("45.00");
    assertThat(valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("180.00");
  }

  @Test
  void ratearDescontoFechaExatamenteNoTotalMesmoComDizimaPeriodica() {
    ComandaItem a = item(ComandaItem.TIPO_SERVICO, "10.00", null);
    ComandaItem b = item(ComandaItem.TIPO_SERVICO, "10.00", null);
    ComandaItem c = item(ComandaItem.TIPO_SERVICO, "10.00", null);

    List<BigDecimal> valores =
        service.ratearDesconto(List.of(a, b, c), new BigDecimal("30.00"), new BigDecimal("10.00"));

    // O ultimo item absorve o residuo de arredondamento — a soma tem que bater no total exato,
    // senao a receita lancada nao fecha com o que foi pago.
    assertThat(valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("10.00");
  }

  @Test
  void ratearDescontoComSubtotalZeroDevolveZeroParaTodosOsItens() {
    List<BigDecimal> valores =
        service.ratearDesconto(
            List.of(item(ComandaItem.TIPO_SERVICO, "0.00", null)),
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    assertThat(valores).containsExactly(BigDecimal.ZERO);
  }

  // ---------------------------------------------------------------- itens

  @Test
  void adicionarItemServicoUsaPrecoDeTabelaQuandoOmitidoEMultiplicaPelaQuantidade() {
    comanda(Comanda.STATUS_ABERTA);
    Servico servico = new Servico();
    servico.setId(serviceId);
    servico.setTenantId(tenantId);
    servico.setName("Corte");
    servico.setPrice(new BigDecimal("70.00"));
    when(servicoRepository.findByIdAndTenantId(eq(serviceId), eq(tenantId)))
        .thenReturn(Optional.of(servico));

    ComandaDtos.AdicionarItemRequest req = new ComandaDtos.AdicionarItemRequest();
    req.tipo = ComandaItem.TIPO_SERVICO;
    req.referenciaId = serviceId.toString();
    req.quantidade = new BigDecimal("2");

    service.adicionarItem(comandaId, req);

    ArgumentCaptor<ComandaItem> captor = ArgumentCaptor.forClass(ComandaItem.class);
    verify(comandaItemRepository).save(captor.capture());
    ComandaItem salvo = captor.getValue();
    assertThat(salvo.getDescricao()).isEqualTo("Corte");
    assertThat(salvo.getPrecoUnitario()).isEqualByComparingTo("70.00");
    assertThat(salvo.getTotal()).isEqualByComparingTo("140.00");
  }

  @Test
  void adicionarItemProdutoSemPrecoDeVendaFalha() {
    comanda(Comanda.STATUS_ABERTA);
    ItemEstoque produto = new ItemEstoque();
    produto.setId(serviceId);
    produto.setTenantId(tenantId);
    produto.setNome("Shampoo");
    when(itemEstoqueRepository.findByIdAndTenantId(eq(serviceId), eq(tenantId)))
        .thenReturn(Optional.of(produto));

    ComandaDtos.AdicionarItemRequest req = new ComandaDtos.AdicionarItemRequest();
    req.tipo = ComandaItem.TIPO_PRODUTO;
    req.referenciaId = serviceId.toString();

    assertThatThrownBy(() -> service.adicionarItem(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Preco de venda e obrigatorio para item do tipo PRODUTO.");

    verify(comandaItemRepository, never()).save(any());
  }

  @Test
  void adicionarItemPacoteExigeComandaComCliente() {
    comanda(Comanda.STATUS_ABERTA);

    ComandaDtos.AdicionarItemRequest req = new ComandaDtos.AdicionarItemRequest();
    req.tipo = ComandaItem.TIPO_PACOTE;
    req.referenciaId = serviceId.toString();

    assertThatThrownBy(() -> service.adicionarItem(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Item do tipo PACOTE exige comanda com cliente identificado.");
  }

  @Test
  void adicionarItemEmComandaFechadaFalha() {
    comanda(Comanda.STATUS_FECHADA);

    ComandaDtos.AdicionarItemRequest req = new ComandaDtos.AdicionarItemRequest();
    req.tipo = ComandaItem.TIPO_SERVICO;
    req.referenciaId = serviceId.toString();

    assertThatThrownBy(() -> service.adicionarItem(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Comanda nao esta aberta.");
  }

  // ---------------------------------------------------------------- desconto

  @Test
  void aplicarDescontoAcimaDeCemPorCentoFalha() {
    comanda(Comanda.STATUS_ABERTA);

    ComandaDtos.AplicarDescontoRequest req = new ComandaDtos.AplicarDescontoRequest();
    req.percentual = new BigDecimal("100.01");
    req.motivo = "erro";

    assertThatThrownBy(() -> service.aplicarDesconto(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Desconto nao pode ser maior que 100%.");
  }

  @Test
  void aplicarDescontoConverteOPercentualEmValorEAtualizaOTotal() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "200.00", null));

    ComandaDtos.AplicarDescontoRequest req = new ComandaDtos.AplicarDescontoRequest();
    req.percentual = new BigDecimal("10");
    req.motivo = "  cliente fiel  ";

    service.aplicarDesconto(comandaId, req);

    assertThat(comanda.getDesconto()).isEqualByComparingTo("20.00");
    assertThat(comanda.getTotal()).isEqualByComparingTo("180.00");
    assertThat(comanda.getDescontoMotivo()).isEqualTo("cliente fiel");
  }

  // ---------------------------------------------------------------- fechamento

  @Test
  void fecharSemItensFalha() {
    comanda(Comanda.STATUS_ABERTA);

    assertThatThrownBy(() -> service.fechar(comandaId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Comanda sem itens nao pode ser fechada.");
  }

  @Test
  void fecharExigeQuitacaoQueCubraTotalMaisGorjeta() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setSubtotal(new BigDecimal("100.00"));
    comanda.setTotal(new BigDecimal("100.00"));
    comanda.setGorjeta(new BigDecimal("10.00"));
    comanda.setGorjetaProfessionalId(professionalId);
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "100.00", null));
    // Pago so o total, sem a gorjeta.
    pagamentosDaComanda(
        pagamento(
            ComandaPagamento.MEIO_DINHEIRO, "100.00", ComandaPagamento.STATUS_CONFIRMADO));

    assertThatThrownBy(() -> service.fechar(comandaId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Comanda nao esta quitada: pago 100.00, total com gorjeta 110.00.");

    verify(transacaoRepository, never()).save(any());
  }

  @Test
  void fecharIgnoraPagamentoPendenteNaQuitacao() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setSubtotal(new BigDecimal("100.00"));
    comanda.setTotal(new BigDecimal("100.00"));
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "100.00", null));
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_PIX_ASAAS, "100.00", ComandaPagamento.STATUS_PENDENTE));

    assertThatThrownBy(() -> service.fechar(comandaId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Comanda nao esta quitada: pago 0, total com gorjeta 100.00.");
  }

  @Test
  void fecharRateiaAReceitaDoItemEntreOsMeiosDePagamentoReais() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setSubtotal(new BigDecimal("100.00"));
    comanda.setTotal(new BigDecimal("100.00"));
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "100.00", null));
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "40.00", ComandaPagamento.STATUS_CONFIRMADO),
        pagamento(
            ComandaPagamento.MEIO_CARTAO_CREDITO_EXTERNO,
            "60.00",
            ComandaPagamento.STATUS_CONFIRMADO));

    service.fechar(comandaId);

    List<Transacao> transacoes = transacoesSalvas();
    assertThat(transacoes).hasSize(2);
    assertThat(transacoes.get(0).getPaymentMethod()).isEqualTo(MetodoPagamento.CASH);
    assertThat(transacoes.get(0).getAmount()).isEqualByComparingTo("40.00");
    assertThat(transacoes.get(1).getPaymentMethod()).isEqualTo(MetodoPagamento.CREDIT_CARD);
    assertThat(transacoes.get(1).getAmount()).isEqualByComparingTo("60.00");
    assertThat(transacoes).allSatisfy(t -> {
      assertThat(t.getType()).isEqualTo(TipoTransacao.INCOME);
      assertThat(t.getComandaId()).isEqualTo(comandaId);
      assertThat(t.getDescription()).isEqualTo("Venda comanda - Corte");
    });
    assertThat(comanda.getStatus()).isEqualTo(Comanda.STATUS_FECHADA);
    assertThat(comanda.getFechadaPor()).isEqualTo(usuarioId);
  }

  @Test
  void fecharLancaGorjetaComoParDeReceitaEDespesa() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setSubtotal(new BigDecimal("100.00"));
    comanda.setTotal(new BigDecimal("100.00"));
    comanda.setGorjeta(new BigDecimal("15.00"));
    comanda.setGorjetaProfessionalId(professionalId);
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "100.00", null));
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "115.00", ComandaPagamento.STATUS_CONFIRMADO));

    service.fechar(comandaId);

    List<Transacao> gorjetas =
        transacoesSalvas().stream()
            .filter(t -> t.getProfessionalId() != null && t.getProfessionalId().equals(professionalId))
            .toList();
    assertThat(gorjetas).hasSize(2);
    // Efeito liquido zero na receita do salao, mas com rastro auditavel de quanto e para quem.
    assertThat(gorjetas.get(0).getType()).isEqualTo(TipoTransacao.INCOME);
    assertThat(gorjetas.get(0).getDescription()).isEqualTo("Gorjeta recebida - comanda");
    assertThat(gorjetas.get(1).getType()).isEqualTo(TipoTransacao.EXPENSE);
    assertThat(gorjetas.get(1).getDescription()).isEqualTo("Repasse de gorjeta ao profissional");
    assertThat(gorjetas).allSatisfy(t -> assertThat(t.getAmount()).isEqualByComparingTo("15.00"));
  }

  @Test
  void fecharRegistraComissaoDeServicoEConsumoDeInsumoEmComandaAvulsa() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setSubtotal(new BigDecimal("100.00"));
    comanda.setTotal(new BigDecimal("90.00"));
    comanda.setDesconto(new BigDecimal("10.00"));
    ComandaItem item = item(ComandaItem.TIPO_SERVICO, "100.00", professionalId);
    itensDaComanda(item);
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "90.00", ComandaPagamento.STATUS_CONFIRMADO));

    service.fechar(comandaId);

    verify(estoqueMovimentacaoService)
        .consumirInsumosPorItemComanda(eq(tenantId), eq(item.getId()), eq(serviceId));
    // A comissao usa o bruto do item e o liquido apos rateio do desconto.
    verify(commissionService)
        .registerServiceCommissionForComandaItemIfApplicable(
            eq(tenantId),
            eq(comandaId),
            eq(item.getId()),
            eq(professionalId),
            eq(serviceId),
            eq(new BigDecimal("100.00")),
            eq(new BigDecimal("90.00")),
            any());
  }

  @Test
  void fecharBaixaEstoqueERegistraComissaoDeProduto() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setSubtotal(new BigDecimal("50.00"));
    comanda.setTotal(new BigDecimal("50.00"));
    ComandaItem item = item(ComandaItem.TIPO_PRODUTO, "50.00", professionalId);
    itensDaComanda(item);
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "50.00", ComandaPagamento.STATUS_CONFIRMADO));

    service.fechar(comandaId);

    verify(estoqueMovimentacaoService)
        .criarMovimentacao(
            eq(serviceId), eq("SAIDA"), eq(BigDecimal.ONE), eq("Venda em comanda"));
    verify(commissionService)
        .registerProductCommissionIfApplicable(
            eq(tenantId), any(), eq(professionalId), eq(serviceId), isNull(), eq(5000L), any(),
            eq("Venda comanda - Corte"));
    assertThat(transacoesSalvas().get(0).getStockItemId()).isEqualTo(serviceId);
  }

  @Test
  void fecharCreditaPontosDeFidelidadeArredondandoParaBaixo() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setClientId(clientId);
    comanda.setSubtotal(new BigDecimal("99.00"));
    comanda.setTotal(new BigDecimal("99.00"));
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "99.00", null));
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "99.00", ComandaPagamento.STATUS_CONFIRMADO));

    TenantLoyaltySettings config = new TenantLoyaltySettings();
    config.setTenantId(tenantId);
    config.setAtivo(true);
    config.setPontosPorReal(new BigDecimal("0.5"));
    when(tenantLoyaltySettingsRepository.findByTenantId(eq(tenantId)))
        .thenReturn(Optional.of(config));

    Cliente cliente = new Cliente();
    cliente.setId(clientId);
    cliente.setTenantId(tenantId);
    cliente.setLoyaltyPoints(10);
    when(clienteRepository.findByIdAndTenantId(eq(clientId), eq(tenantId))).thenReturn(Optional.of(cliente));

    service.fechar(comandaId);

    // 99.00 * 0.5 = 49.5 -> FLOOR -> 49
    assertThat(comanda.getPontosFidelidadeCreditados()).isEqualTo(49);
    assertThat(cliente.getLoyaltyPoints()).isEqualTo(59);
  }

  @Test
  void fecharNaoContaProdutoNaFidelidadeQuandoAConfiguracaoNaoPermite() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setClientId(clientId);
    comanda.setSubtotal(new BigDecimal("100.00"));
    comanda.setTotal(new BigDecimal("100.00"));
    itensDaComanda(item(ComandaItem.TIPO_PRODUTO, "100.00", null));
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "100.00", ComandaPagamento.STATUS_CONFIRMADO));

    TenantLoyaltySettings config = new TenantLoyaltySettings();
    config.setTenantId(tenantId);
    config.setAtivo(true);
    config.setPontosPorReal(BigDecimal.ONE);
    config.setProdutosContam(false);
    when(tenantLoyaltySettingsRepository.findByTenantId(eq(tenantId)))
        .thenReturn(Optional.of(config));

    service.fechar(comandaId);

    assertThat(comanda.getPontosFidelidadeCreditados()).isZero();
  }

  @Test
  void fecharCriaCompraDePacoteComSaldoMultiplicadoPelaQuantidade() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setClientId(clientId);
    comanda.setSubtotal(new BigDecimal("500.00"));
    comanda.setTotal(new BigDecimal("500.00"));
    ComandaItem item = item(ComandaItem.TIPO_PACOTE, "500.00", null);
    item.setQuantidade(new BigDecimal("2"));
    item.setDescricao("Combo Corte");
    itensDaComanda(item);
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "500.00", ComandaPagamento.STATUS_CONFIRMADO));

    br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackageItem pacoteItem =
        new br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackageItem();
    pacoteItem.setServiceId(serviceId);
    pacoteItem.setSessoes(5);
    when(servicePackageItemRepository.findByPackageId(eq(serviceId)))
        .thenReturn(List.of(pacoteItem));
    Servico servico = new Servico();
    servico.setId(serviceId);
    servico.setName("Corte");
    when(servicoRepository.findById(eq(serviceId))).thenReturn(Optional.of(servico));

    service.fechar(comandaId);

    ArgumentCaptor<ClientPackagePurchase> compraCaptor =
        ArgumentCaptor.forClass(ClientPackagePurchase.class);
    verify(clientPackagePurchaseRepository).save(compraCaptor.capture());
    assertThat(compraCaptor.getValue().getClientId()).isEqualTo(clientId);
    assertThat(compraCaptor.getValue().getPackageNome()).isEqualTo("Combo Corte");
    assertThat(compraCaptor.getValue().getPrecoPago()).isEqualByComparingTo("500.00");

    ArgumentCaptor<ClientPackageBalance> saldoCaptor =
        ArgumentCaptor.forClass(ClientPackageBalance.class);
    verify(clientPackageBalanceRepository).save(saldoCaptor.capture());
    assertThat(saldoCaptor.getValue().getSessoesTotais()).isEqualTo(10);
    assertThat(saldoCaptor.getValue().getServiceNome()).isEqualTo("Corte");
  }

  // ---------------------------------------------------------------- pagamentos

  @Test
  void registrarPagamentoDinheiroJaNasceConfirmadoEComDataDePagamento() {
    comanda(Comanda.STATUS_ABERTA);

    ComandaDtos.RegistrarPagamentoRequest req = new ComandaDtos.RegistrarPagamentoRequest();
    req.meio = ComandaPagamento.MEIO_DINHEIRO;
    req.valor = new BigDecimal("30.00");

    service.registrarPagamento(comandaId, req);

    ArgumentCaptor<ComandaPagamento> captor = ArgumentCaptor.forClass(ComandaPagamento.class);
    verify(comandaPagamentoRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ComandaPagamento.STATUS_CONFIRMADO);
    assertThat(captor.getValue().getPaidAt()).isNotNull();
    assertThat(captor.getValue().getRegistradoPor()).isEqualTo(usuarioId);
  }

  @Test
  void registrarPagamentoCreditoSinalConsomeODepositoEExigeVinculoComAgendamento() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    UUID appointmentId = UUID.randomUUID();
    comanda.setAppointmentId(appointmentId);

    AppointmentDeposit deposit = new AppointmentDeposit();
    deposit.setId(UUID.randomUUID());
    deposit.setTenantId(tenantId);
    deposit.setAppointmentId(appointmentId);
    deposit.setStatus(AppointmentDeposit.STATUS_PAID);
    deposit.setAmountCents(5000L);
    when(appointmentDepositRepository.findPaidUnusedByAppointmentId(eq(appointmentId)))
        .thenReturn(Optional.of(deposit));

    ComandaDtos.RegistrarPagamentoRequest req = new ComandaDtos.RegistrarPagamentoRequest();
    req.meio = ComandaPagamento.MEIO_CREDITO_SINAL;
    req.valor = new BigDecimal("50.00");

    service.registrarPagamento(comandaId, req);

    assertThat(deposit.getUsedInComandaId()).isEqualTo(comandaId);
    ArgumentCaptor<ComandaPagamento> captor = ArgumentCaptor.forClass(ComandaPagamento.class);
    verify(comandaPagamentoRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ComandaPagamento.STATUS_CONFIRMADO);
    assertThat(captor.getValue().getAppointmentDepositId()).isEqualTo(deposit.getId());
  }

  @Test
  void registrarPagamentoCreditoSinalAcimaDoSinalPagoFalha() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    UUID appointmentId = UUID.randomUUID();
    comanda.setAppointmentId(appointmentId);

    AppointmentDeposit deposit = new AppointmentDeposit();
    deposit.setId(UUID.randomUUID());
    deposit.setAppointmentId(appointmentId);
    deposit.setTenantId(tenantId);
    deposit.setStatus(AppointmentDeposit.STATUS_PAID);
    deposit.setAmountCents(3000L);
    when(appointmentDepositRepository.findPaidUnusedByAppointmentId(eq(appointmentId)))
        .thenReturn(Optional.of(deposit));

    ComandaDtos.RegistrarPagamentoRequest req = new ComandaDtos.RegistrarPagamentoRequest();
    req.meio = ComandaPagamento.MEIO_CREDITO_SINAL;
    req.valor = new BigDecimal("50.00");

    assertThatThrownBy(() -> service.registrarPagamento(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Valor informado excede o sinal pago (30.00).");

    assertThat(deposit.getUsedInComandaId()).isNull();
  }

  @Test
  void registrarPagamentoPixSemClienteIdentificadoFalha() {
    comanda(Comanda.STATUS_ABERTA);

    ComandaDtos.RegistrarPagamentoRequest req = new ComandaDtos.RegistrarPagamentoRequest();
    req.meio = ComandaPagamento.MEIO_PIX_ASAAS;
    req.valor = new BigDecimal("30.00");

    assertThatThrownBy(() -> service.registrarPagamento(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Comanda sem cliente identificado: obrigatorio para pagamento via Pix.");
  }

  // ---------------------------------------------------------------- cancelamento

  @Test
  void cancelarLiberaODepositoEEncerraACobrancaPixPendente() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    ComandaPagamento pix =
        pagamento(ComandaPagamento.MEIO_PIX_ASAAS, "50.00", ComandaPagamento.STATUS_PENDENTE);
    pix.setAsaasPaymentId("pay_1");
    ComandaPagamento sinal =
        pagamento(
            ComandaPagamento.MEIO_CREDITO_SINAL, "30.00", ComandaPagamento.STATUS_CONFIRMADO);
    UUID depositId = UUID.randomUUID();
    sinal.setAppointmentDepositId(depositId);
    pagamentosDaComanda(pix, sinal);

    AppointmentDeposit deposit = new AppointmentDeposit();
    deposit.setId(depositId);
    deposit.setUsedInComandaId(comandaId);
    when(appointmentDepositRepository.findById(eq(depositId))).thenReturn(Optional.of(deposit));
    when(tenantAsaasChargeService.resolveApiKeyAtivaOuFalhar(eq(tenantId))).thenReturn("key");

    ComandaDtos.CancelarComandaRequest req = new ComandaDtos.CancelarComandaRequest();
    req.motivo = "  cliente desistiu  ";

    service.cancelar(comandaId, req);

    verify(asaasClient).cancelPayment(eq("key"), eq("pay_1"));
    assertThat(deposit.getUsedInComandaId()).isNull();
    assertThat(comanda.getStatus()).isEqualTo(Comanda.STATUS_CANCELADA);
    assertThat(comanda.getCancelMotivo()).isEqualTo("cliente desistiu");
    assertThat(comanda.getClosedAt()).isNotNull();
  }

  // ---------------------------------------------------------------- estorno

  @Test
  void estornarSoAceitaComandaFechada() {
    comanda(Comanda.STATUS_ABERTA);

    ComandaDtos.EstornarComandaRequest req = new ComandaDtos.EstornarComandaRequest();
    req.motivo = "erro de caixa";

    assertThatThrownBy(() -> service.estornar(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Comanda nao esta fechada.");
  }

  @Test
  void estornarFazSoftDeleteDasTransacoesEReverteComissoesEFidelidade() {
    Comanda comanda = comanda(Comanda.STATUS_FECHADA);
    comanda.setClientId(clientId);
    comanda.setPontosFidelidadeCreditados(40);
    ComandaItem servicoItem = item(ComandaItem.TIPO_SERVICO, "100.00", professionalId);
    ComandaItem produtoItem = item(ComandaItem.TIPO_PRODUTO, "50.00", professionalId);
    itensDaComanda(servicoItem, produtoItem);

    Transacao transacao = new Transacao();
    transacao.setId(UUID.randomUUID());
    transacao.setTenantId(tenantId);
    transacao.setComandaId(comandaId);
    when(transacaoRepository.listarAtivasPorComanda(eq(tenantId), eq(comandaId)))
        .thenReturn(List.of(transacao));

    Cliente cliente = new Cliente();
    cliente.setId(clientId);
    cliente.setLoyaltyPoints(25);
    when(clienteRepository.findByIdAndTenantId(eq(clientId), eq(tenantId)))
        .thenReturn(Optional.of(cliente));

    ComandaDtos.EstornarComandaRequest req = new ComandaDtos.EstornarComandaRequest();
    req.motivo = "  duplicidade  ";

    service.estornar(comandaId, req);

    // A Transacao original nunca some — vira soft delete para preservar auditoria.
    assertThat(transacao.getDeletedAt()).isNotNull();
    assertThat(transacao.getDeletedBy()).isEqualTo(usuarioId);
    verify(commissionService)
        .reverseEntryForOrigin(eq(tenantId), eq("PRODUCT"), eq(transacao.getId()), eq("duplicidade"));
    verify(commissionService)
        .reverseEntryForOrigin(
            eq(tenantId), eq("SERVICE"), eq(servicoItem.getId()), eq("duplicidade"));
    verify(estoqueMovimentacaoService)
        .criarMovimentacao(
            eq(produtoItem.getReferenciaId()),
            eq("ENTRADA"),
            eq(BigDecimal.ONE),
            eq("Estorno de comanda: duplicidade"));

    // Saldo de pontos nunca fica negativo: creditados 40, saldo atual 25 -> 0.
    assertThat(cliente.getLoyaltyPoints()).isZero();
    assertThat(comanda.getStatus()).isEqualTo(Comanda.STATUS_ESTORNADA);
    assertThat(comanda.getEstornoMotivo()).isEqualTo("duplicidade");
    assertThat(comanda.getEstornadoPor()).isEqualTo(usuarioId);
  }

  /**
   * O SINAL volta a valer no estorno.
   *
   * O `cancelar` ja soltava o deposito; o estorno nao — e o cliente que pagou sinal e teve a
   * comanda estornada ficava sem a venda E sem o credito, com o deposito preso numa comanda que nao
   * existe mais (achado do roteiro de ponta a ponta de 2026-09-16).
   */
  @Test
  void estornarSoltaOSinalUsadoComoCredito() {
    Comanda comanda = comanda(Comanda.STATUS_FECHADA);
    when(transacaoRepository.listarAtivasPorComanda(any(), any())).thenReturn(List.of());

    AppointmentDeposit deposito = new AppointmentDeposit();
    deposito.setId(UUID.randomUUID());
    deposito.setTenantId(tenantId);
    deposito.setUsedInComandaId(comandaId);
    when(appointmentDepositRepository.findById(eq(deposito.getId())))
        .thenReturn(Optional.of(deposito));

    ComandaPagamento comSinal = new ComandaPagamento();
    comSinal.setId(UUID.randomUUID());
    comSinal.setComandaId(comandaId);
    comSinal.setMeio(ComandaPagamento.MEIO_CREDITO_SINAL);
    comSinal.setValor(new BigDecimal("50.00"));
    comSinal.setAppointmentDepositId(deposito.getId());
    when(comandaPagamentoRepository.findByComandaIdOrderByCreatedAt(eq(comandaId)))
        .thenReturn(List.of(comSinal));

    ComandaDtos.EstornarComandaRequest req = new ComandaDtos.EstornarComandaRequest();
    req.motivo = "cobranca duplicada";

    service.estornar(comandaId, req);

    assertThat(deposito.getUsedInComandaId()).isNull();
    assertThat(comanda.getStatus()).isEqualTo(Comanda.STATUS_ESTORNADA);
  }

  @Test
  void estornarRemoveCompraDePacoteESeusSaldos() {
    comanda(Comanda.STATUS_FECHADA);
    when(transacaoRepository.listarAtivasPorComanda(any(), any())).thenReturn(List.of());

    ClientPackagePurchase compra = new ClientPackagePurchase();
    compra.setId(UUID.randomUUID());
    when(clientPackagePurchaseRepository.findByTenantIdAndComandaId(eq(tenantId), eq(comandaId)))
        .thenReturn(new ArrayList<>(List.of(compra)));
    ClientPackageBalance saldo = new ClientPackageBalance();
    saldo.setId(UUID.randomUUID());
    when(clientPackageBalanceRepository.findByPurchaseId(eq(compra.getId())))
        .thenReturn(List.of(saldo));

    ComandaDtos.EstornarComandaRequest req = new ComandaDtos.EstornarComandaRequest();
    req.motivo = "erro";

    service.estornar(comandaId, req);

    verify(clientPackageBalanceRepository).delete(eq(saldo));
    verify(clientPackagePurchaseRepository).delete(eq(compra));
  }

  // ---------------------------------------------------------------- fidelidade

  @Test
  void resgatarFidelidadeConverteOsPontosEmDescontoEDebitaOSaldo() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setClientId(clientId);
    comanda.setDescontoMotivo("cortesia");
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "200.00", null));

    TenantLoyaltySettings config = new TenantLoyaltySettings();
    config.setAtivo(true);
    config.setPontosPorResgateReal(new BigDecimal("100"));
    when(tenantLoyaltySettingsRepository.findByTenantId(eq(tenantId)))
        .thenReturn(Optional.of(config));

    Cliente cliente = new Cliente();
    cliente.setId(clientId);
    cliente.setLoyaltyPoints(5000);
    when(clienteRepository.findByIdAndTenantId(eq(clientId), eq(tenantId)))
        .thenReturn(Optional.of(cliente));

    ComandaDtos.ResgatarFidelidadeRequest req = new ComandaDtos.ResgatarFidelidadeRequest();
    req.pontos = 3000;

    service.resgatarFidelidade(comandaId, req);

    // 3000 pontos / 100 pontos-por-real = R$ 30,00 de desconto.
    assertThat(comanda.getDesconto()).isEqualByComparingTo("30.00");
    assertThat(comanda.getTotal()).isEqualByComparingTo("170.00");
    assertThat(comanda.getDescontoMotivo())
        .isEqualTo("cortesia; Resgate de 3000 pontos de fidelidade");
    assertThat(cliente.getLoyaltyPoints()).isEqualTo(2000);
  }

  @Test
  void resgatarFidelidadeNuncaDeixaODescontoUltrapassarOSubtotal() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setClientId(clientId);
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "20.00", null));

    TenantLoyaltySettings config = new TenantLoyaltySettings();
    config.setAtivo(true);
    config.setPontosPorResgateReal(new BigDecimal("100"));
    when(tenantLoyaltySettingsRepository.findByTenantId(eq(tenantId)))
        .thenReturn(Optional.of(config));

    Cliente cliente = new Cliente();
    cliente.setId(clientId);
    cliente.setLoyaltyPoints(100000);
    when(clienteRepository.findByIdAndTenantId(eq(clientId), eq(tenantId)))
        .thenReturn(Optional.of(cliente));

    ComandaDtos.ResgatarFidelidadeRequest req = new ComandaDtos.ResgatarFidelidadeRequest();
    req.pontos = 50000;

    service.resgatarFidelidade(comandaId, req);

    assertThat(comanda.getDesconto()).isEqualByComparingTo("20.00");
    assertThat(comanda.getTotal()).isEqualByComparingTo("0.00");
    // O saldo e debitado integralmente mesmo com o desconto capado — comportamento do original.
    assertThat(cliente.getLoyaltyPoints()).isEqualTo(50000);
  }

  @Test
  void resgatarFidelidadeComSaldoInsuficienteFalha() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setClientId(clientId);

    TenantLoyaltySettings config = new TenantLoyaltySettings();
    config.setAtivo(true);
    when(tenantLoyaltySettingsRepository.findByTenantId(eq(tenantId)))
        .thenReturn(Optional.of(config));

    Cliente cliente = new Cliente();
    cliente.setId(clientId);
    cliente.setLoyaltyPoints(10);
    when(clienteRepository.findByIdAndTenantId(eq(clientId), eq(tenantId)))
        .thenReturn(Optional.of(cliente));

    ComandaDtos.ResgatarFidelidadeRequest req = new ComandaDtos.ResgatarFidelidadeRequest();
    req.pontos = 500;

    assertThatThrownBy(() -> service.resgatarFidelidade(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cliente nao possui pontos suficientes (saldo: 10).");
  }

  @Test
  void resgatarFidelidadeComProgramaInativoFalha() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setClientId(clientId);
    when(tenantLoyaltySettingsRepository.findByTenantId(eq(tenantId)))
        .thenReturn(Optional.empty());

    ComandaDtos.ResgatarFidelidadeRequest req = new ComandaDtos.ResgatarFidelidadeRequest();
    req.pontos = 100;

    assertThatThrownBy(() -> service.resgatarFidelidade(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Programa de fidelidade nao esta ativo.");
  }

  // ─── Preco de tabela e trilha de auditoria (achados do E2E de 2026-09-16) ───

  /**
   * O preco do servico e o do CATALOGO: aceitar o do pedido permitia lancar um servico de R$ 70 por
   * R$ 1 — sem aparecer como desconto em relatorio nenhum.
   */
  @Test
  void precoDeServicoIgnoraOQueVeioNoPedido() {
    comanda(Comanda.STATUS_ABERTA);
    servicoDeTabela();

    ComandaDtos.AdicionarItemRequest req = new ComandaDtos.AdicionarItemRequest();
    req.tipo = ComandaItem.TIPO_SERVICO;
    req.referenciaId = serviceId.toString();
    req.precoUnitario = new BigDecimal("1.00");

    service.adicionarItem(comandaId, req);

    ArgumentCaptor<ComandaItem> captor = ArgumentCaptor.forClass(ComandaItem.class);
    verify(comandaItemRepository).save(captor.capture());
    assertThat(captor.getValue().getPrecoUnitario()).isEqualByComparingTo("70.00");
  }

  /** No fluxo interno do agendamento o preco do pedido VALE: e o acordado com o cliente. */
  @Test
  void precoDoAgendamentoContinuaValendoNoFluxoInterno() {
    comanda(Comanda.STATUS_ABERTA);
    servicoDeTabela();

    ComandaDtos.AdicionarItemRequest req = new ComandaDtos.AdicionarItemRequest();
    req.tipo = ComandaItem.TIPO_SERVICO;
    req.referenciaId = serviceId.toString();
    req.precoUnitario = new BigDecimal("55.00");

    service.adicionarItemDoAgendamento(comandaId, req);

    ArgumentCaptor<ComandaItem> captor = ArgumentCaptor.forClass(ComandaItem.class);
    verify(comandaItemRepository).save(captor.capture());
    assertThat(captor.getValue().getPrecoUnitario()).isEqualByComparingTo("55.00");
  }

  /** Produto por R$ 0,00 seria brinde sem registro: sai do estoque e nao entra no caixa. */
  @Test
  void produtoComPrecoZeroEhRecusado() {
    comanda(Comanda.STATUS_ABERTA);
    ItemEstoque produto = new ItemEstoque();
    produto.setId(serviceId);
    produto.setTenantId(tenantId);
    produto.setNome("Shampoo");
    when(itemEstoqueRepository.findByIdAndTenantId(eq(serviceId), eq(tenantId)))
        .thenReturn(Optional.of(produto));

    ComandaDtos.AdicionarItemRequest req = new ComandaDtos.AdicionarItemRequest();
    req.tipo = ComandaItem.TIPO_PRODUTO;
    req.referenciaId = serviceId.toString();
    req.precoUnitario = BigDecimal.ZERO;

    assertThatThrownBy(() -> service.adicionarItem(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Preco de venda do produto precisa ser maior que zero.");
    verify(comandaItemRepository, never()).save(any());
  }

  /**
   * O PDV era o unico lugar do sistema onde dinheiro mudava de mao sem trilha. Desconto e o caso
   * mais sensivel: e por ele que se zera uma conta.
   */
  @Test
  void descontoDeixaTrilhaDeQuemDeu() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setSubtotal(new BigDecimal("100.00"));

    ComandaDtos.AplicarDescontoRequest req = new ComandaDtos.AplicarDescontoRequest();
    req.percentual = new BigDecimal("100");
    req.motivo = "cortesia";

    service.aplicarDesconto(comandaId, req);

    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    AuditEventCommand evento = captor.getValue();
    assertThat(evento.action).isEqualTo("POS_DISCOUNT_APPLY");
    assertThat(evento.entityType).isEqualTo("COMANDA");
    assertThat(evento.entityId).isEqualTo(comandaId.toString());
  }

  @Test
  void pagamentoDeixaTrilha() {
    comanda(Comanda.STATUS_ABERTA);

    ComandaDtos.RegistrarPagamentoRequest req = new ComandaDtos.RegistrarPagamentoRequest();
    req.meio = ComandaPagamento.MEIO_DINHEIRO;
    req.valor = new BigDecimal("50.00");

    service.registrarPagamento(comandaId, req);

    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    assertThat(captor.getValue().action).isEqualTo("POS_PAYMENT_ADD");
  }

  private void servicoDeTabela() {
    Servico servico = new Servico();
    servico.setId(serviceId);
    servico.setTenantId(tenantId);
    servico.setName("Corte");
    servico.setPrice(new BigDecimal("70.00"));
    when(servicoRepository.findByIdAndTenantId(eq(serviceId), eq(tenantId)))
        .thenReturn(Optional.of(servico));
  }

  // ─── Teto de desconto configuravel (V131) ──────────────────────────────────

  /** Com teto de 20%, a equipe nao passa disso — quem passa e o dono. */
  @Test
  void descontoAcimaDoTetoEhRecusado() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setSubtotal(new BigDecimal("500.00"));
    configuracoes.setPosMaxDiscountPercent(20);

    ComandaDtos.AplicarDescontoRequest req = new ComandaDtos.AplicarDescontoRequest();
    req.percentual = new BigDecimal("100");
    req.motivo = "cortesia";

    assertThatThrownBy(() -> service.aplicarDesconto(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("O desconto maximo sem o dono e de 20%. Chame o dono para dar mais que isso.");
    assertThat(aberta.getDesconto()).isEqualByComparingTo("0");
  }

  @Test
  void descontoDentroDoTetoPassa() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setSubtotal(new BigDecimal("500.00"));
    configuracoes.setPosMaxDiscountPercent(20);
    when(comandaItemRepository.findByComandaIdOrderByCreatedAt(eq(comandaId)))
        .thenReturn(List.of(item(ComandaItem.TIPO_SERVICO, "500.00", professionalId)));

    ComandaDtos.AplicarDescontoRequest req = new ComandaDtos.AplicarDescontoRequest();
    req.percentual = new BigDecimal("20");
    req.motivo = "cliente antigo";

    service.aplicarDesconto(comandaId, req);

    assertThat(aberta.getDesconto()).isEqualByComparingTo("100.00");
  }

  /** O dinheiro e do dono: o teto e para a equipe, nao para ele. */
  @Test
  void donoNaoTemTetoDeDesconto() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setSubtotal(new BigDecimal("500.00"));
    configuracoes.setPosMaxDiscountPercent(10);
    when(comandaItemRepository.findByComandaIdOrderByCreatedAt(eq(comandaId)))
        .thenReturn(List.of(item(ComandaItem.TIPO_SERVICO, "500.00", professionalId)));
    when(authenticatedUser.temRole("OWNER")).thenReturn(true);

    ComandaDtos.AplicarDescontoRequest req = new ComandaDtos.AplicarDescontoRequest();
    req.percentual = new BigDecimal("100");
    req.motivo = "cortesia da casa";

    service.aplicarDesconto(comandaId, req);

    assertThat(aberta.getDesconto()).isEqualByComparingTo("500.00");
  }

  // ─── Origem do item e duplicidade (V132) ───────────────────────────────────

  @Test
  void itemLancadoNaTelaNasceManualEODoAgendamentoNasceComOrigem() {
    comanda(Comanda.STATUS_ABERTA);
    servicoDeTabela();

    service.adicionarItem(comandaId, itemDeServico());
    ArgumentCaptor<ComandaItem> captor = ArgumentCaptor.forClass(ComandaItem.class);
    verify(comandaItemRepository).save(captor.capture());
    assertThat(captor.getValue().getOrigem()).isEqualTo(ComandaItem.ORIGEM_MANUAL);

    service.adicionarItemDoAgendamento(comandaId, itemDeServico());
    verify(comandaItemRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getValue().getOrigem()).isEqualTo(ComandaItem.ORIGEM_AGENDAMENTO);
  }

  /** Lancar de novo o servico que o agendamento trouxe cobrava o cliente duas vezes, calado. */
  @Test
  void itemRepetidoPedeConfirmacao() {
    comanda(Comanda.STATUS_ABERTA);
    servicoDeTabela();
    ComandaItem jaLancado = item(ComandaItem.TIPO_SERVICO, "70.00", professionalId);
    jaLancado.setOrigem(ComandaItem.ORIGEM_AGENDAMENTO);
    when(comandaItemRepository.findByComandaIdOrderByCreatedAt(eq(comandaId)))
        .thenReturn(List.of(jaLancado));

    assertThatThrownBy(() -> service.adicionarItem(comandaId, itemDeServico()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ja tem")
        .hasMessageContaining("veio do agendamento");
    verify(comandaItemRepository, never()).save(any());
  }

  /** Dois cortes na mesma conta existem: confirmado, passa. */
  @Test
  void itemRepetidoConfirmadoPassa() {
    comanda(Comanda.STATUS_ABERTA);
    servicoDeTabela();
    when(comandaItemRepository.findByComandaIdOrderByCreatedAt(eq(comandaId)))
        .thenReturn(List.of(item(ComandaItem.TIPO_SERVICO, "70.00", professionalId)));

    ComandaDtos.AdicionarItemRequest req = itemDeServico();
    req.confirmarDuplicado = true;
    service.adicionarItem(comandaId, req);

    verify(comandaItemRepository).save(any(ComandaItem.class));
  }

  // ─── Profissional so na propria comanda (2026-09-16) ───────────────────────

  @Test
  void profissionalNaoEnxergaComandaDeOutro() {
    Comanda deOutro = comanda(Comanda.STATUS_ABERTA);
    deOutro.setAbertaPor(UUID.randomUUID());
    souProfissional();

    assertThatThrownBy(() -> service.obter(comandaId))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("nao encontrada");
  }

  @Test
  void profissionalEnxergaAComandaQueEleAbriu() {
    Comanda minha = comanda(Comanda.STATUS_ABERTA);
    minha.setAbertaPor(usuarioId);
    souProfissional();

    assertThat(service.obter(comandaId).id).isEqualTo(comandaId.toString());
  }

  @Test
  void profissionalEnxergaAComandaComItemDele() {
    Comanda comItemMeu = comanda(Comanda.STATUS_ABERTA);
    comItemMeu.setAbertaPor(UUID.randomUUID());
    UUID meuId = souProfissional();
    when(comandaItemRepository.findByComandaIdOrderByCreatedAt(eq(comandaId)))
        .thenReturn(List.of(item(ComandaItem.TIPO_SERVICO, "70.00", meuId)));

    assertThat(service.obter(comandaId).id).isEqualTo(comandaId.toString());
  }

  /** A recepcao (STAFF) ve o salao inteiro: e o trabalho dela. */
  @Test
  void recepcaoEnxergaComandaDeQualquerUm() {
    Comanda deOutro = comanda(Comanda.STATUS_ABERTA);
    deOutro.setAbertaPor(UUID.randomUUID());
    souProfissional();
    when(authenticatedUser.temRole("STAFF")).thenReturn(true);

    assertThat(service.obter(comandaId).id).isEqualTo(comandaId.toString());
  }

  /** Marca o usuario logado como profissional do salao e devolve o id do cadastro dele. */
  private UUID souProfissional() {
    UUID profissionalDoLogin = UUID.randomUUID();
    when(authenticatedUser.temRole("PROFESSIONAL")).thenReturn(true);
    Profissional eu = new Profissional();
    eu.setId(profissionalDoLogin);
    eu.setTenantId(tenantId);
    when(profissionalRepository.findByTenantIdAndUserId(eq(tenantId), eq(usuarioId)))
        .thenReturn(Optional.of(eu));
    return profissionalDoLogin;
  }

  private ComandaDtos.AdicionarItemRequest itemDeServico() {
    ComandaDtos.AdicionarItemRequest req = new ComandaDtos.AdicionarItemRequest();
    req.tipo = ComandaItem.TIPO_SERVICO;
    req.referenciaId = serviceId.toString();
    return req;
  }

  // ─── Dia com caixa fechado (2026-09-16) ────────────────────────────────────

  /**
   * Fecha certinho hoje, estorna amanha e leva o dinheiro: o estorno apagaria a receita de um dia
   * cujo caixa ja foi contado e assinado.
   */
  @Test
  void estornoDeVendaDeDiaComCaixaFechadoNaoAcontece() {
    Comanda fechada = comanda(Comanda.STATUS_FECHADA);
    fechada.setClosedAt(Instant.now().minusSeconds(86_400));
    Transacao receita = new Transacao();
    receita.setId(UUID.randomUUID());
    when(transacaoRepository.listarAtivasPorComanda(any(), any())).thenReturn(List.of(receita));
    org.mockito.Mockito.doThrow(new IllegalArgumentException("O caixa de ontem ja foi fechado"))
        .when(travaFinanceira)
        .exigirDiaAberto(
            eq(tenantId), eq(fechada.getClosedAt()), eq("POS_COMANDA_REVERSE"), eq("COMANDA"),
            eq(comandaId.toString()), any());

    ComandaDtos.EstornarComandaRequest req = new ComandaDtos.EstornarComandaRequest();
    req.motivo = "cliente desistiu";

    assertThatThrownBy(() -> service.estornar(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ja foi fechado");
    assertThat(fechada.getStatus()).isEqualTo(Comanda.STATUS_FECHADA);
    assertThat(receita.getDeletedAt()).isNull();
  }

  /** Venda fechada depois do caixa de hoje contado ficaria fora da conferencia. */
  @Test
  void fecharComandaConfereQueOCaixaDeHojeEstaAberto() {
    comanda(Comanda.STATUS_ABERTA);
    when(comandaItemRepository.findByComandaIdOrderByCreatedAt(eq(comandaId)))
        .thenReturn(List.of(item(ComandaItem.TIPO_SERVICO, "70.00", professionalId)));
    org.mockito.Mockito.doThrow(new IllegalArgumentException("O caixa de hoje ja foi fechado"))
        .when(travaFinanceira)
        .exigirDiaAberto(eq(tenantId), any(), eq("POS_COMANDA_CLOSE"), eq("COMANDA"), any(), any());

    assertThatThrownBy(() -> service.fechar(comandaId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ja foi fechado");
    verify(transacaoRepository, never()).save(any());
  }

  @Test
  void descontoAcimaDoTetoFicaNaTrilhaComoTentativa() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setSubtotal(new BigDecimal("500.00"));
    configuracoes.setPosMaxDiscountPercent(20);

    ComandaDtos.AplicarDescontoRequest req = new ComandaDtos.AplicarDescontoRequest();
    req.percentual = new BigDecimal("100");
    req.motivo = "cortesia";

    assertThatThrownBy(() -> service.aplicarDesconto(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class);
    verify(travaFinanceira)
        .registrarTentativaBloqueada(
            eq(tenantId), eq("POS_DISCOUNT_APPLY"), eq("COMANDA"), any(), any(), any());
  }

  // ─── A1: comissao pelo que a comanda cobrou (decisao de 2026-09-17) ───────

  /**
   * Comanda de ATENDIMENTO agora gera a comissao do servico ao fechar, sobre o valor liquido: o
   * desconto do PDV reduz a base, e o servico extra tambem entra.
   */
  @Test
  void comandaDeAtendimentoGeraComissaoPeloValorCobrado() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setAppointmentId(UUID.randomUUID());
    aberta.setSubtotal(new BigDecimal("150.00"));
    aberta.setDesconto(new BigDecimal("30.00"));
    aberta.setTotal(new BigDecimal("120.00"));
    ComandaItem doAgendamento = item(ComandaItem.TIPO_SERVICO, "100.00", professionalId);
    ComandaItem extra = item(ComandaItem.TIPO_SERVICO, "50.00", professionalId);
    itensDaComanda(doAgendamento, extra);
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "120.00", ComandaPagamento.STATUS_CONFIRMADO));

    service.fechar(comandaId);

    // desconto de R$ 30 rateado: 100 -> 80, 50 -> 40
    verify(commissionService)
        .registerServiceCommissionForComandaItemIfApplicable(
            eq(tenantId), eq(comandaId), eq(doAgendamento.getId()), eq(professionalId),
            eq(serviceId), any(), org.mockito.ArgumentMatchers.argThat(v -> v.compareTo(new BigDecimal("80.00")) == 0), any());
    verify(commissionService)
        .registerServiceCommissionForComandaItemIfApplicable(
            eq(tenantId), eq(comandaId), eq(extra.getId()), eq(professionalId),
            eq(serviceId), any(), org.mockito.ArgumentMatchers.argThat(v -> v.compareTo(new BigDecimal("40.00")) == 0), any());
    verify(estoqueMovimentacaoService).consumirInsumosPorItemComanda(tenantId, extra.getId(), serviceId);
  }

  /** Atendimento concluido pela regra ANTIGA ja tem comissao: a comanda dele nao gera a segunda. */
  @Test
  void comandaDeAtendimentoJaComissionadoNaoGeraDeNovo() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    UUID appointmentId = UUID.randomUUID();
    aberta.setAppointmentId(appointmentId);
    aberta.setSubtotal(new BigDecimal("100.00"));
    aberta.setTotal(new BigDecimal("100.00"));
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "100.00", professionalId));
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "100.00", ComandaPagamento.STATUS_CONFIRMADO));
    when(commissionService.possuiComissaoDeServicoDoAgendamento(tenantId, appointmentId)).thenReturn(true);

    service.fechar(comandaId);

    verify(commissionService, never())
        .registerServiceCommissionForComandaItemIfApplicable(any(), any(), any(), any(), any(), any(), any(), any());
    verify(estoqueMovimentacaoService, never()).consumirInsumosPorItemComanda(any(), any(), any());
  }

  // ─── A5: a recepcao escolhe o pacote (decisao de 2026-09-17) ──────────────

  private ClientPackageBalance saldoDePacote(int totais, int usadas, UUID clienteDoPacote) {
    ClientPackagePurchase compra = new ClientPackagePurchase();
    compra.setId(UUID.randomUUID());
    compra.setTenantId(tenantId);
    compra.setClientId(clienteDoPacote);
    compra.setPackageNome("5 cortes");
    compra.setPrecoPago(new BigDecimal("250.00"));
    ClientPackageBalance saldo = new ClientPackageBalance();
    saldo.setId(UUID.randomUUID());
    saldo.setTenantId(tenantId);
    saldo.setPurchaseId(compra.getId());
    saldo.setServiceId(serviceId);
    saldo.setServiceNome("Corte");
    saldo.setSessoesTotais(totais);
    saldo.setSessoesUsadas(usadas);
    when(clientPackageBalanceRepository.findById(saldo.getId())).thenReturn(Optional.of(saldo));
    when(clientPackagePurchaseRepository.findById(compra.getId())).thenReturn(Optional.of(compra));
    when(clientPackageBalanceRepository.findByPurchaseId(compra.getId())).thenReturn(List.of(saldo));
    return saldo;
  }

  private ComandaDtos.AplicarCoberturaRequest coberturaDoPacote(ClientPackageBalance saldo) {
    ComandaDtos.AplicarCoberturaRequest req = new ComandaDtos.AplicarCoberturaRequest();
    req.tipo = ComandaItem.COBERTURA_PACOTE;
    req.saldoId = saldo.getId().toString();
    return req;
  }

  /** O item sai da conta e guarda o valor da sessao: R$ 250 / 5 sessoes = R$ 50. */
  @Test
  void pacoteCobreOServicoZerandoAContaEGuardandoOValorDaSessao() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setClientId(clientId);
    ComandaItem corte = item(ComandaItem.TIPO_SERVICO, "70.00", professionalId);
    when(comandaItemRepository.findByIdAndComandaId(corte.getId(), comandaId)).thenReturn(Optional.of(corte));
    ClientPackageBalance saldo = saldoDePacote(5, 1, clientId);

    service.aplicarCobertura(comandaId, corte.getId(), coberturaDoPacote(saldo));

    assertThat(corte.getTotal()).isEqualByComparingTo("0");
    assertThat(corte.getPrecoAntesCobertura()).isEqualByComparingTo("70.00");
    assertThat(corte.getValorCobertura()).isEqualByComparingTo("50.00");
    // A sessao so desce quando a comanda fechar.
    assertThat(saldo.getSessoesUsadas()).isEqualTo(1);
  }

  @Test
  void pacoteDeOutroClienteNaoCobre() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setClientId(clientId);
    ComandaItem corte = item(ComandaItem.TIPO_SERVICO, "70.00", professionalId);
    when(comandaItemRepository.findByIdAndComandaId(corte.getId(), comandaId)).thenReturn(Optional.of(corte));
    ClientPackageBalance deOutro = saldoDePacote(5, 0, UUID.randomUUID());

    assertThatThrownBy(() -> service.aplicarCobertura(comandaId, corte.getId(), coberturaDoPacote(deOutro)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nao e do cliente da comanda");
  }

  @Test
  void pacoteSemSessaoNaoCobre() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setClientId(clientId);
    ComandaItem corte = item(ComandaItem.TIPO_SERVICO, "70.00", professionalId);
    when(comandaItemRepository.findByIdAndComandaId(corte.getId(), comandaId)).thenReturn(Optional.of(corte));
    ClientPackageBalance esgotado = saldoDePacote(5, 5, clientId);

    assertThatThrownBy(() -> service.aplicarCobertura(comandaId, corte.getId(), coberturaDoPacote(esgotado)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nao tem mais sessoes");
  }

  @Test
  void comandaSemClienteNaoUsaPacote() {
    comanda(Comanda.STATUS_ABERTA);
    ComandaItem corte = item(ComandaItem.TIPO_SERVICO, "70.00", professionalId);
    when(comandaItemRepository.findByIdAndComandaId(corte.getId(), comandaId)).thenReturn(Optional.of(corte));
    ClientPackageBalance saldo = saldoDePacote(5, 0, clientId);

    assertThatThrownBy(() -> service.aplicarCobertura(comandaId, corte.getId(), coberturaDoPacote(saldo)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("identifique o cliente");
  }

  /** Ao fechar, a sessao desce e o profissional recebe sobre o valor da sessao. */
  @Test
  void fecharConsomeASessaoEComissionaPeloValorDaSessao() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setClientId(clientId);
    aberta.setSubtotal(BigDecimal.ZERO);
    aberta.setTotal(BigDecimal.ZERO);
    ClientPackageBalance saldo = saldoDePacote(5, 1, clientId);
    ComandaItem corte = item(ComandaItem.TIPO_SERVICO, "0.00", professionalId);
    corte.setCoberturaTipo(ComandaItem.COBERTURA_PACOTE);
    corte.setCoberturaSaldoId(saldo.getId());
    corte.setValorCobertura(new BigDecimal("50.00"));
    itensDaComanda(corte);
    pagamentosDaComanda();
    when(clientPackageBalanceRepository.consumirSeHouverSaldo(saldo.getId(), 1)).thenReturn(1);

    service.fechar(comandaId);

    // O consumo e uma instrucao unica no banco (so passa se ainda houver saldo).
    verify(clientPackageBalanceRepository).consumirSeHouverSaldo(saldo.getId(), 1);
    verify(commissionService)
        .registerServiceCommissionForComandaItemIfApplicable(
            eq(tenantId), eq(comandaId), eq(corte.getId()), eq(professionalId), eq(serviceId),
            any(), org.mockito.ArgumentMatchers.argThat(v -> v.compareTo(new BigDecimal("50.00")) == 0), any());
    verify(transacaoRepository, never()).save(any());
  }

  @Test
  void estornoDevolveASessaoAoPacote() {
    comanda(Comanda.STATUS_FECHADA);
    when(transacaoRepository.listarAtivasPorComanda(any(), any())).thenReturn(List.of());
    ClientPackageBalance saldo = saldoDePacote(5, 3, clientId);
    ComandaItem corte = item(ComandaItem.TIPO_SERVICO, "0.00", professionalId);
    corte.setCoberturaTipo(ComandaItem.COBERTURA_PACOTE);
    corte.setCoberturaSaldoId(saldo.getId());
    itensDaComanda(corte);

    ComandaDtos.EstornarComandaRequest req = new ComandaDtos.EstornarComandaRequest();
    req.motivo = "cliente nao foi atendido";
    service.estornar(comandaId, req);

    verify(clientPackageBalanceRepository).devolver(saldo.getId(), 1);
  }

  /**
   * Duas comandas escolheram a ultima sessao: a que fecha depois encontra o saldo zerado no banco
   * e o fechamento inteiro volta atras — antes as duas fechavam (2026-09-18).
   */
  @Test
  void fecharRecusaQuandoOutraComandaLevouAUltimaSessao() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setClientId(clientId);
    aberta.setSubtotal(BigDecimal.ZERO);
    aberta.setTotal(BigDecimal.ZERO);
    ClientPackageBalance saldo = saldoDePacote(3, 2, clientId);
    ComandaItem corte = item(ComandaItem.TIPO_SERVICO, "0.00", professionalId);
    corte.setCoberturaTipo(ComandaItem.COBERTURA_PACOTE);
    corte.setCoberturaSaldoId(saldo.getId());
    corte.setValorCobertura(new BigDecimal("80.00"));
    itensDaComanda(corte);
    pagamentosDaComanda();
    when(clientPackageBalanceRepository.consumirSeHouverSaldo(saldo.getId(), 1)).thenReturn(0);

    assertThatThrownBy(() -> service.fechar(comandaId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("acabou enquanto a comanda estava aberta");
    assertThat(aberta.getStatus()).isEqualTo(Comanda.STATUS_ABERTA);
  }

  /** Estornar a venda do pacote depois de usar sessoes devolveria o valor inteiro. */
  @Test
  void estornoDaVendaDePacoteComSessoesUsadasEhRecusado() {
    comanda(Comanda.STATUS_FECHADA);
    when(transacaoRepository.listarAtivasPorComanda(any(), any())).thenReturn(List.of());
    ClientPackagePurchase compra = new ClientPackagePurchase();
    compra.setId(UUID.randomUUID());
    compra.setPackageNome("3 cortes");
    when(clientPackagePurchaseRepository.findByTenantIdAndComandaId(tenantId, comandaId))
        .thenReturn(List.of(compra));
    ClientPackageBalance usado = new ClientPackageBalance();
    usado.setSessoesTotais(3);
    usado.setSessoesUsadas(2);
    when(clientPackageBalanceRepository.findByPurchaseId(compra.getId())).thenReturn(List.of(usado));
    ComandaDtos.EstornarComandaRequest req = new ComandaDtos.EstornarComandaRequest();
    req.motivo = "cliente pediu o dinheiro de volta";

    assertThatThrownBy(() -> service.estornar(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ja usou 2 sessoes");
    verify(clientPackagePurchaseRepository, never()).delete(any());
  }

  @Test
  void retirarACoberturaVoltaOPreco() {
    Comanda aberta = comanda(Comanda.STATUS_ABERTA);
    aberta.setClientId(clientId);
    ComandaItem corte = item(ComandaItem.TIPO_SERVICO, "0.00", professionalId);
    corte.setCoberturaTipo(ComandaItem.COBERTURA_PACOTE);
    corte.setCoberturaSaldoId(UUID.randomUUID());
    corte.setValorCobertura(new BigDecimal("50.00"));
    corte.setPrecoAntesCobertura(new BigDecimal("70.00"));
    when(comandaItemRepository.findByIdAndComandaId(corte.getId(), comandaId)).thenReturn(Optional.of(corte));

    service.removerCobertura(comandaId, corte.getId());

    assertThat(corte.getCoberturaTipo()).isNull();
    assertThat(corte.getTotal()).isEqualByComparingTo("70.00");
  }

  // ─── Produto sem estoque e recusado ao lancar (2026-09-16, M5) ─────────────

  /** Antes so o FECHAR recusava — depois de o cliente pagar. */
  @Test
  void produtoSemEstoqueEhRecusadoAoLancar() {
    comanda(Comanda.STATUS_ABERTA);
    ItemEstoque produto = new ItemEstoque();
    produto.setId(serviceId);
    produto.setTenantId(tenantId);
    produto.setNome("Shampoo");
    produto.setSaldoAtual(new BigDecimal("1"));
    when(itemEstoqueRepository.findByIdAndTenantId(eq(serviceId), eq(tenantId))).thenReturn(Optional.of(produto));
    when(estoqueMovimentacaoService.faltaSaldoParaVender(eq(tenantId), eq(serviceId), any())).thenReturn(true);

    ComandaDtos.AdicionarItemRequest req = new ComandaDtos.AdicionarItemRequest();
    req.tipo = ComandaItem.TIPO_PRODUTO;
    req.referenciaId = serviceId.toString();
    req.quantidade = new BigDecimal("2");
    req.precoUnitario = new BigDecimal("40.00");

    assertThatThrownBy(() -> service.adicionarItem(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Sem estoque de Shampoo");
    verify(comandaItemRepository, never()).save(any());
  }

  // ─── Comanda isolada por salao (2026-09-18, teste de isolamento) ───────────

  /** Outro salao abria comanda com o id de um agendamento daqui. */
  @Test
  void abrirRecusaAgendamentoDeOutroSalao() {
    UUID deOutroSalao = UUID.randomUUID();
    when(agendamentoRepository.findByIdAndTenantId(eq(deOutroSalao), eq(tenantId)))
        .thenReturn(Optional.empty());
    ComandaDtos.AbrirComandaRequest req = new ComandaDtos.AbrirComandaRequest();
    req.appointmentId = deOutroSalao.toString();

    assertThatThrownBy(() -> service.abrir(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Agendamento nao encontrado");
    verify(comandaRepository, never()).save(any());
  }

  @Test
  void abrirRecusaClienteDeOutroSalao() {
    UUID deOutroSalao = UUID.randomUUID();
    when(clienteRepository.findByIdAndTenantId(eq(deOutroSalao), eq(tenantId)))
        .thenReturn(Optional.empty());
    ComandaDtos.AbrirComandaRequest req = new ComandaDtos.AbrirComandaRequest();
    req.clientId = deOutroSalao.toString();

    assertThatThrownBy(() -> service.abrir(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cliente nao encontrado");
    verify(comandaRepository, never()).save(any());
  }

  /** O cliente da comanda e o do agendamento: nao da para cobrar o atendimento de um no outro. */
  @Test
  void abrirRecusaClienteDiferenteDoAgendamento() {
    UUID agendamentoId = UUID.randomUUID();
    Agendamento agendamento = new Agendamento();
    agendamento.setId(agendamentoId);
    agendamento.setClientId(UUID.randomUUID());
    when(agendamentoRepository.findByIdAndTenantId(eq(agendamentoId), eq(tenantId)))
        .thenReturn(Optional.of(agendamento));
    ComandaDtos.AbrirComandaRequest req = new ComandaDtos.AbrirComandaRequest();
    req.appointmentId = agendamentoId.toString();
    req.clientId = UUID.randomUUID().toString();

    assertThatThrownBy(() -> service.abrir(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mesmo do agendamento");
  }

  /** Sem cliente informado, a comanda do agendamento herda o cliente dele. */
  @Test
  void abrirComAgendamentoHerdaOCliente() {
    UUID agendamentoId = UUID.randomUUID();
    UUID clienteId = UUID.randomUUID();
    Agendamento agendamento = new Agendamento();
    agendamento.setId(agendamentoId);
    agendamento.setClientId(clienteId);
    when(agendamentoRepository.findByIdAndTenantId(eq(agendamentoId), eq(tenantId)))
        .thenReturn(Optional.of(agendamento));
    when(clienteRepository.findByIdAndTenantId(eq(clienteId), eq(tenantId)))
        .thenReturn(Optional.of(new Cliente()));
    when(comandaRepository.save(any(Comanda.class))).thenAnswer(inv -> {
      Comanda c = inv.getArgument(0);
      c.setId(UUID.randomUUID());
      return c;
    });
    ComandaDtos.AbrirComandaRequest req = new ComandaDtos.AbrirComandaRequest();
    req.appointmentId = agendamentoId.toString();

    ComandaDtos.ComandaResponse response = service.abrir(req);

    assertThat(response.clientId).isEqualTo(clienteId.toString());
    assertThat(response.appointmentId).isEqualTo(agendamentoId.toString());
  }

  /** O sinal pago por cliente de outro salao nao paga comanda daqui. */
  @Test
  void creditoDeSinalDeOutroSalaoNaoEhAceito() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    UUID appointmentId = UUID.randomUUID();
    comanda.setAppointmentId(appointmentId);
    AppointmentDeposit deposit = new AppointmentDeposit();
    deposit.setId(UUID.randomUUID());
    deposit.setAppointmentId(appointmentId);
    deposit.setTenantId(UUID.randomUUID());
    deposit.setStatus(AppointmentDeposit.STATUS_PAID);
    deposit.setAmountCents(3000L);
    when(appointmentDepositRepository.findPaidUnusedByAppointmentId(eq(appointmentId)))
        .thenReturn(Optional.of(deposit));
    ComandaDtos.RegistrarPagamentoRequest req = new ComandaDtos.RegistrarPagamentoRequest();
    req.meio = ComandaPagamento.MEIO_CREDITO_SINAL;
    req.valor = new BigDecimal("30.00");

    assertThatThrownBy(() -> service.registrarPagamento(comandaId, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nao ha sinal pago");
    assertThat(deposit.getUsedInComandaId()).isNull();
  }

  /** Um atendimento, uma cobranca: segunda comanda so depois de estornar a primeira. */
  @Test
  void abrirRecusaSegundaComandaDoMesmoAgendamento() {
    UUID agendamentoId = UUID.randomUUID();
    UUID clienteId = UUID.randomUUID();
    Agendamento agendamento = new Agendamento();
    agendamento.setId(agendamentoId);
    agendamento.setClientId(clienteId);
    when(agendamentoRepository.findByIdAndTenantId(eq(agendamentoId), eq(tenantId)))
        .thenReturn(Optional.of(agendamento));
    Comanda jaCobrada = new Comanda();
    jaCobrada.setStatus(Comanda.STATUS_FECHADA);
    Comanda estornada = new Comanda();
    estornada.setStatus(Comanda.STATUS_ESTORNADA);
    when(comandaRepository.findByAppointmentIdAndTenantId(eq(agendamentoId), eq(tenantId)))
        .thenReturn(List.of(estornada, jaCobrada));
    ComandaDtos.AbrirComandaRequest req = new ComandaDtos.AbrirComandaRequest();
    req.appointmentId = agendamentoId.toString();

    assertThatThrownBy(() -> service.abrir(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ja foi cobrado");
    verify(comandaRepository, never()).save(any());
  }
}
