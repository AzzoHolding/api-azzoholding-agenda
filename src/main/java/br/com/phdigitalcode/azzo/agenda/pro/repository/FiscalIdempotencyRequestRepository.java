package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalIdempotencyRequestEntity;

/** Espelha {@code modules/fiscal/domain/repository/FiscalIdempotencyRequestRepository.java}. */
@Repository
public interface FiscalIdempotencyRequestRepository
    extends JpaRepository<FiscalIdempotencyRequestEntity, UUID> {

  Optional<FiscalIdempotencyRequestEntity> findByTenantIdAndOperationAndIdempotencyKey(
      UUID tenantId, String operation, String idempotencyKey);

  default Optional<FiscalIdempotencyRequestEntity> findByKey(
      UUID tenantId, String operation, String idempotencyKey) {
    return findByTenantIdAndOperationAndIdempotencyKey(tenantId, operation, idempotencyKey);
  }
}
