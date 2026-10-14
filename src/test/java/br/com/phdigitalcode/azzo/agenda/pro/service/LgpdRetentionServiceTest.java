package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationSendLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantSpecialClosureDateRepository;

/** Espelha {@code scheduler/LgpdRetentionService.java}. */
class LgpdRetentionServiceTest {

  private DataSource dataSource;
  private Connection connection;
  private PreparedStatement statement;
  private TenantSpecialClosureDateRepository closureDateRepository;
  private ReactivationSendLogRepository reactivationSendLogRepository;
  private LgpdRetentionService service;
  private ProcessadorImportacaoClientesService processadorImportacaoClientes;

  @BeforeEach
  void setUp() throws SQLException {
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    statement = mock(PreparedStatement.class);
    closureDateRepository = mock(TenantSpecialClosureDateRepository.class);
    reactivationSendLogRepository = mock(ReactivationSendLogRepository.class);
    processadorImportacaoClientes = mock(ProcessadorImportacaoClientesService.class);
    service = new LgpdRetentionService(
        dataSource, closureDateRepository, reactivationSendLogRepository, processadorImportacaoClientes);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
  }

  @Test
  void purgarDadosExpiradosExecutaAsQuatroEtapas() throws SQLException {
    when(statement.executeUpdate()).thenReturn(3);
    when(closureDateRepository.deleteByClosureDateBefore(any(LocalDate.class))).thenReturn(5L);
    when(reactivationSendLogRepository.deleteExpired(any(Instant.class))).thenReturn(2L);

    service.purgarDadosExpirados();

    // webhooks, whatsapp, notificacoes, e-mails, linhas de erro da importacao e senhas de certificado
    verify(connection, times(6)).prepareStatement(any());
    verify(closureDateRepository).deleteByClosureDateBefore(any(LocalDate.class));
    verify(reactivationSendLogRepository).deleteExpired(any(Instant.class));
    verify(processadorImportacaoClientes).limparArquivosDeJobsFinalizados();
  }

  @Test
  void dadosSemPrazoGanhamPrazo() throws SQLException {
    org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
    when(statement.executeUpdate()).thenReturn(0);

    service.purgarDadosSemPrazo();

    verify(connection, times(4)).prepareStatement(sql.capture());
    assertThat(sql.getAllValues())
        .anyMatch(s -> s.contains("FROM notifications") && s.contains("180 days"))
        .anyMatch(s -> s.contains("FROM email_jobs") && s.contains("'PROCESSED', 'FAILED'"))
        .anyMatch(s -> s.contains("FROM importacao_cliente_erro_linha"))
        .anyMatch(s -> s.contains("nfse_certificate_unlock_sessions") && s.contains("password_enc = NULL"));
  }

  @Test
  void falhaAoLimparPlanilhasNaoDerrubaAPurga() {
    when(processadorImportacaoClientes.limparArquivosDeJobsFinalizados()).thenThrow(new IllegalStateException("minio"));
    service.purgarDadosSemPrazo();
  }

  @Test
  void purgarFechamentosAntigosUsaCorteDeDoisAnos() {
    when(closureDateRepository.deleteByClosureDateBefore(any(LocalDate.class))).thenReturn(7L);

    long count = service.purgarFechamentosAntigos();

    assertThat(count).isEqualTo(7L);
    verify(closureDateRepository).deleteByClosureDateBefore(LocalDate.now().minusYears(2));
  }

  @Test
  void purgarLogsReativacaoDelegaAoRepositorio() {
    when(reactivationSendLogRepository.deleteExpired(any(Instant.class))).thenReturn(4L);

    service.purgarLogsReativacao();

    verify(reactivationSendLogRepository).deleteExpired(any(Instant.class));
  }

  @Test
  void purgarLogsReativacaoNaoLogaQuandoNadaPurgado() {
    when(reactivationSendLogRepository.deleteExpired(any(Instant.class))).thenReturn(0L);
    service.purgarLogsReativacao();
    verify(reactivationSendLogRepository).deleteExpired(any(Instant.class));
  }

  @Test
  void purgarNaoLancaQuandoConexaoFalha() throws SQLException {
    when(dataSource.getConnection()).thenThrow(new SQLException("conexao indisponivel"));
    when(closureDateRepository.deleteByClosureDateBefore(any(LocalDate.class))).thenReturn(0L);
    when(reactivationSendLogRepository.deleteExpired(any(Instant.class))).thenReturn(0L);

    service.purgarDadosExpirados();
    // nao deve lancar: erro de purga SQL e engolido e logado
  }

  @Test
  void logDadosExpiradosPercorreOResultSet() throws SQLException {
    ResultSet resultSet = mock(ResultSet.class);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getString("tabela")).thenReturn("webhook_event_logs");
    when(resultSet.getLong("registros_expirados")).thenReturn(10L);
    when(resultSet.getObject("mais_antigo")).thenReturn(Instant.now());

    service.logDadosExpirados();

    verify(resultSet, times(2)).next();
  }

  @Test
  void logDadosExpiradosNaoLancaQuandoQueryFalha() throws SQLException {
    when(statement.executeQuery()).thenThrow(new SQLException("view ausente"));
    service.logDadosExpirados();
    // nao deve lancar: erro e logado, nao propagado
  }
}
