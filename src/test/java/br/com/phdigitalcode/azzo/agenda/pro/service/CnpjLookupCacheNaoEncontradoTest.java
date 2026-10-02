package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import br.com.phdigitalcode.azzo.agenda.pro.exception.CnpjApiIndisponivelException;
import br.com.phdigitalcode.azzo.agenda.pro.exception.CnpjNaoEncontradoException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.BrasilApiCnpjClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CnpjWsClient;

/** "O CNPJ nao existe" (404 nos dois provedores) e diferente de "o provedor esta fora do ar". */
class CnpjLookupCacheNaoEncontradoTest {

  private static final String CNPJ = "11222333000181";

  private CnpjWsClient cnpjWs;
  private BrasilApiCnpjClient brasilApi;
  private CnpjLookupCache cache;

  @BeforeEach
  void setUp() {
    cnpjWs = mock(CnpjWsClient.class);
    brasilApi = mock(BrasilApiCnpjClient.class);
    cache = new CnpjLookupCache(cnpjWs, brasilApi);
  }

  private HttpClientErrorException.NotFound naoEncontrado() {
    return (HttpClientErrorException.NotFound)
        HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
  }

  @Test
  void quatrocentosEQuatroNosDoisEhCnpjInexistente() {
    when(cnpjWs.buscar(anyString())).thenThrow(naoEncontrado());
    when(brasilApi.lookup(anyString())).thenThrow(naoEncontrado());

    assertThatThrownBy(() -> cache.consultarCacheado(CNPJ))
        .isInstanceOf(CnpjNaoEncontradoException.class)
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void quatrocentosEQuatroEmUmSoComFalhaNoOutroEhIndisponibilidade() {
    when(cnpjWs.buscar(anyString())).thenThrow(naoEncontrado());
    when(brasilApi.lookup(anyString())).thenThrow(new ResourceAccessException("timeout"));

    assertThatThrownBy(() -> cache.consultarCacheado(CNPJ))
        .isInstanceOf(CnpjApiIndisponivelException.class);
  }

  @Test
  void erroDeServidorNosDoisContinuaSendoIndisponibilidade() {
    when(cnpjWs.buscar(anyString()))
        .thenThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "x", HttpHeaders.EMPTY, new byte[0], null));
    when(brasilApi.lookup(anyString()))
        .thenThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "x", HttpHeaders.EMPTY, new byte[0], null));

    assertThatThrownBy(() -> cache.consultarCacheado(CNPJ))
        .isInstanceOf(CnpjApiIndisponivelException.class);
  }
}
