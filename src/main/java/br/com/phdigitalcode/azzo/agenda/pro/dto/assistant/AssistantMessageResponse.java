package br.com.phdigitalcode.azzo.agenda.pro.dto.assistant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO local para resposta do azzo-assistant-api. Espelho de
 * {@code br.com.phdigitalcode.azzo.assistant.model.AssistantMessageResponse}. Porte verbatim de
 * {@code application/dto/assistant/AssistantMessageResponse.java} do Quarkus original.
 */
public class AssistantMessageResponse {
  public String reply;
  public AssistantStage stage;
  public Map<String, Object> slots = new LinkedHashMap<>();

  public AssistantMessageResponse() {}

  public AssistantMessageResponse(String reply) {
    this.reply = reply;
  }
}
