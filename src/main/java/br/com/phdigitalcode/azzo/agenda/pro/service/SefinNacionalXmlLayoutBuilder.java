package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Porte verbatim de {@code modules/nfse/application/xml/SefinNacionalXmlLayoutBuilder.java}.
 * Monta o {@code DPS} (Declaracao de Prestacao de Servico) do layout nacional (SEFIN Nacional /
 * NFS-e Nacional), diferente do ABRASF. So verifica boa formacao XML, nao valida contra o XSD
 * oficial nacional (achado 6 da Etapa 25, preservado).
 */
@Service
public class SefinNacionalXmlLayoutBuilder extends AbstractNfseXmlLayoutBuilder {
  private static final String NS_NFSE = "http://www.sped.fazenda.gov.br/nfse";
  private static final DateTimeFormatter UTC_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

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
    if (invoice == null) throw new IllegalArgumentException("NFS-e obrigatoria para gerar XML.");
    NfseConfigEntity config = nfseConfigRepository.findByTenantAndAmbiente(invoice.getTenantId(), invoice.getAmbiente())
        .orElseThrow(() -> new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_CONFIG_MISSING"));
    FiscalTaxConfigEntity taxConfig = fiscalTaxConfigRepository.findByTenantId(invoice.getTenantId())
        .orElseThrow(() -> new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_TAX_CONFIG_MISSING"));
    List<NfseInvoiceItemEntity> items = nfseInvoiceItemRepository.listByTenantAndInvoice(invoice.getTenantId(), invoice.getId());
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_ITEMS_REQUIRED");
    }

    PrestadorIdentity prestador = resolvePrestador(taxConfig);
    String applicationVersion = requireConfigured(config.getApplicationVersion(), "NFSE_PROVIDER_SEFIN_NACIONAL_APP_VERSION_REQUIRED");
    String opSimpNac = requireConfigured(config.getSimplesNacionalSituacao(), "NFSE_PROVIDER_SEFIN_NACIONAL_OP_SIMP_NAC_REQUIRED");
    String regEspTrib = requireConfigured(config.getEspecialRegimeTributacao(), "NFSE_PROVIDER_SEFIN_NACIONAL_REG_ESP_TRIB_REQUIRED");
    String cTribNac = resolveNationalTaxCode(invoice, config, items);
    String cNbs = resolveNbsCode(invoice, config, items);
    String serviceDescription = buildServiceDescription(invoice, items);
    String idDps = buildDpsId(invoice, prestador);
    String cLocPrestacao = digitsOnly(firstNonBlank(invoice.getLocalPrestacaoCodigoIbge(), invoice.getMunicipioCodigoIbge()));
    String dhEmi = formatUtc(invoice.getCreatedAt() != null ? invoice.getCreatedAt() : Instant.now());
    String dCompet = (invoice.getDataCompetencia() != null ? invoice.getDataCompetencia() : LocalDate.now()).toString();
    String tpAmb = invoice.getAmbiente() == NfseAmbiente.PRODUCAO ? "1" : "2";
    String tpRetIssqn = invoice.isIssRetido() ? "2" : "1";
    String tribIssqn = invoice.getCustomerType() == NfseCustomerType.EXTERIOR ? "2" : "1";

    StringBuilder xml = new StringBuilder(4096);
    xml.append("<DPS xmlns=\"").append(NS_NFSE).append("\" versao=\"1.00\">")
        .append("<infDPS Id=\"").append(idDps).append("\">")
        .append("<tpAmb>").append(tpAmb).append("</tpAmb>")
        .append("<dhEmi>").append(dhEmi).append("</dhEmi>")
        .append("<verAplic>").append(escape(applicationVersion)).append("</verAplic>")
        .append("<serie>").append(escape(padRightDigitsOrText(invoice.getSerieRps(), 5))).append("</serie>")
        .append("<nDPS>").append(escape(String.valueOf(invoice.getNumeroRps()))).append("</nDPS>")
        .append("<dCompet>").append(dCompet).append("</dCompet>")
        .append("<tpEmit>1</tpEmit>")
        .append("<cLocEmi>").append(escape(digitsOnly(invoice.getMunicipioCodigoIbge()))).append("</cLocEmi>")
        .append("<prest>")
        .append(prestador.documentTag())
        .append(optionalTag("IM", prestador.inscricaoMunicipal()))
        .append("<xNome>").append(escape(prestador.nome())).append("</xNome>")
        .append(optionalTag("fone", digitsOnly(taxConfig.getIssuerPhone())))
        .append(optionalTag("email", taxConfig.getIssuerEmail()))
        .append("<opSimpNac>").append(escape(opSimpNac)).append("</opSimpNac>")
        .append(optionalTag("regApTribSN", config.getSimplesNacionalRegimeTributacao()))
        .append("<regEspTrib>").append(escape(regEspTrib)).append("</regEspTrib>")
        .append("</prest>")
        .append(buildTomador(invoice))
        .append("<serv>")
        .append("<locPrest>")
        .append(optionalTag("cLocPrestacao", cLocPrestacao))
        .append("</locPrest>")
        .append("<cServ>")
        .append("<cTribNac>").append(escape(cTribNac)).append("</cTribNac>")
        .append(optionalTag("cTribMun", invoice.getCodigoTributacaoMunicipio()))
        .append("<xDescServ>").append(escape(serviceDescription)).append("</xDescServ>")
        .append(optionalTag("cNBS", cNbs))
        .append("</cServ>")
        .append("</serv>")
        .append("<valores>")
        .append("<vServPrest>")
        .append("<vServ>").append(formatAmount(invoice.getValorServicos(), 2)).append("</vServ>")
        .append("</vServPrest>");

    if (invoice.getValorDeducoes() != null && invoice.getValorDeducoes().compareTo(BigDecimal.ZERO) > 0) {
      xml.append("<vDedRed><vDR>")
          .append(formatAmount(invoice.getValorDeducoes(), 2))
          .append("</vDR></vDedRed>");
    }

    xml.append("<trib>")
        .append("<tribMun>")
        .append("<tribISSQN>").append(tribIssqn).append("</tribISSQN>")
        .append(optionalTag("pAliq", formatOptionalAmount(invoice.getAliquotaIss(), 2)))
        .append("<tpRetISSQN>").append(tpRetIssqn).append("</tpRetISSQN>")
        .append("</tribMun>")
        .append("<totTrib><indTotTrib>0</indTotTrib></totTrib>")
        .append("</trib>")
        .append("</valores>")
        .append("</infDPS>")
        .append("</DPS>");

    String xmlString = xml.toString();
    validateWellFormed(xmlString);
    return xmlString;
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

  private String buildDpsId(NfseInvoiceEntity invoice, PrestadorIdentity prestador) {
    String municipio = normalizeDigitsOrNull(invoice.getMunicipioCodigoIbge(), 7);
    if (isBlank(municipio)) {
      throw new IllegalArgumentException("NFSE_CONFIG_MISSING_MUNICIPIO");
    }
    String tipoInscricao = prestador.cnpj().length() == 14 ? "2" : "1";
    String inscricaoFederal = prestador.cnpj().length() == 14 ? prestador.cnpj() : leftPad(prestador.cnpj(), 14);
    String serie = rightPadDigitsOrText(invoice.getSerieRps(), 5);
    String numero = leftPad(String.valueOf(invoice.getNumeroRps()), 15);
    return "DPS" + municipio + tipoInscricao + inscricaoFederal + serie + numero;
  }

  private String formatUtc(Instant instant) {
    return UTC_DATETIME.format(instant.atOffset(ZoneOffset.UTC));
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
