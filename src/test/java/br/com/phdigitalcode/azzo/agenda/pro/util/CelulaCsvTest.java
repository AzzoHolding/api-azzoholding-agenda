package br.com.phdigitalcode.azzo.agenda.pro.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pentest 14/10/2026: FIN-020 — injecao de formula na exportacao CSV (CWE-1236). */
class CelulaCsvTest {

  @Test
  void inicioDeFormulaViraTexto() {
    assertThat(CelulaCsv.semFormula("=HYPERLINK(\"http://x\",\"clique\")")).startsWith("'=");
    assertThat(CelulaCsv.semFormula("+5521999")).isEqualTo("'+5521999");
    assertThat(CelulaCsv.semFormula("-2+3")).isEqualTo("'-2+3");
    assertThat(CelulaCsv.semFormula("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    assertThat(CelulaCsv.semFormula("\tcmd")).isEqualTo("'\tcmd");
  }

  @Test
  void textoComumNaoMuda() {
    assertThat(CelulaCsv.semFormula("Corte feminino")).isEqualTo("Corte feminino");
    assertThat(CelulaCsv.semFormula("a=b")).isEqualTo("a=b");
    assertThat(CelulaCsv.semFormula("")).isEmpty();
    assertThat(CelulaCsv.semFormula(null)).isNull();
  }
}
