package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCustomerType;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceItemRepository;

/**
 * Cobre {@code modules/nfse/application/xml/SefinNacionalXmlLayoutBuilder.java} — porta os 2 casos
 * do original ({@code SefinNacionalXmlLayoutBuilderUnitTest}) e adiciona casos de borda
 * (EXTERIOR/CNPJ do tomador, dedução, ambiente de producao).
 *
 * <p>Armadilha 6 do briefing: os repositories tem metodos {@code default}
 * ({@code findByTenantAndAmbiente}/{@code listByTenantAndInvoice}) que delegam para o metodo
 * concreto do Spring Data — e o {@code default} que a producao efetivamente chama. Os stubs abaixo
 * miram sempre o metodo {@code default} invocado de verdade, porque o mock do Mockito NAO executa
 * o corpo do metodo default (ele intercepta a chamada como qualquer outra e devolve o valor padrao
 * se nao houver stub para ela) — estubar o metodo abstrato por baixo (ex.:
 * {@code findByTenantIdAndAmbiente}) nunca seria consultado e o teste "provaria" o comportamento
 * errado.
 */
@ExtendWith(MockitoExtension.class)
class SefinNacionalXmlLayoutBuilderTest {

  @Mock private NfseConfigRepository nfseConfigRepository;
  @Mock private NfseInvoiceItemRepository nfseInvoiceItemRepository;
  @Mock private FiscalTaxConfigRepository fiscalTaxConfigRepository;

