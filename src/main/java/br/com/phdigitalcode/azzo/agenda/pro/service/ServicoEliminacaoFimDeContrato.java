package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SubscriptionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;

/**
 * Eliminacao ao fim do contrato do salao (LGPD; politica 2026.10, secao 6: "encerrado o contrato do
 * salao, os dados dos clientes dele sao mantidos apenas pelo prazo legal e entao eliminados ou
 * anonimizados").
 *
 * <p>Decisao do dono (19/10/2026): <b>5 anos depois do fim do ultimo plano confirmado, anonimizar</b>.
 * Anonimizar, e nao apagar, mantem os numeros agregados (agendamentos, faturamento) e a integridade
 * referencial; a nota fiscal emitida fica pelo prazo legal, como na anonimizacao do operador.
 *
 * <p>Contrato encerrado = nenhum plano vigente, nenhuma assinatura renovando e um ultimo plano
 * confirmado que terminou ha mais do prazo. Salao que nunca teve plano confirmado fica de fora: sem
 * data de fim nao ha como contar o prazo. Cada cliente e anonimizado na propria transacao (uma falha
 * nao desfaz os outros), com limite por rodada para a rotina diaria nao virar uma noite inteira.
 */
@Service
public class ServicoEliminacaoFimDeContrato {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoEliminacaoFimDeContrato.class);

  private final TenantRepository tenantRepository;
  private final CheckoutOrderRepository checkoutOrderRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final ClienteRepository clienteRepository;
  private final ServicoAnonimizacaoTitular anonimizacao;
  private final int anosAposOFim;
  private final int limitePorRodada;

  public ServicoEliminacaoFimDeContrato(
      TenantRepository tenantRepository,
      CheckoutOrderRepository checkoutOrderRepository,
      SubscriptionRepository subscriptionRepository,
      ClienteRepository clienteRepository,
      ServicoAnonimizacaoTitular anonimizacao,
      @Value("${app.lgpd.fim-de-contrato.anos:5}") int anosAposOFim,
      @Value("${app.lgpd.fim-de-contrato.limite-por-rodada:500}") int limitePorRodada) {
    this.tenantRepository = tenantRepository;
    this.checkoutOrderRepository = checkoutOrderRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.clienteRepository = clienteRepository;
    this.anonimizacao = anonimizacao;
    this.anosAposOFim = anosAposOFim;
    this.limitePorRodada = limitePorRodada;
  }

  /** @return quantos clientes foram anonimizados nesta rodada */
  public int executar() {
    Instant agora = Instant.now();
    Instant limite = agora.atZone(ZoneOffset.UTC).minusYears(anosAposOFim).toInstant();
    int anonimizados = 0;
    for (Tenant tenant : tenantRepository.findAll()) {
      if (anonimizados >= limitePorRodada) break;
      if (!contratoEncerradoAntesDe(tenant.getId(), agora, limite)) continue;
      anonimizados += anonimizarClientes(tenant.getId(), limitePorRodada - anonimizados);
    }
    LOG.info("lgpd_fim_de_contrato concluido: {} cliente(s) anonimizado(s), prazo de {} anos", anonimizados, anosAposOFim);
    return anonimizados;
  }

  boolean contratoEncerradoAntesDe(UUID tenantId, Instant agora, Instant limite) {
    if (tenantId == null) return false;
    if (checkoutOrderRepository.possuiPlanoVigente(tenantId, agora)) return false;
    if (subscriptionRepository.possuiRenovacaoAtiva(tenantId)) return false;
    Instant fim = checkoutOrderRepository.fimDoUltimoPlano(tenantId, StatusCheckout.CONFIRMED);
    return fim != null && fim.isBefore(limite);
  }

  private int anonimizarClientes(UUID tenantId, int maximo) {
    List<UUID> ids = clienteRepository.idsNaoAnonimizados(tenantId, PageRequest.of(0, maximo));
    int feitos = 0;
    for (UUID clientId : ids) {
      try {
        anonimizacao.anonimizarPorFimDeContrato(tenantId, clientId);
        feitos++;
      } catch (RuntimeException e) {
        LOG.warn("lgpd_fim_de_contrato falhou clientId={} tenantId={}: {}", clientId, tenantId, e.getMessage());
      }
    }
    if (feitos > 0) {
      LOG.info("lgpd_fim_de_contrato tenantId={} clientesAnonimizados={}", tenantId, feitos);
    }
    return feitos;
  }
}
