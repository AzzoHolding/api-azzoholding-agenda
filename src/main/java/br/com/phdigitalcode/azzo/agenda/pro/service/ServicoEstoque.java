package br.com.phdigitalcode.azzo.agenda.pro.service;

import static br.com.phdigitalcode.azzo.agenda.pro.service.EstoqueMovimentacaoService.nvl;
import static br.com.phdigitalcode.azzo.agenda.pro.service.EstoqueMovimentacaoService.toItemResponse;
import static br.com.phdigitalcode.azzo.agenda.pro.service.EstoqueMovimentacaoService.toMovimentacaoResponse;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import at.favre.lib.crypto.bcrypt.BCrypt;
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
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueConfiguracao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueFornecedor;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueInventario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueInventarioContagem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoquePedidoCompra;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueTransferencia;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueErroLinha;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicoInsumo;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusInventarioEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPedidoCompraEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusTransferenciaEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoImportacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueConfiguracaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueFornecedorRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueInventarioContagemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueInventarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoquePedidoCompraRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EstoqueTransferenciaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoEstoqueErroLinhaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoEstoqueJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ItemEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentacaoEstoqueRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoInsumoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.specification.EstoqueSpecifications;
import br.com.phdigitalcode.azzo.agenda.pro.util.EstoqueTextoUtil;

/**
 * Espelha {@code modules/inventory/application/ServicoEstoque.java} — a superficie HTTP inteira do
 * modulo {@code inventory}, os 39 endpoints.
 *
 * <p><b>Coberto aqui:</b> CRUD de itens, listagem e criacao de movimentacao, dashboard,
 * configuracao do modulo, vinculo servico↔insumo, inventario/contagem, fornecedor, pedido de compra,
 * transferencia e importacao em massa.
 *
 * <p>O <b>processamento</b> dos jobs de importacao nao esta aqui, como no original: este service so
 * registra, consulta e cancela o job. Quem le a planilha e escreve no estoque e o
 * {@link ProcessadorImportacaoEstoqueService}, acionado pelo scheduler.
 *
 * <p>O calculo de saldo <b>nao</b> e reimplementado: {@link #criarMovimentacao} delega para
 * {@link EstoqueMovimentacaoService}, que ja era o porte desse trecho do original.
 *
 * <p>Duas adaptacoes estruturais herdadas da decisao tomada nas entidades: {@code MovimentacaoEstoque}
 * e {@code ServicoInsumo} guardam apenas {@code itemEstoqueId}, sem a {@code @ManyToOne ItemEstoque}
 * que o original navega para preencher {@code itemNome}. Os itens da pagina sao resolvidos em lote
 * ({@code findByTenantIdAndIdIn}) e passados aos mappers.
 */
@Service
public class ServicoEstoque {

  /**
   * Espelha {@code ServicoEstoque.ModeloImportacaoArquivo} do original: o modelo de planilha nunca
   * chega ao cliente como JSON, entao nao e um DTO de {@code EstoqueDtos} — e o trio
   * nome/content-type/bytes que o controller transforma em download.
   */
  public static class ModeloImportacaoArquivo {
    public String nomeArquivo;
    public String contentType;
    public byte[] conteudo;
  }

  private final ItemEstoqueRepository itemEstoqueRepository;
  private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
  private final ImportacaoEstoqueJobRepository importacaoEstoqueJobRepository;
  private final ImportacaoEstoqueErroLinhaRepository importacaoEstoqueErroLinhaRepository;
  private final EstoqueConfiguracaoRepository estoqueConfiguracaoRepository;
  private final ServicoInsumoRepository servicoInsumoRepository;
  private final EstoqueInventarioRepository estoqueInventarioRepository;
  private final EstoqueInventarioContagemRepository estoqueInventarioContagemRepository;
  private final EstoqueFornecedorRepository estoqueFornecedorRepository;
  private final EstoquePedidoCompraRepository estoquePedidoCompraRepository;
  private final EstoqueTransferenciaRepository estoqueTransferenciaRepository;
  private final UsuarioRepository usuarioRepository;
  private final EstoqueMovimentacaoService estoqueMovimentacaoService;
  private final MinioStorageService minioStorageService;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final AuditService auditService;

  public ServicoEstoque(
      ItemEstoqueRepository itemEstoqueRepository,
      MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
      ImportacaoEstoqueJobRepository importacaoEstoqueJobRepository,
      ImportacaoEstoqueErroLinhaRepository importacaoEstoqueErroLinhaRepository,
      EstoqueConfiguracaoRepository estoqueConfiguracaoRepository,
      ServicoInsumoRepository servicoInsumoRepository,
      EstoqueInventarioRepository estoqueInventarioRepository,
      EstoqueInventarioContagemRepository estoqueInventarioContagemRepository,
      EstoqueFornecedorRepository estoqueFornecedorRepository,
      EstoquePedidoCompraRepository estoquePedidoCompraRepository,
      EstoqueTransferenciaRepository estoqueTransferenciaRepository,
      UsuarioRepository usuarioRepository,
      EstoqueMovimentacaoService estoqueMovimentacaoService,
      MinioStorageService minioStorageService,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      AuditService auditService) {
    this.itemEstoqueRepository = itemEstoqueRepository;
    this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
    this.importacaoEstoqueJobRepository = importacaoEstoqueJobRepository;
    this.importacaoEstoqueErroLinhaRepository = importacaoEstoqueErroLinhaRepository;
    this.estoqueConfiguracaoRepository = estoqueConfiguracaoRepository;
    this.servicoInsumoRepository = servicoInsumoRepository;
    this.estoqueInventarioRepository = estoqueInventarioRepository;
    this.estoqueInventarioContagemRepository = estoqueInventarioContagemRepository;
    this.estoqueFornecedorRepository = estoqueFornecedorRepository;
    this.estoquePedidoCompraRepository = estoquePedidoCompraRepository;
    this.estoqueTransferenciaRepository = estoqueTransferenciaRepository;
    this.usuarioRepository = usuarioRepository;
    this.estoqueMovimentacaoService = estoqueMovimentacaoService;
    this.minioStorageService = minioStorageService;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.auditService = auditService;
  }

