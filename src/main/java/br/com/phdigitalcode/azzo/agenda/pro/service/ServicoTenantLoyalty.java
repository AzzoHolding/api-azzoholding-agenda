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
    // Devolver mais do que a cliente gasta da prejuizo em toda venda (relatorio de 14/11, Medio 3).
    java.math.BigDecimal devolucao = request.pontosPorReal.divide(request.pontosPorResgateReal, 4, java.math.RoundingMode.HALF_UP);
    if (devolucao.compareTo(java.math.BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("Com esses valores a cliente recebe de volta "
          + devolucao.movePointRight(2).stripTrailingZeros().toPlainString()
          + "% do que gasta. O máximo é 100%: aumente os pontos para R$ 1,00 ou diminua os pontos por real.");
    }
    TenantLoyaltySettings config = findByTenantIdOrCreate(tenantId);
    config.setAtivo(request.ativo);
    config.setPontosPorReal(request.pontosPorReal);
    config.setProdutosContam(request.produtosContam);
    config.setValidadeDias(request.validadeDias);
    config.setPontosPorResgateReal(request.pontosPorResgateReal);
    return toResponse(config);
  }

  /** Setter: os testes que montam o servico a mao seguem sem o historico. */
  private br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentoDePontosRepository movimentos;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setMovimentos(br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentoDePontosRepository movimentos) {
    this.movimentos = movimentos;
  }

  /** Saldo, valor em reais, o que vence e o extrato (relatorio de 14/11, Critico 3). */
  @Transactional(readOnly = true)
  public TenantLoyaltyDtos.ClientLoyaltyResponse obterSaldoCliente(UUID clientId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    clienteRepository
        .findByIdAndTenantId(clientId, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Cliente não encontrado.", 404));
    TenantLoyaltySettings config = tenantLoyaltySettingsRepository.findByTenantId(tenantId).orElse(null);
    TenantLoyaltyDtos.ClientLoyaltyResponse response = new TenantLoyaltyDtos.ClientLoyaltyResponse();
    Integer saldo = clienteRepository.saldoDePontos(clientId, tenantId);
    response.saldo = saldo == null ? 0 : saldo;
    response.programaAtivo = config != null && config.isAtivo();
    response.validadeDias = config == null ? null : config.getValidadeDias();
    response.valorEmReais = ServicoDePontos.valorEmReais(response.saldo,
        config == null ? null : config.getPontosPorResgateReal());
    if (movimentos == null) return response;
    for (var credito : movimentos.creditosComSaldo(tenantId, clientId)) {
      if (credito.getExpiraEm() == null) continue;
      TenantLoyaltyDtos.Vencimento v = new TenantLoyaltyDtos.Vencimento();
      v.pontos = credito.getPontosRestantes();
      v.expiraEm = credito.getExpiraEm().toString();
      response.aVencer.add(v);
    }
    for (var m : movimentos.findTop50ByTenantIdAndClientIdOrderByCreatedAtDesc(tenantId, clientId)) {
      TenantLoyaltyDtos.Movimento mov = new TenantLoyaltyDtos.Movimento();
      mov.tipo = m.getTipo();
      mov.pontos = m.getPontos();
      mov.saldoApos = m.getSaldoApos();
      mov.comandaId = m.getComandaId() == null ? null : m.getComandaId().toString();
      mov.motivo = m.getMotivo();
      mov.expiraEm = m.getExpiraEm() == null ? null : m.getExpiraEm().toString();
      mov.createdAt = m.getCreatedAt().toString();
      response.extrato.add(mov);
    }
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
