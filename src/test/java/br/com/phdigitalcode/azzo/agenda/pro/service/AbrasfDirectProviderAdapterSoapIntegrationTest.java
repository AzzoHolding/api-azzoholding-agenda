package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Prova o protocolo SOAP <b>real</b> de {@link AbrasfDirectProviderAdapter} de ponta a ponta: sobe
 * um {@link HttpServer} local (sem TLS — o original tambem nao usa mTLS de transporte para ABRASF,
 * so autenticacao via XML assinado dentro do envelope, ver Javadoc da classe), captura a requisicao
 * inteira (metodo, header {@code SOAPAction}, {@code Content-Type}, corpo do envelope XML) e
 * confirma que:
 *
 * <ul>
 *   <li>o envelope SOAP real enviado contem o XML assinado <b>inline</b> dentro de {@code
 *       <nfseDadosMsg>} (sem CDATA — o comentario do original avisa que CDATA quebraria o parse
 *       XML na prefeitura, este teste garante que o porte preservou isso);
 *   <li>o header {@code SOAPAction} e enviado com o nome exato da operacao ABRASF;
 *   <li>uma resposta SOAP de sucesso (com {@code NumeroNfse}) e interpretada como autorizada;
 *   <li>uma resposta com {@code soap:Fault} e interpretada como rejeicao, propagando a excecao
 *       funcional esperada.
 * </ul>
 */
