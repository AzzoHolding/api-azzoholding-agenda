package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalApuracaoMensalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.service.impl.MockFiscalProvider;

/**
 * Teste de telas de 25/10/2026: a config do emitente ficava so na memoria do {@link MockFiscalProvider}
 * (sumia no deploy) e o codigo da UF ficava "35" (SP) para um salao do RJ.
 */
class FiscalTaxConfigPersistenciaTest {

  private final UUID tenantId = UUID.randomUUID();
  private final AtomicReference<FiscalTaxConfigEntity> gravada = new AtomicReference<>();
  private FiscalPersistenceService persistencia;

  @BeforeEach
  void setUp() {
    FiscalTaxConfigRepository repository = mock(FiscalTaxConfigRepository.class);
    when(repository.findByTenantId(tenantId)).thenAnswer(inv -> Optional.ofNullable(gravada.get()));
    when(repository.save(any(FiscalTaxConfigEntity.class)))
        .thenAnswer(
            inv -> {
              gravada.set(inv.getArgument(0));
              return inv.getArgument(0);
            });
    persistencia =
        new FiscalPersistenceService(
            repository,
            mock(FiscalInvoiceRepository.class),
            mock(FiscalApuracaoMensalRepository.class),
            mock(FiscalSequenceService.class),
            mock(EncryptionService.class),
            new ObjectMapper());
  }

  private static FiscalDtos.TaxConfig config(String uf, String codigo) {
    FiscalDtos.TaxConfig c = new FiscalDtos.TaxConfig();
    c.issuerRazaoSocial = "Salao Teste LTDA";
    c.issuerCnpj = "11222333000181";
    c.issuerState = uf;
    c.issuerUfCode = codigo;
    return c;
  }

  @Test
  @DisplayName("o codigo IBGE sai da UF, e nao do valor que a tela devolve")
  void codigoSaiDaUf() {
    persistencia.upsertTaxConfig(tenantId, config("rj", "35"));

    FiscalDtos.TaxConfig lida = persistencia.lerTaxConfig(tenantId);
    assertThat(lida.issuerState).isEqualTo("RJ");
    assertThat(lida.issuerUfCode).isEqualTo("33");
  }

  @Test
  @DisplayName("UF que nao existe e recusada")
  void ufInvalida() {
    assertThatThrownBy(() -> persistencia.upsertTaxConfig(tenantId, config("RJX", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("UF do emitente inválida.");
  }

  @Test
  @DisplayName("o provider grava no banco e le de volta (sobrevive ao deploy)")
  void providerPersiste() {
    MockFiscalProvider provider = new MockFiscalProvider(persistencia);
    provider.atualizarTaxConfig(tenantId, config("SP", null));

    MockFiscalProvider depoisDoDeploy = new MockFiscalProvider(persistencia);
    FiscalDtos.TaxConfig lida = depoisDoDeploy.obterTaxConfig(tenantId);
    assertThat(lida.issuerRazaoSocial).isEqualTo("Salao Teste LTDA");
    assertThat(lida.issuerUfCode).isEqualTo("35");
  }
}
