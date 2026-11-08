package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditRetentionEvent;
import br.com.phdigitalcode.azzo.agenda.pro.integration.HashesDaAuditoria;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditChainVerificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionEventRepository;

/** Selador e conferencia da trilha de auditoria (V164), sem banco. */
class CadeiaDaAuditoriaTest {

  private final UUID tenant = UUID.randomUUID();
  private AuditEventRepository eventos;
  private AuditRetentionEventRepository expurgos;
  private SeladorDaAuditoria selador;
  private ConferenciaDaAuditoria conferencia;

  @BeforeEach
  void setUp() throws Exception {
    eventos = mock(AuditEventRepository.class);
    expurgos = mock(AuditRetentionEventRepository.class);
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    selador = new SeladorDaAuditoria(eventos, expurgos, tx, null);
    conferencia = new ConferenciaDaAuditoria(
        eventos, expurgos, mock(AuditChainVerificationRepository.class), selador, tx, null);
    var campo = ConferenciaDaAuditoria.class.getDeclaredField("entityManager");
    campo.setAccessible(true);
    campo.set(conferencia, mock(jakarta.persistence.EntityManager.class));
    when(expurgos.ultimaAncoraSelada(any())).thenReturn(Optional.empty());
    when(expurgos.hashesDeAncora(any())).thenReturn(List.of());
    when(eventos.ultimoSelado(any())).thenReturn(Optional.empty());
  }

  private AuditEvent v2(int i) {
    AuditEvent e = new AuditEvent();
    e.setId(UUID.randomUUID());
    e.setTenantId(tenant);
    e.setHashVersion((short) 2);
    e.setModule("FINANCE");
    e.setAction("PAGAMENTO_" + i);
    e.setStatus("SUCCESS");
    e.setRequestId("req-" + i);
    e.setSourceChannel("API");
    e.setActorUserId(UUID.randomUUID());
    e.setIpAddress("10.0.0." + i);
    e.setAfterJson("{\"valor\":" + i + "}");
    e.setPayloadDigests(HashesDaAuditoria.digestsDosJson(null, e.getAfterJson(), null));
    e.setCreatedAt(HashesDaAuditoria.instanteDoBanco(Instant.parse("2026-11-08T10:00:00Z").plusSeconds(i)));
    e.setEventHash(HashesDaAuditoria.hashDoConteudoV2(e));
    return e;
  }

  private AuditEvent v1(String prev, Instant comNanos) {
    AuditEvent e = new AuditEvent();
    e.setId(UUID.randomUUID());
    e.setTenantId(tenant);
    e.setModule("AUTH");
    e.setAction("LOGIN");
    e.setStatus("SUCCESS");
    e.setRequestId("r");
    e.setSourceChannel("API");
    e.setPrevEventHash(prev);
    e.setCreatedAt(comNanos);
    e.setEventHash(HashesDaAuditoria.hashV1(e));
    // O banco arredonda para microssegundos.
    long nanos = comNanos.getNano();
    long arredondado = Math.round(nanos / 1000.0) * 1000;
    e.setCreatedAt(comNanos.minusNanos(nanos).plusNanos(arredondado));
    return e;
  }

  /** Sela uma lista como o selador faria, devolvendo-a ligada. */
  private List<AuditEvent> selar(List<AuditEvent> pendentes, String legado) {
    when(eventos.travaDoSelador()).thenReturn(true);
    when(eventos.findNaoSelados(any(Limit.class))).thenReturn(pendentes);
    when(eventos.ultimoLegado(tenant)).thenReturn(Optional.ofNullable(legado).map(h -> {
      AuditEvent l = new AuditEvent();
      l.setEventHash(h);
      return l;
    }));
    assertThat(selador.selarUmLote()).isEqualTo(pendentes.size());
    return pendentes;
  }

  private void cadeiaNoBanco(List<AuditEvent> selados, List<AuditEvent> legado) {
    when(eventos.paginaDaCadeia(eq(tenant), anyLong(), any(Limit.class))).thenReturn(selados);
    when(eventos.paginaDoLegado(eq(tenant), any(), any(), any(Limit.class))).thenReturn(legado);
  }

  @Test
  void seladorLigaEmOrdemSemBuracoEContinuaOLegado() {
    List<AuditEvent> lote = selar(new ArrayList<>(List.of(v2(1), v2(2), v2(3))), "hash-legado");

    assertThat(lote).extracting(AuditEvent::getChainSeq).containsExactly(1L, 2L, 3L);
    assertThat(lote.get(0).getPrevEventHash()).isEqualTo("hash-legado");
    assertThat(lote.get(1).getPrevEventHash()).isEqualTo(lote.get(0).getChainHash());
    assertThat(lote.get(2).getPrevEventHash()).isEqualTo(lote.get(1).getChainHash());
    verify(eventos).habilitarSeloNestaTransacao();
  }

  @Test
  void seladorSemATravaNaoTocaEmNada() {
    when(eventos.travaDoSelador()).thenReturn(false);
    assertThat(selador.selarUmLote()).isZero();
    verify(eventos, never()).findNaoSelados(any());
  }

  @Test
  void seladorContinuaDoUltimoSelado() {
    AuditEvent ultimo = v2(9);
    ultimo.setChainSeq(41L);
    ultimo.setChainHash("elo-41");
    when(eventos.ultimoSelado(tenant)).thenReturn(Optional.of(ultimo));
    List<AuditEvent> lote = selar(new ArrayList<>(List.of(v2(1))), null);
    assertThat(lote.get(0).getChainSeq()).isEqualTo(42L);
    assertThat(lote.get(0).getPrevEventHash()).isEqualTo("elo-41");
  }

