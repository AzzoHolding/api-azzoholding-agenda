package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.UUID;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Prova mTLS <b>real</b> de {@link SefinNacionalProviderAdapter#buildHttpClient}: nao mocka a
 * camada de transporte, sobe um {@link HttpsServer} local com {@code SSLParameters.setNeedClientAuth(true)}
 * (exige certificado de cliente na negociacao TLS, exatamente como {@code sefin.nfse.gov.br} faz em
 * produção) e verifica de ponta a ponta que:
 *
 * <ul>
 *   <li>o {@link HttpClient} construido pelo adapter com {@code mtlsEnabled=true} completa o
 *       handshake TLS apresentando o certificado <b>de verdade</b> extraido do PKCS12 de teste
 *       ({@code /fiscal/certificado-teste.p12}, mesma fixture de {@code NfseXmlSignerServiceTest})
 *       — o servidor confirma isso lendo {@code SSLSession.getPeerCertificates()} e comparando
 *       byte a byte com o certificado esperado, nao apenas "a chamada nao lancou excecao";
 *   <li>sem certificado de cliente ({@code mtlsEnabled=false}), o mesmo servidor rejeita o
 *       handshake — prova que a exigencia de mTLS e real no servidor de teste, nao um clique-e-passa;
 *   <li>o fluxo completo de {@code authorize()} (URL resolvida por ambiente, POST do XML assinado,
 *       parsing da resposta) funciona sobre essa conexao mTLS real, nao so o {@code HttpClient}
 *       isolado.
 * </ul>
 *
 * <p>Certificado do servidor gerado em runtime via {@code keytool} (JDK do proprio ambiente de
 * teste, sem dependencia externa nova) porque o projeto nao tem Bouncy Castle para gerar X.509
 * programaticamente. O client aprende a confiar nesse certificado de servidor via as system
 * properties {@code javax.net.ssl.trustStore}/{@code trustStorePassword} — {@code
 * SefinNacionalProviderAdapter.buildHttpClient} passa {@code trustManagers=null} para o {@link
 * SSLContext} (igual ao original Quarkus), entao usa o trust manager default da JVM, que le essas
 * properties. Nada de producao foi alterado para viabilizar este teste.
 */
class SefinNacionalProviderAdapterMtlsIntegrationTest {

  private static final String CERT_PASSWORD = "senha-teste-123";
  private static final UUID TENANT_ID = UUID.randomUUID();

  private static File tempDir;
  private static File trustStoreFile;
  private static HttpsServer server;
  private static int port;
  private static FiscalCertificateService.KeyMaterial clientKeyMaterial;
  private static volatile X509Certificate capturedPeerCertificate;
  private static volatile String capturedRequestBody;
  private static volatile String capturedRequestMethod;

  private FiscalCertificateService fiscalCertificateService;
  private EncryptionService encryptionService;
  private MockEnvironment environment;
  private SefinNacionalProviderAdapter adapter;

  @BeforeAll
  static void startServer() throws Exception {
    clientKeyMaterial = loadKeyMaterialFromTestFixture();
    tempDir = Files.createTempDirectory("sefin-mtls-test").toFile();

    File serverKeyStoreFile = new File(tempDir, "server.p12");
    runKeytool(
        "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
        "-validity", "2", "-storetype", "PKCS12",
        "-keystore", serverKeyStoreFile.getAbsolutePath(), "-storepass", "changeit",
        "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1");

    File serverCertFile = new File(tempDir, "server.cer");
    runKeytool(
        "-exportcert", "-alias", "server", "-keystore", serverKeyStoreFile.getAbsolutePath(),
        "-storepass", "changeit", "-file", serverCertFile.getAbsolutePath());

    trustStoreFile = new File(tempDir, "client-truststore.p12");
    runKeytool(
        "-importcert", "-noprompt", "-alias", "server",
        "-file", serverCertFile.getAbsolutePath(),
        "-keystore", trustStoreFile.getAbsolutePath(), "-storepass", "changeit",
        "-storetype", "PKCS12");

    // Server-side key manager (server's own identity)
    KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(serverKeyStoreFile.toPath())) {
      serverKeyStore.load(in, "changeit".toCharArray());
    }
    KeyManagerFactory serverKmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    serverKmf.init(serverKeyStore, "changeit".toCharArray());

    // Server-side trust manager: only trusts the exact client test certificate (mirrors a real
    // SEFIN Nacional endpoint trusting only certificates issued by an accredited AC).
    KeyStore serverTrustStore = KeyStore.getInstance("PKCS12");
    serverTrustStore.load(null, null);
    serverTrustStore.setCertificateEntry("nfse-client-under-test", clientKeyMaterial.certificate());
    TrustManagerFactory serverTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    serverTmf.init(serverTrustStore);

    SSLContext serverSslContext = SSLContext.getInstance("TLS");
    serverSslContext.init(serverKmf.getKeyManagers(), serverTmf.getTrustManagers(), null);

    server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(serverSslContext) {
      @Override
      public void configure(HttpsParameters params) {
        SSLParameters sslParameters = serverSslContext.getDefaultSSLParameters();
        sslParameters.setNeedClientAuth(true);
        params.setSSLParameters(sslParameters);
      }
    });
    server.createContext("/nfse", exchange -> {
      try {
        capturedRequestMethod = exchange.getRequestMethod();
        capturedRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (exchange instanceof HttpsExchange httpsExchange) {
          var peerCerts = httpsExchange.getSSLSession().getPeerCertificates();
          if (peerCerts.length > 0 && peerCerts[0] instanceof X509Certificate x509) {
            capturedPeerCertificate = x509;
          }
        }
        String responseXml = """
            <retorno xmlns="http://www.sped.fazenda.gov.br/nfse">
              <cStat>100</cStat>
              <xMotivo>Autorizado</xMotivo>
              <nNFSe>987654</nNFSe>
              <nProt>PROT-MTLS-1</nProt>
            </retorno>
            """;
        byte[] responseBytes = responseXml.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/xml");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
          os.write(responseBytes);
        }
      } catch (Exception ex) {
        exchange.sendResponseHeaders(500, 0);
        exchange.close();
      }
    });
    server.start();
    port = server.getAddress().getPort();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.stop(0);
    System.clearProperty("javax.net.ssl.trustStore");
    System.clearProperty("javax.net.ssl.trustStorePassword");
    System.clearProperty("javax.net.ssl.trustStoreType");
    if (tempDir != null) {
      for (File f : tempDir.listFiles()) f.delete();
      tempDir.delete();
    }
  }

  @BeforeEach
  void setUp() {
    System.setProperty("javax.net.ssl.trustStore", trustStoreFile.getAbsolutePath());
    System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");

    fiscalCertificateService = mock(FiscalCertificateService.class);
    when(fiscalCertificateService.loadActiveKeyMaterial(eq(CERT_PASSWORD))).thenReturn(clientKeyMaterial);

    encryptionService = mock(EncryptionService.class);
    when(encryptionService.decrypt(any())).thenReturn("<InfDeclaracaoPrestacaoServico/>");

    environment = new MockEnvironment();
    environment.setProperty("app.nfse.provider.sefin-nacional.base-url-homologacao", "https://localhost:" + port);
    environment.setProperty("app.nfse.provider.sefin-nacional.authorize-path-homologacao", "/nfse");
    environment.setProperty("app.nfse.provider.sefin-nacional.production-enabled", "false");
    environment.setProperty("app.nfse.provider.sefin-nacional.connect-timeout-ms", "5000");
    environment.setProperty("app.nfse.provider.sefin-nacional.read-timeout-ms", "5000");

    NfseConfigRepository nfseConfigRepository = mock(NfseConfigRepository.class);
    FiscalTaxConfigRepository fiscalTaxConfigRepository = mock(FiscalTaxConfigRepository.class);

    adapter = new SefinNacionalProviderAdapter(
        encryptionService,
        mock(NfseXmlSignerService.class),
        nfseConfigRepository,
        fiscalTaxConfigRepository,
        fiscalCertificateService,
        environment);
  }

  @Test
  void handshakeMtlsRealApresentaOCertificadoDeClienteEsperadoAoServidor() throws Exception {
    HttpClient client = adapter.buildHttpClient(5000, CERT_PASSWORD, true);

    HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/nfse"))
        .POST(HttpRequest.BodyPublishers.ofString("<ping/>"))
        .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(capturedPeerCertificate).isNotNull();
    assertThat(capturedPeerCertificate.getEncoded()).isEqualTo(clientKeyMaterial.certificate().getEncoded());
  }

  @Test
  void semCertificadoDeClienteOServidorRejeitaOHandshake() throws Exception {
    HttpClient plainClient = adapter.buildHttpClient(5000, null, false);

    HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/nfse"))
        .POST(HttpRequest.BodyPublishers.ofString("<ping/>"))
        .build();

    assertThatThrownBy(() -> plainClient.send(request, HttpResponse.BodyHandlers.ofString()))
        .isInstanceOf(IOException.class);
  }

  @Test
  void autorizarDeVerdadeSobreConexaoMtlsRealParseiaARespostaDoServidor() throws Exception {
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setId(UUID.randomUUID());
    invoice.setTenantId(TENANT_ID);
    invoice.setAmbiente(NfseAmbiente.HOMOLOGACAO);
    invoice.setMunicipioCodigoIbge("3550308");
    invoice.setXmlEnvioEnc("<xml-cifrado/>".getBytes(StandardCharsets.UTF_8));

    var result = adapter.authorize(invoice, CERT_PASSWORD);

    assertThat(result.providerStatusCode()).isEqualTo("100");
    assertThat(result.numeroNfse()).isEqualTo("987654");
    assertThat(result.protocolo()).isEqualTo("PROT-MTLS-1");
    assertThat(capturedRequestMethod).isEqualTo("POST");
    assertThat(capturedRequestBody).isEqualTo("<InfDeclaracaoPrestacaoServico/>");
    assertThat(capturedPeerCertificate.getEncoded()).isEqualTo(clientKeyMaterial.certificate().getEncoded());
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private static void runKeytool(String... args) throws Exception {
    String keytool = System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool";
    java.util.List<String> command = new java.util.ArrayList<>();
    command.add(keytool);
    command.addAll(java.util.Arrays.asList(args));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output;
    try (InputStream in = process.getInputStream()) {
      output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException("keytool falhou (exit=" + exit + "): " + output);
    }
  }

  private static FiscalCertificateService.KeyMaterial loadKeyMaterialFromTestFixture() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream input =
        SefinNacionalProviderAdapterMtlsIntegrationTest.class.getResourceAsStream("/fiscal/certificado-teste.p12")) {
      if (input == null) throw new IllegalStateException("Fixture de certificado de teste nao encontrada.");
      keyStore.load(input, CERT_PASSWORD.toCharArray());
    }
    String alias = null;
    var aliases = keyStore.aliases();
    while (aliases.hasMoreElements()) {
      String current = aliases.nextElement();
      if (keyStore.isKeyEntry(current)) {
        alias = current;
        break;
      }
    }
    PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, CERT_PASSWORD.toCharArray());
    X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
    return new FiscalCertificateService.KeyMaterial(privateKey, certificate);
  }
}
