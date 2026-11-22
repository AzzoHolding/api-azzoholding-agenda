package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;

class LiberacaoDePagamentosTest {

  private final UUID piloto = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private final UUID outro = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Test
  void vazioNaoLiberaNinguem() {
    LiberacaoDePagamentos l = new LiberacaoDePagamentos("");
    assertThat(l.liberado(piloto)).isFalse();
    assertThatThrownBy(() -> l.exigir(piloto)).isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("ainda não estão liberados");
  }

  @Test
  void soOsSaloesDaLista() {
    LiberacaoDePagamentos l = new LiberacaoDePagamentos(" " + piloto + " , nao-e-uuid,");
    assertThat(l.liberado(piloto)).isTrue();
    assertThat(l.liberado(outro)).isFalse();
    assertThat(l.liberado(null)).isFalse();
  }

  @Test
  void asteriscoLiberaTodos() {
    LiberacaoDePagamentos l = new LiberacaoDePagamentos("*");
    assertThat(l.liberado(outro)).isTrue();
  }
}
