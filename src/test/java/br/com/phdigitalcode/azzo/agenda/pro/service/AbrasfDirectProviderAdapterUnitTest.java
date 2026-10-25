package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
 * Cobre a logica pura de {@link AbrasfDirectProviderAdapter} — resolucao de URL (3 fallbacks
 * encadeados: DB tenant &gt; config por municipio &gt; config global), parsing de rejeicao SOAP,
 * envelope XML. O original ({@code AbrasfDirectProviderAdapter.java} em
 * {@code modules/nfse/application/provider/}) <b>nao tem nenhum teste</b> — confirmado por {@code
 * find} no Quarkus original (so existe {@code SefinNacionalProviderAdapterUnitTest}, nada de
 * ABRASF). Esta classe e o {@link AbrasfDirectProviderAdapterSoapIntegrationTest} sao cobertura
 * nova, acima da paridade do original, para fechar a fronteira de maior risco do modulo com
 * confianca real.
 */
class AbrasfDirectProviderAdapterUnitTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  private EncryptionService encryptionService;
  private FiscalTaxConfigRepository fiscalTaxConfigRepository;
  private NfseConfigRepository nfseConfigRepository;
  private MockEnvironment environment;
  private AbrasfDirectProviderAdapter adapter;

  @BeforeEach
  void setUp() {
    encryptionService = mock(EncryptionService.class);
    fiscalTaxConfigRepository = mock(FiscalTaxConfigRepository.class);
    nfseConfigRepository = mock(NfseConfigRepository.class);
    environment = new MockEnvironment();
    environment.setProperty("app.nfse.provider.abrasf.default.ws-url", "__unset__");
    environment.setProperty("app.nfse.provider.abrasf.default.ws-url-homologacao", "__unset__");
    environment.setProperty("app.nfse.provider.abrasf.connect-timeout-ms", "30000");
    environment.setProperty("app.nfse.provider.abrasf.read-timeout-ms", "60000");

    adapter = new AbrasfDirectProviderAdapter(
        encryptionService, fiscalTaxConfigRepository, nfseConfigRepository, environment);
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
  void deveResolverUrlDoBancoComPrioridadeSobreConfigProperty() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    NfseConfigEntity config = new NfseConfigEntity();
    config.setWsUrlHomologacao("https://prefeitura-db.example.com/ws");
    when(nfseConfigRepository.findByTenantAndAmbiente(eq(TENANT_ID), eq(NfseAmbiente.HOMOLOGACAO)))
        .thenReturn(Optional.of(config));
    environment.setProperty("app.nfse.provider.abrasf.3550308.ws-url-homologacao", "https://prefeitura-property.example.com/ws");

    String url = adapter.resolveWsUrl(invoice);

    assertThat(url).isEqualTo("https://prefeitura-db.example.com/ws");
  }

  @Test
  void deveResolverUrlPorMunicipioQuandoSemConfigNoBanco() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    when(nfseConfigRepository.findByTenantAndAmbiente(any(), any())).thenReturn(Optional.empty());
    environment.setProperty("app.nfse.provider.abrasf.3550308.ws-url-homologacao", "https://prefeitura-property.example.com/ws");

    String url = adapter.resolveWsUrl(invoice);

    assertThat(url).isEqualTo("https://prefeitura-property.example.com/ws");
  }

  @Test
  void deveResolverUrlGlobalQuandoSemDbESemPropertyPorMunicipio() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    when(nfseConfigRepository.findByTenantAndAmbiente(any(), any())).thenReturn(Optional.empty());
    environment.setProperty("app.nfse.provider.abrasf.default.ws-url-homologacao", "https://global.example.com/ws");

    String url = adapter.resolveWsUrl(invoice);

    assertThat(url).isEqualTo("https://global.example.com/ws");
  }

  @Test
  void deveFalharComMensagemFuncionalQuandoNenhumaFonteDeUrlConfigurada() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    when(nfseConfigRepository.findByTenantAndAmbiente(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.resolveWsUrl(invoice))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("NFSE_PROVIDER_ABRASF_WS_URL_MISSING");
  }

  @Test
  void producaoUsaChavesDeProducaoNaoHomologacao() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.PRODUCAO);
    when(nfseConfigRepository.findByTenantAndAmbiente(any(), any())).thenReturn(Optional.empty());
    environment.setProperty("app.nfse.provider.abrasf.default.ws-url", "https://producao.example.com/ws");
    environment.setProperty("app.nfse.provider.abrasf.default.ws-url-homologacao", "https://homolog.example.com/ws");

    String url = adapter.resolveWsUrl(invoice);

    assertThat(url).isEqualTo("https://producao.example.com/ws");
  }

  @Test
  void deveExigirInvoiceParaAutorizar() {
    assertThatThrownBy(() -> adapter.authorize(null, "senha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_ABRASF_INVOICE_REQUIRED");
  }

  @Test
  void deveExigirMotivoParaCancelar() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);

    assertThatThrownBy(() -> adapter.cancel(invoice, "", "senha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_ABRASF_CANCEL_REASON_REQUIRED");
  }

  @Test
  void queryStatusVazioSemProtocolo() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);

    Optional<?> result = adapter.queryStatus(invoice);

    assertThat(result).isEmpty();
  }

  @Test
  void queryStatusExigeCnpjEInscricaoMunicipalDoPrestador() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    invoice.setProtocolo("PROTO-1");
    when(fiscalTaxConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.queryStatus(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_XML_PRESTADOR_CNPJ_INVALIDO");
  }

  @Test
  void queryStatusExigeInscricaoMunicipalQuandoCnpjPresente() {
    NfseInvoiceEntity invoice = invoice(NfseAmbiente.HOMOLOGACAO);
    invoice.setProtocolo("PROTO-1");
    FiscalTaxConfigEntity tax = new FiscalTaxConfigEntity();
    tax.setIssuerCnpj("12345678000199");
    tax.setIssuerIm(null);
    when(fiscalTaxConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tax));

    assertThatThrownBy(() -> adapter.queryStatus(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_XML_PRESTADOR_IM_AUSENTE");
  }

  // ─── containsRejection / extractFirst (via reflection, mesma tecnica de acesso a metodo
  // privado usada no restante do modulo quando o comportamento nao e observavel por API publica) ──

  @Test
  void containsRejectionDetectaSoapFault() throws Exception {
    boolean rejected = invokeContainsRejection(
        "<soapenv:Envelope><soapenv:Body><soapenv:Fault><faultstring>erro</faultstring></soapenv:Fault></soapenv:Body></soapenv:Envelope>");

    assertThat(rejected).isTrue();
  }

  @Test
  void containsRejectionFalsoQuandoListaMensagemComResultadoUtil() throws Exception {
    boolean rejected = invokeContainsRejection(
        "<RecepcionarLoteRpsResposta><ListaMensagemRetorno><Mensagem>Aviso</Mensagem></ListaMensagemRetorno><NumeroNfse>123</NumeroNfse></RecepcionarLoteRpsResposta>");

    assertThat(rejected).isFalse();
  }

  @Test
  void containsRejectionVerdadeiroQuandoListaMensagemSemResultadoUtil() throws Exception {
    boolean rejected = invokeContainsRejection(
        "<RecepcionarLoteRpsResposta><ListaMensagemRetorno><Mensagem>Erro real</Mensagem></ListaMensagemRetorno></RecepcionarLoteRpsResposta>");

    assertThat(rejected).isTrue();
  }

  @Test
  void containsRejectionVerdadeiroQuandoRespostaVazia() throws Exception {
    assertThat(invokeContainsRejection(null)).isTrue();
    assertThat(invokeContainsRejection("")).isTrue();
  }

  private boolean invokeContainsRejection(String xml) throws Exception {
    var method = AbrasfDirectProviderAdapter.class.getDeclaredMethod("containsRejection", String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(adapter, xml);
  }
}
