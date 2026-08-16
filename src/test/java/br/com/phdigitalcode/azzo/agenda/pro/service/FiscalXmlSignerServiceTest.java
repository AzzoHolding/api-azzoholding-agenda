package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Cobre {@code modules/fiscal/application/FiscalXmlSignerService.java}. */
class FiscalXmlSignerServiceTest {

  private final FiscalXmlSignerService service = new FiscalXmlSignerService();

  @Test
  void assinaOXmlEEnvelopaComDigestSha256() {
    String assinado = service.sign("<fiscalInvoice><id>1</id></fiscalInvoice>", "senha-123");

    assertThat(assinado).startsWith("<signedFiscalInvoice>");
    assertThat(assinado).contains("<payload><![CDATA[<fiscalInvoice><id>1</id></fiscalInvoice>]]></payload>");
    assertThat(assinado).contains("<signature algorithm=\"SHA-256\">");
    assertThat(assinado).endsWith("</signedFiscalInvoice>");
  }

  @Test
  void oDigestEDeterministicoParaOMesmoXmlESenha() {
    String primeira = service.sign("<a/>", "senha");
    String segunda = service.sign("<a/>", "senha");

    assertThat(primeira).isEqualTo(segunda);
  }

  @Test
  void senhasDiferentesProduzemDigestsDiferentes() {
    String comSenhaA = service.sign("<a/>", "senha-a");
    String comSenhaB = service.sign("<a/>", "senha-b");

    assertThat(comSenhaA).isNotEqualTo(comSenhaB);
  }

  @Test
  void xmlNuloOuVazioLancaIllegalArgument() {
    assertThatThrownBy(() -> service.sign(null, "senha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("XML fiscal obrigatorio para assinatura.");
    assertThatThrownBy(() -> service.sign("   ", "senha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("XML fiscal obrigatorio para assinatura.");
  }

  @Test
  void senhaNulaOuVaziaLancaIllegalArgument() {
    assertThatThrownBy(() -> service.sign("<a/>", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Senha do certificado obrigatoria para assinatura fiscal.");
    assertThatThrownBy(() -> service.sign("<a/>", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Senha do certificado obrigatoria para assinatura fiscal.");
  }
}
