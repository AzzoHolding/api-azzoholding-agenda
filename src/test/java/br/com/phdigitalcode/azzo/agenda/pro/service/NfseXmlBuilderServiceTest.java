package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;

/** Cobre {@code modules/nfse/application/NfseXmlBuilderService.java}. */
class NfseXmlBuilderServiceTest {

  @Test
  void deveSelecionarBuilderCompativelComProvider() {
    NfseXmlBuilderService service = new NfseXmlBuilderService(List.of(
        new FakeBuilder("ABRASF", "<abrasf/>"),
        new FakeBuilder("SEFIN_NACIONAL", "<sefin/>")));
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setProvedor("SEFIN_NACIONAL");

    String xml = service.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).isEqualTo("<sefin/>");
  }

  @Test
  void deveFalharQuandoNaoHouverBuilderCompativel() {
    NfseXmlBuilderService service = new NfseXmlBuilderService(List.of(new FakeBuilder("ABRASF", "<abrasf/>")));
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setProvedor("DESCONHECIDO");

    assertThatThrownBy(() -> service.buildAndValidateAuthorizationXml(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_NOT_SUPPORTED");
  }

  @Test
  void delegaBuildAuthorizationReturnXmlEBuildCancelReturnXmlAoBuilderResolvido() {
    NfseXmlBuilderService service = new NfseXmlBuilderService(List.of(new FakeBuilder("ABRASF", "<abrasf/>")));
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setProvedor("ABRASF");

    assertThat(service.buildAuthorizationReturnXml(invoice, "OK", "msg")).isEqualTo("<abrasf/>");
    assertThat(service.buildCancelReturnXml(invoice, "OK", "msg")).isEqualTo("<abrasf/>");
  }

  private static final class FakeBuilder implements NfseXmlLayoutBuilder {
    private final String providerCode;
    private final String xml;

    private FakeBuilder(String providerCode, String xml) {
      this.providerCode = providerCode;
      this.xml = xml;
    }

    @Override
    public boolean supports(String providerCode) {
      return this.providerCode.equalsIgnoreCase(providerCode);
    }

    @Override
    public String buildAndValidateAuthorizationXml(NfseInvoiceEntity invoice) {
      return xml;
    }

    @Override
    public String buildAuthorizationReturnXml(NfseInvoiceEntity invoice, String providerCode, String providerMessage) {
      return xml;
    }

    @Override
    public String buildCancelReturnXml(NfseInvoiceEntity invoice, String providerCode, String providerMessage) {
      return xml;
    }
  }
}
