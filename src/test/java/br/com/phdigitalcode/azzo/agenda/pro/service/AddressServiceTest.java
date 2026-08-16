package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.AddressResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Address;
import br.com.phdigitalcode.azzo.agenda.pro.integration.ViaCepClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AddressRepository;

/**
 * Cobre {@code AddressService} do original: normalizacao do CEP, cache-first no banco
 * ({@code source=DATABASE}) e fallback ViaCEP ({@code source=VIACEP}).
 */
class AddressServiceTest {

  private AddressRepository addressRepository;
  private ViaCepClient viaCepClient;
  private AddressService service;

  @BeforeEach
  void setUp() {
    addressRepository = mock(AddressRepository.class);
    viaCepClient = mock(ViaCepClient.class);
    when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
    service = new AddressService(addressRepository, viaCepClient);
  }

  @Test
  void cepJaEmCacheNaoChamaViaCepEMarcaSourceDatabase() {
    Address cached = new Address();
    cached.setCep("01001000");
    cached.setStreet("Praca da Se");
    cached.setCity("Sao Paulo");
    cached.setState("SP");
    when(addressRepository.findById("01001000")).thenReturn(Optional.of(cached));

    AddressResponse response = service.buscarPorCep("01001-000");

    assertThat(response.source).isEqualTo("DATABASE");
    assertThat(response.city).isEqualTo("Sao Paulo");
    verify(viaCepClient, never()).consultar(any());
  }

  @Test
  void cepNaoCacheadoConsultaViaCepEPersisteComSourceViacep() {
    when(addressRepository.findById("01001000")).thenReturn(Optional.empty());
    ViaCepClient.ViaCepAddress externo = new ViaCepClient.ViaCepAddress();
    externo.cep = "01001000";
    externo.street = "Praca da Se";
    externo.neighborhood = "Se";
    externo.city = "Sao Paulo";
    externo.state = "SP";
    when(viaCepClient.consultar("01001000")).thenReturn(externo);

    AddressResponse response = service.buscarPorCep("01001000");

    assertThat(response.source).isEqualTo("VIACEP");
    assertThat(response.street).isEqualTo("Praca da Se");
    assertThat(response.neighborhood).isEqualTo("Se");
    verify(addressRepository).save(any(Address.class));
  }

  @Test
  void cepComFormatacaoEhNormalizadoParaSoDigitos() {
    when(addressRepository.findById("01001000")).thenReturn(Optional.empty());
    ViaCepClient.ViaCepAddress externo = new ViaCepClient.ViaCepAddress();
    externo.cep = "01001000";
    when(viaCepClient.consultar("01001000")).thenReturn(externo);

    AddressResponse response = service.buscarPorCep("01.001-000");

    assertThat(response.cep).isEqualTo("01001000");
  }

  @Test
  void cepComTamanhoInvalidoEhRejeitado() {
    assertThatThrownBy(() -> service.buscarPorCep("123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CEP invalido");
  }

  @Test
  void cepNuloEhRejeitado() {
    assertThatThrownBy(() -> service.buscarPorCep(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CEP invalido");
  }
}
