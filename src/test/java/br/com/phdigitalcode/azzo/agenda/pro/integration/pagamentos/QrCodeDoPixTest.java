package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class QrCodeDoPixTest {

  @Test
  void payloadViraSvgEmDataUriEVazioViraNulo() {
    String uri = QrCodeDoPix.dataUri("00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D");
    assertThat(uri).startsWith("data:image/svg+xml;base64,");
    String svg = new String(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)), StandardCharsets.UTF_8);
    assertThat(svg).startsWith("<svg").contains("<path");
    assertThat(QrCodeDoPix.dataUri(null)).isNull();
    assertThat(QrCodeDoPix.dataUri("  ")).isNull();
  }
}
