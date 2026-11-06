package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCustomerType;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceItemRepository;

/**
 * Monta a DPS (Declaracao de Prestacao de Servico) do Emissor Nacional, no leiaute 1.01 da
 * NT SE/CGNFS-e 009, e a confere contra o esquema oficial ({@link LeiauteNfseNacional}).
 *
 * <p>06/11/2026: reescrito a partir do XSD e do Anexo VI oficiais. O XML antigo (versao 1.00) tinha
 * campos fora de ordem e fora do grupo {@code regTrib}, o nome do prestador (proibido quando ele
 * emite), data em UTC "Z", serie completada com zeros a direita e sem {@code finNFSe}. A Receita
 * recusaria todos.
 */
@Service
public class SefinNacionalXmlLayoutBuilder extends AbstractNfseXmlLayoutBuilder {
  private static final String NS_NFSE = "http://www.sped.fazenda.gov.br/nfse";
  /** {@code xxx} da sempre o deslocamento (-03:00); {@code XXX} daria "Z" em UTC, que o esquema recusa. */
  private static final DateTimeFormatter DATA_HORA_COM_FUSO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
  private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");

  private final NfseConfigRepository nfseConfigRepository;
  private final NfseInvoiceItemRepository nfseInvoiceItemRepository;
  private final FiscalTaxConfigRepository fiscalTaxConfigRepository;

  public SefinNacionalXmlLayoutBuilder(
      NfseConfigRepository nfseConfigRepository,
      NfseInvoiceItemRepository nfseInvoiceItemRepository,
      FiscalTaxConfigRepository fiscalTaxConfigRepository) {
    this.nfseConfigRepository = nfseConfigRepository;
    this.nfseInvoiceItemRepository = nfseInvoiceItemRepository;
    this.fiscalTaxConfigRepository = fiscalTaxConfigRepository;
  }

  @Override
  public boolean supports(String providerCode) {
    return providerCode != null && "SEFIN_NACIONAL".equalsIgnoreCase(providerCode);
  }

