package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Espelha {@code modules/lgpd/api/dto/LgpdRequestDtos.java}. */
public final class LgpdRequestDtos {

  private LgpdRequestDtos() {}

  public static class CreateRequest {
    @NotBlank public String requestType;
    @NotBlank public String requesterName;
    @Email @NotBlank public String requesterEmail;
    public String requesterDocument;
    public String description;
  }

  public static class UpdateStatusRequest {
    @NotBlank public String status;
    public String note;
    public String responseSummary;
    public String assignedToUserId;
  }

  public static class ItemResponse {
    public String id;
    public String tenantId;
    public String protocolCode;
    public String requestType;
    public String status;
    public String requesterName;
    public String requesterEmail;
    public String requesterDocument;
    public String description;
    public String responseSummary;
    public String assignedToUserId;
    public String createdByUserId;
    public String createdAt;
    public String updatedAt;
    public String closedAt;
  }

  public static class EventResponse {
    public String id;
    public String requestId;
    public String eventType;
    public String previousStatus;
    public String newStatus;
    public String note;
    public String actorUserId;
    public String createdAt;
  }

  public static class DetailResponse {
    public ItemResponse request;
    public List<EventResponse> events;
  }

  public static class SummaryResponse {
    public long totalOpen;
    public long totalInValidation;
    public long totalResponded;
    public long totalClosed;
    public long unassignedActive;
    public long overdueInitialResponse;
    public long overdueFinalResolution;
    public List<OperationalAlertItem> alerts;
  }

  public static class OperationalAlertItem {
    public String id;
    public String protocolCode;
    public String requestType;
    public String status;
    public String requesterName;
    public String requesterEmail;
    public String assignedToUserId;
    public String assignedToUserName;
    public long ageDays;
    public String overdueType;
    public String createdAt;
  }
}
