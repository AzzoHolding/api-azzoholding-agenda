package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Texto em minusculas e sem acento, para comparar mensagens.
 *
 * <p>As mensagens de erro ganharam acento em 23/10/2026. Regra que reconhece um erro pelo texto
 * ("credenciais invalidas", "nao configurado") compara por aqui, e vale para as duas grafias.
 */
public final class TextoSemAcento {

  private TextoSemAcento() {}

  public static String de(String texto) {
    if (texto == null) return "";
    return Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}
