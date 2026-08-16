package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code br.com.phdigitalcode.azzo.agenda.pro.domain.entity.Usuario} (Quarkus/Panache).
 *
 * <p>Tabela {@code users}, schema {@code azzo_app}. IDs sao UUID atribuidos programaticamente
 * (sem {@code @GeneratedValue}), preservando o padrao do projeto original.
 *
 * <p>NOTA DE ESCOPO: a coluna {@code tenant_id} referencia a tabela {@code tenants}, cujo modulo
 * ({@code tenant}) ainda nao foi migrado nesta etapa. Por isso o relacionamento
 * {@code @ManyToOne Tenant tenant} do Quarkus original foi omitido aqui de proposito — mantemos
 * apenas a coluna {@code tenantId} (UUID), que e o unico dado usado pelos dominios
 * {@code security/common} e {@code auth}. Reintroduzir o relacionamento quando o modulo
 * {@code tenant} for portado.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class Usuario {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "phone")
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private PapelUsuario role;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "avatar")
  private String avatar;

  @Column(name = "mfa_enabled", nullable = false)
  private boolean mfaEnabled;

  @Column(name = "mfa_secret_enc")
  private String mfaSecretEnc;

  /**
   * Permite revogacao imediata de todos os JWTs emitidos antes deste instante. Atualizado em
   * revokeAllForUser (troca de senha, logout-everywhere).
   */
  @Column(name = "tokens_revoked_before")
  private Instant tokensRevokedBefore;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (role == null) role = PapelUsuario.OWNER;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Usuario usuario)) return false;
    return id != null && id.equals(usuario.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
