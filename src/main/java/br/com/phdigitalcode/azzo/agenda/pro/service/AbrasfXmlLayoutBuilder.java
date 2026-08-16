package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;

/**
 * Porte verbatim de {@code modules/nfse/application/xml/AbrasfXmlLayoutBuilder.java}. Monta o
 * envelope {@code EnviarLoteRpsEnvio} (ABRASF 2.04) por concatenacao de string — sem biblioteca
 * SOAP dedicada, mesmo padrao do original. So verifica boa formacao XML ({@link
 * #validateWellFormed(String)}), nao valida contra o XSD oficial.
 */
@Service
public class AbrasfXmlLayoutBuilder extends AbstractNfseXmlLayoutBuilder {
  private static final DateTimeFormatter ABRASF_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter ABRASF_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
  private static final String NS_ABRASF = "http://www.abrasf.org.br/nfse.xsd";

  private final FiscalTaxConfigRepository fiscalTaxConfigRepository;
  private final NfseConfigRepository nfseConfigRepository;

  public AbrasfXmlLayoutBuilder(
      FiscalTaxConfigRepository fiscalTaxConfigRepository, NfseConfigRepository nfseConfigRepository) {
    this.fiscalTaxConfigRepository = fiscalTaxConfigRepository;
    this.nfseConfigRepository = nfseConfigRepository;
  }

  @Override
  public boolean supports(String providerCode) {
    return providerCode != null
        && ("ABRASF".equalsIgnoreCase(providerCode) || "ABRASF_204".equalsIgnoreCase(providerCode));
  }

  @Override
  public String buildAndValidateAuthorizationXml(NfseInvoiceEntity invoice) {
    if (invoice == null) throw new IllegalArgumentException("NFS-e obrigatoria para gerar XML.");
    String infRpsId = "Rps" + escape(invoice.getId() != null ? invoice.getId().toString().replace("-", "") : "AUTO");
    String emissao = invoice.getCreatedAt() != null
        ? invoice.getCreatedAt().atOffset(ZoneOffset.UTC).format(ABRASF_DATETIME)
        : OffsetDateTime.now(ZoneOffset.UTC).format(ABRASF_DATETIME);
    String competencia = (invoice.getDataCompetencia() != null ? invoice.getDataCompetencia() : LocalDate.now())
        .format(ABRASF_DATE);
    String issRetido = invoice.isIssRetido() ? "1" : "2";
    String cpfCnpjTomador = escape(invoice.getCustomerDocument());
    PrestadorIdentity prestador = resolvePrestadorIdentity(invoice);
    String optanteSimplesNacional = resolveOptanteSimplesNacional(invoice);

    String tomadorIdentificacao = "";
    if (invoice.getCustomerType() != null
        && !"EXTERIOR".equalsIgnoreCase(invoice.getCustomerType().name())
        && !cpfCnpjTomador.isBlank()) {
      String cpfCnpjTag = cpfCnpjTomador.length() > 11 ? "Cnpj" : "Cpf";
      tomadorIdentificacao = "<IdentificacaoTomador><CpfCnpj><" + cpfCnpjTag + ">"
          + cpfCnpjTomador
          + "</" + cpfCnpjTag + "></CpfCnpj></IdentificacaoTomador>";
    }

    String xml = "<EnviarLoteRpsEnvio xmlns=\"" + NS_ABRASF + "\">"
        + "<LoteRps Id=\"Lote" + escape(invoice.getId() != null ? invoice.getId().toString().replace("-", "") : "AUTO") + "\" versao=\"2.04\">"
        + "<NumeroLote>" + (invoice.getNumeroRps() != null ? invoice.getNumeroRps() : 1) + "</NumeroLote>"
        + "<CpfCnpj><Cnpj>" + prestador.cnpj() + "</Cnpj></CpfCnpj>"
        + "<InscricaoMunicipal>" + prestador.inscricaoMunicipal() + "</InscricaoMunicipal>"
        + "<QuantidadeRps>1</QuantidadeRps>"
        + "<ListaRps>"
        + "<Rps>"
        + "<InfDeclaracaoPrestacaoServico Id=\"" + infRpsId + "\">"
        + "<Rps><IdentificacaoRps><Numero>" + (invoice.getNumeroRps() != null ? invoice.getNumeroRps() : 1) + "</Numero>"
        + "<Serie>" + escape(invoice.getSerieRps()) + "</Serie><Tipo>1</Tipo></IdentificacaoRps>"
        + "<DataEmissao>" + emissao + "</DataEmissao><Status>1</Status></Rps>"
        + "<Competencia>" + competencia + "</Competencia>"
        + "<Servico><Valores>"
        + "<ValorServicos>" + formatAmount(invoice.getValorServicos(), 2) + "</ValorServicos>"
        + "<ValorDeducoes>" + formatAmount(invoice.getValorDeducoes(), 2) + "</ValorDeducoes>"
        + "<ValorIss>" + formatAmount(invoice.getValorIss(), 2) + "</ValorIss>"
        + "<Aliquota>" + formatAmount(invoice.getAliquotaIss(), 4) + "</Aliquota>"
        + "<IssRetido>" + issRetido + "</IssRetido>"
        + "</Valores>"
        + "<ItemListaServico>" + escape(invoice.getItemListaServico()) + "</ItemListaServico>"
        + "<CodigoTributacaoMunicipio>" + escape(invoice.getCodigoTributacaoMunicipio()) + "</CodigoTributacaoMunicipio>"
        + "<Discriminacao>" + escape(invoice.getNaturezaOperacao()) + "</Discriminacao>"
        + "<CodigoMunicipio>" + escape(invoice.getMunicipioCodigoIbge()) + "</CodigoMunicipio>"
        + "</Servico>"
        + "<Prestador><CpfCnpj><Cnpj>" + prestador.cnpj() + "</Cnpj></CpfCnpj><InscricaoMunicipal>" + prestador.inscricaoMunicipal() + "</InscricaoMunicipal></Prestador>"
        + "<TomadorServico>"
        + tomadorIdentificacao
        + "<RazaoSocial>" + escape(invoice.getCustomerName()) + "</RazaoSocial>"
        + "<Contato><Telefone>" + escape(invoice.getCustomerPhone()) + "</Telefone><Email>" + escape(invoice.getCustomerEmail()) + "</Email></Contato>"
        + "</TomadorServico>"
        + "<OptanteSimplesNacional>" + optanteSimplesNacional + "</OptanteSimplesNacional>"
        + "<IncentivoFiscal>2</IncentivoFiscal>"
        + "</InfDeclaracaoPrestacaoServico>"
        + "</Rps>"
        + "</ListaRps>"
        + "</LoteRps>"
        + "</EnviarLoteRpsEnvio>";
    validateWellFormed(xml);
    return xml;
  }

