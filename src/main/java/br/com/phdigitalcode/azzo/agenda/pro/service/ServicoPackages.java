package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PackageDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackagePurchase;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackageItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientPackageBalanceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientPackagePurchaseRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicePackageItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicePackageRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Espelha {@code modules/packages/application/ServicoPackages.java}.
 *
 * <p>{@code jakarta.ws.rs.NotFoundException} do original vira
 * {@link ApiClientErrorException} com status 404 — o {@code GlobalExceptionHandler} preserva o
 * status, entao o contrato HTTP e identico.
 */
@Service
public class ServicoPackages {

  private final ContextoTenant contextoTenant;
  private final ServicePackageRepository servicePackageRepository;
  private final ServicePackageItemRepository servicePackageItemRepository;
  private final ClientPackagePurchaseRepository clientPackagePurchaseRepository;
  private final ClientPackageBalanceRepository clientPackageBalanceRepository;
  private final ServicoRepository servicoRepository;

  public ServicoPackages(
      ContextoTenant contextoTenant,
      ServicePackageRepository servicePackageRepository,
      ServicePackageItemRepository servicePackageItemRepository,
      ClientPackagePurchaseRepository clientPackagePurchaseRepository,
      ClientPackageBalanceRepository clientPackageBalanceRepository,
      ServicoRepository servicoRepository) {
    this.contextoTenant = contextoTenant;
    this.servicePackageRepository = servicePackageRepository;
    this.servicePackageItemRepository = servicePackageItemRepository;
    this.clientPackagePurchaseRepository = clientPackagePurchaseRepository;
    this.clientPackageBalanceRepository = clientPackageBalanceRepository;
    this.servicoRepository = servicoRepository;
  }

  @Transactional(readOnly = true)
  public List<PackageDtos.PackageResponse> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return servicePackageRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public PackageDtos.PackageResponse obter(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return toResponse(buscarOuFalhar(id, tenantId));
  }

  @Transactional
  public PackageDtos.PackageResponse criar(PackageDtos.PackageRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    validar(tenantId, null, request);
    ServicePackage pacote = new ServicePackage();
    pacote.setTenantId(tenantId);
    aplicar(request, pacote);
    servicePackageRepository.save(pacote);
    aplicarItens(tenantId, pacote.getId(), request.itens);
    PackageDtos.PackageResponse resposta = toResponse(pacote);
    auditar(tenantId, "PACKAGE_CREATE", pacote.getId(), null, resposta);
    return resposta;
  }

  @Transactional
  public PackageDtos.PackageResponse atualizar(UUID id, PackageDtos.PackageRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ServicePackage pacote = buscarOuFalhar(id, tenantId);
    validar(tenantId, id, request);
    PackageDtos.PackageResponse antes = toResponse(pacote);
    aplicar(request, pacote);
    // A composicao do pacote e regravada do zero, igual ao original
    // (`servicePackageItemRepository.delete("packageId", pacote.id)`).
    servicePackageItemRepository.deleteByPackageId(pacote.getId());
    servicePackageItemRepository.flush();
    aplicarItens(tenantId, pacote.getId(), request.itens);
    PackageDtos.PackageResponse depois = toResponse(pacote);
    auditar(tenantId, "PACKAGE_UPDATE", pacote.getId(), antes, depois);
    return depois;
  }

  /**
   * Exclui so o pacote que nunca foi vendido nem lancado numa comanda (relatorio de 14/11, Baixo 5).
   * O que ja foi vendido se inativa: as compras apontam para ele.
   */
  @Transactional
  public void excluir(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ServicePackage pacote = buscarOuFalhar(id, tenantId);
    boolean emComanda = comandaItemRepository != null
        && comandaItemRepository.existsByTenantIdAndTipoAndReferenciaId(tenantId, "PACOTE", id);
    if (emComanda || clientPackagePurchaseRepository.existsByTenantIdAndPackageId(tenantId, id)) {
      throw new ApiClientErrorException(
          "Este pacote já foi vendido ou está numa comanda: inative-o em vez de excluir.", 409);
    }
    PackageDtos.PackageResponse antes = toResponse(pacote);
    servicePackageItemRepository.deleteByPackageId(id);
    servicePackageRepository.delete(pacote);
    auditar(tenantId, "PACKAGE_DELETE", id, antes, null);
  }

