package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteAddressDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Espelha {@code modules/customers/api/dto/ClienteRequest.java}. */
public class ClienteRequest {
  @NotBlank(message = "Nome do cliente e obrigatorio")
  @Size(max = 255, message = "Nome deve ter no maximo 255 caracteres")
  public String name;

  @Email(message = "Email invalido")
  @Size(max = 255)
  public String email;

  public String phone;
  public String birthDate; // ISO yyyy-MM-dd
  public String notes;
  public ClienteAddressDto address;
  public String cpfCnpj;
  public String clientType; // PF ou PJ
  public Boolean whatsappOptOut;
}
