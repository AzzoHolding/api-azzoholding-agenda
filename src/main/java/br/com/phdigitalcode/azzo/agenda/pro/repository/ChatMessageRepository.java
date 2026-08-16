package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatMessageEntity;

/** Espelha {@code modules/chat/domain/repository/ChatMessageRepository.java}. */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

  @Query(
      "from ChatMessageEntity m where m.tenantId = :tenantId and m.conversationId = :conversationId "
          + "order by m.createdAt asc, m.id asc")
  List<ChatMessageEntity> listByConversationOrdered(
      @Param("tenantId") UUID tenantId,
      @Param("conversationId") UUID conversationId,
      Pageable pageable);

  @Query(
      "from ChatMessageEntity m where m.tenantId = :tenantId and m.conversationId = :conversationId "
          + "and (m.createdAt > :cursorCreatedAt or (m.createdAt = :cursorCreatedAt and m.id > :cursorId)) "
          + "order by m.createdAt asc, m.id asc")
  List<ChatMessageEntity> listByConversationAfterCursorRaw(
      @Param("tenantId") UUID tenantId,
      @Param("conversationId") UUID conversationId,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") UUID cursorId,
      Pageable pageable);

  long countByTenantIdAndConversationId(UUID tenantId, UUID conversationId);

  Optional<ChatMessageEntity> findByTenantIdAndProviderMessageId(
      UUID tenantId, String providerMessageId);

  /** Espelha {@code chatMessageRepository.delete("tenantId = ?1 and conversationId = ?2", ...)} do original. */
  @Modifying
  @Transactional
  void deleteByTenantIdAndConversationId(UUID tenantId, UUID conversationId);

  @Modifying
  @Transactional
  @Query(
      value =
          "UPDATE chat_messages SET content = NULL, updated_at = NOW() "
              + "WHERE expires_at < :threshold AND content IS NOT NULL",
      nativeQuery = true)
  int clearExpiredContentsRaw(@Param("threshold") Instant threshold);

  @Modifying
  @Transactional
  @Query(
      value =
          "UPDATE chat_messages SET status = 'READ', read_at = :readAt, updated_at = NOW() "
              + "WHERE tenant_id = :tenantId AND conversation_id = :conversationId "
              + "AND direction = 'INBOUND' AND read_at IS NULL",
      nativeQuery = true)
  int markInboundMessagesReadRaw(
      @Param("tenantId") UUID tenantId,
      @Param("conversationId") UUID conversationId,
      @Param("readAt") Instant readAt);

  @Modifying
  @Transactional
  @Query(
      value =
          "UPDATE chat_messages SET expires_at = :expiresAt, updated_at = NOW() "
              + "WHERE tenant_id = :tenantId AND conversation_id = :conversationId "
              + "AND content IS NOT NULL",
      nativeQuery = true)
  int updateExpiresAtByConversationRaw(
      @Param("tenantId") UUID tenantId,
      @Param("conversationId") UUID conversationId,
      @Param("expiresAt") Instant expiresAt);

  default List<ChatMessageEntity> listByConversation(
      UUID tenantId, UUID conversationId, int page, int pageSize) {
    return listByConversationOrdered(
        tenantId, conversationId, PageRequest.of(Math.max(page, 0), Math.max(pageSize, 1)));
  }

  default List<ChatMessageEntity> listByConversationAfterCursor(
      UUID tenantId, UUID conversationId, UUID cursorId, int limit) {
    Pageable pageable = PageRequest.of(0, Math.max(limit, 1));
    if (cursorId == null) {
      return listByConversationOrdered(tenantId, conversationId, pageable);
    }
    Optional<ChatMessageEntity> cursor = findById(cursorId);
    if (cursor.isEmpty()) {
      return listByConversationOrdered(tenantId, conversationId, pageable);
    }
    return listByConversationAfterCursorRaw(
        tenantId, conversationId, cursor.get().getCreatedAt(), cursorId, pageable);
  }

  default long countByConversation(UUID tenantId, UUID conversationId) {
    return countByTenantIdAndConversationId(tenantId, conversationId);
  }

  default long clearExpiredContents(Instant threshold) {
    if (threshold == null) return 0;
    return clearExpiredContentsRaw(threshold);
  }

  default long markInboundMessagesRead(UUID tenantId, UUID conversationId, Instant readAt) {
    if (tenantId == null || conversationId == null || readAt == null) return 0;
    return markInboundMessagesReadRaw(tenantId, conversationId, readAt);
  }

  default long updateExpiresAtByConversation(UUID tenantId, UUID conversationId, Instant expiresAt) {
    if (tenantId == null || conversationId == null || expiresAt == null) return 0;
    return updateExpiresAtByConversationRaw(tenantId, conversationId, expiresAt);
  }
}
