package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Espelha {@code modules/audit/domain/repository/AuditRetentionConfigRepository.java}. Assim como
 * no original, nao ha entidade JPA para a tabela {@code audit_retention_config} (linha unica,
 * {@code id = 1}) — bean isolado com {@code EntityManager} e uma query nativa, mesmo padrao ja
 * usado em {@link PlanLimitsRepository}.
 */
@Repository
public class AuditRetentionConfigRepository {

  @PersistenceContext
  private EntityManager entityManager;

  public Optional<Integer> findRetentionPeriodDays() {
    Object value =
        entityManager
            .createNativeQuery("SELECT retention_period_days FROM audit_retention_config WHERE id = 1")
            .getResultStream()
            .findFirst()
            .orElse(null);
    if (value == null) return Optional.empty();
    if (value instanceof Number number) return Optional.of(number.intValue());
    try {
      return Optional.of(Integer.parseInt(String.valueOf(value)));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
