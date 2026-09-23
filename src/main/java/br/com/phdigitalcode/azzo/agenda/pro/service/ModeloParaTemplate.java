package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte o texto que o salao escreve na tela para o formato que a Meta aprova.
 *
 * <p>O dono escreve <i>"Olá {cliente}! Seu {servico} está confirmado para {data} às {hora}."</i> —
 * e a Meta exige {@code {{1}}, {{2}}...} numerados, mais um exemplo de cada. A conversao guarda a
 * ORDEM das variaveis, porque e ela que liga {@code {{1}}} ao nome do cliente na hora do envio:
 * trocar duas posicoes manda o servico no lugar do nome, e a Meta aceita sem reclamar.
 *
 * <p>Variavel repetida usa o MESMO numero: escrever o nome duas vezes nao pode pedir dois valores.
 */
public final class ModeloParaTemplate {

  /** O que o salao pode usar no texto. Fora desta lista, fica como escrito. */
  public static final Map<String, String> VARIAVEIS_CONHECIDAS = Map.of(
      "cliente", "Marina",
      "servico", "Corte feminino",
      "profissional", "Bruna",
      "data", "23/09/2026",
      "hora", "14:30",
      "salao", "Studio Bela");

  private static final Pattern MARCADOR = Pattern.compile("[{]([a-zA-Z_]+)[}]");

  private ModeloParaTemplate() {}

  public record Convertido(String corpo, List<String> variaveis, List<String> exemplos) {}

  /**
   * @param texto o modelo escrito pelo salao
   * @return corpo com {@code {{n}}}, as variaveis em ordem e um exemplo para cada
   */
  public static Convertido converter(String texto) {
    if (texto == null || texto.isBlank()) {
      throw new IllegalArgumentException("Modelo de mensagem vazio: nao ha o que aprovar na Meta.");
    }

    Map<String, Integer> posicaoDaVariavel = new LinkedHashMap<>();
    StringBuilder corpo = new StringBuilder();
    Matcher matcher = MARCADOR.matcher(texto);
    int fim = 0;

    while (matcher.find()) {
      String nome = matcher.group(1).toLowerCase();
      corpo.append(texto, fim, matcher.start());
      if (VARIAVEIS_CONHECIDAS.containsKey(nome)) {
        // Repetida reaproveita o numero: dois "{cliente}" sao a mesma pergunta, nao duas.
        int posicao = posicaoDaVariavel.computeIfAbsent(nome, chave -> posicaoDaVariavel.size() + 1);
        corpo.append("{{").append(posicao).append("}}");
      } else {
        // Desconhecida fica literal: melhor a Meta recusar um texto estranho do que o sistema
        // inventar uma variavel que ninguem sabe preencher no envio.
        corpo.append(matcher.group(0));
      }
      fim = matcher.end();
    }
    corpo.append(texto.substring(fim));

    List<String> variaveis = new ArrayList<>(posicaoDaVariavel.keySet());
    List<String> exemplos = variaveis.stream().map(VARIAVEIS_CONHECIDAS::get).toList();
    return new Convertido(corpo.toString(), variaveis, exemplos);
  }
}
