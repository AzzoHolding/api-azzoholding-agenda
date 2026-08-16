package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;

/** Espelha {@code modules/tenant/domain/repository/WhatsAppMessageLogRepository.java}. */
@Repository
public interface WhatsAppMessageLogRepository extends JpaRepository<WhatsAppMessageLogEntity, UUID> {

  /**
   * Espelha {@code find("tenantId = ?1 order by sentAt desc", tenantId).page(0, limit)} do
   * original.
   */
  List<WhatsAppMessageLogEntity> findByTenantIdOrderBySentAtDesc(UUID tenantId, Pageable pageable);
}
