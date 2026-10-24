package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.FechamentoCaixaRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Comanda;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FechamentoCaixa;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusFechamentoCaixa;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FechamentoCaixaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Diferenca entre o esperado e o contado EXIGE explicacao para fechar o caixa.
 *
 * <p>No teste de ponta a ponta de 2026-09-16 deu para fechar um caixa com falta de dinheiro e
 * observacao vazia: a diferenca ficava gravada sem ninguem ter dito por que. Contagem que bate
 * continua fechando sem pedir nada.
 */
class ServicoFechamentoCaixaTest {

  private FechamentoCaixaRepository fechamentoCaixaRepository;
  private ComandaRepository comandaRepository;
  private TransacaoQueryRepository transacaoQueryRepository;
  private AuditService auditService;
  private ServicoFechamentoCaixa service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID caixaId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    fechamentoCaixaRepository = mock(FechamentoCaixaRepository.class);
    comandaRepository = mock(ComandaRepository.class);
    ComandaItemRepository comandaItemRepository = mock(ComandaItemRepository.class);
    when(comandaItemRepository.findByComandaIdOrderByCreatedAt(any())).thenReturn(List.of());
    TransacaoRepository transacaoRepository = mock(TransacaoRepository.class);
    transacaoQueryRepository = mock(TransacaoQueryRepository.class);
    auditService = mock(AuditService.class);

    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
    when(authenticatedUser.idOuNulo()).thenReturn(UUID.randomUUID());

    service =
        new ServicoFechamentoCaixa(
            fechamentoCaixaRepository,
            comandaRepository,
            comandaItemRepository,
            transacaoRepository,
            transacaoQueryRepository,
            contextoTenant,
            authenticatedUser,
            auditService,
            new ObjectMapper());

