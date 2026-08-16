package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.util.List;
import java.util.UUID;

/**
 * Espelha {@code modules/settings/application/dto/SpecialClosureImpactDto.java}.
 *
 * <p>Resultado ao tentar criar um fechamento especial quando ha agendamentos impactados. O frontend
 * usa esta resposta para exibir os impactados ao usuario antes de confirmar.
 */
public class SpecialClosureImpactDto {

  /** true se o fechamento foi criado sem conflitos; false se ha agendamentos impactados. */
  public boolean created;

  /** ID do fechamento criado. Preenchido apenas se created = true. */
  public UUID closureId;

  /** Lista de agendamentos impactados. Preenchida apenas se created = false. */
  public List<ImpactedAppointment> impactedAppointments;

  public static class ImpactedAppointment {
    public UUID appointmentId;
    public String clientName;
    public String professionalName;
    public String date;
    public String startTime;
    public String endTime;
    public String status;
  }
}
