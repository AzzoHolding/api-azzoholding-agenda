package br.com.phdigitalcode.azzo.agenda.pro.dto.chat;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Espelha {@code modules/chat/api/dto/ChatDtos.java}. */
public final class ChatDtos {

  private ChatDtos() {}

  public static class Conversation {
    public String id;
    public String clientId;
    public String clientName;
    public String clientAvatar;
    public String clientPhoneMasked;
    public String clientProfileImageUrl;
    public String channel;
    public String appointmentMarker;
    public String lastMessageAt;
    public String lastMessagePreview;
    public int unreadCount;
    public String updatedAt;
    public String manualModeUntil;
    public boolean manualModeEnabled;
  }

  public static class ConversationListResponse {
    public List<Conversation> items = new ArrayList<>();
    public long total;
    public int page;
    public int pageSize;
  }

  public static class Message {
    public String id;
    public String conversationId;
    public String clientId;
    public String direction;
    public String content;
    public String status;
    public String providerMessageId;
    public String providerErrorCode;
    public String providerErrorMessage;
    public String sentAt;
    public String deliveredAt;
    public String readAt;
    public String failedAt;
    public String createdAt;
  }

  public static class MessageListResponse {
    public List<Message> items = new ArrayList<>();
    public long total;
    public int page;
    public int pageSize;
    public String nextCursor;
    public boolean hasNext;
  }

  public static class SendMessageRequest {
    @NotNull public String clientId;
    @NotBlank public String content;
  }

  public static class SendMessageResponse {
    public String messageId;
    public String conversationId;
    public String status;
    public String providerErrorMessage;
  }

  public static class UpdateMarkerRequest {
    @NotBlank public String appointmentMarker;
  }

  public static class UpdateMarkerResponse {
    public String conversationId;
    public String appointmentMarker;
    public String updatedAt;
  }
}
