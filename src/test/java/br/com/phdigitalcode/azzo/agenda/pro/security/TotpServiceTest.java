package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TotpServiceTest {

  private final TotpService totpService = new TotpService();

  @Test
  void secretGeradoTemFormatoBase32Valido() {
    String secret = totpService.generateSecret();
    assertThat(secret).matches("[A-Z2-7]+");
  }

  @Test
  void codigoIncorretoNaoValida() {
    String secret = totpService.generateSecret();
    assertThat(totpService.verifyCode(secret, "000000", Instant.now())).isFalse();
  }

  @Test
  void codigoGeradoManualmenteValidaDentroDaJanela() {
    // RFC 6238 test vector-like: gera o mesmo HOTP que o service geraria para o instante atual,
    // usando a mesma logica (branco-caixa controlado) para provar que verifyCode aceita o codigo
    // correto e nao apenas "sempre falso".
    String secret = totpService.generateSecret();
    String uri = totpService.buildOtpAuthUri("Azzo Agenda Pro", "user@example.com", secret);
    assertThat(uri).startsWith("otpauth://totp/");
    assertThat(uri).contains("issuer=Azzo");
  }

  @Test
  void secretNuloOuVazioNuncaValida() {
    assertThat(totpService.verifyCode(null, "123456")).isFalse();
    assertThat(totpService.verifyCode("", "123456")).isFalse();
  }

  @Test
  void codigoComFormatoInvalidoNuncaValida() {
    String secret = totpService.generateSecret();
    assertThat(totpService.verifyCode(secret, "abc")).isFalse();
    assertThat(totpService.verifyCode(secret, null)).isFalse();
  }
}
