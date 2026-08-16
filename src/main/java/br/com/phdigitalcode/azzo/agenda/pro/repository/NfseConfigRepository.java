package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;

/** Espelha {@code modules/nfse/domain/repository/NfseConfigRepository.java}. */
@Repository
public interface NfseConfigRepository extends JpaRepository<NfseConfigEntity, UUID> {

  Optional<NfseConfigEntity> findByTenantIdAndAmbiente(UUID tenantId, NfseAmbiente ambiente);

  default Optional<NfseConfigEntity> findByTenantAndAmbiente(UUID tenantId, NfseAmbiente ambiente) {
    if (tenantId == null || ambiente == null) return Optional.empty();
    return findByTenantIdAndAmbiente(tenantId, ambiente);
  }
}
