package br.com.phdigitalcode.azzo.agenda.pro.util;

/**
 * Celula de CSV que vai ser aberta em planilha (Excel, LibreOffice, Google Sheets).
 *
 * <p>Pentest 14/10/2026 (FIN-020): texto livre comecando com {@code = + - @} (ou tab/CR) era
 * gravado como veio e a planilha o executa como FORMULA — {@code =HYPERLINK(...)} ou
 * {@code =cmd|...} na descricao de um lancamento vira acao no computador de quem abre a
 * exportacao (CWE-1236). Prefixar com apostrofo faz a planilha mostrar o texto, sem executar.
 */
public final class CelulaCsv {

  private CelulaCsv() {}

  public static String semFormula(String valor) {
    if (valor == null || valor.isEmpty()) return valor;
    char primeiro = valor.charAt(0);
    if (primeiro == '=' || primeiro == '+' || primeiro == '-' || primeiro == '@'
        || primeiro == '\t' || primeiro == '\r') {
      return "'" + valor;
    }
    return valor;
  }
}
