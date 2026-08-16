package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;

/** Espelha {@code domain/repository/TenantWhatsAppConfigRepository.java}. */
@Repository
public interface TenantWhatsAppConfigRepository extends JpaRepository<TenantWhatsAppConfig, UUID> {

  /**
   * Espelha {@code findByTenantIdOrCreate} do original: os defaults ({@code canSchedule=true},
   * {@code canCancel=true}, {@code canReschedule=true}, {@code whatsappEnabled=false},
   * {@code whatsappAccessTokenEnc=""}) ja sao os defaults de campo/{@code @PrePersist} da entidade
   * aqui, entao basta persistir um registro novo com o {@code tenantId}.
   */
  default TenantWhatsAppConfig findByTenantIdOrCreate(UUID tenantId) {
    return findById(tenantId).orElseGet(() -> {
      TenantWhatsAppConfig created = new TenantWhatsAppConfig();
      created.setTenantId(tenantId);
      return save(created);
    });
  }

  List<TenantWhatsAppConfig> findByWhatsappPhoneNumberId(String whatsappPhoneNumberId);

  boolean existsByWhatsappWebhookVerifyTokenHash(String whatsappWebhookVerifyTokenHash);

  /** Espelha {@code findByPhoneNumberId} do {@code WhatsAppWebhookResource} original. */
  default Optional<TenantWhatsAppConfig> findByPhoneNumberId(String phoneNumberId) {
    if (phoneNumberId == null || phoneNumberId.isBlank()) return Optional.empty();
    return findByWhatsappPhoneNumberId(phoneNumberId.trim()).stream().findFirst();
  }

  /** Espelha {@code existsByWebhookVerifyTokenHash} do original. */
  default boolean existsByWebhookVerifyTokenHash(String tokenHash) {
    if (tokenHash == null || tokenHash.isBlank()) return false;
    return existsByWhatsappWebhookVerifyTokenHash(tokenHash.trim());
  }
}
