package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * Porte verbatim de {@code modules/nfse/application/NfseXmlSignerService.java}.
 *
 * <p>⚠️ <b>Assinatura XMLDSig real</b> — diferente do MVP de {@link FiscalXmlSignerService} (que
 * so faz um digest SHA-256 sem tocar a chave privada). Aqui a assinatura usa {@code
 * javax.xml.crypto.dsig} de verdade: enveloped signature + canonicalizacao inclusiva + {@code
 * RSA-SHA1} + {@code KeyInfo}/{@code X509Data} com o certificado, aplicada com a chave privada
 * real extraida do PFX via {@link FiscalCertificateService#loadActiveKeyMaterial(String)} — o
 * mesmo parsing PKCS12 real ja portado para {@code fiscal}, reusado aqui sem duplicacao. Nunca
 * simplifique isto para um digest — e exatamente a distincao de risco que separa este modulo de
 * {@code fiscal}.
 */
@Service
public class NfseXmlSignerService {
  private static final Logger LOG = LoggerFactory.getLogger(NfseXmlSignerService.class);

  private final FiscalCertificateService fiscalCertificateService;

  public NfseXmlSignerService(FiscalCertificateService fiscalCertificateService) {
    this.fiscalCertificateService = fiscalCertificateService;
  }

  public String sign(String xml, String signingSecret) {
    if (xml == null || xml.isBlank()) throw new IllegalArgumentException("XML NFS-e obrigatorio para assinatura.");
    if (signingSecret == null || signingSecret.isBlank()) {
      throw new IllegalArgumentException("Assinatura NFS-e requer segredo de sessao/certificado.");
    }
    try {
      return signWithXmlDsig(xml, signingSecret);
    } catch (Exception ex) {
      LOG.error("NfseXmlSignerService falhou ao assinar XML com certificado digital.", ex);
      throw new IllegalStateException("NFSE_XML_SIGNING_FAILED: " + ex.getMessage(), ex);
    }
  }

  private String signWithXmlDsig(String xml, String certificatePassword) throws Exception {
    FiscalCertificateService.KeyMaterial keyMaterial = fiscalCertificateService.loadActiveKeyMaterial(certificatePassword);
    Document document = parseXml(xml);
    Element root = document.getDocumentElement();
    if (root == null) throw new IllegalArgumentException("XML NFS-e invalido para assinatura.");

    String referenceId = ensureReferenceId(root);
    XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
    List<Transform> transforms = List.of(
        factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
        factory.newTransform(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null));
    Reference ref = factory.newReference(
        "#" + referenceId,
        factory.newDigestMethod(DigestMethod.SHA1, null),
        transforms,
        null,
        null);

    SignedInfo signedInfo = factory.newSignedInfo(
        factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
        factory.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
        List.of(ref));

    KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
    X509Data x509Data = keyInfoFactory.newX509Data(List.of(keyMaterial.certificate()));
    KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(x509Data));

    DOMSignContext signContext = new DOMSignContext(keyMaterial.privateKey(), root);
    signContext.setDefaultNamespacePrefix("ds");
    XMLSignature signature = factory.newXMLSignature(signedInfo, keyInfo);
    signature.sign(signContext);

    return toXmlString(document);
  }

  private Document parseXml(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setExpandEntityReferences(false);
    factory.setXIncludeAware(false);
    return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
  }

  private String ensureReferenceId(Element root) {
    Element target = root;
    var infRpsNodes = root.getElementsByTagNameNS("*", "InfDeclaracaoPrestacaoServico");
    if (infRpsNodes != null && infRpsNodes.getLength() > 0 && infRpsNodes.item(0) instanceof Element element) {
      target = element;
    }
    var infDpsNodes = root.getElementsByTagNameNS("*", "infDPS");
    if (infDpsNodes != null && infDpsNodes.getLength() > 0 && infDpsNodes.item(0) instanceof Element element) {
      target = element;
    }
    var infPedRegNodes = root.getElementsByTagNameNS("*", "infPedReg");
    if (infPedRegNodes != null && infPedRegNodes.getLength() > 0 && infPedRegNodes.item(0) instanceof Element element) {
      target = element;
    }
    String id = target.getAttribute("Id");
    if (id == null || id.isBlank()) {
      id = "Rps" + UUID.randomUUID().toString().replace("-", "");
      target.setAttribute("Id", id);
    }
    target.setIdAttribute("Id", true);
    return id;
  }

  private String toXmlString(Document document) throws Exception {
    TransformerFactory tf = TransformerFactory.newInstance();
    tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    var transformer = tf.newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty(OutputKeys.INDENT, "no");
    StringWriter writer = new StringWriter();
    transformer.transform(new DOMSource(document), new StreamResult(writer));
    return writer.toString();
  }
}
