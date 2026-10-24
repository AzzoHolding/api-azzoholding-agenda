package br.com.phdigitalcode.azzo.agenda.pro.exception;

/**
 * Os DOIS provedores (CNPJ.ws e BrasilAPI) responderam 404: o CNPJ nao existe na base da Receita.
 * E diferente de "provedor fora do ar" ({@link CnpjApiIndisponivelException}, 503): aqui o dado e
 * invalido, entao e erro do pedido (400, via {@link IllegalArgumentException}).
 */
public class CnpjNaoEncontradoException extends IllegalArgumentException {

  public CnpjNaoEncontradoException() {
    super("CNPJ nao encontrado na Receita Federal. Confira os numeros.");
  }
}
