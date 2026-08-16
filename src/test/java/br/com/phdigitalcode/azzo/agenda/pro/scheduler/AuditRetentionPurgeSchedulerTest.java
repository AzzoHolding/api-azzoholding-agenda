package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.service.AuditRetentionPurgeService;

/**
 * Espelha {@code modules/audit/infrastructure/scheduler/AuditRetentionPurgeScheduler.java}: o
 * corpo delega ao service e uma excecao inesperada e repropagada, mesmo contrato do original.
 */
class AuditRetentionPurgeSchedulerTest {

  private AuditRetentionPurgeService auditRetentionPurgeService;
  private AuditRetentionPurgeScheduler scheduler;

  @BeforeEach
  void setUp() {
    auditRetentionPurgeService = mock(AuditRetentionPurgeService.class);
    scheduler = new AuditRetentionPurgeScheduler(auditRetentionPurgeService);
  }

  @Test
  void purgeExpiredAuditEventsDelegaAoService() {
    when(auditRetentionPurgeService.purgeExpiredAuditEvents()).thenReturn(7L);

    scheduler.purgeExpiredAuditEvents();

    verify(auditRetentionPurgeService, times(1)).purgeExpiredAuditEvents();
  }

  @Test
  void purgeExpiredAuditEventsNaoFalhaQuandoNadaExpirou() {
    when(auditRetentionPurgeService.purgeExpiredAuditEvents()).thenReturn(0L);

    scheduler.purgeExpiredAuditEvents();

    verify(auditRetentionPurgeService, times(1)).purgeExpiredAuditEvents();
  }

  @Test
  void excecaoDoServiceEhRepropagada() {
    when(auditRetentionPurgeService.purgeExpiredAuditEvents()).thenThrow(new RuntimeException("falha na purga"));

    assertThatThrownBy(() -> scheduler.purgeExpiredAuditEvents())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("falha na purga");
  }
}
