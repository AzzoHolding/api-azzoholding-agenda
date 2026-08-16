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
 * Espelha {@code domain/entity/TenantTelegramConfig.java}. Tabela {@code tenant_telegram_config}
 * (migration {@code V111__create_tenant_telegram_config.sql}, ja copiada em etapa anterior).
 *
 * <p>NOTA DE ESCOPO: o original tem um {@code @OneToOne} para {@code Tenant} mapeado na mesma
 * coluna {@code tenant_id} (somente leitura, {@code insertable = false, updatable = false}) — nao
 * portado aqui porque nenhum consumidor desta fronteira precisa navegar de config para tenant (o
 * inverso, tenant -> config, tambem nao existe no original). Se um consumidor futuro precisar,
 * adicionar o relacionamento entao.
 */
@Entity
@Table(name = "tenant_telegram_config")
@Getter
@Setter
public class TenantTelegramConfig {

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "telegram_bot_token_enc", nullable = false)
  private String telegramBotTokenEnc = "";

  @Column(name = "telegram_bot_username")
  private String telegramBotUsername;

  @Column(name = "telegram_webhook_secret_token_enc")
  private String telegramWebhookSecretTokenEnc;

  @Column(name = "telegram_webhook_secret_token_hash")
  private String telegramWebhookSecretTokenHash;

  @Column(name = "telegram_enabled", nullable = false)
  private boolean telegramEnabled = false;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (telegramBotTokenEnc == null) telegramBotTokenEnc = "";
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
    if (telegramBotTokenEnc == null) telegramBotTokenEnc = "";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TenantTelegramConfig other)) return false;
    return tenantId != null && tenantId.equals(other.tenantId);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
