package br.com.phdigitalcode.azzo.agenda.pro.exception;

/**
 * O provedor da NFS-e (Receita Nacional ou prefeitura) RESPONDEU e recusou o pedido.
 *
 * <p>E um resultado definitivo: nada foi emitido ou cancelado, e o motivo vem do provedor. A nota
 * vai para {@code REJECTED} (ou {@code CANCEL_REJECTED}) com o codigo e a mensagem gravados no
 * evento, para o salao saber o que corrigir.
 */
public class NfseProvedorRecusouException extends IllegalStateException {

  private final String codigo;
  private final String motivo;

  public NfseProvedorRecusouException(String codigo, String motivo) {
    super("NFSE_PROVEDOR_RECUSOU: " + motivo);
    this.codigo = codigo;
    this.motivo = motivo;
  }

  public String getCodigo() {
    return codigo;
  }

  public String getMotivo() {
    return motivo;
  }
}
