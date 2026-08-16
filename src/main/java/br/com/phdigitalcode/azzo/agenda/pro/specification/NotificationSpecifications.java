package br.com.phdigitalcode.azzo.agenda.pro.specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Notification;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import jakarta.persistence.criteria.Predicate;

/**
 * Equivalente ao HQL montado dinamicamente em {@code ServicoNotificacoes.listar}/
 * {@code listarMeusAgendamentos} do original (Panache {@code StringBuilder} + lista de
 * parametros posicionais).
 */
public final class NotificationSpecifications {

  private NotificationSpecifications() {}

  public static Specification<Notification> listar(
      UUID tenantId,
      UUID professionalScopeId,
      String channel,
      boolean failedOnly,
      String status,
      boolean unreadOnly,
      Instant cursorCreatedAt,
      UUID cursorId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));

      if (professionalScopeId != null) {
        predicates.add(
            cb.or(
                cb.equal(root.get("professionalId"), professionalScopeId),
                cb.isNull(root.get("professionalId"))));
      }

      if (channel != null && !channel.isBlank()) {
        predicates.add(cb.equal(cb.lower(root.get("channel")), channel.trim().toLowerCase()));
      }

      if (failedOnly) {
        predicates.add(cb.equal(root.get("status"), StatusNotification.FAILED));
      } else if (status != null && !status.isBlank()) {
        predicates.add(cb.equal(root.get("status"), StatusNotification.fromValue(status)));
      }

      if (unreadOnly) {
        predicates.add(cb.isNull(root.get("viewedAt")));
      }

      adicionarCursor(predicates, root, cb, cursorCreatedAt, cursorId);
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  public static Specification<Notification> meusAgendamentos(
      UUID tenantId,
      UUID professionalId,
      boolean unreadOnly,
      Instant cursorCreatedAt,
      UUID cursorId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      predicates.add(cb.isNotNull(root.get("appointmentId")));

      if (professionalId != null) {
        predicates.add(cb.equal(root.get("professionalId"), professionalId));
      }

      if (unreadOnly) {
        predicates.add(cb.isNull(root.get("viewedAt")));
      }

      adicionarCursor(predicates, root, cb, cursorCreatedAt, cursorId);
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static void adicionarCursor(
      List<Predicate> predicates,
      jakarta.persistence.criteria.Root<?> root,
      jakarta.persistence.criteria.CriteriaBuilder cb,
      Instant cursorCreatedAt,
      UUID cursorId) {
    if (cursorCreatedAt == null || cursorId == null) return;
    predicates.add(
        cb.or(
            cb.lessThan(root.get("createdAt"), cursorCreatedAt),
            cb.and(
                cb.equal(root.get("createdAt"), cursorCreatedAt),
                cb.lessThan(root.get("id"), cursorId))));
  }
}
