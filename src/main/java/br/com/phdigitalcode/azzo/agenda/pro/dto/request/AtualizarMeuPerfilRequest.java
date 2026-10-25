package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A propria pessoa troca nome e telefone (tela Perfil, {@code /perfil-usuario}). O e-mail e o login: fica. */
public class AtualizarMeuPerfilRequest {
  @NotBlank @Size(max = 255) public String name;
  public String phone;
}
