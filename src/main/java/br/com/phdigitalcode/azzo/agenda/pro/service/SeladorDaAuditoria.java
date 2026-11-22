package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditRetentionEvent;
import br.com.phdigitalcode.azzo.agenda.pro.integration.HashesDaAuditoria;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionEventRepository;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Liga os eventos de auditoria na cadeia do salao, um selador por vez.
 *
 * <p>Ate a V164 o elo era calculado por quem gravava, lendo "o ultimo evento" sem trava: duas
 * transacoes ao mesmo tempo encadeavam no mesmo anterior (52 bifurcacoes em producao). Agora quem
 * grava so calcula o hash do conteudo; o selador pega os eventos JA CONFIRMADOS, na ordem, e da a
 * cada um a posicao ({@code chain_seq}), o elo anterior e o proprio elo ({@code chain_hash}).
 *
 * <p>Garantias: a trava {@code pg_try_advisory_xact_lock} deixa um selador por vez (mesmo com varias
 * instancias); o indice unico {@code (tenant_id, chain_seq)} recusa posicao repetida; o gatilho so
 * aceita o UPDATE de selo (de nulo para valor, sem tocar em mais nada). Evento de transacao que
 * desfez nunca chega aqui — ele nao existe.
 */
@Service
public class SeladorDaAuditoria {

  private static final Logger LOG = LoggerFactory.getLogger(SeladorDaAuditoria.class);
  static final int LOTE = 500;

  private final AuditEventRepository eventos;
  private final AuditRetentionEventRepository expurgos;
  private final TransactionTemplate transacao;
  private final MeterRegistry metricas;

  public SeladorDaAuditoria(
      AuditEventRepository eventos,
      AuditRetentionEventRepository expurgos,
      PlatformTransactionManager transactionManager,
      MeterRegistry metricas) {
    this.eventos = eventos;
    this.expurgos = expurgos;
    this.transacao = new TransactionTemplate(transactionManager);
    this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.metricas = metricas;
  }

  /** Sela ate esvaziar a fila (lotes de {@value #LOTE}). Devolve quantos eventos selou. */
  public long selarPendentes() {
    long total = 0;
    while (true) {
      Integer selados = transacao.execute(status -> selarUmLote());
      int n = selados == null ? 0 : selados;
      total += n;
      if (n < LOTE) break;
    }
    if (total > 0) {
      LOG.info("audit_seal sealed={}", total);
      if (metricas != null) metricas.counter("audit.seal.sealed").increment(total);
    }
    return total;
  }

  int selarUmLote() {
    if (!eventos.travaDoSelador()) return 0;
    List<AuditEvent> pendentes = eventos.findNaoSelados(Limit.of(LOTE));
    if (pendentes.isEmpty()) return 0;
    eventos.habilitarSeloNestaTransacao();

    Map<UUID, Elo> cabecas = new HashMap<>();
    Instant agora = HashesDaAuditoria.instanteDoBanco(Instant.now());
    for (AuditEvent evento : pendentes) {
      Elo anterior = cabecas.computeIfAbsent(evento.getTenantId(), this::cabecaDaCadeia);
      evento.setChainSeq(anterior.posicao() + 1);
      evento.setPrevEventHash(anterior.hash());
      evento.setSealedAt(agora);
      evento.setChainHash(HashesDaAuditoria.hashDoElo(evento));
      cabecas.put(evento.getTenantId(), new Elo(evento.getChainSeq(), evento.getChainHash()));
    }
    eventos.saveAll(pendentes);
    eventos.flush();
    return pendentes.size();
  }

  /**
   * Onde a cadeia do salao termina agora: o ultimo selado; sem selados, a ancora do ultimo expurgo;
   * sem ancora, o ultimo evento legado (v1), posicao 0 — o primeiro v2 aponta para ele.
   */
  Elo cabecaDaCadeia(UUID tenantId) {
    Optional<AuditEvent> selado = eventos.ultimoSelado(tenantId);
    if (selado.isPresent()) return new Elo(selado.get().getChainSeq(), selado.get().getChainHash());
    Optional<AuditRetentionEvent> ancora = expurgos.ultimaAncoraSelada(tenantId);
    if (ancora.isPresent()) return new Elo(ancora.get().getChainAnchorSeq(), ancora.get().getChainAnchorHash());
    return eventos.ultimoLegado(tenantId)
        .map(legado -> new Elo(0L, legado.getEventHash()))
        .orElseGet(() -> {
          List<String> ancorasDoLegado = expurgos.hashesDeAncora(tenantId);
          return new Elo(0L, ancorasDoLegado.isEmpty() ? null : ancorasDoLegado.get(ancorasDoLegado.size() - 1));
        });
  }

  record Elo(long posicao, String hash) {}
}
