package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.security.SecureRandom;

/**
 * Codigos para cupom de lote e vale-presente (item 6): aleatorios ({@link SecureRandom}), dificeis de
 * adivinhar e faceis de ditar — sem O/0 nem I/1, que se confundem na leitura.
 */
public final class CodigoAleatorio {

  /** 32 simbolos: A-Z sem I e O, e 2-9. */
  public static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private static final SecureRandom SORTEIO = new SecureRandom();

  private CodigoAleatorio() {}

  public static String gerar(int tamanho) {
    StringBuilder codigo = new StringBuilder(tamanho);
    for (int i = 0; i < tamanho; i++) {
      codigo.append(ALFABETO.charAt(SORTEIO.nextInt(ALFABETO.length())));
    }
    return codigo.toString();
  }

  /** O codigo como a pessoa digitou: maiusculas, sem espaco nem traco no meio do vale. */
  public static String normalizar(String digitado) {
    return digitado == null ? "" : digitado.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
