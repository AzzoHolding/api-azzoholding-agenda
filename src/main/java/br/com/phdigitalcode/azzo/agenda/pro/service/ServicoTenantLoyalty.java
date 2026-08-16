package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantLoyaltyDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantLoyaltySettings;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantLoyaltySettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Espelha {@code modules/settings/application/ServicoTenantLoyalty.java} — configuracao do programa
 * de fidelidade do tenant (F08).
 *
 * <p>{@code obterSaldoCliente} <b>nao e {@code @Transactional}</b> no original, ao contrario dos
 * outros dois metodos; preservado. O {@code NotFoundException} do JAX-RS vira
 * {@link ApiClientErrorException} com 404, que e como o {@code GlobalExceptionHandler} reproduz o
 * contrato de erro do {@code ApiExceptionMapper}.
 */
@Service
public class ServicoTenantLoyalty {

  private final ContextoTenant contextoTenant;
  private final TenantLoyaltySettingsRepository tenantLoyaltySettingsRepository;
  private final ClienteRepository clienteRepository;

  public ServicoTenantLoyalty(
      ContextoTenant contextoTenant,
      TenantLoyaltySettingsRepository tenantLoyaltySettingsRepository,
      ClienteRepository clienteRepository) {
    this.contextoTenant = contextoTenant;
    this.tenantLoyaltySettingsRepository = tenantLoyaltySettingsRepository;
    this.clienteRepository = clienteRepository;
  }

  @Transactional
  public TenantLoyaltyDtos.ConfigResponse obterConfiguracaoAtual() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return toResponse(findByTenantIdOrCreate(tenantId));
  }

  @Transactional
  public TenantLoyaltyDtos.ConfigResponse atualizar(TenantLoyaltyDtos.UpdateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantLoyaltySettings config = findByTenantIdOrCreate(tenantId);
    config.setAtivo(request.ativo);
    config.setPontosPorReal(request.pontosPorReal);
    config.setProdutosContam(request.produtosContam);
    config.setValidadeDias(request.validadeDias);
    config.setPontosPorResgateReal(request.pontosPorResgateReal);
    return toResponse(config);
  }

  public TenantLoyaltyDtos.ClientLoyaltyResponse obterSaldoCliente(UUID clientId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente cliente =
        clienteRepository
            .findByIdAndTenantId(clientId, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Cliente nao encontrado.", 404));
    TenantLoyaltyDtos.ClientLoyaltyResponse response =
        new TenantLoyaltyDtos.ClientLoyaltyResponse();
    response.saldo = cliente.getLoyaltyPoints();
    return response;
  }

  /**
   * Equivalente ao {@code findByTenantIdOrCreate} do repositorio Panache. {@code saveAndFlush}
   * porque o {@code persist()} do Panache emitia o INSERT na hora (armadilha 2).
   */
  private TenantLoyaltySettings findByTenantIdOrCreate(UUID tenantId) {
    return tenantLoyaltySettingsRepository
        .findByTenantId(tenantId)
        .orElseGet(
            () -> {
              TenantLoyaltySettings created = new TenantLoyaltySettings();
              created.setTenantId(tenantId);
              return tenantLoyaltySettingsRepository.saveAndFlush(created);
            });
  }

  private TenantLoyaltyDtos.ConfigResponse toResponse(TenantLoyaltySettings config) {
    TenantLoyaltyDtos.ConfigResponse response = new TenantLoyaltyDtos.ConfigResponse();
    response.ativo = config.isAtivo();
    response.pontosPorReal = config.getPontosPorReal();
    response.produtosContam = config.isProdutosContam();
    response.validadeDias = config.getValidadeDias();
    response.pontosPorResgateReal = config.getPontosPorResgateReal();
    return response;
  }
}
