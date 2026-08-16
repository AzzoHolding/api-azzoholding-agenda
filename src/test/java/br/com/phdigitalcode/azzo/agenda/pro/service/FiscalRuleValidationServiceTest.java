package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Cobre {@code modules/fiscal/application/FiscalRuleValidationService.java}.
 *
 * <p>As mensagens sao verificadas <b>literalmente</b> de proposito: elas chegam cruas ao usuario
 * pelo {@code GlobalExceptionHandler}, entao reescrever qualquer uma e mudanca de contrato.
 */
@ExtendWith(MockitoExtension.class)
class FiscalRuleValidationServiceTest {

  @Mock private FiscalCodeCatalogService fiscalCodeCatalogService;

  private FiscalRuleValidationService service;

  @BeforeEach
  void setUp() {
    service = new FiscalRuleValidationService(fiscalCodeCatalogService);
    // Sem catalogo cadastrado: a validacao de vigencia fica desligada, como no teste original.
    lenient().when(fiscalCodeCatalogService.hasCatalogForType(any())).thenReturn(false);
  }

  // ─── regime e CST/CSOSN ───────────────────────────────────────────────────

  @Test
  void aceitaInvoiceValidaNoSimplesNacional() {
    FiscalDtos.TaxConfig tax = new FiscalDtos.TaxConfig();
    tax.regime = "SIMPLES_NACIONAL";

    assertThatCode(() -> service.validarCriacao(invoiceBase("101", "5102"), tax))
        .doesNotThrowAnyException();
  }

  @Test
  void rejeitaCsosnInvalidoNoSimplesNacional() {
    FiscalDtos.TaxConfig tax = new FiscalDtos.TaxConfig();
    tax.regime = "SIMPLES_NACIONAL";

    assertThatThrownBy(() -> service.validarCriacao(invoiceBase("00", "5102"), tax))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CSOSN invalido para Simples Nacional");
  }