  @Override
  public String buildAuthorizationReturnXml(
      NfseInvoiceEntity invoice,
      String providerCode,
      String providerMessage) {
    String xml = "<nfseAuthorizationResponse>"
        + "<invoiceId>" + escape(invoice.getId() != null ? invoice.getId().toString() : null) + "</invoiceId>"
        + "<status>" + escape(invoice.getFiscalStatus() != null ? invoice.getFiscalStatus().name() : null) + "</status>"
        + "<numeroNfse>" + escape(invoice.getNumeroNfse()) + "</numeroNfse>"
        + "<protocolo>" + escape(invoice.getProtocolo()) + "</protocolo>"
        + "<codigoVerificacao>" + escape(invoice.getCodigoVerificacao()) + "</codigoVerificacao>"
        + "<providerCode>" + escape(providerCode) + "</providerCode>"
        + "<providerMessage>" + escape(providerMessage) + "</providerMessage>"
        + "</nfseAuthorizationResponse>";
    validateWellFormed(xml);
    return xml;
  }

  @Override
  public String buildCancelReturnXml(NfseInvoiceEntity invoice, String providerCode, String providerMessage) {
    String xml = "<nfseCancelResponse>"
        + "<invoiceId>" + escape(invoice.getId() != null ? invoice.getId().toString() : null) + "</invoiceId>"
        + "<status>" + escape(invoice.getFiscalStatus() != null ? invoice.getFiscalStatus().name() : null) + "</status>"
        + "<numeroNfse>" + escape(invoice.getNumeroNfse()) + "</numeroNfse>"
        + "<protocolo>" + escape(invoice.getProtocolo()) + "</protocolo>"
        + "<providerCode>" + escape(providerCode) + "</providerCode>"
        + "<providerMessage>" + escape(providerMessage) + "</providerMessage>"
        + "</nfseCancelResponse>";
    validateWellFormed(xml);
    return xml;
  }

  private String formatAmount(BigDecimal value, int scale) {
    BigDecimal safe = value == null ? BigDecimal.ZERO : value;
    return safe.setScale(scale, RoundingMode.HALF_UP).toPlainString();
  }

  private PrestadorIdentity resolvePrestadorIdentity(NfseInvoiceEntity invoice) {
    if (invoice == null || invoice.getTenantId() == null) {
      throw new IllegalStateException("NFSE_XML_PRESTADOR_TENANT_REQUIRED");
    }
    FiscalTaxConfigEntity tax = fiscalTaxConfigRepository != null
        ? fiscalTaxConfigRepository.findByTenantId(invoice.getTenantId()).orElse(null)
        : null;
    String cnpj = digitsOnly(tax != null ? tax.getIssuerCnpj() : null);
    String im = digitsOnly(tax != null ? tax.getIssuerIm() : null);
    if (cnpj.length() != 14) {
      throw new IllegalArgumentException("NFSE_XML_PRESTADOR_CNPJ_INVALIDO");
    }
    if (im.isBlank()) {
      throw new IllegalArgumentException("NFSE_XML_PRESTADOR_IM_AUSENTE");
    }
    return new PrestadorIdentity(cnpj, im);
  }

  private String resolveOptanteSimplesNacional(NfseInvoiceEntity invoice) {
    if (invoice.getTenantId() == null || nfseConfigRepository == null) return "1";
    NfseConfigEntity cfg =
        nfseConfigRepository.findByTenantAndAmbiente(invoice.getTenantId(), invoice.getAmbiente()).orElse(null);
    if (cfg == null || cfg.getSimplesNacionalSituacao() == null || cfg.getSimplesNacionalSituacao().isBlank()) {
      return "1";
    }
    String s = cfg.getSimplesNacionalSituacao().trim().toUpperCase(Locale.ROOT);
    if (s.equals("2") || s.equals("NAO_OPTANTE") || s.startsWith("NAO")) return "2";
    return "1";
  }

  private String digitsOnly(String value) {
    if (value == null || value.isBlank()) return "";
    return value.replaceAll("\\D", "");
  }

  private record PrestadorIdentity(String cnpj, String inscricaoMunicipal) {}
}
