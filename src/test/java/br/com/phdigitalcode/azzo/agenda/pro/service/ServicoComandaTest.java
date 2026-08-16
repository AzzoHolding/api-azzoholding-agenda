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
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackageBalance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackagePurchase;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Comanda;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantLoyaltySettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.TenantAsaasChargeService;
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

    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
    when(authenticatedUser.idOuNulo()).thenReturn(usuarioId);

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
            movimentacaoEstoqueRepository);
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
  void fecharNaoRegistraComissaoDeServicoQuandoHaAgendamentoVinculado() {
    Comanda comanda = comanda(Comanda.STATUS_ABERTA);
    comanda.setAppointmentId(UUID.randomUUID());
    comanda.setSubtotal(new BigDecimal("100.00"));
    comanda.setTotal(new BigDecimal("100.00"));
    itensDaComanda(item(ComandaItem.TIPO_SERVICO, "100.00", professionalId));
    pagamentosDaComanda(
        pagamento(ComandaPagamento.MEIO_DINHEIRO, "100.00", ComandaPagamento.STATUS_CONFIRMADO));

    service.fechar(comandaId);

    // ServicoAgendamentos ja registra comissao e consumo ao concluir o atendimento — repetir aqui
    // lancaria em dobro.
    verify(commissionService, never())
        .registerServiceCommissionForComandaItemIfApplicable(
            any(), any(), any(), any(), any(), any(), any(), any());
    verify(estoqueMovimentacaoService, never())
        .consumirInsumosPorItemComanda(any(), any(), any());
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
    when(clienteRepository.findById(eq(clientId))).thenReturn(Optional.of(cliente));

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
}
