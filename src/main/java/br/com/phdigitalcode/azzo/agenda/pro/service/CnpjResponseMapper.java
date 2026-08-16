package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.Set;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.CnpjConsultaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.integration.BrasilApiCnpjClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CnpjWsClient;

/**
 * Mapeamento CNPJ.ws/BrasilAPI -> {@link CnpjConsultaResponse}, extraido do
 * {@code CnpjConsultaService} original para ser reutilizado por {@link CnpjLookupCache} (bean
 * cacheado) sem duplicar logica.
 */
final class CnpjResponseMapper {

  private static final Set<String> NATUREZA_MEI = Set.of("2135", "2127", "2011");

  private CnpjResponseMapper() {}

  static CnpjConsultaResponse fromCnpjWs(CnpjWsClient.CnpjWsResponse raw) {
    if (raw == null) return null;

    CnpjConsultaResponse dto = new CnpjConsultaResponse();
    dto.cnpj = br.com.phdigitalcode.azzo.agenda.pro.util.CnpjValidator.format(raw.cnpj);
    dto.razaoSocial = normalizeOrNull(raw.razao_social);
    dto.nomeFantasia = normalizeOrNull(raw.nome_fantasia);
    dto.dataInicioAtividade = normalizeOrNull(raw.data_inicio_atividade);
    dto.porte = resolverPorte(raw.porte);

    if (raw.natureza_juridica != null) {
      dto.naturezaJuridica = normalizeOrNull(raw.natureza_juridica.descricao);
      dto.isMei = detectarMei(raw.natureza_juridica.id, raw.natureza_juridica.descricao);
    }

    if (raw.estabelecimento != null) {
      dto.situacaoCadastral = normalizarSituacao(raw.estabelecimento.situacao_cadastral);

      if (raw.estabelecimento.atividade_principal != null) {
        CnpjConsultaResponse.CnaePrincipalDto cnae = new CnpjConsultaResponse.CnaePrincipalDto();
        cnae.codigo = normalizeOrNull(raw.estabelecimento.atividade_principal.codigo);
        cnae.descricao = normalizeOrNull(raw.estabelecimento.atividade_principal.descricao);
        dto.cnaePrincipal = cnae;
      }

      if (raw.estabelecimento.atividades_secundarias != null) {
        dto.cnaesSecundarios = raw.estabelecimento.atividades_secundarias.stream()
            .map(c -> {
              CnpjConsultaResponse.CnaePrincipalDto item = new CnpjConsultaResponse.CnaePrincipalDto();
              item.codigo = normalizeOrNull(c.codigo);
              item.descricao = normalizeOrNull(c.descricao);
              return item;
            })
            .toList();
      }

      CnpjConsultaResponse.EnderecoDto end = new CnpjConsultaResponse.EnderecoDto();
      end.logradouro = normalizeOrNull(raw.estabelecimento.logradouro);
      end.numero = normalizeOrNull(raw.estabelecimento.numero);
      end.complemento = normalizeOrNull(raw.estabelecimento.complemento);
      end.bairro = normalizeOrNull(raw.estabelecimento.bairro);
      end.cep = digitsOnly(raw.estabelecimento.cep);
      if (raw.estabelecimento.municipio != null) end.municipio = normalizeOrNull(raw.estabelecimento.municipio.nome);
      if (raw.estabelecimento.estado != null) end.uf = normalizeOrNull(raw.estabelecimento.estado.sigla);
      dto.endereco = end;
    }

    dto.emailSugestao = null;
    dto.telefoneSugestao = null;
    return dto;
  }

  static CnpjConsultaResponse fromBrasilApi(BrasilApiCnpjClient.BrasilApiCnpjResponse raw) {
    if (raw == null) return null;

    CnpjConsultaResponse dto = new CnpjConsultaResponse();
    dto.cnpj = br.com.phdigitalcode.azzo.agenda.pro.util.CnpjValidator.format(raw.cnpj);
    dto.razaoSocial = normalizeOrNull(raw.razao_social);
    dto.nomeFantasia = normalizeOrNull(raw.nome_fantasia);
    dto.situacaoCadastral = normalizarSituacao(raw.descricao_situacao_cadastral);
    dto.naturezaJuridica = null;
    dto.isMei = false;
    dto.porte = null;
    dto.dataInicioAtividade = null;

    CnpjConsultaResponse.EnderecoDto end = new CnpjConsultaResponse.EnderecoDto();
    String tipoLogradouro = normalizeOrNull(raw.descricao_tipo_de_logradouro);
    String logradouro = normalizeOrNull(raw.logradouro);
    end.logradouro = (tipoLogradouro != null && logradouro != null) ? tipoLogradouro + " " + logradouro : logradouro;
    end.numero = normalizeOrNull(raw.numero);
    end.complemento = normalizeOrNull(raw.complemento);
    end.bairro = normalizeOrNull(raw.bairro);
    end.municipio = normalizeOrNull(raw.municipio);
    end.uf = normalizeOrNull(raw.uf);
    end.cep = digitsOnly(raw.cep);
    dto.endereco = end;

    dto.emailSugestao = null;
    dto.telefoneSugestao = null;
    return dto;
  }

  static CnpjConsultaResponse copiarSemContato(CnpjConsultaResponse origem) {
    if (origem == null) return null;
    CnpjConsultaResponse copia = new CnpjConsultaResponse();
    copia.cnpj = origem.cnpj;
    copia.razaoSocial = origem.razaoSocial;
    copia.nomeFantasia = origem.nomeFantasia;
    copia.situacaoCadastral = origem.situacaoCadastral;
    copia.dataInicioAtividade = origem.dataInicioAtividade;
    copia.naturezaJuridica = origem.naturezaJuridica;
    copia.isMei = origem.isMei;
    copia.porte = origem.porte;
    copia.cnaePrincipal = origem.cnaePrincipal;
    copia.cnaesSecundarios = origem.cnaesSecundarios != null ? List.copyOf(origem.cnaesSecundarios) : null;
    copia.endereco = origem.endereco;
    copia.emailSugestao = null;
    copia.telefoneSugestao = null;
    return copia;
  }

  private static boolean detectarMei(String id, String descricao) {
    if (id != null) {
      String normalized = id.replaceAll("[^0-9]", "");
      if (NATUREZA_MEI.contains(normalized)) return true;
    }
    if (descricao != null) {
      String lower = descricao.toLowerCase();
      return lower.contains("microempreendedor individual") || lower.contains("empresario individual") || lower.contains("eireli");
    }
    return false;
  }

  private static String resolverPorte(String porte) {
    if (porte == null) return null;
    return switch (porte.toUpperCase()) {
      case "MICRO EMPRESA", "ME" -> "ME";
      case "EMPRESA DE PEQUENO PORTE", "EPP" -> "EPP";
      default -> "Demais";
    };
  }

  private static String normalizarSituacao(String situacao) {
    if (situacao == null) return null;
    return switch (situacao.toUpperCase()) {
      case "ATIVA", "2" -> "Ativa";
      case "INAPTA", "4" -> "Inapta";
      case "SUSPENSA", "3" -> "Suspensa";
      case "BAIXADA", "8" -> "Baixada";
      default -> capitalize(situacao);
    };
  }

  private static String capitalize(String value) {
    if (value == null || value.isBlank()) return value;
    return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
  }

  private static String normalizeOrNull(String value) {
    return (value == null || value.isBlank()) ? null : value.trim();
  }

  private static String digitsOnly(String value) {
    return value == null ? null : value.replaceAll("\\D", "");
  }
}
