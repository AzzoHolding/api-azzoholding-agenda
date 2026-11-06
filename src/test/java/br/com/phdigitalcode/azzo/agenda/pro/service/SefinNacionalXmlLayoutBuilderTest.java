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
 * A DPS do Emissor Nacional no leiaute 1.01 (NT SE/CGNFS-e 009).
 *
 * <p>O proprio {@code buildAndValidateAuthorizationXml} confere o XML contra os XSD oficiais da
 * Receita ({@link LeiauteNfseNacional}): todo teste que gera XML com sucesso ja prova que ele passa
 * no esquema oficial. Os casos de regra de negocio vem do Anexo VI (codigos E0xxx citados).
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
  void dpsDoSimplesPassaNoEsquemaOficialDoLeiaute101() {
    NfseInvoiceEntity invoice = invoiceBase();
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).startsWith("<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.01\">");
    assertThat(xml).contains("<tpEmit>1</tpEmit><finNFSe>0</finNFSe><cLocEmi>3304557</cLocEmi>");
    assertThat(xml).contains("<regTrib><opSimpNac>3</opSimpNac><regApTribSN>1</regApTribSN><regEspTrib>0</regEspTrib></regTrib>");
    assertThat(xml).contains("<cTribNac>010101</cTribNac><cNBS>123456789</cNBS><xDescServ>Corte masculino</xDescServ>");
    // ME/EPP: "nao informar" e proibido (E0712); vai o percentual do Simples.
    assertThat(xml).contains("<totTrib><pTotTribSN>6.00</pTotTribSN></totTrib>");
    assertThat(xml).doesNotContain("indTotTrib");
  }

  @Test
  void prestadorQueEmiteNaoLevaNomeNemEndereco() {
    NfseInvoiceEntity invoice = invoiceBase();
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    String prest = xml.substring(xml.indexOf("<prest>"), xml.indexOf("</prest>"));
    // E0121 / E0128: a Receita usa o cadastro do CNPJ.
    assertThat(prest).doesNotContain("<xNome>").doesNotContain("<end>");
    assertThat(prest).contains("<CNPJ>12345678000199</CNPJ><IM>1234567</IM>");
  }

  @Test
  void dataDeEmissaoTemFusoESerieNaoGanhaZerosADireita() {
    NfseInvoiceEntity invoice = invoiceBase();
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).containsPattern("<dhEmi>20\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d-03:00</dhEmi>");
    assertThat(xml).contains("<serie>1</serie>");
    // Id: DPS + municipio + 2 (CNPJ) + CNPJ + serie com zeros a esquerda + numero com zeros a esquerda.
    assertThat(xml).contains("<infDPS Id=\"DPS" + "3304557" + "2" + "12345678000199" + "00001" + "000000000000123" + "\">");
  }

  @Test
  void serieComLetraERecusadaAntesDeEnviar() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setSerieRps("A1");
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_SERIE_INVALIDA");
  }

  @Test
  void aliquotaDoIssSoVaiParaMeEppComIssRetido() {
    NfseInvoiceEntity semRetencao = invoiceBase();
    stubRepositories(semRetencao, configBase(), taxConfigBase(), List.of(itemBase()));
    assertThat(builder.buildAndValidateAuthorizationXml(semRetencao)).doesNotContain("<pAliq>");

    NfseInvoiceEntity comRetencao = invoiceBase();
    comRetencao.setIssRetido(true);
    stubRepositories(comRetencao, configBase(), taxConfigBase(), List.of(itemBase()));
    assertThat(builder.buildAndValidateAuthorizationXml(comRetencao))
        .contains("<tribMun><tribISSQN>1</tribISSQN><tpRetISSQN>2</tpRetISSQN><pAliq>5.00</pAliq></tribMun>");
  }

  @Test
  void regraDaAliquotaSegueOAnexoVI() {
    assertThat(SefinNacionalXmlLayoutBuilder.informaAliquota("3", "1", "0", true)).isTrue();
    assertThat(SefinNacionalXmlLayoutBuilder.informaAliquota("3", "1", "0", false)).isFalse(); // E0625
    assertThat(SefinNacionalXmlLayoutBuilder.informaAliquota("3", "2", "0", true)).isFalse(); // E0635
    assertThat(SefinNacionalXmlLayoutBuilder.informaAliquota("1", null, "0", true)).isFalse(); // E0617
    assertThat(SefinNacionalXmlLayoutBuilder.informaAliquota("2", null, "0", true)).isFalse(); // E0600
    assertThat(SefinNacionalXmlLayoutBuilder.informaAliquota("3", "1", "1", true)).isFalse(); // E0604
  }

  @Test
  void naoOptanteMandaOsPercentuaisFederalEstadualEMunicipal() {
    NfseInvoiceEntity invoice = invoiceBase();
    NfseConfigEntity config = configBase();
    config.setSimplesNacionalSituacao("1");
    config.setPercentualTributosFederais(new BigDecimal("13.45"));
    config.setPercentualTributosEstaduais(BigDecimal.ZERO);
    config.setPercentualTributosMunicipais(new BigDecimal("5"));
    stubRepositories(invoice, config, taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    // E0162: nao optante nao manda regApTribSN, mesmo se estiver na configuracao.
    assertThat(xml).contains("<regTrib><opSimpNac>1</opSimpNac><regEspTrib>0</regEspTrib></regTrib>");
    assertThat(xml).contains(
        "<pTotTrib><pTotTribFed>13.45</pTotTribFed><pTotTribEst>0.00</pTotTribEst><pTotTribMun>5.00</pTotTribMun></pTotTrib>");
  }

  @Test
  void naoOptanteSemPercentuaisNaoSai() {
    NfseInvoiceEntity invoice = invoiceBase();
    NfseConfigEntity config = configBase();
    config.setSimplesNacionalSituacao("1");
    stubRepositories(invoice, config, taxConfigBase(), List.of(itemBase()));

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_PERCENTUAL_TRIBUTOS_REQUIRED");
  }

  @Test
  void meEppSemPercentualDoSimplesNaoSai() {
    NfseInvoiceEntity invoice = invoiceBase();
    NfseConfigEntity config = configBase();
    config.setPercentualTributosSn(null);
    stubRepositories(invoice, config, taxConfigBase(), List.of(itemBase()));

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_PERCENTUAL_TRIBUTOS_SN_REQUIRED");
  }

  @Test
  void meiNaoInformaTotalDeTributos() {
    NfseInvoiceEntity invoice = invoiceBase();
    NfseConfigEntity config = configBase();
    config.setSimplesNacionalSituacao("2");
    stubRepositories(invoice, config, taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<totTrib><indTotTrib>0</indTotTrib></totTrib>");
    assertThat(xml).doesNotContain("<regApTribSN>").doesNotContain("<pAliq>");
  }

  @Test
  void semClassificacaoDoIbsCbsOGrupoNaoVai() {
    NfseInvoiceEntity invoice = invoiceBase();
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    assertThat(builder.buildAndValidateAuthorizationXml(invoice)).doesNotContain("<IBSCBS>");
  }

  @Test
  void comClassificacaoOGrupoIbsCbsVaiSoComOsCodigos() {
    NfseInvoiceEntity invoice = invoiceBase();
    NfseConfigEntity config = configComIbsCbs();
    stubRepositories(invoice, config, taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    // Os valores do IBS e da CBS sao calculados pela Receita: a DPS so classifica.
    assertThat(xml).contains(
        "<IBSCBS><indFinal>1</indFinal><cIndOp>100301</cIndOp><indDest>0</indDest>"
            + "<valores><trib><CST>000</CST><cClassTrib>000001</cClassTrib></trib></valores></IBSCBS></infDPS>");
  }

  @Test
  void tomadorPessoaJuridicaNaoEConsumidorFinal() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setCustomerType(NfseCustomerType.CNPJ);
    invoice.setCustomerDocument("12345678000199");
    stubRepositories(invoice, configComIbsCbs(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<toma><CNPJ>12345678000199</CNPJ>");
    assertThat(xml).contains("<indFinal>0</indFinal>");
  }

  @Test
  void ibsCbsSemNbsNaoSai() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setNbsCode(null);
    NfseInvoiceItemEntity item = itemBase();
    item.setNbsCode(null);
    NfseConfigEntity config = configComIbsCbs();
    config.setNbsCodeDefault(null);
    stubRepositories(invoice, config, taxConfigBase(), List.of(item));

    // E0322: com o grupo IBS/CBS, o NBS e obrigatorio.
    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_NBS_REQUIRED_IBS_CBS");
  }

  @Test
  void deducaoNaoESuportadaNoLeiaute101() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setValorDeducoes(new BigDecimal("10.00"));
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_DEDUCAO_NAO_SUPORTADA");
  }

  @Test
  void campoForaDoEsquemaERecusadoAntesDeEnviar() {
    NfseInvoiceEntity invoice = invoiceBase();
    // cTribMun tem 3 digitos no esquema; o rascunho trouxe 4.
    invoice.setCodigoTributacaoMunicipio("0101");
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("NFSE_DPS_FORA_DO_LEIAUTE:");
  }

  @Test
  void ambienteProducaoGeraTpAmb1() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setAmbiente(NfseAmbiente.PRODUCAO);
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    assertThat(builder.buildAndValidateAuthorizationXml(invoice)).contains("<tpAmb>1</tpAmb>");
  }

  @Test
  void tomadorExteriorSemDocumentoGeraCNaoNIF() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setCustomerType(NfseCustomerType.EXTERIOR);
    invoice.setCustomerDocument(null);
    stubRepositories(invoice, configBase(), taxConfigBase(), List.of(itemBase()));

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<cNaoNIF>0</cNaoNIF>");
    // Antes ia "2" (imunidade) so por o tomador ser do exterior, o que nao e a regra do ISS.
    assertThat(xml).contains("<tribISSQN>1</tribISSQN>");
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
    entity.setSerieRps("1");
    entity.setCustomerType(NfseCustomerType.CPF);
    entity.setCustomerDocument("12345678901");
    entity.setCustomerName("Cliente Teste");
    entity.setCustomerEmail("cliente@teste.com");
    entity.setCustomerPhone("11999999999");
    entity.setMunicipioCodigoIbge("3304557");
    entity.setLocalPrestacaoCodigoIbge("3304557");
    entity.setDataCompetencia(LocalDate.of(2026, 11, 6));
    entity.setCreatedAt(Instant.parse("2026-11-06T10:00:00Z"));
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
    config.setSerieRps("1");
    config.setApplicationVersion("AZZO-1.0");
    config.setNationalTaxCodeDefault("010101");
    config.setNbsCodeDefault("123456789");
    config.setSimplesNacionalSituacao("3");
    config.setSimplesNacionalRegimeTributacao("1");
    config.setEspecialRegimeTributacao("0");
    config.setPercentualTributosSn(new BigDecimal("6.00"));
    return config;
  }

  private NfseConfigEntity configComIbsCbs() {
    NfseConfigEntity config = configBase();
    config.setIbsCbsCst("000");
    config.setIbsCbsClassTrib("000001");
    config.setIbsCbsIndOp("100301");
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
