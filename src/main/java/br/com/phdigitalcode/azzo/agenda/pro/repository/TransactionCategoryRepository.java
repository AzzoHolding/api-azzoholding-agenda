package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory;

/** Espelha {@code modules/finance/domain/repository/TransactionCategoryRepository.java}. */
@Repository
public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, UUID> {

  @Query("select c from TransactionCategory c where c.tenantId = :tenantId and lower(c.name) = lower(:name)")
  Optional<TransactionCategory> findByTenantAndName(@Param("tenantId") UUID tenantId, @Param("name") String name);

  List<TransactionCategory> findByTenantIdOrderByName(UUID tenantId);

  Optional<TransactionCategory> findByIdAndTenantId(UUID id, UUID tenantId);
}
