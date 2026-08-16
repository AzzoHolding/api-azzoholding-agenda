package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Cobre {@code modules/nfse/application/NfseXmlSignerService.java} — assinatura XMLDSig real.
 *
 * <p>Diferente de {@link FiscalXmlSignerServiceTest} (que so verifica um digest SHA-256), este
 * teste <b>valida criptograficamente</b> a assinatura produzida: usa o mesmo certificado PKCS12 de
 * teste real ({@code /fiscal/certificado-teste.p12}, ja usado por {@link
 * FiscalCertificateServiceTest}), extrai a chave privada/certificado exatamente como {@link
 * FiscalCertificateService} faria, e entao usa {@code javax.xml.crypto.dsig.XMLSignature.validate}
 * (a mesma API que um verificador real usaria) para provar que:
 *
 * <ul>
 *   <li>a assinatura devolvida valida com sucesso contra a chave publica do certificado;
 *   <li>qualquer adulteracao do conteudo assinado invalida a assinatura (prova que a assinatura
 *       cobre o conteudo real, nao e um envelope decorativo).
 * </ul>
 *
 * <p>{@link FiscalCertificateService} e mockado apenas na fronteira de persistencia/senha (a
 * responsabilidade dele nesta fronteira e so entregar {@code KeyMaterial}) — o material de chave
 * devolvido pelo mock e o mesmo extraido de verdade do PKCS12, entao a criptografia exercitada e
 * real de ponta a ponta.
 */
class NfseXmlSignerServiceTest {

  private static final String CERT_PASSWORD = "senha-teste-123";

  private FiscalCertificateService fiscalCertificateService;
  private NfseXmlSignerService service;
  private FiscalCertificateService.KeyMaterial keyMaterial;

  @BeforeEach
  void setUp() throws Exception {
    keyMaterial = loadKeyMaterialFromTestFixture();
    fiscalCertificateService = mock(FiscalCertificateService.class);
    service = new NfseXmlSignerService(fiscalCertificateService);
  }

  @Test
  void assinaOXmlComXmlDsigRealEAAssinaturaValidaContraOCertificado() throws Exception {
    when(fiscalCertificateService.loadActiveKeyMaterial(eq(CERT_PASSWORD))).thenReturn(keyMaterial);
    String xml = "<InfDeclaracaoPrestacaoServico><Rps><Numero>1</Numero></Rps></InfDeclaracaoPrestacaoServico>";

    String assinado = service.sign(xml, CERT_PASSWORD);

    assertThat(assinado).contains("<ds:Signature");
    assertThat(assinado).contains("<ds:X509Certificate>");
    assertThat(validateSignature(assinado)).isTrue();
  }

  @Test
  void assinaturaCobreOConteudoReal_adulteracaoInvalidaAAssinatura() throws Exception {
    when(fiscalCertificateService.loadActiveKeyMaterial(eq(CERT_PASSWORD))).thenReturn(keyMaterial);
    String xml = "<InfDeclaracaoPrestacaoServico><Rps><Numero>1</Numero></Rps></InfDeclaracaoPrestacaoServico>";

    String assinado = service.sign(xml, CERT_PASSWORD);
    String adulterado = assinado.replaceFirst("<Numero>1</Numero>", "<Numero>999999</Numero>");

    assertThat(validateSignature(assinado)).isTrue();
    assertThat(validateSignature(adulterado)).isFalse();
  }

  @Test
  void usaOIdDaTagInfDpsQuandoPresente_layoutSefinNacional() throws Exception {
    when(fiscalCertificateService.loadActiveKeyMaterial(eq(CERT_PASSWORD))).thenReturn(keyMaterial);
    String xml = "<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.00\">"
        + "<infDPS><tpAmb>2</tpAmb></infDPS></DPS>";

    String assinado = service.sign(xml, CERT_PASSWORD);

    assertThat(assinado).contains("Id=\"Rps");
    assertThat(validateSignature(assinado)).isTrue();
  }

