package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackageBalance;

/** Espelha {@code modules/packages/domain/repository/ClientPackageBalanceRepository.java}. */
@Repository
public interface ClientPackageBalanceRepository extends JpaRepository<ClientPackageBalance, UUID> {

  List<ClientPackageBalance> findByPurchaseId(UUID purchaseId);
}
