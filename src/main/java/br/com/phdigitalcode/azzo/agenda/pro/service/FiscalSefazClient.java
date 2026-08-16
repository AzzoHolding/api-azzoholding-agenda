package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Porte verbatim de {@code modules/fiscal/application/FiscalSefazClient.java}.
 *
 * <p>Fachada de autorização perante a SEFAZ. Delega para {@link FiscalProvider} — o mock em
 * desenvolvimento, um provider real quando {@code app.fiscal.provider} apontar para outra
 * implementação. A única regra própria desta classe é a guarda de canal seguro: com
 * {@code app.fiscal.provider=real}, a URL do provider tem que ser HTTPS, senão a chamada nem
 * acontece.
 *
 * <p>{@code quarkus.rest-client.fiscal-provider-api.url} do original virou
 * {@code app.fiscal.provider-api.url} (mesma env var {@code FISCAL_PROVIDER_BASE_URL}) — o REST
 * client em si ({@code FiscalProviderClient}) não foi portado nesta fronteira (não é dependência
 * direta de {@code ServicoFiscal}/{@code MockFiscalProvider}); a propriedade existe aqui só para a
 * guarda de {@link #validarCanalSeguro()}.
 */
@Service
public class FiscalSefazClient {

  private final FiscalProvider fiscalProvider;
  private final String providerType;
  private final String fiscalProviderUrl;

  public FiscalSefazClient(
      FiscalProvider fiscalProvider,
      @Value("${app.fiscal.provider:mock}") String providerType,
      @Value("${app.fiscal.provider-api.url:http://localhost:9088/api/v1/fiscal}") String fiscalProviderUrl) {
    this.fiscalProvider = fiscalProvider;
    this.providerType = providerType;
    this.fiscalProviderUrl = fiscalProviderUrl;
  }

  public FiscalDtos.Invoice autorizarInvoice(UUID tenantId, String invoiceId) {
    validarCanalSeguro();
    return fiscalProvider.autorizarInvoice(tenantId, invoiceId);
  }

  private void validarCanalSeguro() {
    String provider = providerType == null ? "" : providerType.trim().toLowerCase(Locale.ROOT);
    if (!"real".equals(provider)) return;
    if (fiscalProviderUrl == null || !fiscalProviderUrl.toLowerCase(Locale.ROOT).startsWith("https://")) {
      throw new IllegalStateException("FiscalSefazClient exige HTTPS quando app.fiscal.provider=real.");
    }
  }
}
