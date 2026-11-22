package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditChainVerification;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditRetentionEvent;
import br.com.phdigitalcode.azzo.agenda.pro.integration.HashesDaAuditoria;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditChainVerificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionEventRepository;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Confere a trilha de auditoria de um salao, evento por evento, e guarda o resultado em
 * {@code audit_chain_verifications}.
 *
 * <p><b>Cadeia selada (v2):</b> as posicoes seguem sem buraco a partir de 1 (ou da ancora do ultimo
 * expurgo); cada {@code prev_event_hash} e o {@code chain_hash} do anterior; o {@code chain_hash} e
 * o {@code event_hash} sao recalculados; sem redacao, os JSON batem com os digests guardados.
 * Qualquer diferenca: VIOLADA.
 *
 * <p><b>Legado (v1, ate a V164):</b> o hash usava o instante em nanossegundos, e o Postgres guarda
 * microssegundos (arredondando). O hash e recalculado com cada um dos 1000 valores possiveis dos
 * nanossegundos perdidos — os 2030 eventos de producao bateram assim em 07/11/2026. O elo de cada
 * evento tem de apontar para um evento anterior (ou para um evento expurgado). Duas escritas no
 * mesmo anterior (bifurcacao) eram o defeito conhecido da v1: ficam como ressalva, nao violacao.
 *
 * <p>Evento redigido pela LGPD: o JSON mudou de proposito. Na v2 o resto do evento continua
 * conferido pelos digests originais; na v1 so o elo.
 */
@Service
public class ConferenciaDaAuditoria {

  private static final Logger LOG = LoggerFactory.getLogger(ConferenciaDaAuditoria.class);
  private static final int PAGINA = 1000;
  /** Evento sem selo ha mais que isso: o selador parou. */
  static final Duration ATRASO_DO_SELO = Duration.ofMinutes(10);

  public static final String INTEGRA = "INTEGRA";
  public static final String INTEGRA_COM_RESSALVAS = "INTEGRA_COM_RESSALVAS";
  public static final String VIOLADA = "VIOLADA";

  private final AuditEventRepository eventos;
  private final AuditRetentionEventRepository expurgos;
  private final AuditChainVerificationRepository conferencias;
  private final SeladorDaAuditoria selador;
  private final TransactionTemplate leitura;
  private final TransactionTemplate escrita;
  private final MeterRegistry metricas;

  @jakarta.persistence.PersistenceContext
  private jakarta.persistence.EntityManager entityManager;

  public ConferenciaDaAuditoria(
      AuditEventRepository eventos,
      AuditRetentionEventRepository expurgos,
      AuditChainVerificationRepository conferencias,
      SeladorDaAuditoria selador,
      PlatformTransactionManager transactionManager,
      MeterRegistry metricas) {
    this.eventos = eventos;
    this.expurgos = expurgos;
    this.conferencias = conferencias;
    this.selador = selador;
    this.leitura = new TransactionTemplate(transactionManager);
    this.leitura.setReadOnly(true);
    this.leitura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.escrita = new TransactionTemplate(transactionManager);
    this.escrita.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.metricas = metricas;
  }

  /** Todos os saloes com eventos. Para a rotina da noite. */
  public void conferirTodos(String executadoPor) {
    selador.selarPendentes();
    for (UUID tenantId : eventos.tenantsComEventos()) {
      try {
        conferir(tenantId, executadoPor, false);
      } catch (Exception e) {
        LOG.error("audit_chain_verification_failed tenant_id={}", tenantId, e);
      }
    }
  }

  /** Confere um salao e guarda o resultado. {@code selarAntes}: sela a fila antes de conferir. */
  public AuditChainVerification conferir(UUID tenantId, String executadoPor, boolean selarAntes) {
    if (selarAntes) selador.selarPendentes();
    Instant inicio = Instant.now();
    Resultado r = leitura.execute(status -> conferirNaTransacao(tenantId, inicio));
    AuditChainVerification registro = Objects.requireNonNull(r).paraRegistro(tenantId, executadoPor, inicio, Instant.now());
    escrita.executeWithoutResult(status -> conferencias.save(registro));
    if (VIOLADA.equals(registro.getStatus())) {
      LOG.error(
          "audit_chain_violation tenant_id={} failures={} first_event_id={} reason={}",
          tenantId, registro.getFailures(), registro.getFirstFailureEventId(), registro.getFirstFailureReason());
      if (metricas != null) metricas.counter("audit.chain.violations").increment();
    } else {
      LOG.info(
          "audit_chain_verified tenant_id={} status={} checked={} legacy_forks={}",
          tenantId, registro.getStatus(), registro.getCheckedEvents(), registro.getLegacyForks());
    }
    return registro;
  }

