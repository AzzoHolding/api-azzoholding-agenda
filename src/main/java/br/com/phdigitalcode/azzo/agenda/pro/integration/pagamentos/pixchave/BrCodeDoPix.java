package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.pixchave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * O "copia e cola" do Pix estatico (BR Code), no padrao do Banco Central (Manual de Padroes para
 * Iniciacao do Pix, formato EMV QRCPS-MPM).
 *
 * <p>Cada campo e {@code ID (2) + tamanho (2) + valor}. O ultimo e o CRC16-CCITT (0x1021, inicio
 * 0xFFFF) de tudo antes dele, incluindo o proprio {@code 6304}. O banco da cliente le a chave, o
 * valor e o identificador (txid), mostra o nome do recebedor e manda o dinheiro direto para a conta
 * dona da chave — sem operadora no meio.
 */
public final class BrCodeDoPix {

  private BrCodeDoPix() {}

  public enum TipoDeChave { CPF, CNPJ, EMAIL, TELEFONE, ALEATORIA }

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern ALEATORIA = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

  /** A chave no formato que o Pix espera, ou uma {@link IllegalArgumentException} com a mensagem da tela. */
  public static String normalizarChave(TipoDeChave tipo, String chave) {
    if (tipo == null) throw new IllegalArgumentException("Informe o tipo da chave: CPF, CNPJ, EMAIL, TELEFONE ou ALEATORIA.");
    String c = chave == null ? "" : chave.trim();
    if (c.isEmpty()) throw new IllegalArgumentException("Informe a chave Pix.");
    switch (tipo) {
      case CPF -> {
        String d = c.replaceAll("\\D", "");
        if (!cpfValido(d)) throw new IllegalArgumentException("CPF inválido na chave Pix.");
        return d;
      }
      case CNPJ -> {
        String d = c.replaceAll("\\D", "");
        if (!cnpjValido(d)) throw new IllegalArgumentException("CNPJ inválido na chave Pix.");
        return d;
      }
      case EMAIL -> {
        String e = c.toLowerCase(Locale.ROOT);
        if (e.length() > 77 || !EMAIL.matcher(e).matches()) throw new IllegalArgumentException("E-mail inválido na chave Pix.");
        return e;
      }
      case TELEFONE -> {
        String d = c.replaceAll("\\D", "");
        if (d.startsWith("55") && (d.length() == 12 || d.length() == 13)) d = d.substring(2);
        if (d.length() != 10 && d.length() != 11) {
          throw new IllegalArgumentException("Telefone inválido na chave Pix: use DDD e número (ex.: 11 98765-4321).");
        }
        return "+55" + d;
      }
      case ALEATORIA -> {
        String a = c.toLowerCase(Locale.ROOT);
        if (!ALEATORIA.matcher(a).matches()) {
          throw new IllegalArgumentException("Chave aleatória inválida: são 32 letras e números separados por hífens, como o banco mostra.");
        }
        return a;
      }
      default -> throw new IllegalArgumentException("Tipo de chave desconhecido.");
    }
  }

  public static TipoDeChave tipo(String tipo) {
    if (tipo == null || tipo.isBlank()) return null;
    String t = semAcento(tipo).trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
    return switch (t) {
      case "CPF" -> TipoDeChave.CPF;
      case "CNPJ" -> TipoDeChave.CNPJ;
      case "EMAIL" -> TipoDeChave.EMAIL;
      case "TELEFONE", "CELULAR", "FONE" -> TipoDeChave.TELEFONE;
      case "ALEATORIA", "EVP", "CHAVEALEATORIA" -> TipoDeChave.ALEATORIA;
      default -> null;
    };
  }

  /**
   * Monta o copia-e-cola. {@code chave} ja normalizada; {@code nome} ate 25 e {@code cidade} ate 15
   * caracteres, sem acento (o padrao pede ASCII); {@code txid} ate 25 letras e numeros.
   */
  public static String montar(String chave, String nome, String cidade, BigDecimal valor, String txid) {
    String contaDoRecebedor = campo("00", "br.gov.bcb.pix") + campo("01", chave);
    StringBuilder s = new StringBuilder()
        .append(campo("00", "01"))
        .append(campo("26", contaDoRecebedor))
        .append(campo("52", "0000"))
        .append(campo("53", "986"));
    if (valor != null && valor.signum() > 0) {
      s.append(campo("54", valor.setScale(2, RoundingMode.HALF_UP).toPlainString()));
    }
    s.append(campo("58", "BR"))
        .append(campo("59", ascii(nome, 25)))
        .append(campo("60", ascii(cidade, 15)))
        .append(campo("62", campo("05", txid(txid))))
        .append("6304");
    return s + crc16(s.toString());
  }

  /** O identificador da cobranca: so letras e numeros, ate 25; sem ele, "***" (o padrao aceita). */
  static String txid(String txid) {
    String t = txid == null ? "" : txid.replaceAll("[^A-Za-z0-9]", "");
    if (t.isEmpty()) return "***";
    return t.length() > 25 ? t.substring(0, 25) : t;
  }

  static String campo(String id, String valor) {
    int tamanho = valor.length();
    if (tamanho > 99) throw new IllegalArgumentException("Campo " + id + " do Pix passa de 99 caracteres.");
    return id + String.format("%02d", tamanho) + valor;
  }

  /** Nome e cidade em ASCII simples (sem acento, sem simbolo estranho), cortados no tamanho do padrao. */
  static String ascii(String texto, int maximo) {
    String t = semAcento(texto == null ? "" : texto).replaceAll("[^A-Za-z0-9 .\\-]", "").replaceAll("\\s+", " ").trim();
    if (t.isEmpty()) throw new IllegalArgumentException("Nome e cidade do recebedor são obrigatórios no Pix.");
    return t.length() > maximo ? t.substring(0, maximo).trim() : t;
  }

  static String semAcento(String texto) {
    return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
  }

  /** CRC16-CCITT-FALSE: polinomio 0x1021, inicio 0xFFFF, 4 digitos hexadecimais maiusculos. */
  static String crc16(String dados) {
    int crc = 0xFFFF;
    for (byte b : dados.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
      crc ^= (b & 0xFF) << 8;
      for (int i = 0; i < 8; i++) {
        crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
        crc &= 0xFFFF;
      }
    }
    return String.format("%04X", crc);
  }

  static boolean cpfValido(String d) {
    if (d == null || !d.matches("\\d{11}") || d.chars().distinct().count() == 1) return false;
    for (int j = 9; j <= 10; j++) {
      int soma = 0;
      for (int i = 0; i < j; i++) soma += (d.charAt(i) - '0') * (j + 1 - i);
      int dv = (soma * 10) % 11 % 10;
      if (dv != d.charAt(j) - '0') return false;
    }
    return true;
  }

  static boolean cnpjValido(String d) {
    if (d == null || !d.matches("\\d{14}") || d.chars().distinct().count() == 1) return false;
    int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    return dv(d, pesos1) == d.charAt(12) - '0' && dv(d, pesos2) == d.charAt(13) - '0';
  }

  private static int dv(String d, int[] pesos) {
    int soma = 0;
    for (int i = 0; i < pesos.length; i++) soma += (d.charAt(i) - '0') * pesos[i];
    int resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  }
}
