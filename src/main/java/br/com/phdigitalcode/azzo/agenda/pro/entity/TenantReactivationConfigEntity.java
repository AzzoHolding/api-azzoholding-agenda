package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/chat/domain/entity/TenantReactivationConfigEntity.java}. */
@Entity
@Table(name = "tenant_reactivation_config")
@Getter
@Setter
public class TenantReactivationConfigEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, unique = true)
  private UUID tenantId;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "abandonment_delay_minutes", nullable = false)
  private int abandonmentDelayMinutes = 30;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts = 3;

  @Column(name = "attempt_1_delay_days", nullable = false)
  private int attempt1DelayDays = 2;

  @Column(name = "attempt_2_delay_days", nullable = false)
  private int attempt2DelayDays = 4;

  @Column(name = "attempt_3_delay_days", nullable = false)
  private int attempt3DelayDays = 7;

  @Column(name = "send_window_start", nullable = false)
  private LocalTime sendWindowStart = LocalTime.of(8, 0);

  @Column(name = "send_window_end", nullable = false)
  private LocalTime sendWindowEnd = LocalTime.of(20, 0);

  /** Limite fixo LGPD: não pode ultrapassar 4 mensagens/mês por cliente. */
  @Column(name = "max_messages_per_month_per_client", nullable = false)
  private int maxMessagesPerMonthPerClient = 4;

  /** Limite fixo LGPD: intervalo mínimo de 7 dias entre mensagens. */
  @Column(name = "min_interval_days", nullable = false)
  private int minIntervalDays = 7;

  @Column(name = "template_attempt_1", columnDefinition = "TEXT")
  private String templateAttempt1;

  @Column(name = "template_attempt_2", columnDefinition = "TEXT")
  private String templateAttempt2;

  @Column(name = "template_attempt_3", columnDefinition = "TEXT")
  private String templateAttempt3;

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
