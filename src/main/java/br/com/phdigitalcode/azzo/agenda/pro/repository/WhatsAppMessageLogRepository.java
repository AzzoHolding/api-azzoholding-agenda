package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;

/** Espelha {@code modules/tenant/domain/repository/WhatsAppMessageLogRepository.java}. */
@Repository
public interface WhatsAppMessageLogRepository extends JpaRepository<WhatsAppMessageLogEntity, UUID> {

  /**
   * Espelha {@code find("tenantId = ?1 order by sentAt desc", tenantId).page(0, limit)} do
   * original.
   */
  List<WhatsAppMessageLogEntity> findByTenantIdOrderBySentAtDesc(UUID tenantId, Pageable pageable);

  /**
   * Apaga numero e texto das mensagens enviadas ao titular anonimizado.
   *
   * Mesmo caso das notificacoes: o vinculo e pelo agendamento, e {@code destination_phone} nao
   * aceita nulo.
   */
  @Modifying
  @Transactional
  @Query(
      value =
          "UPDATE whatsapp_message_log SET destination_phone = '[ANONIMIZADO]', "
              + "message_text = NULL, error_message = NULL "
              + "WHERE tenant_id = :tenantId AND appointment_id IN "
              + "(SELECT a.id FROM appointments a WHERE a.tenant_id = :tenantId "
              + "AND a.client_id = :clientId)",
      nativeQuery = true)
  int anonimizarPorClienteRaw(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);
}
