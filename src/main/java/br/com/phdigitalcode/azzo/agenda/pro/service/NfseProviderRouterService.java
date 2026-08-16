package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Porte verbatim de {@code modules/nfse/application/NfseProviderRouterService.java}.
 *
 * <p>Resolve o {@link NfseProviderAdapter} pelo codigo do provedor. O original injeta via CDI
 * {@code Instance<NfseProviderAdapter>}; aqui o Spring injeta {@code List<NfseProviderAdapter>}
 * diretamente no construtor — todos os beans que implementam a interface (hoje
 * {@code AbrasfDirectProviderAdapter} e {@code SefinNacionalProviderAdapter}, ambos da Fronteira
 * 5). Faz parte da Fronteira 6 porque so {@code NfseService} o consome.
 */
@Service
public class NfseProviderRouterService {

  private final Map<String, NfseProviderAdapter> adaptersByCode;

  public NfseProviderRouterService(List<NfseProviderAdapter> adapters) {
    this.adaptersByCode = new HashMap<>();
    for (NfseProviderAdapter adapter : adapters) {
      if (adapter == null || adapter.providerCode() == null || adapter.providerCode().isBlank()) continue;
      adaptersByCode.put(adapter.providerCode().trim().toUpperCase(Locale.ROOT), adapter);
    }
  }

  public NfseProviderAdapter resolve(String providerCode) {
    if (providerCode == null || providerCode.isBlank()) {
      throw new IllegalArgumentException("NFSE_PROVIDER_MISSING");
    }
    String normalized = providerCode.trim().toUpperCase(Locale.ROOT);
    if ("MOCK_NACIONAL".equals(normalized)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_MOCK_NOT_ALLOWED");
    }
    NfseProviderAdapter adapter = adaptersByCode.get(normalized);
    if (adapter == null) {
      if ("ABRASF_204".equals(normalized)) {
        adapter = adaptersByCode.get("ABRASF");
      } else if ("ABRASF".equals(normalized)) {
        adapter = adaptersByCode.get("ABRASF_204");
      }
    }
    if (adapter == null) {
      throw new IllegalArgumentException("NFSE_PROVIDER_NOT_SUPPORTED");
    }
    return adapter;
  }
}
