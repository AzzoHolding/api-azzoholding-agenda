package br.com.phdigitalcode.azzo.agenda.pro.dto.chat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Espelha {@code modules/chat/api/dto/ReactivationDtos.java}. */
public class ReactivationDtos {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class TenantReactivationConfigResponse {
    public UUID tenantId;
    public boolean enabled;
    public int abandonmentDelayMinutes;
    public int maxAttempts;
    public int attempt1DelayDays;
    public int attempt2DelayDays;
    public int attempt3DelayDays;
    public LocalTime sendWindowStart;
    public LocalTime sendWindowEnd;
    public int maxMessagesPerMonthPerClient;
    public int minIntervalDays;
    public String templateAttempt1;
    public String templateAttempt2;
    public String templateAttempt3;
    public Instant updatedAt;
  }

  public static class UpdateReactivationConfigRequest {
    public Boolean enabled;

    @Min(15)
    public Integer abandonmentDelayMinutes;

    @Min(1) @Max(3)
    public Integer maxAttempts;

    @Min(1)
    public Integer attempt1DelayDays;

    @Min(2)
    public Integer attempt2DelayDays;

    @Min(3)
    public Integer attempt3DelayDays;

    public LocalTime sendWindowStart;
    public LocalTime sendWindowEnd;

    /** Maximo configuravel: 4 (LGPD) */
    @Max(4)
    public Integer maxMessagesPerMonthPerClient;

    /** Minimo configuravel: 7 dias (LGPD) */
    @Min(7)
    public Integer minIntervalDays;

    public String templateAttempt1;
    public String templateAttempt2;
    public String templateAttempt3;
  }

  public static class OptOutRequest {
    @NotNull
    public String reason;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ReactivationMetricsResponse {
    public String period;
    public long totalCycles;
    public long reactivated;
    public long converted;
    public long exhausted;
    public long optedOut;
    public double reactivationRate;
    public double conversionRate;
    public Map<String, Long> byStage;
    public Map<String, AttemptMetrics> byAttempt;

    public static class AttemptMetrics {
      public long sent;
      public long reactivated;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class OptOutClientResponse {
    public UUID clientId;
    public String name;
    public Instant optOutAt;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class CycleResponse {
    public UUID id;
    public String clientName;
    public String lastStage;
    public String status;
    public int nextAttemptNumber;
    public Instant nextAttemptAt;
    public Instant abandonedAt;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ReactivationCyclesPagedResponse {
    public List<CycleResponse> items;
    public long totalItems;
    public int page;
    public int pageSize;
    public int totalPages;
    public boolean hasMore;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class OptOutPagedItem {
    public UUID clientId;
    public String clientNameMasked;
    public Instant optOutAt;
    public String source;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class OptOutPagedResponse {
    public List<OptOutPagedItem> items;
    public long totalItems;
    public int page;
    public int pageSize;
    public int totalPages;
    public boolean hasMore;
  }
}
