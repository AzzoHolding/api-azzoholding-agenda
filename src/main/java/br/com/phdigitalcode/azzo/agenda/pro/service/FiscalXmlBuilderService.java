package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Porte verbatim de {@code modules/fiscal/application/FiscalXmlBuilderService.java}.
 *
 * <p>Gera o XML fiscal MVP (estrutura minima, nao o schema NF-e/NFC-e completo da SEFAZ) e valida
 * contra o XSD normativo do projeto ({@code fiscal/xsd/fiscal-invoice-minimal.xsd}, copiado
 * verbatim do Quarkus original para {@code src/main/resources}).
 */
@Service
public class FiscalXmlBuilderService {

  private static final Logger LOG = LoggerFactory.getLogger(FiscalXmlBuilderService.class);
  private static final String XSD_RESOURCE = "/fiscal/xsd/fiscal-invoice-minimal.xsd";

  public String buildAndValidate(FiscalDtos.Invoice invoice) {
    if (invoice == null) throw new IllegalArgumentException("Invoice obrigatoria para gerar XML fiscal.");
    String xml = build(invoice);
    validate(xml);
    return xml;
  }

  private String build(FiscalDtos.Invoice invoice) {
    String invoiceId = safe(invoice.id);
    String type = safe(invoice.type);
    String customerName = safe(invoice.customer != null ? invoice.customer.name : null);
    int itemsCount = invoice.items != null ? invoice.items.size() : 0;
    long totalAmount = invoice.totalAmount;

    // XML minimo normativo para validacao estrutural MVP.
    return "<fiscalInvoice>"
        + "<id>" + escapeXml(invoiceId) + "</id>"
        + "<type>" + escapeXml(type) + "</type>"
        + "<customerName>" + escapeXml(customerName) + "</customerName>"
        + "<itemsCount>" + itemsCount + "</itemsCount>"
        + "<totalAmount>" + totalAmount + "</totalAmount>"
        + "</fiscalInvoice>";
  }

  private void validate(String xml) {
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Schema schema = factory.newSchema(FiscalXmlBuilderService.class.getResource(XSD_RESOURCE));
      Validator validator = schema.newValidator();
      validator.validate(new StreamSource(new StringReader(xml)));
    } catch (SAXException e) {
      throw new IllegalArgumentException("XML fiscal invalido para o schema MVP.", e);
    } catch (Exception e) {
      LOG.error("Falha ao validar XML fiscal contra schema MVP.");
      throw new IllegalStateException("Falha na validacao estrutural de XML fiscal.", e);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private String escapeXml(String value) {
    if (value == null || value.isBlank()) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
