package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Porte verbatim de {@code modules/nfse/application/provider/AbrasfDirectProviderAdapter.java}.
 *
 * <p><b>SOAP puro, sem mTLS de transporte</b> — diferente de {@link SefinNacionalProviderAdapter},
 * a autenticacao aqui e so via XML assinado dentro do envelope SOAP (concatenacao de string, sem
 * biblioteca SOAP dedicada, mesmo padrao do original). Fala com webservices municipais diferentes
 * por prefeitura, resolvidos por 3 fallbacks encadeados (DB tenant &gt; config por municipio &gt;
 * config global) — assimetria deliberada com {@code SefinNacionalProviderAdapter} (URL fixa por
 * ambiente, sem override por tenant), preservada do original, nao "corrigida" para simetria.
 *
 * <p>Chamada HTTP via {@link RestClient} (regra do projeto: nunca {@code WebClient}/reativo),
 * envolvendo um {@link HttpClient} plano sem {@code SSLContext} customizado.
 */
@Service
public class AbrasfDirectProviderAdapter implements NfseProviderAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(AbrasfDirectProviderAdapter.class);
  public static final String CODE = "ABRASF";

  private final EncryptionService encryptionService;
  private final FiscalTaxConfigRepository fiscalTaxConfigRepository;
  private final NfseConfigRepository nfseConfigRepository;
  private final Environment environment;

  public AbrasfDirectProviderAdapter(
      EncryptionService encryptionService,
      FiscalTaxConfigRepository fiscalTaxConfigRepository,
      NfseConfigRepository nfseConfigRepository,
      Environment environment) {
    this.encryptionService = encryptionService;
    this.fiscalTaxConfigRepository = fiscalTaxConfigRepository;
    this.nfseConfigRepository = nfseConfigRepository;
    this.environment = environment;
  }

  @Override
  public String providerCode() {
    return CODE;
  }

  @Override
  public AuthorizationResult authorize(NfseInvoiceEntity invoice, String certificatePassword) {
    if (invoice == null) throw new IllegalArgumentException("NFSE_PROVIDER_ABRASF_INVOICE_REQUIRED");
    String signedXml = decryptXmlEnvio(invoice);
    String wsUrl = resolveWsUrl(invoice);
    String envelope = wrapSoapEnvelope("RecepcionarLoteRps", signedXml);

    String responseBody = postSoap(wsUrl, "RecepcionarLoteRps", envelope, invoice);
    if (containsRejection(responseBody)) {
      String message = extractFirst(responseBody, "Mensagem");
      if (message == null) message = extractFirst(responseBody, "MensagemRetorno");
      if (message == null) message = "Rejeicao do provedor ABRASF";
      throw new IllegalStateException("NFSE_PROVIDER_ABRASF_REJECTED: " + message);
    }

    String numeroNfse = firstNonBlank(
        extractFirst(responseBody, "NumeroNfse"),
        extractFirst(responseBody, "Numero"));
    String protocolo = firstNonBlank(
        extractFirst(responseBody, "Protocolo"),
        extractFirst(responseBody, "NumeroLote"));
    String codigoVerificacao = firstNonBlank(
        extractFirst(responseBody, "CodigoVerificacao"),
        extractFirst(responseBody, "CodigoVerificacaoNfse"),
        null);

    if (numeroNfse == null || numeroNfse.isBlank()) {
      if (protocolo == null || protocolo.isBlank()) {
        throw new IllegalStateException("NFSE_PROVIDER_ABRASF_PROTOCOL_MISSING");
      }
      return new AuthorizationResult(
          "102",
          "Lote recebido para processamento no provedor ABRASF",
          null,
          protocolo,
          codigoVerificacao,
          null);
    }

    return new AuthorizationResult("100", "Autorizado via ABRASF", numeroNfse, protocolo, codigoVerificacao, null);
  }

  @Override
  public CancellationResult cancel(NfseInvoiceEntity invoice, String reason, String certificatePassword) {
    if (invoice == null) throw new IllegalArgumentException("NFSE_PROVIDER_ABRASF_INVOICE_REQUIRED");
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("NFSE_PROVIDER_ABRASF_CANCEL_REASON_REQUIRED");

    String wsUrl = resolveWsUrl(invoice);
    String cancelXml = "<CancelarNfseEnvio>"
        + "<Pedido><InfPedidoCancelamento Id=\"CAN" + invoice.getId() + "\">"
        + "<IdentificacaoNfse><Numero>" + safe(invoice.getNumeroNfse()) + "</Numero>"
        + "<CodigoMunicipio>" + safe(invoice.getMunicipioCodigoIbge()) + "</CodigoMunicipio>"
        + "<CodigoVerificacao>" + safe(invoice.getCodigoVerificacao()) + "</CodigoVerificacao>"
        + "</IdentificacaoNfse>"
        + "<CodigoCancelamento>1</CodigoCancelamento>"
        + "<MotivoCancelamento>" + escape(reason.trim()) + "</MotivoCancelamento>"
        + "</InfPedidoCancelamento></Pedido>"
        + "</CancelarNfseEnvio>";
    String envelope = wrapSoapEnvelope("CancelarNfse", cancelXml);
    String responseBody = postSoap(wsUrl, "CancelarNfse", envelope, invoice);
    if (containsRejection(responseBody)) {
      String message = extractFirst(responseBody, "Mensagem");
      if (message == null) message = extractFirst(responseBody, "MensagemRetorno");
      if (message == null) message = "Cancelamento rejeitado pelo provedor ABRASF";
      throw new IllegalStateException("NFSE_PROVIDER_ABRASF_CANCEL_REJECTED: " + message);
    }
    return new CancellationResult("101", "Cancelado via ABRASF");
  }

  @Override
  public Optional<StatusQueryResult> queryStatus(NfseInvoiceEntity invoice) {
    if (invoice == null) return Optional.empty();
    if (invoice.getProtocolo() == null || invoice.getProtocolo().isBlank()) return Optional.empty();
    PrestadorIdentity prestador = resolvePrestadorIdentity(invoice);

    String wsUrl = resolveWsUrl(invoice);
    String consultaXml = "<ConsultarLoteRpsEnvio>"
        + "<Prestador><CpfCnpj><Cnpj>" + prestador.cnpj + "</Cnpj></CpfCnpj><InscricaoMunicipal>" + prestador.inscricaoMunicipal + "</InscricaoMunicipal></Prestador>"
        + "<Protocolo>" + safe(invoice.getProtocolo()) + "</Protocolo>"
        + "</ConsultarLoteRpsEnvio>";

    String envelope = wrapSoapEnvelope("ConsultarLoteRps", consultaXml);
    String responseBody = postSoap(wsUrl, "ConsultarLoteRps", envelope, invoice);

    if (containsRejection(responseBody)) {
      String msg = firstNonBlank(
          extractFirst(responseBody, "Mensagem"),
          extractFirst(responseBody, "MensagemRetorno"),
          "Rejeicao no processamento ABRASF");
      return Optional.of(new StatusQueryResult(
          "400",
          msg,
          null,
          invoice.getProtocolo(),
          null,
          null,
          false,
          true,
          false));
    }

    String numeroNfse = firstNonBlank(
        extractFirst(responseBody, "NumeroNfse"),
        extractFirst(responseBody, "Numero"));
    String codigoVerificacao = firstNonBlank(
        extractFirst(responseBody, "CodigoVerificacao"),
        extractFirst(responseBody, "CodigoVerificacaoNfse"));
    String protocolo = firstNonBlank(
        extractFirst(responseBody, "Protocolo"),
        invoice.getProtocolo());

    if (numeroNfse != null && !numeroNfse.isBlank()) {
      return Optional.of(new StatusQueryResult(
          "100",
          "Autorizado via consulta ABRASF",
          numeroNfse,
          protocolo,
          codigoVerificacao,
          null,
          true,
          false,
          false));
    }

    return Optional.of(new StatusQueryResult(
        "102",
        "Ainda em processamento no provedor ABRASF",
        null,
        protocolo,
        null,
        null,
        false,
        false,
        true));
  }

  private String decryptXmlEnvio(NfseInvoiceEntity invoice) {
    if (invoice.getXmlEnvioEnc() == null || invoice.getXmlEnvioEnc().length == 0) {
      throw new IllegalStateException("NFSE_PROVIDER_ABRASF_SIGNED_XML_REQUIRED");
    }
    String encrypted = new String(invoice.getXmlEnvioEnc(), StandardCharsets.UTF_8);
    return encryptionService.decrypt(encrypted);
  }

  String resolveWsUrl(NfseInvoiceEntity invoice) {
    // 1. DB config takes priority so each tenant can configure via UI
    if (invoice.getTenantId() != null && nfseConfigRepository != null) {
      NfseConfigEntity cfg = nfseConfigRepository.findByTenantAndAmbiente(invoice.getTenantId(), invoice.getAmbiente()).orElse(null);
      if (cfg != null) {
        String dbUrl = invoice.getAmbiente() == NfseAmbiente.PRODUCAO ? cfg.getWsUrl() : cfg.getWsUrlHomologacao();
        if (isConfigured(dbUrl)) return dbUrl.trim();
      }
    }

    // 2. Per-municipality config property fallback
    String municipio = invoice.getMunicipioCodigoIbge() == null ? "" : invoice.getMunicipioCodigoIbge().trim();
    String envKey = invoice.getAmbiente() == NfseAmbiente.PRODUCAO ? "ws-url" : "ws-url-homologacao";
    String dynamicKey = "app.nfse.provider.abrasf." + municipio + "." + envKey;
    String dynamic = environment.getProperty(dynamicKey);
    if (isConfigured(dynamic)) return dynamic.trim();

    // 3. Global default fallback
    String fallbackKey = invoice.getAmbiente() == NfseAmbiente.PRODUCAO
        ? "app.nfse.provider.abrasf.default.ws-url"
        : "app.nfse.provider.abrasf.default.ws-url-homologacao";
    String fallback = environment.getProperty(fallbackKey);
    if (isConfigured(fallback)) return fallback.trim();

    throw new IllegalStateException("NFSE_PROVIDER_ABRASF_WS_URL_MISSING");
  }

  private String postSoap(String wsUrl, String soapAction, String envelope, NfseInvoiceEntity invoice) {
    int connectTimeoutMs = environment.getProperty("app.nfse.provider.abrasf.connect-timeout-ms", Integer.class, 30000);
    int readTimeoutMs = environment.getProperty("app.nfse.provider.abrasf.read-timeout-ms", Integer.class, 60000);

    try {
      RestClient client = buildRestClient(connectTimeoutMs, readTimeoutMs);
      String body = client
          .post()
          .uri(URI.create(wsUrl))
          .header(HttpHeaders.CONTENT_TYPE, "text/xml; charset=utf-8")
          .header("SOAPAction", soapAction)
          .body(envelope)
          .exchange((request, response) -> {
            int status = response.getStatusCode().value();
            LOG.info(
                "nfse_abrasf_http tenant_id={} invoice_id={} ambiente={} municipio={} http_status={} action={}",
                invoice.getTenantId(), invoice.getId(), invoice.getAmbiente(),
                invoice.getMunicipioCodigoIbge(), status, soapAction);
            String responseBody = readBody(response);
            if (status < 200 || status >= 300) {
              throw new IllegalStateException("NFSE_PROVIDER_ABRASF_HTTP_ERROR_" + status);
            }
            return responseBody;
          });
      return body;
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("NFSE_PROVIDER_ABRASF_COMMUNICATION_ERROR", ex);
    }
  }

  private String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
    try (var input = response.getBody()) {
      byte[] bytes = input == null ? new byte[0] : input.readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("NFSE_PROVIDER_ABRASF_COMMUNICATION_ERROR", ex);
    }
  }

  RestClient buildRestClient(int connectTimeoutMs, int readTimeoutMs) {
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(Math.max(connectTimeoutMs, 1000)))
        .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(Math.max(readTimeoutMs, 1000)));
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  private String wrapSoapEnvelope(String operation, String xmlPayload) {
    // ABRASF 2.04: operation element wraps <nfseDadosMsg> with the signed XML inline (no CDATA).
    // CDATA would deliver text instead of XML nodes, causing SOAP parse failures at the prefeitura.
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soapenv:Header/>"
        + "<soapenv:Body>"
        + "<" + operation + ">"
        + "<nfseDadosMsg>"
        + xmlPayload
        + "</nfseDadosMsg>"
        + "</" + operation + ">"
        + "</soapenv:Body>"
        + "</soapenv:Envelope>";
  }

  private boolean containsRejection(String xml) {
    if (xml == null || xml.isBlank()) return true;
    String upper = xml.toUpperCase(Locale.ROOT);
    // SOAP Fault is always an error regardless of content
    if (upper.contains("SOAP:FAULT") || upper.contains("SOAPENV:FAULT")
        || upper.contains(":FAULT>") || upper.contains("<FAULT>")) return true;
    // If no error block present, response is success or pending
    if (!upper.contains("LISTAMENSAGEMRETORNO")) return false;
    // Error block present but result was also included — warning only, not an error
    if (upper.contains("NUMERONFSE") || upper.contains("NUMEROPROTOCOLO")
        || upper.contains("NUMEROLOTE") || upper.contains("<NFSE>")) return false;
    // Error block with no usable result — real rejection
    return true;
  }

  private String extractFirst(String xml, String tag) {
    if (xml == null || xml.isBlank()) return null;
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      var nodes = doc.getElementsByTagNameNS("*", tag);
      if (nodes == null || nodes.getLength() == 0) return null;
      String text = nodes.item(0).getTextContent();
      return text == null || text.isBlank() ? null : text.trim();
    } catch (Exception ex) {
      return null;
    }
  }

  private String safe(String value) {
    return value == null ? "" : escape(value);
  }

  private String escape(String value) {
    if (value == null || value.isBlank()) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private boolean isConfigured(String value) {
    return value != null && !value.isBlank() && !"__unset__".equalsIgnoreCase(value.trim());
  }

  private PrestadorIdentity resolvePrestadorIdentity(NfseInvoiceEntity invoice) {
    if (invoice == null || invoice.getTenantId() == null || fiscalTaxConfigRepository == null) {
      throw new IllegalStateException("NFSE_PRESTADOR_FISCAL_CONFIG_REQUIRED");
    }
    FiscalTaxConfigEntity tax = fiscalTaxConfigRepository.findByTenantId(invoice.getTenantId()).orElse(null);
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

  private String digitsOnly(String value) {
    if (value == null || value.isBlank()) return "";
    return value.replaceAll("\\D", "");
  }

  private record PrestadorIdentity(String cnpj, String inscricaoMunicipal) {}
}
