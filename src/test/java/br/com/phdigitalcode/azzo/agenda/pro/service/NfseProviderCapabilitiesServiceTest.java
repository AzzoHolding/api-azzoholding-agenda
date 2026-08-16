package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseProviderCapabilitiesEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCancelMode;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseProviderCapabilitiesRepository;

/**
 * Cobre a fatia de CRUD de {@code nfse_provider_capabilities} de {@code
 * modules/nfse/application/NfseService.java} (metodos {@code listarProviderCapabilities}/{@code
 * salvarProviderCapabilities}), portada como {@link NfseProviderCapabilitiesService} na Fronteira
 * 2 do porte de {@code nfse} (ver {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/26).
 *
 * <p>{@code findByMunicipioProvedor}/{@code listByMunicipioAndProvedor} sao metodos {@code
 * default} em {@link NfseProviderCapabilitiesRepository}: estubados diretamente, nao as queries
 * derivadas por baixo — mesma armadilha ja registrada em {@link NfseIdempotencyServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class NfseProviderCapabilitiesServiceTest {

  @Mock private NfseProviderCapabilitiesRepository nfseProviderCapabilitiesRepository;

  private NfseProviderCapabilitiesService service;

  @BeforeEach
  void setUp() {
    service = new NfseProviderCapabilitiesService(nfseProviderCapabilitiesRepository);
  }

  @Test
  void listarProviderCapabilitiesNormalizaFiltrosEMapeiaTodosOsCampos() {
    NfseProviderCapabilitiesEntity entity = new NfseProviderCapabilitiesEntity();
    entity.setMunicipioCodigoIbge("3550308");
    entity.setProvedor("ABRASF");
    entity.setLayoutVersion("2.04");
    entity.setCancelSupported(true);
    entity.setCancelWindowHours(24);
    entity.setCancelMode(NfseCancelMode.SYNC);
    entity.setAcceptedCancelReasonCodes("1,2,3");
    when(nfseProviderCapabilitiesRepository.listByMunicipioAndProvedor("3550308", "ABRASF"))
        .thenReturn(List.of(entity));

    List<NfseDtos.ProviderCapabilities> resultado =
        service.listarProviderCapabilities("  3550308  ", "  ABRASF  ");

    assertThat(resultado).hasSize(1);
    NfseDtos.ProviderCapabilities dto = resultado.get(0);
    assertThat(dto.municipioCodigoIbge).isEqualTo("3550308");
    assertThat(dto.provedor).isEqualTo("ABRASF");
    assertThat(dto.layoutVersion).isEqualTo("2.04");
    assertThat(dto.cancelSupported).isTrue();
    assertThat(dto.cancelWindowHours).isEqualTo(24);
    assertThat(dto.cancelMode).isEqualTo("SYNC");
    assertThat(dto.acceptedCancelReasonCodes).isEqualTo("1,2,3");
  }

  @Test
  void listarProviderCapabilitiesComFiltrosVaziosViramNulos() {
    when(nfseProviderCapabilitiesRepository.listByMunicipioAndProvedor(null, null))
        .thenReturn(List.of());

    List<NfseDtos.ProviderCapabilities> resultado = service.listarProviderCapabilities("  ", null);

    assertThat(resultado).isEmpty();
  }

  @Test
  void salvarProviderCapabilitiesCriaNovaEntidadeQuandoNaoExisteENormalizaCampos() {
    // findByMunicipioProvedor recebe o request cru (sem trim) — o service so normaliza os
    // campos gravados na entidade depois de resolver a linha existente.
    when(nfseProviderCapabilitiesRepository.findByMunicipioProvedor("  3550308  ", "  ABRASF  "))
        .thenReturn(Optional.empty());
    when(nfseProviderCapabilitiesRepository.save(any(NfseProviderCapabilitiesEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NfseDtos.ProviderCapabilities request = new NfseDtos.ProviderCapabilities();
    request.municipioCodigoIbge = "  3550308  ";
    request.provedor = "  ABRASF  ";
    request.layoutVersion = "  2.04  ";
    request.cancelSupported = true;
    request.cancelWindowHours = 48;
    request.cancelMode = "async";
    request.acceptedCancelReasonCodes = "1,2";

    NfseDtos.ProviderCapabilities resultado = service.salvarProviderCapabilities(request);

    ArgumentCaptor<NfseProviderCapabilitiesEntity> gravada =
        ArgumentCaptor.forClass(NfseProviderCapabilitiesEntity.class);
    verifySavedOnce(gravada);
    assertThat(gravada.getValue().getMunicipioCodigoIbge()).isEqualTo("3550308");
    assertThat(gravada.getValue().getProvedor()).isEqualTo("ABRASF");
    assertThat(gravada.getValue().getLayoutVersion()).isEqualTo("2.04");
    assertThat(gravada.getValue().getCancelMode()).isEqualTo(NfseCancelMode.ASYNC);
    assertThat(gravada.getValue().getCancelWindowHours()).isEqualTo(48);
    assertThat(resultado.cancelMode).isEqualTo("ASYNC");
  }

  @Test
  void salvarProviderCapabilitiesReutilizaEntidadeExistente() {
    NfseProviderCapabilitiesEntity existente = new NfseProviderCapabilitiesEntity();
    existente.setMunicipioCodigoIbge("3550308");
    existente.setProvedor("ABRASF");
    when(nfseProviderCapabilitiesRepository.findByMunicipioProvedor("3550308", "ABRASF"))
        .thenReturn(Optional.of(existente));
    when(nfseProviderCapabilitiesRepository.save(any(NfseProviderCapabilitiesEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NfseDtos.ProviderCapabilities request = new NfseDtos.ProviderCapabilities();
    request.municipioCodigoIbge = "3550308";
    request.provedor = "ABRASF";
    request.layoutVersion = "2.04";
    request.cancelSupported = false;

    service.salvarProviderCapabilities(request);

    ArgumentCaptor<NfseProviderCapabilitiesEntity> gravada =
        ArgumentCaptor.forClass(NfseProviderCapabilitiesEntity.class);
    verifySavedOnce(gravada);
    assertThat(gravada.getValue()).isSameAs(existente);
  }

  /**
   * ⚠️ {@code cancelWindowHours} so e persistido quando {@code cancelSupported} e verdadeiro — do
   * original, linha "{@code entity.cancelWindowHours = request.cancelSupported ? request.cancelWindowHours : null}".
   */
  @Test
  void cancelWindowHoursEDescartadoQuandoCancelSupportedEFalso() {
    when(nfseProviderCapabilitiesRepository.findByMunicipioProvedor("3550308", "ABRASF"))
        .thenReturn(Optional.empty());
    when(nfseProviderCapabilitiesRepository.save(any(NfseProviderCapabilitiesEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NfseDtos.ProviderCapabilities request = new NfseDtos.ProviderCapabilities();
    request.municipioCodigoIbge = "3550308";
    request.provedor = "ABRASF";
    request.layoutVersion = "2.04";
    request.cancelSupported = false;
    request.cancelWindowHours = 72;

    NfseDtos.ProviderCapabilities resultado = service.salvarProviderCapabilities(request);

    assertThat(resultado.cancelWindowHours).isNull();
  }

  @Test
  void salvarProviderCapabilitiesRejeitaCamposObrigatoriosAusentes() {
    assertThatThrownBy(() -> service.salvarProviderCapabilities(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Capacidades do provedor sao obrigatorias.");

    NfseDtos.ProviderCapabilities semMunicipio = new NfseDtos.ProviderCapabilities();
    semMunicipio.provedor = "ABRASF";
    semMunicipio.layoutVersion = "2.04";
    assertThatThrownBy(() -> service.salvarProviderCapabilities(semMunicipio))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_CAP_MISSING_MUNICIPIO");

    NfseDtos.ProviderCapabilities semProvedor = new NfseDtos.ProviderCapabilities();
    semProvedor.municipioCodigoIbge = "3550308";
    semProvedor.layoutVersion = "2.04";
    assertThatThrownBy(() -> service.salvarProviderCapabilities(semProvedor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_CAP_MISSING_PROVEDOR");

    NfseDtos.ProviderCapabilities semLayout = new NfseDtos.ProviderCapabilities();
    semLayout.municipioCodigoIbge = "3550308";
    semLayout.provedor = "ABRASF";
    assertThatThrownBy(() -> service.salvarProviderCapabilities(semLayout))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("NFSE_PROVIDER_CAP_MISSING_LAYOUT_VERSION");
  }

  @Test
  void salvarProviderCapabilitiesRejeitaCancelModeInvalido() {
    when(nfseProviderCapabilitiesRepository.findByMunicipioProvedor("3550308", "ABRASF"))
        .thenReturn(Optional.empty());

    NfseDtos.ProviderCapabilities request = new NfseDtos.ProviderCapabilities();
    request.municipioCodigoIbge = "3550308";
    request.provedor = "ABRASF";
    request.layoutVersion = "2.04";
    request.cancelMode = "INVALIDO";

    assertThatThrownBy(() -> service.salvarProviderCapabilities(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cancel mode NFS-e invalido.");
  }

  @Test
  void salvarProviderCapabilitiesUsaSyncComoCancelModeDefault() {
    when(nfseProviderCapabilitiesRepository.findByMunicipioProvedor("3550308", "ABRASF"))
        .thenReturn(Optional.empty());
    when(nfseProviderCapabilitiesRepository.save(any(NfseProviderCapabilitiesEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NfseDtos.ProviderCapabilities request = new NfseDtos.ProviderCapabilities();
    request.municipioCodigoIbge = "3550308";
    request.provedor = "ABRASF";
    request.layoutVersion = "2.04";
    request.cancelMode = null;

    NfseDtos.ProviderCapabilities resultado = service.salvarProviderCapabilities(request);

    assertThat(resultado.cancelMode).isEqualTo("SYNC");
  }

  private void verifySavedOnce(ArgumentCaptor<NfseProviderCapabilitiesEntity> captor) {
    org.mockito.Mockito.verify(nfseProviderCapabilitiesRepository).save(captor.capture());
  }
}
