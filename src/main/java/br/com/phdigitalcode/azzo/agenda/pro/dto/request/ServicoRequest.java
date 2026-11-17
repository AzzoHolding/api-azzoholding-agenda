package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Espelha {@code modules/services/api/dto/ServicoRequest.java}. */
public class ServicoRequest {
  @NotBlank(message = "Nome do serviço é obrigatório")
  @Size(max = 120, message = "Nome do serviço deve ter no máximo 120 caracteres")
  public String name;
  public String description;

  // 100.000 minutos era aceito (analise de 15/11/2026, M4). 12 horas cobre qualquer servico real.
  @Min(value = 1, message = "A duração deve ser de pelo menos 1 minuto")
  @Max(value = 720, message = "A duração máxima é de 12 horas (720 minutos)")
  public int duration;
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
