package br.com.phdigitalcode.azzo.agenda.pro.dto.notification;

import java.util.List;

/**
 * Espelha {@code modules/notifications/api/dto/NotificationResponse.java} e
 * {@code NotificationListResponse.java}.
 */
public final class NotificationDtos {

  private NotificationDtos() {}

  public static class NotificationResponse {
    public String id;
    public String tenantId;
    public String appointmentId;
    public String professionalId;
    public String channel;
    public String destination;
    public String message;
    public String status;
    public String errorMessage;
    public String sentAt;
    public String viewedAt;
    public boolean viewed;
    public String createdAt;
  }

  public static class NotificationListResponse {
    public List<NotificationResponse> items;
    public String nextCursorCreatedAt;
    public String nextCursorId;
    public boolean hasMore;
  }
}
