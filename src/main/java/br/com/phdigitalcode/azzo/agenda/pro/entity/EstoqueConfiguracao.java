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
 * Espelha {@code modules/inventory/domain/entity/EstoqueConfiguracao.java}. Tabela
 * {@code estoque_configuracao} (uma linha por tenant, com {@code id} proprio e {@code tenant_id}
 * unico — nao e PK como em {@code tenant_payment_settings}).
 */
@Entity
@Table(name = "estoque_configuracao")
@Getter
@Setter
public class EstoqueConfiguracao {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, unique = true)
  private UUID tenantId;

  @Column(name = "alerta_estoque_minimo_ativo", nullable = false)
  private Boolean alertaEstoqueMinimoAtivo;

  @Column(name = "bloquear_saida_sem_saldo", nullable = false)
  private Boolean bloquearSaidaSemSaldo;

  @Column(name = "permitir_ajuste_negativo_com_permissao", nullable = false)
  private Boolean permitirAjusteNegativoComPermissao;

  @Column(name = "dias_cobertura_meta", nullable = false)
  private Integer diasCoberturaMeta;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (alertaEstoqueMinimoAtivo == null) alertaEstoqueMinimoAtivo = Boolean.TRUE;
    if (bloquearSaidaSemSaldo == null) bloquearSaidaSemSaldo = Boolean.TRUE;
    if (permitirAjusteNegativoComPermissao == null) permitirAjusteNegativoComPermissao = Boolean.FALSE;
    if (diasCoberturaMeta == null) diasCoberturaMeta = 15;
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EstoqueConfiguracao other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