  public Optional<AuditChainVerification> ultima(UUID tenantId) {
    return conferencias.ultimaDoSalao(tenantId);
  }

  Resultado conferirNaTransacao(UUID tenantId, Instant agora) {
    Resultado r = new Resultado();
    boolean houveExpurgo = expurgos.houveExpurgo(tenantId);
    Set<String> ancoras = new HashSet<>(expurgos.hashesDeAncora(tenantId));
    Set<String> hashesDoLegado = new HashSet<>();
    List<EloPendente> elosSemDono = new ArrayList<>();
    conferirLegado(tenantId, r, ancoras, hashesDoLegado, elosSemDono);
    Set<String> hashesDaCadeia = new HashSet<>();
    conferirCadeia(tenantId, r, ancoras, hashesDoLegado, hashesDaCadeia);
    // Um evento legado gravado pela versao antiga durante o deploy pode apontar para um evento v2.
    for (EloPendente pendente : elosSemDono) {
      if (!hashesDaCadeia.contains(pendente.anterior()) && !houveExpurgo) {
        r.falha(pendente.evento(), pendente.criadoEm(), "legado: elo para um evento que nao existe");
      }
    }
    r.pendentes = eventos.contarSemSeloAntesDe(tenantId, agora.minus(ATRASO_DO_SELO));
    return r;
  }

  private record EloPendente(UUID evento, Instant criadoEm, String anterior) {}

  private void conferirLegado(
      UUID tenantId, Resultado r, Set<String> ancoras, Set<String> hashes, List<EloPendente> elosSemDono) {
    Set<String> anterioresUsados = new HashSet<>();
    Instant depoisDe = Instant.EPOCH;
    UUID depoisDoId = new UUID(0, 0);
    boolean primeiro = true;
    while (true) {
      List<AuditEvent> pagina = eventos.paginaDoLegado(tenantId, depoisDe, depoisDoId, Limit.of(PAGINA));
      for (AuditEvent e : pagina) {
        r.legado++;
        r.conferidos++;
        if (e.getRedactedAt() != null) {
          r.redigidos++;
        } else if (!confereV1(e)) {
          r.falha(e, "legado: o conteudo nao bate com o hash");
        }
        String anterior = e.getPrevEventHash();
        if (anterior == null) {
          if (!primeiro) r.falha(e, "legado: evento sem elo no meio da cadeia");
        } else if (!hashes.contains(anterior) && !ancoras.contains(anterior)) {
          elosSemDono.add(new EloPendente(e.getId(), e.getCreatedAt(), anterior));
        }
        if (anterior != null && !anterioresUsados.add(anterior)) r.bifurcacoesDoLegado++;
        hashes.add(e.getEventHash());
        primeiro = false;
      }
      if (pagina.size() < PAGINA) return;
      AuditEvent ultimo = pagina.get(pagina.size() - 1);
      depoisDe = ultimo.getCreatedAt();
      depoisDoId = ultimo.getId();
      entityManager.clear();
    }
  }