  @Override
  public String buildAndValidateAuthorizationXml(NfseInvoiceEntity invoice) {
    if (invoice == null) throw new IllegalArgumentException("NFS-e obrigatória para gerar XML.");
    NfseConfigEntity config = nfseConfigRepository.findByTenantAndAmbiente(invoice.getTenantId(), invoice.getAmbiente())
        .orElseThrow(() -> new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_CONFIG_MISSING"));
    FiscalTaxConfigEntity taxConfig = fiscalTaxConfigRepository.findByTenantId(invoice.getTenantId())
        .orElseThrow(() -> new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_TAX_CONFIG_MISSING"));
    List<NfseInvoiceItemEntity> items = nfseInvoiceItemRepository.listByTenantAndInvoice(invoice.getTenantId(), invoice.getId());
    return montar(invoice, config, taxConfig, items);
  }

  /**
   * Monta e confere (XSD oficial) uma DPS a partir de dados em memoria, sem ler nem gravar nada. Usada
   * pelo assistente de configuracao para mostrar o que falta antes do primeiro envio.
   */
  public String montarDpsDeExemplo(
      NfseInvoiceEntity invoice,
      NfseConfigEntity config,
      FiscalTaxConfigEntity taxConfig,
      List<NfseInvoiceItemEntity> items) {
    return montar(invoice, config, taxConfig, items);
  }

  private String montar(
      NfseInvoiceEntity invoice,
      NfseConfigEntity config,
      FiscalTaxConfigEntity taxConfig,
      List<NfseInvoiceItemEntity> items) {
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_ITEMS_REQUIRED");
    }

    PrestadorIdentity prestador = resolvePrestador(taxConfig);
    String applicationVersion = requireConfigured(config.getApplicationVersion(), "NFSE_PROVIDER_SEFIN_NACIONAL_APP_VERSION_REQUIRED");
    String opSimpNac = requireConfigured(config.getSimplesNacionalSituacao(), "NFSE_PROVIDER_SEFIN_NACIONAL_OP_SIMP_NAC_REQUIRED");
    String regEspTrib = requireConfigured(config.getEspecialRegimeTributacao(), "NFSE_PROVIDER_SEFIN_NACIONAL_REG_ESP_TRIB_REQUIRED");
    boolean meEpp = "3".equals(opSimpNac);
    boolean mei = "2".equals(opSimpNac);
    // E0162 / E0166: o regime de apuracao do SN so existe (e e obrigatorio) para ME/EPP.
    String regApTribSN = meEpp
        ? requireConfigured(config.getSimplesNacionalRegimeTributacao(), "NFSE_PROVIDER_SEFIN_NACIONAL_REG_AP_TRIB_SN_REQUIRED")
        : null;
    if (invoice.getValorDeducoes() != null && invoice.getValorDeducoes().compareTo(BigDecimal.ZERO) > 0) {
      // O leiaute 1.01 trocou vDedRed por vAjusteBC, que exige os documentos de cada ajuste.
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_DEDUCAO_NAO_SUPORTADA");
    }

    String serie = serieDaDps(invoice.getSerieRps());
    String cTribNac = resolveNationalTaxCode(invoice, config, items);
    String cNbs = resolveNbsCode(invoice, config, items);
    String serviceDescription = buildServiceDescription(invoice, items);
    String idDps = buildDpsId(invoice, prestador, serie);
    String cLocPrestacao = digitsOnly(firstNonBlank(invoice.getLocalPrestacaoCodigoIbge(), invoice.getMunicipioCodigoIbge()));
    // A DPS e emitida agora (na assinatura), com o fuso: o esquema nao aceita "Z".
    String dhEmi = OffsetDateTime.now(ZONA_BRASIL).withNano(0).format(DATA_HORA_COM_FUSO);
    String dCompet = (invoice.getDataCompetencia() != null ? invoice.getDataCompetencia() : LocalDate.now(ZONA_BRASIL)).toString();
    String tpAmb = invoice.getAmbiente() == NfseAmbiente.PRODUCAO ? "1" : "2";
    String tpRetIssqn = invoice.isIssRetido() ? "2" : "1";
    String tribIssqn = "1";
    String ibsCbs = buildIbsCbs(invoice, config, cNbs);

    StringBuilder xml = new StringBuilder(4096);
    xml.append("<DPS xmlns=\"").append(NS_NFSE).append("\" versao=\"").append(LeiauteNfseNacional.VERSAO).append("\">")
        .append("<infDPS Id=\"").append(idDps).append("\">")
        .append("<tpAmb>").append(tpAmb).append("</tpAmb>")
        .append("<dhEmi>").append(dhEmi).append("</dhEmi>")
        .append("<verAplic>").append(escape(applicationVersion)).append("</verAplic>")
        .append("<serie>").append(serie).append("</serie>")
        .append("<nDPS>").append(escape(String.valueOf(invoice.getNumeroRps()))).append("</nDPS>")
        .append("<dCompet>").append(dCompet).append("</dCompet>")
        .append("<tpEmit>1</tpEmit>")
        // NT 009: finalidade da NFS-e, 0 = regular (o unico valor do esquema 1.01).
        .append("<finNFSe>0</finNFSe>")
        .append("<cLocEmi>").append(escape(digitsOnly(invoice.getMunicipioCodigoIbge()))).append("</cLocEmi>")
        .append("<prest>")
        .append(prestador.documentTag())
        .append(optionalTag("IM", prestador.inscricaoMunicipal()))
        // Sem xNome nem endereco: o proprio prestador emite (E0121, E0128); a Receita usa o cadastro.
        .append(optionalTag("fone", digitsOrNullForPhone(taxConfig.getIssuerPhone())))
        .append(optionalTag("email", taxConfig.getIssuerEmail()))
        .append("<regTrib>")
        .append("<opSimpNac>").append(escape(opSimpNac)).append("</opSimpNac>")
        .append(optionalTag("regApTribSN", regApTribSN))
        .append("<regEspTrib>").append(escape(regEspTrib)).append("</regEspTrib>")
        .append("</regTrib>")
        .append("</prest>")
        .append(buildTomador(invoice))
        .append("<serv>")
        .append("<locPrest>")
        .append("<cLocPrestacao>").append(escape(cLocPrestacao)).append("</cLocPrestacao>")
        .append("</locPrest>")
        .append("<cServ>")
        .append("<cTribNac>").append(escape(cTribNac)).append("</cTribNac>")
        .append(optionalTag("cTribMun", invoice.getCodigoTributacaoMunicipio()))
        .append(optionalTag("cNBS", cNbs))
        .append("<xDescServ>").append(escape(serviceDescription)).append("</xDescServ>")
        .append("</cServ>")
        .append("</serv>")
        .append("<valores>")
        .append("<vServPrest>")
        .append("<vServ>").append(formatAmount(invoice.getValorServicos(), 2)).append("</vServ>")
        .append("</vServPrest>")
        .append("<trib>")
        .append("<tribMun>")
        .append("<tribISSQN>").append(tribIssqn).append("</tribISSQN>")
        .append("<tpRetISSQN>").append(tpRetIssqn).append("</tpRetISSQN>")
        .append(optionalTag("pAliq", informaAliquota(opSimpNac, regApTribSN, regEspTrib, invoice.isIssRetido())
            ? formatOptionalAmount(invoice.getAliquotaIss(), 2)
            : null))
        .append("</tribMun>")
        .append(buildTotalDeTributos(config, meEpp, mei))
        .append("</trib>")
        .append("</valores>")
        .append(ibsCbs)
        .append("</infDPS>")
        .append("</DPS>");

    String xmlString = xml.toString();
    validateWellFormed(xmlString);
    LeiauteNfseNacional.validarDps(xmlString);
    return xmlString;
  }

  /**
   * Quando a aliquota do ISS vai na DPS, pelas regras E0595-E0640 (municipio com convenio ativo,
   * que e o caso de quem emite pelo Emissor Nacional): so para ME/EPP que apura o ISS pelo Simples
   * (regApTribSN = 1), com ISS retido e sem regime especial. Nos outros casos a Receita usa a
   * aliquota do municipio e recusa a DPS que a informa.
   */
  static boolean informaAliquota(String opSimpNac, String regApTribSN, String regEspTrib, boolean issRetido) {
    return "3".equals(opSimpNac) && "1".equals(regApTribSN) && "0".equals(regEspTrib) && issRetido;
  }

  /**
   * Total aproximado de tributos (Lei 12.741). ME/EPP: percentual do Simples, porque "nao informar"
   * e proibido (E0712). Nao optante: percentuais federal, estadual e municipal, porque "nao
   * informar" e o percentual do Simples sao proibidos (E0713). MEI: "nao informar" (E0710 proibe o
   * percentual do Simples).
   */
  private String buildTotalDeTributos(NfseConfigEntity config, boolean meEpp, boolean mei) {
    if (mei) return "<totTrib><indTotTrib>0</indTotTrib></totTrib>";
    if (meEpp) {
      BigDecimal sn = config.getPercentualTributosSn();
      if (sn == null) throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_PERCENTUAL_TRIBUTOS_SN_REQUIRED");
      return "<totTrib><pTotTribSN>" + formatAmount(sn, 2) + "</pTotTribSN></totTrib>";
    }
    BigDecimal fed = config.getPercentualTributosFederais();
    BigDecimal est = config.getPercentualTributosEstaduais();
    BigDecimal mun = config.getPercentualTributosMunicipais();
    if (fed == null || est == null || mun == null) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_PERCENTUAL_TRIBUTOS_REQUIRED");
    }
    return "<totTrib><pTotTrib>"
        + "<pTotTribFed>" + formatAmount(fed, 2) + "</pTotTribFed>"
        + "<pTotTribEst>" + formatAmount(est, 2) + "</pTotTribEst>"
        + "<pTotTribMun>" + formatAmount(mun, 2) + "</pTotTribMun>"
        + "</pTotTrib></totTrib>";
  }

