package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.AddressResponse;
import br.com.phdigitalcode.azzo.agenda.pro.service.AddressService;

/** Espelha {@code modules/address/api/AddressResource.java}. */
@RestController
@RequestMapping("/api/v1/utils/addresses")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class AddressController {

  private final AddressService addressService;

  public AddressController(AddressService addressService) {
    this.addressService = addressService;
  }

  @GetMapping("/{cep}")
  public AddressResponse buscarPorCep(@PathVariable String cep) {
    return addressService.buscarPorCep(cep);
  }
}
