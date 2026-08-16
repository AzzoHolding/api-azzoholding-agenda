package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.AtualizarContagemInventarioRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.CancelarInventarioRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ConfiguracaoEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ConfiguracaoEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.DashboardEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.FornecedorEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.FornecedorEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ImportacaoErroLinhaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ImportacaoEstoqueJobResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ImportacaoResultadoArquivoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioContagemRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioContagemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioEstoquePageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.InventarioEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ItemEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ItemEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ItemEstoqueUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.MovimentacaoEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.MovimentacaoEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.PedidoCompraEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.PedidoCompraEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.PedidoCompraRecebimentoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ServicoInsumoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ServicoInsumoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.ServicoInsumoUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.TransferenciaEstoqueRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.EstoqueDtos.TransferenciaEstoqueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoEstoque;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/inventory/api/EstoqueResource.java} — mesmos paths, verbos, parametros,
 * roles e permissoes ({@code stock:view} para leitura, {@code stock:manage} para escrita).
 *
 * <p><b>Completo:</b> os 39 endpoints do recurso original — 13 de itens/movimentacoes/dashboard/
 * configuracoes/servico-insumo, 19 de {@code /inventarios*}, {@code /fornecedores*},
 * {@code /pedidos-compra*} e {@code /transferencias*}, e 7 de {@code /importacoes*}.
 *
 * <p>Atencao aos verbos: as atualizacoes deste recurso usam {@code PUT}, nao {@code PATCH} — ao
 * contrario de {@code packages}/{@code membership}. Semanticamente sao patches parciais (campo
 * ausente nao mexe no valor), mas o verbo do original e {@code PUT} e foi preservado.
 */
