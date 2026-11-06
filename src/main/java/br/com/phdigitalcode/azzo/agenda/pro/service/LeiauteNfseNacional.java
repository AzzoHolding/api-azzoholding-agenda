package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.InputStream;
import java.io.StringReader;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.SAXParseException;

/**
 * Confere o XML contra os esquemas OFICIAIS da NFS-e nacional antes de enviar.
 *
 * <p>Os XSD em {@code resources/nfse/xsd/sefin-nacional-1.01} sao os da Receita (pacote de producao
 * restrita de 20/10/2026, leiaute da NT SE/CGNFS-e 009, versao 1.01), copiados sem alteracao.
 * Antes, o sistema so conferia se o XML era bem formado, e a primeira conferencia de leiaute era a
 * recusa da Receita (E1235, falha de esquema).
 *
 * <p>Os includes entre os esquemas sao resolvidos pelo nome do arquivo, dentro da mesma pasta do
 * classpath: nada e buscado fora da aplicacao.
 */
final class LeiauteNfseNacional {

  static final String VERSAO = "1.01";
  private static final String PASTA = "nfse/xsd/sefin-nacional-1.01/";
  private static final ConcurrentHashMap<String, Schema> ESQUEMAS = new ConcurrentHashMap<>();

  private LeiauteNfseNacional() {}

  /** DPS sem assinatura (a assinatura e opcional no esquema e entra depois). */
  static void validarDps(String xml) {
    validar(xml, "DPS_v1.01.xsd", "NFSE_DPS_FORA_DO_LEIAUTE");
  }

  static void validarPedidoDeEvento(String xml) {
    validar(xml, "pedRegEvento_v1.01.xsd", "NFSE_EVENTO_FORA_DO_LEIAUTE");
  }

  private static void validar(String xml, String esquema, String codigo) {
    try {
      ESQUEMAS.computeIfAbsent(esquema, LeiauteNfseNacional::carregar)
          .newValidator()
          .validate(new StreamSource(new StringReader(xml)));
    } catch (SAXParseException ex) {
      throw new IllegalArgumentException(codigo + ": " + ex.getMessage(), ex);
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException(codigo + ": " + ex.getMessage(), ex);
    }
  }

  private static Schema carregar(String esquema) {
    try {
      SchemaFactory fabrica = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      DOMImplementationLS ls =
          (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
      fabrica.setResourceResolver((type, namespace, publicId, systemId, baseUri) -> {
        if (systemId == null) return null;
        String nome = systemId.substring(systemId.lastIndexOf('/') + 1);
        InputStream conteudo = abrir(nome);
        LSInput entrada = ls.createLSInput();
        entrada.setByteStream(conteudo);
        entrada.setSystemId(nome);
        entrada.setPublicId(publicId);
        return entrada;
      });
      // Com o resolvedor acima, todo include vem do classpath; acesso externo fica fechado.
      fabrica.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      fabrica.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      StreamSource raiz = new StreamSource(abrir(esquema), esquema);
      return fabrica.newSchema(raiz);
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Falha ao carregar o esquema oficial da NFS-e: " + esquema, ex);
    }
  }

  private static InputStream abrir(String nome) {
    InputStream conteudo = LeiauteNfseNacional.class.getClassLoader().getResourceAsStream(PASTA + nome);
    if (conteudo == null) {
      throw new IllegalStateException("Esquema oficial da NFS-e ausente: " + PASTA + nome);
    }
    return conteudo;
  }
}
