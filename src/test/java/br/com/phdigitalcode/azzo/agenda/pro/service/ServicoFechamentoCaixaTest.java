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
import java.time.LocalDate;
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
import br.com.phdigitalcode.azzo.agenda.pro.entity.FechamentoCaixa;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusFechamentoCaixa;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
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
  private TransacaoQueryRepository transacaoQueryRepository;
  private AuditService auditService;
  private ServicoFechamentoCaixa service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID caixaId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    fechamentoCaixaRepository = mock(FechamentoCaixaRepository.class);
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
}
