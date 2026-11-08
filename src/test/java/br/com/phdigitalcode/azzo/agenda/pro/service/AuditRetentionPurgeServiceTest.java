package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionConfigRepository;

/** Espelha {@code modules/audit/application/AuditRetentionPurgeService.java}. */
class AuditRetentionPurgeServiceTest {

  private AuditEventRepository auditEventRepository;
  private RetentionService retentionService;
  private AuditRetentionConfigRepository auditRetentionConfigRepository;
  private AuditRetentionPurgeService service;
  private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() throws Exception {
    auditEventRepository = mock(AuditEventRepository.class);
    retentionService = mock(RetentionService.class);
    auditRetentionConfigRepository = mock(AuditRetentionConfigRepository.class);
    transactionManager = mock(PlatformTransactionManager.class);
    service =
        new AuditRetentionPurgeService(
            auditEventRepository, retentionService, auditRetentionConfigRepository, transactionManager);
    setPrivateField("fallbackRetentionDays", 365);
    setPrivateField("policyVersion", "v1");
    setPrivateField("maxTenantsPerRun", 500);
  }

  private void setPrivateField(String name, Object value) throws Exception {
    Field field = AuditRetentionPurgeService.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(service, value);
  }

  @Test
  void retornaZeroSemChamarPurgaQuandoNaoHaTenantsElegiveis() {
    when(auditRetentionConfigRepository.findRetentionPeriodDays()).thenReturn(Optional.of(90));
    when(auditEventRepository.findTenantIdsWithEventsBefore(any(), eq(500))).thenReturn(List.of());

    long purged = service.purgeExpiredAuditEvents();

    assertThat(purged).isEqualTo(0);
    verify(auditEventRepository, never()).deleteBeforeByTenantRespeitandoACadeia(any(), any(), anyLong());
  }

  @Test
  void purgaCadaTenantElegivelERegistraEvidencia() {
    UUID tenant1 = UUID.randomUUID();
    UUID tenant2 = UUID.randomUUID();
    when(auditRetentionConfigRepository.findRetentionPeriodDays()).thenReturn(Optional.of(90));
    when(auditEventRepository.findTenantIdsWithEventsBefore(any(), eq(500))).thenReturn(List.of(tenant1, tenant2));
    when(auditEventRepository.findOldestCreatedAtBefore(eq(tenant1), any())).thenReturn(Optional.of(Instant.now().minusSeconds(1000)));
    when(auditEventRepository.findOldestCreatedAtBefore(eq(tenant2), any())).thenReturn(Optional.empty());
    when(auditEventRepository.deleteBeforeByTenantRespeitandoACadeia(eq(tenant1), any(), anyLong())).thenReturn(10);
    when(auditEventRepository.deleteBeforeByTenantRespeitandoACadeia(eq(tenant2), any(), anyLong())).thenReturn(0);

    long purged = service.purgeExpiredAuditEvents();

    assertThat(purged).isEqualTo(10L);
    verify(retentionService, times(1)).registerPurgeEvent(
        eq(tenant1), eq("v1"), eq(90), any(), any(), eq(10L), eq("SCHEDULER"), any(), any(), any(), any());
    // tenant2 nao purgou nada (purged <= 0): nao registra evidencia para ele.
    verify(retentionService, times(1)).registerPurgeEvent(any(), any(), anyInt(), any(), any(), anyLong(), any(), any(), any(), any(), any());
  }

