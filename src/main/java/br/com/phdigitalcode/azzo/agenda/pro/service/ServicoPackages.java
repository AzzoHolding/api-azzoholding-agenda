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
    ServicePackage pacote = new ServicePackage();
    pacote.setTenantId(tenantId);
    aplicar(request, pacote);
    servicePackageRepository.save(pacote);
    aplicarItens(tenantId, pacote.getId(), request.itens);
    return toResponse(pacote);
  }

  @Transactional
  public PackageDtos.PackageResponse atualizar(UUID id, PackageDtos.PackageRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ServicePackage pacote = buscarOuFalhar(id, tenantId);
    aplicar(request, pacote);
    // A composicao do pacote e regravada do zero, igual ao original
    // (`servicePackageItemRepository.delete("packageId", pacote.id)`).
    servicePackageItemRepository.deleteByPackageId(pacote.getId());
    servicePackageItemRepository.flush();
    aplicarItens(tenantId, pacote.getId(), request.itens);
    return toResponse(pacote);
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

  private void aplicar(PackageDtos.PackageRequest request, ServicePackage pacote) {
    pacote.setNome(request.nome);
    pacote.setDescricao(request.descricao);
    pacote.setPreco(request.preco);
    pacote.setAtivo(request.ativo);
  }

  private void aplicarItens(UUID tenantId, UUID packageId, List<PackageDtos.ItemRequest> itens) {
    for (PackageDtos.ItemRequest itemRequest : itens) {
      UUID serviceId = UUID.fromString(itemRequest.serviceId);
      servicoRepository
          .findByIdAndTenantId(serviceId, tenantId)
          .orElseThrow(
              () -> new ApiClientErrorException("Servico nao encontrado: " + itemRequest.serviceId, 404));

      ServicePackageItem item = new ServicePackageItem();
      item.setTenantId(tenantId);
      item.setPackageId(packageId);
      item.setServiceId(serviceId);
      item.setSessoes(itemRequest.sessoes);
      servicePackageItemRepository.save(item);
    }
  }

  ServicePackage buscarOuFalhar(UUID id, UUID tenantId) {
    return servicePackageRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Pacote nao encontrado.", 404));
  }

  private PackageDtos.PackageResponse toResponse(ServicePackage pacote) {
    PackageDtos.PackageResponse r = new PackageDtos.PackageResponse();
    r.id = pacote.getId().toString();
    r.nome = pacote.getNome();
    r.descricao = pacote.getDescricao();
    r.preco = pacote.getPreco();
    r.ativo = pacote.isAtivo();
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

  private PackageDtos.ClientPackagePurchaseResponse toPurchaseResponse(ClientPackagePurchase compra) {
    PackageDtos.ClientPackagePurchaseResponse r = new PackageDtos.ClientPackagePurchaseResponse();
    r.id = compra.getId().toString();
    r.packageId = compra.getPackageId() != null ? compra.getPackageId().toString() : null;
    r.packageNome = compra.getPackageNome();
    r.precoPago = compra.getPrecoPago();
    r.createdAt = compra.getCreatedAt() != null ? compra.getCreatedAt().toString() : null;
    r.saldos =
        clientPackageBalanceRepository.findByPurchaseId(compra.getId()).stream()
            .map(
                saldo -> {
                  PackageDtos.ClientPackageBalanceResponse saldoResponse =
                      new PackageDtos.ClientPackageBalanceResponse();
                  saldoResponse.serviceId = saldo.getServiceId().toString();
                  saldoResponse.serviceNome = saldo.getServiceNome();
                  saldoResponse.sessoesTotais = saldo.getSessoesTotais();
                  saldoResponse.sessoesUsadas = saldo.getSessoesUsadas();
                  saldoResponse.sessoesDisponiveis =
                      saldo.getSessoesTotais() - saldo.getSessoesUsadas();
                  return saldoResponse;
                })
            .toList();
    return r;
  }
}
