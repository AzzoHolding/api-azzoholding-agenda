package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.AuditDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditRetentionEvent;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditRetentionEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;

/** Espelha {@code modules/audit/application/AuditQueryService.java}. */
@SuppressWarnings("unchecked")
class AuditQueryServiceTest {

  private AuditEventRepository auditEventRepository;
  private AuditRetentionEventRepository auditRetentionEventRepository;
  private UsuarioRepository usuarioRepository;
  private AuditQueryService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    auditEventRepository = mock(AuditEventRepository.class);
    auditRetentionEventRepository = mock(AuditRetentionEventRepository.class);
    usuarioRepository = mock(UsuarioRepository.class);
    when(usuarioRepository.mapNamesByTenantAndIds(any(), any())).thenReturn(Map.of());
    service = new AuditQueryService(auditEventRepository, auditRetentionEventRepository, usuarioRepository, new ObjectMapper());
  }

  private AuditEvent buildEvent(String module, UUID actorUserId, Instant createdAt) {
    AuditEvent event = new AuditEvent();
    event.setId(UUID.randomUUID());
    event.setTenantId(tenantId);
    event.setActorUserId(actorUserId);
    event.setActorRole("OWNER");
    event.setModule(module);
    event.setAction("SOME_ACTION");
    event.setEntityType("SOME_TYPE");
    event.setEntityId("123");
    event.setStatus(AuditConstants.Status.SUCCESS);
    event.setRequestId(UUID.randomUUID().toString());
    event.setSourceChannel(AuditConstants.SourceChannel.API);
    event.setHasChanges(false);
    event.setEventHash("hash-" + UUID.randomUUID());
    event.setCreatedAt(createdAt);
    return event;
  }

  @Test
  void searchRetornaItensSemProximaPaginaQuandoAbaixoDoLimite() {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now());
    Page<AuditEvent> page = new PageImpl<>(List.of(event));
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

    AuditDtos.AuditSearchResponse response =
        service.search(tenantId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    assertThat(response.hasNext).isFalse();
    assertThat(response.nextCursor).isNull();
    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).module).isEqualTo(AuditConstants.Module.FINANCE);
    assertThat(response.limit).isEqualTo(50);
  }

  @Test
  void searchSinalizaHasNextEGeraCursorQuandoExcedeOLimite() {
    AuditEvent e1 = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now());
    AuditEvent e2 = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now().minusSeconds(1));
    Page<AuditEvent> page = new PageImpl<>(List.of(e1, e2));
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

    AuditDtos.AuditSearchResponse response =
        service.search(tenantId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 1);

    assertThat(response.limit).isEqualTo(1);
    assertThat(response.hasNext).isTrue();
    assertThat(response.items).hasSize(1);
    assertThat(response.nextCursor).isNotBlank();
  }

  @Test
  void searchNormalizaLimiteAbaixoDoMinimoEAcimaDoMaximo() {
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.search(tenantId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0);
    verify(auditEventRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 51,
        org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("createdAt"),
            org.springframework.data.domain.Sort.Order.desc("id")))));

    AuditDtos.AuditSearchResponse response =
        service.search(tenantId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 500);
    assertThat(response.limit).isEqualTo(200);
  }

  @Test
  void detailLancaQuandoEventoNaoEncontrado() {
    when(auditEventRepository.findById(any())).thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> service.detail(tenantId, UUID.randomUUID().toString()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void detailLancaQuandoEventoDeOutroTenant() {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now());
    event.setTenantId(UUID.randomUUID());
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    assertThatThrownBy(() -> service.detail(tenantId, event.getId().toString()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void detailLancaQuandoModuloEhSystem() {
    AuditEvent event = buildEvent(AuditConstants.Module.SYSTEM, UUID.randomUUID(), Instant.now());
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    assertThatThrownBy(() -> service.detail(tenantId, event.getId().toString()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void detailLancaQuandoAtorNulo() {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, null, Instant.now());
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    assertThatThrownBy(() -> service.detail(tenantId, event.getId().toString()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void detailRetornaChainValidTrueQuandoNaoHaPrevHash() {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now());
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    AuditDtos.AuditEventDetail detail = service.detail(tenantId, event.getId().toString());

    assertThat(detail.chainValid).isTrue();
    assertThat(detail.id).isEqualTo(event.getId().toString());
  }

  @Test
  void detailRetornaChainValidFalseQuandoHashAnteriorNaoBate() {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now());
    event.setPrevEventHash("hash-esperado");
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    AuditEvent previous = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), event.getCreatedAt().minusSeconds(10));
    previous.setEventHash("hash-diferente");
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(previous)));

    AuditDtos.AuditEventDetail detail = service.detail(tenantId, event.getId().toString());

    assertThat(detail.chainValid).isFalse();
  }

  @Test
  void detailRetornaChainValidTrueQuandoHashAnteriorBate() {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now());
    event.setPrevEventHash("hash-esperado");
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    AuditEvent previous = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), event.getCreatedAt().minusSeconds(10));
    previous.setEventHash("hash-esperado");
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(previous)));

    AuditDtos.AuditEventDetail detail = service.detail(tenantId, event.getId().toString());

    assertThat(detail.chainValid).isTrue();
  }

  @Test
  void detailUsaLimiteDePurgaQuandoNaoHaCandidatoAnterior() {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now());
    event.setPrevEventHash("hash-esperado");
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of()));
    when(auditRetentionEventRepository.existsPurgeBoundaryBefore(tenantId, event.getCreatedAt())).thenReturn(true);

    AuditDtos.AuditEventDetail detail = service.detail(tenantId, event.getId().toString());

    assertThat(detail.chainValid).isTrue();
  }

  @Test
  void filterOptionsDelegaAoRepositorio() {
    AuditDtos.AuditFilterOptionsResponse expected = new AuditDtos.AuditFilterOptionsResponse();
    when(auditEventRepository.aggregateFilterOptions(eq(tenantId), any(), any())).thenReturn(expected);

    AuditDtos.AuditFilterOptionsResponse response = service.filterOptions(tenantId, null, null);

    assertThat(response).isSameAs(expected);
  }

  @Test
  void listRetentionEventsPagedAplicaLimiteESinalizaHasNext() {
    AuditRetentionEvent e1 = new AuditRetentionEvent();
    e1.setId(UUID.randomUUID());
    AuditRetentionEvent e2 = new AuditRetentionEvent();
    e2.setId(UUID.randomUUID());
    when(auditRetentionEventRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
        .thenReturn(List.of(e1, e2));

    AuditDtos.AuditRetentionPageResponse response = service.listRetentionEventsPaged(tenantId, null, null, null, 1);

    assertThat(response.items).hasSize(1);
    assertThat(response.hasNext).isTrue();
    assertThat(response.nextCursor).isEqualTo(e1.getId().toString());
  }

  @Test
  void exportGeraJsonPorPadraoEDownloadFunciona() {
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of()));

    AuditDtos.AuditExportRequest request = new AuditDtos.AuditExportRequest();
    AuditDtos.AuditExportResponse response = service.export(tenantId, request);

    assertThat(response.format).isEqualTo("JSON");
    assertThat(response.exportId).isNotBlank();
    assertThat(response.downloadUrl).isEqualTo("/api/v1/auditoria/events/export/" + response.exportId);

    AuditQueryService.ExportDownload download = service.downloadExport(response.exportId);
    assertThat(download.format()).isEqualTo("JSON");
    assertThat(download.contentType()).contains("application/json");
  }

  @Test
  void exportGeraCsvQuandoSolicitado() {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now());
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(event)));

    AuditDtos.AuditExportRequest request = new AuditDtos.AuditExportRequest();
    request.format = "csv";
    AuditDtos.AuditExportResponse response = service.export(tenantId, request);

    assertThat(response.format).isEqualTo("CSV");
    AuditQueryService.ExportDownload download = service.downloadExport(response.exportId);
    assertThat(download.contentType()).contains("text/csv");
    assertThat(download.payload()).contains("id,createdAt,module,action");
  }

  @Test
  void exportComRequestNuloUsaFiltrosVazios() {
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of()));

    AuditDtos.AuditExportResponse response = service.export(tenantId, null);

    assertThat(response.format).isEqualTo("JSON");
  }

  @Test
  void downloadExportLancaQuandoExportIdInvalido() {
    assertThatThrownBy(() -> service.downloadExport(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.downloadExport("  ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void downloadExportLancaQuandoNaoEncontrado() {
    assertThatThrownBy(() -> service.downloadExport("inexistente")).isInstanceOf(IllegalArgumentException.class);
  }

}
