package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Espelha {@code modules/auth/api/dto/LoginRequest.java}. */
public class LoginRequest {
  @Email @NotBlank public String email;
  @NotBlank public String password;
  public String mfaCode;
}
