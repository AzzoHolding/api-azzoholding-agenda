package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FechamentoCaixa;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusFechamentoCaixa;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FechamentoCaixaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;

/**
 * As travas do dinheiro ja conferido (analise de fraude de 2026-09-16): dia com caixa fechado nao
 * muda, lancamento fora de hoje e so do dono, nada mais de um ano a frente — e toda tentativa
 * barrada vira evento DENIED numa transacao propria.
 */
class TravaFinanceiraTest {

  private FechamentoCaixaRepository fechamentoCaixaRepository;
  private AuthenticatedUser authenticatedUser;
  private AuditService auditService;
  private TravaFinanceira trava;

  private final UUID tenantId = UUID.randomUUID();
  private final LocalDate hoje = LocalDate.now(TravaFinanceira.ZONA_BR);

  @BeforeEach
  void setUp() {
    fechamentoCaixaRepository = mock(FechamentoCaixaRepository.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    auditService = mock(AuditService.class);
    when(fechamentoCaixaRepository.findByTenantIdAndBusinessDate(any(), any()))
        .thenReturn(Optional.empty());
    trava = new TravaFinanceira(fechamentoCaixaRepository, authenticatedUser, auditService);
  }

  private Instant meioDia(LocalDate dia) {
    return dia.atTime(12, 0).atZone(TravaFinanceira.ZONA_BR).toInstant();
  }

  private void caixaDoDia(LocalDate dia, StatusFechamentoCaixa status) {
    FechamentoCaixa caixa = new FechamentoCaixa();
    caixa.setTenantId(tenantId);
    caixa.setBusinessDate(dia);
    caixa.setStatus(status);
    when(fechamentoCaixaRepository.findByTenantIdAndBusinessDate(eq(tenantId), eq(dia)))
        .thenReturn(Optional.of(caixa));
  }

  @Test
  void diaComCaixaFechadoNaoAceitaMexerEmDinheiro_nemODono() {
    LocalDate ontem = hoje.minusDays(1);
    caixaDoDia(ontem, StatusFechamentoCaixa.CLOSED);
    when(authenticatedUser.temRole("OWNER")).thenReturn(true);

    assertThatThrownBy(
            () ->
                trava.exigirDiaAberto(
                    tenantId, meioDia(ontem), "POS_COMANDA_REVERSE", "COMANDA", "c-1", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ja foi fechado");

    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordDeniedIsolated(captor.capture());
    assertThat(captor.getValue().action).isEqualTo("POS_COMANDA_REVERSE");
    assertThat(captor.getValue().entityId).isEqualTo("c-1");
    assertThat(captor.getValue().errorCode).isEqualTo("BLOQUEADO");
  }

  /** Caixa ABERTO, ou salao que nao usa fechamento de caixa: nada a travar. */
  @Test
  void diaSemCaixaOuComCaixaAbertoPassa() {
    caixaDoDia(hoje, StatusFechamentoCaixa.OPEN);

    assertThatCode(
            () -> trava.exigirDiaAberto(tenantId, meioDia(hoje), "X", "T", null, null))
        .doesNotThrowAnyException();
    assertThatCode(
            () -> trava.exigirDiaAberto(tenantId, meioDia(hoje.minusDays(3)), "X", "T", null, null))
        .doesNotThrowAnyException();
    verify(auditService, never()).recordDeniedIsolated(any());
  }

  @Test
  void equipeSoLancaComADataDeHoje() {
    assertThatCode(
            () -> trava.exigirDataDeLancamentoManual(tenantId, meioDia(hoje), "C", null, null))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () ->
                trava.exigirDataDeLancamentoManual(
                    tenantId, meioDia(hoje.minusDays(2)), "C", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("So o dono");
    assertThatThrownBy(
            () ->
                trava.exigirDataDeLancamentoManual(
                    tenantId, meioDia(hoje.plusDays(10)), "C", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("So o dono");
  }

  @Test
  void donoLancaEmOutraData_masNuncaMaisDeUmAnoAFrente() {
    when(authenticatedUser.temRole("OWNER")).thenReturn(true);

    assertThatCode(
            () ->
                trava.exigirDataDeLancamentoManual(
                    tenantId, meioDia(hoje.plusDays(30)), "C", null, null))
        .doesNotThrowAnyException();

    LocalDate daquiAAnos = hoje.plus(5, ChronoUnit.YEARS);
    assertThatThrownBy(
            () ->
                trava.exigirDataDeLancamentoManual(
                    tenantId, meioDia(daquiAAnos), "C", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("muito distante");
    verify(auditService).recordDeniedIsolated(any());
  }

  /** A trilha nunca decide a resposta: auditoria fora do ar nao vira erro 500. */
  @Test
  void falhaDaAuditoriaNaoMudaAResposta() {
    LocalDate ontem = hoje.minusDays(1);
    caixaDoDia(ontem, StatusFechamentoCaixa.CLOSED);
    when(auditService.recordDeniedIsolated(any())).thenThrow(new RuntimeException("banco fora"));

    assertThatThrownBy(
            () -> trava.exigirDiaAberto(tenantId, meioDia(ontem), "X", "T", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ja foi fechado");
  }
}
