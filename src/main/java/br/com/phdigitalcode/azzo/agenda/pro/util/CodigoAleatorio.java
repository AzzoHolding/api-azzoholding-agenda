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

  /** Tamanho do prefixo do salao no inicio dos codigos gerados. */
  public static final int TAMANHO_DO_PREFIXO_DO_SALAO = 6;

  /**
   * Os 6 digitos do salao no inicio dos codigos gerados (pedido do dono, 17/11): um hash SHA-256 do CPF/CNPJ (so os
   * numeros), para o codigo de um salao nao se misturar com o de outro. Sem documento, o hash e do id do salao. O
   * hash nao deixa chegar ao documento: so 6 digitos, e muitos documentos dao os mesmos 6.
   */
  public static String prefixoDoSalao(String documento, java.util.UUID tenantId) {
    String digitos = documento == null ? "" : documento.replaceAll("\\D", "");
    String base = digitos.isEmpty() ? "salao:" + tenantId : digitos;
    try {
      byte[] h = java.security.MessageDigest.getInstance("SHA-256")
          .digest(base.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      long n = 0;
      for (int i = 0; i < 6; i++) n = (n << 8) | (h[i] & 0xffL);
      return String.format("%06d", n % 1_000_000L);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("Algoritmo SHA-256 indisponível.", e);
    }
  }

  /** O codigo digitado parece gerado por outro salao: comeca com 6 digitos que nao sao os deste. */
  public static boolean pareceDeOutroSalao(String codigoNormalizado, String prefixoDoSalao) {
    if (codigoNormalizado == null || prefixoDoSalao == null) return false;
    String c = codigoNormalizado.replace("-", "").replace(" ", "");
    return c.length() > TAMANHO_DO_PREFIXO_DO_SALAO
        && c.substring(0, TAMANHO_DO_PREFIXO_DO_SALAO).chars().allMatch(Character::isDigit)
        && !c.startsWith(prefixoDoSalao);
  }

  /** O codigo como a pessoa digitou: maiusculas, sem espaco nem traco no meio do vale. */
  public static String normalizar(String digitado) {
    return digitado == null ? "" : digitado.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