  @Test
  void xmlNuloOuVazioLancaIllegalArgument() {
    assertThatThrownBy(() -> service.sign(null, CERT_PASSWORD))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("XML NFS-e obrigatorio para assinatura.");
    assertThatThrownBy(() -> service.sign("   ", CERT_PASSWORD))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("XML NFS-e obrigatorio para assinatura.");
  }

  @Test
  void segredoNuloOuVazioLancaIllegalArgument() {
    assertThatThrownBy(() -> service.sign("<nfse/>", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Assinatura NFS-e requer segredo de sessao/certificado.");
    assertThatThrownBy(() -> service.sign("<nfse/>", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Assinatura NFS-e requer segredo de sessao/certificado.");
  }

  @Test
  void falhaAoCarregarChaveEEmbrulhadaComoNfseXmlSigningFailed() {
    when(fiscalCertificateService.loadActiveKeyMaterial(eq(CERT_PASSWORD)))
        .thenThrow(new IllegalArgumentException("NFSE_CERTIFICATE_PASSWORD_INVALID"));

    assertThatThrownBy(() -> service.sign("<nfse/>", CERT_PASSWORD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("NFSE_XML_SIGNING_FAILED:");
  }

  @Test
  void xmlMalformadoLancaNfseXmlSigningFailed() {
    when(fiscalCertificateService.loadActiveKeyMaterial(eq(CERT_PASSWORD))).thenReturn(keyMaterial);

    assertThatThrownBy(() -> service.sign("<nao-fecha>", CERT_PASSWORD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("NFSE_XML_SIGNING_FAILED:");
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  /**
   * Reproduz exatamente {@code FiscalCertificateService.extractKeyMaterial} sobre a mesma fixture
   * real, para obter chave privada + certificado X509 reais sem depender de mocks de criptografia.
   */
  private FiscalCertificateService.KeyMaterial loadKeyMaterialFromTestFixture() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream input = getClass().getResourceAsStream("/fiscal/certificado-teste.p12")) {
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

  private boolean validateSignature(String signedXml) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    Document document = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(signedXml)));

    NodeList signatureNodes =
        document.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature");
    assertThat(signatureNodes.getLength()).isEqualTo(1);
    Element signatureElement = (Element) signatureNodes.item(0);

    XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
    DOMValidateContext validateContext =
        new DOMValidateContext(keyMaterial.certificate().getPublicKey(), signatureElement);
    // O provedor JSR-105 do JDK bloqueia RSA-SHA1 quando "secure validation" esta ligado (padrao
    // desde Java 17, por causa de colisoes conhecidas de SHA-1). O ORIGINAL Quarkus assina com
    // RSA-SHA1 de proposito (preservado 1:1 por instrucao do briefing, nao e para "consertar" o
    // algoritmo aqui) — entao o teste, que so verifica se a assinatura PRODUZIDA e criptografica-
    // mente valida, desliga essa trava apenas do lado do validador de teste. Isso nao afeta o
    // algoritmo usado em producao (continua RSA-SHA1, definido em NfseXmlSignerService).
    validateContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
    // O parser generico nao sabe que "Id" e um atributo do tipo ID (isso normalmente vem de um
    // DTD/XSD); registramos manualmente aqui para que a resolucao da Reference "#valor" funcione,
    // reproduzindo o que NfseXmlSignerService.ensureReferenceId faz do lado da assinatura via
    // Element.setIdAttribute.
    registerIdAttributes(document.getDocumentElement(), validateContext);
    XMLSignature signature = factory.unmarshalXMLSignature(validateContext);
    return signature.validate(validateContext);
  }

  private void registerIdAttributes(Element element, DOMValidateContext validateContext) {
    if (element.hasAttribute("Id")) {
      validateContext.setIdAttributeNS(element, null, "Id");
    }
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element child) {
        registerIdAttributes(child, validateContext);
      }
    }
  }
}
