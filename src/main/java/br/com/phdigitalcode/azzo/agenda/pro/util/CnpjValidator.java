package br.com.phdigitalcode.azzo.agenda.pro.util;

/** Porte verbatim de {@code shared/util/CnpjValidator.java}. */
public final class CnpjValidator {

  private CnpjValidator() {}

  public static String sanitize(String cnpj) {
    if (cnpj == null) return null;
    return cnpj.replaceAll("[^0-9]", "");
  }

  public static boolean isValid(String cnpj) {
    String digits = sanitize(cnpj);
    if (digits == null || digits.length() != 14) return false;
    if (digits.chars().distinct().count() == 1) return false;

    int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    int sum = 0;
    for (int i = 0; i < 12; i++) sum += (digits.charAt(i) - '0') * weights1[i];
    int d1 = sum % 11 < 2 ? 0 : 11 - (sum % 11);
    if (d1 != (digits.charAt(12) - '0')) return false;

    sum = 0;
    for (int i = 0; i < 13; i++) sum += (digits.charAt(i) - '0') * weights2[i];
    int d2 = sum % 11 < 2 ? 0 : 11 - (sum % 11);
    return d2 == (digits.charAt(13) - '0');
  }

  /** Retorna CNPJ mascarado para logs: primeiros 6 digitos + mascara. */
  public static String mask(String cnpj) {
    String digits = sanitize(cnpj);
    if (digits == null || digits.length() < 6) return "****";
    return digits.substring(0, 6) + "****/****.--";
  }

  /** Formata CNPJ no padrao visual: 00.000.000/0001-00 */
  public static String format(String cnpj) {
    String digits = sanitize(cnpj);
    if (digits == null || digits.length() != 14) return cnpj;
    return digits.substring(0, 2) + "." +
        digits.substring(2, 5) + "." +
        digits.substring(5, 8) + "/" +
        digits.substring(8, 12) + "-" +
        digits.substring(12, 14);
  }
}
