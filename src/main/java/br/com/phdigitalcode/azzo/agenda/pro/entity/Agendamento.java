package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.converter.StatusAgendamentoConverter;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/scheduling/domain/entity/Agendamento.java}. Tabela {@code appointments}.
 *
 * <p>As associacoes {@code @ManyToOne} para {@code Tenant}/{@code Cliente}/{@code Profissional} do
 * original nao foram portadas: o service resolve cliente e profissional por repositorio explicito
 * (filtrando por tenant), exatamente como o original ja fazia em {@code toResponse}. Restou apenas
 * a colecao {@code items}, que e navegada em praticamente todo metodo do dominio.
 *
 * <p>{@code startTime}/{@code endTime} sao {@code String} ("HH:mm") como no original — a coluna e
 * textual e o formato ja e assumido pelo frontend.
 */
@Entity
@Table(name = "appointments")
@Getter
@Setter
public class Agendamento {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "professional_id", nullable = false)
  private UUID professionalId;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "start_time", nullable = false)
  private String startTime;

  @Column(name = "end_time", nullable = false)
  private String endTime;

  @Convert(converter = StatusAgendamentoConverter.class)
  @Column(name = "status", nullable = false)
  private StatusAgendamento status = StatusAgendamento.PENDING;

  @Column(name = "notes")
  private String notes;

  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "appointment_id", referencedColumnName = "id")
  private List<AgendamentoItem> items = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  public UUID resolvePrimaryServiceId() {
    AgendamentoItem primary = resolvePrimaryItem();
    return primary != null ? primary.getServiceId() : null;
  }

  public BigDecimal resolveEffectiveTotalPrice() {
    if (items != null && !items.isEmpty()) {
      return items.stream()
          .filter(java.util.Objects::nonNull)
          .map(AgendamentoItem::getTotalPrice)
          .reduce(NumericUtil.zero(), NumericUtil::add);
    }
    return NumericUtil.zero();
  }

  public AgendamentoItem resolvePrimaryItem() {
    if (items == null || items.isEmpty()) return null;
    return items.stream()
        .filter(java.util.Objects::nonNull)
        .min(
            Comparator.comparing(
                    (AgendamentoItem item) -> item.getCreatedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AgendamentoItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .orElse(null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Agendamento other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
