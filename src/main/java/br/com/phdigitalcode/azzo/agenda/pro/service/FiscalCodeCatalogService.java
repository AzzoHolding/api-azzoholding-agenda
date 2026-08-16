package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCodeCatalogRepository;

/**
 * Espelha {@code modules/fiscal/application/FiscalCodeCatalogService.java}.
 *
 * <p>Fachada de leitura do catalogo global de codigos fiscais (NCM, CFOP, CST, CSOSN). Nao recorta
 * por tenant — o catalogo e compartilhado, como no original.
 *
 * <p>⚠️ <b>A assimetria de normalizacao do original esta preservada:</b> o {@code codeType} e
 * normalizado ({@code trim} + maiuscula) nos dois metodos, mas o {@code codeValue} recebe apenas
 * {@code trim}, sem mudanca de caixa. Para os tipos numericos em uso (NCM, CFOP, CST, CSOSN) isso
 * nao muda nada; a diferenca so apareceria num tipo de codigo alfanumerico.
 *
 * <p>O original usa {@code toUpperCase()} sem locale; aqui vai {@link Locale#ROOT}, como no resto do
 * projeto migrado — mesmo resultado para codigos ASCII.
 */
@Service
public class FiscalCodeCatalogService {

  private final FiscalCodeCatalogRepository fiscalCodeCatalogRepository;

  public FiscalCodeCatalogService(FiscalCodeCatalogRepository fiscalCodeCatalogRepository) {
    this.fiscalCodeCatalogRepository = fiscalCodeCatalogRepository;
  }

  /** Codigo vigente na data informada. Argumento em branco devolve {@code false}, nao estoura. */
  @Transactional(readOnly = true)
  public boolean existsActive(String codeType, String codeValue, LocalDate referenceDate) {
    if (codeType == null || codeType.isBlank() || codeValue == null || codeValue.isBlank()) {
      return false;
    }
    LocalDate date = referenceDate != null ? referenceDate : LocalDate.now();
    return fiscalCodeCatalogRepository
        .findActiveByTypeAndValueAtDate(
            codeType.trim().toUpperCase(Locale.ROOT), codeValue.trim(), date)
        .isPresent();
  }

  /**
   * Existe ao menos um codigo ativo desse tipo? E o interruptor que decide se a validacao por
   * catalogo entra em vigor: sem linhas cadastradas, {@code FiscalRuleValidationService} nao exige
   * vigencia — a nota passa so com a validacao de formato.
   */
  @Transactional(readOnly = true)
  public boolean hasCatalogForType(String codeType) {
    if (codeType == null || codeType.isBlank()) return false;
    return fiscalCodeCatalogRepository.hasAnyActiveByType(codeType.trim().toUpperCase(Locale.ROOT));
  }
}
