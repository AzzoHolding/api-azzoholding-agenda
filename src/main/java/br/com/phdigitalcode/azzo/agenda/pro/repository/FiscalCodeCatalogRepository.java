package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCodeCatalogEntity;

/**
 * Espelha {@code modules/fiscal/domain/repository/FiscalCodeCatalogRepository.java}. Catalogo
 * global — nenhuma consulta recorta por tenant, e correto.
 */
@Repository
public interface FiscalCodeCatalogRepository extends JpaRepository<FiscalCodeCatalogEntity, UUID> {

  /**
   * Vigencia aberta a direita: {@code validTo} nulo conta como vigente para sempre. A mesma data
   * alimenta as duas pontas da janela, como no original.
   */
  @Query(
      "select c from FiscalCodeCatalogEntity c"
          + " where c.codeType = :codeType and c.codeValue = :codeValue and c.status = 'ACTIVE'"
          + "   and c.validFrom <= :date and (c.validTo is null or c.validTo >= :date)")
  Optional<FiscalCodeCatalogEntity> findActiveByTypeAndValueAtDate(
      @Param("codeType") String codeType,
      @Param("codeValue") String codeValue,
      @Param("date") LocalDate date);

  @Query(
      "select count(c) from FiscalCodeCatalogEntity c"
          + " where c.codeType = :codeType and c.status = 'ACTIVE'")
  long contarAtivosPorTipo(@Param("codeType") String codeType);

  /**
   * ⚠️ <b>Assimetria do original preservada:</b> aqui o {@code codeType} e normalizado
   * ({@code trim} + maiuscula), mas em {@link #findActiveByTypeAndValueAtDate} <b>nao e</b> — o
   * valor vai cru para a consulta. Nao foi "consertado".
   *
   * <p>O original usa {@code toUpperCase()} sem locale; aqui vai {@code Locale.ROOT}, como no resto
   * do projeto migrado. Os tipos sao ASCII ({@code NCM}, {@code CFOP}, {@code CST}), entao o
   * resultado e o mesmo — a diferenca so apareceria sob locale turco.
   */
  default boolean hasAnyActiveByType(String type) {
    if (type == null || type.isBlank()) return false;
    return contarAtivosPorTipo(type.trim().toUpperCase(Locale.ROOT)) > 0;
  }
}
