package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackagePurchase;

/** Espelha {@code modules/packages/domain/repository/ClientPackagePurchaseRepository.java}. */
@Repository
public interface ClientPackagePurchaseRepository extends JpaRepository<ClientPackagePurchase, UUID> {

  List<ClientPackagePurchase> findByTenantIdAndClientIdOrderByCreatedAtDesc(
      UUID tenantId, UUID clientId);

  List<ClientPackagePurchase> findByTenantIdAndComandaId(UUID tenantId, UUID comandaId);
}
