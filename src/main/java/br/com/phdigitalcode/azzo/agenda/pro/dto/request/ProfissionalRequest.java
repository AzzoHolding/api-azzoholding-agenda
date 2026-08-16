package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import java.math.BigDecimal;
import java.util.List;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.WorkingHoursDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Espelha {@code modules/professionals/api/dto/ProfissionalRequest.java}. */
public class ProfissionalRequest {
  public String userId;
  public Boolean createUser = true;
  public String accessPassword;

  @NotBlank public String name;
  public String email;
  public String phone;
  public String avatar;
  public List<String> specialties;
  @Min(0) public BigDecimal commissionRate;
  public List<WorkingHoursDto> workingHours;
  public boolean isActive = true;
}
