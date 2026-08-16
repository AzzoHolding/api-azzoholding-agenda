package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfsePdfJobEntity;

/**
 * Espelha {@code modules/nfse/domain/repository/NfsePdfJobRepository.java}.
 *
 * <p>{@code tryLockByInvoice} e lock <b>logico</b> via {@code UPDATE} condicional (nao pessimista):
 * so passa de {@code QUEUED} para {@code PROCESSING} se nao houver outro job da mesma invoice ja
 * {@code PROCESSING}. Preservar exatamente esse mecanismo ao portar {@code NfsePdfJobWorker}
 * (Fronteira 7) — nao trocar por {@code @Lock(PESSIMISTIC_WRITE)}.
 */
@Repository
public interface NfsePdfJobRepository extends JpaRepository<NfsePdfJobEntity, UUID> {

  Optional<NfsePdfJobEntity> findFirstByTenantIdAndInvoiceIdAndStatusInOrderByRequestedAtDesc(
      UUID tenantId, UUID invoiceId, List<String> statuses);

  Optional<NfsePdfJobEntity> findByTenantIdAndId(UUID tenantId, UUID jobId);

  List<NfsePdfJobEntity> findByStatusOrderByRequestedAtAsc(
      String status, org.springframework.data.domain.Pageable pageable);

  @Modifying
  @Query(
      """
      update NfsePdfJobEntity j
         set j.status = 'PROCESSING'
       where j.id = :jobId
         and j.tenantId = :tenantId
         and j.status = 'QUEUED'
         and not exists (
           select 1 from NfsePdfJobEntity x
            where x.invoiceId = :invoiceId
              and x.tenantId = :tenantId
              and x.status = 'PROCESSING'
              and x.id <> :jobId
         )
      """)
  int lockForProcessing(
      @Param("jobId") UUID jobId, @Param("tenantId") UUID tenantId, @Param("invoiceId") UUID invoiceId);

  default Optional<NfsePdfJobEntity> findActiveJob(UUID tenantId, UUID invoiceId) {
    return findFirstByTenantIdAndInvoiceIdAndStatusInOrderByRequestedAtDesc(
        tenantId, invoiceId, List.of("QUEUED", "PROCESSING"));
  }

  default List<NfsePdfJobEntity> findQueued(int limit) {
    return findByStatusOrderByRequestedAtAsc("QUEUED", PageRequest.of(0, Math.max(limit, 1)));
  }

  default boolean tryLockByInvoice(UUID jobId, UUID tenantId, UUID invoiceId) {
    if (jobId == null || tenantId == null || invoiceId == null) return false;
    return lockForProcessing(jobId, tenantId, invoiceId) > 0;
  }
}
