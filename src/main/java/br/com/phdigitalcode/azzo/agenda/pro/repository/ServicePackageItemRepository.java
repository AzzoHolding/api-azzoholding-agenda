package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackageItem;

/** Espelha {@code modules/packages/domain/repository/ServicePackageItemRepository.java}. */
@Repository
public interface ServicePackageItemRepository extends JpaRepository<ServicePackageItem, UUID> {

  List<ServicePackageItem> findByPackageId(UUID packageId);

  /**
   * Equivalente ao {@code delete("packageId", id)} do Panache: usado no {@code atualizar} para
   * regravar a composicao do pacote do zero.
   */
  void deleteByPackageId(UUID packageId);
}