    // A resposta do fechamento monta o resumo de comissoes por SQL nativa; aqui basta nao explodir.
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of());
    EntityManager entityManager = mock(EntityManager.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    ReflectionTestUtils.setField(service, "entityManager", entityManager);
  }

  /** O dia esperava R$ 100 em dinheiro. */
  private FechamentoCaixa caixaAbertoEsperando100EmDinheiro() {
    FechamentoCaixa caixa = new FechamentoCaixa();
    caixa.setId(caixaId);
    caixa.setTenantId(tenantId);
    caixa.setBusinessDate(LocalDate.now());
    caixa.setStatus(StatusFechamentoCaixa.OPEN);
    when(fechamentoCaixaRepository.findByTenantIdAndId(eq(tenantId), eq(caixaId)))
        .thenReturn(Optional.of(caixa));

    Map<MetodoPagamento, Long> esperado = new LinkedHashMap<>();
    esperado.put(MetodoPagamento.CASH, 10_000L);
    when(transacaoQueryRepository.summarizeNetByPaymentMethod(eq(tenantId), any(), any()))
        .thenReturn(esperado);
    return caixa;
  }

  private FechamentoCaixaRequest contagem(String dinheiro, String observacoes) {
    FechamentoCaixaRequest request = new FechamentoCaixaRequest();
    request.countedTotals = Map.of("CASH", new BigDecimal(dinheiro));
    request.notes = observacoes;
    return request;
  }

  @Test
  @DisplayName("faltou dinheiro e ninguem explicou: nao fecha")
  void diferencaSemExplicacaoNaoFecha() {
    FechamentoCaixa caixa = caixaAbertoEsperando100EmDinheiro();

    assertThatThrownBy(() -> service.fechar(caixaId, contagem("0.00", "   ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Explique a diferenca entre o esperado e o contado antes de fechar o caixa.");

    assertThat(caixa.getStatus()).isEqualTo(StatusFechamentoCaixa.OPEN);
    assertThat(caixa.getClosedAt()).isNull();
    verify(auditService, never()).recordSuccess(any());
    // A tentativa de fechar com falta e sem explicar fica na trilha, mesmo com o rollback.
    verify(auditService).recordDeniedIsolated(any());
  }

  @Test
  @DisplayName("com a explicacao, fecha e registra a diferenca")
  void diferencaExplicadaFecha() {
    FechamentoCaixa caixa = caixaAbertoEsperando100EmDinheiro();

    service.fechar(caixaId, contagem("80.00", "  Faltou troco que ficou na gaveta do balcao.  "));

    assertThat(caixa.getStatus()).isEqualTo(StatusFechamentoCaixa.CLOSED);
    assertThat(caixa.getClosingNotes()).isEqualTo("Faltou troco que ficou na gaveta do balcao.");
    assertThat(caixa.getDifferenceTotalsJson()).contains("-20.00");

    org.mockito.ArgumentCaptor<AuditEventCommand> captor =
        org.mockito.ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    assertThat(captor.getValue().action).isEqualTo("FINANCE_CASH_CLOSING_CLOSE");
  }

  @Test
  @DisplayName("contagem que bate fecha sem exigir observacao")
  void contagemCertaFechaSemObservacao() {
    FechamentoCaixa caixa = caixaAbertoEsperando100EmDinheiro();

    service.fechar(caixaId, contagem("100.00", null));

    assertThat(caixa.getStatus()).isEqualTo(StatusFechamentoCaixa.CLOSED);
    assertThat(caixa.getClosingNotes()).isNull();
  }

  /**
   * Fechar o caixa TRAVA o dia: a comanda que ficou aberta so fecha amanha, com a venda caindo no
   * dia errado e o dinheiro fora da contagem que acabou de ser assinada. O fechamento nem olhava
   * para elas (pedido do usuario em 2026-09-21).
   */
  private Comanda comandaAbertaDeTresDiasAtras() {
    Comanda comanda = new Comanda();
    comanda.setId(UUID.randomUUID());
    comanda.setTenantId(tenantId);
    comanda.setStatus(Comanda.STATUS_ABERTA);
    comanda.setTotal(new BigDecimal("50.50"));
    comanda.setOpenedAt(Instant.now().minus(3, ChronoUnit.DAYS));
    return comanda;
  }

  @Test
  @DisplayName("ha comanda aberta e ninguem confirmou: nao fecha, e diz qual e a consequencia")
  void comandaAbertaSemConfirmacaoNaoFecha() {
    FechamentoCaixa caixa = caixaAbertoEsperando100EmDinheiro();
    when(comandaRepository.findByTenantIdAndStatusOrderByOpenedAtAsc(
            tenantId, Comanda.STATUS_ABERTA))
        .thenReturn(List.of(comandaAbertaDeTresDiasAtras()));

    assertThatThrownBy(() -> service.fechar(caixaId, contagem("100.00", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1 comanda aberta")
        .hasMessageContaining("50.50")
        // Sem isso a pessoa nao tem como saber o que perde ao confirmar.
        .hasMessageContaining("so poderao ser fechadas amanha");

    assertThat(caixa.getStatus()).isEqualTo(StatusFechamentoCaixa.OPEN);
    verify(auditService, never()).recordSuccess(any());
    // A tentativa fica na trilha: e a prova de que o aviso apareceu.
    verify(auditService).recordDeniedIsolated(any());
  }

  @Test
  @DisplayName("comanda aberta confirmada: fecha, porque deixar para amanha e escolha legitima")
  void comandaAbertaConfirmadaFecha() {
    FechamentoCaixa caixa = caixaAbertoEsperando100EmDinheiro();
    when(comandaRepository.findByTenantIdAndStatusOrderByOpenedAtAsc(
            tenantId, Comanda.STATUS_ABERTA))
        .thenReturn(List.of(comandaAbertaDeTresDiasAtras()));

    FechamentoCaixaRequest request = contagem("100.00", null);
    request.confirmarComandasAbertas = true;
    service.fechar(caixaId, request);

    assertThat(caixa.getStatus()).isEqualTo(StatusFechamentoCaixa.CLOSED);
  }

  /** Sem comanda aberta nada muda: a confirmacao nao passa a ser exigida de todo mundo. */
  @Test
  @DisplayName("sem comanda aberta, fecha sem pedir confirmacao nenhuma")
  void semComandaAbertaFechaDireto() {
    FechamentoCaixa caixa = caixaAbertoEsperando100EmDinheiro();
    when(comandaRepository.findByTenantIdAndStatusOrderByOpenedAtAsc(
            tenantId, Comanda.STATUS_ABERTA))
        .thenReturn(List.of());

    service.fechar(caixaId, contagem("100.00", null));

    assertThat(caixa.getStatus()).isEqualTo(StatusFechamentoCaixa.CLOSED);
  }
}
