package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Espelha {@code TenantToggleDtos.ToggleRequest}. Chaves suportadas: WHATSAPP_ENABLED,
 * WHATSAPP_USAGE_PROFILE, WHATSAPP_CAN_SCHEDULE, WHATSAPP_CAN_CANCEL, WHATSAPP_CAN_RESCHEDULE.
 */
public class ToggleRequest {
  @NotBlank public String key;

  @NotNull public Object value; // Boolean ou String - Jackson resolve na desserializacao
}
