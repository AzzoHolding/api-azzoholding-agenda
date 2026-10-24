package br.com.phdigitalcode.azzo.agenda.pro.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class IndicadoresDoClienteTest {

  private static LocalDate d(String iso) {
    return LocalDate.parse(iso);
  }

  @Test
  void semDuasVisitasNaoHaFrequencia() {
    assertThat(IndicadoresDoCliente.intervaloMedioEmDias(List.of())).isNull();
    assertThat(IndicadoresDoCliente.intervaloMedioEmDias(List.of(d("2026-01-10")))).isNull();
    // Duas visitas no mesmo dia sao um dia so.
    assertThat(IndicadoresDoCliente.intervaloMedioEmDias(List.of(d("2026-01-10"), d("2026-01-10")))).isNull();
  }

  @Test
  void mediaDosIntervalosEmQualquerOrdem() {
    assertThat(IndicadoresDoCliente.intervaloMedioEmDias(
        List.of(d("2026-03-02"), d("2026-01-01"), d("2026-01-31")))).isEqualTo(30);
  }

  @Test
  void soOsUltimosSeisIntervalosContam() {
    List<LocalDate> dias = new ArrayList<>();
    dias.add(d("2020-01-01")); // visita antiga, fora da media
    LocalDate dia = d("2026-01-01");
    for (int i = 0; i <= IndicadoresDoCliente.INTERVALOS_NA_MEDIA; i++) {
      dias.add(dia);
      dia = dia.plusDays(14);
    }
    assertThat(IndicadoresDoCliente.intervaloMedioEmDias(dias)).isEqualTo(14);
  }

  @Test
  void comparecimento() {
    assertThat(IndicadoresDoCliente.taxaDeComparecimento(0, 0)).isNull();
    assertThat(IndicadoresDoCliente.taxaDeComparecimento(9, 1)).isEqualTo(90);
    assertThat(IndicadoresDoCliente.taxaDeComparecimento(0, 2)).isZero();
    assertThat(IndicadoresDoCliente.taxaDeComparecimento(2, 1)).isEqualTo(67);
  }
}
