package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Espelha {@code SpecialtyDtos.CreateRequest}. */
public class SpecialtyCreateRequest {
  @NotBlank public String name;

  @Size(max = 500) public String description;
}
