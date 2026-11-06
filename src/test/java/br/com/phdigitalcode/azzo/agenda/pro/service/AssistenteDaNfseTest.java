package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseAssistenteDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCertificateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.integration.BrasilApiCnpjClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCertificateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * O assistente monta uma nota de exemplo com o montador REAL da DPS (que confere o XSD oficial):
 * "pronto" aqui quer dizer que a configuracao gera uma nota que passa no esquema da Receita.
 */
class AssistenteDaNfseTest {

  private final UUID tenantId = UUID.randomUUID();
  private final ContextoTenant contextoTenant = mock(ContextoTenant.class);
  private final BrasilApiCnpjClient brasilApi = mock(BrasilApiCnpjClient.class);
  private final NfseConfigRepository configs = mock(NfseConfigRepository.class);
  private final FiscalTaxConfigRepository impostos = mock(FiscalTaxConfigRepository.class);
  private final FiscalCertificateRepository certificados = mock(FiscalCertificateRepository.class);
  private AssistenteDaNfse assistente;

  @BeforeEach
  void setUp() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    SefinNacionalXmlLayoutBuilder montador =
        new SefinNacionalXmlLayoutBuilder(configs, mock(NfseInvoiceItemRepository.class), impostos);
    assistente = new AssistenteDaNfse(contextoTenant, brasilApi, configs, impostos, certificados, montador);
  }

  @Test
  void situacaoNoSimplesVemDaConsultaDoCnpj() {
    assertThat(AssistenteDaNfse.situacaoNoSimples(true, true)).isEqualTo("2");
    assertThat(AssistenteDaNfse.situacaoNoSimples(false, true)).isEqualTo("3");
    assertThat(AssistenteDaNfse.situacaoNoSimples(false, false)).isEqualTo("1");
    // Sem resposta da consulta, nao afirma nada: o dono escolhe.
    assertThat(AssistenteDaNfse.situacaoNoSimples(null, null)).isNull();
  }

  @Test
  void dadosDoEmissorPreenchemMunicipioESimples() {
    BrasilApiCnpjClient.BrasilApiCnpjResponse resposta = new BrasilApiCnpjClient.BrasilApiCnpjResponse();
    resposta.razao_social = "SALAO TESTE LTDA";
    resposta.descricao_tipo_de_logradouro = "RUA";
    resposta.logradouro = "DAS FLORES";
    resposta.cep = "20.000-000";
    resposta.codigo_municipio_ibge = 3304557;
    resposta.opcao_pelo_simples = true;
    resposta.opcao_pelo_mei = false;
    when(brasilApi.lookup("12345678000199")).thenReturn(resposta);

    NfseAssistenteDtos.Emissor emissor = assistente.dadosDoEmissor("12.345.678/0001-99");

    assertThat(emissor.razaoSocial).isEqualTo("SALAO TESTE LTDA");
    assertThat(emissor.logradouro).isEqualTo("RUA DAS FLORES");
    assertThat(emissor.cep).isEqualTo("20000000");
    assertThat(emissor.municipioCodigoIbge).isEqualTo("3304557");
    assertThat(emissor.situacaoNoSimples).isEqualTo("3");
  }

  @Test
  void cnpjInvalidoNaoConsulta() {
    assertThatThrownBy(() -> assistente.dadosDoEmissor("123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_TOMADOR_CNPJ_INVALID");
  }

  @Test
  void configuracaoCompletaFicaProntaSemErro() {
    when(configs.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO)).thenReturn(Optional.of(configCompleta()));
    when(impostos.findByTenantId(tenantId)).thenReturn(Optional.of(emissor()));
    when(certificados.findActiveByTenant(tenantId)).thenReturn(Optional.of(certificado(365)));

    NfseAssistenteDtos.Conferencia conferencia = assistente.conferir(null);

    assertThat(conferencia.problemas).isEmpty();
    assertThat(conferencia.pronto).isTrue();
  }

  @Test
  void apontaOQueFaltaEmPortuguesComOPasso() {
    NfseConfigEntity config = configCompleta();
    config.setPercentualTributosSn(null);
    FiscalTaxConfigEntity semIm = emissor();
    semIm.setIssuerIm(null);
    when(configs.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO)).thenReturn(Optional.of(config));
    when(impostos.findByTenantId(tenantId)).thenReturn(Optional.of(semIm));
    when(certificados.findActiveByTenant(any())).thenReturn(Optional.empty());

    NfseAssistenteDtos.Conferencia conferencia = assistente.conferir("HOMOLOGACAO");

    assertThat(conferencia.pronto).isFalse();
    assertThat(conferencia.problemas)
        .extracting(p -> p.tipo + ":" + p.campo)
        .containsExactlyInAnyOrder("AVISO:inscricaoMunicipal", "ERRO:certificado", "ERRO:impostos");
    assertThat(conferencia.problemas)
        .filteredOn(p -> "impostos".equals(p.campo))
        .first()
        .satisfies(p -> assertThat(p.mensagem).contains("percentual aproximado de tributos do Simples"));
  }

  @Test
  void provedorQueNaoEOEmissorNacionalPedeParaEscolher() {
    NfseConfigEntity mock = configCompleta();
    mock.setProvedor("MOCK");
    when(configs.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO)).thenReturn(Optional.of(mock));

    NfseAssistenteDtos.Conferencia conferencia = assistente.conferir(null);

    assertThat(conferencia.pronto).isFalse();
    assertThat(conferencia.problemas).singleElement().satisfies(p -> assertThat(p.campo).isEqualTo("provedor"));
  }

  @Test
  void certificadoPertoDeVencerEAvisoENaoErro() {
    when(configs.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO)).thenReturn(Optional.of(configCompleta()));
    when(impostos.findByTenantId(tenantId)).thenReturn(Optional.of(emissor()));
    when(certificados.findActiveByTenant(tenantId)).thenReturn(Optional.of(certificado(10)));

    NfseAssistenteDtos.Conferencia conferencia = assistente.conferir(null);

    assertThat(conferencia.pronto).isTrue();
    assertThat(conferencia.problemas).singleElement().satisfies(p -> assertThat(p.tipo).isEqualTo("AVISO"));
  }

  private NfseConfigEntity configCompleta() {
    NfseConfigEntity config = new NfseConfigEntity();
    config.setTenantId(tenantId);
    config.setAmbiente(NfseAmbiente.HOMOLOGACAO);
    config.setProvedor("SEFIN_NACIONAL");
    config.setMunicipioCodigoIbge("3304557");
    config.setSerieRps("1");
    config.setAliquotaIssPadrao(new BigDecimal("2.00"));
    config.setItemListaServicoPadrao("6.01");
    config.setApplicationVersion("AZZO-1.0");
    config.setNationalTaxCodeDefault("060101");
    config.setSimplesNacionalSituacao("3");
    config.setSimplesNacionalRegimeTributacao("1");
    config.setEspecialRegimeTributacao("0");
    config.setPercentualTributosSn(new BigDecimal("6.00"));
    return config;
  }

  private FiscalTaxConfigEntity emissor() {
    FiscalTaxConfigEntity emissor = new FiscalTaxConfigEntity();
    emissor.setTenantId(tenantId);
    emissor.setIssuerCnpj("12345678000199");
    emissor.setIssuerIm("1234567");
    emissor.setIssuerRazaoSocial("Salao Teste Ltda");
    return emissor;
  }

  private FiscalCertificateEntity certificado(int diasDeValidade) {
    FiscalCertificateEntity certificado = new FiscalCertificateEntity();
    certificado.setValidTo(Instant.now().plus(diasDeValidade, ChronoUnit.DAYS));
    return certificado;
  }
}
