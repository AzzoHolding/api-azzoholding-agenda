package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditChainVerification;

@Repository
public interface AuditChainVerificationRepository extends JpaRepository<AuditChainVerification, UUID> {

  @Query("select v from AuditChainVerification v where v.tenantId = :tenantId order by v.finishedAt desc")
  List<AuditChainVerification> recentesDoSalao(@Param("tenantId") UUID tenantId, Limit limit);

  default Optional<AuditChainVerification> ultimaDoSalao(UUID tenantId) {
    return recentesDoSalao(tenantId, Limit.of(1)).stream().findFirst();
  }
}
