package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ProductCategory;

/** Espelha {@code modules/finance/domain/repository/ProductCategoryRepository.java}. */
@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

  @Query("select c from ProductCategory c where c.tenantId = :tenantId and lower(c.name) = lower(:name)")
  Optional<ProductCategory> findByTenantAndName(@Param("tenantId") UUID tenantId, @Param("name") String name);
}
