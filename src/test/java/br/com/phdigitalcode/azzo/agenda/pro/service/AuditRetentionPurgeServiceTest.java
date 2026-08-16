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

import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionConfigRepository;

/** Espelha {@code modules/audit/application/AuditRetentionPurgeService.java}. */
class AuditRetentionPurgeServiceTest {

  private AuditEventRepository auditEventRepository;
  private RetentionService retentionService;
  private AuditRetentionConfigRepository auditRetentionConfigRepository;
  private AuditRetentionPurgeService service;

  @BeforeEach
  void setUp() throws Exception {
    auditEventRepository = mock(AuditEventRepository.class);
    retentionService = mock(RetentionService.class);
    auditRetentionConfigRepository = mock(AuditRetentionConfigRepository.class);
    service = new AuditRetentionPurgeService(auditEventRepository, retentionService, auditRetentionConfigRepository);
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
    verify(auditEventRepository, never()).purgeBeforeByTenant(any(), any());
  }

  @Test
  void purgaCadaTenantElegivelERegistraEvidencia() {
    UUID tenant1 = UUID.randomUUID();
    UUID tenant2 = UUID.randomUUID();
    when(auditRetentionConfigRepository.findRetentionPeriodDays()).thenReturn(Optional.of(90));
    when(auditEventRepository.findTenantIdsWithEventsBefore(any(), eq(500))).thenReturn(List.of(tenant1, tenant2));
    when(auditEventRepository.findOldestCreatedAtBefore(eq(tenant1), any())).thenReturn(Optional.of(Instant.now().minusSeconds(1000)));
    when(auditEventRepository.findOldestCreatedAtBefore(eq(tenant2), any())).thenReturn(Optional.empty());
    when(auditEventRepository.purgeBeforeByTenant(eq(tenant1), any())).thenReturn(10L);
    when(auditEventRepository.purgeBeforeByTenant(eq(tenant2), any())).thenReturn(0L);

    long purged = service.purgeExpiredAuditEvents();

    assertThat(purged).isEqualTo(10L);
    verify(retentionService, times(1)).registerPurgeEvent(
        eq(tenant1), eq("v1"), eq(90), any(), any(), eq(10L), eq("SCHEDULER"), any(), any());
    // tenant2 nao purgou nada (purged <= 0): nao registra evidencia para ele.
    verify(retentionService, times(1)).registerPurgeEvent(any(), any(), anyInt(), any(), any(), anyLong(), any(), any(), any());
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
    when(auditEventRepository.purgeBeforeByTenant(eq(tenantId), any())).thenReturn(0L);

    long purged = service.purgeTenant(tenantId, Instant.now(), 30, "exec-1");

    assertThat(purged).isEqualTo(0);
    verify(retentionService, never()).registerPurgeEvent(any(), any(), anyInt(), any(), any(), anyLong(), any(), any(), any());
  }
}
