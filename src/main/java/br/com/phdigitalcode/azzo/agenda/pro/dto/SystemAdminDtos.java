package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Espelha {@code modules/admin/api/dto/SystemAdminDtos.java}. */
public final class SystemAdminDtos {

  private SystemAdminDtos() {}

  public static class CommercialOverviewResponse {
    public long totalTenants;
    public long totalSignups30d;
    public long payingTenants;
    public long activeTenants;
    public long expiredTenants;
    public long suspendedTenants;
    public double conversionRatePercent;
    public BigDecimal revenueReceived30d;
    public BigDecimal pendingAmount;
    public List<TenantPlanStatusItem> tenantsByPlanStatus = new ArrayList<>();
  }

  public static class TenantPlanStatusItem {
    public String planStatus;
    public long count;
  }

  public static class GlobalAuditListResponse {
    public List<GlobalAuditItem> items = new ArrayList<>();
    public int limit;
  }

  public static class GlobalAuditItem {
    public String id;
    public String tenantId;
    public String tenantName;
    public String actorUserId;
    public String actorRole;
    public String module;
    public String action;
    public String entityType;
    public String entityId;
    public String status;
    public String errorCode;
    public String requestId;
    public String sourceChannel;
    public String ipAddress;
    public String createdAt;
  }

  public static class GlobalAuditDetailResponse extends GlobalAuditItem {
    public String errorMessage;
    public String userAgent;
    public String beforeJson;
    public String afterJson;
    public String metadataJson;
    public boolean hasChanges;
    public String changedFieldsJson;
    public String eventHash;
    public String prevEventHash;
  }

  public static class RevokeSessionsRequest {
    public String tenantId;
    public String userId;
  }

  public static class RevokeSessionsResponse {
    public String status;
    public String message;
    public String tenantId;
    public String userId;
    public long revokedCount;
  }

  public static class SessionListResponse {
    public List<SessionItem> items = new ArrayList<>();
    public int limit;
  }

  public static class SessionItem {
    public String refreshTokenId;
    public String tenantId;
    public String tenantName;
    public String userId;
    public String userName;
    public String userEmail;
    public String userRole;
    public String createdAt;
    public String expiresAt;
    public String revokedAt;
    public boolean active;
  }

  public static class RevokeSessionTokenRequest {
    public String refreshTokenId;
    public String tenantId;
  }

  public static class MfaResetRequest {
    public String tenantId;
    public String reason;
    public Boolean revokeSessions;
  }

  public static class MfaResetResponse {
    public String status;
    public String message;
    public String tenantId;
    public String userId;
    public boolean mfaWasEnabled;
    public boolean mfaWasEnrolled;
    public long revokedCount;
  }

  public static class GlobalSuggestionListResponse {
    public List<GlobalSuggestionItem> items = new ArrayList<>();
    public int limit;
  }

  public static class GlobalSuggestionItem {
    public String id;
    public String tenantId;
    public String tenantName;
    public String userId;
    public String userName;
    public String userRole;
    public String category;
    public String title;
    public String message;
    public String status;
    public String adminResponse;
    public String respondedByUserId;
    public String respondedByUserName;
    public String respondedAt;
    public String closedAt;
    public String sourcePage;
    public String createdAt;
    public String updatedAt;
  }

  public static class SuggestionUpdateRequest {
    public String adminResponse;
    public String status;
  }

  public static class PlanListResponse {
    public List<PlanItem> items = new ArrayList<>();
  }

  public static class PlanItem {
    public String id;
    public String name;
    public String description;
    public String currency;
    public BigDecimal price;
    public int validityMonths;
    public Integer validityDays;
    public String highlight;
    public String featuresJson;
    public boolean active;
    public boolean trial;
    public int priority;
    public Integer maxProfessionals;
    // Plano exclusivo para venda interna (vendedores/staff): nao aparece na
    // contratacao publica e so pode ser ativado por fluxo interno autorizado.
    public boolean exclusivoVendaInterna;
    public String createdAt;
    public String updatedAt;
  }

  public static class PlanUpsertRequest {
    public String name;
    public String description;
    public String currency;
    public BigDecimal price;
    @Deprecated
    public BigDecimal priceCents;
    public Integer validityMonths;
    public Integer validityDays;
    public String highlight;
    public String featuresJson;
    public Boolean active;
    public Boolean trial;
    public Integer priority;
    public Integer maxProfessionals;
    public Boolean exclusivoVendaInterna;
  }

  public static class PlanStatusUpdateRequest {
    public Boolean active;
  }

  public static class EmailTemplateListResponse {
    public List<EmailTemplateSummaryItem> items = new ArrayList<>();
  }

  public static class EmailTemplateSummaryItem {
    public String templateType;
    public String label;
    public boolean configured;
    public boolean active;
    public String updatedAt;
  }

  public static class EmailTemplateDetailResponse {
    public String templateType;
    public String label;
    public boolean configured;
    public boolean active;
    public String fromEmail;
    public String fromName;
    public String replyTo;
    public String subjectTemplate;
    public String htmlTemplate;
    public List<String> placeholders = new ArrayList<>();
    public String updatedAt;
    public String updatedBy;
    public Map<String, String> sampleValues = new LinkedHashMap<>();
  }

  public static class EmailTemplateUpsertRequest {
    public Boolean active;
    public String fromEmail;
    public String fromName;
    public String replyTo;
    public String subjectTemplate;
    public String htmlTemplate;
  }

  public static class EmailTemplateStatusUpdateRequest {
    public Boolean active;
  }

  public static class EmailTemplatePreviewResponse {
    public String templateType;
    public String label;
    public String subject;
    public String html;
    public String fromEmail;
    public String fromName;
    public String replyTo;
    public List<String> placeholders = new ArrayList<>();
    public Map<String, String> sampleValues = new LinkedHashMap<>();
  }
}
