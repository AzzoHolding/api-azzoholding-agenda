package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCpfPolicyMode;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseEmissionMode;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseProviderCapabilitiesRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Espelha a fatia de CRUD de configuracao de {@code modules/nfse/application/NfseService.java}
 * (metodos {@code obterConfig}/{@code salvarConfig} + os helpers privados que eles usam:
 * {@code validarConfig}, {@code resolveProvedor}, {@code toConfigDto}, os parsers de enum e as
 * normalizacoes de string). Fronteira 2 do porte de {@code nfse} (ver
 * {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/26) — extraida como servico proprio, em vez de
 * entrar no {@code NfseService} monolitico (1313L no original), porque nao depende de XML,
 * assinatura, mTLS ou da maquina de estados fiscal: e CRUD puro sobre {@code nfse_configs}. O
 * {@code NfseService} orquestrador (Fronteira 6) delega para ca.
 *
 * <p>{@code jakarta.ws.rs.NotFoundException} do original vira {@link ApiClientErrorException} 404
 * (mesmo padrao ja aplicado em {@code FiscalCertificateService}).
 *
 * <p>Panache {@code isPersistent(entity)} + {@code persist()} condicional vira sempre
 * {@code save(entity)} — Spring Data resolve insert/update pelo estado do id (mesmo padrao ja
 * usado em {@code AppointmentSettingsService}).
 */
@Service
public class NfseConfigService {

  private final NfseConfigRepository nfseConfigRepository;
  private final NfseProviderCapabilitiesRepository nfseProviderCapabilitiesRepository;
  private final ContextoTenant contextoTenant;

  public NfseConfigService(
      NfseConfigRepository nfseConfigRepository,
      NfseProviderCapabilitiesRepository nfseProviderCapabilitiesRepository,
      ContextoTenant contextoTenant) {
    this.nfseConfigRepository = nfseConfigRepository;
    this.nfseProviderCapabilitiesRepository = nfseProviderCapabilitiesRepository;
    this.contextoTenant = contextoTenant;
  }

  @Transactional(readOnly = true)
  public NfseDtos.Config obterConfig(String ambienteRaw) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    NfseAmbiente ambiente = parseAmbiente(ambienteRaw);
    NfseConfigEntity entity =
        nfseConfigRepository
            .findByTenantAndAmbiente(tenantId, ambiente)
            .orElseThrow(
                () ->
                    new ApiClientErrorException(
                        "Configuração NFS-e não encontrada para o ambiente informado.", 404));
    return toConfigDto(entity);
  }

  @Transactional
  public NfseDtos.Config salvarConfig(NfseDtos.Config request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    if (request == null) throw new IllegalArgumentException("Configuração NFS-e obrigatória.");

    NfseAmbiente ambiente = parseAmbiente(request.ambiente);
    validarConfig(request);

    NfseConfigEntity entity =
        nfseConfigRepository.findByTenantAndAmbiente(tenantId, ambiente).orElseGet(NfseConfigEntity::new);
    String municipioNormalizado = normalize(request.municipioCodigoIbge);
    entity.setTenantId(tenantId);
    entity.setAmbiente(ambiente);
    entity.setMunicipioCodigoIbge(municipioNormalizado);
    entity.setProvedor(resolveProvedor(request.provedor, municipioNormalizado));
    entity.setSerieRps(normalize(request.serieRps));
    entity.setAliquotaIssPadrao(request.aliquotaIssPadrao);
    entity.setItemListaServicoPadrao(normalize(request.itemListaServicoPadrao));
    entity.setCodigoTributacaoMunicipio(normalizeOrNull(request.codigoTributacaoMunicipio));
    entity.setApplicationVersion(normalizeOrNull(request.applicationVersion));
    entity.setNationalTaxCodeDefault(normalizeOrNull(request.nationalTaxCodeDefault));
    entity.setNbsCodeDefault(normalizeOrNull(request.nbsCodeDefault));
    entity.setSimplesNacionalSituacao(normalizeOrNull(request.simplesNacionalSituacao));
    entity.setSimplesNacionalRegimeTributacao(normalizeOrNull(request.simplesNacionalRegimeTributacao));
    entity.setEspecialRegimeTributacao(normalizeOrNull(request.especialRegimeTributacao));
    entity.setIbsCbsCst(normalizeOrNull(request.ibsCbsCst));
    entity.setIbsCbsClassTrib(normalizeOrNull(request.ibsCbsClassTrib));
    entity.setIbsCbsIndOp(normalizeOrNull(request.ibsCbsIndOp));
    entity.setPercentualTributosSn(request.percentualTributosSn);
    entity.setPercentualTributosFederais(request.percentualTributosFederais);
    entity.setPercentualTributosEstaduais(request.percentualTributosEstaduais);
    entity.setPercentualTributosMunicipais(request.percentualTributosMunicipais);
    entity.setEmissionMode(parseEmissionMode(request.emissionMode));
    entity.setEmitForCpfMode(parseCpfPolicyMode(request.emitForCpfMode));
    entity.setAutoIssueOnAppointmentClose(request.autoIssueOnAppointmentClose);
    entity.setWsUrl(normalizeOrNull(request.wsUrl));
    entity.setWsUrlHomologacao(normalizeOrNull(request.wsUrlHomologacao));

    nfseConfigRepository.save(entity);
    return toConfigDto(entity);
  }

  /**
   * Resolve o provedor NFS-e a ser usado. Se {@code provedorInformado} estiver preenchido, usa
   * esse valor (o router valida posteriormente na Fronteira 5). Caso contrario, consulta
   * {@code nfse_provider_capabilities} pelo municipio com ordem de preferencia: ABRASF primeiro
   * (padrao nacional), depois SEFIN_NACIONAL, depois outros. Fallback final: ABRASF, caso o
   * municipio nao possua capabilities cadastradas.
   */
  public String resolveProvedor(String provedorInformado, String municipioCodigoIbge) {
    if (!isBlank(provedorInformado)) {
      return provedorInformado.trim();
    }
    if (!isBlank(municipioCodigoIbge)) {
      return nfseProviderCapabilitiesRepository
          .findPreferredProviderForMunicipio(municipioCodigoIbge.trim())
          .orElse("ABRASF");
    }
    return "ABRASF";
  }

  private void validarConfig(NfseDtos.Config request) {
    validarIbsCbs(request);
    if (isBlank(request.municipioCodigoIbge)) {
      throw new IllegalArgumentException("NFSE_CONFIG_MISSING_MUNICIPIO");
    }
    // provedor e opcional: se nao informado, sera resolvido automaticamente por municipio via
    // nfse_provider_capabilities
    if (isBlank(request.serieRps)) throw new IllegalArgumentException("NFSE_CONFIG_MISSING_SERIE_RPS");
    if (request.aliquotaIssPadrao == null) {
      throw new IllegalArgumentException("NFSE_CONFIG_MISSING_ALIQUOTA_ISS");
    }
    if (isBlank(request.itemListaServicoPadrao)) {
      throw new IllegalArgumentException("NFSE_CONFIG_MISSING_ITEM_LISTA_SERVICO");
    }
    String provedorEfetivo =
        isBlank(request.provedor)
            ? resolveProvedor(null, request.municipioCodigoIbge)
            : request.provedor.trim();
    if ("SEFIN_NACIONAL".equalsIgnoreCase(provedorEfetivo)) {
      if (isBlank(request.applicationVersion)) {
        throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_APP_VERSION_REQUIRED");
      }
      if (isBlank(request.nationalTaxCodeDefault)) {
        throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_CTRIBNAC_REQUIRED");
      }
      if (isBlank(request.simplesNacionalSituacao)) {
        throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_OP_SIMP_NAC_REQUIRED");
      }
      if (isBlank(request.especialRegimeTributacao)) {
        throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_REG_ESP_TRIB_REQUIRED");
      }
    }
  }

  /**
   * IBS/CBS: os tres codigos vao juntos ou nenhum (o grupo e opcional em 2026). A classificacao
   * comeca pelos 3 digitos do CST, como na tabela oficial de cClassTrib.
   */
  private void validarIbsCbs(NfseDtos.Config request) {
    String cst = normalizeOrNull(request.ibsCbsCst);
    String classTrib = normalizeOrNull(request.ibsCbsClassTrib);
    String indOp = normalizeOrNull(request.ibsCbsIndOp);
    boolean algum = cst != null || classTrib != null || indOp != null;
    if (!algum) return;
    if (cst == null || classTrib == null || indOp == null) {
      throw new IllegalArgumentException("NFSE_CONFIG_IBS_CBS_INCOMPLETO");
    }
    if (!cst.matches("[0-9]{3}") || !classTrib.matches("[0-9]{6}") || !indOp.matches("[0-9]{6}")) {
      throw new IllegalArgumentException("NFSE_CONFIG_IBS_CBS_FORMATO");
    }
    if (!classTrib.startsWith(cst)) {
      throw new IllegalArgumentException("NFSE_CONFIG_IBS_CBS_CLASS_TRIB_DIFERENTE_DO_CST");
    }
  }

  private NfseDtos.Config toConfigDto(NfseConfigEntity entity) {
    NfseDtos.Config dto = new NfseDtos.Config();
    dto.ambiente = entity.getAmbiente() != null ? entity.getAmbiente().name() : null;
    dto.municipioCodigoIbge = entity.getMunicipioCodigoIbge();
    dto.provedor = entity.getProvedor();
    dto.serieRps = entity.getSerieRps();
    dto.aliquotaIssPadrao = entity.getAliquotaIssPadrao();
    dto.itemListaServicoPadrao = entity.getItemListaServicoPadrao();
    dto.codigoTributacaoMunicipio = entity.getCodigoTributacaoMunicipio();
    dto.applicationVersion = entity.getApplicationVersion();
    dto.nationalTaxCodeDefault = entity.getNationalTaxCodeDefault();
    dto.nbsCodeDefault = entity.getNbsCodeDefault();
    dto.simplesNacionalSituacao = entity.getSimplesNacionalSituacao();
    dto.simplesNacionalRegimeTributacao = entity.getSimplesNacionalRegimeTributacao();
    dto.especialRegimeTributacao = entity.getEspecialRegimeTributacao();
    dto.ibsCbsCst = entity.getIbsCbsCst();
    dto.ibsCbsClassTrib = entity.getIbsCbsClassTrib();
    dto.ibsCbsIndOp = entity.getIbsCbsIndOp();
    dto.percentualTributosSn = entity.getPercentualTributosSn();
    dto.percentualTributosFederais = entity.getPercentualTributosFederais();
    dto.percentualTributosEstaduais = entity.getPercentualTributosEstaduais();
    dto.percentualTributosMunicipais = entity.getPercentualTributosMunicipais();
    dto.emissionMode = entity.getEmissionMode() != null ? entity.getEmissionMode().name() : null;
    dto.emitForCpfMode = entity.getEmitForCpfMode() != null ? entity.getEmitForCpfMode().name() : null;
    dto.autoIssueOnAppointmentClose = entity.isAutoIssueOnAppointmentClose();
    dto.wsUrl = entity.getWsUrl();
    dto.wsUrlHomologacao = entity.getWsUrlHomologacao();
    return dto;
  }

  private NfseAmbiente parseAmbiente(String value) {
    String raw = isBlank(value) ? NfseAmbiente.HOMOLOGACAO.name() : value;
    try {
      return NfseAmbiente.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Ambiente NFS-e inválido.");
    }
  }

  private NfseEmissionMode parseEmissionMode(String value) {
    if (isBlank(value)) return NfseEmissionMode.ASK_ON_CLOSE;
    try {
      return NfseEmissionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Modo de emissão NFS-e inválido.");
    }
  }

  private NfseCpfPolicyMode parseCpfPolicyMode(String value) {
    if (isBlank(value)) return NfseCpfPolicyMode.ASK;
    try {
      return NfseCpfPolicyMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Politica CPF NFS-e inválida.");
    }
  }

  private String normalize(String value) {
    if (isBlank(value)) throw new IllegalArgumentException("Campo obrigatório ausente.");
    return value.trim();
  }

  private String normalizeOrNull(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
