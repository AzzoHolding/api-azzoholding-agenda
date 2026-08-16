package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCustomerType;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;

/**
 * Cobre {@code modules/nfse/application/NfsePdfRenderService.java} (Fronteira 7). Original sem
 * teste proprio no Quarkus.
 *
 * <p>Smoke test do render: nao reimplementa OpenPDF, so verifica que o documento gerado e um PDF
 * valido (assinatura {@code %PDF}), com itens preenchidos, sem itens (lista vazia/nula) e com
 * codigo de verificacao ausente (bloco de autenticidade omitido).
 */
class NfsePdfRenderServiceTest {

  private final NfsePdfRenderService service = new NfsePdfRenderService();

  @Test
  void rendeUmPdfValidoComItens() {
    NfseInvoiceEntity invoice = invoiceCompleta();
    List<NfseInvoiceItemEntity> items = List.of(itemComLinha(1), itemComLinha(2));

    byte[] pdf = service.render(invoice, items);

    assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    assertThat(pdf.length).isGreaterThan(100);
  }

  @Test
  void rendeMesmoSemItens() {
    NfseInvoiceEntity invoice = invoiceCompleta();

    byte[] pdf = service.render(invoice, null);

    assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
  }

  @Test
  void rendeMesmoSemCodigoVerificacao() {
    NfseInvoiceEntity invoice = invoiceCompleta();
    invoice.setCodigoVerificacao(null);

    byte[] pdf = service.render(invoice, List.of());

    assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
  }

  private NfseInvoiceEntity invoiceCompleta() {
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setId(UUID.randomUUID());
    invoice.setNumeroNfse("12345");
    invoice.setNumeroRps(10L);
    invoice.setSerieRps("1");
    invoice.setFiscalStatus(NfseFiscalStatus.AUTHORIZED);
    invoice.setCodigoVerificacao("ABC123");
    invoice.setProtocolo("proto-1");
    invoice.setDataEmissao(Instant.parse("2026-01-15T10:00:00Z"));
    invoice.setDataCompetencia(LocalDate.of(2026, 1, 1));
    invoice.setMunicipioCodigoIbge("3550308");
    invoice.setCustomerName("Cliente Teste");
    invoice.setCustomerType(NfseCustomerType.CPF);
    invoice.setCustomerDocument("12345678900");
    invoice.setNaturezaOperacao("Tributacao no municipio");
    invoice.setAliquotaIss(BigDecimal.valueOf(5));
    invoice.setValorIss(BigDecimal.valueOf(50));
    invoice.setValorServicos(BigDecimal.valueOf(1000));
    invoice.setValorDeducoes(BigDecimal.ZERO);
    invoice.setNotes("observacao de teste");
    return invoice;
  }

  private NfseInvoiceItemEntity itemComLinha(int linha) {
    NfseInvoiceItemEntity item = new NfseInvoiceItemEntity();
    item.setLineNumber(linha);
    item.setDescricaoServico("Servico " + linha);
    item.setItemListaServico("1.01");
    item.setQuantidade(BigDecimal.ONE);
    item.setValorUnitario(BigDecimal.valueOf(100));
    item.setValorTotal(BigDecimal.valueOf(100));
    return item;
  }
}
