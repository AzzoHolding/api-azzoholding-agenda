package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * Espelha {@code modules/fiscal/domain/fiscal/FiscalDocumentStatus.java}.
 *
 * <p><b>Nao e usado como {@code @Enumerated}</b> em nenhuma entidade: a coluna
 * {@code fiscal_invoices.status} guarda texto livre e a leitura passa sempre por
 * {@link #tryParse(String)}, que devolve {@link Optional#empty()} para valor desconhecido em vez de
 * estourar. E o que permite ler linhas gravadas por versoes anteriores do provider.
 */
public enum FiscalDocumentStatus {
  DRAFT,
  GENERATED,
  SIGNED,
  SUBMITTED,
  CONTINGENCY_PENDING,
  AUTHORIZED,
  REJECTED,
  CANCEL_PENDING,
  CANCELLED,
  INUTILIZED;

  /**
   * Parsing tolerante: {@code null}, branco e valor fora do enum viram {@link Optional#empty()}.
   *
   * <p>O alias legado {@code "ISSUED"} — contrato antigo do provider — e traduzido para
   * {@link #AUTHORIZED}, como no original.
   */
  public static Optional<FiscalDocumentStatus> tryParse(String raw) {
    if (raw == null || raw.isBlank()) return Optional.empty();
    String normalized = raw.trim().toUpperCase(Locale.ROOT);

    // Legacy alias kept for compatibility with previous provider contract.
    if ("ISSUED".equals(normalized)) {
      return Optional.of(AUTHORIZED);
    }

    try {
      return Optional.of(FiscalDocumentStatus.valueOf(normalized));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }
}
