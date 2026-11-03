package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.PeriodoDeConciliacao;

@Repository
public interface PeriodoDeConciliacaoRepository extends JpaRepository<PeriodoDeConciliacao, UUID> {

  /** A ultima conciliacao terminada dentro do intervalo: e dela que a proxima continua. */
  Optional<PeriodoDeConciliacao>
      findFirstByTenantIdAndEndsAtGreaterThanAndEndsAtLessThanEqualOrderByEndsAtDesc(
          UUID tenantId, Instant depoisDe, Instant ate);

  List<PeriodoDeConciliacao> findTop12ByTenantIdOrderByEndsAtDesc(UUID tenantId);
}