  /**
   * O gatilho deny_audit_events_mutation so deixa o DELETE passar com a flag ligada NA MESMA
   * transacao: cada tenant precisa da sua, aberta de verdade (26/10/2026).
   */
  @Test
  void cadaTenantRodaNumaTransacaoPropria() {
    UUID tenant1 = UUID.randomUUID();
    UUID tenant2 = UUID.randomUUID();
    when(auditRetentionConfigRepository.findRetentionPeriodDays()).thenReturn(Optional.of(90));
    when(auditEventRepository.findTenantIdsWithEventsBefore(any(), eq(500))).thenReturn(List.of(tenant1, tenant2));
    when(auditEventRepository.findOldestCreatedAtBefore(any(), any())).thenReturn(Optional.empty());

    service.purgeExpiredAuditEvents();

    ArgumentCaptor<TransactionDefinition> definicoes = ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager, times(2)).getTransaction(definicoes.capture());
    assertThat(definicoes.getAllValues())
        .allMatch(d -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Test
  void usaRetentionDoBancoQuandoConfigurado() {
    when(auditRetentionConfigRepository.findRetentionPeriodDays()).thenReturn(Optional.of(45));
    when(auditEventRepository.findTenantIdsWithEventsBefore(any(), eq(500))).thenReturn(List.of());

    service.purgeExpiredAuditEvents();

    verify(auditEventRepository).findTenantIdsWithEventsBefore(any(), eq(500));
  }

  @Test
  void usaFallbackQuandoBancoNaoTemConfig() throws Exception {
    when(auditRetentionConfigRepository.findRetentionPeriodDays()).thenReturn(Optional.empty());
    when(auditEventRepository.findTenantIdsWithEventsBefore(any(), eq(500))).thenReturn(List.of());

    service.purgeExpiredAuditEvents();

    verify(auditEventRepository).findTenantIdsWithEventsBefore(any(), eq(500));
  }

  @Test
  void usaFallbackQuandoBancoRetornaValorInvalido() {
    when(auditRetentionConfigRepository.findRetentionPeriodDays()).thenReturn(Optional.of(0));
    when(auditEventRepository.findTenantIdsWithEventsBefore(any(), eq(500))).thenReturn(List.of());

    service.purgeExpiredAuditEvents();

    verify(auditEventRepository).findTenantIdsWithEventsBefore(any(), eq(500));
  }

  @Test
  void purgeTenantRetornaZeroSemRegistrarEvidenciaQuandoNadaPurgado() {
    UUID tenantId = UUID.randomUUID();
    when(auditEventRepository.findOldestCreatedAtBefore(eq(tenantId), any())).thenReturn(Optional.empty());
    when(auditEventRepository.deleteBeforeByTenantRespeitandoACadeia(eq(tenantId), any(), anyLong())).thenReturn(0);

    long purged = service.purgeTenant(tenantId, Instant.now(), 30, "exec-1");

    assertThat(purged).isEqualTo(0);
    verify(retentionService, never()).registerPurgeEvent(any(), any(), anyInt(), any(), any(), anyLong(), any(), any(), any(), any(), any());
  }

  /**
   * O expurgo remove so um prefixo da cadeia selada e guarda o elo do ultimo removido: o primeiro
   * evento que fica continua conferivel (V164).
   */
  @Test
  void expurgoGuardaAAncoraDoUltimoSeladoRemovidoESoCortaAntesDoPrimeiroQueFica() {
    UUID tenantId = UUID.randomUUID();
    Instant cutoff = Instant.now();
    br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent ultimo = new br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent();
    ultimo.setChainSeq(7L);
    ultimo.setChainHash("elo-7");
    when(auditEventRepository.findOldestCreatedAtBefore(eq(tenantId), any())).thenReturn(Optional.empty());
    when(auditEventRepository.primeiraPosicaoQueFica(tenantId, cutoff)).thenReturn(8L);
    when(auditEventRepository.ultimoSeladoAExpurgar(eq(tenantId), eq(cutoff), eq(8L), any()))
        .thenReturn(List.of(ultimo));
    when(auditEventRepository.deleteBeforeByTenantRespeitandoACadeia(tenantId, cutoff, 7L)).thenReturn(12);

    long purged = service.purgeTenant(tenantId, cutoff, 30, "exec-1");

    assertThat(purged).isEqualTo(12);
    verify(auditEventRepository).esperarTravaDoSelador();
    verify(auditEventRepository).enableRetentionPurgeFlag();
    verify(retentionService).registerPurgeEvent(
        eq(tenantId), any(), eq(30), any(), eq(cutoff), eq(12L), eq("SCHEDULER"), eq("exec-1"), any(),
        eq("elo-7"), eq(7L));
  }

  @Test
  void semSeladoParaRemoverAAncoraEhOUltimoLegadoRemovido() {
    UUID tenantId = UUID.randomUUID();
    Instant cutoff = Instant.now();
    br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent legado = new br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent();
    legado.setEventHash("hash-legado");
    when(auditEventRepository.findOldestCreatedAtBefore(eq(tenantId), any())).thenReturn(Optional.empty());
    when(auditEventRepository.ultimoSeladoAExpurgar(eq(tenantId), eq(cutoff), eq(Long.MAX_VALUE), any()))
        .thenReturn(List.of());
    when(auditEventRepository.ultimoLegadoAExpurgar(eq(tenantId), eq(cutoff), any())).thenReturn(List.of(legado));
    when(auditEventRepository.deleteBeforeByTenantRespeitandoACadeia(tenantId, cutoff, 0L)).thenReturn(3);

    service.purgeTenant(tenantId, cutoff, 30, "exec-1");

    verify(retentionService).registerPurgeEvent(
        eq(tenantId), any(), eq(30), any(), eq(cutoff), eq(3L), eq("SCHEDULER"), eq("exec-1"), any(),
        eq("hash-legado"), eq(null));
  }
}
