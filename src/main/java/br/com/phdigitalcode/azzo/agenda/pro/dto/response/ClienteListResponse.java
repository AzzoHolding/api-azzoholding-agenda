package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Espelha {@code modules/customers/api/dto/ClienteListResponse.java}. */
public class ClienteListResponse {
  public String id;
  public String tenantId;
  public String name;
  public String email;
  public String phone;
  public String avatar;
  public String avatarUrl;
  public String birthDate;
  public String notes;
  public ClienteAddressDto address;
  public String cpfCnpj;
  public String clientType;
  public List<ClienteTopServiceDto> topServices;
  public int totalVisits;
  public BigDecimal totalSpent;
  public String lastVisit;
  public String createdAt;
}
