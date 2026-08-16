package br.com.phdigitalcode.azzo.agenda.pro.specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStatus;
import jakarta.persistence.criteria.Predicate;

/**
 * Equivalente ao HQL montado dinamicamente em
 * {@code WhatsAppBookingReactivationCycleRepository.buildOperationalQuery} do original:
 * {@code tenantId} sempre entra, os demais filtros só entram quando informados. O termo de busca
 * cobre os mesmos 6 campos (case-insensitive, {@code coalesce} para nulo) do original.
 */
public final class WhatsAppBookingReactivationCycleSpecifications {

  private WhatsAppBookingReactivationCycleSpecifications() {}

  public static Specification<WhatsAppBookingReactivationCycleEntity> operational(
      UUID tenantId,
      Instant abandonedAfter,
      Instant abandonedBefore,
      WhatsAppBookingReactivationStatus status,
      WhatsAppBookingReactivationStage stage,
      String searchTerm) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      if (abandonedAfter != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("abandonedAt"), abandonedAfter));
      }
      if (abandonedBefore != null) {
        predicates.add(cb.lessThan(root.get("abandonedAt"), abandonedBefore));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (stage != null) {
        predicates.add(cb.equal(root.get("lastStage"), stage));
      }
      if (searchTerm != null && !searchTerm.isBlank()) {
        String like = "%" + searchTerm.trim().toLowerCase(Locale.ROOT) + "%";
        Predicate customerName =
            cb.like(cb.lower(cb.coalesce(root.get("customerName"), "")), like);
        Predicate userIdentifier =
            cb.like(cb.lower(cb.coalesce(root.get("userIdentifier"), "")), like);
        Predicate lastServiceName =
            cb.like(cb.lower(cb.coalesce(root.get("lastServiceName"), "")), like);
        Predicate lastProfessionalName =
            cb.like(cb.lower(cb.coalesce(root.get("lastProfessionalName"), "")), like);
        Predicate customerLastMessage =
            cb.like(cb.lower(cb.coalesce(root.get("customerLastMessage"), "")), like);
        Predicate assistantLastPrompt =
            cb.like(cb.lower(cb.coalesce(root.get("assistantLastPrompt"), "")), like);
        predicates.add(
            cb.or(
                customerName,
                userIdentifier,
                lastServiceName,
                lastProfessionalName,
                customerLastMessage,
                assistantLastPrompt));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
