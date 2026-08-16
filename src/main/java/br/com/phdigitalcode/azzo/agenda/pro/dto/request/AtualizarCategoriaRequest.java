package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Porte verbatim de {@code modules/finance/api/dto/AtualizarCategoriaRequest.java}. */
public class AtualizarCategoriaRequest {

  @NotBlank(message = "Nome da categoria e obrigatorio")
  @Size(max = 160, message = "Nome deve ter no maximo 160 caracteres")
  public String name;
}
