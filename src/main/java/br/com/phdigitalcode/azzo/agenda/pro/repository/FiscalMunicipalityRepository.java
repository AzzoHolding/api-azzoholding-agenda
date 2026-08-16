package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalMunicipalityEntity;

/**
 * Espelha {@code modules/nfse/domain/repository/FiscalMunicipalityRepository.java}.
 *
 * <p>Armadilha #7 do briefing (JPQL com {@code :param is null} quebra no PostgreSQL) evitada:
 * as duas variantes com/sem filtro de UF sao metodos JPQL separados, como no original (que tambem
 * bifurca a query em vez de usar {@code is null} opcional).
 */
@Repository
public interface FiscalMunicipalityRepository extends JpaRepository<FiscalMunicipalityEntity, String> {

  @Query("from FiscalMunicipalityEntity m join fetch m.state order by m.nome asc")
  List<FiscalMunicipalityEntity> listAllOrderByNome();

  @Query(
      "from FiscalMunicipalityEntity m join fetch m.state where upper(m.state.uf) = :uf"
          + " order by m.nome asc")
  List<FiscalMunicipalityEntity> listByStateUfExact(@Param("uf") String uf);

  @Query(
      "from FiscalMunicipalityEntity m join fetch m.state where lower(m.nome) like :search"
          + " order by m.nome asc")
  List<FiscalMunicipalityEntity> searchByNome(
      @Param("search") String search, org.springframework.data.domain.Pageable pageable);

  @Query(
      "from FiscalMunicipalityEntity m join fetch m.state where upper(m.state.uf) = :uf"
          + " and lower(m.nome) like :search order by m.nome asc")
  List<FiscalMunicipalityEntity> searchByStateUfAndNome(
      @Param("uf") String uf,
      @Param("search") String search,
      org.springframework.data.domain.Pageable pageable);

  default List<FiscalMunicipalityEntity> listByStateUf(String stateUf) {
    if (stateUf == null || stateUf.isBlank()) {
      return listAllOrderByNome();
    }
    return listByStateUfExact(stateUf.trim().toUpperCase());
  }

  default List<FiscalMunicipalityEntity> searchByStateUfAndName(
      String stateUf, String search, int limit) {
    String normalizedSearch = "%" + search.trim().toLowerCase() + "%";
    PageRequest page = PageRequest.of(0, limit);
    if (stateUf == null || stateUf.isBlank()) {
      return searchByNome(normalizedSearch, page);
    }
    return searchByStateUfAndNome(stateUf.trim().toUpperCase(), normalizedSearch, page);
  }

  default Optional<FiscalMunicipalityEntity> findByCodigoIbge(String codigoIbge) {
    if (codigoIbge == null || codigoIbge.isBlank()) return Optional.empty();
    return findById(codigoIbge.trim());
  }
}
