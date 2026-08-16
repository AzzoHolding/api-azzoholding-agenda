package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/** Cobre {@code modules/fiscal/application/FiscalDanfeMapper.java}. */
class FiscalDanfeMapperTest {

  private final FiscalDanfeMapper mapper = new FiscalDanfeMapper();

  @Test
  void invoiceNulaDevolveViewModelVazio() {
    FiscalDanfeMapper.DanfeViewModel vm = mapper.map(null, null);

    assertThat(vm.invoiceId).isNull();
    assertThat(vm.items).isEmpty();
  }

  @Test
  void mapeiaTodosOsCamposDaInvoiceETaxConfig() {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.id = "inv-1";
    invoice.type = "NFE";
    invoice.status = "AUTHORIZED";
    invoice.numeroNf = "123";
    invoice.serieNf = "1";
    invoice.operationNature = "Venda";
    invoice.createdAt = "2026-01-01";
    invoice.accessKey = "chave";
    invoice.authorizationProtocol = "protocolo";
    invoice.notes = "obs";
    invoice.totalAmount = 5000L;
    FiscalDtos.InvoiceCustomer customer = new FiscalDtos.InvoiceCustomer();
    customer.name = "Cliente";
    invoice.customer = customer;
    FiscalDtos.InvoiceItem item = new FiscalDtos.InvoiceItem();
    item.description = "Corte";
    invoice.items.add(item);

    FiscalDtos.TaxConfig taxConfig = new FiscalDtos.TaxConfig();
    taxConfig.issuerRazaoSocial = "Salao";

    FiscalDanfeMapper.DanfeViewModel vm = mapper.map(invoice, taxConfig);

    assertThat(vm.invoiceId).isEqualTo("inv-1");
    assertThat(vm.model).isEqualTo("NFE");
    assertThat(vm.status).isEqualTo("AUTHORIZED");
    assertThat(vm.number).isEqualTo("123");
    assertThat(vm.series).isEqualTo("1");
    assertThat(vm.operationNature).isEqualTo("Venda");
    assertThat(vm.issueDate).isEqualTo("2026-01-01");
    assertThat(vm.accessKey).isEqualTo("chave");
    assertThat(vm.authorizationProtocol).isEqualTo("protocolo");
    assertThat(vm.customer.name).isEqualTo("Cliente");
    assertThat(vm.notes).isEqualTo("obs");
    assertThat(vm.taxConfig.issuerRazaoSocial).isEqualTo("Salao");
    assertThat(vm.totalAmount).isEqualTo(5000L);
    assertThat(vm.items).hasSize(1);
  }

  @Test
  void itemsNulosViramListaVaziaMutavel() {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.items = null;

    FiscalDanfeMapper.DanfeViewModel vm = mapper.map(invoice, null);

    assertThat(vm.items).isNotNull().isEmpty();
  }
}
