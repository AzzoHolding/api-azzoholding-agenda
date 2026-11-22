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

  /**
   * O profissional como o LINK PUBLICO mostra: so o que a tela de agendar usa. A rota nao pede
   * login — devolver e-mail, telefone, taxa de comissao e ids internos do profissional expunha
   * dado pessoal e comercial a qualquer um com o link (achado de 2026-09-20).
   */
  public static class PublicProfessional {
    public String id;
    public String name;
    public String avatar;
    public List<String> specialties = new ArrayList<>();
  }

  /** O servico como o link publico mostra: o suficiente para escolher e saber do sinal. */
  public static class PublicService {
    public String id;
    public String name;
    public String description;
    public Integer duration;
    public BigDecimal price;
    public String category;
    public boolean requiresDeposit;
    public String depositType;
    public BigDecimal depositValue;
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
    /** Cupom de desconto (item 6). Opcional. */
    @jakarta.validation.constraints.Size(max = 30, message = "Código do cupom inválido")
    public String cupom;
  }

  public static class ItemRequest {
    @NotBlank public String serviceId;
    public int quantity = 1;
  }

  public static class PublicAppointmentResponse {
    public String appointmentId;
    public String status;
    public String message;
    /** Desconto do cupom aplicado (item 6); nulo sem cupom. */
    public java.math.BigDecimal discountValue;

    /** Link para a cliente preencher a anamnese e assinar os termos (servicos que pedem). Nulo se nada pede. */
    public String linkDocumentos;

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
