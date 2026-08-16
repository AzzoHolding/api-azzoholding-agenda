package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Cobre {@code modules/publicbooking/application/PublicBookingUrlService.java}: montagem e
 * validacao da URL publica de agendamento (esquema/host obrigatorios, barra final removida,
 * localhost bloqueado em producao).
 */
class PublicBookingUrlServiceTest {

  @Test
  void montaUrlComSlugNormalizado() {
    PublicBookingUrlService service = new PublicBookingUrlService("http://localhost:5173", "dev");

    assertThat(service.buildPublicBookingUrl("  salao-qa  ")).isEqualTo("http://localhost:5173/agendar/salao-qa");
  }

  @Test
  void removeBarraFinalDaBaseUrl() {
    PublicBookingUrlService service = new PublicBookingUrlService("https://agenda.exemplo.com/", "dev");

    assertThat(service.buildPublicBookingUrl("salao-qa")).isEqualTo("https://agenda.exemplo.com/agendar/salao-qa");
  }

  @Test
  void falhaQuandoSlugEhNuloOuEmBranco() {
    PublicBookingUrlService service = new PublicBookingUrlService("http://localhost:5173", "dev");

    assertThatThrownBy(() -> service.buildPublicBookingUrl(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.buildPublicBookingUrl("   ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void falhaQuandoBaseUrlNaoConfigurada() {
    PublicBookingUrlService blank = new PublicBookingUrlService("", "dev");
    PublicBookingUrlService unset = new PublicBookingUrlService("__unset__", "prod");

    assertThatThrownBy(() -> blank.buildPublicBookingUrl("salao")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> unset.buildPublicBookingUrl("salao")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void falhaQuandoBaseUrlNaoUsaHttpOuHttps() {
    PublicBookingUrlService service = new PublicBookingUrlService("ftp://exemplo.com", "dev");

    assertThatThrownBy(() -> service.buildPublicBookingUrl("salao"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("http/https");
  }

  @Test
  void falhaQuandoBaseUrlSemHost() {
    PublicBookingUrlService service = new PublicBookingUrlService("http://", "dev");

    assertThatThrownBy(() -> service.buildPublicBookingUrl("salao"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("host");
  }

  @Test
  void falhaQuandoLocalhostEmProducao() {
    PublicBookingUrlService service = new PublicBookingUrlService("http://localhost:5173", "prod");

    assertThatThrownBy(() -> service.buildPublicBookingUrl("salao"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("localhost");
  }

  @Test
  void permiteLocalhostForaDeProducao() {
    PublicBookingUrlService service = new PublicBookingUrlService("http://127.0.0.1:5173", "dev");

    assertThat(service.buildPublicBookingUrl("salao")).isEqualTo("http://127.0.0.1:5173/agendar/salao");
  }

  @Test
  void perfilComProdEmListaTambemBloqueiaLocalhost() {
    // "spring.profiles.active" pode vir como lista (ex.: "prod,eu-west"); o contains cobre isso.
    PublicBookingUrlService service = new PublicBookingUrlService("http://localhost:5173", "eu-west,prod");

    assertThatThrownBy(() -> service.buildPublicBookingUrl("salao")).isInstanceOf(IllegalStateException.class);
  }
}
