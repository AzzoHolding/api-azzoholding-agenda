package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Espelha {@code modules/auth/api/dto/RegisterRequest.java}. */
public class RegisterRequest {
  @NotBlank public String name;
  @Email @NotBlank public String email;
  @NotBlank public String password;

  public String salonName;
  public String phone;
  @NotBlank public String cpfCnpj;
  @NotNull public Boolean acceptedTermsOfUse;
  @NotNull public Boolean acceptedPrivacyPolicy;
  @NotBlank public String termsOfUseVersion;
  @NotBlank public String privacyPolicyVersion;
}
