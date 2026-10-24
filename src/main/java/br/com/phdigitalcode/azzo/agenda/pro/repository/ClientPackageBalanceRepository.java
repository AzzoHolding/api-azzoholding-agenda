package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackageBalance;

/** Espelha {@code modules/packages/domain/repository/ClientPackageBalanceRepository.java}. */
@Repository
public interface ClientPackageBalanceRepository extends JpaRepository<ClientPackageBalance, UUID> {

  List<ClientPackageBalance> findByPurchaseId(UUID purchaseId);

  /**
   * Consome sessoes numa unica instrucao, so se ainda houver saldo. Ler-conferir-gravar deixava
   * duas comandas fechando juntas passarem pela mesma ultima sessao (2026-09-18).
   *
   * @return 1 se consumiu; 0 se o saldo nao tinha as sessoes
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update ClientPackageBalance b set b.sessoesUsadas = b.sessoesUsadas + :sessoes"
          + " where b.id = :id and b.sessoesTotais - b.sessoesUsadas >= :sessoes")
  int consumirSeHouverSaldo(@Param("id") UUID id, @Param("sessoes") int sessoes);

  /** Devolve sessoes (estorno) numa unica instrucao, nunca abaixo de zero. */
  @Modifying(flushAutomatically = true)
  @Query(
      "update ClientPackageBalance b set b.sessoesUsadas ="
          + " case when b.sessoesUsadas > :sessoes then b.sessoesUsadas - :sessoes else 0 end"
          + " where b.id = :id")
  int devolver(@Param("id") UUID id, @Param("sessoes") int sessoes);
}
