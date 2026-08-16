package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseIdempotencyRequestEntity;

/** Espelha {@code modules/nfse/domain/repository/NfseIdempotencyRequestRepository.java}. */
@Repository
public interface NfseIdempotencyRequestRepository
    extends JpaRepository<NfseIdempotencyRequestEntity, UUID> {

  Optional<NfseIdempotencyRequestEntity> findByTenantIdAndOperationAndIdempotencyKey(
      UUID tenantId, String operation, String idempotencyKey);

  default Optional<NfseIdempotencyRequestEntity> findByKey(
      UUID tenantId, String operation, String idempotencyKey) {
    if (tenantId == null
        || operation == null
        || operation.isBlank()
        || idempotencyKey == null
        || idempotencyKey.isBlank()) {
      return Optional.empty();
    }
    return findByTenantIdAndOperationAndIdempotencyKey(
        tenantId, operation.trim().toUpperCase(), idempotencyKey.trim());
  }
}
