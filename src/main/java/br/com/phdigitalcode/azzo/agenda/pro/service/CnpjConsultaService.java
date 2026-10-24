package br.com.phdigitalcode.azzo.agenda.pro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.CnpjConsultaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CnpjWsClient;
import br.com.phdigitalcode.azzo.agenda.pro.util.CnpjValidator;

/**
 * Espelha {@code modules/company/application/CnpjConsultaService.java}. O metodo cacheado
 * ({@code cnpj-cache}, 24h/500 entradas) vive em {@link CnpjLookupCache} — ver o motivo (evitar
 * self-invocation bypass do Spring Cache) documentado la.
 */
@Service
public class CnpjConsultaService {

  private static final Logger LOG = LoggerFactory.getLogger(CnpjConsultaService.class);

  private final CnpjWsClient cnpjWsClient;
  private final CnpjLookupCache cnpjLookupCache;

  public CnpjConsultaService(CnpjWsClient cnpjWsClient, CnpjLookupCache cnpjLookupCache) {
    this.cnpjWsClient = cnpjWsClient;
    this.cnpjLookupCache = cnpjLookupCache;
  }

  public CnpjConsultaResponse consultar(String cnpj) {
    LOG.info("Iniciando consulta de CNPJ {}", CnpjValidator.mask(cnpj));

    String emailSugestao = null;
    String telefoneSugestao = null;

    try {
      CnpjWsClient.CnpjWsResponse raw = cnpjWsClient.buscar(cnpj);
      if (raw != null && raw.estabelecimento != null) {
        emailSugestao = normalizeOrNull(raw.estabelecimento.email);
        telefoneSugestao = normalizeOrNull(raw.estabelecimento.telefone1);
      }
    } catch (Exception e) {
      LOG.debug("Nao foi possivel obter contatos de {} via CNPJ.ws: {}", CnpjValidator.mask(cnpj), e.getClass().getSimpleName());
    }

    CnpjConsultaResponse cached = cnpjLookupCache.consultarCacheado(cnpj);

    if (emailSugestao != null || telefoneSugestao != null) {
      CnpjConsultaResponse resultado = CnpjResponseMapper.copiarSemContato(cached);
      resultado.emailSugestao = emailSugestao;
      resultado.telefoneSugestao = telefoneSugestao;
      return resultado;
    }
    return cached;
  }

  /**
   * Consulta so o que e cacheavel — sem a segunda chamada ao CNPJ.ws atras de e-mail e telefone.
   * E o que a consulta publica usa: contato nao sai sem login (LGPD).
   */
  public CnpjConsultaResponse consultarSemContato(String cnpj) {
    LOG.info("Consulta publica de CNPJ {}", CnpjValidator.mask(cnpj));
    return cnpjLookupCache.consultarCacheado(cnpj);
  }

  private String normalizeOrNull(String value) {
    return (value == null || value.isBlank()) ? null : value.trim();
  }
}
