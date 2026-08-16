package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCustomerType;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;

/** Cobre {@code modules/nfse/application/xml/AbrasfXmlLayoutBuilder.java}. */
@ExtendWith(MockitoExtension.class)
class AbrasfXmlLayoutBuilderTest {

  @Mock private FiscalTaxConfigRepository fiscalTaxConfigRepository;
  @Mock private NfseConfigRepository nfseConfigRepository;

  private AbrasfXmlLayoutBuilder builder;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    builder = new AbrasfXmlLayoutBuilder(fiscalTaxConfigRepository, nfseConfigRepository);
    tenantId = UUID.randomUUID();
  }

  @Test
  void suportaApenasAbrasfEAbrasf204() {
    assertThat(builder.supports("ABRASF")).isTrue();
    assertThat(builder.supports("abrasf_204")).isTrue();
    assertThat(builder.supports("SEFIN_NACIONAL")).isFalse();
    assertThat(builder.supports(null)).isFalse();
  }

  @Test
  void deveGerarXmlAutorizacaoValidoComPrestadorETomador() {
    when(fiscalTaxConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.of(taxConfig()));
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.empty());

    NfseInvoiceEntity invoice = invoiceBase();

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<EnviarLoteRpsEnvio xmlns=\"http://www.abrasf.org.br/nfse.xsd\">");
    assertThat(xml).contains("<InfDeclaracaoPrestacaoServico Id=\"Rps");
    assertThat(xml).contains("<Cnpj>12345678000190</Cnpj>");
    assertThat(xml).contains("<InscricaoMunicipal>123456</InscricaoMunicipal>");
    assertThat(xml).contains("<ValorServicos>100.00</ValorServicos>");
    assertThat(xml).contains("<Aliquota>5.0000</Aliquota>");
    assertThat(xml).contains("<IssRetido>2</IssRetido>");
    assertThat(xml).contains("<OptanteSimplesNacional>1</OptanteSimplesNacional>");
    assertThat(xml).contains("<IdentificacaoTomador><CpfCnpj><Cpf>12345678901</Cpf></CpfCnpj></IdentificacaoTomador>");
  }

  @Test
  void issRetidoTrueGeraCodigo1() {
    when(fiscalTaxConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.of(taxConfig()));
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.empty());
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setIssRetido(true);

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<IssRetido>1</IssRetido>");
  }

  @Test
  void naoOptanteSimplesNacionalGeraCodigo2() {
    when(fiscalTaxConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.of(taxConfig()));
    NfseConfigEntity cfg = new NfseConfigEntity();
    cfg.setSimplesNacionalSituacao("NAO_OPTANTE");
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(cfg));
    NfseInvoiceEntity invoice = invoiceBase();

    String xml = builder.buildAndValidateAuthorizationXml(invoice);

    assertThat(xml).contains("<OptanteSimplesNacional>2</OptanteSimplesNacional>");
  }

  @Test
  void cnpjDoPrestadorInvalidoLancaIllegalArgument() {
    FiscalTaxConfigEntity tax = taxConfig();
    tax.setIssuerCnpj("123");
    when(fiscalTaxConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.of(tax));

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoiceBase()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_XML_PRESTADOR_CNPJ_INVALIDO");
  }

  @Test
  void inscricaoMunicipalAusenteLancaIllegalArgument() {
    FiscalTaxConfigEntity tax = taxConfig();
    tax.setIssuerIm(null);
    when(fiscalTaxConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.of(tax));

    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(invoiceBase()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_XML_PRESTADOR_IM_AUSENTE");
  }

  @Test
  void invoiceNulaLancaIllegalArgument() {
    assertThatThrownBy(() -> builder.buildAndValidateAuthorizationXml(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFS-e obrigatoria para gerar XML.");
  }

  @Test
  void buildAuthorizationReturnXmlEscapaCamposDaInvoice() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setFiscalStatus(NfseFiscalStatus.AUTHORIZED);
    invoice.setNumeroNfse("123");
    invoice.setProtocolo("PROTO-1");
    invoice.setCodigoVerificacao("ABC&DEF");

    String xml = builder.buildAuthorizationReturnXml(invoice, "PROV_OK", "Mensagem <ok>");

    assertThat(xml).contains("<status>AUTHORIZED</status>");
    assertThat(xml).contains("<numeroNfse>123</numeroNfse>");
    assertThat(xml).contains("<codigoVerificacao>ABC&amp;DEF</codigoVerificacao>");
    assertThat(xml).contains("<providerMessage>Mensagem &lt;ok&gt;</providerMessage>");
  }

  @Test
  void buildCancelReturnXmlContemStatusEProtocolo() {
    NfseInvoiceEntity invoice = invoiceBase();
    invoice.setFiscalStatus(NfseFiscalStatus.CANCELLED);
    invoice.setProtocolo("PROTO-9");

    String xml = builder.buildCancelReturnXml(invoice, "PROV_CANCEL", "cancelado");

    assertThat(xml).contains("<status>CANCELLED</status>");
    assertThat(xml).contains("<protocolo>PROTO-9</protocolo>");
    assertThat(xml).contains("<providerCode>PROV_CANCEL</providerCode>");
  }

  private NfseInvoiceEntity invoiceBase() {
    NfseInvoiceEntity entity = new NfseInvoiceEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setAmbiente(NfseAmbiente.HOMOLOGACAO);
    entity.setProvedor("ABRASF");
    entity.setNumeroRps(1001L);
    entity.setSerieRps("A1");
    entity.setCustomerType(NfseCustomerType.CPF);
    entity.setCustomerDocument("12345678901");
    entity.setCustomerName("Cliente Teste");
    entity.setCustomerPhone("11999999999");
    entity.setCustomerEmail("cliente@teste.com");
    entity.setItemListaServico("1.01");
    entity.setNaturezaOperacao("Prestacao de servico");
    entity.setCodigoTributacaoMunicipio("101");
    entity.setMunicipioCodigoIbge("3550308");
    entity.setValorServicos(new BigDecimal("100.00"));
    entity.setValorDeducoes(BigDecimal.ZERO);
    entity.setValorIss(new BigDecimal("5.00"));
    entity.setAliquotaIss(new BigDecimal("5.00"));
    entity.setIssRetido(false);
    entity.setDataCompetencia(LocalDate.of(2026, 3, 14));
    entity.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
    return entity;
  }

  private FiscalTaxConfigEntity taxConfig() {
    FiscalTaxConfigEntity config = new FiscalTaxConfigEntity();
    config.setIssuerCnpj("12.345.678/0001-90");
    config.setIssuerIm("123456");
    return config;
  }
}
