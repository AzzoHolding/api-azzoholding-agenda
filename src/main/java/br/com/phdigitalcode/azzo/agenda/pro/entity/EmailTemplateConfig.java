package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/email/domain/entity/EmailTemplateConfig.java} (tabela
 * {@code email_template_configs}, V30).
 *
 * <p>Faz parte do modulo {@code email}, que <b>nao</b> foi migrado por inteiro: portou-se apenas o
 * subconjunto de <i>templates</i> (esta entidade, o repositorio, o enum e o
 * {@code EmailTemplateRendererService}), porque os tres endpoints
 * {@code /api/v1/settings/email-templates} do {@code SettingsResource} dependem dele. A fila/worker
 * de envio ({@code EmailJob}, {@code EmailJobScheduler}, {@code EmailJobExecutor}) continua fora —
 * ver o placeholder {@code integration/EmailJobService}.
 *
 * <p>{@code tenantId} nulo identifica o template <b>global</b> (fallback de todos os tenants).
 */
@Entity
@Table(name = "email_template_configs")
@Getter
@Setter
public class EmailTemplateConfig {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  /** Nulo = template global (fallback). */
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "template_type", nullable = false, length = 50)
  private EmailTemplateType templateType;

  @Column(name = "from_email", length = 255)
  private String fromEmail;

  @Column(name = "from_name", length = 255)
  private String fromName;

  @Column(name = "reply_to", length = 255)
  private String replyTo;

  @Column(name = "subject_template", nullable = false, columnDefinition = "TEXT")
  private String subjectTemplate;

  @Column(name = "html_template", nullable = false, columnDefinition = "TEXT")
  private String htmlTemplate;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
