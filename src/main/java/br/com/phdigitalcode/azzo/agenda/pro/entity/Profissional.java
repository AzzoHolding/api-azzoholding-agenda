package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/professionals/domain/entity/Profissional.java}. Tabela
 * {@code professionals}.
 *
 * <p>DECISOES DELIBERADAS (cuidado com Lombok/relacionamentos, ver regras de qualidade):
 * <ul>
 *   <li>Sem {@code @ManyToOne Tenant}/{@code @OneToOne Usuario} (o original mapeia ambos so para
 *       leitura de conveniencia) — mantemos apenas {@code tenantId}/{@code userId} escalares,
 *       igual ao padrao ja adotado em {@code Usuario}/{@code Cliente};</li>
 *   <li>{@code specialties} e {@code @ManyToMany} UNIDIRECIONAL (dono, {@code FetchType.EAGER}
 *       porque toda leitura de profissional precisa das especialidades para montar a resposta,
 *       evitando {@code LazyInitializationException} fora de uma transacao) — o lado inverso
 *       {@code Specialty.professionals} do original nao foi replicado (nunca usado);</li>
 *   <li>{@code servicos}/{@code workingHours} do original (colecoes de leitura) nao sao
 *       mapeadas aqui — {@code ServicoServicos} navega a partir de {@code Servico.profissionais}
 *       (dono do relacionamento) e {@code ProfissionalWorkingHourRepository} é consultado
 *       diretamente por {@code professionalId}, exatamente como o service original faz.</li>
 * </ul>
 */
@Entity
@Table(name = "professionals")
@Getter
@Setter
public class Profissional {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "email")
  private String email;

  @Column(name = "phone")
  private String phone;

  @Column(name = "avatar")
  private String avatar;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "professional_specialties",
      joinColumns = @JoinColumn(name = "professional_id"),
      inverseJoinColumns = @JoinColumn(name = "specialty_id"))
  private Set<Specialty> specialties = new HashSet<>();

  @Column(name = "commission_rate", nullable = false)
  private BigDecimal commissionRate = BigDecimal.ZERO;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

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
    if (!(o instanceof Profissional other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
