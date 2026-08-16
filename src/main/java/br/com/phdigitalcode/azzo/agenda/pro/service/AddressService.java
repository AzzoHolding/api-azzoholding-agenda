package br.com.phdigitalcode.azzo.agenda.pro.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.AddressResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Address;
import br.com.phdigitalcode.azzo.agenda.pro.integration.ViaCepClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.ViaCepClient.ViaCepAddress;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AddressRepository;

/** Espelha {@code modules/address/application/AddressService.java}. */
@Service
public class AddressService {

  private final AddressRepository addressRepository;
  private final ViaCepClient viaCepClient;

  public AddressService(AddressRepository addressRepository, ViaCepClient viaCepClient) {
    this.addressRepository = addressRepository;
    this.viaCepClient = viaCepClient;
  }

  @Transactional
  public AddressResponse buscarPorCep(String cepRaw) {
    String cep = normalizarCep(cepRaw);
    Address cached = addressRepository.findById(cep).orElse(null);
    if (cached != null) return toResponse(cached, "DATABASE");

    ViaCepAddress external = viaCepClient.consultar(cep);
    Address address = new Address();
    address.setCep(cep);
    address.setStreet(external.street);
    address.setComplement(external.complement);
    address.setNeighborhood(external.neighborhood);
    address.setCity(external.city);
    address.setState(external.state);
    addressRepository.save(address);
    return toResponse(address, "VIACEP");
  }

  private String normalizarCep(String cepRaw) {
    String cep = cepRaw == null ? "" : cepRaw.replaceAll("\\D", "");
    if (cep.length() != 8) throw new IllegalArgumentException("CEP invalido");
    return cep;
  }

  private AddressResponse toResponse(Address a, String source) {
    AddressResponse r = new AddressResponse();
    r.cep = a.getCep();
    r.street = a.getStreet();
    r.complement = a.getComplement();
    r.neighborhood = a.getNeighborhood();
    r.city = a.getCity();
    r.state = a.getState();
    r.source = source;
    return r;
  }
}
