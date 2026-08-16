package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/** Cobre {@code modules/fiscal/application/FiscalXmlBuilderService.java}. */
class FiscalXmlBuilderServiceTest {

  private final FiscalXmlBuilderService service = new FiscalXmlBuilderService();

  @Test
  void invoiceNulaLancaIllegalArgument() {
    assertThatThrownBy(() -> service.buildAndValidate(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invoice obrigatoria para gerar XML fiscal.");
  }

  @Test
  void geraXmlValidoContraOXsdComTodosOsCampos() {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.id = "inv-1";
    invoice.type = "NFE";
    FiscalDtos.InvoiceCustomer customer = new FiscalDtos.InvoiceCustomer();
    customer.name = "Cliente Teste";
    invoice.customer = customer;
    FiscalDtos.InvoiceItem item = new FiscalDtos.InvoiceItem();
    invoice.items.add(item);
    invoice.totalAmount = 1500L;

    String xml = service.buildAndValidate(invoice);

    assertThat(xml).isEqualTo(
        "<fiscalInvoice><id>inv-1</id><type>NFE</type><customerName>Cliente Teste</customerName>"
            + "<itemsCount>1</itemsCount><totalAmount>1500</totalAmount></fiscalInvoice>");
  }

  @Test
  void camposNulosViramVazioENaoQuebramOXsd() {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();

    String xml = service.buildAndValidate(invoice);

    assertThat(xml).isEqualTo(
        "<fiscalInvoice><id></id><type></type><customerName></customerName>"
            + "<itemsCount>0</itemsCount><totalAmount>0</totalAmount></fiscalInvoice>");
  }

  @Test
  void escapaCaracteresEspeciaisXmlNosCamposDeTexto() {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.id = "id&<>\"'";
    invoice.type = "NFE";
    FiscalDtos.InvoiceCustomer customer = new FiscalDtos.InvoiceCustomer();
    customer.name = "M&M's <Salao>";
    invoice.customer = customer;

    String xml = service.buildAndValidate(invoice);

    assertThat(xml).contains("<id>id&amp;&lt;&gt;&quot;&apos;</id>");
    assertThat(xml).contains("<customerName>M&amp;M&apos;s &lt;Salao&gt;</customerName>");
  }
}
