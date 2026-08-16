package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Espelha {@code modules/auth/api/dto/ForgotPasswordRequest.java}. */
public class ForgotPasswordRequest {
  @Email @NotBlank public String email;
}
