package br.com.phdigitalcode.azzo.agenda.pro.integration;

/** Espelha {@code modules/tenant/infrastructure/MetaEmbeddedSignupGateway.java}. */
public interface MetaEmbeddedSignupGateway {

  String exchangeCodeForAccessToken(String code);

  MetaEmbeddedSignupClient.PhoneNumberDetails fetchPhoneNumberDetails(String accessToken, String phoneNumberId);
}
