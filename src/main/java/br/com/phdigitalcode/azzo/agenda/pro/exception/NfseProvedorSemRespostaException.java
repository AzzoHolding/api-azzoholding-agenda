package br.com.phdigitalcode.azzo.agenda.pro.exception;

/**
 * O pedido saiu, mas nao ha resposta confiavel do provedor da NFS-e (tempo esgotado, falha de
 * rede, HTTP 5xx, 408 ou 429).
 *
 * <p>O resultado e INCERTO: a nota pode ter sido emitida do lado de la. Nunca tratar como
 * recusa e reenviar as cegas, porque isso gera nota duplicada. A nota fica {@code PENDING} ate uma
 * consulta ao provedor dizer o que aconteceu.
 */
public class NfseProvedorSemRespostaException extends IllegalStateException {

  private final String codigo;

  public NfseProvedorSemRespostaException(String codigo, String mensagem, Throwable causa) {
    super(mensagem, causa);
    this.codigo = codigo;
  }

  public NfseProvedorSemRespostaException(String codigo, String mensagem) {
    this(codigo, mensagem, null);
  }

  public String getCodigo() {
    return codigo;
  }
}
