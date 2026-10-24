package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;

public class ConfirmEmailRequest {
  @NotBlank public String token;
}
