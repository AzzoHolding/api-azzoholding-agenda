package br.com.phdigitalcode.azzo.agenda.pro.util;

/**
 * Valida CPF e CNPJ pelo digito verificador. Ate 02/10/2026 so a tela conferia: a API aceitava
 * qualquer sequencia de 11 ou 14 digitos, e o documento do cadastro e a chave do periodo gratuito.
 */
public final class DocumentoValidator {

  private DocumentoValidator() {}

  public static String digitos(String valor) {
    return valor == null ? null : valor.replaceAll("\\D", "");
  }

  /** CPF (11 digitos) ou CNPJ (14) com digito verificador correto. */
  public static boolean cpfOuCnpjValido(String valor) {
    String d = digitos(valor);
    if (d == null) return false;
    if (d.length() == 11) return cpfValido(d);
    if (d.length() == 14) return CnpjValidator.isValid(d);
    return false;
  }

  public static boolean cpfValido(String valor) {
    String d = digitos(valor);
    if (d == null || d.length() != 11) return false;
    if (d.chars().distinct().count() == 1) return false;
    return d.charAt(9) - '0' == digitoDoCpf(d, 9) && d.charAt(10) - '0' == digitoDoCpf(d, 10);
  }

  private static int digitoDoCpf(String d, int quantos) {
    int soma = 0;
    for (int i = 0; i < quantos; i++) soma += (d.charAt(i) - '0') * (quantos + 1 - i);
    int resto = (soma * 10) % 11;
    return resto == 10 ? 0 : resto;
  }
}