  /**
   * Consumido pelo detalhe do cliente ({@code /clients/{id}/packages}) — o endpoint em si ainda nao
   * foi portado (ver pendencias do modulo {@code customers}), mas o metodo e o mesmo do original.
   */
  @Transactional(readOnly = true)
  public List<PackageDtos.ClientPackagePurchaseResponse> listarDoCliente(UUID clientId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return clientPackagePurchaseRepository
        .findByTenantIdAndClientIdOrderByCreatedAtDesc(tenantId, clientId).stream()
        .map(this::toPurchaseResponse)
        .toList();
  }

  /**
   * Regras que a tela ja aplicava e a API nao (relatorio de 14/11, Medios 1 e 2): sem servico
   * repetido, sem servico inativo, sem nome repetido entre os pacotes ativos.
   */
  private void validar(UUID tenantId, UUID idAtual, PackageDtos.PackageRequest request) {
    java.util.Set<UUID> vistos = new java.util.HashSet<>();
    for (PackageDtos.ItemRequest item : request.itens) {
      UUID serviceId = idDoServico(item.serviceId);
      if (!vistos.add(serviceId)) {
        throw new IllegalArgumentException("O mesmo serviço aparece duas vezes no pacote: junte as sessões numa linha só.");
      }
      Servico servico = servicoRepository.findByIdAndTenantId(serviceId, tenantId)
          .orElseThrow(() -> new ApiClientErrorException("Serviço não encontrado.", 404));
      if (!servico.isActive()) {
        throw new IllegalArgumentException("O serviço " + servico.getName() + " está inativo e não pode entrar no pacote.");
      }
    }
    if (request.ativo && servicePackageRepository.existeAtivoComNome(tenantId, request.nome, idAtual)) {
      throw new IllegalArgumentException("Já existe um pacote ativo com o nome \"" + request.nome.trim() + "\".");
    }
  }

