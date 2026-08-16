package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembershipBalance;

/** Espelha {@code modules/membership/domain/repository/ClientMembershipBalanceRepository.java}. */
@Repository
public interface ClientMembershipBalanceRepository
    extends JpaRepository<ClientMembershipBalance, UUID> {

  List<ClientMembershipBalance> findByMembershipId(UUID membershipId);
}