  /** O mesmo codigo "00" que e invalido no Simples e valido como CST fora dele. */
  @Test
  void oMesmoCodigoTrocaDeSignificadoConformeORegime() {
    FiscalDtos.TaxConfig lucroReal = new FiscalDtos.TaxConfig();
    lucroReal.regime = "lucro_real";

    assertThatCode(() -> service.validarCriacao(invoiceBase("00", "5102"), lucroReal))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> service.validarCriacao(invoiceBase("101", "5102"), lucroReal))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CST invalido para o regime tributario informado");
  }

  @Test
  void regimeAusenteCaiNoSimplesNacional() {
    // taxConfig nula => SIMPLES_NACIONAL => "101" passa e "00" nao.
    assertThatCode(() -> service.validarCriacao(invoiceBase("101", "5102"), null))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> service.validarCriacao(invoiceBase("00", "5102"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CSOSN invalido para Simples Nacional");
  }

  @Test
  void rejeitaRegimeNaoSuportado() {
    FiscalDtos.TaxConfig tax = new FiscalDtos.TaxConfig();
    tax.regime = "MEI";

    assertThatThrownBy(() -> service.validarCriacao(invoiceBase("101", "5102"), tax))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Regime tributario nao suportado");
  }

  @Test
  void exigeCstOuCsosnNoItem() {
    assertThatThrownBy(() -> service.validarCriacao(invoiceBase("  ", "5102"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CST/CSOSN obrigatorio no item fiscal");
  }

  // ─── NCM e CFOP ───────────────────────────────────────────────────────────

  /** NCM e opcional, CFOP nao — assimetria do original. */
  @Test
  void ncmAusentePassaMasCfopAusenteNao() {
    FiscalDtos.Invoice semNcm = invoiceBase("101", "5102");
    semNcm.items.get(0).ncm = null;
    assertThatCode(() -> service.validarCriacao(semNcm, null)).doesNotThrowAnyException();

    FiscalDtos.Invoice semCfop = invoiceBase("101", null);
    assertThatThrownBy(() -> service.validarCriacao(semCfop, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CFOP invalido. Informe 4 digitos numericos.");
  }

  @Test
  void rejeitaNcmForaDoFormatoDeOitoDigitos() {
    FiscalDtos.Invoice invoice = invoiceBase("101", "5102");
    invoice.items.get(0).ncm = "1234567";

    assertThatThrownBy(() -> service.validarCriacao(invoice, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NCM invalido. Informe 8 digitos numericos.");
  }

  @Test
  void semCatalogoCadastradoAVigenciaNaoEConsultada() {
    FiscalDtos.Invoice invoice = invoiceBase("101", "5102");
    invoice.items.get(0).ncm = "12345678";

    service.validarCriacao(invoice, null);

    verify(fiscalCodeCatalogService, never()).existsActive(any(), any(), any());
  }

  @Test
  void comCatalogoCadastradoOCodigoForaDaVigenciaERejeitado() {
    when(fiscalCodeCatalogService.hasCatalogForType("CFOP")).thenReturn(true);
    when(fiscalCodeCatalogService.existsActive(eq("CFOP"), eq("5102"), any(LocalDate.class)))
        .thenReturn(false);

    assertThatThrownBy(() -> service.validarCriacao(invoiceBase("101", "5102"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CFOP fora da vigencia ativa");
  }

  // ─── partilha ─────────────────────────────────────────────────────────────

  @Test
  void rejeitaPartilhaQuandoNaoTotaliza100() {
    FiscalDtos.Invoice invoice = invoiceBase("101", "5102");
    invoice.items.get(0).percentualPartilhaOrigem = 20d;
    invoice.items.get(0).percentualPartilhaDestino = 70d;

    assertThatThrownBy(() -> service.validarCriacao(invoice, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Percentuais de partilha devem totalizar 100%");
  }

  /** Os dois nulos = partilha nao informada, sai calado. Apenas um preenchido = rejeita. */
  @Test
  void partilhaPelaMetadeERejeitadaMasPartilhaAusenteNao() {
    FiscalDtos.Invoice semPartilha = invoiceBase("101", "5102");
    assertThatCode(() -> service.validarCriacao(semPartilha, null)).doesNotThrowAnyException();

    FiscalDtos.Invoice apenasOrigem = invoiceBase("101", "5102");
    apenasOrigem.items.get(0).percentualPartilhaOrigem = 100d;
    assertThatCode(() -> service.validarCriacao(apenasOrigem, null)).doesNotThrowAnyException();

    FiscalDtos.Invoice origemParcial = invoiceBase("101", "5102");
    origemParcial.items.get(0).percentualPartilhaOrigem = 40d;
    assertThatThrownBy(() -> service.validarCriacao(origemParcial, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Percentuais de partilha devem totalizar 100%");
  }

  @Test
  void rejeitaPartilhaNegativa() {
    FiscalDtos.Invoice invoice = invoiceBase("101", "5102");
    invoice.items.get(0).percentualPartilhaOrigem = -10d;
    invoice.items.get(0).percentualPartilhaDestino = 110d;

    assertThatThrownBy(() -> service.validarCriacao(invoice, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Percentuais de partilha nao podem ser negativos");
  }

  // ─── item basico ──────────────────────────────────────────────────────────

  @Test
  void exigeAoMenosUmItem() {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.items = new ArrayList<>();

    assertThatThrownBy(() -> service.validarCriacao(invoice, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invoice deve possuir ao menos um item");

    assertThatThrownBy(() -> service.validarCriacao(null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invoice obrigatoria");
  }

  @Test
  void rejeitaTotalDoItemDivergenteDeQuantidadeVezesUnitario() {
    FiscalDtos.Invoice invoice = invoiceBase("101", "5102");
    invoice.items.get(0).quantity = 2;

    assertThatThrownBy(() -> service.validarCriacao(invoice, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Total do item divergente de quantidade x valor unitario");
  }

  @Test
  void rejeitaQuantidadeZerada() {
    FiscalDtos.Invoice invoice = invoiceBase("101", "5102");
    invoice.items.get(0).quantity = 0;

    assertThatThrownBy(() -> service.validarCriacao(invoice, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Quantidade do item deve ser maior que zero");
  }

  // ─── validarTotais ────────────────────────────────────────────────────────

  @Test
  void preencheOTotalDaInvoiceQuandoNaoInformado() {
    FiscalDtos.Invoice invoice = invoiceBase("101", "5102");
    invoice.totalAmount = 0L;
    FiscalTaxCalculationService.TaxCalculationResult calculation =
        new FiscalTaxCalculationService().calcular(invoice, new FiscalDtos.TaxConfig());

    service.validarTotais(invoice, calculation);

    assertThat(invoice.totalAmount).isEqualTo(1000L);
  }

  @Test
  void rejeitaTotalDaInvoiceDivergenteDaSomaDosItens() {
    FiscalDtos.Invoice invoice = invoiceBase("101", "5102");
    invoice.totalAmount = 999L;
    FiscalTaxCalculationService.TaxCalculationResult calculation =
        new FiscalTaxCalculationService().calcular(invoice, new FiscalDtos.TaxConfig());

    assertThatThrownBy(() -> service.validarTotais(invoice, calculation))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Total da invoice divergente da soma dos itens");
  }

  @Test
  void validarTotaisSaiCaladoComArgumentoNulo() {
    assertThatCode(() -> service.validarTotais(null, null)).doesNotThrowAnyException();
    assertThatCode(() -> service.validarTotais(invoiceBase("101", "5102"), null))
        .doesNotThrowAnyException();
  }

  private FiscalDtos.Invoice invoiceBase(String cst, String cfop) {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.type = "NFE";
    invoice.customer = new FiscalDtos.InvoiceCustomer();
    invoice.customer.name = "Cliente";
    FiscalDtos.InvoiceItem item = new FiscalDtos.InvoiceItem();
    item.cst = cst;
    item.cfop = cfop;
    item.quantity = 1;
    item.unitPrice = 1000L;
    item.totalPrice = 1000L;
    invoice.items = new ArrayList<>(List.of(item));
    invoice.totalAmount = 1000L;
    return invoice;
  }
}
