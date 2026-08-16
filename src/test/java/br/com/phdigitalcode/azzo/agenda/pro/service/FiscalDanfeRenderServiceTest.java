package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Cobre {@code modules/fiscal/application/FiscalDanfeRenderService.java}.
 *
 * <p>Smoke test do render: nao reimplementa OpenPDF, so verifica que o documento gerado e um PDF
 * valido (assinatura {@code %PDF}) tanto para invoice NFE/NFC-e quanto para o viewmodel vazio (o
 * caso de invoice nula do mapper).
 */
class FiscalDanfeRenderServiceTest {

  private final FiscalDanfeRenderService service = new FiscalDanfeRenderService();
  private final FiscalDanfeMapper mapper = new FiscalDanfeMapper();

  @Test
  void rendeUmPdfValidoParaNfe() {
    FiscalDtos.Invoice invoice = invoiceCompleta("NFE");

    byte[] pdf = service.render(UUID.randomUUID(), invoice.id, mapper.map(invoice, taxConfig()));

    assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    assertThat(pdf.length).isGreaterThan(100);
  }

  @Test
  void rendeUmPdfValidoParaNfce() {
    FiscalDtos.Invoice invoice = invoiceCompleta("NFCE");

    byte[] pdf = service.render(UUID.randomUUID(), invoice.id, mapper.map(invoice, taxConfig()));

    assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
  }

  @Test
  void rendeMesmoComViewModelVazio() {
    byte[] pdf = service.render(UUID.randomUUID(), "sem-invoice", mapper.map(null, null));

    assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
  }

  private FiscalDtos.Invoice invoiceCompleta(String tipo) {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.id = UUID.randomUUID().toString();
    invoice.type = tipo;
    invoice.status = "AUTHORIZED";
    invoice.numeroNf = "42";
    invoice.serieNf = "1";
    invoice.operationNature = "Venda de servico";
    invoice.createdAt = "2026-01-15T10:00:00Z";
    invoice.accessKey = "12345678901234567890123456789012345678901234";
    invoice.authorizationProtocol = "135260000000001";
    invoice.notes = "Nenhuma observacao.";
    invoice.totalAmount = 15000L;
    FiscalDtos.InvoiceCustomer customer = new FiscalDtos.InvoiceCustomer();
    customer.name = "Cliente Teste";
    customer.type = "PF";
    customer.document = "12345678900";
    invoice.customer = customer;
    FiscalDtos.InvoiceItem item = new FiscalDtos.InvoiceItem();
    item.description = "Corte de cabelo";
    item.cfop = "5102";
    item.cst = "102";
    item.quantity = 1;
    item.unitPrice = 15000L;
    item.totalPrice = 15000L;
    invoice.items.add(item);
    return invoice;
  }

  private FiscalDtos.TaxConfig taxConfig() {
    FiscalDtos.TaxConfig cfg = new FiscalDtos.TaxConfig();
    cfg.issuerRazaoSocial = "Salao Teste LTDA";
    cfg.issuerCnpj = "12345678000199";
    cfg.issuerIe = "123456789";
    cfg.issuerCity = "Sao Paulo";
    cfg.issuerState = "SP";
    return cfg;
  }
}
