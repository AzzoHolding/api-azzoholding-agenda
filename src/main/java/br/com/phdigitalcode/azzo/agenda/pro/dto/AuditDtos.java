package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.util.ArrayList;
import java.util.List;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditRetentionEvent;

/**
 * Espelha {@code modules/audit/api/dto/AuditDtos.java}. Campos publicos porque o original tambem
 * os expoe assim e o contrato JSON precisa bater com o frontend.
 */
public final class AuditDtos {

  private AuditDtos() {}

  public static class AggregationItem {
    public String key;
    public long count;
  }

  public static class Aggregations {
    public List<AggregationItem> byModule = new ArrayList<>();
    public List<AggregationItem> byStatus = new ArrayList<>();
    public List<AggregationItem> byAction = new ArrayList<>();
  }

  public static class AuditEventListItem {
    public String id;
    public String tenantId;
    public String actorUserId;
    public String actorName;
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
    public boolean alterado;
    public List<String> camposAlterados = new ArrayList<>();
  }

  public static class AuditEventDetail extends AuditEventListItem {
    public String errorMessage;
    public Object before;
    public Object after;
    public Object metadata;
    public String eventHash;
    public String prevEventHash;
    public boolean chainValid;
  }

  public static class AuditSearchResponse {
    public List<AuditEventListItem> items = new ArrayList<>();
    public String nextCursor;
    public int limit;
    public boolean hasNext;
    public Aggregations aggregations = new Aggregations();
  }

  public static class AuditFilterOptionsResponse {
    public List<String> modules = new ArrayList<>();
    public List<String> statuses = new ArrayList<>();
    public List<String> actions = new ArrayList<>();
    public List<String> entityTypes = new ArrayList<>();
    public List<String> sourceChannels = new ArrayList<>();
  }

  public static class AuditRetentionPageResponse {
    public List<AuditRetentionEvent> items = new ArrayList<>();
    public String nextCursor;
    public boolean hasNext;
  }

  public static class AuditExportRequest {
    public String from;
    public String to;
    public List<String> modules = new ArrayList<>();
    public List<String> actions = new ArrayList<>();
    public List<String> statuses = new ArrayList<>();
    public List<String> entityTypes = new ArrayList<>();
    public String entityId;
    public List<String> actorUserIds = new ArrayList<>();
    public String requestId;
    public List<String> sourceChannels = new ArrayList<>();
    public String ip;
    public Boolean hasChanges;
    public String text;
    public String format;
    public List<String> columns = new ArrayList<>();
  }

  public static class AuditExportResponse {
    public String exportId;
    public String format;
    public String downloadUrl;
    public String expiresAt;
    public String checksumSha256;
  }
}
