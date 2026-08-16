package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/suggestions/domain/entity/FeedbackSuggestion.java}. Tabela
 * {@code feedback_suggestions}.
 *
 * <p>{@code adminResponse}/{@code respondedByUserId}/{@code respondedByUserName}/
 * {@code respondedAt}/{@code closedAt} sao escritos e lidos pelo modulo {@code admin}
 * ({@code SystemAdminService}), ainda nao migrado — mapeados aqui porque pertencem a esta tabela,
 * mas sem uso ainda no modulo {@code suggestions} em si (que so cria e lista).
 */
@Entity
@Table(name = "feedback_suggestions")
@Getter
@Setter
public class FeedbackSuggestion {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "user_name", nullable = false)
  private String userName;

  @Column(name = "user_role", nullable = false)
  private String userRole;

  @Column(name = "category", nullable = false, length = 30)
  private String category;

  @Column(name = "title", nullable = false, length = 160)
  private String title;

  @Column(name = "message", nullable = false, length = 5000)
  private String message;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "admin_response", length = 8000)
  private String adminResponse;

  @Column(name = "responded_by_user_id")
  private UUID respondedByUserId;

  @Column(name = "responded_by_user_name", length = 160)
  private String respondedByUserName;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "source_page", length = 160)
  private String sourcePage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (status == null || status.isBlank()) status = "OPEN";
    if (category == null || category.isBlank()) category = "MELHORIA";
    if (createdAt == null) createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
