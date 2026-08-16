package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Espelha {@code modules/professionals/api/dto/ProfissionalResponse.java}. */
public class ProfissionalResponse {
  public String id;
  public String tenantId;
  public String userId;
  public boolean accessUserCreated;
  public String name;
  public String email;
  public String phone;
  public String avatar;
  public List<String> specialties;
  /** Especialidades com nome + descricao - usado pelo assistente WhatsApp. */
  public List<SpecialidadeInfoDto> specialtiesDetailed;
  public BigDecimal commissionRate;
  public List<WorkingHoursDto> workingHours;
  public boolean isActive;
  public String createdAt;

  public static class SpecialidadeInfoDto {
    public String name;
    public String description;
  }

  public static class PasswordResetResponse {
    public String professionalId;
    public String userId;
    public String email;
    public String message;
  }
}
