package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.service.AfterCommitExecutor;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoConfirmacaoDeAgendamento;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * AGD-008: o aviso de cancelamento sai DEPOIS do commit. Os chamadores (mudanca de status e
 * fechamento especial) rodam dentro da transacao do cancelamento; avisar antes mandaria "cancelado"
 * de um cancelamento que depois sofre rollback.
 */
class WhatsAppAppointmentNotificationServiceTest {

  private static final UUID TENANT = UUID.randomUUID();

  private ServicoConfirmacaoDeAgendamento servicoConfirmacao;
  private Agendamento agendamento;

  @BeforeEach
  void preparar() {
    servicoConfirmacao = mock(ServicoConfirmacaoDeAgendamento.class);
    agendamento = new Agendamento();
    agendamento.setId(UUID.randomUUID());
    agendamento.setTenantId(TENANT);
  }

  @AfterEach
  void limpar() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void cancelamentoSoEnviaQuandoATarefaPosCommitRoda() {
    AfterCommitExecutor executor = mock(AfterCommitExecutor.class);
    var service = new WhatsAppAppointmentNotificationService(servicoConfirmacao, executor);

    service.sendCancellation(TENANT, agendamento);

    ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).run(tarefa.capture());
    verifyNoInteractions(servicoConfirmacao);

    tarefa.getValue().run();

    verify(servicoConfirmacao).enviarCancelamento(TENANT, agendamento);
  }

  @Test
  void semTenantOuAgendamentoNaoAgendaNada() {
    AfterCommitExecutor executor = mock(AfterCommitExecutor.class);
    var service = new WhatsAppAppointmentNotificationService(servicoConfirmacao, executor);

    service.sendCancellation(null, agendamento);
    service.sendCancellation(TENANT, null);

    verifyNoInteractions(executor, servicoConfirmacao);
  }

  /** Com o executor de verdade: rollback nunca envia, commit envia. */
  @Test
  void rollbackNaoEnviaECommitEnvia() {
    var service = new WhatsAppAppointmentNotificationService(servicoConfirmacao, new AfterCommitExecutor());

    // transacao que sofre rollback
    TransactionSynchronizationManager.initSynchronization();
    service.sendCancellation(TENANT, agendamento);
    var sincronizacoes = new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
    sincronizacoes.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    TransactionSynchronizationManager.clearSynchronization();
    verify(servicoConfirmacao, after(400).never()).enviarCancelamento(any(), any());

    // transacao que commita
    TransactionSynchronizationManager.initSynchronization();
    service.sendCancellation(TENANT, agendamento);
    var commit = new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
    commit.forEach(TransactionSynchronization::afterCommit);
    TransactionSynchronizationManager.clearSynchronization();
    verify(servicoConfirmacao, timeout(3000)).enviarCancelamento(TENANT, agendamento);
  }

  @Test
  void confirmacaoContinuaDelegando() {
    var service = new WhatsAppAppointmentNotificationService(servicoConfirmacao, mock(AfterCommitExecutor.class));

    service.sendConfirmation(TENANT, agendamento);

    verify(servicoConfirmacao).enviar(TENANT, agendamento);
  }

  /** No-show continua sem enviar: nao ha template, e avisar que o cliente faltou e decisao de produto. */
  @Test
  void noShowContinuaSoNoLog() {
    AfterCommitExecutor executor = mock(AfterCommitExecutor.class);
    var service = new WhatsAppAppointmentNotificationService(servicoConfirmacao, executor);

    service.sendNoShow(agendamento);

    verifyNoInteractions(servicoConfirmacao, executor);
    verify(servicoConfirmacao, never()).enviarCancelamento(any(), any());
  }
}
