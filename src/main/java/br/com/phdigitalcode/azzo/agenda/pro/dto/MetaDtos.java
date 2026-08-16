package br.com.phdigitalcode.azzo.agenda.pro.dto;

/**
 * Espelha os inner classes de resposta de {@code modules/meta/api/publicapi/MetaPlatformPublicResource.java}.
 * Campos publicos porque o original tambem os expoe assim (classes estaticas com campos publicos,
 * sem getters/setters) e o contrato JSON precisa bater campo a campo com o consumidor (Meta/Facebook).
 */
public final class MetaDtos {

  private MetaDtos() {}

  public static class MetaAcknowledgeResponse {
    public boolean success;
    public String message;
    public String receivedAt;
    public String userId;
  }

  public static class DataDeletionResponse {
    public String url;
    public String confirmationCode;
  }

  public static class DataDeletionStatusResponse {
    public String confirmationCode;
    public String status;
    public String receivedAt;
    public String message;
  }
}
