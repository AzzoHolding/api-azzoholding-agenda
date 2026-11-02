package br.com.phdigitalcode.azzo.agenda.pro.specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
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
        predicates.add(doProfissional(root, query, cb, tenantId, professionalScopeId));
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
        predicates.add(doProfissional(root, query, cb, tenantId, professionalId));
      }

      if (unreadOnly) {
        predicates.add(cb.isNull(root.get("viewedAt")));
      }

      adicionarCursor(predicates, root, cb, cursorCreatedAt, cursorId);
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * O envio e dele se traz o `professionalId` ou se o agendamento e dele: quase nenhum registro vem
   * com o campo preenchido. Aceitar o campo vazio mostrava telefone e nome de clientes de qualquer
   * profissional do salao (02/11/2026).
   */
  private static Predicate doProfissional(
      jakarta.persistence.criteria.Root<Notification> root,
      jakarta.persistence.criteria.CriteriaQuery<?> query,
      jakarta.persistence.criteria.CriteriaBuilder cb,
      UUID tenantId,
      UUID professionalId) {
    jakarta.persistence.criteria.Subquery<UUID> agendamentos = query.subquery(UUID.class);
    jakarta.persistence.criteria.Root<Agendamento> agendamento = agendamentos.from(Agendamento.class);
    agendamentos
        .select(agendamento.get("id"))
        .where(
            cb.equal(agendamento.get("tenantId"), tenantId),
            cb.equal(agendamento.get("professionalId"), professionalId));
    return cb.or(
        cb.equal(root.get("professionalId"), professionalId),
        root.get("appointmentId").in(agendamentos));
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
