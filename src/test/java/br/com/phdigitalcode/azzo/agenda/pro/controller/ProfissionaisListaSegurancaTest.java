package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;

/** Pentest 14/10/2026: AGD-016 — a lista entregava comissao e contato de toda a equipe ao PROFESSIONAL. */
class ProfissionaisListaSegurancaTest {

  private static ProfissionalResponse profissional(UUID userId, String nome) {
    ProfissionalResponse p = new ProfissionalResponse();
    p.userId = userId == null ? null : userId.toString();
    p.name = nome;
    p.email = nome.toLowerCase() + "@salao.test";
    p.phone = "21999990000";
    p.commissionRate = new BigDecimal("40");
    return p;
  }

  @Test
  void profissionalVeOsPropriosDadosEOsDosColegasSemComissaoNemContato() {
    UUID eu = UUID.randomUUID();
    ProfissionalResponse meu = profissional(eu, "Bruna");
    ProfissionalResponse colega = profissional(UUID.randomUUID(), "Carla");
    ProfissionalResponse semLogin = profissional(null, "Dani");

    ProfissionaisController.semDadosDosColegas(List.of(meu, colega, semLogin), eu);

    assertThat(meu.commissionRate).isEqualByComparingTo("40");
    assertThat(meu.email).isNotNull();
    for (ProfissionalResponse outro : List.of(colega, semLogin)) {
      assertThat(outro.name).isNotNull();
      assertThat(outro.commissionRate).isNull();
      assertThat(outro.email).isNull();
      assertThat(outro.phone).isNull();
    }
  }
}
