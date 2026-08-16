package br.com.phdigitalcode.azzo.agenda.pro.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.service.EstadoImportacaoEstoque;

/**
 * Espelha
 * {@code modules/inventory/infrastructure/health/EstoqueImportacaoReadinessHealthCheck.java}
 * ({@code @Readiness}, nome {@code estoque-importacao-readiness}).
 *
 * <p>O nome do bean vira a chave do indicador (<b>{@code estoqueImportacaoReadiness}</b>); e por
 * essa chave que ele entra no grupo {@code readiness} configurado no {@code application.yml} —
 * equivalente Spring da anotacao {@code @Readiness} do MicroProfile Health.
 *
 * <p><b>Regra do original preservada:</b> storage <b>desabilitado</b> conta como saudavel
 * ({@code up = !habilitado || operacional}). Ou seja, em ambiente sem MinIO a sonda fica UP mesmo
 * sabendo que toda importacao vai falhar no download do arquivo. Os mesmos dados sao expostos nos
 * dois ramos, UP e DOWN.
 */
@Component("estoqueImportacaoReadiness")
public class EstoqueImportacaoReadinessHealthIndicator implements HealthIndicator {

  private final MinioStorageService minioStorageService;
  private final EstadoImportacaoEstoque estadoImportacaoEstoque;

  public EstoqueImportacaoReadinessHealthIndicator(
      MinioStorageService minioStorageService, EstadoImportacaoEstoque estadoImportacaoEstoque) {
    this.minioStorageService = minioStorageService;
    this.estadoImportacaoEstoque = estadoImportacaoEstoque;
  }

  @Override
  public Health health() {
    boolean storageHabilitado = minioStorageService.isStorageHabilitado();
    boolean storageOperacional = minioStorageService.isStorageOperacional();
    boolean up = !storageHabilitado || storageOperacional;

    Health.Builder builder = up ? Health.up() : Health.down();
    return builder
        .withDetail("storageEnabled", storageHabilitado)
        .withDetail("storageOperational", storageOperacional)
        .withDetail(
            "lastProcessingAt", toStringSafe(estadoImportacaoEstoque.getUltimaExecucaoProcessamento()))
        .withDetail("lastCleanupAt", toStringSafe(estadoImportacaoEstoque.getUltimaExecucaoLimpeza()))
        .withDetail("lastProcessedJobs", estadoImportacaoEstoque.getUltimoTotalProcessados())
        .withDetail("lastCleanupJobs", estadoImportacaoEstoque.getUltimoTotalLimpos())
        .withDetail("lastPendingJobs", estadoImportacaoEstoque.getUltimoTotalFilaPendente())
        .withDetail("lastProcessingError", orNA(estadoImportacaoEstoque.getUltimoErroProcessamento()))
        .withDetail("lastCleanupError", orNA(estadoImportacaoEstoque.getUltimoErroLimpeza()))
        .build();
  }

  private String toStringSafe(Object value) {
    return value == null ? "N/A" : value.toString();
  }

  private String orNA(String value) {
    return value == null || value.isBlank() ? "N/A" : value;
  }
}
