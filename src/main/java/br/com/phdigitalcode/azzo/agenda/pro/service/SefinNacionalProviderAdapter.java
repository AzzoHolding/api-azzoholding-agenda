package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
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
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Porte verbatim de {@code modules/nfse/application/provider/SefinNacionalProviderAdapter.java}.
 *
 * <p>⚠️ <b>mTLS real</b> — monta um {@link KeyStore} PKCS12 em memoria a partir do {@code
 * KeyMaterial} devolvido por {@link FiscalCertificateService#loadActiveKeyMaterial(String)} (mesmo
 * parsing PKCS12 real ja usado por {@code fiscal}, sem duplicacao), inicializa {@link
 * KeyManagerFactory} + {@link SSLContext} e aplica no {@link HttpClient} usado para falar com
 * {@code sefin.nfse.gov.br}/{@code sefin.producaorestrita.nfse.gov.br}. <b>Nunca substituir isto
 * por HTTP simples</b> — e exatamente o requisito de risco alto que separa esta fronteira das
 * demais de {@code nfse}. O {@link HttpClient} configurado com o {@link SSLContext} e envolvido em
 * {@link JdkClientHttpRequestFactory} para que a chamada HTTP em si passe pelo {@link RestClient}
 * (regra do projeto: nunca {@code WebClient}/reativo), preservando o mTLS de transporte que o
 * {@link HttpClient} negocia.
 *
 * <p>Config keys resolvidas em runtime via {@link Environment} (nao {@code @Value}) porque o
 * original usa {@code ConfigProvider.getConfig().getOptionalValue} com chaves construidas
 * dinamicamente por ambiente/operacao — mesmo padrao aplicado em {@code AbrasfDirectProviderAdapter}.
 */
