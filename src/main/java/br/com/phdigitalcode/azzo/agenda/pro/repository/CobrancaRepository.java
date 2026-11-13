package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cobranca;

@Repository
public interface CobrancaRepository extends JpaRepository<Cobranca, UUID> {

  Optional<Cobranca> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<Cobranca> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

  Optional<Cobranca> findByProvedorAndIdNaOperadora(String provedor, String idNaOperadora);

  List<Cobranca> findByTenantIdAndOrigemTipoAndOrigemIdOrderByCreatedAtAsc(UUID tenantId, String origemTipo, UUID origemId);

  /** As que ainda esperam resposta, numa janela de tempo: a consulta de reserva olha estas. */
  @Query("select c from Cobranca c where c.status in ('CRIADA', 'AGUARDANDO') and c.idNaOperadora is not null "
      + "and c.createdAt < :antesDe and c.createdAt > :depoisDe order by c.createdAt asc")
  List<Cobranca> pendentesParaConsultar(
      @Param("antesDe") Instant antesDe, @Param("depoisDe") Instant depoisDe, Limit limit);
}
