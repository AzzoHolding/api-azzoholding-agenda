package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.service.ChatMessageRetentionService;

/**
 * Espelha {@code modules/chat/infrastructure/scheduler/ChatMessageRetentionScheduler.java}: o
 * corpo delega ao service e uma excecao inesperada e repropagada, mesmo contrato do original.
 */
class ChatMessageRetentionSchedulerTest {

  private ChatMessageRetentionService retentionService;
  private ChatMessageRetentionScheduler scheduler;

  @BeforeEach
  void setUp() {
    retentionService = mock(ChatMessageRetentionService.class);
    scheduler = new ChatMessageRetentionScheduler(retentionService);
  }

  @Test
  void purgeExpiredMessageContentsDelegaAoService() {
    when(retentionService.purgeExpiredMessageContents()).thenReturn(5L);

    scheduler.purgeExpiredMessageContents();

    verify(retentionService, times(1)).purgeExpiredMessageContents();
  }

  @Test
  void purgeExpiredMessageContentsNaoFalhaQuandoNadaExpirou() {
    when(retentionService.purgeExpiredMessageContents()).thenReturn(0L);

    scheduler.purgeExpiredMessageContents();

    verify(retentionService, times(1)).purgeExpiredMessageContents();
  }

  @Test
  void excecaoDoServiceEhRepropagada() {
    when(retentionService.purgeExpiredMessageContents()).thenThrow(new RuntimeException("falha na purga"));

    assertThatThrownBy(() -> scheduler.purgeExpiredMessageContents())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("falha na purga");
  }
}