  private void conferirCadeia(
      UUID tenantId, Resultado r, Set<String> ancoras, Set<String> hashesDoLegado, Set<String> hashesDaCadeia) {
    Optional<AuditRetentionEvent> ancora = expurgos.ultimaAncoraSelada(tenantId);
    long depoisDe = 0;
    Long esperada = null;
    String eloAnterior = null;
    while (true) {
      List<AuditEvent> pagina = eventos.paginaDaCadeia(tenantId, depoisDe, Limit.of(PAGINA));
      for (AuditEvent e : pagina) {
        r.selados++;
        r.conferidos++;
        hashesDaCadeia.add(e.getEventHash());
        if (esperada == null) {
          // O primeiro que ficou: logo depois da ancora do expurgo, ou o 1 ligado ao legado.
          if (ancora.isPresent() && e.getChainSeq() == ancora.get().getChainAnchorSeq() + 1) {
            if (!Objects.equals(e.getPrevEventHash(), ancora.get().getChainAnchorHash())) {
              r.falha(e, "cadeia: o primeiro evento nao continua a ancora do expurgo");
            }
          } else if (e.getChainSeq() == 1) {
            String anterior = e.getPrevEventHash();
            boolean ok = anterior == null
                ? hashesDoLegado.isEmpty() && ancoras.isEmpty()
                : hashesDoLegado.contains(anterior) || ancoras.contains(anterior);
            if (!ok) r.falha(e, "cadeia: o primeiro evento nao continua o legado");
          } else {
            r.falha(e, "cadeia: comeca na posicao " + e.getChainSeq() + " sem ancora de expurgo");
          }
        } else {
          // Long com Long por valor: com != a comparacao era por referencia e falhava a partir de 128.
          if (e.getChainSeq().longValue() != esperada.longValue()) r.falha(e, "cadeia: falta a posicao " + esperada);
          if (!Objects.equals(e.getPrevEventHash(), eloAnterior)) r.falha(e, "cadeia: elo anterior diferente");
        }
        if (!HashesDaAuditoria.hashDoConteudoV2(e).equals(e.getEventHash())) {
          r.falha(e, "cadeia: o conteudo nao bate com o event_hash");
        }
        if (e.getRedactedAt() != null) {
          r.redigidos++;
        } else if (!HashesDaAuditoria.digestsDosJson(e.getBeforeJson(), e.getAfterJson(), e.getMetadataJson())
            .equals(e.getPayloadDigests())) {
          r.falha(e, "cadeia: o JSON nao bate com os digests guardados");
        }
        if (!HashesDaAuditoria.hashDoElo(e).equals(e.getChainHash())) r.falha(e, "cadeia: o elo nao bate");
        esperada = e.getChainSeq() + 1;
        eloAnterior = e.getChainHash();
        r.ultimaPosicao = e.getChainSeq();
        r.ultimoElo = e.getChainHash();
      }
      if (pagina.size() < PAGINA) return;
      depoisDe = pagina.get(pagina.size() - 1).getChainSeq();
      entityManager.clear();
    }
  }

  /**
   * O hash v1 com o instante original. O banco arredonda para o microssegundo mais proximo: o
   * original esta entre {@code t - 500 ns} e {@code t + 499 ns}.
   */
  static boolean confereV1(AuditEvent e) {
    if (e.getCreatedAt() == null || e.getEventHash() == null) return false;
    Instant inicio = e.getCreatedAt().minusNanos(500);
    for (int n = 0; n < 1000; n++) {
      if (HashesDaAuditoria.hashV1(e, inicio.plusNanos(n)).equals(e.getEventHash())) return true;
    }
    return false;
  }

  static final class Resultado {
    long conferidos;
    long selados;
    long legado;
    long bifurcacoesDoLegado;
    long redigidos;
    long pendentes;
    long falhas;
    UUID primeiraFalhaEvento;
    String primeiraFalhaMotivo;
    Long ultimaPosicao;
    String ultimoElo;

    private Instant primeiraFalhaEm;

    void falha(AuditEvent e, String motivo) {
      falha(e.getId(), e.getCreatedAt(), motivo);
    }

    /** Guarda a falha mais ANTIGA: e por ela que se comeca a investigar. */
    void falha(UUID evento, Instant criadoEm, String motivo) {
      falhas++;
      boolean maisAntiga = primeiraFalhaEm != null && criadoEm != null && criadoEm.isBefore(primeiraFalhaEm);
      if (primeiraFalhaEvento == null || maisAntiga) {
        primeiraFalhaEvento = evento;
        primeiraFalhaEm = criadoEm;
        primeiraFalhaMotivo = motivo.length() > 300 ? motivo.substring(0, 300) : motivo;
      }
    }

    String status() {
      if (falhas > 0) return VIOLADA;
      if (bifurcacoesDoLegado > 0 || pendentes > 0) return INTEGRA_COM_RESSALVAS;
      return INTEGRA;
    }

    AuditChainVerification paraRegistro(UUID tenantId, String executadoPor, Instant inicio, Instant fim) {
      AuditChainVerification v = new AuditChainVerification();
      v.setTenantId(tenantId);
      v.setStatus(status());
      v.setCheckedEvents(conferidos);
      v.setSealedEvents(selados);
      v.setLegacyEvents(legado);
      v.setLegacyForks(bifurcacoesDoLegado);
      v.setRedactedEvents(redigidos);
      v.setPendingSeal(pendentes);
      v.setFailures(falhas);
      v.setFirstFailureEventId(primeiraFalhaEvento);
      v.setFirstFailureReason(primeiraFalhaMotivo);
      v.setLastChainSeq(ultimaPosicao);
      v.setLastChainHash(ultimoElo);
      v.setExecutedBy(executadoPor == null ? "SISTEMA" : executadoPor);
      v.setStartedAt(inicio);
      v.setFinishedAt(fim);
      return v;
    }
  }
}
