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
  void detailDevolveOsDadosDoEventoQueOJackson3SerializaComoSao() {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.now());
    event.setAfterJson("{\"reason\":\"Valor diferente\"}");
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    AuditDtos.AuditEventDetail detail = service.detail(tenantId, event.getId().toString());

    // A resposta sai em Jackson 3: um JsonNode do Jackson 2 viraria "textual":false, sem os dados.
    assertThat(new tools.jackson.databind.json.JsonMapper().writeValueAsString(detail.after))
        .isEqualTo("{\"reason\":\"Valor diferente\"}");
  }

  /** Evento v1 com o hash que a versao antiga gravava (instante em nanossegundos). */
  private AuditEvent eventoV1(String anterior) {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.parse("2026-10-01T12:00:00.123456789Z"));
    event.setPrevEventHash(anterior);
    event.setEventHash(br.com.phdigitalcode.azzo.agenda.pro.integration.HashesDaAuditoria.hashV1(event));
    event.setCreatedAt(Instant.parse("2026-10-01T12:00:00.123457Z")); // o banco arredonda
    return event;
  }

  private AuditEvent eventoV2Selado(long posicao, String anterior) {
    AuditEvent event = buildEvent(AuditConstants.Module.FINANCE, UUID.randomUUID(), Instant.parse("2026-11-08T12:00:00.123456Z"));
    event.setHashVersion((short) 2);
    event.setPayloadDigests(br.com.phdigitalcode.azzo.agenda.pro.integration.HashesDaAuditoria.digestsDosJson(null, null, null));
    event.setEventHash(br.com.phdigitalcode.azzo.agenda.pro.integration.HashesDaAuditoria.hashDoConteudoV2(event));
    event.setChainSeq(posicao);
    event.setPrevEventHash(anterior);
    event.setSealedAt(Instant.now());
    event.setChainHash(br.com.phdigitalcode.azzo.agenda.pro.integration.HashesDaAuditoria.hashDoElo(event));
    return event;
  }

  @Test
  void detailLegadoSemAnteriorComHashCorretoConfere() {
    AuditEvent event = eventoV1(null);
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    AuditDtos.AuditEventDetail detail = service.detail(tenantId, event.getId().toString());

    assertThat(detail.chainValid).isTrue();
    assertThat(detail.id).isEqualTo(event.getId().toString());
  }

  @Test
  void detailLegadoAdulteradoNaoConfere() {
    AuditEvent event = eventoV1(null);
    event.setAction("OUTRA_ACAO");
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    assertThat(service.detail(tenantId, event.getId().toString()).chainValid).isFalse();
  }

  @Test
  void detailLegadoConfereOEloPeloAnteriorQueExisteOuPeloExpurgo() {
    AuditEvent event = eventoV1("hash-anterior");
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    when(auditEventRepository.count(any(Specification.class))).thenReturn(1L);
    assertThat(service.detail(tenantId, event.getId().toString()).chainValid).isTrue();

    when(auditEventRepository.count(any(Specification.class))).thenReturn(0L);
    assertThat(service.detail(tenantId, event.getId().toString()).chainValid).isFalse();

    when(auditRetentionEventRepository.existsPurgeBoundaryBefore(tenantId, event.getCreatedAt())).thenReturn(true);
    assertThat(service.detail(tenantId, event.getId().toString()).chainValid).isTrue();
  }

  @Test
  void detailSeladoConfereOConteudoEOEloComAPosicaoAnterior() {
    AuditEvent anterior = eventoV2Selado(4, "elo-3");
    AuditEvent event = eventoV2Selado(5, anterior.getChainHash());
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));
    when(auditEventRepository.naPosicao(tenantId, 4)).thenReturn(java.util.Optional.of(anterior));

    assertThat(service.detail(tenantId, event.getId().toString()).chainValid).isTrue();

    event.setIpAddress("192.168.0.99");
    assertThat(service.detail(tenantId, event.getId().toString()).chainValid).isFalse();
  }

  @Test
  void detailAindaSemSeloNaoDizQueConfereNemQueQuebrou() {
    AuditEvent event = eventoV2Selado(1, null);
    event.setChainSeq(null);
    event.setChainHash(null);
    when(auditEventRepository.findById(event.getId())).thenReturn(java.util.Optional.of(event));

    assertThat(service.detail(tenantId, event.getId().toString()).chainValid).isNull();
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

    AuditQueryService.ExportDownload download = service.downloadExport(tenantId, response.exportId);
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
    AuditQueryService.ExportDownload download = service.downloadExport(tenantId, response.exportId);
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
    assertThatThrownBy(() -> service.downloadExport(tenantId, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.downloadExport(tenantId, "  ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void downloadExportLancaQuandoNaoEncontrado() {
    assertThatThrownBy(() -> service.downloadExport(tenantId, "inexistente"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void downloadExportDeOutroSalaoRespondeComoNaoEncontrado() {
    when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of()));
    AuditDtos.AuditExportResponse response = service.export(tenantId, null);

    assertThatThrownBy(() -> service.downloadExport(UUID.randomUUID(), response.exportId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Export não encontrado ou expirado");
    assertThatThrownBy(() -> service.downloadExport(null, response.exportId))
        .isInstanceOf(IllegalArgumentException.class);
    // O dono da exportacao continua baixando: a recusa acima nao a apagou.
    assertThat(service.downloadExport(tenantId, response.exportId).format()).isEqualTo("JSON");
  }

}