class AbrasfDirectProviderAdapterSoapIntegrationTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  private HttpServer server;
  private int port;
  private final AtomicReference<String> capturedMethod = new AtomicReference<>();
  private final AtomicReference<String> capturedSoapAction = new AtomicReference<>();
  private final AtomicReference<String> capturedContentType = new AtomicReference<>();
  private final AtomicReference<String> capturedBody = new AtomicReference<>();
  private final AtomicReference<String> nextResponseXml = new AtomicReference<>();
  private final AtomicReference<Integer> nextResponseStatus = new AtomicReference<>(200);

  private EncryptionService encryptionService;
  private MockEnvironment environment;
  private NfseConfigRepository nfseConfigRepository;
  private AbrasfDirectProviderAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/ws/abrasf", exchange -> {
      capturedMethod.set(exchange.getRequestMethod());
      capturedSoapAction.set(exchange.getRequestHeaders().getFirst("SOAPAction"));
      capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
      capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

      byte[] responseBytes = nextResponseXml.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
      exchange.sendResponseHeaders(nextResponseStatus.get(), responseBytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(responseBytes);
      }
    });
    server.start();
    port = server.getAddress().getPort();

    encryptionService = mock(EncryptionService.class);
    when(encryptionService.decrypt(any())).thenReturn("<InfDeclaracaoPrestacaoServico><Rps><Numero>1</Numero></Rps></InfDeclaracaoPrestacaoServico>");

    nfseConfigRepository = mock(NfseConfigRepository.class);
    when(nfseConfigRepository.findByTenantAndAmbiente(any(), any())).thenReturn(Optional.empty());

    environment = new MockEnvironment();
    environment.setProperty("app.nfse.provider.abrasf.default.ws-url-homologacao", "http://localhost:" + port + "/ws/abrasf");
    environment.setProperty("app.nfse.provider.abrasf.default.ws-url", "__unset__");
    environment.setProperty("app.nfse.provider.abrasf.connect-timeout-ms", "5000");
    environment.setProperty("app.nfse.provider.abrasf.read-timeout-ms", "5000");

    adapter = new AbrasfDirectProviderAdapter(
        encryptionService, mock(FiscalTaxConfigRepository.class), nfseConfigRepository, environment);
  }

  @AfterEach
  void tearDown() {
    if (server != null) server.stop(0);
  }

  private NfseInvoiceEntity invoice() {
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setId(UUID.randomUUID());
    invoice.setTenantId(TENANT_ID);
    invoice.setAmbiente(NfseAmbiente.HOMOLOGACAO);
    invoice.setMunicipioCodigoIbge("3550308");
    invoice.setXmlEnvioEnc("<xml-cifrado/>".getBytes(StandardCharsets.UTF_8));
    return invoice;
  }

  @Test
  void autorizarEnviaEnvelopeSoapRealComXmlInlineESoapActionCorreto() {
    nextResponseXml.set("""
        <RecepcionarLoteRpsResposta>
          <NumeroNfse>NFSE-123</NumeroNfse>
          <Protocolo>PROTO-ABC</Protocolo>
          <CodigoVerificacao>CV-1</CodigoVerificacao>
        </RecepcionarLoteRpsResposta>
        """);

    var result = adapter.authorize(invoice(), null);

    assertThat(capturedMethod.get()).isEqualTo("POST");
    assertThat(capturedSoapAction.get()).isEqualTo("RecepcionarLoteRps");
    assertThat(capturedContentType.get()).contains("text/xml");
    assertThat(capturedBody.get())
        .contains("<soapenv:Envelope")
        .contains("<RecepcionarLoteRps>")
        .contains("<nfseDadosMsg>")
        .contains("<InfDeclaracaoPrestacaoServico><Rps><Numero>1</Numero></Rps></InfDeclaracaoPrestacaoServico>")
        .doesNotContain("<![CDATA[");

    assertThat(result.providerStatusCode()).isEqualTo("100");
    assertThat(result.numeroNfse()).isEqualTo("NFSE-123");
    assertThat(result.protocolo()).isEqualTo("PROTO-ABC");
    assertThat(result.codigoVerificacao()).isEqualTo("CV-1");
  }

  @Test
  void autorizarSemNumeroMasComProtocoloVoltaComoLoteEmProcessamento() {
    nextResponseXml.set("""
        <RecepcionarLoteRpsResposta>
          <NumeroLote>LOTE-9</NumeroLote>
        </RecepcionarLoteRpsResposta>
        """);

    var result = adapter.authorize(invoice(), null);

    assertThat(result.providerStatusCode()).isEqualTo("102");
    assertThat(result.numeroNfse()).isNull();
    assertThat(result.protocolo()).isEqualTo("LOTE-9");
  }

  @Test
  void autorizarComSoapFaultLancaRejeicaoFuncionalComMensagemDoProvedor() {
    nextResponseXml.set("""
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
          <soapenv:Body>
            <soapenv:Fault>
              <faultstring>Erro de validacao</faultstring>
            </soapenv:Fault>
          </soapenv:Body>
        </soapenv:Envelope>
        """);

    assertThatThrownBy(() -> adapter.authorize(invoice(), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("NFSE_PROVIDER_ABRASF_REJECTED:");
  }

  @Test
  void cancelarEnviaEnvelopeCorretoEInterpretaSucesso() {
    nextResponseXml.set("<CancelarNfseResposta><Sucesso>true</Sucesso></CancelarNfseResposta>");
    NfseInvoiceEntity invoice = invoice();
    invoice.setNumeroNfse("NFSE-1");
    invoice.setCodigoVerificacao("CV-1");

    var result = adapter.cancel(invoice, "Erro na emissao", null);

    assertThat(capturedSoapAction.get()).isEqualTo("CancelarNfse");
    assertThat(capturedBody.get()).contains("<CancelarNfseEnvio>").contains("<MotivoCancelamento>Erro na emissao</MotivoCancelamento>");
    assertThat(result.providerStatusCode()).isEqualTo("101");
  }

  @Test
  void httpErrorDoServidorViraExcecaoDeComunicacao() {
    nextResponseStatus.set(500);
    nextResponseXml.set("<erro/>");

    assertThatThrownBy(() -> adapter.authorize(invoice(), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("NFSE_PROVIDER_ABRASF_HTTP_ERROR_500");
  }
}
