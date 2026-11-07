package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.exception.NfseProvedorRecusouException;
import br.com.phdigitalcode.azzo.agenda.pro.exception.NfseProvedorSemRespostaException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Cobre a logica pura de {@link SefinNacionalProviderAdapter} — resolucao de URL, parsing de
 * resposta, bloqueio de producao. Porta os 6 testes de {@code
 * modules/nfse/application/provider/SefinNacionalProviderAdapterUnitTest.java} do original
 * (confirmado: o Quarkus original <b>nunca</b> exercita a camada de transporte/mTLS em teste — so
 * testa metodos puros que nao chamam {@code buildHttpClient}/{@code doPostXml}/{@code doGet}) e
 * acrescenta casos novos para as assimetrias documentadas na Etapa 25 (fallback unico de URL do
 * SEFIN Nacional, bloqueio de query method != GET). A prova real de mTLS/SOAP fica em {@link
 * SefinNacionalProviderAdapterMtlsIntegrationTest}.
 *
 * <p>{@link Environment} e usado via {@link MockEnvironment} (nao Mockito) porque o original
 * consulta {@code ConfigProvider} com chaves construidas dinamicamente por ambiente/operacao —
 * {@code MockEnvironment} reproduz a resolucao real de propriedades sem precisar estubar cada
 * chave manualmente.
 */
class SefinNacionalProviderAdapterUnitTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  private EncryptionService encryptionService;
  private NfseXmlSignerService nfseXmlSignerService;
  private NfseConfigRepository nfseConfigRepository;
  private FiscalTaxConfigRepository fiscalTaxConfigRepository;
  private FiscalCertificateService fiscalCertificateService;
  private MockEnvironment environment;
  private SefinNacionalProviderAdapter adapter;

  @BeforeEach
  void setUp() {
    encryptionService = mock(EncryptionService.class);
    nfseXmlSignerService = mock(NfseXmlSignerService.class);
    nfseConfigRepository = mock(NfseConfigRepository.class);
    fiscalTaxConfigRepository = mock(FiscalTaxConfigRepository.class);
    fiscalCertificateService = mock(FiscalCertificateService.class);
    environment = new MockEnvironment();
    // Mesmos defaults do application.yml (V. app.nfse.provider.sefin-nacional.*)
    environment.setProperty("app.nfse.provider.sefin-nacional.base-url", "https://sefin.nfse.gov.br/SefinNacional");
    environment.setProperty(
        "app.nfse.provider.sefin-nacional.base-url-homologacao",
        "https://sefin.producaorestrita.nfse.gov.br/SefinNacional");
    environment.setProperty("app.nfse.provider.sefin-nacional.production-enabled", "false");
    environment.setProperty("app.nfse.provider.sefin-nacional.authorize-path", "__unset__");
    environment.setProperty("app.nfse.provider.sefin-nacional.authorize-path-homologacao", "__unset__");
    environment.setProperty("app.nfse.provider.sefin-nacional.cancel-path", "/nfse/{chaveAcesso}/eventos");
    environment.setProperty("app.nfse.provider.sefin-nacional.cancel-path-homologacao", "/nfse/{chaveAcesso}/eventos");
    environment.setProperty("app.nfse.provider.sefin-nacional.query-path", "/nfse/{chaveAcesso}");
    environment.setProperty("app.nfse.provider.sefin-nacional.query-path-homologacao", "/nfse/{chaveAcesso}");
    environment.setProperty("app.nfse.provider.sefin-nacional.cancel-reason-code-default", "9");

    adapter = new SefinNacionalProviderAdapter(
        encryptionService,
        nfseXmlSignerService,
        nfseConfigRepository,
        fiscalTaxConfigRepository,
        fiscalCertificateService,
        environment);
  }

  private NfseInvoiceEntity invoice(NfseAmbiente ambiente) {
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setId(UUID.randomUUID());
    invoice.setTenantId(TENANT_ID);
    invoice.setAmbiente(ambiente);
    invoice.setMunicipioCodigoIbge("3550308");
    return invoice;
  }

  @Test
  void deveResolverBaseUrlDeHomologacaoDaReceita() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);

    String baseUrl = adapter.resolveBaseUrl(invoice);

    assertThat(baseUrl).isEqualTo("https://sefin.producaorestrita.nfse.gov.br/SefinNacional");
  }

  @Test
  void deveResolverBaseUrlDeProducaoDaReceita() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.PRODUCAO);

    String baseUrl = adapter.resolveBaseUrl(invoice);

    assertThat(baseUrl).isEqualTo("https://sefin.nfse.gov.br/SefinNacional");
  }

  @Test
  void deveFalharComCodigoFuncionalQuandoPathDeAutorizacaoNaoEstiverConfigurado() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    invoice.setXmlEnvioEnc("<xml/>".getBytes());

    assertThatThrownBy(() -> adapter.authorize(invoice, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_AUTHORIZE_URL_MISSING");
  }

  @Test
  void deveBloquearProducaoPorPadrao() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.PRODUCAO);
    invoice.setXmlEnvioEnc("<xml/>".getBytes());

    assertThatThrownBy(() -> adapter.authorize(invoice, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_PRODUCTION_DISABLED");
  }

  private static final String CHAVE = "12345678901234567890123456789012345678901234567890";

  private SefinNacionalProviderAdapter.RespostaHttp resposta(int status, String body) {
    return new SefinNacionalProviderAdapter.RespostaHttp(status, body);
  }

  @Test
  void enviaADpsCompactadaEmGzipBase64NoCampoDaApi() throws Exception {
    String json = adapter.jsonComXmlCompactado("dpsXmlGZipB64", "<DPS>teste</DPS>");

    var lido = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
    assertThat(lido.fieldNames()).toIterable().containsExactly("dpsXmlGZipB64");
    // E1229 (homologacao, 06/11/2026): a Sefin exige a declaracao UTF-8 no inicio.
    assertThat(SefinNacionalProviderAdapter.descompactar(lido.get("dpsXmlGZipB64").asText()))
        .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?><DPS>teste</DPS>");
  }

  @Test
  void deveInterpretarRetornoAutorizadoDaReceitaNacional() {
    String nfse = """
        <NFSe xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.00">
          <infNFSe Id="NFS%s"><nNFSe>123456</nNFSe></infNFSe>
        </NFSe>
        """.formatted(CHAVE);
    String body = """
        {"tipoAmbiente":2,"idDps":"DPS355030821234567800019900001000000000000001",
         "chaveAcesso":"%s","nfseXmlGZipB64":"%s"}
        """.formatted(CHAVE, SefinNacionalProviderAdapter.compactar(nfse));

    var result = adapter.parseAuthorizationResponse(resposta(201, body));

    assertThat(result.providerStatusCode()).isEqualTo("100");
    assertThat(result.numeroNfse()).isEqualTo("123456");
    assertThat(result.chaveAcessoNfse()).isEqualTo(CHAVE);
    assertThat(result.protocolo()).isEqualTo("DPS355030821234567800019900001000000000000001");
  }

  @Test
  void respostaDeSucessoSemChaveNaoEhTratadaComoAutorizada() {
    // Antes, qualquer texto com "numero" virava nota autorizada. Sem chave, o resultado e incerto.
    assertThatThrownBy(() -> adapter.parseAuthorizationResponse(resposta(200, "{\"numero\":\"1\"}")))
        .isInstanceOf(NfseProvedorSemRespostaException.class);
  }

  @Test
  void recusaDaReceitaTrazOCodigoEOMotivo() {
    String body = """
        {"tipoAmbiente":2,"erros":[
          {"Codigo":"E0312","Descricao":"CNPJ do prestador nao encontrado","Complemento":"cadastro"},
          {"Codigo":"E0100","Descricao":"Data de competencia invalida"}]}
        """;

    assertThatThrownBy(() -> adapter.parseAuthorizationResponse(resposta(400, body)))
        .isInstanceOfSatisfying(NfseProvedorRecusouException.class, ex -> {
          assertThat(ex.getCodigo()).isEqualTo("E0312");
          assertThat(ex.getMotivo())
              .isEqualTo("E0312 - CNPJ do prestador nao encontrado (cadastro); E0100 - Data de competencia invalida");
        });
  }

  @Test
  void recusaSemJsonGuardaOCorpoDaResposta() {
    assertThatThrownBy(() -> adapter.parseAuthorizationResponse(resposta(422, "schema invalido")))
        .isInstanceOfSatisfying(NfseProvedorRecusouException.class, ex -> {
          assertThat(ex.getCodigo()).isEqualTo("422");
          assertThat(ex.getMotivo()).isEqualTo("HTTP 422: schema invalido");
        });
  }

  @Test
  void erroDoServidorDaReceitaEhSemRespostaENaoRecusa() {
    assertThatThrownBy(() -> adapter.parseAuthorizationResponse(resposta(503, "")))
        .isInstanceOfSatisfying(NfseProvedorSemRespostaException.class,
            ex -> assertThat(ex.getCodigo()).isEqualTo("503"));
    assertThatThrownBy(() -> adapter.parseAuthorizationResponse(resposta(429, "")))
        .isInstanceOf(NfseProvedorSemRespostaException.class);
  }

  @Test
  void certificadoRecusadoPelaReceitaTemMensagemPropria() {
    assertThatThrownBy(() -> adapter.parseAuthorizationResponse(resposta(403, "")))
        .isInstanceOfSatisfying(NfseProvedorRecusouException.class,
            ex -> assertThat(ex.getMotivo()).contains("certificado digital"));
  }

  @Test
  void deveInterpretarCancelamentoRejeitado() {
    String body = "{\"erros\":[{\"codigo\":\"E1235\",\"descricao\":\"Prazo expirado\"}]}";

    assertThatThrownBy(() -> adapter.parseCancellationResponse(resposta(400, body)))
        .isInstanceOfSatisfying(NfseProvedorRecusouException.class,
            ex -> assertThat(ex.getMotivo()).isEqualTo("E1235 - Prazo expirado"));
  }

  @Test
  void cancelamentoAceito() {
    assertThat(adapter.parseCancellationResponse(resposta(201, "{}")).providerStatusMessage())
        .isEqualTo("Cancelamento registrado pela Receita Nacional");
  }

  @Test
  void queryStatusVazioQuandoInvoiceSemChaveNemProtocolo() {
    // Sem consulta em segundo plano: toda chamada a Receita exige o certificado (mTLS).
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    invoice.setChaveAcessoNfse(CHAVE);

    Optional<?> result = adapter.queryStatus(invoice);

    assertThat(result).isEmpty();
  }

  @Test
  void deveExigirSenhaDeCertificadoParaCancelamento() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);

    assertThatThrownBy(() -> adapter.cancel(invoice, "motivo qualquer", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_CERTIFICATE_PASSWORD_REQUIRED");
  }

  @Test
  void deveExigirMotivoParaCancelamento() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);

    assertThatThrownBy(() -> adapter.cancel(invoice, "", "senha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_CANCEL_REASON_REQUIRED");
  }

  @Test
  void deveExigirSenhaDeCertificadoParaAutorizarComMtls() {
    environment.setProperty("app.nfse.provider.sefin-nacional.authorize-path-homologacao", "/nfse/authorize");
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    invoice.setXmlEnvioEnc("<xml/>".getBytes());
    when(encryptionService.decrypt(any())).thenReturn("<xml/>");

    assertThatThrownBy(() -> adapter.authorize(invoice, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_CERTIFICATE_PASSWORD_REQUIRED");
  }

  @Test
  void buildCancellationRequestXmlMontaPedRegEventoComDadosDoTenant() throws Exception {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    invoice.setChaveAcessoNfse("1".repeat(50));

    NfseConfigEntity config = new NfseConfigEntity();
    config.setApplicationVersion("1.00");
    when(nfseConfigRepository.findByTenantAndAmbiente(eq(TENANT_ID), eq(NfseAmbiente.HOMOLOGACAO)))
        .thenReturn(Optional.of(config));

    FiscalTaxConfigEntity taxConfig = new FiscalTaxConfigEntity();
    taxConfig.setIssuerCnpj("12345678000199");
    taxConfig.setIcmsRate(BigDecimal.ZERO);
    taxConfig.setPisRate(BigDecimal.ZERO);
    taxConfig.setCofinsRate(BigDecimal.ZERO);
    when(fiscalTaxConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(taxConfig));

    when(nfseXmlSignerService.sign(any(), eq("senha-teste-123"))).thenAnswer(inv -> inv.getArgument(0));

    java.lang.reflect.Method method = SefinNacionalProviderAdapter.class
        .getDeclaredMethod("buildCancellationRequestXml", NfseInvoiceEntity.class, String.class);
    method.setAccessible(true);
    String xml = (String) method.invoke(adapter, invoice, "Erro de emissao");

    assertThat(xml).contains("<pedRegEvento");
    assertThat(xml).contains("<CNPJAutor>12345678000199</CNPJAutor>");
    assertThat(xml).contains("<chNFSe>" + "1".repeat(50) + "</chNFSe>");
    assertThat(xml).contains("<cMotivo>1</cMotivo>");
    // Leiaute 1.01: Id = PRE + chave + evento, sem nPedRegEvento (o XML ja passou no XSD oficial).
    assertThat(xml).contains("<infPedReg Id=\"PRE" + "1".repeat(50) + "101101\">");
    assertThat(xml).doesNotContain("nPedRegEvento");
    assertThat(xml).containsPattern("<dhEvento>20\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d-03:00</dhEvento>");

    // xMotivo tem no minimo 15 caracteres no esquema.
    assertThatThrownBy(() -> {
          try {
            method.invoke(adapter, invoice, "Erro");
          } catch (java.lang.reflect.InvocationTargetException ex) {
            throw ex.getCause();
          }
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_CANCEL_REASON_TOO_SHORT");
  }

  /** Teste em homologacao de 06/11/2026: a Sefin corta HTTP/2 com "Use HTTP/1.1 for request". */
  @Test
  void falaComAReceitaEmHttp11() throws Exception {
    assertThat(adapter.buildHttpClient(1000, null, false).version())
        .isEqualTo(java.net.http.HttpClient.Version.HTTP_1_1);
  }

  @Test
  void declaracaoUtf8NaoDuplicaETrocaOutraCodificacao() {
    assertThat(SefinNacionalProviderAdapter.comDeclaracaoUtf8("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<a/>"))
        .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?><a/>");
    assertThat(SefinNacionalProviderAdapter.comDeclaracaoUtf8("\uFEFF<a/>"))
        .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?><a/>");
  }
}