  /**
   * Grupo IBS/CBS (reforma tributaria). A DPS leva so a classificacao; os valores do IBS e da CBS
   * sao calculados pela Receita. Opcional em 2026: sem a classificacao configurada, o grupo nao vai.
   * Quando vai, o NBS e obrigatorio (E0322) e o cIndOp tambem (E0860).
   */
  private String buildIbsCbs(NfseInvoiceEntity invoice, NfseConfigEntity config, String cNbs) {
    String cst = config.getIbsCbsCst();
    String classTrib = config.getIbsCbsClassTrib();
    String indOp = config.getIbsCbsIndOp();
    if (isBlank(cst) && isBlank(classTrib) && isBlank(indOp)) return "";
    if (isBlank(cst) || isBlank(classTrib) || isBlank(indOp)) {
      throw new IllegalArgumentException("NFSE_CONFIG_IBS_CBS_INCOMPLETO");
    }
    if (isBlank(cNbs)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_NBS_REQUIRED_IBS_CBS");
    }
    // Consumidor final: pessoa fisica (ou sem tomador identificado). Pessoa juridica: nao.
    String indFinal = invoice.getCustomerType() == NfseCustomerType.CNPJ ? "0" : "1";
    return "<IBSCBS>"
        + "<indFinal>" + indFinal + "</indFinal>"
        + "<cIndOp>" + escape(indOp.trim()) + "</cIndOp>"
        // O destinatario e o proprio tomador (dest proibido com indDest = 0, E0908).
        + "<indDest>0</indDest>"
        + "<valores><trib>"
        + "<CST>" + escape(cst.trim()) + "</CST>"
        + "<cClassTrib>" + escape(classTrib.trim()) + "</cClassTrib>"
        + "</trib></valores>"
        + "</IBSCBS>";
  }

