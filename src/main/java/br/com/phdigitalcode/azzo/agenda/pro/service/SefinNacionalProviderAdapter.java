package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.exception.NfseProvedorRecusouException;
import br.com.phdigitalcode.azzo.agenda.pro.exception.NfseProvedorSemRespostaException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Cliente da API do Emissor Nacional da NFS-e (Sefin Nacional).
 *
 * <p><b>Contrato da API</b> (corrigido em 06/11/2026; antes o sistema mandava o XML puro e lia a
 * resposta procurando palavras soltas, o que a Receita recusaria em todo envio):
 *
 * <ul>
 *   <li>Emissao: {@code POST /nfse} com JSON {@code {"dpsXmlGZipB64": "..."}} — a DPS assinada,
 *       compactada em GZip e codificada em Base64. Sucesso (2xx) devolve {@code chaveAcesso},
 *       {@code idDps} e {@code nfseXmlGZipB64} (a NFS-e gerada, de onde sai o {@code nNFSe}).
 *   <li>Cancelamento: {@code POST /nfse/{chaveAcesso}/eventos} com JSON {@code
 *       {"pedidoRegistroEventoXmlGZipB64": "..."}}.
 *   <li>Consulta de um envio: {@code GET /dps/{idDps}} devolve a {@code chaveAcesso} da nota gerada
 *       por aquela DPS; 404 quer dizer que a DPS nao foi recebida. {@code GET /nfse/{chaveAcesso}}
 *       devolve a nota.
 *   <li>Recusa: HTTP 4xx com uma lista {@code erros} de {@code {Codigo, Descricao, Complemento}}. O
 *       motivo vai para o evento da nota ({@link NfseProvedorRecusouException}).
 *   <li>Sem resposta confiavel (rede, tempo esgotado, 5xx, 408, 429): {@link
 *       NfseProvedorSemRespostaException}. O resultado e incerto; o servico consulta a DPS antes de
 *       reenviar.
 * </ul>
 *
 * <p><b>Todas as chamadas usam mTLS</b> com o certificado A1 do salao, inclusive as consultas. Por
 * isso nao ha consulta automatica em segundo plano ({@link #queryStatus} fica vazio): o polling nao
 * tem a senha do certificado. A emissao e sincrona, e o caso sem resposta e resolvido quando o
 * usuario emite de novo (com a senha), via {@link #consultarEmissao}.
 *
 * <p>⚠️ <b>mTLS real</b> — monta um {@link KeyStore} PKCS12 em memoria a partir do {@code
 * KeyMaterial} de {@link FiscalCertificateService#loadActiveKeyMaterial(String)}. Nunca substituir
 * por HTTP simples.
 */
@Service
public class SefinNacionalProviderAdapter implements NfseProviderAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(SefinNacionalProviderAdapter.class);
  public static final String CODE = "SEFIN_NACIONAL";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int LIMITE_DA_MENSAGEM = 1000;

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

  /** Resposta HTTP crua: o status e o corpo, mesmo quando o status nao e 2xx. */
  record RespostaHttp(int status, String body) {
    boolean sucesso() {
      return status >= 200 && status < 300;
    }
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
    String corpo = jsonComXmlCompactado("dpsXmlGZipB64", signedXml);
    RespostaHttp resposta = doPostJson(url, corpo, invoice, "authorize", certificatePassword);
    return parseAuthorizationResponse(resposta);
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
    String corpo = jsonComXmlCompactado("pedidoRegistroEventoXmlGZipB64", signedXml);
    RespostaHttp resposta = doPostJson(url, corpo, invoice, "cancel", certificatePassword);
    return parseCancellationResponse(resposta);
  }

  /**
   * Sem consulta automatica: toda chamada a Receita exige o certificado do salao (mTLS), e o
   * polling roda sem a senha. Ver {@link #consultarEmissao}.
   */
  @Override
  public Optional<StatusQueryResult> queryStatus(NfseInvoiceEntity invoice) {
    return Optional.empty();
  }

  @Override
  public boolean consultaEmissaoAposFalha() {
    return true;
  }

  @Override
  public Optional<AuthorizationResult> consultarEmissao(NfseInvoiceEntity invoice, String certificatePassword) {
    if (invoice == null) throw new IllegalArgumentException("NFSE_PROVIDER_MISSING");
    ensureProductionEnabled(invoice);
    String idDps = extractFirstAttribute(decryptSignedXml(invoice), "infDPS", "Id");
    if (isBlank(idDps)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_SIGNED_XML_REQUIRED");
    }
    String urlDps = resolveOperationUrl(invoice, "dps", "NFSE_PROVIDER_SEFIN_NACIONAL_QUERY_URL_MISSING")
        .replace("{idDps}", encode(idDps));
    RespostaHttp resposta = doGet(urlDps, invoice, "dps", certificatePassword);
    if (resposta.status() == 404) return Optional.empty();
    if (!resposta.sucesso()) throw erroDaResposta(resposta);

    String chave = chaveOrNull(texto(lerJson(resposta.body()), "chaveAcesso"));
    if (chave == null) return Optional.empty();

    String numero = null;
    NfseInvoiceEntity comChave = new NfseInvoiceEntity();
    comChave.setChaveAcessoNfse(chave);
    String urlNota = expandPathPlaceholders(
        resolveBaseUrl(invoice) + resolvePath(invoice, "query", "NFSE_PROVIDER_SEFIN_NACIONAL_QUERY_URL_MISSING"),
        comChave);
    try {
      RespostaHttp nota = doGet(urlNota, invoice, "query", certificatePassword);
      if (nota.sucesso()) {
        numero = extractFirst(descompactar(texto(lerJson(nota.body()), "nfseXmlGZipB64")), "nNFSe");
      }
    } catch (NfseProvedorSemRespostaException ex) {
      // A chave ja prova que a nota existe; o numero fica para depois, nao vale perder a nota.
      LOG.warn("nfse_sefin_nacional_numero_indisponivel invoice_id={}", invoice.getId(), ex);
    }
    return Optional.of(new AuthorizationResult("100", "NFS-e encontrada na Receita Nacional", numero, idDps, null, chave));
  }

  String resolveOperationUrl(NfseInvoiceEntity invoice, String operation, String missingCode) {
    return expandPathPlaceholders(resolveBaseUrl(invoice) + resolvePath(invoice, operation, missingCode), invoice);
  }

  private String resolvePath(NfseInvoiceEntity invoice, String operation, String missingCode) {
    String pathKey = invoice.getAmbiente() == NfseAmbiente.PRODUCAO
        ? "app.nfse.provider.sefin-nacional." + operation + "-path"
        : "app.nfse.provider.sefin-nacional." + operation + "-path-homologacao";
    String path = environment.getProperty(pathKey, "__unset__");
    if (!isConfigured(path)) {
      throw new IllegalArgumentException(missingCode);
    }
    return path.trim();
  }

  String resolveBaseUrl(NfseInvoiceEntity invoice) {
    String key = invoice.getAmbiente() == NfseAmbiente.PRODUCAO
        ? "app.nfse.provider.sefin-nacional.base-url"
        : "app.nfse.provider.sefin-nacional.base-url-homologacao";
    String value = environment.getProperty(key, "__unset__");
    if (!isConfigured(value)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_NOT_SUPPORTED");
    }
    String trimmed = value.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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

  // ─── leitura das respostas ────────────────────────────────────────────────

  AuthorizationResult parseAuthorizationResponse(RespostaHttp resposta) {
    if (!resposta.sucesso()) throw erroDaResposta(resposta);
    JsonNode json = lerJson(resposta.body());
    if (json == null) {
      // 2xx sem JSON legivel: nao da para afirmar que a nota existe nem que nao existe.
      throw new NfseProvedorSemRespostaException(
          String.valueOf(resposta.status()), "A Receita Nacional respondeu sem os dados da nota.");
    }
    String nfseXml = descompactar(texto(json, "nfseXmlGZipB64"));
    String chave = firstNonBlank(chaveOrNull(texto(json, "chaveAcesso")), extractAccessKey(nfseXml));
    if (chave == null) {
      throw new NfseProvedorSemRespostaException(
          String.valueOf(resposta.status()), "A Receita Nacional respondeu sem a chave de acesso da nota.");
    }
    String numero = extractFirst(nfseXml, "nNFSe");
    String idDps = texto(json, "idDps");
    String mensagem = firstNonBlank(juntarMensagens(json, "alertas"), "NFS-e autorizada pela Receita Nacional");
    return new AuthorizationResult("100", limit(mensagem, LIMITE_DA_MENSAGEM), numero, idDps, null, chave);
  }

  CancellationResult parseCancellationResponse(RespostaHttp resposta) {
    if (!resposta.sucesso()) throw erroDaResposta(resposta);
    return new CancellationResult(String.valueOf(resposta.status()), "Cancelamento registrado pela Receita Nacional");
  }

  /**
   * Transforma uma resposta nao 2xx no erro certo. 5xx, 408 e 429 sao "sem resposta" (o pedido
   * pode ter sido processado); o resto do 4xx e recusa, com os erros que a Receita mandou.
   */
  RuntimeException erroDaResposta(RespostaHttp resposta) {
    int status = resposta.status();
    String codigoHttp = String.valueOf(status);
    if (status >= 500 || status == 408 || status == 429) {
      return new NfseProvedorSemRespostaException(
          codigoHttp, "A Receita Nacional nao respondeu (HTTP " + status + ").");
    }
    JsonNode json = lerJson(resposta.body());
    String codigo = firstNonBlank(primeiroCodigo(json, "erros"), primeiroCodigo(json, "erro"), codigoHttp);
    String mensagem = firstNonBlank(
        juntarMensagens(json, "erros"),
        juntarMensagens(json, "erro"),
        texto(json, "mensagem"),
        texto(json, "message"),
        status == 401 || status == 403
            ? "A Receita Nacional nao aceitou o certificado digital (HTTP " + status + ")."
            : null,
        isBlank(resposta.body()) ? null : "HTTP " + status + ": " + resposta.body().trim(),
        "HTTP " + status + " sem detalhe.");
    return new NfseProvedorRecusouException(limit(codigo, 30), limit(mensagem, LIMITE_DA_MENSAGEM));
  }

  /** Junta {@code Codigo - Descricao (Complemento)} de cada item de uma lista de erros/alertas. */
  private String juntarMensagens(JsonNode json, String campo) {
    List<String> partes = new ArrayList<>();
    for (JsonNode item : itens(json, campo)) {
      if (item.isTextual()) {
        partes.add(item.asText().trim());
        continue;
      }
      String codigo = texto(item, "codigo");
      String descricao = firstNonBlank(texto(item, "descricao"), texto(item, "mensagem"));
      String complemento = texto(item, "complemento");
      StringBuilder parte = new StringBuilder();
      if (codigo != null) parte.append(codigo);
      if (descricao != null) parte.append(parte.isEmpty() ? "" : " - ").append(descricao);
      if (complemento != null) parte.append(" (").append(complemento).append(")");
      if (!parte.isEmpty()) partes.add(parte.toString());
    }
    return partes.isEmpty() ? null : String.join("; ", partes);
  }

  private String primeiroCodigo(JsonNode json, String campo) {
    for (JsonNode item : itens(json, campo)) {
      String codigo = texto(item, "codigo");
      if (codigo != null) return codigo;
    }
    return null;
  }

  private List<JsonNode> itens(JsonNode json, String campo) {
    JsonNode valor = campo(json, campo);
    List<JsonNode> lista = new ArrayList<>();
    if (valor == null || valor.isNull()) return lista;
    if (valor.isArray()) {
      valor.forEach(lista::add);
    } else {
      lista.add(valor);
    }
    return lista;
  }

  /** Le um campo sem diferenciar maiusculas: a Receita usa {@code Codigo} e {@code codigo}. */
  private JsonNode campo(JsonNode json, String nome) {
    if (json == null || !json.isObject()) return null;
    Iterator<Map.Entry<String, JsonNode>> campos = json.fields();
    while (campos.hasNext()) {
      Map.Entry<String, JsonNode> entrada = campos.next();
      if (entrada.getKey().equalsIgnoreCase(nome)) return entrada.getValue();
    }
    return null;
  }

  private String texto(JsonNode json, String nome) {
    JsonNode valor = campo(json, nome);
    if (valor == null || valor.isNull() || valor.isContainerNode()) return null;
    String texto = valor.asText();
    return texto == null || texto.isBlank() ? null : texto.trim();
  }

  private JsonNode lerJson(String body) {
    if (isBlank(body)) return null;
    try {
      return JSON.readTree(body);
    } catch (Exception ex) {
      return null;
    }
  }

  // ─── GZip + Base64 ────────────────────────────────────────────────────────

  String jsonComXmlCompactado(String campo, String xml) {
    ObjectNode corpo = JSON.createObjectNode();
    corpo.put(campo, compactar(xml));
    return corpo.toString();
  }

  static String compactar(String xml) {
    try {
      ByteArrayOutputStream saida = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(saida)) {
        gzip.write(xml.getBytes(StandardCharsets.UTF_8));
      }
      return Base64.getEncoder().encodeToString(saida.toByteArray());
    } catch (Exception ex) {
      throw new IllegalStateException("Falha ao compactar o XML da NFS-e.", ex);
    }
  }

  static String descompactar(String base64) {
    if (base64 == null || base64.isBlank()) return null;
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64.trim())))) {
      return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return null;
    }
  }

  // ─── HTTP ─────────────────────────────────────────────────────────────────

  private RespostaHttp doPostJson(
      String url, String json, NfseInvoiceEntity invoice, String operation, String certificatePassword) {
    RestClient client = cliente(certificatePassword);
    try {
      return client
          .post()
          .uri(URI.create(url))
          .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
          .header(HttpHeaders.ACCEPT, "application/json")
          .body(json)
          .exchange((request, response) -> registrar(invoice, operation, response.getStatusCode().value(), readBody(response)));
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new NfseProvedorSemRespostaException(
          "SEM_RESPOSTA", "Sem resposta da Receita Nacional: " + ex.getClass().getSimpleName(), ex);
    }
  }

  private RespostaHttp doGet(String url, NfseInvoiceEntity invoice, String operation, String certificatePassword) {
    RestClient client = cliente(certificatePassword);
    try {
      return client
          .get()
          .uri(URI.create(url))
          .header(HttpHeaders.ACCEPT, "application/json")
          .exchange((request, response) -> registrar(invoice, operation, response.getStatusCode().value(), readBody(response)));
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new NfseProvedorSemRespostaException(
          "SEM_RESPOSTA", "Sem resposta da Receita Nacional: " + ex.getClass().getSimpleName(), ex);
    }
  }

  /** O cliente com mTLS. Erro de certificado ou senha aqui quer dizer que nada foi enviado. */
  private RestClient cliente(String certificatePassword) {
    int connectTimeoutMs = environment.getProperty(
        "app.nfse.provider.sefin-nacional.connect-timeout-ms", Integer.class, 30000);
    int readTimeoutMs = environment.getProperty(
        "app.nfse.provider.sefin-nacional.read-timeout-ms", Integer.class, 60000);
    try {
      return buildRestClient(connectTimeoutMs, readTimeoutMs, certificatePassword, true);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("NFSE_CERTIFICATE_PASSWORD_INVALID", ex);
    }
  }

  private RespostaHttp registrar(NfseInvoiceEntity invoice, String operation, int status, String body) {
    LOG.info(
        "nfse_sefin_nacional_http tenant_id={} invoice_id={} ambiente={} municipio={} http_status={} operation={}",
        invoice.getTenantId(), invoice.getId(), invoice.getAmbiente(),
        invoice.getMunicipioCodigoIbge(), status, operation);
    return new RespostaHttp(status, body);
  }

  private String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
    try (var input = response.getBody()) {
      byte[] bytes = input == null ? new byte[0] : input.readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new NfseProvedorSemRespostaException("SEM_RESPOSTA", "Falha ao ler a resposta da Receita Nacional.", ex);
    }
  }

  // ─── XML ──────────────────────────────────────────────────────────────────

  private Document parseXml(String xml) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
  }

  private String extractFirst(String xml, String tag) {
    if (xml == null || xml.isBlank()) return null;
    try {
      var nodes = parseXml(xml).getElementsByTagNameNS("*", tag);
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
      var nodes = parseXml(xml).getElementsByTagNameNS("*", tag);
      if (nodes == null || nodes.getLength() == 0 || !(nodes.item(0) instanceof Element element)) return null;
      String value = element.getAttribute(attributeName);
      return value == null || value.isBlank() ? null : value.trim();
    } catch (Exception ex) {
      return null;
    }
  }

  /** A chave fica no {@code Id} do {@code infNFSe}: {@code "NFS"} + 50 digitos. */
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
    String chave = chaveOrNull(invoice.getChaveAcessoNfse());
    if (chave == null) chave = "";
    if (chave.length() != 50) {
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_ACCESS_KEY_MISSING");
    }
    String motivo = limit(reason, 255);
    if (motivo == null || motivo.length() < 15) {
      // Esquema 1.01: xMotivo tem de 15 a 255 caracteres.
      throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_CANCEL_REASON_TOO_SHORT");
    }
    // Leiaute 1.01: Id = "PRE" + chave (50) + codigo do evento (6); nPedRegEvento saiu do pedido.
    String eventId = "PRE" + chave + "101101";
    // Sempre com segundos e fuso: OffsetDateTime.toString() omite os segundos quando sao zero.
    String dhEvento = OffsetDateTime.now(ZoneOffset.of("-03:00")).withNano(0)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));
    String xml = "<pedRegEvento xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"" + LeiauteNfseNacional.VERSAO + "\">"
        + "<infPedReg Id=\"" + escape(eventId) + "\">"
        + "<tpAmb>" + toAmbienteCode(invoice.getAmbiente()) + "</tpAmb>"
        + "<verAplic>" + escape(applicationVersion) + "</verAplic>"
        + "<dhEvento>" + escape(dhEvento) + "</dhEvento>"
        + "<CNPJAutor>" + escape(authorDocument) + "</CNPJAutor>"
        + "<chNFSe>" + escape(chave) + "</chNFSe>"
        + "<e101101>"
        + "<xDesc>Cancelamento de NFS-e</xDesc>"
        + "<cMotivo>" + resolveCancelReasonCode(reason) + "</cMotivo>"
        + "<xMotivo>" + escape(motivo) + "</xMotivo>"
        + "</e101101>"
        + "</infPedReg>"
        + "</pedRegEvento>";
    LeiauteNfseNacional.validarPedidoDeEvento(xml);
    return xml;
  }

  private String toAmbienteCode(NfseAmbiente ambiente) {
    return ambiente == NfseAmbiente.PRODUCAO ? "1" : "2";
  }

  private String resolveCancelReasonCode(String reason) {
    String normalized = reason == null ? "" : reason.trim().toLowerCase();
    if (normalized.contains("erro")) return "1";
    if (normalized.contains("nao prest") || normalized.contains("não prest")) return "2";
    // Esquema 1.01: so 1 (erro na emissao), 2 (servico nao prestado) ou 9 (outros).
    String configured = environment.getProperty("app.nfse.provider.sefin-nacional.cancel-reason-code-default");
    return isConfigured(configured) && configured.trim().matches("[129]") ? configured.trim() : "9";
  }

  // ─── utilitarios ──────────────────────────────────────────────────────────

  private String requireConfigured(String value, String code) {
    if (!isConfigured(value)) throw new IllegalArgumentException(code);
    return value.trim();
  }

  private String digitsOnly(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }

  /**
   * Chave de acesso: 50 posicoes, letras maiusculas e digitos (o CNPJ alfanumerico entra no meio
   * da chave). So tira o que nao e letra nem digito.
   */
  private String chaveOrNull(String value) {
    if (value == null) return null;
    String chave = value.trim().toUpperCase().replaceAll("[^0-9A-Z]", "");
    return chave.isEmpty() ? null : chave;
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
    // HTTP/1.1 fixo: o HttpClient do Java tenta HTTP/2, e a Sefin corta o pedido com
    // "RST_STREAM: Use HTTP/1.1 for request" antes de processar (teste em homologacao de 06/11/2026).
    HttpClient.Builder builder = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
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
