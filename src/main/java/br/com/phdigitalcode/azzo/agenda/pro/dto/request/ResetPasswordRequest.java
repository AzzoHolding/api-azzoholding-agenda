package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Espelha {@code modules/auth/api/dto/ResetPasswordRequest.java}. */
public class ResetPasswordRequest {
  @NotBlank public String token;
  @NotBlank @Size(min = 8) public String password;
}