  private SefinNacionalXmlLayoutBuilder builder;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    builder = new SefinNacionalXmlLayoutBuilder(nfseConfigRepository, nfseInvoiceItemRepository, fiscalTaxConfigRepository);
    tenantId = UUID.randomUUID();
  }

  @Test
  void suportaApenasSefinNacional() {
    assertThat(builder.supports("SEFIN_NACIONAL")).isTrue();
    assertThat(builder.supports("sefin_nacional")).isTrue();
    assertThat(builder.supports("ABRASF")).isFalse();
    assertThat(builder.supports(null)).isFalse();
  }

  @Test
  void deveGerarDpsMinimaParaReceitaNacional() {
    NfseInvoiceEntity invoice = invoiceBase();
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.00\">");
    assertThat(xml).contains("<infDPS Id=\"DPS");
    assertThat(xml).contains("<tpAmb>2</tpAmb>");
    assertThat(xml).contains("<cTribNac>010101</cTribNac>");
    assertThat(xml).contains("<xDescServ>Corte masculino</xDescServ>");
    assertThat(xml).contains("<opSimpNac>3</opSimpNac>");
    assertThat(xml).contains("<regEspTrib>0</regEspTrib>");
    assertThat(xml).contains("<tpRetISSQN>1</tpRetISSQN>");
    assertThat(xml).contains("<indTotTrib>0</indTotTrib>");
  }

  @Test
  void ambienteProducaoGeraTpAmb1() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setAmbiente(NfseAmbiente.PRODUCAO);
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<tpAmb>1</tpAmb>");
  }

  @Test
  void tomadorExteriorSemDocumentoGeraCNaoNIF() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setCustomerType(NfseCustomerType.EXTERIOR);
    invoice.setCustomerDocument(null);
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<cNaoNIF>0</cNaoNIF>");
    assertThat(xml).contains("<tribISSQN>2</tribISSQN>");
  }

  @Test
  void tomadorPessoaJuridicaGeraTagCnpj() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setCustomerType(NfseCustomerType.CNPJ);
    invoice.setCustomerDocument("12345678000199");
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<CNPJ>12345678000199</CNPJ>");
  }

  @Test
  void deducaoPositivaGeraTagVDedRed() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setValorDeducoes(new BigDecimal("10.00"));
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<vDedRed><vDR>10.00</vDR></vDedRed>");
  }

  @Test
  void deveFalharQuandoCodigoTributacaoNacionalNaoEstiverDisponivel() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setNationalTaxCode(null);
    NfseConfigEntity config = configBase();
    config.setNationalTaxCodeDefault(null);
    NfseInvoiceItemEntity item = itemBase();
    item.setNationalTaxCode(null);
    stubRepositories(invoice, config, taxConfigBase(), List.of(item));

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_CTRIBNAC_REQUIRED");
  }

  @Test
  void configAusenteLancaConfigMissing() {
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoiceBase()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_CONFIG_MISSING");
  }

  @Test
  void semItensLancaItemsRequired() {
    NfseInvoiceEntity invoice = invoiceBase();
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(configBase()));
    when(fiscalTaxConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.of(taxConfigBase()));
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoice.getId()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_ITEMS_REQUIRED");
  }

  private void stubRepositories(
      NfseInvoiceEntity invoice,
      NfseConfigEntity config,
      FiscalTaxConfigEntity taxConfig,
      List<NfseInvoiceItemEntity> items) {
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, invoice.getAmbiente())).thenReturn(Optional.of(config));
    when(fiscalTaxConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.of(taxConfig));
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoice.getId())).thenReturn(items);
  }

  private NfseInvoiceEntity invoiceBase() {
    NfseInvoiceEntity entity = new NfseInvoiceEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setAmbiente(NfseAmbiente.HOMOLOGACAO);
    entity.setProvedor("SEFIN_NACIONAL");
    entity.setNumeroRps(123L);
    entity.setSerieRps("A1");
    entity.setCustomerType(NfseCustomerType.CPF);
    entity.setCustomerDocument("12345678901");
    entity.setCustomerName("Cliente Teste");
    entity.setCustomerEmail("cliente@teste.com");
    entity.setCustomerPhone("11999999999");
    entity.setMunicipioCodigoIbge("3304557");
    entity.setLocalPrestacaoCodigoIbge("3304557");
    entity.setDataCompetencia(LocalDate.of(2026, 3, 14));
    entity.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
    entity.setNaturezaOperacao("Prestacao de servico");
    entity.setItemListaServico("0101");
    entity.setNationalTaxCode("010101");
    entity.setNbsCode("123456789");
    entity.setValorServicos(new BigDecimal("100.00"));
    entity.setValorDeducoes(BigDecimal.ZERO);
    entity.setAliquotaIss(new BigDecimal("5.00"));
    entity.setValorIss(new BigDecimal("5.00"));
    entity.setIssRetido(false);
    return entity;
  }

  private NfseInvoiceItemEntity itemBase() {
    NfseInvoiceItemEntity item = new NfseInvoiceItemEntity();
    item.setId(UUID.randomUUID());
    item.setTenantId(tenantId);
    item.setInvoiceId(UUID.randomUUID());
    item.setLineNumber(1);
    item.setDescricaoServico("Corte masculino");
    item.setQuantidade(BigDecimal.ONE);
    item.setValorUnitario(new BigDecimal("100.00"));
    item.setValorTotal(new BigDecimal("100.00"));
    item.setItemListaServico("0101");
    item.setCodigoTributacaoMunicipio("0101");
    item.setNationalTaxCode("010101");
    item.setNbsCode("123456789");
    item.setAliquotaIss(new BigDecimal("5.00"));
    item.setValorIss(new BigDecimal("5.00"));
    return item;
  }

  private NfseConfigEntity configBase() {
    NfseConfigEntity config = new NfseConfigEntity();
    config.setId(UUID.randomUUID());
    config.setTenantId(tenantId);
    config.setAmbiente(NfseAmbiente.HOMOLOGACAO);
    config.setProvedor("SEFIN_NACIONAL");
    config.setMunicipioCodigoIbge("3304557");
    config.setSerieRps("A1");
    config.setApplicationVersion("AZZO-1.0");
    config.setNationalTaxCodeDefault("010101");
    config.setNbsCodeDefault("123456789");
    config.setSimplesNacionalSituacao("3");
    config.setSimplesNacionalRegimeTributacao("1");
    config.setEspecialRegimeTributacao("0");
    return config;
  }

  private FiscalTaxConfigEntity taxConfigBase() {
    FiscalTaxConfigEntity taxConfig = new FiscalTaxConfigEntity();
    taxConfig.setId(UUID.randomUUID());
    taxConfig.setTenantId(tenantId);
    taxConfig.setIssuerCnpj("12345678000199");
    taxConfig.setIssuerIm("1234567");
    taxConfig.setIssuerRazaoSocial("Salao QA Ltda");
    taxConfig.setIssuerPhone("1133334444");
    taxConfig.setIssuerEmail("fiscal@salaoqa.com");
    return taxConfig;
  }
}