  // ─── Itens ───────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<ItemEstoqueResponse> listarItens(
      Integer page,
      Integer limit,
      String cursorCreatedAt,
      String cursorId,
      String search,
      Boolean ativo,
      Boolean abaixoMinimo) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cursor cursor = Cursor.parse(cursorCreatedAt, cursorId);
    Specification<ItemEstoque> spec =
        EstoqueSpecifications.itens(
            tenantId, search, ativo, abaixoMinimo, cursor.createdAt(), cursor.id());
    return buscar(itemEstoqueRepository, spec, page, limit).stream()
        .map(EstoqueMovimentacaoService::toItemResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ItemEstoqueResponse buscarItem(UUID id) {
    return toItemResponse(obterItemOuFalhar(id, contextoTenant.obterTenantIdOuFalhar()));
  }

  @Transactional
  public ItemEstoqueResponse criarItem(ItemEstoqueRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ItemEstoque item = new ItemEstoque();
    item.setTenantId(tenantId);
    item.setNome(
        EstoqueTextoUtil.normalizarTextoLivreObrigatorio(request.nome, "Nome do item obrigatorio."));
    item.setSku(EstoqueTextoUtil.normalizarCodigoOpcional(request.sku));
    item.setUnidadeMedida(
        EstoqueTextoUtil.normalizarCodigoObrigatorio(
            request.unidadeMedida, "Unidade de medida obrigatoria."));
    item.setEstoqueMinimo(request.estoqueMinimo);
    item.setAtivo(request.ativo == null ? Boolean.TRUE : request.ativo);
    item.setSaldoAtual(BigDecimal.ZERO);

    // saveAndFlush: o id so existe depois do @PrePersist e vai no payload de auditoria montado
    // logo abaixo (armadilha 2 — o Panache ja teria emitido o INSERT no persist).
    ItemEstoque persistido = itemEstoqueRepository.saveAndFlush(item);
    ItemEstoqueResponse response = toItemResponse(persistido);
    auditarEstoque(tenantId, "STOCK_ITEM_CREATE", "STOCK_ITEM", persistido.getId(), null, response, null);
    return response;
  }

  @Transactional
  public ItemEstoqueResponse atualizarItem(UUID id, ItemEstoqueUpdateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ItemEstoque item = obterItemOuFalhar(id, tenantId);
    ItemEstoqueResponse before = toItemResponse(item);

    // Patch parcial: campo ausente (ou em branco, nos textuais) nao mexe no valor atual.
    if (request.nome != null && !request.nome.isBlank()) {
      item.setNome(
          EstoqueTextoUtil.normalizarTextoLivreObrigatorio(
              request.nome, "Nome do item obrigatorio."));
    }
    if (request.sku != null) {
      item.setSku(EstoqueTextoUtil.normalizarCodigoOpcional(request.sku));
    }
    if (request.unidadeMedida != null && !request.unidadeMedida.isBlank()) {
      item.setUnidadeMedida(
          EstoqueTextoUtil.normalizarCodigoObrigatorio(
              request.unidadeMedida, "Unidade de medida obrigatoria."));
    }
    if (request.estoqueMinimo != null) item.setEstoqueMinimo(request.estoqueMinimo);
    if (request.ativo != null) item.setAtivo(request.ativo);

    ItemEstoque salvo = itemEstoqueRepository.save(item);
    ItemEstoqueResponse response = toItemResponse(salvo);
    auditarEstoque(tenantId, "STOCK_ITEM_UPDATE", "STOCK_ITEM", salvo.getId(), before, response, null);
    return response;
  }

  // ─── Movimentacoes ───────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<MovimentacaoEstoqueResponse> listarMovimentacoes(
      Integer page, Integer limit, String cursorCreatedAt, String cursorId, UUID itemId, String tipo) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cursor cursor = Cursor.parse(cursorCreatedAt, cursorId);

    TipoMovimentacaoEstoque tipoFiltro = null;
    if (tipo != null && !tipo.isBlank()) {
      // O original faz valueOf direto: codigo desconhecido estoura IllegalArgumentException (500).
      // Preservado — so a normalizacao/branco vira 400, como la.
      tipoFiltro =
          TipoMovimentacaoEstoque.valueOf(
              EstoqueTextoUtil.normalizarCodigoObrigatorio(tipo, "Tipo invalido."));
    }

    Specification<MovimentacaoEstoque> spec =
        EstoqueSpecifications.movimentacoes(
            tenantId, itemId, tipoFiltro, cursor.createdAt(), cursor.id());
    List<MovimentacaoEstoque> movimentacoes =
        buscar(movimentacaoEstoqueRepository, spec, page, limit);

    Map<UUID, ItemEstoque> itens =
        carregarItens(
            tenantId,
            movimentacoes.stream().map(MovimentacaoEstoque::getItemEstoqueId).collect(Collectors.toSet()));
    return movimentacoes.stream()
        .map(m -> toMovimentacaoResponse(m, itens.get(m.getItemEstoqueId())))
        .toList();
  }

  /** Delega o calculo de saldo, a auditoria e as validacoes para o motor de movimentacao. */
  public MovimentacaoEstoqueResponse criarMovimentacao(MovimentacaoEstoqueRequest request) {
    return estoqueMovimentacaoService.criarMovimentacao(request);
  }

  // ─── Dashboard ───────────────────────────────────────────────────────────

  /**
   * Porte fiel, incluindo duas assimetrias do original:
   *
   * <ul>
   *   <li>{@code margemServicos} <b>nunca e preenchido</b> — o DTO tem o campo e o original o
   *       devolve sempre vazio;
   *   <li>o recurso aceita {@code inicio}/{@code fim}/{@code serviceId}/{@code itemId} na query
   *       string mas <b>nao os repassa</b>: o dashboard e sempre do tenant inteiro, sem recorte.
   * </ul>
   *
   * <p>{@code itensAbaixoMinimo} usa {@code <=} (item exatamente no minimo ja conta) e
   * {@code perdasValor} soma o valor de <b>toda</b> SAIDA, nao so das perdas.
   */
  @Transactional(readOnly = true)
  public DashboardEstoqueResponse obterDashboard() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<ItemEstoque> itens = itemEstoqueRepository.findByTenantId(tenantId);
    List<MovimentacaoEstoque> movimentacoes = movimentacaoEstoqueRepository.findByTenantId(tenantId);

    DashboardEstoqueResponse response = new DashboardEstoqueResponse();
    response.atualizadoEm = Instant.now().toString();
    response.itensAbaixoMinimo =
        (int)
            itens.stream()
                .filter(i -> nvl(i.getSaldoAtual()).compareTo(nvl(i.getEstoqueMinimo())) <= 0)
                .count();
    response.itensZerados =
        (int)
            itens.stream()
                .filter(i -> nvl(i.getSaldoAtual()).compareTo(BigDecimal.ZERO) <= 0)
                .count();
    response.valorEstoqueCustoMedio =
        itens.stream()
            .map(i -> nvl(i.getSaldoAtual()).multiply(nvl(i.getCustoMedioUnitario())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    response.rupturaTaxa =
        itens.isEmpty() ? 0d : (double) response.itensZerados / (double) itens.size();
    response.perdasValor =
        movimentacoes.stream()
            .filter(m -> m.getTipo() == TipoMovimentacaoEstoque.SAIDA)
            .map(m -> nvl(m.getValorTotalMovimentacao()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return response;
  }

  // ─── Configuracao ────────────────────────────────────────────────────────

  /** Nao e {@code readOnly}: o original cria a linha de configuracao na primeira leitura. */
  @Transactional
  public ConfiguracaoEstoqueResponse obterConfiguracoes() {
    return toConfiguracaoResponse(obterOuCriarConfiguracao(contextoTenant.obterTenantIdOuFalhar()));
  }

  @Transactional
  public ConfiguracaoEstoqueResponse atualizarConfiguracoes(ConfiguracaoEstoqueRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    EstoqueConfiguracao configuracao = obterOuCriarConfiguracao(tenantId);
    ConfiguracaoEstoqueResponse before = toConfiguracaoResponse(configuracao);

    if (request.alertaEstoqueMinimoAtivo != null) {
      configuracao.setAlertaEstoqueMinimoAtivo(request.alertaEstoqueMinimoAtivo);
    }
    if (request.bloquearSaidaSemSaldo != null) {
      configuracao.setBloquearSaidaSemSaldo(request.bloquearSaidaSemSaldo);
    }
    if (request.permitirAjusteNegativoComPermissao != null) {
      configuracao.setPermitirAjusteNegativoComPermissao(request.permitirAjusteNegativoComPermissao);
    }
    if (request.diasCoberturaMeta != null) {
      if (request.diasCoberturaMeta <= 0) {
        throw new ApiClientErrorException(
            "diasCoberturaMeta deve ser maior que zero.", HttpStatus.BAD_REQUEST.value());
      }
      configuracao.setDiasCoberturaMeta(request.diasCoberturaMeta);
    }

    EstoqueConfiguracao salva = estoqueConfiguracaoRepository.save(configuracao);
    ConfiguracaoEstoqueResponse response = toConfiguracaoResponse(salva);
    auditarEstoque(
        tenantId, "STOCK_SETTINGS_UPDATE", "STOCK_SETTINGS", salva.getId(), before, response, null);
    return response;
  }

  // ─── Servico ↔ insumo ────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<ServicoInsumoResponse> listarInsumosPorServico(String serviceId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<ServicoInsumo> insumos =
        servicoInsumoRepository.findByTenantAndService(tenantId, UUID.fromString(serviceId));
    Map<UUID, ItemEstoque> itens =
        carregarItens(
            tenantId,
            insumos.stream().map(ServicoInsumo::getItemEstoqueId).collect(Collectors.toSet()));
    return insumos.stream()
        .map(i -> toServicoInsumoResponse(i, itens.get(i.getItemEstoqueId())))
        .toList();
  }

  /**
   * Reativa o vinculo em vez de duplicar quando ja existe uma linha inativa para o par
   * (servico, item) — por isso {@code findByTenantServiceAndItem} nao filtra {@code ativo}.
   * Vinculo ja ativo e 409.
   */
  @Transactional
  public ServicoInsumoResponse adicionarInsumo(ServicoInsumoRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID serviceId = UUID.fromString(request.serviceId);
    UUID itemId = UUID.fromString(request.itemEstoqueId);

    ItemEstoque item = obterItemOuFalhar(itemId, tenantId);

    ServicoInsumo existente =
        servicoInsumoRepository.findByTenantServiceAndItem(tenantId, serviceId, itemId).orElse(null);
    if (existente != null && Boolean.TRUE.equals(existente.getAtivo())) {
      throw new ApiClientErrorException(
          "Este item ja esta vinculado ao servico.", HttpStatus.CONFLICT.value());
    }
    if (existente != null) {
      existente.setQuantidadeConsumo(request.quantidadeConsumo);
      existente.setPercentualPerda(
          request.percentualPerda != null ? request.percentualPerda : BigDecimal.ZERO);
      existente.setAtivo(Boolean.TRUE);
      return toServicoInsumoResponse(servicoInsumoRepository.save(existente), item);
    }

    ServicoInsumo insumo = new ServicoInsumo();
    insumo.setTenantId(tenantId);
    insumo.setServiceId(serviceId);
    insumo.setItemEstoqueId(itemId);
    insumo.setQuantidadeConsumo(request.quantidadeConsumo);
    insumo.setPercentualPerda(
        request.percentualPerda != null ? request.percentualPerda : BigDecimal.ZERO);
    // saveAndFlush: o id vem do @PrePersist e entra na resposta 201 (armadilha 2).
    return toServicoInsumoResponse(servicoInsumoRepository.saveAndFlush(insumo), item);
  }

  @Transactional
  public ServicoInsumoResponse atualizarInsumo(String insumoId, ServicoInsumoUpdateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ServicoInsumo insumo = obterInsumoOuFalhar(UUID.fromString(insumoId), tenantId);
    if (request.quantidadeConsumo != null) insumo.setQuantidadeConsumo(request.quantidadeConsumo);
    if (request.percentualPerda != null) insumo.setPercentualPerda(request.percentualPerda);
    ServicoInsumo salvo = servicoInsumoRepository.save(insumo);
    return toServicoInsumoResponse(
        salvo, itemEstoqueRepository.findByIdAndTenantId(salvo.getItemEstoqueId(), tenantId).orElse(null));
  }

  /** Remocao logica ({@code ativo = false}), como no original — o historico nao e apagado. */
  @Transactional
  public void removerInsumo(String insumoId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ServicoInsumo insumo = obterInsumoOuFalhar(UUID.fromString(insumoId), tenantId);
    insumo.setAtivo(Boolean.FALSE);
    servicoInsumoRepository.save(insumo);
  }

  // ─── Importacao em massa ─────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<ImportacaoEstoqueJobResponse> listarImportacoes() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return importacaoEstoqueJobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
        .map(ServicoEstoque::toImportacaoJobResponse)
        .toList();
  }

  /**
   * Registra o job com o arquivo <b>ja</b> no storage — o upload acontece no controller, como no
   * original.
   *
   * <p>Duas assimetrias do original preservadas: {@code tipoImportacao} ausente ou em branco vira
   * {@code ENTRADAS} (o default silencioso), mas um valor <b>invalido</b> estoura o
   * {@code IllegalArgumentException} do {@code valueOf} — diferente de
   * {@link #gerarModeloImportacao}, que trata o mesmo erro e devolve 400 com mensagem util.
   */
  @Transactional
  public ImportacaoEstoqueJobResponse criarImportacao(
      String tipoImportacao, Boolean dryRun, String arquivoSha256, String arquivoStorageKey) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ImportacaoEstoqueJob job = new ImportacaoEstoqueJob();
    job.setTenantId(tenantId);
    job.setTipoImportacao(
        tipoImportacao == null || tipoImportacao.isBlank()
            ? TipoImportacaoEstoque.ENTRADAS
            : TipoImportacaoEstoque.valueOf(tipoImportacao));
    job.setDryRun(dryRun != null ? dryRun : Boolean.FALSE);
    job.setTotalLinhas(0);
    job.setLinhasProcessadas(0);
    job.setLinhasComErro(0);
    job.setStatus(StatusImportacaoEstoque.RECEBIDO);
    job.setArquivoSha256(arquivoSha256);
    job.setArquivoStorageKey(arquivoStorageKey);
    // saveAndFlush: o id vem do @PrePersist e entra tanto no payload de auditoria quanto na
    // resposta desta mesma chamada (armadilha 2).
    importacaoEstoqueJobRepository.saveAndFlush(job);
    auditarEstoque(
        tenantId,
        "STOCK_IMPORT_CREATE",
        "STOCK_IMPORT_JOB",
        job.getId(),
        null,
        toImportacaoJobResponse(job),
        null);
    return toImportacaoJobResponse(job);
  }

  @Transactional(readOnly = true)
  public ImportacaoEstoqueJobResponse buscarImportacao(UUID jobId) {
    return toImportacaoJobResponse(obterJobOuFalhar(jobId));
  }

  @Transactional(readOnly = true)
  public List<ImportacaoErroLinhaResponse> listarErrosImportacao(UUID jobId) {
    ImportacaoEstoqueJob job = obterJobOuFalhar(jobId);
    return importacaoEstoqueErroLinhaRepository
        .findByJobIdAndTenantIdOrderByLinhaAsc(job.getId(), job.getTenantId())
        .stream()
        .map(ServicoEstoque::toErroLinhaResponse)
        .toList();
  }

  /**
   * <b>Assimetria do original preservada:</b> o nome promete o "arquivo de resultado", mas a chave
   * devolvida e a do <b>arquivo enviado</b> ({@code arquivoStorageKey}) — a importacao nunca gera
   * arquivo de saida. E como o processador zera essa chave ao finalizar o job, o endpoint so
   * responde 200 enquanto o job ainda nao foi processado; depois vira 404 permanente.
   */
  @Transactional(readOnly = true)
  public ImportacaoResultadoArquivoResponse obterArquivoResultado(UUID jobId) {
    ImportacaoEstoqueJob job = obterJobOuFalhar(jobId);
    if (job.getArquivoStorageKey() == null || job.getArquivoStorageKey().isBlank()) {
      throw naoEncontrado("Arquivo de resultado indisponivel para este job.");
    }
    ImportacaoResultadoArquivoResponse response = new ImportacaoResultadoArquivoResponse();
    try {
      response.downloadUrl =
          minioStorageService.gerarUrlAssinadaLeitura(
              job.getArquivoStorageKey(), job.getTenantId());
      response.expiresAt = minioStorageService.calcularExpiracaoUrlAssinada().toString();
    } catch (IllegalStateException exception) {
      throw new ApiClientErrorException(
          "Storage de importacao indisponivel.", HttpStatus.SERVICE_UNAVAILABLE.value());
    }
    return response;
  }

  @Transactional
  public ImportacaoEstoqueJobResponse cancelarImportacao(UUID jobId) {
    ImportacaoEstoqueJob job = obterJobOuFalhar(jobId);
    ImportacaoEstoqueJobResponse before = toImportacaoJobResponse(job);
    if (!statusPermiteCancelamento(job.getStatus())) {
      throw conflito("Job ja finalizado.");
    }
    job.setStatus(StatusImportacaoEstoque.CANCELADO);
    job.setFinishedAt(Instant.now());
    importacaoEstoqueJobRepository.save(job);
    auditarEstoque(
        job.getTenantId(),
        "STOCK_IMPORT_CANCEL",
        "STOCK_IMPORT_JOB",
        job.getId(),
        before,
        toImportacaoJobResponse(job),
        null);
    return toImportacaoJobResponse(job);
  }

  /**
   * Modelo de planilha para preenchimento. Nao toca no banco.
   *
   * <p><b>Assimetria do original preservada:</b> {@code tipoImportacao} aqui e obrigatorio e
   * case-sensitive (so {@code trim}, sem {@code toUpperCase}) — {@code "itens"} minusculo e 400,
   * enquanto {@code formato} aceita qualquer caixa. E o modelo pode ser baixado em CSV, mas o
   * processador so entende XLSX de volta.
   */
  public ModeloImportacaoArquivo gerarModeloImportacao(String tipoImportacaoRaw, String formatoRaw) {
    TipoImportacaoEstoque tipoImportacao;
    try {
      tipoImportacao =
          TipoImportacaoEstoque.valueOf(tipoImportacaoRaw == null ? "" : tipoImportacaoRaw.trim());
    } catch (RuntimeException exception) {
      throw new ApiClientErrorException(
          "tipoImportacao invalido. Use ITENS, ENTRADAS ou AJUSTES.", HttpStatus.BAD_REQUEST.value());
    }

    String formato =
        formatoRaw == null || formatoRaw.isBlank()
            ? "xlsx"
            : EstoqueTextoUtil.normalizarTextoBase(formatoRaw).toLowerCase(Locale.ROOT);
    if (!"xlsx".equals(formato) && !"csv".equals(formato)) {
      throw new ApiClientErrorException(
          "formato invalido. Use xlsx ou csv.", HttpStatus.BAD_REQUEST.value());
    }

    List<String> cabecalho = cabecalhoModelo(tipoImportacao);
    List<String> exemplo = linhaExemploModelo(tipoImportacao);
    ModeloImportacaoArquivo modelo = new ModeloImportacaoArquivo();
    modelo.nomeArquivo =
        "modelo-importacao-" + tipoImportacao.name().toLowerCase(Locale.ROOT) + "." + formato;

    if ("csv".equals(formato)) {
      modelo.contentType = "text/csv";
      modelo.conteudo = gerarCsv(cabecalho, exemplo);
      return modelo;
    }

    modelo.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    modelo.conteudo = gerarXlsx(cabecalho, exemplo);
    return modelo;
  }

  private ImportacaoEstoqueJob obterJobOuFalhar(UUID jobId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return importacaoEstoqueJobRepository
        .findByIdAndTenantId(jobId, tenantId)
        .orElseThrow(() -> naoEncontrado("Job de importacao nao encontrado."));
  }

  /** Terminal nao cancela: {@code CONCLUIDO}, {@code CONCLUIDO_COM_ERROS}, {@code FALHOU} e {@code CANCELADO}. */
  static boolean statusPermiteCancelamento(StatusImportacaoEstoque status) {
    if (status == null) return false;
    return status != StatusImportacaoEstoque.CONCLUIDO
        && status != StatusImportacaoEstoque.CONCLUIDO_COM_ERROS
        && status != StatusImportacaoEstoque.FALHOU
        && status != StatusImportacaoEstoque.CANCELADO;
  }

  private static List<String> cabecalhoModelo(TipoImportacaoEstoque tipoImportacao) {
    if (tipoImportacao == TipoImportacaoEstoque.ITENS) {
      return List.of("nome", "sku", "unidadeMedida", "estoqueMinimo", "ativo");
    }
    if (tipoImportacao == TipoImportacaoEstoque.AJUSTES) {
      return List.of("sku", "quantidade", "motivo", "origem", "dataMovimento");
    }
    return List.of(
        "sku",
        "quantidade",
        "unidadeMedida",
        "valorUnitarioPago",
        "motivo",
        "gerarLancamentoFinanceiro",
        "categoriaFinanceira",
        "formaPagamento",
        "dataMovimento");
  }

  private static List<String> linhaExemploModelo(TipoImportacaoEstoque tipoImportacao) {
    if (tipoImportacao == TipoImportacaoEstoque.ITENS) {
      return List.of("Shampoo Profissional", "SHAMP-001", "ML", "500", "true");
    }
    if (tipoImportacao == TipoImportacaoEstoque.AJUSTES) {
      return List.of("SHAMP-001", "25", "Ajuste inventario", "INVENTARIO", "2026-02-28");
    }
    return List.of(
        "SHAMP-001",
        "1000",
        "ML",
        "0.45",
        "Reposicao mensal",
        "true",
        "Compra de insumos",
        "PIX",
        "2026-02-28");
  }

  /**
   * CSV sem escaping, como no original: os valores de exemplo nao tem virgula, entao a juncao crua
   * por {@code ","} nunca quebra na pratica.
   */
  private static byte[] gerarCsv(List<String> cabecalho, List<String> exemplo) {
    StringBuilder csv = new StringBuilder();
    csv.append(String.join(",", cabecalho)).append('\n');
    csv.append(String.join(",", exemplo)).append('\n');
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] gerarXlsx(List<String> cabecalho, List<String> exemplo) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      XSSFSheet sheet = workbook.createSheet("modelo");
      Row cabecalhoRow = sheet.createRow(0);
      for (int i = 0; i < cabecalho.size(); i++) {
        cabecalhoRow.createCell(i).setCellValue(cabecalho.get(i));
      }
      Row exemploRow = sheet.createRow(1);
      for (int i = 0; i < exemplo.size(); i++) {
        exemploRow.createCell(i).setCellValue(exemplo.get(i));
      }
      for (int i = 0; i < cabecalho.size(); i++) {
        int maxLen =
            Math.max(cabecalho.get(i).length(), i < exemplo.size() ? exemplo.get(i).length() : 0);
        sheet.setColumnWidth(i, Math.max(maxLen * 400 + 2000, 4000));
      }
      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao gerar modelo de importacao.", exception);
    }
  }

  private static ImportacaoEstoqueJobResponse toImportacaoJobResponse(ImportacaoEstoqueJob entity) {
    ImportacaoEstoqueJobResponse response = new ImportacaoEstoqueJobResponse();
    response.jobId = entity.getId().toString();
    response.tipoImportacao = entity.getTipoImportacao().name();
    response.status = entity.getStatus().name();
    response.dryRun = entity.getDryRun();
    response.totalLinhas = entity.getTotalLinhas() == null ? 0 : entity.getTotalLinhas();
    response.linhasProcessadas =
        entity.getLinhasProcessadas() == null ? 0 : entity.getLinhasProcessadas();
    response.linhasComErro = entity.getLinhasComErro() == null ? 0 : entity.getLinhasComErro();
    response.arquivoSha256 = entity.getArquivoSha256();
    response.arquivoStorageKey = entity.getArquivoStorageKey();
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    response.finishedAt = entity.getFinishedAt() != null ? entity.getFinishedAt().toString() : null;
    return response;
  }

  private static ImportacaoErroLinhaResponse toErroLinhaResponse(ImportacaoEstoqueErroLinha entity) {
    ImportacaoErroLinhaResponse response = new ImportacaoErroLinhaResponse();
    response.linha = entity.getLinha();
    response.coluna = entity.getColuna();
    response.codigoErro = entity.getCodigoErro();
    response.mensagem = entity.getMensagem();
    response.valorRecebido = entity.getValorRecebido();
    return response;
  }

  // ─── Inventario / contagem ───────────────────────────────────────────────

  /**
   * Unica listagem do recurso com envelope paginado em vez de lista crua, e a unica <b>sem</b>
   * cursor. Tres comportamentos do original preservados:
   *
   * <ul>
   *   <li>{@code page} nulo/zero/negativo cai para 1 e {@code limit} idem cai para 20 — aqui nunca
   *       existe "sem paginacao", ao contrario das outras listagens;
   *   <li>{@code status} desconhecido e <b>silenciosamente ignorado</b> (o {@code valueOf} do
   *       original esta dentro de um {@code try/catch} vazio) — diferente de
   *       {@link #listarMovimentacoes}, onde {@code tipo} invalido estoura;
   *   <li>{@code totalPages} e <b>1</b> quando nao ha nenhum registro, nao 0.
   * </ul>
   */
  @Transactional(readOnly = true)
  public InventarioEstoquePageResponse listarInventarios(
      Integer page, Integer limit, String search, String status) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    StatusInventarioEstoque statusFiltro = null;
    if (status != null && !status.isBlank()) {
      try {
        statusFiltro = StatusInventarioEstoque.valueOf(status.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        // Status desconhecido nao filtra nada, como no original.
      }
    }

    Specification<EstoqueInventario> spec =
        EstoqueSpecifications.inventarios(tenantId, search, statusFiltro);
    int pageNum = (page != null && page > 0) ? page : 1;
    int pageSize = (limit != null && limit > 0) ? limit : 20;
    long total = estoqueInventarioRepository.count(spec);
    int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / pageSize);

    Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    List<InventarioEstoqueResponse> items =
        estoqueInventarioRepository
            .findAll(spec, PageRequest.of(pageNum - 1, pageSize, sort))
            .getContent()
            .stream()
            .map(ServicoEstoque::toInventarioResponse)
            .toList();

    InventarioEstoquePageResponse response = new InventarioEstoquePageResponse();
    response.items = items;
    response.page = pageNum;
    response.totalPages = totalPages;
    response.total = total;
    response.hasNext = pageNum < totalPages;
    return response;
  }

  /** O status gravado e {@code EM_CONTAGEM}, nao o {@code ABERTO} do {@code @PrePersist}. */
  @Transactional
  public InventarioEstoqueResponse criarInventario(InventarioEstoqueRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    EstoqueInventario inventario = new EstoqueInventario();
    inventario.setTenantId(tenantId);
    inventario.setNome(
        EstoqueTextoUtil.normalizarTextoLivreObrigatorio(
            request.nome, "Nome do inventario obrigatorio."));
    inventario.setObservacao(EstoqueTextoUtil.normalizarTextoBase(request.observacao));
    inventario.setStatus(StatusInventarioEstoque.EM_CONTAGEM);

    // saveAndFlush: id, dataAbertura, createdAt e updatedAt vem do @PrePersist e entram tanto na
    // resposta quanto no payload de auditoria montado abaixo (armadilha 2).
    EstoqueInventario persistido = estoqueInventarioRepository.saveAndFlush(inventario);
    InventarioEstoqueResponse response = toInventarioResponse(persistido);
    auditarEstoque(
        tenantId, "STOCK_INVENTORY_CREATE", "STOCK_INVENTORY", persistido.getId(), null, response, null);
    return response;
  }

  @Transactional(readOnly = true)
  public InventarioEstoqueResponse buscarInventario(UUID id) {
    return toInventarioResponse(obterInventarioOuFalhar(id));
  }

  /**
   * Devolve o <b>inventario</b>, nao a contagem recem-criada — assimetria do original, preservada
   * (compare com {@link #atualizarContagemInventario}, que devolve a contagem).
   *
   * <p>Um item ja contado neste inventario e 409: a segunda contagem exige editar a primeira.
   */
  @Transactional
  public InventarioEstoqueResponse registrarContagemInventario(
      UUID inventarioId, InventarioContagemRequest request) {
    EstoqueInventario inventario = obterInventarioOuFalhar(inventarioId);
    if (inventario.getStatus() == StatusInventarioEstoque.FECHADO
        || inventario.getStatus() == StatusInventarioEstoque.CANCELADO) {
      throw conflito("Inventario ja finalizado.");
    }

    UUID itemId = UUID.fromString(request.itemEstoqueId);
    UUID tenantId = inventario.getTenantId();
    ItemEstoque item = obterItemOuFalhar(itemId, tenantId);

    boolean jaContado =
        estoqueInventarioContagemRepository.countByInventarioIdAndItemEstoqueIdAndTenantId(
                inventario.getId(), item.getId(), tenantId)
            > 0;
    if (jaContado) {
      throw conflito("Este item ja possui contagem neste inventario. Edite a contagem existente.");
    }

    EstoqueInventarioContagem contagem = new EstoqueInventarioContagem();
    contagem.setTenantId(tenantId);
    contagem.setInventarioId(inventario.getId());
    contagem.setItemEstoqueId(item.getId());
    contagem.setQuantidadeContada(request.quantidadeContada);
    contagem.setQuantidadeEsperada(
        item.getSaldoAtual() != null ? item.getSaldoAtual() : BigDecimal.ZERO);
    contagem.setObservacao(EstoqueTextoUtil.normalizarTextoBase(request.observacao));
    contagem.setUsuarioId(authenticatedUser.idOuNulo());

    // saveAndFlush: o id do @PrePersist e o entityId do evento de auditoria (armadilha 2).
    EstoqueInventarioContagem persistida = estoqueInventarioContagemRepository.saveAndFlush(contagem);

    Map<String, Object> after = new HashMap<>();
    after.put("inventarioId", inventario.getId() != null ? inventario.getId().toString() : null);
    after.put("itemEstoqueId", item.getId() != null ? item.getId().toString() : null);
    after.put("quantidadeEsperada", persistida.getQuantidadeEsperada());
    after.put("quantidadeContada", persistida.getQuantidadeContada());
    after.put("observacao", persistida.getObservacao());
    auditarEstoque(
        tenantId,
        "STOCK_INVENTORY_COUNT_REGISTER",
        "STOCK_INVENTORY_COUNT",
        persistida.getId(),
        null,
        after,
        null);
    return toInventarioResponse(inventario);
  }

  @Transactional(readOnly = true)
  public List<InventarioContagemResponse> listarContagensInventario(UUID inventarioId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    // obterInventarioOuFalhar ja filtra por tenant; o original ainda reconfere e o porte mantem.
    EstoqueInventario inventario = obterInventarioOuFalhar(inventarioId);
    if (!inventario.getTenantId().equals(tenantId)) {
      throw naoEncontrado("Inventario nao encontrado.");
    }
    List<EstoqueInventarioContagem> contagens =
        estoqueInventarioContagemRepository.findByInventarioIdAndTenantIdOrderByCreatedAtDesc(
            inventarioId, tenantId);
    Map<UUID, ItemEstoque> itens =
        carregarItens(
            tenantId,
            contagens.stream()
                .map(EstoqueInventarioContagem::getItemEstoqueId)
                .collect(Collectors.toSet()));
    return contagens.stream()
        .map(c -> toContagemResponse(c, itens.get(c.getItemEstoqueId())))
        .toList();
  }

  /** {@code updatedAt} e carimbado aqui, na mao: a entidade de contagem nao tem {@code @PreUpdate}. */
  @Transactional
  public InventarioContagemResponse atualizarContagemInventario(
      UUID inventarioId, UUID contagemId, AtualizarContagemInventarioRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    EstoqueInventario inventario = obterInventarioOuFalhar(inventarioId);
    if (!inventario.getTenantId().equals(tenantId)) {
      throw naoEncontrado("Inventario nao encontrado.");
    }
    if (inventario.getStatus() == StatusInventarioEstoque.FECHADO
        || inventario.getStatus() == StatusInventarioEstoque.CANCELADO) {
      throw conflito("Inventario ja finalizado. Nao e possivel editar contagens.");
    }
    EstoqueInventarioContagem contagem =
        estoqueInventarioContagemRepository
            .findByIdAndInventarioIdAndTenantId(contagemId, inventarioId, tenantId)
            .orElseThrow(() -> naoEncontrado("Contagem nao encontrada."));

    Map<String, Object> before = new HashMap<>();
    before.put("quantidadeContada", contagem.getQuantidadeContada());
    before.put("observacao", contagem.getObservacao());

    contagem.setQuantidadeContada(request.quantidadeContada);
    contagem.setObservacao(EstoqueTextoUtil.normalizarTextoBase(request.observacao));
    contagem.setUpdatedAt(Instant.now());
    contagem.setUsuarioAtualizacaoId(authenticatedUser.idOuNulo());
    EstoqueInventarioContagem salva = estoqueInventarioContagemRepository.save(contagem);

    Map<String, Object> after = new HashMap<>();
    after.put("quantidadeContada", salva.getQuantidadeContada());
    after.put("observacao", salva.getObservacao());
    auditarEstoque(
        tenantId,
        "STOCK_INVENTORY_COUNT_UPDATE",
        "STOCK_INVENTORY_COUNT",
        salva.getId(),
        before,
        after,
        null);
    return toContagemResponse(
        salva,
        itemEstoqueRepository.findByIdAndTenantId(salva.getItemEstoqueId(), tenantId).orElse(null));
  }

  /**
   * Idempotente para {@code FECHADO} (devolve como esta, sem auditar). <b>Nao</b> bloqueia
   * {@code CANCELADO}: um inventario cancelado pode ser fechado por este endpoint — assimetria do
   * original, preservada.
   */
  @Transactional
  public InventarioEstoqueResponse fecharInventario(UUID inventarioId) {
    EstoqueInventario inventario = obterInventarioOuFalhar(inventarioId);
    InventarioEstoqueResponse before = toInventarioResponse(inventario);
    if (inventario.getStatus() == StatusInventarioEstoque.FECHADO) {
      return toInventarioResponse(inventario);
    }
    inventario.setStatus(StatusInventarioEstoque.FECHADO);
    inventario.setDataFechamento(Instant.now());
    EstoqueInventario salvo = estoqueInventarioRepository.save(inventario);
    InventarioEstoqueResponse response = toInventarioResponse(salvo);
    auditarEstoque(
        salvo.getTenantId(),
        "STOCK_INVENTORY_CLOSE",
        "STOCK_INVENTORY",
        salvo.getId(),
        before,
        response,
        null);
    return response;
  }

  /**
   * Unico endpoint do modulo que <b>reconfere a senha</b> do usuario logado (BCrypt
   * {@code at.favre.lib}, o mesmo verificador de {@code AuthServiceImpl.login} — nao ha uma segunda
   * implementacao de hash no projeto). Senha errada e <b>401</b>.
   *
   * <p>A ordem do original e preservada: identidade e senha antes de sequer carregar o inventario.
   * Inventario ja fechado e 409; ja cancelado devolve como esta, sem auditar.
   */
  @Transactional
  public InventarioEstoqueResponse cancelarInventario(
      UUID inventarioId, CancelarInventarioRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = authenticatedUser.idOuNulo();
    if (userId == null) throw naoEncontrado("Usuario nao identificado.");

    Usuario usuario =
        usuarioRepository
            .findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> naoEncontrado("Usuario nao encontrado."));

    boolean senhaOk =
        BCrypt.verifyer().verify(request.senha.toCharArray(), usuario.getPasswordHash()).verified;
    if (!senhaOk) {
      throw new ApiClientErrorException("Senha incorreta.", HttpStatus.UNAUTHORIZED.value());
    }

    EstoqueInventario inventario = obterInventarioOuFalhar(inventarioId);
    if (!inventario.getTenantId().equals(tenantId)) {
      throw naoEncontrado("Inventario nao encontrado.");
    }
    if (inventario.getStatus() == StatusInventarioEstoque.FECHADO) {
      throw conflito("Inventario ja fechado. Nao e possivel cancelar.");
    }
    if (inventario.getStatus() == StatusInventarioEstoque.CANCELADO) {
      return toInventarioResponse(inventario);
    }

    InventarioEstoqueResponse before = toInventarioResponse(inventario);
    inventario.setStatus(StatusInventarioEstoque.CANCELADO);
    EstoqueInventario salvo = estoqueInventarioRepository.save(inventario);

    Map<String, Object> after = new HashMap<>();
    after.put("status", "CANCELADO");
    after.put("motivo", request.motivo);
    auditarEstoque(
        tenantId, "STOCK_INVENTORY_CANCEL", "STOCK_INVENTORY", salvo.getId(), before, after, null);
    return toInventarioResponse(salvo);
  }

  // ─── Fornecedor ──────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<FornecedorEstoqueResponse> listarFornecedores(
      Integer page, Integer limit, String cursorCreatedAt, String cursorId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cursor cursor = Cursor.parse(cursorCreatedAt, cursorId);
    Specification<EstoqueFornecedor> spec =
        EstoqueSpecifications.porTenantComCursor(tenantId, cursor.createdAt(), cursor.id());
    return buscar(estoqueFornecedorRepository, spec, page, limit).stream()
        .map(ServicoEstoque::toFornecedorResponse)
        .toList();
  }

  @Transactional
  public FornecedorEstoqueResponse criarFornecedor(FornecedorEstoqueRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    EstoqueFornecedor fornecedor = new EstoqueFornecedor();
    fornecedor.setTenantId(tenantId);
    aplicarDadosFornecedor(fornecedor, request);
    fornecedor.setAtivo(request.ativo == null ? Boolean.TRUE : request.ativo);

    // saveAndFlush: id e timestamps vem do @PrePersist e entram na resposta e na auditoria.
    EstoqueFornecedor persistido = estoqueFornecedorRepository.saveAndFlush(fornecedor);
    FornecedorEstoqueResponse response = toFornecedorResponse(persistido);
    auditarEstoque(
        tenantId, "STOCK_SUPPLIER_CREATE", "STOCK_SUPPLIER", persistido.getId(), null, response, null);
    return response;
  }

  /**
   * Substituicao total, nao patch: {@code documento}, {@code email}, {@code telefone} e
   * {@code contato} ausentes no corpo <b>limpam</b> o valor gravado. So {@code ativo} e opcional.
   * Comportamento do original, preservado.
   */
  @Transactional
  public FornecedorEstoqueResponse atualizarFornecedor(
      UUID fornecedorId, FornecedorEstoqueRequest request) {
    EstoqueFornecedor fornecedor = obterFornecedorOuFalhar(fornecedorId);
    FornecedorEstoqueResponse before = toFornecedorResponse(fornecedor);
    aplicarDadosFornecedor(fornecedor, request);
    if (request.ativo != null) {
      fornecedor.setAtivo(request.ativo);
    }
    EstoqueFornecedor salvo = estoqueFornecedorRepository.save(fornecedor);
    FornecedorEstoqueResponse response = toFornecedorResponse(salvo);
    auditarEstoque(
        salvo.getTenantId(),
        "STOCK_SUPPLIER_UPDATE",
        "STOCK_SUPPLIER",
        salvo.getId(),
        before,
        response,
        null);
    return response;
  }

  // ─── Pedido de compra ────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<PedidoCompraEstoqueResponse> listarPedidosCompra(
      Integer page, Integer limit, String cursorCreatedAt, String cursorId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cursor cursor = Cursor.parse(cursorCreatedAt, cursorId);
    Specification<EstoquePedidoCompra> spec =
        EstoqueSpecifications.porTenantComCursor(tenantId, cursor.createdAt(), cursor.id());
    List<EstoquePedidoCompra> pedidos = buscar(estoquePedidoCompraRepository, spec, page, limit);
    Map<UUID, EstoqueFornecedor> fornecedores =
        carregarFornecedores(
            tenantId,
            pedidos.stream().map(EstoquePedidoCompra::getFornecedorId).collect(Collectors.toSet()));
    return pedidos.stream()
        .map(p -> toPedidoCompraResponse(p, fornecedores.get(p.getFornecedorId())))
        .toList();
  }

  /** O tenant do pedido vem do fornecedor resolvido, nao do contexto — como no original. */
  @Transactional
  public PedidoCompraEstoqueResponse criarPedidoCompra(PedidoCompraEstoqueRequest request) {
    EstoqueFornecedor fornecedor = obterFornecedorOuFalhar(UUID.fromString(request.fornecedorId));
    EstoquePedidoCompra pedido = new EstoquePedidoCompra();
    pedido.setTenantId(fornecedor.getTenantId());
    pedido.setFornecedorId(fornecedor.getId());
    pedido.setStatus(StatusPedidoCompraEstoque.RASCUNHO);
    pedido.setValorTotal(nvl(request.valorTotal));
    pedido.setQuantidadeItens(request.quantidadeItens);
    pedido.setQuantidadePendente(request.quantidadeItens);
    pedido.setObservacao(EstoqueTextoUtil.normalizarTextoBase(request.observacao));

    // saveAndFlush: id e timestamps vem do @PrePersist e entram na resposta e na auditoria.
    EstoquePedidoCompra persistido = estoquePedidoCompraRepository.saveAndFlush(pedido);
    PedidoCompraEstoqueResponse response = toPedidoCompraResponse(persistido, fornecedor);
    auditarEstoque(
        persistido.getTenantId(),
        "STOCK_PURCHASE_CREATE",
        "STOCK_PURCHASE_ORDER",
        persistido.getId(),
        null,
        response,
        null);
    return response;
  }

  @Transactional(readOnly = true)
  public PedidoCompraEstoqueResponse buscarPedidoCompra(UUID pedidoId) {
    EstoquePedidoCompra pedido = obterPedidoCompraOuFalhar(pedidoId);
    return toPedidoCompraResponse(pedido, resolverFornecedor(pedido));
  }

  /**
   * Recebimento parcial ou total. Duas particularidades do original, preservadas:
   * {@code quantidadeRecebida} nao positiva e <b>400</b> (a validacao de bean so exige
   * {@code @NotNull}), acima da pendente e <b>409</b>; e a {@code observacao} do corpo
   * <b>substitui</b> a do pedido mesmo quando vem nula.
   */
  @Transactional
  public PedidoCompraEstoqueResponse receberPedidoCompra(
      UUID pedidoId, PedidoCompraRecebimentoRequest request) {
    EstoquePedidoCompra pedido = obterPedidoCompraOuFalhar(pedidoId);
    EstoqueFornecedor fornecedor = resolverFornecedor(pedido);
    PedidoCompraEstoqueResponse before = toPedidoCompraResponse(pedido, fornecedor);

    int quantidadeRecebida = request.quantidadeRecebida == null ? 0 : request.quantidadeRecebida;
    if (quantidadeRecebida <= 0) {
      throw new ApiClientErrorException(
          "quantidadeRecebida deve ser maior que zero.", HttpStatus.BAD_REQUEST.value());
    }
    if (pedido.getQuantidadePendente() == null) pedido.setQuantidadePendente(0);
    if (quantidadeRecebida > pedido.getQuantidadePendente()) {
      throw conflito("Quantidade recebida maior que a pendente.");
    }

    pedido.setQuantidadePendente(pedido.getQuantidadePendente() - quantidadeRecebida);
    pedido.setObservacao(EstoqueTextoUtil.normalizarTextoBase(request.observacao));
    pedido.setStatus(
        pedido.getQuantidadePendente() == 0
            ? StatusPedidoCompraEstoque.RECEBIDO
            : StatusPedidoCompraEstoque.PARCIALMENTE_RECEBIDO);
    EstoquePedidoCompra salvo = estoquePedidoCompraRepository.save(pedido);

    PedidoCompraEstoqueResponse response = toPedidoCompraResponse(salvo, fornecedor);
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("quantidadeRecebida", quantidadeRecebida);
    auditarEstoque(
        salvo.getTenantId(),
        "STOCK_PURCHASE_RECEIVE",
        "STOCK_PURCHASE_ORDER",
        salvo.getId(),
        before,
        response,
        metadata);
    return response;
  }

  // ─── Transferencia ───────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<TransferenciaEstoqueResponse> listarTransferencias(
      Integer page, Integer limit, String cursorCreatedAt, String cursorId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cursor cursor = Cursor.parse(cursorCreatedAt, cursorId);
    Specification<EstoqueTransferencia> spec =
        EstoqueSpecifications.porTenantComCursor(tenantId, cursor.createdAt(), cursor.id());
    List<EstoqueTransferencia> transferencias =
        buscar(estoqueTransferenciaRepository, spec, page, limit);
    Map<UUID, ItemEstoque> itens =
        carregarItens(
            tenantId,
            transferencias.stream()
                .map(EstoqueTransferencia::getItemEstoqueId)
                .collect(Collectors.toSet()));
    return transferencias.stream()
        .map(t -> toTransferenciaResponse(t, itens.get(t.getItemEstoqueId())))
        .toList();
  }

  @Transactional
  public TransferenciaEstoqueResponse criarTransferencia(TransferenciaEstoqueRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ItemEstoque item = obterItemOuFalhar(UUID.fromString(request.itemEstoqueId), tenantId);

    EstoqueTransferencia transferencia = new EstoqueTransferencia();
    transferencia.setTenantId(tenantId);
    transferencia.setOrigem(
        EstoqueTextoUtil.normalizarTextoLivreObrigatorio(request.origem, "Origem obrigatoria."));
    transferencia.setDestino(
        EstoqueTextoUtil.normalizarTextoLivreObrigatorio(request.destino, "Destino obrigatorio."));
    transferencia.setItemEstoqueId(item.getId());
    transferencia.setQuantidade(request.quantidade);
    transferencia.setObservacao(EstoqueTextoUtil.normalizarTextoBase(request.observacao));
    transferencia.setStatus(StatusTransferenciaEstoque.RASCUNHO);

    // saveAndFlush: id e timestamps vem do @PrePersist e entram na resposta e na auditoria.
    EstoqueTransferencia persistida = estoqueTransferenciaRepository.saveAndFlush(transferencia);
    TransferenciaEstoqueResponse response = toTransferenciaResponse(persistida, item);
    auditarEstoque(
        tenantId, "STOCK_TRANSFER_CREATE", "STOCK_TRANSFER", persistida.getId(), null, response, null);
    return response;
  }

  /** So promove {@code RASCUNHO} → {@code ENVIADA}; qualquer outro status e devolvido intacto. */
  @Transactional
  public TransferenciaEstoqueResponse enviarTransferencia(UUID transferenciaId) {
    EstoqueTransferencia transferencia = obterTransferenciaOuFalhar(transferenciaId);
    ItemEstoque item = resolverItemDaTransferencia(transferencia);
    TransferenciaEstoqueResponse before = toTransferenciaResponse(transferencia, item);
    if (transferencia.getStatus() != StatusTransferenciaEstoque.RASCUNHO) {
      return before;
    }
    transferencia.setStatus(StatusTransferenciaEstoque.ENVIADA);
    EstoqueTransferencia salva = estoqueTransferenciaRepository.save(transferencia);
    TransferenciaEstoqueResponse response = toTransferenciaResponse(salva, item);
    auditarEstoque(
        salva.getTenantId(),
        "STOCK_TRANSFER_SEND",
        "STOCK_TRANSFER",
        salva.getId(),
        before,
        response,
        null);
    return response;
  }

  /**
   * Marca {@code RECEBIDA} <b>sem checar o status anterior</b>: um rascunho pode ir direto para
   * recebida, e receber duas vezes audita duas vezes. Assimetria do original (compare com
   * {@link #enviarTransferencia}, que so age no rascunho), preservada.
   */
  @Transactional
  public TransferenciaEstoqueResponse receberTransferencia(UUID transferenciaId) {
    EstoqueTransferencia transferencia = obterTransferenciaOuFalhar(transferenciaId);
    ItemEstoque item = resolverItemDaTransferencia(transferencia);
    TransferenciaEstoqueResponse before = toTransferenciaResponse(transferencia, item);
    transferencia.setStatus(StatusTransferenciaEstoque.RECEBIDA);
    EstoqueTransferencia salva = estoqueTransferenciaRepository.save(transferencia);
    TransferenciaEstoqueResponse response = toTransferenciaResponse(salva, item);
    auditarEstoque(
        salva.getTenantId(),
        "STOCK_TRANSFER_RECEIVE",
        "STOCK_TRANSFER",
        salva.getId(),
        before,
        response,
        null);
    return response;
  }

  // ─── Auxiliares ──────────────────────────────────────────────────────────

  private ItemEstoque obterItemOuFalhar(UUID id, UUID tenantId) {
    return itemEstoqueRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(
            () ->
                new ApiClientErrorException(
                    "Item de estoque nao encontrado.", HttpStatus.NOT_FOUND.value()));
  }

  private ServicoInsumo obterInsumoOuFalhar(UUID id, UUID tenantId) {
    return servicoInsumoRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(
            () ->
                new ApiClientErrorException(
                    "Vinculo servico-insumo nao encontrado.", HttpStatus.NOT_FOUND.value()));
  }

  private EstoqueInventario obterInventarioOuFalhar(UUID inventarioId) {
    return estoqueInventarioRepository
        .findByIdAndTenantId(inventarioId, contextoTenant.obterTenantIdOuFalhar())
        .orElseThrow(() -> naoEncontrado("Inventario nao encontrado."));
  }

  private EstoqueFornecedor obterFornecedorOuFalhar(UUID fornecedorId) {
    return estoqueFornecedorRepository
        .findByIdAndTenantId(fornecedorId, contextoTenant.obterTenantIdOuFalhar())
        .orElseThrow(() -> naoEncontrado("Fornecedor nao encontrado."));
  }

  private EstoquePedidoCompra obterPedidoCompraOuFalhar(UUID pedidoId) {
    return estoquePedidoCompraRepository
        .findByIdAndTenantId(pedidoId, contextoTenant.obterTenantIdOuFalhar())
        .orElseThrow(() -> naoEncontrado("Pedido de compra nao encontrado."));
  }

  private EstoqueTransferencia obterTransferenciaOuFalhar(UUID transferenciaId) {
    return estoqueTransferenciaRepository
        .findByIdAndTenantId(transferenciaId, contextoTenant.obterTenantIdOuFalhar())
        .orElseThrow(() -> naoEncontrado("Transferencia nao encontrada."));
  }

  /** O original navega {@code pedido.fornecedor}; aqui o id escalar e resolvido no repositorio. */
  private EstoqueFornecedor resolverFornecedor(EstoquePedidoCompra pedido) {
    if (pedido.getFornecedorId() == null) return null;
    return estoqueFornecedorRepository
        .findByIdAndTenantId(pedido.getFornecedorId(), pedido.getTenantId())
        .orElse(null);
  }

  /** O original navega {@code transferencia.itemEstoque}; aqui vem do repositorio. */
  private ItemEstoque resolverItemDaTransferencia(EstoqueTransferencia transferencia) {
    if (transferencia.getItemEstoqueId() == null) return null;
    return itemEstoqueRepository
        .findByIdAndTenantId(transferencia.getItemEstoqueId(), transferencia.getTenantId())
        .orElse(null);
  }

  private Map<UUID, EstoqueFornecedor> carregarFornecedores(UUID tenantId, Set<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return estoqueFornecedorRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
        .collect(Collectors.toMap(EstoqueFornecedor::getId, fornecedor -> fornecedor));
  }

  /** Os cinco campos textuais que {@code criar} e {@code atualizar} tratam identicamente. */
  private void aplicarDadosFornecedor(
      EstoqueFornecedor fornecedor, FornecedorEstoqueRequest request) {
    fornecedor.setNome(
        EstoqueTextoUtil.normalizarTextoLivreObrigatorio(
            request.nome, "Nome do fornecedor obrigatorio."));
    fornecedor.setDocumento(EstoqueTextoUtil.normalizarTextoBase(request.documento));
    fornecedor.setEmail(EstoqueTextoUtil.normalizarTextoBase(request.email));
    fornecedor.setTelefone(EstoqueTextoUtil.normalizarTextoBase(request.telefone));
    fornecedor.setContato(EstoqueTextoUtil.normalizarTextoBase(request.contato));
  }

  private ApiClientErrorException naoEncontrado(String mensagem) {
    return new ApiClientErrorException(mensagem, HttpStatus.NOT_FOUND.value());
  }

  /** Porte de {@code ServicoEstoque.conflito}: mensagem + 409. */
  private ApiClientErrorException conflito(String mensagem) {
    return new ApiClientErrorException(mensagem, HttpStatus.CONFLICT.value());
  }

  private EstoqueConfiguracao obterOuCriarConfiguracao(UUID tenantId) {
    return estoqueConfiguracaoRepository
        .findByTenantId(tenantId)
        .orElseGet(
            () -> {
              EstoqueConfiguracao configuracao = new EstoqueConfiguracao();
              configuracao.setTenantId(tenantId);
              // saveAndFlush: o @PrePersist e quem preenche id, defaults e updatedAt, e todos
              // entram na resposta desta mesma chamada (armadilha 2).
              return estoqueConfiguracaoRepository.saveAndFlush(configuracao);
            });
  }

  private Map<UUID, ItemEstoque> carregarItens(UUID tenantId, Set<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return itemEstoqueRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
        .collect(Collectors.toMap(ItemEstoque::getId, item -> item));
  }

  /**
   * Porte de {@code aplicarPaginacao}: {@code page}/{@code limit} nulos, zerados ou negativos
   * significam "sem paginacao" (o original devolve a lista inteira). A ordenacao
   * {@code createdAt desc, id desc} e sempre aplicada, com ou sem paginacao — e o que faz o cursor
   * ser um keyset coerente.
   */
  private <T> List<T> buscar(
      JpaSpecificationExecutor<T> repositorio, Specification<T> spec, Integer page, Integer limit) {
    Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    if (page == null || limit == null || page <= 0 || limit <= 0) {
      return repositorio.findAll(spec, sort);
    }
    return repositorio.findAll(spec, PageRequest.of(page - 1, limit, sort)).getContent();
  }

  /**
   * Porte de {@code aplicarCursor}: os dois campos sao exigidos juntos — informar so um equivale a
   * nao informar nenhum. Valor mal formado e 400, nunca cursor silenciosamente ignorado.
   */
  private record Cursor(Instant createdAt, UUID id) {

    static Cursor parse(String cursorCreatedAt, String cursorId) {
      if (cursorCreatedAt == null
          || cursorCreatedAt.isBlank()
          || cursorId == null
          || cursorId.isBlank()) {
        return new Cursor(null, null);
      }
      try {
        return new Cursor(Instant.parse(cursorCreatedAt), UUID.fromString(cursorId));
      } catch (RuntimeException exception) {
        throw new ApiClientErrorException(
            "Cursor invalido. Use cursorCreatedAt (ISO-8601) e cursorId (UUID).",
            HttpStatus.BAD_REQUEST.value());
      }
    }
  }

  private ConfiguracaoEstoqueResponse toConfiguracaoResponse(EstoqueConfiguracao entity) {
    ConfiguracaoEstoqueResponse response = new ConfiguracaoEstoqueResponse();
    response.alertaEstoqueMinimoAtivo = entity.getAlertaEstoqueMinimoAtivo();
    response.bloquearSaidaSemSaldo = entity.getBloquearSaidaSemSaldo();
    response.permitirAjusteNegativoComPermissao = entity.getPermitirAjusteNegativoComPermissao();
    response.diasCoberturaMeta = entity.getDiasCoberturaMeta();
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  private static InventarioEstoqueResponse toInventarioResponse(EstoqueInventario entity) {
    InventarioEstoqueResponse response = new InventarioEstoqueResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.nome = entity.getNome();
    response.status = entity.getStatus() != null ? entity.getStatus().name() : null;
    response.observacao = entity.getObservacao();
    response.dataAbertura =
        entity.getDataAbertura() != null ? entity.getDataAbertura().toString() : null;
    response.dataFechamento =
        entity.getDataFechamento() != null ? entity.getDataFechamento().toString() : null;
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  /**
   * {@code diferenca} = contada − esperada, e so e preenchida quando <b>as duas</b> quantidades
   * existem (senao fica nula, como no original). O item vem por parametro.
   */
  private static InventarioContagemResponse toContagemResponse(
      EstoqueInventarioContagem entity, ItemEstoque item) {
    InventarioContagemResponse response = new InventarioContagemResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.inventarioId =
        entity.getInventarioId() != null ? entity.getInventarioId().toString() : null;
    response.itemEstoqueId =
        entity.getItemEstoqueId() != null ? entity.getItemEstoqueId().toString() : null;
    if (item != null) {
      response.itemNome = item.getNome();
      response.itemUnidadeMedida = item.getUnidadeMedida();
    }
    response.quantidadeEsperada = entity.getQuantidadeEsperada();
    response.quantidadeContada = entity.getQuantidadeContada();
    if (entity.getQuantidadeEsperada() != null && entity.getQuantidadeContada() != null) {
      response.diferenca = entity.getQuantidadeContada().subtract(entity.getQuantidadeEsperada());
    }
    response.observacao = entity.getObservacao();
    response.usuarioId = entity.getUsuarioId() != null ? entity.getUsuarioId().toString() : null;
    response.usuarioAtualizacaoId =
        entity.getUsuarioAtualizacaoId() != null
            ? entity.getUsuarioAtualizacaoId().toString()
            : null;
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  private static FornecedorEstoqueResponse toFornecedorResponse(EstoqueFornecedor entity) {
    FornecedorEstoqueResponse response = new FornecedorEstoqueResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.nome = entity.getNome();
    response.documento = entity.getDocumento();
    response.email = entity.getEmail();
    response.telefone = entity.getTelefone();
    response.contato = entity.getContato();
    response.ativo = entity.getAtivo();
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  /** O fornecedor vem por parametro: a entidade migrada nao tem a {@code @ManyToOne} do original. */
  private static PedidoCompraEstoqueResponse toPedidoCompraResponse(
      EstoquePedidoCompra entity, EstoqueFornecedor fornecedor) {
    PedidoCompraEstoqueResponse response = new PedidoCompraEstoqueResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.fornecedorId =
        entity.getFornecedorId() != null ? entity.getFornecedorId().toString() : null;
    response.fornecedorNome = fornecedor != null ? fornecedor.getNome() : null;
    response.status = entity.getStatus() != null ? entity.getStatus().name() : null;
    response.valorTotal = entity.getValorTotal();
    response.quantidadeItens = entity.getQuantidadeItens();
    response.quantidadePendente = entity.getQuantidadePendente();
    response.observacao = entity.getObservacao();
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  /** O item vem por parametro: a entidade migrada nao tem a {@code @ManyToOne} do original. */
  private static TransferenciaEstoqueResponse toTransferenciaResponse(
      EstoqueTransferencia entity, ItemEstoque item) {
    TransferenciaEstoqueResponse response = new TransferenciaEstoqueResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.origem = entity.getOrigem();
    response.destino = entity.getDestino();
    response.status = entity.getStatus() != null ? entity.getStatus().name() : null;
    response.itemEstoqueId =
        entity.getItemEstoqueId() != null ? entity.getItemEstoqueId().toString() : null;
    response.itemNome = item != null ? item.getNome() : null;
    response.quantidade = entity.getQuantidade();
    response.observacao = entity.getObservacao();
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  /** O item vem por parametro: a entidade migrada nao tem a {@code @ManyToOne} do original. */
  private ServicoInsumoResponse toServicoInsumoResponse(ServicoInsumo entity, ItemEstoque item) {
    ServicoInsumoResponse response = new ServicoInsumoResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.serviceId = entity.getServiceId() != null ? entity.getServiceId().toString() : null;
    response.itemEstoqueId =
        entity.getItemEstoqueId() != null ? entity.getItemEstoqueId().toString() : null;
    if (item != null) {
      response.itemNome = item.getNome();
      response.itemUnidadeMedida = item.getUnidadeMedida();
      response.saldoAtualItem = item.getSaldoAtual();
    }
    response.quantidadeConsumo = entity.getQuantidadeConsumo();
    response.percentualPerda = entity.getPercentualPerda();
    response.ativo = entity.getAtivo();
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  private void auditarEstoque(
      UUID tenantId,
      String action,
      String entityType,
      UUID entityId,
      Object before,
      Object after,
      Object metadata) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = authenticatedUser.idOuNulo();
      command.actorRole = authenticatedUser.roleOuNulo();
      command.module = AuditConstants.Module.INVENTORY;
      command.action = action;
      command.entityType = entityType;
      command.entityId = entityId != null ? entityId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      command.metadata = metadata;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve bloquear fluxo principal.
    }
  }
}
