package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Regras puras da area da cliente (PLANO-AREA-DA-CLIENTE.md). */
class AreaDaClienteRegrasTest {

  @Test
  void telefoneCanonicoJuntaAsFormasComESem55() {
    assertThat(ContasDaCliente.telefoneCanonico("(21) 99876-5432")).isEqualTo("5521998765432");
    assertThat(ContasDaCliente.telefoneCanonico("5521998765432")).isEqualTo("5521998765432");
    assertThat(ContasDaCliente.telefoneCanonico("2133334444")).isEqualTo("552133334444");
    assertThat(ContasDaCliente.telefoneCanonico("552133334444")).isEqualTo("552133334444");
    // Estrangeiro (WhatsApp grava como chegou): fica como esta.
    assertThat(ContasDaCliente.telefoneCanonico("351912345678")).isEqualTo("351912345678");
    assertThat(ContasDaCliente.telefoneCanonico("  ")).isNull();
    assertThat(ContasDaCliente.telefoneCanonico(null)).isNull();
  }

  @Test
  void cpfAparecePelaMetadeNaConta() {
    assertThat(ContasDaCliente.cpfMascarado("52998224725")).isEqualTo("***.982.247-**");
    assertThat(ContasDaCliente.cpfMascarado(null)).isNull();
  }

  @Test
  void emailEConferidoENormalizado() {
    assertThat(ContasDaCliente.emailLimpo("  Maria@Exemplo.COM ")).isEqualTo("maria@exemplo.com");
    assertThat(ContasDaCliente.emailLimpo("")).isNull();
    assertThatThrownBy(() -> ContasDaCliente.emailLimpo("maria@")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exameAceitaPdfComoVeioERecusaOutrosArquivos() {
    byte[] pdf = "%PDF-1.4 conteudo".getBytes(StandardCharsets.US_ASCII);
    assertThat(ExamesDaCliente.ehPdf(pdf)).isTrue();
    ArquivosProtegidos.Imagem i = ExamesDaCliente.preparar(pdf);
    assertThat(i.contentType()).isEqualTo("application/pdf");
    assertThat(i.bytes()).isEqualTo(pdf);
    assertThatThrownBy(() -> ExamesDaCliente.preparar("<html>".getBytes(StandardCharsets.US_ASCII)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PDF");
    assertThatThrownBy(() -> ExamesDaCliente.preparar(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExamesDaCliente.preparar(new byte[ExamesDaCliente.MAXIMO + 1])).hasMessageContaining("10 MB");
  }

  @Test
  void nomeDoArquivoPerdeCaracteresDeCaminho() {
    assertThat(ExamesDaCliente.nomeLimpo("../../etc/passwd")).isEqualTo(".._.._etc_passwd");
    assertThat(ExamesDaCliente.nomeLimpo("exame de sangue.pdf")).isEqualTo("exame de sangue.pdf");
  }

  @Test
  void conviteValeSeteDias() {
    assertThat(LinksDaCliente.VALIDADE.toDays()).isEqualTo(7);
    assertThat(ContasDaCliente.SESSAO.toDays()).isEqualTo(30);
  }
}
