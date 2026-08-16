package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;

/** Espelha {@code modules/customers/api/dto/ClienteTopServiceDto.java}. */
public class ClienteTopServiceDto {
  public String serviceId;
  public String serviceName;
  public String topProfessionalId;
  public String topProfessionalName;
  public int completedAppointments;
  public int completedServices;
  public BigDecimal revenueTotal;
  public String lastAppointmentDate;
}
