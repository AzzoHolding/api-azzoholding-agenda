package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaItem;

/** Espelha {@code modules/pos/domain/repository/ComandaItemRepository.java}. */
@Repository
public interface ComandaItemRepository extends JpaRepository<ComandaItem, UUID> {

  List<ComandaItem> findByComandaIdOrderByCreatedAt(UUID comandaId);

  Optional<ComandaItem> findByIdAndComandaId(UUID id, UUID comandaId);

  /** Atendimentos cobertos por estes saldos de pacote, em comandas fechadas: [item, data do fechamento]. */
  @org.springframework.data.jpa.repository.Query(
      "select i, c.closedAt from ComandaItem i, Comanda c where c.id = i.comandaId and c.tenantId = :tenantId"
          + " and i.coberturaTipo = 'PACOTE' and i.coberturaSaldoId in :saldos and c.status = 'FECHADA'"
          + " order by c.closedAt desc")
  List<Object[]> usosDePacote(
      @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
      @org.springframework.data.repository.query.Param("saldos") java.util.Collection<UUID> saldos);
}
