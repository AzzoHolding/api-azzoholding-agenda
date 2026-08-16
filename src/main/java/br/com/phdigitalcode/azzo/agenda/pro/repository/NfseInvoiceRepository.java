package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseOperationalStatus;

/**
 * Espelha {@code modules/nfse/domain/repository/NfseInvoiceRepository.java}.
 *
 * <p>{@code findByTenantAndIdForUpdate} usa lock pessimista real ({@code PESSIMISTIC_WRITE}) — o
 * mesmo mecanismo que {@code NfseResourceIntegrationTest.devePermitirApenasUmaAutorizacaoEmConcorrencia}
 * (Fronteira 6) cobre no original. Nao trocar por lock otimista ao portar as fronteiras seguintes.
 */
@Repository
public interface NfseInvoiceRepository extends JpaRepository<NfseInvoiceEntity, UUID> {

  Optional<NfseInvoiceEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("from NfseInvoiceEntity e where e.tenantId = :tenantId and e.id = :id")
  Optional<NfseInvoiceEntity> findByTenantIdAndIdForUpdate(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  long countByFiscalStatusAndCreatedAtGreaterThanEqual(NfseFiscalStatus fiscalStatus, Instant threshold);

  /** Paginacao de {@code listarInvoices} (Fronteira 6) — sem filtro de status. */
  Page<NfseInvoiceEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

  /** Paginacao de {@code listarInvoices} (Fronteira 6) — filtrado por {@code fiscalStatus}. */
  Page<NfseInvoiceEntity> findByTenantIdAndFiscalStatusOrderByCreatedAtDesc(
      UUID tenantId, NfseFiscalStatus fiscalStatus, Pageable pageable);

  @Query(
      """
      from NfseInvoiceEntity e
      where e.fiscalStatus = :status
      and e.protocolo is not null
      and (e.operationalStatus is null or e.operationalStatus in (:waitingProvider, :retryScheduled))
      order by e.updatedAt asc
      """)
  List<NfseInvoiceEntity> listPendingForStatusPolling(
      @Param("status") NfseFiscalStatus status,
      @Param("waitingProvider") NfseOperationalStatus waitingProvider,
      @Param("retryScheduled") NfseOperationalStatus retryScheduled,
      org.springframework.data.domain.Pageable pageable);

  @Query(
      "select coalesce(max(e.numeroRps), 0) from NfseInvoiceEntity e"
          + " where e.tenantId = :tenantId and e.municipioCodigoIbge = :municipio"
          + " and e.serieRps = :serie and e.ambiente = :ambiente")
  long maxNumeroRps(
      @Param("tenantId") UUID tenantId,
      @Param("municipio") String municipioCodigoIbge,
      @Param("serie") String serieRps,
      @Param("ambiente") NfseAmbiente ambiente);

  @Query(
      "from NfseInvoiceEntity e where e.tenantId = :tenantId and e.dataCompetencia >= :from"
          + " and e.dataCompetencia <= :to order by e.dataCompetencia asc, e.createdAt asc")
  List<NfseInvoiceEntity> listForAccountingExportAllStatuses(
      @Param("tenantId") UUID tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query(
      "from NfseInvoiceEntity e where e.tenantId = :tenantId and e.dataCompetencia >= :from"
          + " and e.dataCompetencia <= :to and e.fiscalStatus in :statuses"
          + " order by e.dataCompetencia asc, e.createdAt asc")
  List<NfseInvoiceEntity> listForAccountingExportByStatuses(
      @Param("tenantId") UUID tenantId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("statuses") List<NfseFiscalStatus> statuses);

  default Optional<NfseInvoiceEntity> findByTenantAndId(UUID tenantId, UUID id) {
    if (tenantId == null || id == null) return Optional.empty();
    return findByTenantIdAndId(tenantId, id);
  }

  default Optional<NfseInvoiceEntity> findByTenantAndIdForUpdate(UUID tenantId, UUID id) {
    if (tenantId == null || id == null) return Optional.empty();
    return findByTenantIdAndIdForUpdate(tenantId, id);
  }

  default long countRejectedSince(Instant threshold) {
    if (threshold == null) return 0;
    return countByFiscalStatusAndCreatedAtGreaterThanEqual(NfseFiscalStatus.REJECTED, threshold);
  }

  default List<NfseInvoiceEntity> listPendingForStatusPolling(int limit) {
    int batchSize = Math.max(limit, 1);
    return listPendingForStatusPolling(
        NfseFiscalStatus.PENDING,
        NfseOperationalStatus.WAITING_PROVIDER,
        NfseOperationalStatus.RETRY_SCHEDULED,
        PageRequest.of(0, batchSize));
  }

  /** Paridade com o original: {@code MAX+1} por tenant/municipio/serie/ambiente. */
  default int nextRpsNumber(UUID tenantId, String municipioCodigoIbge, String serieRps, String ambiente) {
    if (tenantId == null || municipioCodigoIbge == null || serieRps == null || ambiente == null) {
      return 1;
    }
    long max = maxNumeroRps(tenantId, municipioCodigoIbge, serieRps, NfseAmbiente.valueOf(ambiente));
    return (int) (max + 1);
  }

  /** Paginacao de {@code listarInvoices} (Fronteira 6): pagina 1-based, tamanho minimo 1. */
  default Page<NfseInvoiceEntity> pageByTenantAndOptionalStatus(
      UUID tenantId, NfseFiscalStatus fiscalStatus, int page, int pageSize) {
    int pg = Math.max(page, 1);
    int ps = Math.max(pageSize, 1);
    Pageable pageable = PageRequest.of(pg - 1, ps);
    if (fiscalStatus == null) {
      return findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }
    return findByTenantIdAndFiscalStatusOrderByCreatedAtDesc(tenantId, fiscalStatus, pageable);
  }

  default List<NfseInvoiceEntity> listForAccountingExport(
      UUID tenantId, LocalDate from, LocalDate to, List<NfseFiscalStatus> statuses) {
    if (tenantId == null || from == null || to == null) return List.of();
    if (statuses == null || statuses.isEmpty()) {
      return listForAccountingExportAllStatuses(tenantId, from, to);
    }
    return listForAccountingExportByStatuses(tenantId, from, to, statuses);
  }
}
