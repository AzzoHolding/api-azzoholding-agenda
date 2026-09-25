package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
   * O log com filtro de situacao e periodo.
   *
   * <p>JPQL, e nao consulta nativa: em nativa o {@code :de is null} nao da ao Postgres como deduzir
   * o tipo do parametro e ele recusa a consulta INTEIRA — foi o que quebrou os filtros da auditoria
   * em 2026-09-20. Em JPQL o Hibernate tipa o parametro, e o padrao funciona.
   */
  @Query(
      "select m from WhatsAppMessageLogEntity m where m.tenantId = :tenantId "
          + "and (:status is null or m.status = :status) "
          + "and (:de is null or m.sentAt >= :de) "
          + "and (:ate is null or m.sentAt <= :ate) "
          + "order by m.sentAt desc")
  List<WhatsAppMessageLogEntity> filtrar(
      @Param("tenantId") UUID tenantId,
      @Param("status") String status,
      @Param("de") Instant de,
      @Param("ate") Instant ate,
      Pageable pageable);

  /**
   * A linha do log que corresponde a um {@code wamid}.
   *
   * <p>E o que liga o status que a Meta manda pelo webhook a mensagem que aparece na tela: sem
   * isso, "Aceita" nunca virava "Entregue" nem "Falhou", e o motivo da nao-entrega existia so na
   * resposta da Meta que ninguem lia.
   */
  Optional<WhatsAppMessageLogEntity> findByTenantIdAndProviderMessageId(
      UUID tenantId, String providerMessageId);

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
