package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
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

import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;

/** Espelha {@code modules/chat/domain/repository/ChatConversationRepository.java}. */
@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversationEntity, UUID> {

  @Modifying
  @Transactional
  @Query(
      value =
          "UPDATE chat_conversations SET unread_count = unread_count + 1, updated_at = NOW() "
              + "WHERE tenant_id = :tenantId AND id = :id",
      nativeQuery = true)
  int incrementUnreadRaw(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

  @Modifying
  @Transactional
  @Query(
      value =
          "UPDATE chat_conversations SET unread_count = 0, updated_at = NOW() "
              + "WHERE tenant_id = :tenantId AND id = :id AND unread_count > 0",
      nativeQuery = true)
  int clearUnreadRaw(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

  Optional<ChatConversationEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<ChatConversationEntity> findByTenantIdAndClientIdAndChannel(
      UUID tenantId, UUID clientId, ChatChannel channel);

  @Query(
      "from ChatConversationEntity c where c.tenantId = :tenantId and c.clientId = :clientId "
          + "order by c.lastMessageAt desc nulls last, c.createdAt desc")
  List<ChatConversationEntity> listLatestByTenantAndClient(
      @Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, Pageable pageable);

  Optional<ChatConversationEntity> findByTenantIdAndExternalContactIdAndChannel(
      UUID tenantId, String externalContactId, ChatChannel channel);

  @Query(
      "from ChatConversationEntity c where c.tenantId = :tenantId "
          + "order by c.lastMessageAt desc nulls last, c.createdAt desc")
  List<ChatConversationEntity> listByTenantOrdered(
      @Param("tenantId") UUID tenantId, Pageable pageable);

  long countByTenantId(UUID tenantId);

  @Query(
      "from ChatConversationEntity c where c.tenantId = :tenantId and exists "
          + "(select 1 from ChatMessageEntity m where m.conversationId = c.id "
          + "and m.createdAt >= :dayStart and m.createdAt < :nextDayStart) "
          + "order by c.lastMessageAt desc nulls last, c.createdAt desc")
  List<ChatConversationEntity> listTodayByTenantOrdered(
      @Param("tenantId") UUID tenantId,
      @Param("dayStart") Instant dayStart,
      @Param("nextDayStart") Instant nextDayStart,
      Pageable pageable);

  @Query(
      "select count(c) from ChatConversationEntity c where c.tenantId = :tenantId and exists "
          + "(select 1 from ChatMessageEntity m where m.conversationId = c.id "
          + "and m.createdAt >= :dayStart and m.createdAt < :nextDayStart)")
  long countTodayByTenant(
      @Param("tenantId") UUID tenantId,
      @Param("dayStart") Instant dayStart,
      @Param("nextDayStart") Instant nextDayStart);

  @Query(
      value =
          """
          SELECT
            c.id, c.client_id, c.channel, c.external_contact_id,
            c.appointment_marker, c.last_message_at, c.last_message_preview,
            c.manual_mode_until, c.manual_mode_by_user_id, c.manual_mode_reason,
            c.unread_count, c.created_at, c.updated_at,
            cl.name AS client_name, cl.phone AS client_phone, cl.avatar AS client_avatar
          FROM chat_conversations c
          LEFT JOIN clients cl ON cl.id = c.client_id
          WHERE c.tenant_id = :tenantId
          ORDER BY c.last_message_at DESC NULLS LAST, c.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<Object[]> listPagedWithClientRaw(
      @Param("tenantId") UUID tenantId, @Param("limit") int limit, @Param("offset") int offset);

  @Query(
      value =
          """
          SELECT
            c.id, c.client_id, c.channel, c.external_contact_id,
            c.appointment_marker, c.last_message_at, c.last_message_preview,
            c.manual_mode_until, c.manual_mode_by_user_id, c.manual_mode_reason,
            c.unread_count, c.created_at, c.updated_at,
            cl.name AS client_name, cl.phone AS client_phone, cl.avatar AS client_avatar
          FROM chat_conversations c
          LEFT JOIN clients cl ON cl.id = c.client_id
          WHERE c.tenant_id = :tenantId
            AND EXISTS (
              SELECT 1 FROM chat_messages m
              WHERE m.conversation_id = c.id
                AND m.created_at >= :dayStart
                AND m.created_at < :nextDayStart
            )
          ORDER BY c.last_message_at DESC NULLS LAST, c.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<Object[]> listTodayPagedWithClientRaw(
      @Param("tenantId") UUID tenantId,
      @Param("dayStart") Instant dayStart,
      @Param("nextDayStart") Instant nextDayStart,
      @Param("limit") int limit,
      @Param("offset") int offset);

  default long incrementUnread(UUID tenantId, UUID conversationId) {
    if (tenantId == null || conversationId == null) return 0;
    return incrementUnreadRaw(tenantId, conversationId);
  }

  default long clearUnread(UUID tenantId, UUID conversationId) {
    if (tenantId == null || conversationId == null) return 0;
    return clearUnreadRaw(tenantId, conversationId);
  }

  default Optional<ChatConversationEntity> findByTenantAndId(UUID tenantId, UUID id) {
    if (tenantId == null || id == null) return Optional.empty();
    return findByTenantIdAndId(tenantId, id);
  }

  default Optional<ChatConversationEntity> findByTenantClientAndChannel(
      UUID tenantId, UUID clientId, ChatChannel channel) {
    if (tenantId == null || clientId == null || channel == null) return Optional.empty();
    return findByTenantIdAndClientIdAndChannel(tenantId, clientId, channel);
  }

  default Optional<ChatConversationEntity> findLatestByTenantAndClient(UUID tenantId, UUID clientId) {
    if (tenantId == null || clientId == null) return Optional.empty();
    List<ChatConversationEntity> result =
        listLatestByTenantAndClient(tenantId, clientId, PageRequest.of(0, 1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }

  default Optional<ChatConversationEntity> findByTenantAndExternalContactId(
      UUID tenantId, String externalContactId) {
    return findByTenantAndExternalContactId(tenantId, externalContactId, ChatChannel.WHATSAPP);
  }

  default Optional<ChatConversationEntity> findByTenantAndExternalContactId(
      UUID tenantId, String externalContactId, ChatChannel channel) {
    if (tenantId == null
        || externalContactId == null
        || externalContactId.isBlank()
        || channel == null) {
      return Optional.empty();
    }
    return findByTenantIdAndExternalContactIdAndChannel(
        tenantId, externalContactId.trim(), channel);
  }

  default List<ChatConversationEntity> listPaged(UUID tenantId, int page, int pageSize) {
    return listByTenantOrdered(tenantId, PageRequest.of(Math.max(page, 0), Math.max(pageSize, 1)));
  }

  default long countByTenant(UUID tenantId) {
    return countByTenantId(tenantId);
  }

  default List<ChatConversationEntity> listTodayPaged(
      UUID tenantId, Instant dayStart, Instant nextDayStart, int page, int pageSize) {
    return listTodayByTenantOrdered(
        tenantId, dayStart, nextDayStart, PageRequest.of(Math.max(page, 0), Math.max(pageSize, 1)));
  }

  default List<ConversationWithClientRow> listPagedWithClient(UUID tenantId, int offset, int limit) {
    return listPagedWithClientRaw(tenantId, limit, offset).stream()
        .map(ConversationWithClientRow::from)
        .toList();
  }

  default List<ConversationWithClientRow> listTodayPagedWithClient(
      UUID tenantId, Instant dayStart, Instant nextDayStart, int offset, int limit) {
    return listTodayPagedWithClientRaw(tenantId, dayStart, nextDayStart, limit, offset).stream()
        .map(ConversationWithClientRow::from)
        .toList();
  }

  record ConversationWithClientRow(
      UUID id,
      UUID clientId,
      String channel,
      String externalContactId,
      String appointmentMarker,
      Instant lastMessageAt,
      String lastMessagePreview,
      Instant manualModeUntil,
      UUID manualModeByUserId,
      String manualModeReason,
      int unreadCount,
      Instant createdAt,
      Instant updatedAt,
      String clientName,
      String clientPhone,
      String clientAvatar) {

    static ConversationWithClientRow from(Object[] r) {
      return new ConversationWithClientRow(
          toUuid(r[0]),
          toUuid(r[1]),
          str(r[2]),
          str(r[3]),
          str(r[4]),
          toInstant(r[5]),
          str(r[6]),
          toInstant(r[7]),
          toUuid(r[8]),
          str(r[9]),
          r[10] != null ? ((Number) r[10]).intValue() : 0,
          toInstant(r[11]),
          toInstant(r[12]),
          str(r[13]),
          str(r[14]),
          str(r[15]));
    }

    private static UUID toUuid(Object o) {
      if (o == null) return null;
      if (o instanceof UUID u) return u;
      try {
        return UUID.fromString(o.toString());
      } catch (Exception e) {
        return null;
      }
    }

    private static Instant toInstant(Object o) {
      if (o == null) return null;
      if (o instanceof Instant i) return i;
      if (o instanceof Timestamp ts) return ts.toInstant();
      if (o instanceof OffsetDateTime odt) return odt.toInstant();
      return null;
    }

    private static String str(Object o) {
      return o != null ? o.toString() : null;
    }
  }
}
