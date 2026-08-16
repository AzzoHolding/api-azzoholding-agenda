package br.com.phdigitalcode.azzo.agenda.pro.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.service.EstadoImportacaoEstoque;

/**
 * Cobre {@code EstoqueImportacaoReadinessHealthCheck} do original: a regra
 * {@code up = !habilitado || operacional} e o fato de os mesmos nove detalhes serem expostos nos
 * dois ramos, UP e DOWN.
 */
class EstoqueImportacaoReadinessHealthIndicatorTest {

  private MinioStorageService minioStorageService;
  private EstadoImportacaoEstoque estado;
  private EstoqueImportacaoReadinessHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    minioStorageService = mock(MinioStorageService.class);
    estado = new EstadoImportacaoEstoque();
    indicator = new EstoqueImportacaoReadinessHealthIndicator(minioStorageService, estado);
  }

  private void storage(boolean habilitado, boolean operacional) {
    when(minioStorageService.isStorageHabilitado()).thenReturn(habilitado);
    when(minioStorageService.isStorageOperacional()).thenReturn(operacional);
  }

  @Test
  void storageHabilitadoEOperacionalEUp() {
    storage(true, true);

    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void storageHabilitadoEForaDoArEDown() {
    storage(true, false);

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails())
        .containsEntry("storageEnabled", true)
        .containsEntry("storageOperational", false);
  }

  /**
   * Assimetria do original preservada: sem MinIO configurado a sonda fica <b>UP</b>, embora toda
   * importacao va falhar no download do arquivo. Nao e defeito introduzido pela migracao.
   */
  @Test
  void storageDesabilitadoContaComoSaudavelMesmoNaoEstandoOperacional() {
    storage(false, false);

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("storageEnabled", false);
  }

  @Test
  void semExecucaoAlgumaOsCamposDeDataEErroViramNA() {
    storage(true, true);

    Health health = indicator.health();

    assertThat(health.getDetails())
        .containsEntry("lastProcessingAt", "N/A")
        .containsEntry("lastCleanupAt", "N/A")
        .containsEntry("lastProcessingError", "N/A")
        .containsEntry("lastCleanupError", "N/A")
        .containsEntry("lastProcessedJobs", 0)
        .containsEntry("lastCleanupJobs", 0)
        .containsEntry("lastPendingJobs", 0);
  }

  @Test
  void detalhesRefletemAUltimaExecucaoRegistrada() {
    storage(true, true);
    Instant processamento = Instant.parse("2026-03-10T08:00:00Z");
    Instant limpeza = Instant.parse("2026-03-10T08:05:00Z");
    estado.registrarProcessamento(processamento, 7, 3);
    estado.registrarLimpeza(limpeza, 2);

    Health health = indicator.health();

    assertThat(health.getDetails())
        .containsEntry("lastProcessingAt", processamento.toString())
        .containsEntry("lastCleanupAt", limpeza.toString())
        .containsEntry("lastProcessedJobs", 7)
        .containsEntry("lastPendingJobs", 3)
        .containsEntry("lastCleanupJobs", 2);
  }

  @Test
  void erroDeProcessamentoApareceNosDetalhesEOStatusSegueOStorage() {
    storage(true, true);
    estado.registrarFalhaProcessamento("IllegalStateException: storage fora");

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("lastProcessingError", "IllegalStateException: storage fora");
  }

  @Test
  void osMesmosDetalhesSaoExpostosNoRamoUpENoRamoDown() {
    storage(true, true);
    var chavesUp = indicator.health().getDetails().keySet();

    setUp();
    storage(true, false);
    var chavesDown = indicator.health().getDetails().keySet();

    assertThat(chavesDown).isEqualTo(chavesUp).hasSize(9);
  }
}
