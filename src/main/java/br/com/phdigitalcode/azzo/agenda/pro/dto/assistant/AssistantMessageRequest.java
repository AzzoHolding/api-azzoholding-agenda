package br.com.phdigitalcode.azzo.agenda.pro.dto.assistant;

/**
 * DTO local para requisicao ao azzo-assistant-api. Espelho de
 * {@code br.com.phdigitalcode.azzo.assistant.model.AssistantMessageRequest}. Porte verbatim de
 * {@code application/dto/assistant/AssistantMessageRequest.java} do Quarkus original.
 */
public class AssistantMessageRequest {
  public String message;

  public AssistantMessageRequest() {}

  public AssistantMessageRequest(String message) {
    this.message = message;
  }
}
