package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Porte verbatim de {@code modules/commission/api/dto/CommissionDtos.java} — mesmos nomes de
 * campo, mesmas anotacoes de validacao, mesmo formato JSON.
 */
public final class CommissionDtos {

  private CommissionDtos() {}

  public static class RuleSetUpsertRequest {
    @NotBlank
    @Pattern(regexp = "GLOBAL|PROFESSIONAL")
    public String scopeType;

    public String professionalId;

    @NotBlank
    @Size(max = 160)
    public String name;

    public Boolean active = Boolean.TRUE;

    @Valid
    @NotEmpty
    public List<RuleRequest> rules = new ArrayList<>();
  }

  public static class RuleRequest {
    @NotBlank
    @Pattern(regexp = "GENERAL|SERVICE|SERVICE_CATEGORY|PRODUCT|PRODUCT_CATEGORY")
    public String targetType;

    public String targetId;

    @Size(max = 160)
    public String targetCode;

    @NotNull public BigDecimal percentValue;

    @NotNull public BigDecimal fixedAmount;

    @NotBlank
    @Pattern(regexp = "GROSS|NET_OF_DISCOUNT")
    public String percentBaseType;

    @NotBlank
    @Pattern(regexp = "KEEP_COMMISSION|REVERSE_COMMISSION")
    public String refundPolicy;

    public Boolean active = Boolean.TRUE;
    public String startsAt;
    public String endsAt;
  }

  public static class RuleSetResponse {
    public String id;
    public String scopeType;
    public String professionalId;
    public String professionalName;
    public String name;
    public Boolean active;
    public String createdAt;
    public String updatedAt;
    public List<RuleItemResponse> rules = new ArrayList<>();
  }

  public static class RuleItemResponse {
    public String id;
    public String targetType;
    public String targetId;
    public String targetCode;
    public String targetLabel;
    public BigDecimal percentValue;
    public BigDecimal fixedAmount;
    public String percentBaseType;
    public String refundPolicy;
    public Boolean active;
    public String startsAt;
    public String endsAt;
  }

  public static class RuleSetListResponse {
    public List<RuleSetResponse> items = new ArrayList<>();
  }

  public static class ActivationRequest {
    @NotNull public Boolean active;
  }

  public static class AdjustmentRequest {
    @NotBlank public String professionalId;

    @NotNull public BigDecimal amount;

    public String effectiveAt;

    @NotBlank
    @Size(max = 500)
    public String reason;
  }

  public static class EntryResponse {
    public String id;
    public String professionalId;
    public String professionalName;
    public String originType;
    public String originId;
    public String originReference;
    public String periodKey;
    public BigDecimal baseAmount;
    public BigDecimal percentValue;
    public BigDecimal percentAmount;
    public BigDecimal fixedAmount;
    public BigDecimal totalAmount;
    public String entryStatus;
    public String notes;
    public String createdAt;
    public String reversedAt;
    public String cycleId;
  }

  public static class AdjustmentResponse {
    public EntryResponse entry;
    public String message;
  }

  public static class ReportItemResponse {
    public String professionalId;
    public String professionalName;
    public BigDecimal serviceAmount;
    public BigDecimal productAmount;
    public BigDecimal manualAdjustmentAmount;
    public BigDecimal totalAmount;
    public BigDecimal openAmount;
    public BigDecimal paidAmount;
    public Integer totalEntries;
  }

  public static class ReportResponse {
    public String from;
    public String to;
    public String professionalId;
    public String status;
    public BigDecimal totalAmount;
    public BigDecimal totalOpenAmount;
    public BigDecimal totalPaidAmount;
    public Integer totalEntries;
    public List<ReportItemResponse> items = new ArrayList<>();
  }

  public static class ProfessionalReportResponse {
    public String professionalId;
    public String professionalName;
    public String from;
    public String to;
    public BigDecimal totalAmount;
    public BigDecimal totalOpenAmount;
    public BigDecimal totalPaidAmount;
    public List<EntryResponse> entries = new ArrayList<>();
  }

  public static class CycleCloseRequest {
    @NotBlank public String periodStart;

    @NotBlank public String periodEnd;
  }

  public static class CyclePayRequest {
    @Size(max = 500)
    public String notes;
  }

  public static class CycleResponse {
    public String id;
    public String periodStart;
    public String periodEnd;
    public String status;
    public String closedAt;
    public String closedByUserId;
    public String paidAt;
    public String paidByUserId;
    public BigDecimal totalAmount;
    public Integer entryCount;
    public String createdAt;
  }

  public static class CycleListResponse {
    public List<CycleResponse> items = new ArrayList<>();
  }
}
