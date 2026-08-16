package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;

/**
 * Porte verbatim de {@code modules/nfse/application/NfseXmlBuilderService.java}. Resolve o {@link
 * NfseXmlLayoutBuilder} correto (ABRASF ou SEFIN Nacional) pelo {@code provedor} da invoice.
 *
 * <p>O original injeta via CDI {@code Instance<NfseXmlLayoutBuilder>}; aqui o Spring injeta
 * {@code List<NfseXmlLayoutBuilder>} diretamente no construtor (equivalente — todos os beans que
 * implementam a interface).
 */
@Service
public class NfseXmlBuilderService {

  private final List<NfseXmlLayoutBuilder> builders;

  public NfseXmlBuilderService(List<NfseXmlLayoutBuilder> builders) {
    this.builders = List.copyOf(builders);
  }

  public String buildAndValidateAuthorizationXml(NfseInvoiceEntity invoice) {
    return resolveBuilder(invoice).buildAndValidateAuthorizationXml(invoice);
  }

  public String buildAuthorizationReturnXml(
      NfseInvoiceEntity invoice,
      String providerCode,
      String providerMessage) {
    return resolveBuilder(invoice).buildAuthorizationReturnXml(invoice, providerCode, providerMessage);
  }

  public String buildCancelReturnXml(NfseInvoiceEntity invoice, String providerCode, String providerMessage) {
    return resolveBuilder(invoice).buildCancelReturnXml(invoice, providerCode, providerMessage);
  }

  private NfseXmlLayoutBuilder resolveBuilder(NfseInvoiceEntity invoice) {
    String providerCode = invoice != null ? invoice.getProvedor() : null;
    return builders.stream()
        .filter(builder -> builder.supports(providerCode))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("NFSE_PROVIDER_NOT_SUPPORTED"));
  }
}
