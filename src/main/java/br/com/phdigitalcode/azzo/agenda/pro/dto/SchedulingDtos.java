package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTOs de {@code modules/scheduling/api/dto/*}. Agregados numa classe so, no mesmo padrao de
 * {@code ComandaDtos}/{@code CommissionDtos}/{@code BillingDtos} — os nomes das classes internas e
 * dos campos sao os mesmos do original, porque o contrato JSON com o frontend depende deles.
 *
 * <p>Campos publicos (sem getter/setter) tambem por fidelidade: o original serializa campos
 * publicos e trocar para bean properties nao mudaria o JSON, mas mudaria todos os call sites sem
 * ganho.
 */
public final class SchedulingDtos {

  private SchedulingDtos() {}

  /** Espelha {@code api/dto/TimeSlotResponse.java}. */
  public static class TimeSlotResponse {
    public LocalTime startTime;
    public LocalTime endTime;
    public int optimizationScore;
  }

  /** Espelha {@code api/dto/ManualTimeSlotResponse.java}. */
  public static class ManualTimeSlotResponse {
    public LocalTime startTime;
    public LocalTime endTime;
    public int optimizationScore;
    public Boolean conflicting;
    public String slotType;
    public String badge;
    public List<AppointmentConflictSummaryResponse> conflicts = new ArrayList<>();
  }

  /** Espelha {@code api/dto/AppointmentConflictSummaryResponse.java}. */
  public static class AppointmentConflictSummaryResponse {
    public String appointmentId;
    public String clientId;
    public String clientName;
    public String serviceId;
    public String serviceName;
    public String startTime;
    public String endTime;
    public String status;
  }

  /** Espelha {@code api/dto/AppointmentSchedulingSettingsResponse.java}. */
  public static class AppointmentSchedulingSettingsResponse {
    public Boolean allowConflictingAppointmentsOnManualScheduling;
    public String updatedAt;
  }

  /** Espelha {@code api/dto/AppointmentSchedulingSettingsRequest.java}. */
  public static class AppointmentSchedulingSettingsRequest {
    public Boolean allowConflictingAppointmentsOnManualScheduling;
  }

  /** Espelha {@code api/dto/AppointmentConflictDetailsResponse.java}. */
  public static class AppointmentConflictDetailsResponse {
    public String requestedDate;
    public String requestedStartTime;
    public String requestedEndTime;
    public String origin;
    public Boolean canOverride;
    public Boolean allowConflictingAppointmentsOnManualScheduling;
    public List<AppointmentConflictSummaryResponse> conflicts = new ArrayList<>();
  }

  /**
   * Espelha {@code api/dto/AgendamentoRequest.java}. Sem bean validation, igual ao original — o
   * resource anota {@code @Valid} mas o DTO nao declara restricao nenhuma; toda a validacao esta no
   * service.
   */
  public static class AgendamentoRequest {
    public String clientId;
    public String professionalId;
    public String serviceId;
    public List<ItemRequest> items = new ArrayList<>();
    public String date; // yyyy-MM-dd
    public String startTime;
    public String endTime;
    public String status; // enum string
    public BigDecimal totalPrice;
    public String notes;
    public String origin;
    public Boolean allowConflict;
    public Boolean conflictAcknowledged;

    public static class ItemRequest {
      public String serviceId;
      public int quantity = 1;
      public BigDecimal unitPrice;
      public BigDecimal grossAmount;
      public BigDecimal discountAmount;
      public BigDecimal totalPrice;
    }
  }

  /** Espelha {@code api/dto/AppointmentUpdateRequest.java}. */
  public static class AppointmentUpdateRequest {
    @NotBlank(message = "ID do profissional e obrigatorio")
    public String professionalId;

    @NotBlank(message = "Data do agendamento e obrigatoria")
    public String date;

    @NotBlank(message = "Hora de inicio e obrigatoria")
    public String startTime;

    public String notes;
    public Boolean allowConflict;
    public Boolean conflictAcknowledged;
    public List<AgendamentoRequest.ItemRequest> items = new ArrayList<>();
    public String serviceId;
    public BigDecimal totalPrice;
  }

  /** Espelha {@code api/dto/AgendamentoResponse.java}. */
  public static class AgendamentoResponse {
    public String id;
    public String tenantId;

    public String clientId;
    public ClienteResponse client;

    public String professionalId;
    public ProfissionalResponse professional;

    public String serviceId;
    public ServicoResponse service;
    public List<ItemResponse> items = new ArrayList<>();

    public String date;
    public String startTime;
    public String endTime;
    public String status;
    public String notes;
    public BigDecimal totalPrice;
    public String createdAt;

    /**
     * Preenchido apenas ao transicionar para IN_PROGRESS - id da Comanda aberta automaticamente
     * para este atendimento, se houver.
     */
    public String comandaId;

    public static class ItemResponse {
      public String serviceId;
      public ServicoResponse service;
      public int quantity;
      public BigDecimal unitPrice;
      public BigDecimal grossAmount;
      public BigDecimal discountAmount;
      public BigDecimal totalPrice;
    }
  }

  /** Espelha {@code api/dto/AgendamentoMetricaDiariaResponse.java}. */
  public static class AgendamentoMetricaDiariaResponse {
    public int dia;
    public int mes;
    public long quantidadeAgendamentos;
  }

  /** Espelha {@code api/dto/AppointmentCustomerNoteRequest.java}. */
  public static class AppointmentCustomerNoteRequest {
    public String serviceExecutionNotes;
    public String clientFeedbackNotes;
    public String internalFollowupNotes;
  }

  /** Espelha {@code api/dto/AppointmentCustomerNoteUpdateRequest.java}. */
  public static class AppointmentCustomerNoteUpdateRequest extends AppointmentCustomerNoteRequest {}

  /** Espelha {@code api/dto/AppointmentCustomerNoteResponse.java}. */
  public static class AppointmentCustomerNoteResponse {
    public String noteId;
    public String appointmentId;
    public String clientId;
    public String recordedByUserId;
    public String recordedAt;
    public String updatedAt;
    public String serviceExecutionNotes;
    public String clientFeedbackNotes;
    public String internalFollowupNotes;
  }

  /** Espelha {@code api/dto/AppointmentTimelineEventResponse.java}. */
  public static class AppointmentTimelineEventResponse {
    public String eventId;
    public String action;
    public String actionLabel;
    public String actorUserId;
    public String actorName;
    public String actorRole;
    public String status;
    public String sourceChannel;
    public String createdAt;
    public List<String> changedFields = new ArrayList<>();
    public Object before;
    public Object after;
    public Object metadata;
  }

  /** Espelha {@code api/dto/AppointmentDetailResponse.java}. */
  public static class AppointmentDetailResponse {
    public AgendamentoResponse appointment;
    public List<AppointmentCustomerNoteResponse> careNotes = new ArrayList<>();
    public List<AppointmentTimelineEventResponse> timeline = new ArrayList<>();
  }

  /** Espelha {@code api/dto/AppointmentStatusUpdateRequest.java}. */
  public static class AppointmentStatusUpdateRequest {
    public String paymentMethod;

    /**
     * Relevante apenas ao concluir (status=COMPLETED): "ADD_TO_COMANDA" (padrao - so vincula o
     * servico a comanda, sem lancar no caixa agora) ou "PAY_NOW" (cobra e fecha a comanda
     * imediatamente, lancando no caixa).
     */
    public String conclusionAction;
  }

  /** Espelha {@code api/dto/AttendanceRequest.java}. */
  public static class AttendanceRequest {
    @NotNull(message = "O campo attended e obrigatorio")
    public Boolean attended;
  }

  /** Espelha {@code api/dto/PendingAttendanceResponse.java}. */
  public static class PendingAttendanceResponse {
    public String id;
    public String clientName;
    public String clientPhone;
    public String professionalName;
    public String date;
    public String startTime;
    public String endTime;
    public String status;
    public List<String> serviceNames;
  }

  /** Espelha {@code api/dto/ClientAppointmentHistoryItemDto.java}. */
  public static class ClientAppointmentHistoryItemDto {
    public String appointmentId;
    public String date;
    public String status;
    public String professionalId;
    public String professionalName;
    public String notes;
    public List<AgendamentoResponse.ItemResponse> services = new ArrayList<>();
    public List<AppointmentCustomerNoteResponse> careNotes = new ArrayList<>();
  }

  /** Espelha {@code api/dto/ClientAppointmentHistoryResponse.java}. */
  public static class ClientAppointmentHistoryResponse {
    public String clientId;
    public int page;
    public int size;
    public long totalItems;
    public List<ClientAppointmentHistoryItemDto> items = new ArrayList<>();
  }

  /** Espelha {@code api/dto/AppointmentManagementReportSignalResponse.java}. */
  public static class AppointmentManagementReportSignalResponse {
    public String code;
    public String title;
    public String description;
    public String severity;
  }

  /** Espelha {@code api/dto/AppointmentManagementReportItemResponse.java}. */
  public static class AppointmentManagementReportItemResponse {
    public String appointmentId;
    public String date;
    public String startTime;
    public String endTime;
    public String clientId;
    public String clientName;
    public String professionalId;
    public String professionalName;
    public String serviceLabel;
    public String status;
    public String origin;
    public BigDecimal totalPrice;
    public boolean flagHorarioVago;
    public boolean flagNaoConfirmado;
    public boolean flagAbandonoFluxo;
  }

  /** Espelha {@code api/dto/AppointmentManagementReportResponse.java}. */
  public static class AppointmentManagementReportResponse {
    public String startDate;
    public String endDate;
    public String lastUpdatedAt;
    public int totalAppointments;
    public int totalConfirmed;
    public int totalPending;
    public int totalCancelled;
    public int totalNoShow;
    public int totalCompleted;
    public BigDecimal totalRevenue;
    public int totalGapOpportunities;
    public int totalUnconfirmed;
    public int totalAbandonmentSignalDays;
    public double occupancyRate;
    public double cancellationRate;
    public double noShowRate;
    public int limit;
    public int totalItems;
    public int page;
    public int pageSize;
    public boolean hasMore;
    public List<AppointmentManagementReportSignalResponse> alerts = new ArrayList<>();
    public List<AppointmentManagementReportSignalResponse> opportunities = new ArrayList<>();
    public List<AppointmentManagementReportItemResponse> items = new ArrayList<>();
  }

  /** Espelha {@code api/dto/NoShowGroupBy.java}. */
  public enum NoShowGroupBy {
    DAY,
    PROFESSIONAL,
    CLIENT,
    SERVICE;

    public static NoShowGroupBy fromValue(String value) {
      if (value == null || value.isBlank()) return DAY;
      for (NoShowGroupBy item : values()) {
        if (item.name().equalsIgnoreCase(value.trim())) return item;
      }
      throw new IllegalArgumentException(
          "groupBy invalido. Use DAY, PROFESSIONAL, CLIENT ou SERVICE.");
    }
  }

  /** Espelha {@code api/dto/NoShowGroupResponse.java}. */
  public static class NoShowGroupResponse {
    public String key;
    public String label;
    public int totalNoShows;
    public BigDecimal revenueAtRisk;
  }

  /** Espelha {@code api/dto/NoShowReportPointResponse.java}. */
  public static class NoShowReportPointResponse {
    public String date;
    public int totalNoShows;
    public BigDecimal revenueAtRisk;
  }

  /** Espelha {@code api/dto/NoShowReportItemResponse.java}. */
  public static class NoShowReportItemResponse {
    public String appointmentId;
    public String clientId;
    public String clientName;
    public String clientPhone;
    public String clientEmail;
    public String professionalId;
    public String professionalName;
    public String date;
    public String startTime;
    public String endTime;
    public String status;
    public String notes;
    public BigDecimal totalPrice;
    public int totalServices;
    public List<String> serviceNames = new ArrayList<>();
    public String createdAt;
  }

  /** Espelha {@code api/dto/NoShowReportResponse.java}. */
  public static class NoShowReportResponse {
    public String startDate;
    public String endDate;
    public String lastUpdatedAt;
    public String groupBy;
    public int totalNoShows;
    public int previousPeriodNoShows;
    public int lastSevenDaysNoShows;
    public int completedAppointments;
    public double noShowRate;
    public BigDecimal revenueAtRisk;
    public int limit;
    public String afterId;
    public String nextAfterId;
    public boolean hasMore;
    public long totalItems;
    public List<NoShowReportItemResponse> items = new ArrayList<>();
    public List<NoShowReportPointResponse> points = new ArrayList<>();
    public List<NoShowGroupResponse> groups = new ArrayList<>();
  }
}
