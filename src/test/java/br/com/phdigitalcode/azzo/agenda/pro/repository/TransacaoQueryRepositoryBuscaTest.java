package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository.TransacaoFilter;

class TransacaoQueryRepositoryBuscaTest {

  @Test
  @DisplayName("Curingas digitados na busca viram texto: 50% procura \"50%\", e nao \"50 e qualquer coisa\"")
  void escapaCuringasDoLike() {
    assertThat(TransacaoQueryRepository.escaparLike("50%")).isEqualTo("50\\%");
    assertThat(TransacaoQueryRepository.escaparLike("a_b")).isEqualTo("a\\_b");
    assertThat(TransacaoQueryRepository.escaparLike("c:\\x")).isEqualTo("c:\\\\x");
    assertThat(TransacaoQueryRepository.escaparLike("corte")).isEqualTo("corte");
  }

  @Test
  @DisplayName("A contagem das abas tira so o filtro de conciliacao e mantem os demais, inclusive a busca")
  void contagemIgnoraSoAConciliacao() {
    UUID tenant = UUID.randomUUID();
    TransacaoFilter filtro =
        new TransacaoFilter(tenant, null, null, null, null, null, null, true, "corte");

    TransacaoFilter semAba = filtro.semConciliacao();

    assertThat(semAba.reconciled()).isNull();
    assertThat(semAba.q()).isEqualTo("corte");
    assertThat(semAba.tenantId()).isEqualTo(tenant);
  }
}
