package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/** Cobre {@code modules/fiscal/application/FiscalSefazClient.java}. */
@ExtendWith(MockitoExtension.class)
class FiscalSefazClientTest {

  @Mock private FiscalProvider fiscalProvider;
  private final UUID tenantId = UUID.randomUUID();

  @Test
  void comProviderMockDelegaDiretoSemExigirHttps() {
    FiscalSefazClient client =
        new FiscalSefazClient(fiscalProvider, "mock", "http://localhost:9088/api/v1/fiscal");
    FiscalDtos.Invoice autorizada = new FiscalDtos.Invoice();
    autorizada.status = "AUTHORIZED";
    when(fiscalProvider.autorizarInvoice(tenantId, "inv-1")).thenReturn(autorizada);

    FiscalDtos.Invoice resultado = client.autorizarInvoice(tenantId, "inv-1");

    assertThat(resultado.status).isEqualTo("AUTHORIZED");
    verify(fiscalProvider).autorizarInvoice(tenantId, "inv-1");
  }

  @Test
  void comProviderRealEUrlHttpLancaIllegalState() {
    FiscalSefazClient client =
        new FiscalSefazClient(fiscalProvider, "real", "http://localhost:9088/api/v1/fiscal");

    assertThatThrownBy(() -> client.autorizarInvoice(tenantId, "inv-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("FiscalSefazClient exige HTTPS quando app.fiscal.provider=real.");
  }

  @Test
  void comProviderRealEUrlHttpsDelegaNormalmente() {
    FiscalSefazClient client =
        new FiscalSefazClient(fiscalProvider, "REAL", "https://sefaz.provider.com/api/v1/fiscal");
    FiscalDtos.Invoice autorizada = new FiscalDtos.Invoice();
    when(fiscalProvider.autorizarInvoice(tenantId, "inv-1")).thenReturn(autorizada);

    FiscalDtos.Invoice resultado = client.autorizarInvoice(tenantId, "inv-1");

    assertThat(resultado).isSameAs(autorizada);
  }

  @Test
  void comProviderRealEUrlNulaLancaIllegalState() {
    FiscalSefazClient client = new FiscalSefazClient(fiscalProvider, "real", null);

    assertThatThrownBy(() -> client.autorizarInvoice(tenantId, "inv-1"))
        .isInstanceOf(IllegalStateException.class);
  }
}
