package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Espelha {@code modules/nfse/application/xml/AbstractXmlLayoutBuilder.java} — base comum dos
 * builders de layout XML de NFS-e (ABRASF, SEFIN Nacional). Renomeada de {@code
 * AbstractXmlLayoutBuilder} para {@code AbstractNfseXmlLayoutBuilder} apenas porque o pacote flat
 * do Spring nao tem o subpacote {@code xml} do original (mesmo nome geraria confusao com outros
 * builders do projeto) — comportamento identico, sem desvio funcional.
 *
 * <p>{@code validateWellFormed} so verifica boa formacao XML (parse DOM basico) — nao valida
 * contra o XSD oficial ABRASF/SEFIN Nacional, mesma limitacao do original (achado 6 da Etapa 25).
 */
abstract class AbstractNfseXmlLayoutBuilder implements NfseXmlLayoutBuilder {

  protected void validateWellFormed(String xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setExpandEntityReferences(false);
      factory.setXIncludeAware(false);
      Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      if (document == null || document.getDocumentElement() == null) {
        throw new IllegalArgumentException("XML NFS-e invalido.");
      }
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("XML NFS-e invalido.", ex);
    }
  }

  protected String escape(String value) {
    if (value == null || value.isBlank()) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
