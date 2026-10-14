package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Pentest 14/10/2026: SEG-021 — o tipo do logo vinha da extensao quando o conteudo nao era reconhecido. */
class SalonControllerLogoTipoTest {

  private static byte[] bytes(int... v) {
    byte[] b = new byte[v.length];
    for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
    return b;
  }

  @Test
  void reconheceAsTresImagensPelaAssinatura() {
    assertThat(SalonController.detectarContentType(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A), "x.png"))
        .isEqualTo("image/png");
    assertThat(SalonController.detectarContentType(bytes(0xFF, 0xD8, 0xFF, 0xE0), "x.jpg")).isEqualTo("image/jpeg");
    assertThat(SalonController.detectarContentType(
        bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'), "x.webp")).isEqualTo("image/webp");
  }

  @Test
  void htmlOuSvgComNomeDeImagemNaoPassa() {
    byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
    byte[] svg = "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8);
    assertThat(SalonController.detectarContentType(html, "logo.webp")).isEqualTo("application/octet-stream");
    assertThat(SalonController.detectarContentType(svg, "logo.png")).isEqualTo("application/octet-stream");
    assertThat(SalonController.detectarContentType(null, "logo.jpg")).isEqualTo("application/octet-stream");
  }
}
