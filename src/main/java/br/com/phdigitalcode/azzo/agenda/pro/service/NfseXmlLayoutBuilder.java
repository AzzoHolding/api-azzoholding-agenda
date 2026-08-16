package br.com.phdigitalcode.azzo.agenda.pro.service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;

/**
 * Espelha {@code modules/nfse/application/xml/NfseXmlLayoutBuilder.java} — contrato implementado
 * por um builder de layout XML por provedor (ABRASF, SEFIN Nacional). {@link NfseXmlBuilderService}
 * resolve o builder correto via {@link #supports(String)}.
 */
public interface NfseXmlLayoutBuilder {

  boolean supports(String providerCode);

  String buildAndValidateAuthorizationXml(NfseInvoiceEntity invoice);

  String buildAuthorizationReturnXml(NfseInvoiceEntity invoice, String providerCode, String providerMessage);

  String buildCancelReturnXml(NfseInvoiceEntity invoice, String providerCode, String providerMessage);
}
