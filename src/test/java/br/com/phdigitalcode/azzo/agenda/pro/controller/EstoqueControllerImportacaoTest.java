package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ImportacaoErroLinhaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ImportacaoEstoqueJobResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ImportacaoResultadoArquivoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoEstoque;

/**
 * Cobre a fronteira 3 de {@code inventory} no controller — o unico trecho do {@code EstoqueController}
 * com logica propria, porte de {@code EstoqueResource}: hash SHA-256 do arquivo, upload ao storage e
 * o mapeamento de erro (400 para arquivo ausente ou ilegivel, 503 para storage indisponivel).
 *
 * <p>As demais rotas de {@code /importacoes*} sao delegacao pura e estao cobertas aqui apenas quanto
 * ao contrato de repasse.
 */
class EstoqueControllerImportacaoTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  /** SHA-256 de "sku;quantidade" — hex minusculo, conferido fora do codigo sob teste. */
  private static final String HASH_ESPERADO =
      "274232d6e97592f1636e5a772dcc40b04d53940be43da49fad1c45af80b082ca";

  private ServicoEstoque servicoEstoque;
  private MinioStorageService minioStorageService;
  private ContextoTenant contextoTenant;
  private EstoqueController controller;

  @BeforeEach
  void setUp() {
    servicoEstoque = mock(ServicoEstoque.class);
    minioStorageService = mock(MinioStorageService.class);
    contextoTenant = mock(ContextoTenant.class);
    controller = new EstoqueController(servicoEstoque, minioStorageService, contextoTenant);
  }

  private MockMultipartFile arquivo(String nome, byte[] conteudo) {
    return new MockMultipartFile("arquivo", nome, null, conteudo);
  }

  // ─── criarImportacao: caminho feliz ───────────────────────────────────────

  @Test
  void uploadCalculaHashSalvaNoStorageEDelegaAChaveAoService() {
    byte[] conteudo = "sku;quantidade".getBytes(StandardCharsets.UTF_8);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(minioStorageService.salvarArquivoImportacao(conteudo, "entradas.xlsx", TENANT_ID))
        .thenReturn("tenants/11111111/importacoes/abc.xlsx");
    ImportacaoEstoqueJobResponse esperado = new ImportacaoEstoqueJobResponse();
    when(servicoEstoque.criarImportacao(
            eq("ENTRADAS"), eq(Boolean.TRUE), any(), eq("tenants/11111111/importacoes/abc.xlsx")))
        .thenReturn(esperado);

    ImportacaoEstoqueJobResponse resposta =
        controller.criarImportacao(arquivo("entradas.xlsx", conteudo), "ENTRADAS", Boolean.TRUE);

    assertThat(resposta).isSameAs(esperado);

    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
    verify(servicoEstoque)
        .criarImportacao(
            eq("ENTRADAS"),
            eq(Boolean.TRUE),
            hash.capture(),
            eq("tenants/11111111/importacoes/abc.xlsx"));
    assertThat(hash.getValue()).isEqualTo(HASH_ESPERADO);
  }

  @Test
  void hashEHexMinusculoDeSessentaEQuatroCaracteres() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(minioStorageService.salvarArquivoImportacao(any(), any(), any())).thenReturn("key");

    controller.criarImportacao(
        arquivo("x.xlsx", new byte[] {0x00, (byte) 0xFF, 0x10}), "ITENS", null);

    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
    verify(servicoEstoque).criarImportacao(any(), any(), hash.capture(), any());
    assertThat(hash.getValue()).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  void tipoEDryRunAusentesSaoRepassadosNulosParaOService() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(minioStorageService.salvarArquivoImportacao(any(), any(), any())).thenReturn("key");

    controller.criarImportacao(arquivo("x.xlsx", new byte[] {1}), null, null);

    verify(servicoEstoque).criarImportacao(eq(null), eq(null), any(), eq("key"));
  }

  // ─── criarImportacao: erros ───────────────────────────────────────────────

  @Test
  void arquivoAusenteEQuatrocentosSemTocarNoStorage() {
    assertThatThrownBy(() -> controller.criarImportacao(null, "ITENS", null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Arquivo de importacao obrigatorio.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);

    verifyNoInteractions(minioStorageService, servicoEstoque, contextoTenant);
  }

  @Test
  void arquivoComNomeEmBrancoEQuatrocentos() {
    assertThatThrownBy(
            () -> controller.criarImportacao(arquivo("   ", new byte[] {1}), "ITENS", null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Arquivo de importacao obrigatorio.");

    verify(servicoEstoque, never()).criarImportacao(any(), any(), any(), any());
  }

  @Test
  void arquivoComNomeNuloEQuatrocentos() {
    assertThatThrownBy(
            () -> controller.criarImportacao(arquivo(null, new byte[] {1}), "ITENS", null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Arquivo de importacao obrigatorio.");
  }

  @Test
  void falhaDeLeituraDoArquivoEQuatrocentos() throws IOException {
    MultipartFile ilegivel = mock(MultipartFile.class);
    when(ilegivel.getOriginalFilename()).thenReturn("entradas.xlsx");
    when(ilegivel.getBytes()).thenThrow(new IOException("disco"));

    assertThatThrownBy(() -> controller.criarImportacao(ilegivel, "ENTRADAS", null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Falha ao processar arquivo de importacao.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);

    verifyNoInteractions(servicoEstoque);
  }

  @Test
  void storageIndisponivelEQuinhentosETres() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(minioStorageService.salvarArquivoImportacao(any(), any(), any()))
        .thenThrow(new IllegalStateException("MinIO desabilitado"));

    assertThatThrownBy(
            () -> controller.criarImportacao(arquivo("x.xlsx", new byte[] {1}), "ITENS", null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Storage de importacao indisponivel.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(503);

    verifyNoInteractions(servicoEstoque);
  }

  /**
   * O 400/503 do original so cobre {@code IOException} e {@code IllegalStateException} — qualquer
   * outra falha do service sobe crua para o handler global. Comportamento preservado.
   */
  @Test
  void erroDoServiceNaoEReembalhadoPeloControlador() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT_ID);
    when(minioStorageService.salvarArquivoImportacao(any(), any(), any())).thenReturn("key");
    when(servicoEstoque.criarImportacao(any(), any(), any(), any()))
        .thenThrow(new ApiClientErrorException("Tipo de importacao invalido.", 400));

    assertThatThrownBy(
            () -> controller.criarImportacao(arquivo("x.xlsx", new byte[] {1}), "XPTO", null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Tipo de importacao invalido.");
  }

  // ─── modelo ───────────────────────────────────────────────────────────────

  @Test
  void modeloVaiComoAnexoNoContentTypeQueOServiceDefiniu() {
    ServicoEstoque.ModeloImportacaoArquivo modelo = new ServicoEstoque.ModeloImportacaoArquivo();
    modelo.nomeArquivo = "modelo-entradas.csv";
    modelo.contentType = "text/csv";
    modelo.conteudo = "a;b".getBytes(StandardCharsets.UTF_8);
    when(servicoEstoque.gerarModeloImportacao("ENTRADAS", "csv")).thenReturn(modelo);

    ResponseEntity<byte[]> resposta = controller.baixarModeloImportacao("ENTRADAS", "csv");

    assertThat(resposta.getStatusCode().value()).isEqualTo(200);
    assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/csv");
    assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"modelo-entradas.csv\"");
    assertThat(resposta.getBody()).isEqualTo(modelo.conteudo);
  }

  // ─── delegacao pura ───────────────────────────────────────────────────────

  @Test
  void rotasDeLeituraEcancelamentoApenasDelegam() {
    UUID jobId = UUID.randomUUID();
    List<ImportacaoEstoqueJobResponse> jobs = List.of(new ImportacaoEstoqueJobResponse());
    ImportacaoEstoqueJobResponse job = new ImportacaoEstoqueJobResponse();
    List<ImportacaoErroLinhaResponse> erros = List.of(new ImportacaoErroLinhaResponse());
    ImportacaoResultadoArquivoResponse resultado = new ImportacaoResultadoArquivoResponse();

    when(servicoEstoque.listarImportacoes()).thenReturn(jobs);
    when(servicoEstoque.buscarImportacao(jobId)).thenReturn(job);
    when(servicoEstoque.listarErrosImportacao(jobId)).thenReturn(erros);
    when(servicoEstoque.obterArquivoResultado(jobId)).thenReturn(resultado);
    when(servicoEstoque.cancelarImportacao(jobId)).thenReturn(job);

    assertThat(controller.listarImportacoes()).isSameAs(jobs);
    assertThat(controller.buscarImportacao(jobId)).isSameAs(job);
    assertThat(controller.listarErrosImportacao(jobId)).isSameAs(erros);
    assertThat(controller.obterArquivoResultado(jobId)).isSameAs(resultado);
    assertThat(controller.cancelarImportacao(jobId)).isSameAs(job);
  }
}
