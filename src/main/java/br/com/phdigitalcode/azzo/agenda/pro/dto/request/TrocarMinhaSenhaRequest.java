package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Troca de senha de quem esta logado: pede a atual, como prova de que e a propria pessoa. */
public class TrocarMinhaSenhaRequest {
  @NotBlank public String senhaAtual;
  @NotBlank @Size(min = 8) public String novaSenha;
}
