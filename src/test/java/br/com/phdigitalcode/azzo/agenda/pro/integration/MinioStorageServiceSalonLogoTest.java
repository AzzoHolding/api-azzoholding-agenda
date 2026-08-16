package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Cobre {@code salvarArquivoSalaoLogo}/{@code removerArquivoSalaoLogo}, adicionados a
 * {@code MinioStorageService} para o modulo {@code salon} — espelham
 * {@code infrastructure/storage/MinioStorageService.java} do original.
 *
 * <p>Com o storage desabilitado (padrao de testes, sem MinIO real disponivel): geram/validam a
 * storage key sem tentar rede, mesmo comportamento ja coberto para
 * {@code salvarArquivoImportacao}/{@code removerArquivoImportacao}.
 */
class MinioStorageServiceSalonLogoTest {

  private MinioStorageService disabledService() {
    return new MinioStorageService(
        false, "http://localhost:9000", "minio", "minio123", "bucket", 10, "http://localhost:8080");
  }

  @Test
  void salvarComStorageDesabilitadoDevolveKeySemEnviarNada() {
    MinioStorageService service = disabledService();
    UUID tenantId = UUID.randomUUID();

    String key = service.salvarArquivoSalaoLogo("fake".getBytes(), "logo.webp", "image/webp", tenantId);

    assertThat(key).startsWith("tenant/" + tenantId + "/salao/logo/").endsWith("-logo.webp");
  }

  @Test
  void salvarUsaNomePadraoQuandoNomeArquivoAusente() {
    MinioStorageService service = disabledService();
    UUID tenantId = UUID.randomUUID();

    String key = service.salvarArquivoSalaoLogo("fake".getBytes(), null, "image/webp", tenantId);

    assertThat(key).endsWith("-logo-estabelecimento.webp");
  }

  @Test
  void salvarExigeTenantId() {
    MinioStorageService service = disabledService();

    assertThatThrownBy(() -> service.salvarArquivoSalaoLogo("fake".getBytes(), "logo.webp", "image/webp", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void removerComStorageDesabilitadoNaoFazNada() {
    MinioStorageService service = disabledService();
    UUID tenantId = UUID.randomUUID();

    // Nao deve lancar mesmo com uma key fora do escopo do tenant, porque o early-return de
    // minioEnabled=false acontece antes da validacao de escopo.
    service.removerArquivoSalaoLogo("tenant/outro/salao/logo/x.webp", tenantId);
  }

  @Test
  void removerComKeyNulaOuEmBrancoNaoFazNada() {
    MinioStorageService service = disabledService();
    UUID tenantId = UUID.randomUUID();

    service.removerArquivoSalaoLogo(null, tenantId);
    service.removerArquivoSalaoLogo("  ", tenantId);
  }
}
