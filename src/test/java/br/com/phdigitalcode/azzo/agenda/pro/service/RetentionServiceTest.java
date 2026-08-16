package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditRetentionEvent;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionEventRepository;

/** Espelha {@code modules/audit/application/RetentionService.java}. */
class RetentionServiceTest {

  private AuditRetentionEventRepository repository;
  private RetentionService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repository = mock(AuditRetentionEventRepository.class);
    service = new RetentionService(repository, new ObjectMapper());
  }

  @Test
  void registraEventoDePurgaComHashDeEvidencia() {
    Instant start = Instant.now().minusSeconds(3600);
    Instant end = Instant.now();

    AuditRetentionEvent event = service.registerPurgeEvent(
        tenantId, "v1", 365, start, end, 42L, "SCHEDULER", "exec-1", Map.of("k", "v"));

    assertThat(event.getTenantId()).isEqualTo(tenantId);
    assertThat(event.getPolicyVersion()).isEqualTo("v1");
    assertThat(event.getRetentionPeriodDays()).isEqualTo(365);
    assertThat(event.getWindowStart()).isEqualTo(start);
    assertThat(event.getWindowEnd()).isEqualTo(end);
    assertThat(event.getAffectedRows()).isEqualTo(42L);
    assertThat(event.getExecutedBy()).isEqualTo("SCHEDULER");
    assertThat(event.getExecutionId()).isEqualTo("exec-1");
    assertThat(event.getEvidenceHash()).isNotBlank();
    verify(repository).save(event);
  }

  @Test
  void hashDeEvidenciaEhDeterministicoParaOsMesmosDados() {
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant end = Instant.parse("2026-01-02T00:00:00Z");

    AuditRetentionEvent e1 = service.registerPurgeEvent(tenantId, "v1", 30, start, end, 5L, "SCHEDULER", "exec-1", null);
    AuditRetentionEvent e2 = service.registerPurgeEvent(tenantId, "v1", 30, start, end, 5L, "SCHEDULER", "exec-1", null);

    assertThat(e1.getEvidenceHash()).isEqualTo(e2.getEvidenceHash());
  }

  @Test
  void lancaQuandoPolicyVersionAusente() {
    assertThatThrownBy(() -> service.registerPurgeEvent(
            tenantId, " ", 30, Instant.now(), Instant.now(), 1L, "SCHEDULER", "exec-1", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lancaQuandoRetentionPeriodDaysInvalido() {
    assertThatThrownBy(() -> service.registerPurgeEvent(
            tenantId, "v1", 0, Instant.now(), Instant.now(), 1L, "SCHEDULER", "exec-1", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lancaQuandoJanelaAusente() {
    assertThatThrownBy(() -> service.registerPurgeEvent(
            tenantId, "v1", 30, null, Instant.now(), 1L, "SCHEDULER", "exec-1", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.registerPurgeEvent(
            tenantId, "v1", 30, Instant.now(), null, 1L, "SCHEDULER", "exec-1", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lancaQuandoExecutedByAusente() {
    assertThatThrownBy(() -> service.registerPurgeEvent(
            tenantId, "v1", 30, Instant.now(), Instant.now(), 1L, " ", "exec-1", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lancaQuandoExecutionIdAusente() {
    assertThatThrownBy(() -> service.registerPurgeEvent(
            tenantId, "v1", 30, Instant.now(), Instant.now(), 1L, "SCHEDULER", " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aceitaTenantIdNuloParaExpurgoGlobal() {
    AuditRetentionEvent event = service.registerPurgeEvent(
        null, "v1", 30, Instant.now().minusSeconds(10), Instant.now(), 1L, "SCHEDULER", "exec-1", null);
    assertThat(event.getTenantId()).isNull();
  }
}