@RestController
@RequestMapping("/api/v1/estoque")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class EstoqueController {

  private final ServicoEstoque servicoEstoque;
  private final MinioStorageService minioStorageService;
  private final ContextoTenant contextoTenant;

  public EstoqueController(
      ServicoEstoque servicoEstoque,
      MinioStorageService minioStorageService,
      ContextoTenant contextoTenant) {
    this.servicoEstoque = servicoEstoque;
    this.minioStorageService = minioStorageService;
    this.contextoTenant = contextoTenant;
  }

  // ─── Itens ───────────────────────────────────────────────────────────────

  @GetMapping("/itens")
  @RequiresPermission("stock:view")
  public List<ItemEstoqueResponse> listarItens(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "cursorCreatedAt", required = false) String cursorCreatedAt,
      @RequestParam(name = "cursorId", required = false) String cursorId,
      @RequestParam(name = "search", required = false) String search,
      @RequestParam(name = "ativo", required = false) Boolean ativo,
      @RequestParam(name = "abaixoMinimo", required = false) Boolean abaixoMinimo) {
    return servicoEstoque.listarItens(page, limit, cursorCreatedAt, cursorId, search, ativo, abaixoMinimo);
  }

  @GetMapping("/itens/{id}")
  @RequiresPermission("stock:view")
  public ItemEstoqueResponse buscarItem(@PathVariable UUID id) {
    return servicoEstoque.buscarItem(id);
  }

  @PostMapping("/itens")
  @RequiresPermission("stock:manage")
  public ItemEstoqueResponse criarItem(@Valid @RequestBody ItemEstoqueRequest request) {
    return servicoEstoque.criarItem(request);
  }

  @PutMapping("/itens/{id}")
  @RequiresPermission("stock:manage")
  public ItemEstoqueResponse atualizarItem(
      @PathVariable UUID id, @Valid @RequestBody ItemEstoqueUpdateRequest request) {
    return servicoEstoque.atualizarItem(id, request);
  }

  // ─── Movimentacoes ───────────────────────────────────────────────────────

  @GetMapping("/movimentacoes")
  @RequiresPermission("stock:view")
  public List<MovimentacaoEstoqueResponse> listarMovimentacoes(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "cursorCreatedAt", required = false) String cursorCreatedAt,
      @RequestParam(name = "cursorId", required = false) String cursorId,
      @RequestParam(name = "itemId", required = false) UUID itemId,
      @RequestParam(name = "tipo", required = false) String tipo) {
    return servicoEstoque.listarMovimentacoes(page, limit, cursorCreatedAt, cursorId, itemId, tipo);
  }

  @PostMapping("/movimentacoes")
  @RequiresPermission("stock:manage")
  public MovimentacaoEstoqueResponse criarMovimentacao(
      @Valid @RequestBody MovimentacaoEstoqueRequest request) {
    return servicoEstoque.criarMovimentacao(request);
  }

  // ─── Dashboard ───────────────────────────────────────────────────────────

  /**
   * Os quatro parametros de recorte sao aceitos e <b>ignorados</b> — assimetria do original,
   * preservada: {@code EstoqueResource.dashboard} os declara e chama
   * {@code servicoEstoque.obterDashboard()} sem argumento nenhum.
   */
  @GetMapping("/dashboard")
  @RequiresPermission("stock:view")
  public DashboardEstoqueResponse dashboard(
      @RequestParam(name = "inicio", required = false) String inicio,
      @RequestParam(name = "fim", required = false) String fim,
      @RequestParam(name = "serviceId", required = false) String serviceId,
      @RequestParam(name = "itemId", required = false) String itemId) {
    return servicoEstoque.obterDashboard();
  }

  // ─── Importacao em massa ─────────────────────────────────────────────────

  @GetMapping("/importacoes")
  @RequiresPermission("stock:view")
  public List<ImportacaoEstoqueJobResponse> listarImportacoes() {
    return servicoEstoque.listarImportacoes();
  }

  /**
   * Download do modelo de planilha. Unico endpoint do recurso que nao devolve JSON — dai o
   * {@code produces} restrito aos dois tipos que o original declara em {@code @Produces}.
   */
  @GetMapping(
      value = "/importacoes/modelo",
      produces = {"text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})
  @RequiresPermission("stock:manage")
  public ResponseEntity<byte[]> baixarModeloImportacao(
      @RequestParam(name = "tipoImportacao", required = false) String tipoImportacao,
      @RequestParam(name = "formato", required = false, defaultValue = "xlsx") String formato) {
    ServicoEstoque.ModeloImportacaoArquivo modelo =
        servicoEstoque.gerarModeloImportacao(tipoImportacao, formato);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, modelo.contentType)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + modelo.nomeArquivo + "\"")
        .body(modelo.conteudo);
  }

  /**
   * Upload do arquivo e criacao do job. Porte fiel do original, inclusive quanto a divisao de
   * responsabilidade: hash e envio ao storage ficam aqui (adaptacao de fronteira entre
   * {@code FileUpload} do RESTEasy e {@code MultipartFile} do Spring MVC), e o service so registra o
   * job ja com a chave.
   *
   * <p>Mapeamento de erro do original preservado: arquivo ausente ou sem nome e <b>400</b>, falha de
   * leitura do arquivo e <b>400</b> e storage indisponivel e <b>503</b>.
   *
   * <p>{@code required = false} no {@code @RequestPart} e proposital — sem isso o Spring devolveria
   * 400 generico antes de chegar a validacao explicita, e a mensagem do original se perderia.
   */
  @PostMapping(value = "/importacoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresPermission("stock:manage")
  public ImportacaoEstoqueJobResponse criarImportacao(
      @RequestPart(name = "arquivo", required = false) MultipartFile arquivo,
      @RequestParam(name = "tipoImportacao", required = false) String tipoImportacao,
      @RequestParam(name = "dryRun", required = false) Boolean dryRun) {
    if (arquivo == null
        || arquivo.getOriginalFilename() == null
        || arquivo.getOriginalFilename().isBlank()) {
      throw new ApiClientErrorException(
          "Arquivo de importacao obrigatorio.", HttpStatus.BAD_REQUEST.value());
    }
    try {
      byte[] bytesArquivo = arquivo.getBytes();
      String hashSha256 = hashSha256Hex(bytesArquivo);
      UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
      String storageKey =
          minioStorageService.salvarArquivoImportacao(
              bytesArquivo, arquivo.getOriginalFilename(), tenantId);
      return servicoEstoque.criarImportacao(tipoImportacao, dryRun, hashSha256, storageKey);
    } catch (IOException exception) {
      throw new ApiClientErrorException(
          "Falha ao processar arquivo de importacao.", HttpStatus.BAD_REQUEST.value());
    } catch (IllegalStateException exception) {
      throw new ApiClientErrorException(
          "Storage de importacao indisponivel.", HttpStatus.SERVICE_UNAVAILABLE.value());
    }
  }

  @GetMapping("/importacoes/{jobId}")
  @RequiresPermission("stock:view")
  public ImportacaoEstoqueJobResponse buscarImportacao(@PathVariable UUID jobId) {
    return servicoEstoque.buscarImportacao(jobId);
  }

  @GetMapping("/importacoes/{jobId}/erros")
  @RequiresPermission("stock:view")
  public List<ImportacaoErroLinhaResponse> listarErrosImportacao(@PathVariable UUID jobId) {
    return servicoEstoque.listarErrosImportacao(jobId);
  }

  @GetMapping("/importacoes/{jobId}/arquivo-resultado")
  @RequiresPermission("stock:view")
  public ImportacaoResultadoArquivoResponse obterArquivoResultado(@PathVariable UUID jobId) {
    return servicoEstoque.obterArquivoResultado(jobId);
  }

  @PostMapping("/importacoes/{jobId}/cancelar")
  @RequiresPermission("stock:manage")
  public ImportacaoEstoqueJobResponse cancelarImportacao(@PathVariable UUID jobId) {
    return servicoEstoque.cancelarImportacao(jobId);
  }

  /** Porte de {@code EstoqueResource.hashSha256Hex} — hex minusculo, sem separador. */
  private String hashSha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Algoritmo SHA-256 indisponivel.", e);
    }
  }

  // ─── Configuracoes ───────────────────────────────────────────────────────

  @GetMapping("/configuracoes")
  @RequiresPermission("stock:view")
  public ConfiguracaoEstoqueResponse obterConfiguracoes() {
    return servicoEstoque.obterConfiguracoes();
  }

  @PutMapping("/configuracoes")
  @RequiresPermission("stock:manage")
  public ConfiguracaoEstoqueResponse atualizarConfiguracoes(
      @Valid @RequestBody ConfiguracaoEstoqueRequest request) {
    return servicoEstoque.atualizarConfiguracoes(request);
  }

  // ─── Servico ↔ insumo ────────────────────────────────────────────────────

  @GetMapping("/servico-insumo")
  @RequiresPermission("stock:view")
  public List<ServicoInsumoResponse> listarInsumosPorServico(
      @RequestParam(name = "serviceId", required = false) String serviceId) {
    return servicoEstoque.listarInsumosPorServico(serviceId);
  }

  /** Unico endpoint do recurso que devolve 201; os demais POST devolvem 200. */
  @PostMapping("/servico-insumo")
  @RequiresPermission("stock:manage")
  public ResponseEntity<ServicoInsumoResponse> adicionarInsumo(
      @Valid @RequestBody ServicoInsumoRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(servicoEstoque.adicionarInsumo(request));
  }

  @PutMapping("/servico-insumo/{id}")
  @RequiresPermission("stock:manage")
  public ServicoInsumoResponse atualizarInsumo(
      @PathVariable String id, @Valid @RequestBody ServicoInsumoUpdateRequest request) {
    return servicoEstoque.atualizarInsumo(id, request);
  }

  @DeleteMapping("/servico-insumo/{id}")
  @RequiresPermission("stock:manage")
  public ResponseEntity<Void> removerInsumo(@PathVariable String id) {
    servicoEstoque.removerInsumo(id);
    return ResponseEntity.noContent().build();
  }

  // ─── Inventarios ─────────────────────────────────────────────────────────

  /**
   * Unico endpoint do recurso com {@code @DefaultValue} no original: {@code limit} chega 20 quando
   * ausente. Tambem e o unico que devolve um envelope paginado
   * ({@link InventarioEstoquePageResponse}); os demais listam array cru com cursor.
   */
  @GetMapping("/inventarios")
  @RequiresPermission("stock:view")
  public InventarioEstoquePageResponse listarInventarios(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "limit", required = false, defaultValue = "20") Integer limit,
      @RequestParam(name = "search", required = false) String search,
      @RequestParam(name = "status", required = false) String status) {
    return servicoEstoque.listarInventarios(page, limit, search, status);
  }

  @PostMapping("/inventarios")
  @RequiresPermission("stock:manage")
  public InventarioEstoqueResponse criarInventario(
      @Valid @RequestBody InventarioEstoqueRequest request) {
    return servicoEstoque.criarInventario(request);
  }

  @GetMapping("/inventarios/{id}")
  @RequiresPermission("stock:view")
  public InventarioEstoqueResponse buscarInventario(@PathVariable UUID id) {
    return servicoEstoque.buscarInventario(id);
  }

  /** Devolve o <b>inventario</b>, nao a contagem criada — assimetria do original, preservada. */
  @PostMapping("/inventarios/{id}/contagens")
  @RequiresPermission("stock:manage")
  public InventarioEstoqueResponse registrarContagem(
      @PathVariable UUID id, @Valid @RequestBody InventarioContagemRequest request) {
    return servicoEstoque.registrarContagemInventario(id, request);
  }

  @GetMapping("/inventarios/{id}/contagens")
  @RequiresPermission("stock:view")
  public List<InventarioContagemResponse> listarContagens(@PathVariable UUID id) {
    return servicoEstoque.listarContagensInventario(id);
  }

  /**
   * O {@code id} do inventario no path e usado pelo service para validar o vinculo da contagem.
   * Diferente de {@link #registrarContagem}, aqui a resposta e a <b>contagem</b>.
   */
  @PutMapping("/inventarios/{id}/contagens/{contagemId}")
  @RequiresPermission("stock:manage")
  public InventarioContagemResponse atualizarContagem(
      @PathVariable UUID id,
      @PathVariable UUID contagemId,
      @Valid @RequestBody AtualizarContagemInventarioRequest request) {
    return servicoEstoque.atualizarContagemInventario(id, contagemId, request);
  }

  @PostMapping("/inventarios/{id}/fechamento")
  @RequiresPermission("stock:manage")
  public InventarioEstoqueResponse fecharInventario(@PathVariable UUID id) {
    return servicoEstoque.fecharInventario(id);
  }

  /** Unico endpoint do modulo que reconfere a senha do usuario logado (401 quando incorreta). */
  @PostMapping("/inventarios/{id}/cancelamento")
  @RequiresPermission("stock:manage")
  public InventarioEstoqueResponse cancelarInventario(
      @PathVariable UUID id, @Valid @RequestBody CancelarInventarioRequest request) {
    return servicoEstoque.cancelarInventario(id, request);
  }

  // ─── Fornecedores ────────────────────────────────────────────────────────

  @GetMapping("/fornecedores")
  @RequiresPermission("stock:view")
  public List<FornecedorEstoqueResponse> listarFornecedores(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "cursorCreatedAt", required = false) String cursorCreatedAt,
      @RequestParam(name = "cursorId", required = false) String cursorId) {
    return servicoEstoque.listarFornecedores(page, limit, cursorCreatedAt, cursorId);
  }

  @PostMapping("/fornecedores")
  @RequiresPermission("stock:manage")
  public FornecedorEstoqueResponse criarFornecedor(
      @Valid @RequestBody FornecedorEstoqueRequest request) {
    return servicoEstoque.criarFornecedor(request);
  }

  /** {@code PUT} de substituicao total: campos ausentes no corpo limpam o valor gravado. */
  @PutMapping("/fornecedores/{id}")
  @RequiresPermission("stock:manage")
  public FornecedorEstoqueResponse atualizarFornecedor(
      @PathVariable UUID id, @Valid @RequestBody FornecedorEstoqueRequest request) {
    return servicoEstoque.atualizarFornecedor(id, request);
  }

  // ─── Pedidos de compra ───────────────────────────────────────────────────

  @GetMapping("/pedidos-compra")
  @RequiresPermission("stock:view")
  public List<PedidoCompraEstoqueResponse> listarPedidosCompra(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "cursorCreatedAt", required = false) String cursorCreatedAt,
      @RequestParam(name = "cursorId", required = false) String cursorId) {
    return servicoEstoque.listarPedidosCompra(page, limit, cursorCreatedAt, cursorId);
  }

  @PostMapping("/pedidos-compra")
  @RequiresPermission("stock:manage")
  public PedidoCompraEstoqueResponse criarPedidoCompra(
      @Valid @RequestBody PedidoCompraEstoqueRequest request) {
    return servicoEstoque.criarPedidoCompra(request);
  }

  @GetMapping("/pedidos-compra/{id}")
  @RequiresPermission("stock:view")
  public PedidoCompraEstoqueResponse buscarPedidoCompra(@PathVariable UUID id) {
    return servicoEstoque.buscarPedidoCompra(id);
  }

  @PostMapping("/pedidos-compra/{id}/recebimento")
  @RequiresPermission("stock:manage")
  public PedidoCompraEstoqueResponse receberPedidoCompra(
      @PathVariable UUID id, @Valid @RequestBody PedidoCompraRecebimentoRequest request) {
    return servicoEstoque.receberPedidoCompra(id, request);
  }

  // ─── Transferencias ──────────────────────────────────────────────────────

  @GetMapping("/transferencias")
  @RequiresPermission("stock:view")
  public List<TransferenciaEstoqueResponse> listarTransferencias(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "cursorCreatedAt", required = false) String cursorCreatedAt,
      @RequestParam(name = "cursorId", required = false) String cursorId) {
    return servicoEstoque.listarTransferencias(page, limit, cursorCreatedAt, cursorId);
  }

  @PostMapping("/transferencias")
  @RequiresPermission("stock:manage")
  public TransferenciaEstoqueResponse criarTransferencia(
      @Valid @RequestBody TransferenciaEstoqueRequest request) {
    return servicoEstoque.criarTransferencia(request);
  }

  @PostMapping("/transferencias/{id}/enviar")
  @RequiresPermission("stock:manage")
  public TransferenciaEstoqueResponse enviarTransferencia(@PathVariable UUID id) {
    return servicoEstoque.enviarTransferencia(id);
  }

  @PostMapping("/transferencias/{id}/receber")
  @RequiresPermission("stock:manage")
  public TransferenciaEstoqueResponse receberTransferencia(@PathVariable UUID id) {
    return servicoEstoque.receberTransferencia(id);
  }
}
