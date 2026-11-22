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

  // ─── Painel, busca e chat (20/11/2026) ──────────────────────────────────────

  @Test
  void serieMensalTemDozeMesesComZeroOndeNaoHaAtendimento() {
    java.util.Map<String, long[]> porMes = new java.util.HashMap<>();
    porMes.put("2026-11", new long[] {3});
    java.util.Map<String, java.math.BigDecimal> valores = java.util.Map.of("2026-11", new java.math.BigDecimal("150.00"));
    var serie = PainelDaCliente.serieMensal(porMes, new java.math.BigDecimal[] {java.math.BigDecimal.ZERO}, valores,
        java.time.YearMonth.of(2026, 11));
    assertThat(serie).hasSize(12);
    assertThat(serie.get(0).get("mes")).isEqualTo("2025-12");
    assertThat(serie.get(11).get("mes")).isEqualTo("2026-11");
    assertThat(serie.get(11).get("atendimentos")).isEqualTo(3L);
    assertThat(serie.get(10).get("atendimentos")).isEqualTo(0L);
  }

  @Test
  void retornoEsperadoEAUltimaVisitaMaisOIntervalo() {
    assertThat(PainelDaCliente.retornoEsperado(java.time.LocalDate.of(2026, 11, 1), 30)).isEqualTo(java.time.LocalDate.of(2026, 12, 1));
    assertThat(PainelDaCliente.retornoEsperado(java.time.LocalDate.of(2026, 11, 1), null)).isNull();
  }

  @Test
  void buscaTiraOsCuringasDaPessoa() {
    assertThat(AgendaDaCliente.padrao("  escova  ")).isEqualTo("%escova%");
    assertThat(AgendaDaCliente.padrao("100%_")).isEqualTo("%100 %");
    assertThat(AgendaDaCliente.padrao("   ")).isNull();
  }

  @Test
  void telefoneDaContaVaiParaOCadastroSemO55() {
    assertThat(AgendaDaCliente.telefoneParaCadastro("5521998765432")).isEqualTo("21998765432");
    assertThat(AgendaDaCliente.telefoneParaCadastro("351912345678")).isEqualTo("351912345678");
  }

  @Test
  void mensagemDaAreaTemTextoEAteDoisMilCaracteres() {
    assertThat(ChatDaArea.textoLimpo("  oi  ")).isEqualTo("oi");
    assertThatThrownBy(() -> ChatDaArea.textoLimpo("   ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ChatDaArea.textoLimpo("x".repeat(2001))).hasMessageContaining("2000");
  }
}
