package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Criterio;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Linha;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Metricas;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Regra;

class CalculoDoRankingTest {

  private static BigDecimal n(String v) {
    return new BigDecimal(v);
  }

  private static Metricas p(String nome, String faturamento, String atendimentos, String faltas) {
    Map<Criterio, BigDecimal> v = new EnumMap<>(Criterio.class);
    v.put(Criterio.FATURAMENTO_SERVICOS, n(faturamento));
    v.put(Criterio.ATENDIMENTOS_CONCLUIDOS, n(atendimentos));
    v.put(Criterio.FALTAS, n(faltas));
    return new Metricas(UUID.randomUUID(), nome, v);
  }

  private static Regra pontos(String chave, String pontos, String aCada) {
    return new Regra(chave, n(pontos), n(aCada), null);
  }

  private static Regra peso(String chave, String peso) {
    return new Regra(chave, null, null, n(peso));
  }

  @Test
  void pontosPorUnidadeSomaECastigaAsFaltas() {
    List<Regra> regras = List.of(
        pontos("FATURAMENTO_SERVICOS", "1", "100"),
        pontos("ATENDIMENTOS_CONCLUIDOS", "2", "1"),
        pontos("FALTAS", "-3", "1"));
    List<Linha> ranking = CalculoDoRanking.calcular(CalculoDoRanking.PONTOS, regras, List.of(
        p("Ana", "1000", "10", "4"),   // 10 + 20 - 12 = 18
        p("Bia", "800", "12", "0")));  // 8 + 24 - 0 = 32

    assertThat(ranking).extracting(Linha::nome).containsExactly("Bia", "Ana");
    assertThat(ranking.get(0).total()).isEqualByComparingTo("32");
    assertThat(ranking.get(1).total()).isEqualByComparingTo("18");
    assertThat(ranking.get(1).criterios().get(2).pontos()).isEqualByComparingTo("-12");
  }

  @Test
  void pesoDaNotaContraOMelhorEInverteQuandoMenorEMelhor() {
    List<Regra> regras = List.of(peso("FATURAMENTO_SERVICOS", "60"), peso("FALTAS", "40"));
    List<Linha> ranking = CalculoDoRanking.calcular(CalculoDoRanking.PESO, regras, List.of(
        p("Ana", "1000", "0", "4"),   // fat 100*0.6=60; faltas (1-4/4)=0 -> 0 => 60
        p("Bia", "500", "0", "0")));  // fat 50*0.6=30; faltas 100*0.4=40 => 70

    assertThat(ranking).extracting(Linha::nome).containsExactly("Bia", "Ana");
    assertThat(ranking.get(0).total()).isEqualByComparingTo("70");
    assertThat(ranking.get(1).total()).isEqualByComparingTo("60");
  }

  @Test
  void criterioQueNinguemPontuouDaNotaCheiaATodos() {
    List<Regra> regras = List.of(peso("FALTAS", "100"));
    List<Linha> ranking = CalculoDoRanking.calcular(CalculoDoRanking.PESO, regras, List.of(
        p("Ana", "0", "0", "0"), p("Bia", "0", "0", "0")));
    assertThat(ranking).allSatisfy(l -> assertThat(l.total()).isEqualByComparingTo("100"));
  }

  @Test
  void empateVaiParaQuemFaturouMaisDepoisPeloNome() {
    List<Regra> regras = List.of(pontos("ATENDIMENTOS_CONCLUIDOS", "1", "1"));
    List<Linha> ranking = CalculoDoRanking.calcular(CalculoDoRanking.PONTOS, regras, List.of(
        p("Carla", "100", "5", "0"), p("Ana", "100", "5", "0"), p("Bia", "900", "5", "0")));
    assertThat(ranking).extracting(Linha::nome).containsExactly("Bia", "Ana", "Carla");
    assertThat(ranking).extracting(Linha::posicao).containsExactly(1, 2, 3);
  }

  @Test
  void validacaoJuntaTodosOsProblemas() {
    assertThat(CalculoDoRanking.validar("", "PONTOS", List.of())).containsExactly(
        "Dê um nome ao ranking.", "Escolha pelo menos um critério.");
    assertThat(CalculoDoRanking.validar("Vendas", "PONTOS", List.of(
        pontos("FATURAMENTO_SERVICOS", "1", "0"), pontos("FATURAMENTO_SERVICOS", "1", "100"))))
        .anySatisfy(m -> assertThat(m).contains("maior que zero"))
        .anySatisfy(m -> assertThat(m).contains("aparece duas vezes"));
    assertThat(CalculoDoRanking.validar("Equilíbrio", "PESO", List.of(
        peso("FATURAMENTO_SERVICOS", "60"), peso("FALTAS", "30"))))
        .containsExactly("Os pesos somam 90%; precisam somar 100%.");
    assertThat(CalculoDoRanking.validar("Vendas", "PESO", List.of(peso("NAO_EXISTE", "100"))))
        .containsExactly("Critério desconhecido: NAO_EXISTE.");
    assertThat(CalculoDoRanking.validar("Ok", "PESO", List.of(
        peso("FATURAMENTO_SERVICOS", "50"), peso("ATENDIMENTOS_CONCLUIDOS", "50")))).isEmpty();
  }
}
