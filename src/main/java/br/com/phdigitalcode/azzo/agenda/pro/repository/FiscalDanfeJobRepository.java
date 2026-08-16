package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalDanfeJobEntity;

/** Espelha {@code modules/fiscal/domain/repository/FiscalDanfeJobRepository.java}. */
@Repository
public interface FiscalDanfeJobRepository extends JpaRepository<FiscalDanfeJobEntity, UUID> {

  @Query(
      "select j from FiscalDanfeJobEntity j"
          + " where j.tenantId = :tenantId and j.invoiceId = :invoiceId"
          + "   and j.status in ('QUEUED', 'PROCESSING')"
          + " order by j.requestedAt desc")
  List<FiscalDanfeJobEntity> buscarJobsAtivos(
      @Param("tenantId") UUID tenantId, @Param("invoiceId") UUID invoiceId, Pageable pageable);

  Optional<FiscalDanfeJobEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  @Query("select j from FiscalDanfeJobEntity j where j.status = 'QUEUED' order by j.requestedAt asc")
  List<FiscalDanfeJobEntity> buscarNaFila(Pageable pageable);

  /**
   * Lock otimista de job por nota: so promove {@code QUEUED → PROCESSING} se <b>nenhum outro</b>
   * job da mesma nota ja estiver {@code PROCESSING}. E o que impede dois workers gerarem o mesmo
   * DANFE em paralelo. Porte literal do {@code update ... where not exists} do original.
   */
  @Modifying
  @Query(
      """
      update FiscalDanfeJobEntity j
         set j.status = 'PROCESSING'
       where j.id = :jobId
         and j.status = 'QUEUED'
         and not exists (
           select 1 from FiscalDanfeJobEntity x
            where x.invoiceId = :invoiceId
              and x.status = 'PROCESSING'
              and x.id <> :jobId
         )
      """)
  int promoverParaProcessando(@Param("jobId") UUID jobId, @Param("invoiceId") UUID invoiceId);

  default Optional<FiscalDanfeJobEntity> findActiveJob(UUID tenantId, UUID invoiceId) {
    return buscarJobsAtivos(tenantId, invoiceId, PageRequest.ofSize(1)).stream().findFirst();
  }

  default Optional<FiscalDanfeJobEntity> findByTenantAndId(UUID tenantId, UUID jobId) {
    return findByTenantIdAndId(tenantId, jobId);
  }

  /**
   * O original usa {@code range(0, max(limit - 1, 0))}, que e inclusivo nas duas pontas: para
   * {@code limit <= 1} devolve <b>1</b> item, nao zero. {@link PageRequest#ofSize(int)} exige
   * tamanho positivo, entao o piso e 1 — mesmo resultado.
   */
  default List<FiscalDanfeJobEntity> findQueued(int limit) {
    return buscarNaFila(PageRequest.ofSize(Math.max(limit, 1)));
  }

  default boolean tryLockByInvoice(UUID jobId, UUID invoiceId) {
    if (jobId == null || invoiceId == null) return false;
    return promoverParaProcessando(jobId, invoiceId) > 0;
  }
}
