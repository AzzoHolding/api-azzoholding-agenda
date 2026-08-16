package br.com.phdigitalcode.azzo.agenda.pro.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Cobre {@code modules/fiscal/infrastructure/fiscal/MockFiscalProvider.java}.
 *
 * <p>Porte verbatim: mesmo estado em memoria por tenant, mesmas regras de validacao obrigatoria
 * antes de autorizar, mesmo algoritmo de chave de acesso mock (modulo 11) e mesmo calculo de
 * apuracao. Cada teste isola um tenant novo para nao vazar estado entre casos (o provider e um
 * singleton com {@code ConcurrentHashMap} por processo, fiel ao original).
 */
class MockFiscalProviderTest {

  private final MockFiscalProvider provider = new MockFiscalProvider();
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
  }

  // ─── TaxConfig ──────────────────────────────────────────────────────────

  @Test
  void obterTaxConfigCriaDefaultNaPrimeiraChamadaEReusaDepois() {
    FiscalDtos.TaxConfig primeira = provider.obterTaxConfig(tenantId);

    assertThat(primeira.regime).isEqualTo("SIMPLES_NACIONAL");
    assertThat(primeira.icmsRate).isEqualByComparingTo("2.75");
    assertThat(primeira.pisRate).isEqualByComparingTo("0.65");
    assertThat(primeira.cofinsRate).isEqualByComparingTo("3.00");
    assertThat(primeira.issuerUfCode).isEqualTo("35");

    FiscalDtos.TaxConfig segunda = provider.obterTaxConfig(tenantId);
    assertThat(segunda).isSameAs(primeira);
  }

  @Test
  void atualizarTaxConfigComNullRestauraODefault() {
    FiscalDtos.TaxConfig customizado = new FiscalDtos.TaxConfig();
    customizado.regime = "LUCRO_PRESUMIDO";
    provider.atualizarTaxConfig(tenantId, customizado);
    assertThat(provider.obterTaxConfig(tenantId).regime).isEqualTo("LUCRO_PRESUMIDO");

    FiscalDtos.TaxConfig resultado = provider.atualizarTaxConfig(tenantId, null);

    assertThat(resultado.regime).isEqualTo("SIMPLES_NACIONAL");
  }

  @Test
  void taxConfigEIsoladaPorTenant() {
    UUID outroTenant = UUID.randomUUID();
    FiscalDtos.TaxConfig customizado = new FiscalDtos.TaxConfig();
    customizado.regime = "LUCRO_REAL";
    provider.atualizarTaxConfig(tenantId, customizado);

    assertThat(provider.obterTaxConfig(outroTenant).regime).isEqualTo("SIMPLES_NACIONAL");
    assertThat(provider.obterTaxConfig(tenantId).regime).isEqualTo("LUCRO_REAL");
  }

  // ─── CRUD de invoice ────────────────────────────────────────────────────

  @Test
  void criarInvoiceGeraIdESomaOsItensNoTotalAmount() {
    FiscalDtos.Invoice request = invoiceComItens(1000L, 2500L);
    request.type = "NFCE";

    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, request);

    assertThat(criada.id).isNotBlank();
    assertThat(criada.status).isEqualTo("DRAFT");
    assertThat(criada.totalAmount).isEqualTo(3500L);
    assertThat(criada.createdAt).isEqualTo(LocalDate.now().toString());
    assertThat(provider.obterInvoice(tenantId, criada.id)).isSameAs(criada);
  }

  @Test
  void criarInvoiceComStatusEmBrancoViraDraft() {
    FiscalDtos.Invoice request = invoiceComItens(100L);
    request.status = "   ";

    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, request);

    assertThat(criada.status).isEqualTo("DRAFT");
  }

  @Test
  void criarInvoiceComStatusExplicitoPreservaOStatus() {
    FiscalDtos.Invoice request = invoiceComItens(100L);
    request.status = "PENDING";

    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, request);

    assertThat(criada.status).isEqualTo("PENDING");
  }

  @Test
  void obterInvoiceInexistenteLancaIllegalArgumentException() {
    assertThatThrownBy(() -> provider.obterInvoice(tenantId, "nao-existe"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invoice nao encontrada");
  }

  @Test
  void atualizarInvoiceEmDraftSubstituiOsCamposESomaNovamente() {
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));

    FiscalDtos.Invoice request = invoiceComItens(500L, 500L);
    request.notes = "revisado";
    FiscalDtos.Invoice atualizada = provider.atualizarInvoice(tenantId, criada.id, request);

    assertThat(atualizada.totalAmount).isEqualTo(1000L);
    assertThat(atualizada.notes).isEqualTo("revisado");
    assertThat(atualizada.id).isEqualTo(criada.id);
  }

  @Test
  void atualizarInvoiceForaDeDraftLancaIllegalArgumentException() {
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));
    criada.status = "AUTHORIZED";

    assertThatThrownBy(() -> provider.atualizarInvoice(tenantId, criada.id, invoiceComItens(1L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Somente rascunhos podem ser alterados.");
  }

  @Test
  void cancelarInvoiceRegistraOMotivoEOStatus() {
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));
    FiscalDtos.CancelInvoiceRequest cancelamento = new FiscalDtos.CancelInvoiceRequest();
    cancelamento.reason = "cliente desistiu";

    FiscalDtos.Invoice cancelada = provider.cancelarInvoice(tenantId, criada.id, cancelamento);

    assertThat(cancelada.status).isEqualTo("CANCELLED");
    assertThat(cancelada.cancelReason).isEqualTo("cliente desistiu");
  }

  @Test
  void cancelarInvoiceSemRequestNaoLancaEDeixaMotivoNulo() {
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));

    FiscalDtos.Invoice cancelada = provider.cancelarInvoice(tenantId, criada.id, null);

    assertThat(cancelada.status).isEqualTo("CANCELLED");
    assertThat(cancelada.cancelReason).isNull();
  }

  // ─── autorizarInvoice / validacao obrigatoria ──────────────────────────

  @Test
  void autorizarInvoiceSemConfiguracaoFiscalLancaIllegalArgumentException() {
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));

    assertThatThrownBy(() -> provider.autorizarInvoice(tenantId, criada.id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Configure Razao Social do emitente.");
  }

  @Test
  void autorizarInvoiceExigeCnpjComQuatorzeDigitos() {
    FiscalDtos.TaxConfig cfg = configuracaoCompleta();
    cfg.issuerCnpj = "123";
    provider.atualizarTaxConfig(tenantId, cfg);
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));

    assertThatThrownBy(() -> provider.autorizarInvoice(tenantId, criada.id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Configure CNPJ valido do emitente (14 digitos).");
  }

  @Test
  void autorizarInvoiceExigeIeDoEmitente() {
    FiscalDtos.TaxConfig cfg = configuracaoCompleta();
    cfg.issuerIe = null;
    provider.atualizarTaxConfig(tenantId, cfg);
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));

    assertThatThrownBy(() -> provider.autorizarInvoice(tenantId, criada.id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Configure IE do emitente.");
  }

  @Test
  void autorizarInvoiceExigeCidadeEEstado() {
    FiscalDtos.TaxConfig cfg = configuracaoCompleta();
    cfg.issuerCity = "";
    provider.atualizarTaxConfig(tenantId, cfg);
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));

    assertThatThrownBy(() -> provider.autorizarInvoice(tenantId, criada.id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Configure cidade e UF do emitente.");
  }

  @Test
  void autorizarNfceExigeCscEIdToken() {
    FiscalDtos.TaxConfig cfg = configuracaoCompleta();
    provider.atualizarTaxConfig(tenantId, cfg);
    FiscalDtos.Invoice request = invoiceComItens(100L);
    request.type = "NFCE";
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, request);

    assertThatThrownBy(() -> provider.autorizarInvoice(tenantId, criada.id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Configure CSC e idToken da NFC-e para simulacao SEFAZ.");
  }

  @Test
  void autorizarNfceComCscEIdTokenPreenchidosFunciona() {
    FiscalDtos.TaxConfig cfg = configuracaoCompleta();
    cfg.nfceCscHomologation = "csc-homolog";
    cfg.nfceCscIdTokenHomologation = "token-homolog";
    provider.atualizarTaxConfig(tenantId, cfg);
    FiscalDtos.Invoice request = invoiceComItens(100L);
    request.type = "NFCE";
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, request);

    FiscalDtos.Invoice autorizada = provider.autorizarInvoice(tenantId, criada.id);

    assertThat(autorizada.status).isEqualTo("AUTHORIZED");
    assertThat(autorizada.accessKey).hasSize(44).matches("\\d{44}");
    assertThat(autorizada.authorizationProtocol).hasSize(15).startsWith("13");
  }

  @Test
  void autorizarNfeNaoExigeCscNemIdToken() {
    FiscalDtos.TaxConfig cfg = configuracaoCompleta();
    provider.atualizarTaxConfig(tenantId, cfg);
    FiscalDtos.Invoice request = invoiceComItens(100L);
    request.type = "NFE";
    request.numeroNf = "123";
    request.serieNf = "1";
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, request);

    FiscalDtos.Invoice autorizada = provider.autorizarInvoice(tenantId, criada.id);

    assertThat(autorizada.status).isEqualTo("AUTHORIZED");
    assertThat(autorizada.accessKey).hasSize(44);
    // modelo "55" para NFE, na posicao 20 (0-indexed) da chave de 44 digitos.
    assertThat(autorizada.accessKey.substring(20, 22)).isEqualTo("55");
  }

  @Test
  void gerarPdfInvoiceProduzBytesComIdEStatus() {
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));

    byte[] pdf = provider.gerarPdfInvoice(tenantId, criada.id);

    String texto = new String(pdf, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(texto).startsWith("%PDF-1.4").contains(criada.id).contains("DRAFT").endsWith("%%EOF");
  }

  // ─── listarInvoices ─────────────────────────────────────────────────────

  @Test
  void listarInvoicesAplicaPaginacaoDefaultQuandoParametrosAusentes() {
    for (int i = 0; i < 3; i++) {
      provider.criarInvoice(tenantId, invoiceComItens(100L));
    }

    FiscalDtos.InvoiceListResponse resp = provider.listarInvoices(tenantId, null, null, null, null, null);

    assertThat(resp.page).isEqualTo(1);
    assertThat(resp.pageSize).isEqualTo(20);
    assertThat(resp.total).isEqualTo(3);
    assertThat(resp.items).hasSize(3);
  }

  @Test
  void listarInvoicesFiltraPorStatusIgnorandoCaixa() {
    FiscalDtos.Invoice draft = provider.criarInvoice(tenantId, invoiceComItens(100L));
    FiscalDtos.Invoice cancelada = provider.criarInvoice(tenantId, invoiceComItens(200L));
    provider.cancelarInvoice(tenantId, cancelada.id, null);

    FiscalDtos.InvoiceListResponse resp = provider.listarInvoices(tenantId, "cancelled", null, null, null, null);

    assertThat(resp.items).extracting(i -> i.id).containsExactly(cancelada.id);
  }

  @Test
  void listarInvoicesComPaginaAlemDoTotalDevolveListaVazia() {
    provider.criarInvoice(tenantId, invoiceComItens(100L));

    FiscalDtos.InvoiceListResponse resp = provider.listarInvoices(tenantId, null, null, null, 5, 10);

    assertThat(resp.items).isEmpty();
    assertThat(resp.total).isEqualTo(1);
  }

  @Test
  void listarInvoicesFiltraPorIntervaloDeData() {
    FiscalDtos.Invoice criada = provider.criarInvoice(tenantId, invoiceComItens(100L));
    String hoje = LocalDate.now().toString();
    String ontem = LocalDate.now().minusDays(1).toString();
    String amanha = LocalDate.now().plusDays(1).toString();

    FiscalDtos.InvoiceListResponse dentro = provider.listarInvoices(tenantId, null, ontem, amanha, null, null);
    FiscalDtos.InvoiceListResponse fora = provider.listarInvoices(tenantId, null, amanha, amanha, null, null);

    assertThat(dentro.items).extracting(i -> i.id).containsExactly(criada.id);
    assertThat(fora.items).isEmpty();
    assertThat(hoje).isNotBlank();
  }

  // ─── apuracao ───────────────────────────────────────────────────────────

  @Test
  void obterApuracaoAtualUsaAnoEMesCorrentes() {
    provider.criarInvoice(tenantId, invoiceComItens(1000L));
    YearMonth agora = YearMonth.now();

    FiscalDtos.ApuracaoMensal apuracao = provider.obterApuracaoAtual(tenantId);

    assertThat(apuracao.ano).isEqualTo(agora.getYear());
    assertThat(apuracao.mes).isEqualTo(agora.getMonthValue());
    assertThat(apuracao.totalServicos).isEqualTo(1000L);
    assertThat(apuracao.totalDocumentos).isEqualTo(1);
  }

  @Test
  void calculoDeImpostosNaApuracaoUsaAsAliquotasConfiguradas() {
    FiscalDtos.TaxConfig cfg = new FiscalDtos.TaxConfig();
    cfg.icmsRate = new BigDecimal("10");
    cfg.pisRate = new BigDecimal("1");
    cfg.cofinsRate = new BigDecimal("1");
    provider.atualizarTaxConfig(tenantId, cfg);
    provider.criarInvoice(tenantId, invoiceComItens(1000L));

    FiscalDtos.ApuracaoMensal apuracao = provider.obterApuracaoAtual(tenantId);

    // (1000 * 12%) = 120.00 -> arredondado para 120
    assertThat(apuracao.totalImpostos).isEqualTo(120L);
  }

  @Test
  void recalcularApuracaoDevolveOMesmoResultadoQueObterApuracao() {
    provider.criarInvoice(tenantId, invoiceComItens(500L));
    YearMonth agora = YearMonth.now();

    FiscalDtos.ApuracaoMensal a = provider.obterApuracao(tenantId, agora.getYear(), agora.getMonthValue());
    FiscalDtos.ApuracaoMensal b = provider.recalcularApuracao(tenantId, agora.getYear(), agora.getMonthValue());

    assertThat(a.totalServicos).isEqualTo(b.totalServicos);
    assertThat(a.totalImpostos).isEqualTo(b.totalImpostos);
  }

  @Test
  void historicoComLimiteMenorQueUmUsaDozeMeses() {
    provider.criarInvoice(tenantId, invoiceComItens(100L));

    var historico = provider.historico(tenantId, 0);

    assertThat(historico).hasSize(12);
  }

  @Test
  void historicoRespeitaLimitePositivo() {
    var historico = provider.historico(tenantId, 3);

    assertThat(historico).hasSize(3);
  }

  @Test
  void resumoAnualSomaOsDozeMeses() {
    FiscalDtos.Invoice request = invoiceComItens(1200L);
    provider.criarInvoice(tenantId, request);
    int ano = LocalDate.now().getYear();
    int mesAtual = LocalDate.now().getMonthValue();

    FiscalDtos.ResumoAnual resumo = provider.resumoAnual(tenantId, ano);

    assertThat(resumo.meses).hasSize(12);
    assertThat(resumo.totalServicos).isEqualTo(1200L);
    assertThat(resumo.meses.get(mesAtual - 1).totalServicos).isEqualTo(1200L);
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private FiscalDtos.Invoice invoiceComItens(long... precos) {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.type = "NFE";
    invoice.items = new java.util.ArrayList<>();
    for (long preco : precos) {
      FiscalDtos.InvoiceItem item = new FiscalDtos.InvoiceItem();
      item.totalPrice = preco;
      invoice.items.add(item);
    }
    return invoice;
  }

  private FiscalDtos.TaxConfig configuracaoCompleta() {
    FiscalDtos.TaxConfig cfg = new FiscalDtos.TaxConfig();
    cfg.issuerRazaoSocial = "Salao Teste LTDA";
    cfg.issuerCnpj = "12345678000199";
    cfg.issuerIe = "123456789";
    cfg.issuerCity = "Sao Paulo";
    cfg.issuerState = "SP";
    cfg.issuerUfCode = "35";
    return cfg;
  }
}
