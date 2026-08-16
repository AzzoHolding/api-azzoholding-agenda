package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

/** Espelha {@code modules/chat/application/channel/ChannelSendResult.java}. */
public record ChannelSendResult(
    boolean success,
    String providerMessageId,
    String providerErrorCode,
    String providerErrorMessage) {

  public static ChannelSendResult sent(String providerMessageId) {
    return new ChannelSendResult(true, providerMessageId, null, null);
  }

  public static ChannelSendResult failed(String providerErrorCode, String providerErrorMessage) {
    return new ChannelSendResult(false, null, providerErrorCode, providerErrorMessage);
  }
}
