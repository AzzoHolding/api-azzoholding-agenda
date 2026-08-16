package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalMunicipalityEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalStateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalMunicipalityRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalStateRepository;

/**
 * Cobre {@code modules/nfse/application/NfseLocationService.java} (Fronteira 2 do porte de
 * {@code nfse}, ver {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/26) — catalogo de UFs e
 * municipios (IBGE/TOM), porte verbatim, sem I/O externo.
 *
 * <p>{@code listByStateUf}/{@code searchByStateUfAndName} sao metodos {@code default} em {@link
 * FiscalMunicipalityRepository}: o teste estuba exatamente esses metodos (os que o service
 * chama), nao as queries JPQL derivadas por baixo deles — mesma armadilha ja registrada em
 * {@link NfseIdempotencyServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class NfseLocationServiceTest {

  @Mock private FiscalStateRepository fiscalStateRepository;
  @Mock private FiscalMunicipalityRepository fiscalMunicipalityRepository;

  private NfseLocationService service;

  @BeforeEach
  void setUp() {
    service = new NfseLocationService(fiscalStateRepository, fiscalMunicipalityRepository);
  }

  @Test
  void listarEstadosMapeiaTodosOsCamposDoDto() {
    FiscalStateEntity sp = new FiscalStateEntity();
    sp.setCodigoIbge("35");
    sp.setUf("SP");
    sp.setNome("Sao Paulo");
    sp.setRegiaoSigla("SE");
    sp.setRegiaoNome("Sudeste");
    when(fiscalStateRepository.listAllOrdered()).thenReturn(List.of(sp));

    List<NfseDtos.FiscalState> resultado = service.listarEstados();

    assertThat(resultado).hasSize(1);
    NfseDtos.FiscalState dto = resultado.get(0);
    assertThat(dto.codigoIbge).isEqualTo("35");
    assertThat(dto.uf).isEqualTo("SP");
    assertThat(dto.nome).isEqualTo("Sao Paulo");
    assertThat(dto.regiaoSigla).isEqualTo("SE");
    assertThat(dto.regiaoNome).isEqualTo("Sudeste");
  }

  @Test
  void listarMunicipiosSemBuscaUsaListByStateUfELimitaEmMemoria() {
    when(fiscalMunicipalityRepository.listByStateUf("SP"))
        .thenReturn(List.of(municipio("3550308", "Sao Paulo", "SP"), municipio("3509502", "Campinas", "SP")));

    List<NfseDtos.FiscalMunicipality> resultado = service.listarMunicipios("SP", null, 1);

    assertThat(resultado).hasSize(1);
    assertThat(resultado.get(0).codigoIbge).isEqualTo("3550308");
    assertThat(resultado.get(0).stateUf).isEqualTo("SP");
    verify(fiscalMunicipalityRepository, never()).searchByStateUfAndName(anyString(), anyString(), anyInt());
  }

  @Test
  void listarMunicipiosComBuscaUsaSearchByStateUfAndNameSemRelimitarEmMemoria() {
    when(fiscalMunicipalityRepository.searchByStateUfAndName(eq("SP"), eq("cam"), eq(50)))
        .thenReturn(List.of(municipio("3509502", "Campinas", "SP")));

    List<NfseDtos.FiscalMunicipality> resultado = service.listarMunicipios("SP", "cam", 50);

    assertThat(resultado).hasSize(1);
    assertThat(resultado.get(0).nome).isEqualTo("Campinas");
    verify(fiscalMunicipalityRepository, never()).listByStateUf(any());
  }

  @Test
  void limiteNuloOuNaoPositivoCaiParaODefaultDeDuzentos() {
    when(fiscalMunicipalityRepository.listByStateUf(null))
        .thenReturn(List.of(municipio("3550308", "Sao Paulo", "SP"), municipio("3509502", "Campinas", "SP")));

    List<NfseDtos.FiscalMunicipality> comLimiteNulo = service.listarMunicipios(null, null, null);
    List<NfseDtos.FiscalMunicipality> comLimiteNegativo = service.listarMunicipios(null, "", -5);

    // safeLimit cai para 200 nos dois casos, entao as 2 entidades retornadas nao sao cortadas.
    assertThat(comLimiteNulo).hasSize(2);
    assertThat(comLimiteNegativo).hasSize(2);
    verify(fiscalMunicipalityRepository, never())
        .searchByStateUfAndName(anyString(), anyString(), anyInt());
  }

  @Test
  void limiteAcimaDeMilEClampeadoParaMil() {
    when(fiscalMunicipalityRepository.searchByStateUfAndName(eq("SP"), eq("a"), eq(1000)))
        .thenReturn(List.of());

    service.listarMunicipios("SP", "a", 5000);

    verify(fiscalMunicipalityRepository).searchByStateUfAndName("SP", "a", 1000);
  }

  private FiscalMunicipalityEntity municipio(String codigoIbge, String nome, String uf) {
    FiscalMunicipalityEntity entity = new FiscalMunicipalityEntity();
    entity.setCodigoIbge(codigoIbge);
    entity.setNome(nome);
    entity.setCodigoTom("1234");
    entity.setCodigoTomDv("5");
    entity.setCodigoTomComDv("12345");
    FiscalStateEntity state = new FiscalStateEntity();
    state.setCodigoIbge("35");
    state.setUf(uf);
    state.setNome("Sao Paulo");
    entity.setState(state);
    return entity;
  }
}
