package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;

/** Espelha {@code domain/repository/TenantTelegramConfigRepository.java}. */
@Repository
public interface TenantTelegramConfigRepository extends JpaRepository<TenantTelegramConfig, UUID> {

  /**
   * Espelha {@code findByTenantId} do original ({@code Panache.findById}, ja que {@code tenant_id}
   * e a chave primaria da tabela): devolve {@code null} quando ausente, nao {@code Optional}.
   */
  default TenantTelegramConfig findByTenantId(UUID tenantId) {
    return findById(tenantId).orElse(null);
  }

  /** Espelha {@code findByTenantIdOrCreate} do original: cria e persiste se ausente. */
  default TenantTelegramConfig findByTenantIdOrCreate(UUID tenantId) {
    TenantTelegramConfig existing = findByTenantId(tenantId);
    if (existing != null) return existing;

    TenantTelegramConfig created = new TenantTelegramConfig();
    created.setTenantId(tenantId);
    created.setTelegramBotTokenEnc("");
    created.setTelegramEnabled(false);
    return save(created);
  }

  /** Espelha {@code existsByWebhookSecretHash} do original. */
  default boolean existsByWebhookSecretHash(String tokenHash) {
    if (tokenHash == null || tokenHash.isBlank()) return false;
    return existsByTelegramWebhookSecretTokenHash(tokenHash.trim());
  }

  boolean existsByTelegramWebhookSecretTokenHash(String telegramWebhookSecretTokenHash);
}
