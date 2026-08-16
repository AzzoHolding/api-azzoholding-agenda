package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionCycle;

/** Espelha {@code modules/commission/domain/repository/CommissionCycleRepository.java}. */
@Repository
public interface CommissionCycleRepository extends JpaRepository<CommissionCycle, UUID> {

  Optional<CommissionCycle> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<CommissionCycle> findByTenantIdAndPeriodStartAndPeriodEnd(
      UUID tenantId, LocalDate periodStart, LocalDate periodEnd);

  List<CommissionCycle> findByTenantIdOrderByPeriodStartDescCreatedAtDesc(UUID tenantId);

  @Query("""
      select c from CommissionCycle c
      where c.tenantId = :tenantId and upper(c.status) = :status
      order by c.periodStart desc, c.createdAt desc
      """)
  List<CommissionCycle> listByTenantAndStatus(
      @Param("tenantId") UUID tenantId, @Param("status") String status);
}
