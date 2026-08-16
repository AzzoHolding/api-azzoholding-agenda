package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalMunicipalityEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalStateEntity;

/**
 * Teste de integracao contra PostgreSQL real (Testcontainers) para o catalogo de
 * {@code fiscal_states}/{@code fiscal_municipalities} portado na Fronteira 1 de {@code nfse} (ver
 * MIGRACAO-QUARKUS-SPRING.md, Etapa 25/26). Os dados sao pre-carregados pela migration
 * {@code V20__create_fiscal_states_and_municipalities.sql} (27 UFs + municipios oficiais
 * IBGE/TOM) — este teste nao insere fixtures, so consulta o catalogo real.
 *
 * <p>Cobre especificamente as duas bifurcacoes JPQL de {@code FiscalMunicipalityRepository}
 * (com/sem filtro de UF) que existem para evitar a armadilha #7 do projeto ({@code :param is null}
 * quebra no PostgreSQL) — validando que ambas realmente compilam e retornam dados corretos contra
 * o schema real, nao so contra um mock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FiscalMunicipalityRepositoryIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private FiscalStateRepository stateRepository;
  @Autowired private FiscalMunicipalityRepository municipalityRepository;

  @Test
  void deveListarTodosOs27EstadosOrdenadosPorUf() {
    List<FiscalStateEntity> states = stateRepository.listAllOrdered();

    assertThat(states).hasSize(27);
    assertThat(states.get(0).getUf()).isEqualTo("AC");
    assertThat(states).isSortedAccordingTo((a, b) -> a.getUf().compareTo(b.getUf()));
  }

  @Test
  void deveEncontrarEstadoPorUfIgnorandoCaixa() {
    Optional<FiscalStateEntity> sp = stateRepository.findByUf("sp");

    assertThat(sp).isPresent();
    assertThat(sp.get().getCodigoIbge()).isEqualTo("35");
    assertThat(sp.get().getNome()).isEqualTo("São Paulo");
  }

  @Test
  void deveRetornarVazioParaUfInexistente() {
    assertThat(stateRepository.findByUf("XX")).isEmpty();
    assertThat(stateRepository.findByUf(null)).isEmpty();
    assertThat(stateRepository.findByUf("  ")).isEmpty();
  }

  @Test
  void deveListarMunicipiosDeUmaUfComStateCarregado() {
    List<FiscalMunicipalityEntity> municipios = municipalityRepository.listByStateUf("sp");

    assertThat(municipios).isNotEmpty();
    assertThat(municipios).allMatch(m -> "SP".equals(m.getState().getUf()));
    // join fetch: acessar o estado nao deve estourar LazyInitializationException fora da sessao
    assertThat(municipios.get(0).getState().getNome()).isEqualTo("São Paulo");
  }

  @Test
  void deveListarTodosOsMunicipiosQuandoUfEmBranco() {
    List<FiscalMunicipalityEntity> todos = municipalityRepository.listByStateUf(null);

    assertThat(todos.size()).isGreaterThan(5000);
  }

  @Test
  void deveBuscarMunicipioPorNomeComFiltroDeUf() {
    List<FiscalMunicipalityEntity> resultado =
        municipalityRepository.searchByStateUfAndName("SP", "paulo", 10);

    assertThat(resultado).isNotEmpty();
    assertThat(resultado).anyMatch(m -> "São Paulo".equals(m.getNome()));
    assertThat(resultado).allMatch(m -> "SP".equals(m.getState().getUf()));
  }

  @Test
  void deveBuscarMunicipioPorNomeSemFiltroDeUfRespeitandoLimite() {
    List<FiscalMunicipalityEntity> resultado =
        municipalityRepository.searchByStateUfAndName(null, "santa", 3);

    assertThat(resultado).hasSizeLessThanOrEqualTo(3);
    assertThat(resultado).allMatch(m -> m.getNome().toLowerCase().contains("santa"));
  }

  @Test
  void deveEncontrarMunicipioPorCodigoIbge() {
    Optional<FiscalMunicipalityEntity> saoPaulo = municipalityRepository.findByCodigoIbge("3550308");

    assertThat(saoPaulo).isPresent();
    assertThat(saoPaulo.get().getNome()).isEqualTo("São Paulo");
    assertThat(saoPaulo.get().getCodigoTom()).isEqualTo("7107");
  }

  @Test
  void deveRetornarVazioParaCodigoIbgeInexistenteOuEmBranco() {
    assertThat(municipalityRepository.findByCodigoIbge("0000000")).isEmpty();
    assertThat(municipalityRepository.findByCodigoIbge(null)).isEmpty();
    assertThat(municipalityRepository.findByCodigoIbge("")).isEmpty();
  }
}
