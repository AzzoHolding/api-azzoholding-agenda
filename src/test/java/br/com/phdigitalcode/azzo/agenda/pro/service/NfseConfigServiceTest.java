package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCpfPolicyMode;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseEmissionMode;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseProviderCapabilitiesRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre a fatia de CRUD de configuracao de {@code modules/nfse/application/NfseService.java}
 * (metodos {@code obterConfig}/{@code salvarConfig} + helpers privados), portada como {@link
 * NfseConfigService} na Fronteira 2 do porte de {@code nfse} (ver {@code
 * MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/26).
 *
 * <p>{@code findByTenantAndAmbiente}/{@code findPreferredProviderForMunicipio} sao metodos {@code
 * default}: estubados diretamente, nao as queries derivadas por baixo — mesma armadilha ja
 * registrada em {@link NfseIdempotencyServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class NfseConfigServiceTest {

  @Mock private NfseConfigRepository nfseConfigRepository;
  @Mock private NfseProviderCapabilitiesRepository nfseProviderCapabilitiesRepository;
  @Mock private ContextoTenant contextoTenant;

  private NfseConfigService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new NfseConfigService(nfseConfigRepository, nfseProviderCapabilitiesRepository, contextoTenant);
    lenient().when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
  }

  @Test
  void obterConfigDevolveODtoMapeadoQuandoEncontrada() {
    NfseConfigEntity entity = configPersistida();
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(entity));

    NfseDtos.Config dto = service.obterConfig("homologacao");

    assertThat(dto.ambiente).isEqualTo("HOMOLOGACAO");
    assertThat(dto.municipioCodigoIbge).isEqualTo("3550308");
    assertThat(dto.provedor).isEqualTo("ABRASF");
    assertThat(dto.serieRps).isEqualTo("A1");
    assertThat(dto.aliquotaIssPadrao).isEqualByComparingTo("2.5");
    assertThat(dto.emissionMode).isEqualTo("ASK_ON_CLOSE");
    assertThat(dto.emitForCpfMode).isEqualTo("ASK");
  }

  @Test
  void obterConfigSemValorUsaHomologacaoComoAmbientePadrao() {
    NfseConfigEntity entity = configPersistida();
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(entity));

    service.obterConfig(null);

    // Nao lanca e resolve para HOMOLOGACAO — a chamada acima ja comprova via stub exato.
  }

  @Test
  void obterConfigNaoEncontradaVira404() {
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.PRODUCAO))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.obterConfig("PRODUCAO"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Configuracao NFS-e nao encontrada para o ambiente informado.")
        .extracting(ex -> ((ApiClientErrorException) ex).getStatus())
        .isEqualTo(404);
  }

  @Test
  void obterConfigComAmbienteInvalidoLancaIllegalArgument() {
    assertThatThrownBy(() -> service.obterConfig("INEXISTENTE"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Ambiente NFS-e invalido.");
  }

  @Test
  void salvarConfigCriaNovaEntidadeEResolveProvedorAutomaticamentePorMunicipio() {
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.empty());
    when(nfseProviderCapabilitiesRepository.findPreferredProviderForMunicipio("3550308"))
        .thenReturn(Optional.of("SEFIN_NACIONAL"));
    lenient()
        .when(nfseConfigRepository.save(any(NfseConfigEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NfseDtos.Config request = requestValido();
    request.provedor = null;
    request.applicationVersion = "1.0";
    request.nationalTaxCodeDefault = "01";
    request.simplesNacionalSituacao = "SN";
    request.especialRegimeTributacao = "NENHUM";

    NfseDtos.Config resultado = service.salvarConfig(request);

    ArgumentCaptor<NfseConfigEntity> gravada = ArgumentCaptor.forClass(NfseConfigEntity.class);
    org.mockito.Mockito.verify(nfseConfigRepository).save(gravada.capture());
    assertThat(gravada.getValue().getProvedor()).isEqualTo("SEFIN_NACIONAL");
    assertThat(gravada.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(resultado.provedor).isEqualTo("SEFIN_NACIONAL");
  }

  @Test
  void salvarConfigProvedorInformadoTemPrecedenciaSobreOAutomatico() {
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.empty());
    lenient()
        .when(nfseConfigRepository.save(any(NfseConfigEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NfseDtos.Config request = requestValido();
    request.provedor = "ABRASF";

    NfseDtos.Config resultado = service.salvarConfig(request);

    assertThat(resultado.provedor).isEqualTo("ABRASF");
    org.mockito.Mockito.verifyNoInteractions(nfseProviderCapabilitiesRepository);
  }

  @Test
  void salvarConfigReutilizaEntidadeExistentePorTenantEAmbiente() {
    NfseConfigEntity existente = configPersistida();
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(existente));
    lenient()
        .when(nfseConfigRepository.save(any(NfseConfigEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NfseDtos.Config request = requestValido();
    request.provedor = "ABRASF";
    request.serieRps = "B2";

    service.salvarConfig(request);

    ArgumentCaptor<NfseConfigEntity> gravada = ArgumentCaptor.forClass(NfseConfigEntity.class);
    org.mockito.Mockito.verify(nfseConfigRepository).save(gravada.capture());
    assertThat(gravada.getValue()).isSameAs(existente);
    assertThat(gravada.getValue().getSerieRps()).isEqualTo("B2");
  }

  @Test
  void salvarConfigRejeitaRequestNulo() {
    assertThatThrownBy(() -> service.salvarConfig(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Configuracao NFS-e obrigatoria.");
  }

  @Test
  void salvarConfigRejeitaCamposObrigatoriosAusentes() {
    lenient()
        .when(nfseConfigRepository.findByTenantAndAmbiente(any(), any()))
        .thenReturn(Optional.empty());

    NfseDtos.Config semMunicipio = requestValido();
    semMunicipio.municipioCodigoIbge = null;
    assertThatThrownBy(() -> service.salvarConfig(semMunicipio))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_CONFIG_MISSING_MUNICIPIO");

    NfseDtos.Config semSerie = requestValido();
    semSerie.serieRps = null;
    assertThatThrownBy(() -> service.salvarConfig(semSerie))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_CONFIG_MISSING_SERIE_RPS");

    NfseDtos.Config semAliquota = requestValido();
    semAliquota.aliquotaIssPadrao = null;
    assertThatThrownBy(() -> service.salvarConfig(semAliquota))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_CONFIG_MISSING_ALIQUOTA_ISS");

    NfseDtos.Config semItemLista = requestValido();
    semItemLista.itemListaServicoPadrao = null;
    assertThatThrownBy(() -> service.salvarConfig(semItemLista))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_CONFIG_MISSING_ITEM_LISTA_SERVICO");
  }

  /**
   * ⚠️ Quando o provedor efetivo (informado ou resolvido automaticamente) e SEFIN_NACIONAL, quatro
   * campos adicionais passam a ser obrigatorios — do original, {@code validarConfig}.
   */
  @Test
  void salvarConfigExigeCamposExtrasQuandoProvedorEfetivoESefinNacional() {
    lenient()
        .when(nfseConfigRepository.findByTenantAndAmbiente(any(), any()))
        .thenReturn(Optional.empty());

    NfseDtos.Config request = requestValido();
    request.provedor = "SEFIN_NACIONAL";

    assertThatThrownBy(() -> service.salvarConfig(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_APP_VERSION_REQUIRED");

    request.applicationVersion = "1.0";
    assertThatThrownBy(() -> service.salvarConfig(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_CTRIBNAC_REQUIRED");

    request.nationalTaxCodeDefault = "01";
    assertThatThrownBy(() -> service.salvarConfig(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_OP_SIMP_NAC_REQUIRED");

    request.simplesNacionalSituacao = "SN";
    assertThatThrownBy(() -> service.salvarConfig(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_SEFIN_NACIONAL_REG_ESP_TRIB_REQUIRED");
  }

  @Test
  void salvarConfigComEmissionModeECpfPolicyModeAusentesUsaDefaults() {
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.empty());
    when(nfseConfigRepository.save(any(NfseConfigEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NfseDtos.Config request = requestValido();
    request.provedor = "ABRASF";
    request.emissionMode = null;
    request.emitForCpfMode = null;

    NfseDtos.Config resultado = service.salvarConfig(request);

    assertThat(resultado.emissionMode).isEqualTo(NfseEmissionMode.ASK_ON_CLOSE.name());
    assertThat(resultado.emitForCpfMode).isEqualTo(NfseCpfPolicyMode.ASK.name());
  }

  @Test
  void salvarConfigComEnumInvalidoLancaIllegalArgument() {
    lenient()
        .when(nfseConfigRepository.findByTenantAndAmbiente(any(), any()))
        .thenReturn(Optional.empty());

    NfseDtos.Config comAmbienteInvalido = requestValido();
    comAmbienteInvalido.ambiente = "XPTO";
    assertThatThrownBy(() -> service.salvarConfig(comAmbienteInvalido))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Ambiente NFS-e invalido.");

    NfseDtos.Config comEmissionModeInvalido = requestValido();
    comEmissionModeInvalido.provedor = "ABRASF";
    comEmissionModeInvalido.emissionMode = "XPTO";
    assertThatThrownBy(() -> service.salvarConfig(comEmissionModeInvalido))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Modo de emissao NFS-e invalido.");

    NfseDtos.Config comCpfModeInvalido = requestValido();
    comCpfModeInvalido.provedor = "ABRASF";
    comCpfModeInvalido.emitForCpfMode = "XPTO";
    assertThatThrownBy(() -> service.salvarConfig(comCpfModeInvalido))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Politica CPF NFS-e invalida.");
  }

  @Test
  void resolveProvedorUsaFallbackAbrasfQuandoMunicipioSemCapabilities() {
    when(nfseProviderCapabilitiesRepository.findPreferredProviderForMunicipio("3550308"))
        .thenReturn(Optional.empty());

    String provedor = service.resolveProvedor(null, "3550308");

    assertThat(provedor).isEqualTo("ABRASF");
  }

  @Test
  void resolveProvedorUsaFallbackAbrasfQuandoMunicipioAusente() {
    String provedor = service.resolveProvedor(null, null);

    assertThat(provedor).isEqualTo("ABRASF");
    org.mockito.Mockito.verifyNoInteractions(nfseProviderCapabilitiesRepository);
  }

  private NfseConfigEntity configPersistida() {
    NfseConfigEntity entity = new NfseConfigEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setAmbiente(NfseAmbiente.HOMOLOGACAO);
    entity.setMunicipioCodigoIbge("3550308");
    entity.setProvedor("ABRASF");
    entity.setSerieRps("A1");
    entity.setAliquotaIssPadrao(new BigDecimal("2.5"));
    entity.setItemListaServicoPadrao("1.01");
    entity.setEmissionMode(NfseEmissionMode.ASK_ON_CLOSE);
    entity.setEmitForCpfMode(NfseCpfPolicyMode.ASK);
    entity.setAutoIssueOnAppointmentClose(false);
    return entity;
  }

  private NfseDtos.Config requestValido() {
    NfseDtos.Config request = new NfseDtos.Config();
    request.ambiente = "HOMOLOGACAO";
    request.municipioCodigoIbge = "3550308";
    request.serieRps = "A1";
    request.aliquotaIssPadrao = new BigDecimal("2.5");
    request.itemListaServicoPadrao = "1.01";
    return request;
  }
}
