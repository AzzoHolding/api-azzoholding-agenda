package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/auth/domain/entity/TermsAcceptance.java}. Tabela
 * {@code terms_acceptances} (plural), consumida por {@code TermsService} (modulo {@code audit}).
 *
 * <p>Nomeada {@code AuditTermsAcceptance} para nao colidir com a entidade
 * {@link TermsAcceptance} ja existente nesta aplicacao, que espelha
 * {@code modules/onboarding/domain/entity/TermsAcceptanceEntity.java} — outra entidade legitima
 * do original, tabela {@code terms_acceptance} (singular), com colunas diferentes. As duas
 * coexistem no Quarkus original e continuam coexistindo aqui.
 */
@Entity
@Table(name = "terms_acceptances")
@Getter
@Setter
public class AuditTermsAcceptance {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "terms_version_id", nullable = false)
  private UUID termsVersionId;

  @Column(name = "accepted_at", nullable = false)
  private Instant acceptedAt;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "request_id", nullable = false)
  private String requestId;

  @Column(name = "acceptance_hash", nullable = false)
  private String acceptanceHash;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (acceptedAt == null) acceptedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AuditTermsAcceptance other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
