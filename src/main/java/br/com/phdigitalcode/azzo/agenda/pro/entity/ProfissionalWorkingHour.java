package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/professionals/domain/entity/ProfissionalWorkingHour.java}. Tabela {@code professional_working_hours}. */
@Entity
@Table(name = "professional_working_hours")
@Getter
@Setter
public class ProfissionalWorkingHour {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "professional_id", nullable = false)
  private UUID professionalId;

  @Column(name = "day_of_week", nullable = false)
  private int dayOfWeek;

  // Grava o LocalTime direto pelo driver, sem o hibernate.jdbc.time_zone=UTC: o horario ia com +3h
  // (09:00 virava 12:00; 'fecha 23:00' virava 02:00 e atravessava a meia-noite). V151 corrige os dados.
  @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType.class)
  @Column(name = "start_time")
  private LocalTime startTime;

  @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType.class)
  @Column(name = "end_time")
  private LocalTime endTime;

  @Column(name = "is_working", nullable = false)
  private boolean isWorking = true;

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
    if (!(o instanceof ProfissionalWorkingHour other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
