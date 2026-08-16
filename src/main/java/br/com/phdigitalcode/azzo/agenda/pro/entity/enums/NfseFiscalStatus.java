package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

/**
 * Espelha {@code modules/nfse/domain/nfse/NfseFiscalStatus.java}. Grafo de transicoes em
 * {@code service.NfseFiscalStateMachine}.
 *
 * <p><b>{@code CANCEL_REJECTED} e aparentemente inalcancavel na pratica</b> (assimetria do
 * original, preservada de proposito): {@code NfseService.cancelar()} so persiste {@code CANCELLED}
 * em caso de sucesso; se o provider lanca excecao, o rollback da transacao devolve a invoice para
 * {@code AUTHORIZED} sem nunca gravar {@code CANCEL_REJECTED} em disco. Nao "consertar" ao portar a
 * Fronteira 6 (orquestracao) — sinalizar apenas.
 */
public enum NfseFiscalStatus {
  DRAFT,
  READY_TO_SEND,
  SIGNED,
  SUBMITTED,
  PENDING,
  AUTHORIZED,
  REJECTED,
  CANCEL_PENDING,
  CANCELLED,
  CANCEL_REJECTED
}
