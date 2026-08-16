package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

/** Espelha {@code modules/auth/api/dto/AuthResponse.java}. */
public class AuthResponse {
  public String access_token;
  public String refresh_token;
  public String token_type = "Bearer";
  public long expires_in;
  public UsuarioResponse user;
}
