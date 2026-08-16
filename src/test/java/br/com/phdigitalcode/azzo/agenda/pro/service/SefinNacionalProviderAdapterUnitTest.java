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
    environment.setProperty("app.nfse.provider.sefin-nacional.query-http-method", "GET");
    environment.setProperty("app.nfse.provider.sefin-nacional.query-access-key-param", "chNFSe");
    environment.setProperty("app.nfse.provider.sefin-nacional.query-protocol-param", "protocolo");
    environment.setProperty("app.nfse.provider.sefin-nacional.query-include-protocol", "true");
    environment.setProperty("app.nfse.provider.sefin-nacional.query-mtls-enabled", "false");

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

  @Test
  void deveInterpretarRetornoAutorizadoDaReceitaNacional() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    String response = """
        <retorno xmlns="http://www.sped.fazenda.gov.br/nfse">
          <cStat>100</cStat>
          <xMotivo>Autorizado</xMotivo>
          <chNFSe>12345678901234567890123456789012345678901234567890</chNFSe>
          <nNFSe>123456</nNFSe>
          <nProt>ABC123</nProt>
          <cVerif>XYZ789</cVerif>
        </retorno>
        """;

    var result = adapter.parseAuthorizationResponse(invoice, response);

    assertThat(result).isNotNull();
    assertThat(result.providerStatusCode()).isEqualTo("100");
    assertThat(result.providerStatusMessage()).isEqualTo("Autorizado");
    assertThat(result.numeroNfse()).isEqualTo("123456");
    assertThat(result.protocolo()).isEqualTo("ABC123");
    assertThat(result.codigoVerificacao()).isEqualTo("XYZ789");
    assertThat(result.chaveAcessoNfse()).isEqualTo("12345678901234567890123456789012345678901234567890");
  }

  @Test
  void deveInterpretarRetornoDeConsultaDaReceitaNacional() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    invoice.setProtocolo("PROTO-1");
    String response = """
        <NFSe xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.00">
          <infNFSe Id="NFS12345678901234567890123456789012345678901234567890">
            <nNFSe>123456</nNFSe>
            <cStat>100</cStat>
            <xMotivo>Autorizado</xMotivo>
          </infNFSe>
        </NFSe>
        """;

    var result = adapter.parseQueryResponse(invoice, response);

    assertThat(result).isNotNull();
    assertThat(result.authorized()).isTrue();
    assertThat(result.numeroNfse()).isEqualTo("123456");
    assertThat(result.chaveAcessoNfse()).isEqualTo("12345678901234567890123456789012345678901234567890");
    assertThat(result.protocolo()).isEqualTo("PROTO-1");
  }

  // ─── casos novos (assimetrias/riscos documentados na Etapa 25) ────────────

  @Test
  void deveInterpretarRetornoRejeitadoLancandoIllegalState() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    String response = """
        <retorno xmlns="http://www.sped.fazenda.gov.br/nfse">
          <cStat>204</cStat>
          <xMotivo>Rejeitado por erro de schema</xMotivo>
        </retorno>
        """;

    assertThatThrownBy(() -> adapter.parseAuthorizationResponse(invoice, response))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("NFSE_PROVIDER_SEFIN_NACIONAL_REJECTED:");
  }

  @Test
  void deveInterpretarCancelamentoRejeitado() {
    String response = """
        <retorno><cStat>301</cStat><xMotivo>Erro no cancelamento</xMotivo></retorno>
        """;

    assertThatThrownBy(() -> adapter.parseCancellationResponse(response))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("NFSE_PROVIDER_SEFIN_NACIONAL_CANCEL_REJECTED:");
  }

  @Test
  void deveBloquearMetodoDeConsultaDiferenteDeGet() {
    environment.setProperty("app.nfse.provider.sefin-nacional.query-http-method", "POST");
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    invoice.setChaveAcessoNfse("12345678901234567890123456789012345678901234567890");

    assertThatThrownBy(() -> adapter.queryStatus(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_QUERY_METHOD_NOT_SUPPORTED");
  }

  @Test
  void queryStatusVazioQuandoInvoiceSemChaveNemProtocolo() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);

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
  }
}
