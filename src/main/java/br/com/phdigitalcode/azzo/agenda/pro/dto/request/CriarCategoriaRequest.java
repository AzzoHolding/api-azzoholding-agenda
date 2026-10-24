package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Porte verbatim de {@code modules/finance/api/dto/CriarCategoriaRequest.java}. */
public class CriarCategoriaRequest {

  @NotBlank(message = "Nome da categoria é obrigatório")
  @Size(max = 160, message = "Nome deve ter no máximo 160 caracteres")
  public String name;
}
