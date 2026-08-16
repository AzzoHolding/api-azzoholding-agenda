package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.springframework.stereotype.Service;

/**
 * Porte verbatim de {@code modules/fiscal/application/FiscalXmlSignerService.java}.
 *
 * <p>⚠️ <b>Assinatura MVP, não é XMLDSig/ICP-Brasil real</b> — mesmo comportamento do original: um
 * digest SHA-256 do XML combinado com a senha do certificado, sem usar o material de chave do
 * certificado carregado por {@link FiscalCertificateService}. A senha nunca é persistida. Não
 * "melhore" isto silenciosamente; se a assinatura real (XMLDSig com a chave privada do PFX) for
 * necessária, é decisão de produto/arquitetura fora do escopo desta migração — o Quarkus original
 * tem a mesma limitação.
 */
@Service
public class FiscalXmlSignerService {

  public String sign(String xml, String certificatePassword) {
    if (xml == null || xml.isBlank()) {
      throw new IllegalArgumentException("XML fiscal obrigatorio para assinatura.");
    }
    if (certificatePassword == null || certificatePassword.isBlank()) {
      throw new IllegalArgumentException("Senha do certificado obrigatoria para assinatura fiscal.");
    }

    // Assinatura MVP com comprovacao de integridade sem persistencia de senha.
    String digest = digest(xml, certificatePassword);
    return "<signedFiscalInvoice>"
        + "<payload><![CDATA[" + xml + "]]></payload>"
        + "<signature algorithm=\"SHA-256\">" + digest + "</signature>"
        + "</signedFiscalInvoice>";
  }

  private String digest(String xml, String password) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(xml.getBytes(StandardCharsets.UTF_8));
      md.update((byte) '|');
      md.update(password.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(md.digest());
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao assinar XML fiscal.", e);
    }
  }
}