  /** Serie da DPS: so digitos, 1 a 5, como o esquema exige (antes, "1" virava "10000"). */
  static String serieDaDps(String serieRps) {
    String serie = serieRps == null ? "" : serieRps.trim();
    if (!serie.matches("[0-9]{1,4}|[0-8][0-9]{4}")) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_SERIE_INVALIDA");
    }
    return serie;
  }

  private String digitsOrNullForPhone(String value) {
    String digits = digitsOnly(value);
    return digits.length() >= 6 && digits.length() <= 20 ? digits : null;
  }

  @Override
  public String buildAuthorizationReturnXml(
      NfseInvoiceEntity invoice,
      String providerCode,
      String providerMessage) {
    String xml = "<nfseAuthorizationResponse>"
        + "<invoiceId>" + escape(invoice != null && invoice.getId() != null ? invoice.getId().toString() : null) + "</invoiceId>"
        + "<status>" + escape(invoice != null && invoice.getFiscalStatus() != null ? invoice.getFiscalStatus().name() : null) + "</status>"
        + "<providerCode>" + escape(providerCode) + "</providerCode>"
        + "<providerMessage>" + escape(providerMessage) + "</providerMessage>"
        + "</nfseAuthorizationResponse>";
    validateWellFormed(xml);
    return xml;
  }

  @Override
  public String buildCancelReturnXml(NfseInvoiceEntity invoice, String providerCode, String providerMessage) {
    String xml = "<nfseCancelResponse>"
        + "<invoiceId>" + escape(invoice != null && invoice.getId() != null ? invoice.getId().toString() : null) + "</invoiceId>"
        + "<status>" + escape(invoice != null && invoice.getFiscalStatus() != null ? invoice.getFiscalStatus().name() : null) + "</status>"
        + "<providerCode>" + escape(providerCode) + "</providerCode>"
        + "<providerMessage>" + escape(providerMessage) + "</providerMessage>"
        + "</nfseCancelResponse>";
    validateWellFormed(xml);
    return xml;
  }

  private String buildTomador(NfseInvoiceEntity invoice) {
    if (invoice == null) return "";
    StringBuilder xml = new StringBuilder();
    xml.append("<toma>");
    if (invoice.getCustomerType() == NfseCustomerType.EXTERIOR) {
      String document = digitsOnly(invoice.getCustomerDocument());
      if (!isBlank(document)) {
        xml.append("<NIF>").append(escape(document)).append("</NIF>");
      } else {
        xml.append("<cNaoNIF>0</cNaoNIF>");
      }
    } else {
      String document = digitsOnly(invoice.getCustomerDocument());
      if (document.length() > 11) {
        xml.append("<CNPJ>").append(escape(leftPad(document, 14))).append("</CNPJ>");
      } else {
        xml.append("<CPF>").append(escape(leftPad(document, 11))).append("</CPF>");
      }
    }
    xml.append("<xNome>").append(escape(invoice.getCustomerName())).append("</xNome>")
        .append(optionalTag("fone", digitsOnly(invoice.getCustomerPhone())))
        .append(optionalTag("email", invoice.getCustomerEmail()))
        .append("</toma>");
    return xml.toString();
  }

  private PrestadorIdentity resolvePrestador(FiscalTaxConfigEntity taxConfig) {
    String cnpj = digitsOnly(taxConfig != null ? taxConfig.getIssuerCnpj() : null);
    if (cnpj.length() != 14) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_PRESTADOR_CNPJ_REQUIRED");
    }
    String nome = firstNonBlank(
        taxConfig != null ? taxConfig.getIssuerRazaoSocial() : null,
        taxConfig != null ? taxConfig.getIssuerNomeFantasia() : null);
    if (isBlank(nome)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_PRESTADOR_NAME_REQUIRED");
    }
    return new PrestadorIdentity(cnpj, digitsOnly(taxConfig.getIssuerIm()), nome);
  }

  private String resolveNationalTaxCode(
      NfseInvoiceEntity invoice,
      NfseConfigEntity config,
      List<NfseInvoiceItemEntity> items) {
    String itemValue = items.stream()
        .map(item -> normalizeDigitsOrNull(item.getNationalTaxCode(), 6))
        .filter(value -> value != null)
        .findFirst()
        .orElse(null);
    String value = firstNonBlank(
        normalizeDigitsOrNull(invoice.getNationalTaxCode(), 6),
        itemValue,
        normalizeDigitsOrNull(config.getNationalTaxCodeDefault(), 6));
    if (isBlank(value)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_CTRIBNAC_REQUIRED");
    }
    return value;
  }

  private String resolveNbsCode(
      NfseInvoiceEntity invoice,
      NfseConfigEntity config,
      List<NfseInvoiceItemEntity> items) {
    String itemValue = items.stream()
        .map(item -> normalizeDigitsOrNull(item.getNbsCode(), 9))
        .filter(value -> value != null)
        .findFirst()
        .orElse(null);
    return firstNonBlank(normalizeDigitsOrNull(invoice.getNbsCode(), 9), itemValue, normalizeDigitsOrNull(config.getNbsCodeDefault(), 9));
  }

  private String buildServiceDescription(NfseInvoiceEntity invoice, List<NfseInvoiceItemEntity> items) {
    String joined = items.stream()
        .map(NfseInvoiceItemEntity::getDescricaoServico)
        .filter(value -> !isBlank(value))
        .distinct()
        .reduce((left, right) -> left + " | " + right)
        .orElse(null);
    return firstNonBlank(joined, invoice.getNaturezaOperacao(), "Servico prestado");
  }

  private String buildDpsId(NfseInvoiceEntity invoice, PrestadorIdentity prestador, String serie) {
    String municipio = normalizeDigitsOrNull(invoice.getMunicipioCodigoIbge(), 7);
    if (isBlank(municipio)) {
      throw new IllegalArgumentException("NFSE_CONFIG_MISSING_MUNICIPIO");
    }
    // Id = "DPS" + municipio (7) + tipo de inscricao (1 CPF, 2 CNPJ) + inscricao (14) + serie (5) + numero (15).
    String tipoInscricao = prestador.cnpj().length() == 14 ? "2" : "1";
    String inscricaoFederal = prestador.cnpj().length() == 14 ? prestador.cnpj() : leftPad(prestador.cnpj(), 14);
    return "DPS" + municipio + tipoInscricao + inscricaoFederal + leftPad(serie, 5)
        + leftPad(String.valueOf(invoice.getNumeroRps()), 15);
  }

  private String optionalTag(String tagName, String value) {
    if (isBlank(value)) return "";
    return "<" + tagName + ">" + escape(value) + "</" + tagName + ">";
  }

  private String formatAmount(BigDecimal value, int scale) {
    BigDecimal safe = value == null ? BigDecimal.ZERO : value;
    return safe.setScale(scale, RoundingMode.HALF_UP).toPlainString();
  }

  private String formatOptionalAmount(BigDecimal value, int scale) {
    if (value == null) return null;
    return formatAmount(value, scale);
  }

  private String requireConfigured(String value, String errorCode) {
    if (isBlank(value)) throw new IllegalArgumentException(errorCode);
    return value.trim();
  }

  private String normalizeDigitsOrNull(String value, int expectedLength) {
    String digits = digitsOnly(value);
    if (digits.isBlank() || digits.length() != expectedLength) return null;
    return digits;
  }

  private String digitsOnly(String value) {
    if (value == null || value.isBlank()) return "";
    return value.replaceAll("\\D", "");
  }

  private String leftPad(String value, int size) {
    String raw = value == null ? "" : value.trim();
    if (raw.length() >= size) return raw.substring(raw.length() - size);
    return "0".repeat(size - raw.length()) + raw;
  }

  private String padRightDigitsOrText(String value, int size) {
    String raw = value == null ? "" : value.trim();
    if (raw.length() >= size) return raw.substring(0, size);
    return raw + "0".repeat(size - raw.length());
  }

  private String rightPadDigitsOrText(String value, int size) {
    return padRightDigitsOrText(value, size);
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (!isBlank(value)) return value.trim();
    }
    return null;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record PrestadorIdentity(String cnpj, String inscricaoMunicipal, String nome) {
    String documentTag() {
      return "<CNPJ>" + cnpj + "</CNPJ>";
    }
  }
}
