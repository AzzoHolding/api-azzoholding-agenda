package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;

/**
 * Cobre {@code modules/nfse/application/NfseProviderRouterService.java} (Fronteira 6, ver
 * {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/27). O original resolve o adapter certo
 * (ABRASF/SEFIN_NACIONAL, hoje portados na Fronteira 5) pelo codigo do provedor, com alias
 * ABRASF/ABRASF_204 nos dois sentidos e bloqueio explicito de MOCK_NACIONAL.
 */
class NfseProviderRouterServiceTest {

  @Test
  void resolve_codigoConhecido_retornaAdapter() {
    StubAdapter abrasf = new StubAdapter("ABRASF");
    NfseProviderRouterService router = new NfseProviderRouterService(List.of(abrasf));

    assertThat(router.resolve("abrasf")).isSameAs(abrasf);
  }

  @Test
  void resolve_codigoAusente_lancaExcecao() {
    NfseProviderRouterService router = new NfseProviderRouterService(List.of());

    assertThatThrownBy(() -> router.resolve(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_PROVIDER_MISSING");
    assertThatThrownBy(() -> router.resolve(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_PROVIDER_MISSING");
  }

  @Test
  void resolve_mockNacional_sempreBloqueado_mesmoSeAdapterExistisse() {
    StubAdapter mockNacional = new StubAdapter("MOCK_NACIONAL");
    NfseProviderRouterService router = new NfseProviderRouterService(List.of(mockNacional));

    assertThatThrownBy(() -> router.resolve("MOCK_NACIONAL"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_PROVIDER_MOCK_NOT_ALLOWED");
  }

  @Test
  void resolve_codigoNaoSuportado_lancaExcecao() {
    NfseProviderRouterService router = new NfseProviderRouterService(List.of(new StubAdapter("ABRASF")));

    assertThatThrownBy(() -> router.resolve("DESCONHECIDO"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_PROVIDER_NOT_SUPPORTED");
  }

  @Test
  void resolve_abrasf204_caiParaAbrasfSeNaoCadastradoDiretamente() {
    StubAdapter abrasf = new StubAdapter("ABRASF");
    NfseProviderRouterService router = new NfseProviderRouterService(List.of(abrasf));

    assertThat(router.resolve("ABRASF_204")).isSameAs(abrasf);
  }

  @Test
  void resolve_abrasf_caiParaAbrasf204SeNaoCadastradoDiretamente() {
    StubAdapter abrasf204 = new StubAdapter("ABRASF_204");
    NfseProviderRouterService router = new NfseProviderRouterService(List.of(abrasf204));

    assertThat(router.resolve("ABRASF")).isSameAs(abrasf204);
  }

  @Test
  void construtor_ignoraAdaptersComCodigoNuloOuVazio() {
    StubAdapter semCodigo = new StubAdapter("");
    NfseProviderRouterService router = new NfseProviderRouterService(List.of(semCodigo));

    assertThatThrownBy(() -> router.resolve("ABRASF"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_PROVIDER_NOT_SUPPORTED");
  }

  private static final class StubAdapter implements NfseProviderAdapter {
    private final String code;

    private StubAdapter(String code) {
      this.code = code;
    }

    @Override
    public String providerCode() {
      return code;
    }

    @Override
    public AuthorizationResult authorize(NfseInvoiceEntity invoice, String certificatePassword) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CancellationResult cancel(NfseInvoiceEntity invoice, String reason, String certificatePassword) {
      throw new UnsupportedOperationException();
    }
  }
}
