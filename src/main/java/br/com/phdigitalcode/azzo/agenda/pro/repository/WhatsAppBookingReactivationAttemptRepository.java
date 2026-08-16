package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationAttemptEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationAttemptStatus;

/** Espelha {@code modules/chat/domain/repository/WhatsAppBookingReactivationAttemptRepository.java}. */
@Repository
public interface WhatsAppBookingReactivationAttemptRepository
    extends JpaRepository<WhatsAppBookingReactivationAttemptEntity, UUID> {

  @Query(
      "from WhatsAppBookingReactivationAttemptEntity a where a.cycleId = :cycleId and a.status = :status "
          + "order by a.sentAt desc nulls last, a.createdAt desc")
  List<WhatsAppBookingReactivationAttemptEntity> listLatestSentAttempts(
      @Param("cycleId") UUID cycleId,
      @Param("status") WhatsAppBookingReactivationAttemptStatus status,
      Pageable pageable);

  @Query(
      "from WhatsAppBookingReactivationAttemptEntity a where a.cycleId = :cycleId "
          + "order by a.sentAt desc nulls last, a.scheduledFor desc nulls last, a.createdAt desc")
  List<WhatsAppBookingReactivationAttemptEntity> listLatestAttempts(
      @Param("cycleId") UUID cycleId, Pageable pageable);

  default Optional<WhatsAppBookingReactivationAttemptEntity> findLatestSentAttempt(UUID cycleId) {
    if (cycleId == null) return Optional.empty();
    List<WhatsAppBookingReactivationAttemptEntity> result =
        listLatestSentAttempts(
            cycleId, WhatsAppBookingReactivationAttemptStatus.SENT, PageRequest.of(0, 1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }

  default Optional<WhatsAppBookingReactivationAttemptEntity> findLatestAttempt(UUID cycleId) {
    if (cycleId == null) return Optional.empty();
    List<WhatsAppBookingReactivationAttemptEntity> result =
        listLatestAttempts(cycleId, PageRequest.of(0, 1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }
}
