package br.com.phdigitalcode.azzo.agenda.pro.util;

/**
 * Telefone brasileiro: DDD + numero, com 10 (fixo) ou 11 (celular) digitos, ou os mesmos com o 55 na
 * frente (12 ou 13), que e como o WhatsApp grava.
 *
 * <p>Ate 21/10/2026 a API aceitava qualquer sequencia: "2198759961399" (13 digitos, sem o 55) foi
 * salvo como telefone de cliente — nao e celular nenhum e nao recebe WhatsApp (teste ponta a ponta).
 */
public final class TelefoneBr {

  public static final String MENSAGEM_INVALIDO =
      "Telefone inválido: informe DDD e número, com 10 ou 11 dígitos.";

  private TelefoneBr() {}

  public static boolean valido(String digitos) {
    if (digitos == null) return false;
    int tamanho = digitos.length();
    if (tamanho == 10 || tamanho == 11) return true;
    return (tamanho == 12 || tamanho == 13) && digitos.startsWith("55");
  }

  /**
   * Os digitos do telefone, ou {@code null} quando vazio (o campo e opcional).
   *
   * @throws IllegalArgumentException quando informado e nao e um telefone valido
   */
  public static String normalizarOuFalhar(String telefone) {
    if (telefone == null) return null;
    String digitos = telefone.replaceAll("\\D", "");
    if (digitos.isEmpty()) return null;
    if (!valido(digitos)) throw new IllegalArgumentException(MENSAGEM_INVALIDO);
    return digitos;
  }

  /**
   * Como {@link #normalizarOuFalhar}, mas so valida quando o telefone MUDOU em relacao ao gravado.
   *
   * <p>Numeros que ja estao no banco por outro caminho — o WhatsApp grava o de um cliente
   * estrangeiro como chegou — nao podem travar a edicao do resto do cadastro.
   */
  public static String normalizarSeMudou(String telefone, String gravado) {
    String digitos = telefone == null ? null : telefone.replaceAll("\\D", "");
    if (digitos != null && !digitos.isEmpty() && gravado != null
        && digitos.equals(gravado.replaceAll("\\D", ""))) {
      return digitos;
    }
    return normalizarOuFalhar(telefone);
  }
}
