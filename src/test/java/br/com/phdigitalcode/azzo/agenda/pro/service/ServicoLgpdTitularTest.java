package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.LgpdRequestDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Espelha {@code modules/lgpd/application/ServicoLgpdTitular.java}. */
class ServicoLgpdTitularTest {

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  private ContextoTenant contextoTenant;
  private AuthenticatedUser authenticatedUser;
  private UsuarioRepository usuarioRepository;
  private LgpdDataSubjectRequestRepository requestRepository;
  private LgpdDataSubjectRequestEventRepository eventRepository;
  private AuditService auditService;
  private ServicoLgpdTitular service;

  @BeforeEach
  void setUp() {
    contextoTenant = mock(ContextoTenant.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    usuarioRepository = mock(UsuarioRepository.class);
    requestRepository = mock(LgpdDataSubjectRequestRepository.class);
    eventRepository = mock(LgpdDataSubjectRequestEventRepository.class);
    auditService = mock(AuditService.class);
    service = new ServicoLgpdTitular(
        contextoTenant, authenticatedUser, usuarioRepository, requestRepository, eventRepository, auditService);

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(new Usuario()));
    when(requestRepository.findByTenantAndProtocol(any(), any())).thenReturn(Optional.empty());
  }

  private LgpdRequestDtos.CreateRequest buildCreateRequest() {
    LgpdRequestDtos.CreateRequest request = new LgpdRequestDtos.CreateRequest();
    request.requestType = "acesso";
    request.requesterName = "Fulano de Tal";
    request.requesterEmail = "Fulano@Example.com";
    request.requesterDocument = "12345678900";
    request.description = "quero meus dados";
    return request;
  }

  @Test
  void criarPersisteSolicitacaoComProtocoloEStatusAberto() {
    LgpdRequestDtos.ItemResponse response = service.criar(buildCreateRequest());

    assertThat(response.status).isEqualTo("ABERTO");
    assertThat(response.requestType).isEqualTo("ACESSO");
    assertThat(response.requesterEmail).isEqualTo("fulano@example.com");
    assertThat(response.protocolCode).startsWith("LGPD-");
    assertThat(response.createdByUserId).isEqualTo(userId.toString());
    verify(requestRepository).save(any(LgpdDataSubjectRequest.class));
    verify(eventRepository).save(any());
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void criarValidaCamposObrigatorios() {
    LgpdRequestDtos.CreateRequest request = buildCreateRequest();
    request.requesterName = " ";
    assertThatThrownBy(() -> service.criar(request)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void criarNaoQuebraQuandoAuditoriaFalha() {
    when(auditService.recordSuccess(any())).thenThrow(new RuntimeException("falhou"));
    LgpdRequestDtos.ItemResponse response = service.criar(buildCreateRequest());
    assertThat(response).isNotNull();
  }

  @Test
  void criarUsaCreatedByUserIdNuloQuandoUsuarioNaoExisteMaisNoBanco() {
    when(usuarioRepository.findById(userId)).thenReturn(Optional.empty());
    LgpdRequestDtos.ItemResponse response = service.criar(buildCreateRequest());
    assertThat(response.createdByUserId).isNull();
  }

  @Test
  void criarUsaCreatedByUserIdNuloQuandoNaoHaTokenAutenticado() {
    when(authenticatedUser.idOuNulo()).thenReturn(null);
    LgpdRequestDtos.ItemResponse response = service.criar(buildCreateRequest());
    assertThat(response.createdByUserId).isNull();
  }

  @Test
  void listarDelegaAoRepositorioComFiltrosNormalizados() {
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setStatus("ABERTO");
    when(requestRepository.listByTenant(tenantId, "ABERTO", "ACESSO", 10)).thenReturn(List.of(entity));

    List<LgpdRequestDtos.ItemResponse> result = service.listar(" aberto ", "acesso", 10);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).status).isEqualTo("ABERTO");
  }

  @Test
  void listarAceitaStatusNuloSemNormalizar() {
    when(requestRepository.listByTenant(tenantId, null, null, null)).thenReturn(List.of());
    assertThat(service.listar(null, null, null)).isEmpty();
  }

  @Test
  void detalharRetornaRequestEEventos() {
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    when(requestRepository.findByTenantAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));
    when(eventRepository.listByRequestId(entity.getId())).thenReturn(List.of());

    LgpdRequestDtos.DetailResponse response = service.detalhar(entity.getId());

    assertThat(response.request.id).isEqualTo(entity.getId().toString());
    assertThat(response.events).isEmpty();
  }

