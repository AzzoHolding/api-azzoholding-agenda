package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.util.ArrayList;
import java.util.List;

/** Espelha {@code modules/customers/api/dto/ClienteRecentAppointmentDto.java}. */
public class ClienteRecentAppointmentDto {
  public String appointmentId;
  public String date;
  public String status;
  public String professionalId;
  public String professionalName;
  public String notes;
  public List<ClienteRecentAppointmentServiceDto> services = new ArrayList<>();
}
