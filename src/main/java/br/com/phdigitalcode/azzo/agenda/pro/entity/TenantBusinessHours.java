package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/settings/domain/entity/TenantBusinessHoursEntity.java} (tabela relacional
 * {@code tenant_business_hours}, V75/V76, com pausa adicionada em V84).
 *
 * <p><b>Unica entidade deste modulo com {@code @GeneratedValue}</b> — o original tambem usa, ao
 * contrario do padrao do projeto (UUID atribuido em {@code @PrePersist}). Preservado como esta.
 *
 * <p>Os metodos estaticos {@code findByTenant}/{@code findByTenantAndDay} do Panache viraram
 * metodos do {@link br.com.phdigitalcode.azzo.agenda.pro.repository.TenantBusinessHoursRepository}.
 */
@Entity
@Table(name = "tenant_business_hours")
@Getter
@Setter
public class TenantBusinessHours {

  @Id
  @GeneratedValue
  @Column(name = "id")
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "day_of_week", nullable = false, length = 10)
  private String dayOfWeek;

  @Column(name = "open_time")
  private LocalTime openTime;

  @Column(name = "close_time")
  private LocalTime closeTime;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  // Pausa (break) — adicionados em V84

  @Column(name = "break_start")
  private LocalTime breakStart;

  @Column(name = "break_end")
  private LocalTime breakEnd;
}
