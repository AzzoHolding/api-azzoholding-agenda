package br.com.phdigitalcode.azzo.agenda.pro.integration;

/** Espelha {@code modules/tenant/infrastructure/MetaEmbeddedSignupGateway.java}. */
public interface MetaEmbeddedSignupGateway {

  String exchangeCodeForAccessToken(String code);

  MetaEmbeddedSignupClient.PhoneNumberDetails fetchPhoneNumberDetails(String accessToken, String phoneNumberId);

  /** {@code user_id} de quem autorizou o app, lido no {@code debug_token} com o token do app. */
  String fetchTokenUserId(String accessToken);

  /** Remove na Meta as permissoes que o usuario deu ao app ({@code DELETE /{user-id}/permissions}). */
  void revokeUserPermissions(String metaUserId);
}
