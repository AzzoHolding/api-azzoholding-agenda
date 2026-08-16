package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalStateEntity;

/**
 * Espelha {@code modules/nfse/domain/repository/FiscalStateRepository.java}.
 */
@Repository
public interface FiscalStateRepository extends JpaRepository<FiscalStateEntity, String> {

  List<FiscalStateEntity> findAllByOrderByUfAsc();

  Optional<FiscalStateEntity> findByUfIgnoreCase(String uf);

  default List<FiscalStateEntity> listAllOrdered() {
    return findAllByOrderByUfAsc();
  }

  default Optional<FiscalStateEntity> findByUf(String uf) {
    if (uf == null || uf.isBlank()) return Optional.empty();
    return findByUfIgnoreCase(uf.trim());
  }
}
