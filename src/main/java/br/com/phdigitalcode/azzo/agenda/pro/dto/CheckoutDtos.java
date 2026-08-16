package br.com.phdigitalcode.azzo.agenda.pro.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

/**
 * Porte verbatim de {@code modules/billing/api/dto/CheckoutDtos.java} — mesmos nomes de campo,
 * mesmas anotacoes de validacao, mesmo formato JSON.
 */
public final class CheckoutDtos {

  private CheckoutDtos() {}

  public static class ProductResponse {
    public String id;
    public String name;
    public String description;
    public String currency;
    public double price;
    public int validityDays;
    public int validityMonths;
    public String highlight;
    public Integer maxProfessionals;
    public boolean exclusivoVendaInterna;
    public List<String> features = new ArrayList<>();
  }

  public static class CreateIntentRequest {
    @NotBlank
    public String productId;

    @Min(1)
    public int quantity = 1;
  }

  public static class CreateIntentResponse {
    public String intentId;
    public String productId;
    public String productName;
    public int quantity;
    public String currency;
    public double unitPrice;
    public double totalPrice;
    public String status;
    public String expiresAt;
  }

  public static class ConfirmIntentResponse {
    public String intentId;
    public String status;
    public String validUntil;
    public String redirectUrl;
  }
}