  private static UUID idDoServico(String id) {
    try {
      return UUID.fromString(id == null ? "" : id.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Serviço inválido no pacote.");
    }
  }

  private void aplicar(PackageDtos.PackageRequest request, ServicePackage pacote) {
    pacote.setNome(request.nome.trim());
    pacote.setDescricao(request.descricao);
    pacote.setPreco(request.preco);
    pacote.setAtivo(request.ativo);
    pacote.setValidadeDias(request.validadeDias);
  }

  private void aplicarItens(UUID tenantId, UUID packageId, List<PackageDtos.ItemRequest> itens) {
    for (PackageDtos.ItemRequest itemRequest : itens) {
      UUID serviceId = idDoServico(itemRequest.serviceId);
      servicoRepository
          .findByIdAndTenantId(serviceId, tenantId)
          .orElseThrow(
              () -> new ApiClientErrorException("Serviço não encontrado: " + itemRequest.serviceId, 404));

      ServicePackageItem item = new ServicePackageItem();
      item.setTenantId(tenantId);
      item.setPackageId(packageId);
      item.setServiceId(serviceId);
      item.setSessoes(itemRequest.sessoes);
      servicePackageItemRepository.save(item);
    }
  }

  /** Criar e editar pacote (inclusive o preco) ficam na trilha (relatorio de 14/11, Alto 5). */
  private br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService auditService;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setAuditService(br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService auditService) {
    this.auditService = auditService;
  }

  private void auditar(UUID tenantId, String acao, UUID pacoteId, Object antes, Object depois) {
    if (auditService == null) return;
    var command = new br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand();
    command.tenantId = tenantId;
    command.module = br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants.Module.FINANCE;
    command.action = acao;
    command.entityType = "SERVICE_PACKAGE";
    command.entityId = pacoteId.toString();
    command.sourceChannel = br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants.SourceChannel.API;
    command.before = antes == null ? null : mapa(antes);
    command.after = depois == null ? null : mapa(depois);
    auditService.recordSuccess(command);
  }

  private static java.util.Map<String, Object> mapa(Object resposta) {
    var r = (PackageDtos.PackageResponse) resposta;
    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
    m.put("nome", r.nome);
    m.put("preco", r.preco);
    m.put("ativo", r.ativo);
    m.put("validadeDias", r.validadeDias);
    m.put("itens", r.itens.stream().map(i -> i.serviceNome + " x" + i.sessoes).toList());
    return m;
  }

  /** Setter: os testes que montam o servico a mao seguem sem os usos. */
  private br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaItemRepository comandaItemRepository;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setComandaItemRepository(br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaItemRepository repo) {
    this.comandaItemRepository = repo;
  }

  ServicePackage buscarOuFalhar(UUID id, UUID tenantId) {
    return servicePackageRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Pacote não encontrado.", 404));
  }

  private PackageDtos.PackageResponse toResponse(ServicePackage pacote) {
    PackageDtos.PackageResponse r = new PackageDtos.PackageResponse();
    r.id = pacote.getId().toString();
    r.nome = pacote.getNome();
    r.descricao = pacote.getDescricao();
    r.preco = pacote.getPreco();
    r.ativo = pacote.isAtivo();
    r.validadeDias = pacote.getValidadeDias();
    r.createdAt = pacote.getCreatedAt() != null ? pacote.getCreatedAt().toString() : null;
    r.itens =
        servicePackageItemRepository.findByPackageId(pacote.getId()).stream()
            .map(
                item -> {
                  PackageDtos.ItemResponse itemResponse = new PackageDtos.ItemResponse();
                  itemResponse.serviceId = item.getServiceId().toString();
                  itemResponse.serviceNome =
                      servicoRepository.findById(item.getServiceId()).map(Servico::getName).orElse(null);
                  itemResponse.sessoes = item.getSessoes();
                  return itemResponse;
                })
            .toList();
    return r;
  }

  private java.util.Map<UUID, List<PackageDtos.UsoDoPacote>> usosPorSaldo(
      UUID tenantId, List<br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackageBalance> saldos) {
    java.util.Map<UUID, List<PackageDtos.UsoDoPacote>> usos = new java.util.HashMap<>();
    if (comandaItemRepository == null || saldos.isEmpty() || tenantId == null) return usos;
    var ids = saldos.stream().map(br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackageBalance::getId).toList();
    for (Object[] linha : comandaItemRepository.usosDePacote(tenantId, ids)) {
      var item = (br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaItem) linha[0];
      java.time.Instant fechada = (java.time.Instant) linha[1];
      PackageDtos.UsoDoPacote uso = new PackageDtos.UsoDoPacote();
      uso.data = fechada == null ? null : fechada.toString();
      uso.servico = item.getDescricao();
      uso.sessoes = item.getQuantidade() == null ? 1 : item.getQuantidade().intValue();
      uso.comandaId = item.getComandaId().toString();
      usos.computeIfAbsent(item.getCoberturaSaldoId(), k -> new java.util.ArrayList<>()).add(uso);
    }
    return usos;
  }

  private PackageDtos.ClientPackagePurchaseResponse toPurchaseResponse(ClientPackagePurchase compra) {
    PackageDtos.ClientPackagePurchaseResponse r = new PackageDtos.ClientPackagePurchaseResponse();
    r.id = compra.getId().toString();
    r.packageId = compra.getPackageId() != null ? compra.getPackageId().toString() : null;
    r.packageNome = compra.getPackageNome();
    r.precoPago = compra.getPrecoPago();
    r.createdAt = compra.getCreatedAt() != null ? compra.getCreatedAt().toString() : null;
    var saldosDaCompra = clientPackageBalanceRepository.findByPurchaseId(compra.getId());
    java.util.Map<UUID, List<PackageDtos.UsoDoPacote>> usos = usosPorSaldo(compra.getTenantId(), saldosDaCompra);
    r.saldos =
        saldosDaCompra.stream()
            .map(
                saldo -> {
                  PackageDtos.ClientPackageBalanceResponse saldoResponse =
                      new PackageDtos.ClientPackageBalanceResponse();
                  saldoResponse.id = saldo.getId() == null ? null : saldo.getId().toString();
                  saldoResponse.usos = usos.getOrDefault(saldo.getId(), List.of());
                  saldoResponse.serviceId = saldo.getServiceId().toString();
                  saldoResponse.serviceNome = saldo.getServiceNome();
                  saldoResponse.sessoesTotais = saldo.getSessoesTotais();
                  saldoResponse.sessoesUsadas = saldo.getSessoesUsadas();
                  saldoResponse.sessoesDisponiveis =
                      saldo.getSessoesTotais() - saldo.getSessoesUsadas();
                  saldoResponse.expiraEm = saldo.getExpiraEm() == null ? null : saldo.getExpiraEm().toString();
                  saldoResponse.vencido = saldo.getExpiraEm() != null && saldo.getExpiraEm().isBefore(java.time.Instant.now());
                  return saldoResponse;
                })
            .toList();
    return r;
  }
}
