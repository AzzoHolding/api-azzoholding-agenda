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
 * Espelha {@code modules/audit/domain/entity/AuditEvent.java}. Tabela {@code audit_events}.
 *
 * <p><b>Escopo</b>: portada aqui apenas para o lado de <em>leitura</em> — a timeline de um
 * agendamento ({@code GET /api/v1/appointments/{id}}) le desta tabela. A escrita continua no
 * placeholder {@code integration/AuditService}, que hoje so loga; enquanto isso, a timeline
 * responde vazia para eventos gerados por esta aplicacao. Ao portar o modulo {@code audit}
 * completo (encadeamento de hash, retencao, LGPD), esta entidade e o repositorio de leitura ja
 * estao no formato final e nao precisam mudar.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "actor_role")
  private String actorRole;

  @Column(name = "module", nullable = false)
  private String module;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "entity_type")
  private String entityType;

  @Column(name = "entity_id")
  private String entityId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "request_id", nullable = false)
  private String requestId;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "source_channel", nullable = false)
  private String sourceChannel;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "user_agent")
  private String userAgent;

  @Column(name = "before_json")
  private String beforeJson;

  @Column(name = "after_json")
  private String afterJson;

  @Column(name = "metadata_json")
  private String metadataJson;

  @Column(name = "has_changes", nullable = false)
  private boolean hasChanges;

  @Column(name = "changed_fields_json")
  private String changedFieldsJson;

  @Column(name = "event_hash", nullable = false)
  private String eventHash;

  @Column(name = "prev_event_hash")
  private String prevEventHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AuditEvent other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