@Service
public class SefinNacionalProviderAdapter implements NfseProviderAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(SefinNacionalProviderAdapter.class);
  public static final String CODE = "SEFIN_NACIONAL";

  private final EncryptionService encryptionService;
  private final NfseXmlSignerService nfseXmlSignerService;
  private final NfseConfigRepository nfseConfigRepository;
  private final FiscalTaxConfigRepository fiscalTaxConfigRepository;
  private final FiscalCertificateService fiscalCertificateService;
  private final Environment environment;

  public SefinNacionalProviderAdapter(
      EncryptionService encryptionService,
      NfseXmlSignerService nfseXmlSignerService,
      NfseConfigRepository nfseConfigRepository,
      FiscalTaxConfigRepository fiscalTaxConfigRepository,
      FiscalCertificateService fiscalCertificateService,
      Environment environment) {
    this.encryptionService = encryptionService;
    this.nfseXmlSignerService = nfseXmlSignerService;
    this.nfseConfigRepository = nfseConfigRepository;
    this.fiscalTaxConfigRepository = fiscalTaxConfigRepository;
    this.fiscalCertificateService = fiscalCertificateService;
    this.environment = environment;
  }

  @Override
  public String providerCode() {
    return CODE;
  }

  @Override
  public AuthorizationResult authorize(NfseInvoiceEntity invoice, String certificatePassword) {
    if (invoice == null) throw new IllegalArgumentException("NFSE_PROVIDER_MISSING");
    ensureProductionEnabled(invoice);
    String signedXml = decryptSignedXml(invoice);
    String url = resolveOperationUrl(invoice, "authorize", "NFSE_PROVIDER_SEFIN_NACIONAL_AUTHORIZE_URL_MISSING");
    String responseBody = doPostXml(url, signedXml, invoice, "authorize", certificatePassword, true);
    return parseAuthorizationResponse(invoice, responseBody);
  }

  @Override
  public CancellationResult cancel(NfseInvoiceEntity invoice, String reason, String certificatePassword) {
    if (invoice == null) throw new IllegalArgumentException("NFSE_PROVIDER_MISSING");
    ensureProductionEnabled(invoice);
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_CANCEL_REASON_REQUIRED");
    }
    if (certificatePassword == null || certificatePassword.isBlank()) {
      throw new IllegalArgumentException("NFSE_CERTIFICATE_PASSWORD_REQUIRED");
    }
    String url = resolveOperationUrl(invoice, "cancel", "NFSE_PROVIDER_SEFIN_NACIONAL_CANCEL_URL_MISSING");
    String xml = buildCancellationRequestXml(invoice, reason);
    String signedXml = nfseXmlSignerService.sign(xml, certificatePassword);
    String responseBody = doPostXml(url, signedXml, invoice, "cancel", certificatePassword, true);
    return parseCancellationResponse(responseBody);
  }

  @Override
  public Optional<StatusQueryResult> queryStatus(NfseInvoiceEntity invoice) {
    if (invoice == null) return Optional.empty();
    ensureProductionEnabled(invoice);
    String url = resolveOperationUrl(invoice, "query", "NFSE_PROVIDER_SEFIN_NACIONAL_QUERY_URL_MISSING");
    if (isBlank(invoice.getChaveAcessoNfse()) && isBlank(invoice.getProtocolo())) {
      return Optional.empty();
    }
    String responseBody = doQueryRequest(url, invoice);
    return Optional.of(parseQueryResponse(invoice, responseBody));
  }

  String resolveOperationUrl(NfseInvoiceEntity invoice, String operation, String missingCode) {
    String baseUrl = resolveBaseUrl(invoice);
    String pathKey = invoice.getAmbiente() == NfseAmbiente.PRODUCAO
        ? "app.nfse.provider.sefin-nacional." + operation + "-path"
        : "app.nfse.provider.sefin-nacional." + operation + "-path-homologacao";
    String path = environment.getProperty(pathKey, "__unset__");
    if (!isConfigured(path)) {
      throw new IllegalArgumentException(missingCode);
    }
    String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String normalizedPath = expandPathPlaceholders(path.startsWith("/") ? path : "/" + path, invoice);
    return normalizedBase + normalizedPath;
  }

  String resolveBaseUrl(NfseInvoiceEntity invoice) {
    String key = invoice.getAmbiente() == NfseAmbiente.PRODUCAO
        ? "app.nfse.provider.sefin-nacional.base-url"
        : "app.nfse.provider.sefin-nacional.base-url-homologacao";
    String value = environment.getProperty(key, "__unset__");
    if (!isConfigured(value)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_NOT_SUPPORTED");
    }
    return value.trim();
  }

  void ensureProductionEnabled(NfseInvoiceEntity invoice) {
    if (invoice == null || invoice.getAmbiente() != NfseAmbiente.PRODUCAO) return;
    boolean enabled = environment.getProperty(
        "app.nfse.provider.sefin-nacional.production-enabled", Boolean.class, false);
    if (!enabled) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_PRODUCTION_DISABLED");
    }
  }

  private String decryptSignedXml(NfseInvoiceEntity invoice) {
    if (invoice.getXmlEnvioEnc() == null || invoice.getXmlEnvioEnc().length == 0) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_SIGNED_XML_REQUIRED");
    }
    if (encryptionService == null) {
      return new String(invoice.getXmlEnvioEnc(), StandardCharsets.UTF_8);
    }
    String encrypted = new String(invoice.getXmlEnvioEnc(), StandardCharsets.UTF_8);
    return encryptionService.decrypt(encrypted);
  }

  AuthorizationResult parseAuthorizationResponse(NfseInvoiceEntity invoice, String responseBody) {
    String body = responseBody == null ? "" : responseBody.trim();
    String providerStatusCode = firstNonBlank(
        extractFirst(body, "cStat"),
        extractFirst(body, "status"),
        extractFirst(body, "codigo"),
        body.isBlank() ? null : "202");
    String providerStatusMessage = firstNonBlank(
        extractFirst(body, "xMotivo"),
        extractFirst(body, "mensagem"),
        extractFirst(body, "message"),
        "Receita Nacional recebeu a requisicao");
    String numeroNfse = firstNonBlank(
        extractFirst(body, "nNFSe"),
        extractFirst(body, "numero"),
        extractFirst(body, "numeroNfse"));
    String protocolo = firstNonBlank(
        extractFirst(body, "nProt"),
        extractFirst(body, "protocolo"),
        extractFirst(body, "numeroProtocolo"));
    String codigoVerificacao = firstNonBlank(
        extractFirst(body, "cVerif"),
        extractFirst(body, "codigoVerificacao"));
    String chaveAcessoNfse = extractAccessKey(body);

    boolean authorized = containsAuthorizedSignal(body) || numeroNfse != null;
    boolean rejected = containsRejectedSignal(body);

    if (rejected) {
      throw new IllegalStateException("NFSE_PROVIDER_SEFIN_NACIONAL_REJECTED: " + providerStatusMessage);
    }
    if (authorized) {
      return new AuthorizationResult(
          providerStatusCode == null ? "100" : providerStatusCode,
          providerStatusMessage,
          numeroNfse,
          protocolo,
          codigoVerificacao,
          chaveAcessoNfse);
    }
    return new AuthorizationResult(
        providerStatusCode == null ? "102" : providerStatusCode,
        providerStatusMessage,
        null,
        protocolo,
        codigoVerificacao,
        chaveAcessoNfse);
  }

  CancellationResult parseCancellationResponse(String responseBody) {
    String body = responseBody == null ? "" : responseBody.trim();
    String providerStatusCode = firstNonBlank(
        extractFirst(body, "cStat"),
        extractFirst(body, "status"),
        extractFirst(body, "codigo"),
        body.isBlank() ? null : "101");
    String providerStatusMessage = firstNonBlank(
        extractFirst(body, "xMotivo"),
        extractFirst(body, "mensagem"),
        extractFirst(body, "message"),
        "Receita Nacional recebeu o pedido de cancelamento");
    if (containsRejectedSignal(body)) {
      throw new IllegalStateException("NFSE_PROVIDER_SEFIN_NACIONAL_CANCEL_REJECTED: " + providerStatusMessage);
    }
    return new CancellationResult(
        providerStatusCode == null ? "101" : providerStatusCode,
        providerStatusMessage);
  }

  StatusQueryResult parseQueryResponse(NfseInvoiceEntity invoice, String responseBody) {
    String body = responseBody == null ? "" : responseBody.trim();
    String providerStatusCode = firstNonBlank(
        extractFirst(body, "cStat"),
        extractFirst(body, "status"),
        extractFirst(body, "codigo"),
        body.isBlank() ? null : "102");
    String providerStatusMessage = firstNonBlank(
        extractFirst(body, "xMotivo"),
        extractFirst(body, "mensagem"),
        extractFirst(body, "message"),
        "Receita Nacional retornou o status da requisicao");
    String numeroNfse = firstNonBlank(
        extractFirst(body, "nNFSe"),
        extractFirst(body, "numero"),
        extractFirst(body, "numeroNfse"));
    String protocolo = firstNonBlank(
        extractFirst(body, "nProt"),
        extractFirst(body, "protocolo"),
        extractFirst(body, "numeroProtocolo"),
        invoice != null ? invoice.getProtocolo() : null);
    String codigoVerificacao = firstNonBlank(
        extractFirst(body, "cVerif"),
        extractFirst(body, "codigoVerificacao"),
        invoice != null ? invoice.getCodigoVerificacao() : null);
    String chaveAcessoNfse = firstNonBlank(
        extractAccessKey(body),
        invoice != null ? invoice.getChaveAcessoNfse() : null);

    boolean authorized = containsAuthorizedSignal(body) || numeroNfse != null;
    boolean rejected = containsRejectedSignal(body);
    boolean pending = !authorized && !rejected;

    return new StatusQueryResult(
        providerStatusCode == null ? (authorized ? "100" : pending ? "102" : "400") : providerStatusCode,
        providerStatusMessage,
        numeroNfse,
        protocolo,
        codigoVerificacao,
        chaveAcessoNfse,
        authorized,
        rejected,
        pending);
  }

  private String doPostXml(
      String url,
      String xml,
      NfseInvoiceEntity invoice,
      String operation,
      String certificatePassword,
      boolean mtlsEnabled) {
    int connectTimeoutMs = environment.getProperty(
        "app.nfse.provider.sefin-nacional.connect-timeout-ms", Integer.class, 30000);
    int readTimeoutMs = environment.getProperty(
        "app.nfse.provider.sefin-nacional.read-timeout-ms", Integer.class, 60000);
    try {
      RestClient client = buildRestClient(connectTimeoutMs, readTimeoutMs, certificatePassword, mtlsEnabled);
      String body = client
          .post()
          .uri(URI.create(url))
          .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8")
          .header(HttpHeaders.ACCEPT, "application/xml, text/xml, application/json, text/plain")
          .body(xml)
          .exchange((request, response) -> {
            LOG.info(
                "nfse_sefin_nacional_http tenant_id={} invoice_id={} ambiente={} municipio={} http_status={} operation={}",
                invoice.getTenantId(), invoice.getId(), invoice.getAmbiente(),
                invoice.getMunicipioCodigoIbge(), response.getStatusCode().value(), operation);
            String responseBody = readBody(response);
            int status = response.getStatusCode().value();
            if (status < 200 || status >= 300) {
              throw new IllegalStateException("NFSE_PROVIDER_SEFIN_NACIONAL_HTTP_ERROR_" + status);
            }
            return responseBody;
          });
      return body;
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("NFSE_PROVIDER_SEFIN_NACIONAL_COMMUNICATION_ERROR", ex);
    }
  }

  private String doQueryRequest(String url, NfseInvoiceEntity invoice) {
    String method = environment
        .getProperty("app.nfse.provider.sefin-nacional.query-http-method", "GET")
        .trim()
        .toUpperCase();
    if (!"GET".equals(method)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_QUERY_METHOD_NOT_SUPPORTED");
    }
    String accessKeyParam = environment.getProperty(
        "app.nfse.provider.sefin-nacional.query-access-key-param", "chNFSe");
    String protocolParam = environment.getProperty(
        "app.nfse.provider.sefin-nacional.query-protocol-param", "protocolo");
    boolean includeProtocol = environment.getProperty(
        "app.nfse.provider.sefin-nacional.query-include-protocol", Boolean.class, true);

    boolean pathHasAccessKey = !isBlank(invoice.getChaveAcessoNfse())
        && url.contains(encode(invoice.getChaveAcessoNfse().trim()));
    boolean pathHasProtocol = !isBlank(invoice.getProtocolo())
        && url.contains(encode(invoice.getProtocolo().trim()));

    StringBuilder builder = new StringBuilder(url);
    boolean hasQuery = url.contains("?");
    if (!pathHasAccessKey && !isBlank(invoice.getChaveAcessoNfse())) {
      builder.append(hasQuery ? '&' : '?')
          .append(encode(accessKeyParam)).append('=').append(encode(invoice.getChaveAcessoNfse().trim()));
      hasQuery = true;
    }
    if (includeProtocol && !pathHasProtocol && !isBlank(invoice.getProtocolo())) {
      builder.append(hasQuery ? '&' : '?')
          .append(encode(protocolParam)).append('=').append(encode(invoice.getProtocolo().trim()));
    }
    String finalUrl = builder.toString();
    boolean queryMtlsEnabled = environment.getProperty(
        "app.nfse.provider.sefin-nacional.query-mtls-enabled", Boolean.class, false);
    return doGet(finalUrl, invoice, "query", null, queryMtlsEnabled);
  }

  private String doGet(String url, NfseInvoiceEntity invoice, String operation, String certificatePassword, boolean mtlsEnabled) {
    int connectTimeoutMs = environment.getProperty(
        "app.nfse.provider.sefin-nacional.connect-timeout-ms", Integer.class, 30000);
    int readTimeoutMs = environment.getProperty(
        "app.nfse.provider.sefin-nacional.read-timeout-ms", Integer.class, 60000);
    try {
      RestClient client = buildRestClient(connectTimeoutMs, readTimeoutMs, certificatePassword, mtlsEnabled);
      String body = client
          .get()
          .uri(URI.create(url))
          .header(HttpHeaders.ACCEPT, "application/xml, text/xml, application/json, text/plain")
          .exchange((request, response) -> {
            LOG.info(
                "nfse_sefin_nacional_http tenant_id={} invoice_id={} ambiente={} municipio={} http_status={} operation={}",
                invoice.getTenantId(), invoice.getId(), invoice.getAmbiente(),
                invoice.getMunicipioCodigoIbge(), response.getStatusCode().value(), operation);
            String responseBody = readBody(response);
            int status = response.getStatusCode().value();
            if (status < 200 || status >= 300) {
              throw new IllegalStateException("NFSE_PROVIDER_SEFIN_NACIONAL_HTTP_ERROR_" + status);
            }
            return responseBody;
          });
      return body;
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("NFSE_PROVIDER_SEFIN_NACIONAL_COMMUNICATION_ERROR", ex);
    }
  }

  private String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
    try (var input = response.getBody()) {
      byte[] bytes = input == null ? new byte[0] : input.readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("NFSE_PROVIDER_SEFIN_NACIONAL_COMMUNICATION_ERROR", ex);
    }
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

  private String extractFirstAttribute(String xml, String tag, String attributeName) {
    if (xml == null || xml.isBlank()) return null;
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      var nodes = doc.getElementsByTagNameNS("*", tag);
      if (nodes == null || nodes.getLength() == 0 || !(nodes.item(0) instanceof Element element)) return null;
      String value = element.getAttribute(attributeName);
      return value == null || value.isBlank() ? null : value.trim();
    } catch (Exception ex) {
      return null;
    }
  }

  private String extractAccessKey(String xml) {
    String direct = extractFirst(xml, "chNFSe");
    if (direct != null && !direct.isBlank()) return direct;
    String infNfseId = extractFirstAttribute(xml, "infNFSe", "Id");
    if (infNfseId != null && infNfseId.startsWith("NFS") && infNfseId.length() == 53) {
      return infNfseId.substring(3);
    }
    return null;
  }

  private String buildCancellationRequestXml(NfseInvoiceEntity invoice, String reason) {
    if (isBlank(invoice.getChaveAcessoNfse())) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_ACCESS_KEY_MISSING");
    }
    NfseConfigEntity config = nfseConfigRepository.findByTenantAndAmbiente(invoice.getTenantId(), invoice.getAmbiente())
        .orElseThrow(() -> new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_CONFIG_MISSING"));
    var taxConfig = fiscalTaxConfigRepository.findByTenantId(invoice.getTenantId())
        .orElseThrow(() -> new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_TAX_CONFIG_MISSING"));
    String applicationVersion = requireConfigured(config.getApplicationVersion(), "NFSE_PROVIDER_SEFIN_NACIONAL_APP_VERSION_REQUIRED");
    String authorDocument = digitsOnly(taxConfig.getIssuerCnpj());
    if (authorDocument.length() != 14) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_PRESTADOR_CNPJ_REQUIRED");
    }
    String chave = digitsOnly(invoice.getChaveAcessoNfse());
    if (chave.length() != 50) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_ACCESS_KEY_MISSING");
    }
    String eventCode = "101101";
    String eventSeq = "001";
    String eventId = "PRE" + chave + eventCode + eventSeq;
    String dhEvento = OffsetDateTime.now(ZoneOffset.of("-03:00")).withNano(0).toString();
    return """
        <pedRegEvento xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.00">
          <infPedReg Id="%s">
            <tpAmb>%s</tpAmb>
            <verAplic>%s</verAplic>
            <dhEvento>%s</dhEvento>
            <CNPJAutor>%s</CNPJAutor>
            <chNFSe>%s</chNFSe>
            <nPedRegEvento>%s</nPedRegEvento>
            <e101101>
              <xDesc>Cancelamento de NFS-e</xDesc>
              <cMotivo>%s</cMotivo>
              <xMotivo>%s</xMotivo>
            </e101101>
          </infPedReg>
        </pedRegEvento>
        """.formatted(
            escape(eventId),
            toAmbienteCode(invoice.getAmbiente()),
            escape(applicationVersion),
            escape(dhEvento),
            escape(authorDocument),
            escape(chave),
            eventSeq,
            resolveCancelReasonCode(reason),
            escape(limit(reason, 255)));
  }

  private String toAmbienteCode(NfseAmbiente ambiente) {
    return ambiente == NfseAmbiente.PRODUCAO ? "1" : "2";
  }

  private String resolveCancelReasonCode(String reason) {
    String normalized = reason == null ? "" : reason.trim().toLowerCase();
    if (normalized.contains("erro")) return "1";
    if (normalized.contains("nao prest") || normalized.contains("não prest")) return "2";
    String configured = environment.getProperty("app.nfse.provider.sefin-nacional.cancel-reason-code-default");
    return isConfigured(configured) ? configured : "9";
  }

  private String requireConfigured(String value, String code) {
    if (!isConfigured(value)) throw new IllegalArgumentException(code);
    return value.trim();
  }

  private String digitsOnly(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
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

  private String limit(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }

  private String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  /**
   * Monta o {@link HttpClient} (com ou sem mTLS) e o envolve num {@link RestClient} via {@link
   * JdkClientHttpRequestFactory} — a negociacao TLS/handshake com certificado de cliente acontece
   * dentro do {@link HttpClient}, o {@link RestClient} so orquestra a requisicao por cima.
   */
  RestClient buildRestClient(int connectTimeoutMs, int readTimeoutMs, String certificatePassword, boolean mtlsEnabled) throws Exception {
    HttpClient httpClient = buildHttpClient(connectTimeoutMs, certificatePassword, mtlsEnabled);
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(Math.max(readTimeoutMs, 1000)));
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  HttpClient buildHttpClient(int connectTimeoutMs, String certificatePassword, boolean mtlsEnabled) throws Exception {
    HttpClient.Builder builder = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(Math.max(connectTimeoutMs, 1000)));
    if (!mtlsEnabled) {
      return builder.build();
    }
    if (certificatePassword == null || certificatePassword.isBlank()) {
      throw new IllegalArgumentException("NFSE_CERTIFICATE_PASSWORD_REQUIRED");
    }
    FiscalCertificateService.KeyMaterial keyMaterial = fiscalCertificateService.loadActiveKeyMaterial(certificatePassword);
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, null);
    char[] password = certificatePassword.toCharArray();
    keyStore.setKeyEntry(
        "nfse-client",
        keyMaterial.privateKey(),
        password,
        new java.security.cert.Certificate[] { keyMaterial.certificate() });
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, password);
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), null, null);
    builder.sslContext(sslContext);
    return builder.build();
  }

  private String expandPathPlaceholders(String path, NfseInvoiceEntity invoice) {
    String result = path;
    result = result.replace("{chaveAcesso}", safePathValue(invoice != null ? invoice.getChaveAcessoNfse() : null));
    result = result.replace("{chave_acesso}", safePathValue(invoice != null ? invoice.getChaveAcessoNfse() : null));
    result = result.replace("{accessKey}", safePathValue(invoice != null ? invoice.getChaveAcessoNfse() : null));
    result = result.replace("{protocolo}", safePathValue(invoice != null ? invoice.getProtocolo() : null));
    result = result.replace("{protocol}", safePathValue(invoice != null ? invoice.getProtocolo() : null));
    result = result.replace("{numeroNfse}", safePathValue(invoice != null ? invoice.getNumeroNfse() : null));
    result = result.replace("{id}", safePathValue(invoice != null ? invoice.getId() != null ? invoice.getId().toString() : null : null));
    return result;
  }

  private String safePathValue(String value) {
    return encode(value == null ? "" : value.trim());
  }

  private boolean containsAuthorizedSignal(String responseBody) {
    String normalized = responseBody == null ? "" : responseBody.toUpperCase();
    return normalized.contains("AUTORIZ")
        || normalized.contains("<NNFSE>")
        || normalized.contains("<NUMERONFSE>");
  }

  private boolean containsRejectedSignal(String responseBody) {
    String normalized = responseBody == null ? "" : responseBody.toUpperCase();
    return normalized.contains("REJEIT")
        || normalized.contains("ERRO")
        || normalized.contains("INVALID");
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return null;
  }

  private boolean isConfigured(String value) {
    return value != null && !value.isBlank() && !"__unset__".equalsIgnoreCase(value.trim());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
