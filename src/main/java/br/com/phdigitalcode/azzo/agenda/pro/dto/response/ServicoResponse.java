package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Espelha {@code modules/services/api/dto/ServicoResponse.java}. */
public class ServicoResponse {
  public String id;
  public String tenantId;
  public String name;
  public String description;
  public int duration;
  public BigDecimal price;
  public String category;
  public List<UUID> professionalIds;
  public boolean isActive;
  public String createdAt;

  public boolean requiresDeposit;
  public String depositType;
  public BigDecimal depositValue;
}
