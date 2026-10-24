package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembershipBalance;

/** Espelha {@code modules/membership/domain/repository/ClientMembershipBalanceRepository.java}. */
@Repository
public interface ClientMembershipBalanceRepository
    extends JpaRepository<ClientMembershipBalance, UUID> {

  List<ClientMembershipBalance> findByMembershipId(UUID membershipId);

  /** Mesmo cuidado de {@link ClientPackageBalanceRepository#consumirSeHouverSaldo}. */
  @Modifying(flushAutomatically = true)
  @Query(
      "update ClientMembershipBalance b set b.usadasNoPeriodo = b.usadasNoPeriodo + :sessoes"
          + " where b.id = :id and b.quantidadeMensal - b.usadasNoPeriodo >= :sessoes")
  int consumirSeHouverSaldo(@Param("id") UUID id, @Param("sessoes") int sessoes);

  @Modifying(flushAutomatically = true)
  @Query(
      "update ClientMembershipBalance b set b.usadasNoPeriodo ="
          + " case when b.usadasNoPeriodo > :sessoes then b.usadasNoPeriodo - :sessoes else 0 end"
          + " where b.id = :id")
  int devolver(@Param("id") UUID id, @Param("sessoes") int sessoes);
}
