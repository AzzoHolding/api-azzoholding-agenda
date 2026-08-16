package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus;

/**
 * Espelha {@code modules/email/domain/repository/EmailJobRepository.java}. As duas atualizacoes
 * condicionais ({@code markProcessed}/{@code markFailed}) preservam a guarda {@code status = NEW}
 * do original (nao marcar um job que ja saiu do estado {@code NEW}, ex.: reprocessamento tardio de
 * um job ja processado por outra rodada do scheduler).
 */
@Repository
public interface EmailJobRepository extends JpaRepository<EmailJob, UUID> {

  List<EmailJob> findByStatusOrderByCreatedAtAsc(EmailJobStatus status, Pageable pageable);

  @Modifying
  @Query(
      """
      update EmailJob j
         set j.status = br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus.PROCESSED,
             j.providerStatus = :providerStatus,
             j.fromEmail = :fromEmail,
             j.processedAt = :processedAt,
             j.errorMessage = null
       where j.id = :jobId
         and j.status = br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus.NEW
      """)
  int markProcessedInternal(
      @Param("jobId") UUID jobId,
      @Param("providerStatus") String providerStatus,
      @Param("fromEmail") String fromEmail,
      @Param("processedAt") Instant processedAt);

  @Modifying
  @Query(
      """
      update EmailJob j
         set j.status = br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus.FAILED,
             j.providerStatus = :providerStatus,
             j.errorMessage = :errorMessage,
             j.fromEmail = :fromEmail,
             j.processedAt = :processedAt
       where j.id = :jobId
         and j.status = br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailJobStatus.NEW
      """)
  int markFailedInternal(
      @Param("jobId") UUID jobId,
      @Param("providerStatus") String providerStatus,
      @Param("errorMessage") String errorMessage,
      @Param("fromEmail") String fromEmail,
      @Param("processedAt") Instant processedAt);

  default List<EmailJob> findNextNewBatch(int limit) {
    return findByStatusOrderByCreatedAtAsc(EmailJobStatus.NEW, PageRequest.of(0, Math.max(limit, 1)));
  }

  default boolean markProcessed(UUID jobId, String providerStatus, String fromEmail, Instant processedAt) {
    return markProcessedInternal(jobId, providerStatus, fromEmail, processedAt) > 0;
  }

  default boolean markFailed(
      UUID jobId, String providerStatus, String errorMessage, String fromEmail, Instant processedAt) {
    return markFailedInternal(jobId, providerStatus, errorMessage, fromEmail, processedAt) > 0;
  }
}
