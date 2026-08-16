package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

/** Espelha {@code application/dto/contract/PublicBookingDtos.java}. */
public final class PublicBookingDtos {

  private PublicBookingDtos() {}

  public static class AvailabilitySlot {
    public String time;
    public boolean available;
  }

  public static class AvailabilityResponse {
    public String date;
    public List<AvailabilitySlot> slots = new ArrayList<>();
  }

  public static class PublicAppointmentRequest {
    @NotBlank public String customerName;
    @NotBlank public String customerPhone;
    public String customerEmail;
    /** Exigido apenas quando algum servico selecionado exige sinal (F02). */
    public String customerCpfCnpj;
    @NotBlank public String professionalId;
    public String serviceId;
    public List<ItemRequest> items = new ArrayList<>();
    @NotBlank public String date;
    @NotBlank public String startTime;
  }

  public static class ItemRequest {
    @NotBlank public String serviceId;
    public int quantity = 1;
  }

  public static class PublicAppointmentResponse {
    public String appointmentId;
    public String status;
    public String message;

    public boolean depositRequired;
    public BigDecimal depositValue;
    public String depositPixPayload;
    public String depositExpiresAt;
  }

  public static class BookingFunnelEventRequest {
    @NotBlank public String sessionId;
    @NotBlank public String stage;
    public String occurredAt;
  }

  public static class BookingFunnelEventResponse {
    public String sessionId;
    public String stage;
    public String recordedAt;
  }
}
