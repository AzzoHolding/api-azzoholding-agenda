package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.util.ArrayList;
import java.util.List;

/** Espelha {@code modules/customers/api/dto/ClienteDetailResponse.java} (estende a resposta de lista). */
public class ClienteDetailResponse extends ClienteListResponse {
  public ClienteStatsDto stats;
  public List<ClienteRecentAppointmentDto> recentAppointments = new ArrayList<>();
  public List<ClienteCareTimelineDto> careTimeline = new ArrayList<>();
}
