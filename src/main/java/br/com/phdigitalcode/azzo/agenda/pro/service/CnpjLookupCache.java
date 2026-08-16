package br.com.phdigitalcode.azzo.agenda.pro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.CnpjConsultaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.CnpjApiIndisponivelException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.BrasilApiCnpjClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CnpjWsClient;
import br.com.phdigitalcode.azzo.agenda.pro.util.CnpjValidator;

/**
 * Bean dedicado para o cache {@code cnpj-cache} (24h/500 entradas). Extraido de
 * {@link CnpjConsultaService} pelo mesmo motivo documentado em
 * {@code security/RbacPermissionCache.java}: evitar self-invocation bypass do proxy de cache do
 * Spring. Mapeamento CNPJ.ws/BrasilAPI fica aqui porque e exatamente o resultado que deve ser
 * cacheado (sem dados de contato — LGPD).
 */
@Component
public class CnpjLookupCache {

  private static final Logger LOG = LoggerFactory.getLogger(CnpjLookupCache.class);

  private final CnpjWsClient cnpjWsClient;
  private final BrasilApiCnpjClient brasilApiCnpjClient;

  public CnpjLookupCache(CnpjWsClient cnpjWsClient, BrasilApiCnpjClient brasilApiCnpjClient) {
    this.cnpjWsClient = cnpjWsClient;
    this.brasilApiCnpjClient = brasilApiCnpjClient;
  }

  @Cacheable(cacheNames = "cnpj-cache", key = "#cnpj")
  public CnpjConsultaResponse consultarCacheado(String cnpj) {
    LOG.info("Cache miss - consultando APIs externas para CNPJ {}", CnpjValidator.mask(cnpj));

    try {
      CnpjWsClient.CnpjWsResponse raw = cnpjWsClient.buscar(cnpj);
      LOG.info("CNPJ {} obtido via CNPJ.ws", CnpjValidator.mask(cnpj));
      return CnpjResponseMapper.fromCnpjWs(raw);
    } catch (Exception e) {
      LOG.warn("Falha na CNPJ.ws para {} ({}), acionando fallback BrasilAPI", CnpjValidator.mask(cnpj), e.getClass().getSimpleName());
    }

    try {
      BrasilApiCnpjClient.BrasilApiCnpjResponse raw = brasilApiCnpjClient.lookup(cnpj);
      LOG.info("CNPJ {} obtido via BrasilAPI (fallback)", CnpjValidator.mask(cnpj));
      return CnpjResponseMapper.fromBrasilApi(raw);
    } catch (Exception e) {
      LOG.error("Ambas as APIs falharam para CNPJ {}: {}", CnpjValidator.mask(cnpj), e.getMessage());
      throw new CnpjApiIndisponivelException("Servico de consulta CNPJ temporariamente indisponivel. Tente novamente em instantes.");
    }
  }
}
