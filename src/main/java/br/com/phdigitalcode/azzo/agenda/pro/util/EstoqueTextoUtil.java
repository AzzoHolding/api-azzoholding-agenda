package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.http.HttpStatus;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;

/**
 * Porte dos normalizadores privados de {@code modules/inventory/application/ServicoEstoque.java}
 * ({@code normalizarTextoBase}, {@code normalizarCodigoOpcional},
 * {@code normalizarCodigoObrigatorio}, {@code normalizarTextoLivreObrigatorio}).
 *
 * <p>No original eles vivem dentro do {@code ServicoEstoque}, que tambem contem o motor de
 * movimentacao. Aqui esses dois papeis estao em classes separadas ({@code ServicoEstoque} e
 * {@code EstoqueMovimentacaoService}) e as duas precisam das mesmas regras — extrair evita a copia.
 *
 * <p>Atencao a diferenca em relacao a {@link SlugUtil}: aqui a forma e <b>NFKC</b> e os acentos
 * sao <b>preservados</b>; o que se remove sao caracteres de controle ({@code \p{C}}) e espacos
 * repetidos.
 */
public final class EstoqueTextoUtil {

  private EstoqueTextoUtil() {}

  /** Devolve {@code null} quando o texto fica vazio apos a normalizacao, como no original. */
  public static String normalizarTextoBase(String value) {
    if (value == null) return null;
    String normalized =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replaceAll("\\p{C}", "")
            .replaceAll("\\s+", " ")
            .trim();
    return normalized.isEmpty() ? null : normalized;
  }

  /** Normaliza e sobe para maiuscula (SKU, unidade de medida, codigos de enum). */
  public static String normalizarCodigoOpcional(String value) {
    String normalized = normalizarTextoBase(value);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
  }

  public static String normalizarCodigoObrigatorio(String value, String errorMessage) {
    String normalized = normalizarCodigoOpcional(value);
    if (normalized == null) {
      throw new ApiClientErrorException(errorMessage, HttpStatus.BAD_REQUEST.value());
    }
    return normalized;
  }

  /** Texto livre (nome, motivo): normaliza mas <b>nao</b> muda a caixa. */
  public static String normalizarTextoLivreObrigatorio(String value, String errorMessage) {
    String normalized = normalizarTextoBase(value);
    if (normalized == null) {
      throw new ApiClientErrorException(errorMessage, HttpStatus.BAD_REQUEST.value());
    }
    return normalized;
  }
}