  @Test
  void conferenciaDeCadeiaIntegraComLegadoEmNanossegundos() {
    AuditEvent l1 = v1(null, Instant.parse("2026-09-12T04:49:57.387274178Z"));
    AuditEvent l2 = v1(l1.getEventHash(), Instant.parse("2026-09-12T04:51:40.179517600Z"));
    List<AuditEvent> selados = selar(new ArrayList<>(List.of(v2(1), v2(2))), l2.getEventHash());
    cadeiaNoBanco(selados, List.of(l1, l2));

    var r = conferencia.conferirNaTransacao(tenant, Instant.now());

    assertThat(r.falhas).isZero();
    assertThat(r.status()).isEqualTo(ConferenciaDaAuditoria.INTEGRA);
    assertThat(r.legado).isEqualTo(2);
    assertThat(r.selados).isEqualTo(2);
  }

  @Test
  void trocarOIpDeUmEventoSeladoViolaACadeia() {
    List<AuditEvent> selados = selar(new ArrayList<>(List.of(v2(1), v2(2), v2(3))), null);
    selados.get(1).setIpAddress("192.168.0.99");
    cadeiaNoBanco(selados, List.of());

    var r = conferencia.conferirNaTransacao(tenant, Instant.now());

    assertThat(r.status()).isEqualTo(ConferenciaDaAuditoria.VIOLADA);
    assertThat(r.primeiraFalhaEvento).isEqualTo(selados.get(1).getId());
  }

  @Test
  void apagarUmEventoDoMeioViolaACadeia() {
    List<AuditEvent> selados = selar(new ArrayList<>(List.of(v2(1), v2(2), v2(3))), null);
    cadeiaNoBanco(List.of(selados.get(0), selados.get(2)), List.of());

    var r = conferencia.conferirNaTransacao(tenant, Instant.now());

    assertThat(r.status()).isEqualTo(ConferenciaDaAuditoria.VIOLADA);
    assertThat(r.primeiraFalhaMotivo).contains("falta a posicao 2");
  }

  @Test
  void trocarOJsonSemRedacaoViolaComRedacaoNao() {
    List<AuditEvent> selados = selar(new ArrayList<>(List.of(v2(1), v2(2))), null);
    selados.get(0).setAfterJson("{\"valor\":999}");
    cadeiaNoBanco(selados, List.of());
    assertThat(conferencia.conferirNaTransacao(tenant, Instant.now()).status())
        .isEqualTo(ConferenciaDaAuditoria.VIOLADA);

    selados.get(0).setRedactedAt(Instant.now());
    var r = conferencia.conferirNaTransacao(tenant, Instant.now());
    assertThat(r.status()).isEqualTo(ConferenciaDaAuditoria.INTEGRA);
    assertThat(r.redigidos).isEqualTo(1);
  }

  @Test
  void bifurcacaoDoLegadoEhRessalvaEAdulteracaoDoLegadoEhViolacao() {
    AuditEvent l1 = v1(null, Instant.parse("2026-09-12T04:49:57.387274178Z"));
    AuditEvent l2 = v1(l1.getEventHash(), Instant.parse("2026-09-12T04:50:00.000000400Z"));
    AuditEvent l3 = v1(l1.getEventHash(), Instant.parse("2026-09-12T04:50:00.000000900Z"));
    cadeiaNoBanco(List.of(), List.of(l1, l2, l3));
    var r = conferencia.conferirNaTransacao(tenant, Instant.now());
    assertThat(r.bifurcacoesDoLegado).isEqualTo(1);
    assertThat(r.status()).isEqualTo(ConferenciaDaAuditoria.INTEGRA_COM_RESSALVAS);

    l2.setAction("LOGOUT");
    assertThat(conferencia.conferirNaTransacao(tenant, Instant.now()).status())
        .isEqualTo(ConferenciaDaAuditoria.VIOLADA);
  }

  @Test
  void depoisDoExpurgoACadeiaContinuaDaAncora() {
    List<AuditEvent> selados = selar(new ArrayList<>(List.of(v2(1), v2(2), v2(3))), null);
    AuditRetentionEvent ancora = new AuditRetentionEvent();
    ancora.setChainAnchorSeq(1L);
    ancora.setChainAnchorHash(selados.get(0).getChainHash());
    when(expurgos.ultimaAncoraSelada(tenant)).thenReturn(Optional.of(ancora));
    when(expurgos.houveExpurgo(tenant)).thenReturn(true);
    cadeiaNoBanco(List.of(selados.get(1), selados.get(2)), List.of());

    assertThat(conferencia.conferirNaTransacao(tenant, Instant.now()).status())
        .isEqualTo(ConferenciaDaAuditoria.INTEGRA);

    // Sem a ancora, o mesmo corte e um buraco.
    when(expurgos.ultimaAncoraSelada(tenant)).thenReturn(Optional.empty());
    assertThat(conferencia.conferirNaTransacao(tenant, Instant.now()).status())
        .isEqualTo(ConferenciaDaAuditoria.VIOLADA);
  }

  @Test
  void eventoSemSeloAntigoEhRessalva() {
    cadeiaNoBanco(List.of(), List.of());
    when(eventos.contarSemSeloAntesDe(eq(tenant), any())).thenReturn(3L);
    assertThat(conferencia.conferirNaTransacao(tenant, Instant.now()).status())
        .isEqualTo(ConferenciaDaAuditoria.INTEGRA_COM_RESSALVAS);
  }
}
