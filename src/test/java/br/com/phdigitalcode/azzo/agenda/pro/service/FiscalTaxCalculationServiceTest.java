package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Cobre {@code modules/fiscal/application/FiscalTaxCalculationService.java}.
 *
 * <p>Alem dos dois casos do teste original, trava as bordas que o porte poderia ter mudado sem
 * ninguem notar: aliquota negativa, item nulo dentro da lista, invoice nula e o arredondamento
 * unico no fim do acumulado {@code double}.
 */
class FiscalTaxCalculationServiceTest {

  private final FiscalTaxCalculationService service = new FiscalTaxCalculationService();

  // ─── ICMS/PIS/COFINS (caminho BigDecimal) ─────────────────────────────────

  @Test
  void aplicaAsAliquotasBasicasSobreASomaDosItens() {
    FiscalDtos.Invoice invoice = invoiceComItem(1000L);
    FiscalDtos.TaxConfig tax = new FiscalDtos.TaxConfig();
    tax.icmsRate = new BigDecimal("10");
    tax.pisRate = new BigDecimal("1.65");
    tax.cofinsRate = new BigDecimal("7.6");

    FiscalTaxCalculationService.TaxCalculationResult result = service.calcular(invoice, tax);

    assertThat(result.totalServicos()).isEqualTo(1000L);
    assertThat(result.valorIcms()).isEqualTo(100L);
    assertThat(result.valorPis()).isEqualTo(17L);
    assertThat(result.valorCofins()).isEqualTo(76L);
    assertThat(result.totalImpostos()).isEqualTo(193L);
  }

  @Test
  void aliquotaNegativaEIgnoradaEmVezDeVirarCredito() {
    FiscalDtos.Invoice invoice = invoiceComItem(1000L);
    FiscalDtos.TaxConfig tax = new FiscalDtos.TaxConfig();
    tax.icmsRate = new BigDecimal("-10");

    FiscalTaxCalculationService.TaxCalculationResult result = service.calcular(invoice, tax);

    assertThat(result.valorIcms()).isZero();
    assertThat(result.totalImpostos()).isZero();
  }

  @Test
  void taxConfigNulaZeraOsImpostosMasPreservaOTotalDosServicos() {
    FiscalTaxCalculationService.TaxCalculationResult result =
        service.calcular(invoiceComItem(2500L), null);

    assertThat(result.totalServicos()).isEqualTo(2500L);
    assertThat(result.totalImpostos()).isZero();
  }

  // ─── ICMS-ST / DIFAL / FCP / partilha (caminho double) ────────────────────

  @Test
  void calculaIcmsStDifalFcpEPartilha() {
    FiscalDtos.Invoice invoice = invoiceComItem(1000L);
    FiscalDtos.InvoiceItem item = invoice.items.get(0);
    item.baseIcmsSt = 1000d;
    item.mvaPercent = 40d;
    item.aliquotaInternaDestino = 18d;
    item.aliquotaInterestadual = 12d;
    item.baseFcp = 1000d;
    item.aliquotaFcp = 2d;
    item.percentualPartilhaOrigem = 40d;
    item.percentualPartilhaDestino = 60d;

    FiscalTaxCalculationService.TaxCalculationResult result =
        service.calcular(invoice, new FiscalDtos.TaxConfig());

    assertThat(result.valorIcmsSt()).isEqualTo(132L);
    assertThat(result.valorDifal()).isEqualTo(60L);
    assertThat(result.valorFcp()).isEqualTo(20L);
    assertThat(result.valorPartilhaOrigem()).isEqualTo(24L);
    assertThat(result.valorPartilhaDestino()).isEqualTo(36L);
  }

  @Test
  void oDifalExigeAsDuasAliquotasEnquantoOStExigeApenasAInterna() {
    FiscalDtos.Invoice invoice = invoiceComItem(1000L);
    FiscalDtos.InvoiceItem item = invoice.items.get(0);
    item.baseIcmsSt = 1000d;
    item.aliquotaInternaDestino = 18d;
    // sem aliquotaInterestadual

    FiscalTaxCalculationService.TaxCalculationResult result =
        service.calcular(invoice, new FiscalDtos.TaxConfig());

    assertThat(result.valorIcmsSt()).isEqualTo(180L);
    assertThat(result.valorDifal()).isZero();
  }

  /**
   * O acumulado e {@code double} e so arredonda no fim. Tres itens de 0,5 centavo cada somam 1,5 e
   * viram 2 — se cada item fosse arredondado isoladamente o total seria 3 (ou 0).
   */
  @Test
  void oArredondamentoAcontenceUmaVezSobreOAcumuladoNaoPorItem() {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.items = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      FiscalDtos.InvoiceItem item = new FiscalDtos.InvoiceItem();
      item.quantity = 1;
      item.unitPrice = 0L;
      item.totalPrice = 0L;
      item.baseFcp = 50d;
      item.aliquotaFcp = 1d;
      invoice.items.add(item);
    }

    FiscalTaxCalculationService.TaxCalculationResult result =
        service.calcular(invoice, new FiscalDtos.TaxConfig());

    assertThat(result.valorFcp()).isEqualTo(2L);
  }

  @Test
  void baseNegativaContaComoZero() {
    FiscalDtos.Invoice invoice = invoiceComItem(1000L);
    FiscalDtos.InvoiceItem item = invoice.items.get(0);
    item.baseIcmsSt = -500d;
    item.aliquotaInternaDestino = 18d;

    FiscalTaxCalculationService.TaxCalculationResult result =
        service.calcular(invoice, new FiscalDtos.TaxConfig());

    assertThat(result.valorIcmsSt()).isZero();
  }

  // ─── entradas degeneradas ─────────────────────────────────────────────────

  @Test
  void itemNuloNaListaEPuladoSemQuebrar() {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    FiscalDtos.InvoiceItem valido = new FiscalDtos.InvoiceItem();
    valido.quantity = 1;
    valido.unitPrice = 700L;
    valido.totalPrice = 700L;
    invoice.items = new ArrayList<>(Arrays.asList(null, valido, null));

    FiscalTaxCalculationService.TaxCalculationResult result =
        service.calcular(invoice, new FiscalDtos.TaxConfig());

    assertThat(result.totalServicos()).isEqualTo(700L);
  }

  @Test
  void invoiceNulaDevolveResultadoZerado() {
    FiscalTaxCalculationService.TaxCalculationResult result = service.calcular(null, null);

    assertThat(result.totalServicos()).isZero();
    assertThat(result.totalImpostos()).isZero();
    assertThat(result.valorIcmsSt()).isZero();
    assertThat(result.valorDifal()).isZero();
    assertThat(result.valorFcp()).isZero();
  }

  private FiscalDtos.Invoice invoiceComItem(long totalItem) {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    FiscalDtos.InvoiceItem item = new FiscalDtos.InvoiceItem();
    item.quantity = 1;
    item.unitPrice = totalItem;
    item.totalPrice = totalItem;
    invoice.items = new ArrayList<>(java.util.List.of(item));
    return invoice;
  }
}
