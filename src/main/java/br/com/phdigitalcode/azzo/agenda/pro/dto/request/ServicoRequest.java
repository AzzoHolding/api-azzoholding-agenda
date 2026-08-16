package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Espelha {@code modules/services/api/dto/ServicoRequest.java}. */
public class ServicoRequest {
  @NotBlank public String name;
  public String description;
  @Min(1) public int duration;
  @DecimalMin("0.00") public BigDecimal price;
  public String category;
  public List<UUID> professionalIds;
  public boolean isActive = true;

  public boolean requiresDeposit = false;

  @Pattern(regexp = "PERCENTUAL|FIXO")
  public String depositType;

  @DecimalMin("0.01")
  public BigDecimal depositValue;
}