  @Test
  void detalharLancaQuandoNaoEncontrada() {
    UUID id = UUID.randomUUID();
    when(requestRepository.findByTenantAndId(tenantId, id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.detalhar(id)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void detalharPorProtocoloNormalizaEDelega() {
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setId(UUID.randomUUID());
    when(requestRepository.findByTenantAndProtocol(tenantId, "LGPD-20260101-ABCDEF12")).thenReturn(Optional.of(entity));
    when(eventRepository.listByRequestId(entity.getId())).thenReturn(List.of());

    LgpdRequestDtos.DetailResponse response = service.detalharPorProtocolo(" lgpd-20260101-abcdef12 ");

    assertThat(response.request.id).isEqualTo(entity.getId().toString());
  }

  @Test
  void detalharPorProtocoloLancaQuandoNaoEncontrado() {
    when(requestRepository.findByTenantAndProtocol(any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.detalharPorProtocolo("XPTO")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resumirOperacaoAgregaContadoresEAlertas() {
    LgpdDataSubjectRequest alert = new LgpdDataSubjectRequest();
    alert.setId(UUID.randomUUID());
    alert.setAssignedToUserId(userId);
    alert.setCreatedAt(java.time.Instant.now().minusSeconds(40L * 24 * 3600));
    when(requestRepository.countByTenantAndStatus(eq(tenantId), any())).thenReturn(3L);
    when(requestRepository.countUnassignedActive(tenantId)).thenReturn(2L);
    when(requestRepository.countOverdueInitialResponse(eq(tenantId), any())).thenReturn(1L);
    when(requestRepository.countOverdueFinalResolution(eq(tenantId), any())).thenReturn(1L);
    when(requestRepository.listOperationalAlerts(eq(tenantId), any(), any())).thenReturn(List.of(alert));
    when(usuarioRepository.mapNamesByTenantAndIds(eq(tenantId), any())).thenReturn(Map.of(userId, "Fulano"));

    LgpdRequestDtos.SummaryResponse response = service.resumirOperacao(10);

    assertThat(response.totalOpen).isEqualTo(3L);
    assertThat(response.unassignedActive).isEqualTo(2L);
    assertThat(response.alerts).hasSize(1);
    assertThat(response.alerts.get(0).assignedToUserName).isEqualTo("Fulano");
    assertThat(response.alerts.get(0).overdueType).isEqualTo("FINAL_RESOLUTION");
  }

  @Test
  void atualizarStatusValidaTransicaoEPersiste() {
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setStatus("ABERTO");
    when(requestRepository.findByTenantAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

    LgpdRequestDtos.UpdateStatusRequest request = new LgpdRequestDtos.UpdateStatusRequest();
    request.status = "EM_VALIDACAO";
    request.note = "iniciando validacao";

    LgpdRequestDtos.ItemResponse response = service.atualizarStatus(entity.getId(), request);

    assertThat(response.status).isEqualTo("EM_VALIDACAO");
    verify(eventRepository).save(any());
    verify(auditService).recordSuccess(any());
  }

  @Test
  void atualizarStatusEncerraDefinePreenchendoClosedAt() {
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setStatus("RESPONDIDO");
    when(requestRepository.findByTenantAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

    LgpdRequestDtos.UpdateStatusRequest request = new LgpdRequestDtos.UpdateStatusRequest();
    request.status = "ENCERRADO";
    request.assignedToUserId = UUID.randomUUID().toString();

    LgpdRequestDtos.ItemResponse response = service.atualizarStatus(entity.getId(), request);

    assertThat(response.closedAt).isNotBlank();
    assertThat(response.assignedToUserId).isEqualTo(request.assignedToUserId);
  }

  @Test
  void atualizarStatusLancaParaTransicaoInvalida() {
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setStatus("ENCERRADO");
    when(requestRepository.findByTenantAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

    LgpdRequestDtos.UpdateStatusRequest request = new LgpdRequestDtos.UpdateStatusRequest();
    request.status = "ABERTO";

    assertThatThrownBy(() -> service.atualizarStatus(entity.getId(), request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void atualizarStatusPermiteReenviarOMesmoStatus() {
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setStatus("ABERTO");
    when(requestRepository.findByTenantAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

    LgpdRequestDtos.UpdateStatusRequest request = new LgpdRequestDtos.UpdateStatusRequest();
    request.status = "aberto";

    LgpdRequestDtos.ItemResponse response = service.atualizarStatus(entity.getId(), request);

    assertThat(response.status).isEqualTo("ABERTO");
  }

  @Test
  void atualizarStatusLancaParaStatusInvalido() {
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setStatus("ABERTO");
    when(requestRepository.findByTenantAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

    LgpdRequestDtos.UpdateStatusRequest request = new LgpdRequestDtos.UpdateStatusRequest();
    request.status = "QUALQUER_COISA";

    assertThatThrownBy(() -> service.atualizarStatus(entity.getId(), request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void atualizarStatusLancaParaAssignedToUserIdInvalido() {
    LgpdDataSubjectRequest entity = new LgpdDataSubjectRequest();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setStatus("ABERTO");
    when(requestRepository.findByTenantAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

    LgpdRequestDtos.UpdateStatusRequest request = new LgpdRequestDtos.UpdateStatusRequest();
    request.status = "EM_VALIDACAO";
    request.assignedToUserId = "nao-e-um-uuid";

    assertThatThrownBy(() -> service.atualizarStatus(entity.getId(), request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void atualizarStatusLancaQuandoRequestNaoEncontrada() {
    UUID id = UUID.randomUUID();
    when(requestRepository.findByTenantAndId(tenantId, id)).thenReturn(Optional.empty());

    LgpdRequestDtos.UpdateStatusRequest request = new LgpdRequestDtos.UpdateStatusRequest();
    request.status = "ABERTO";

    assertThatThrownBy(() -> service.atualizarStatus(id, request)).isInstanceOf(IllegalArgumentException.class);
  }
}
