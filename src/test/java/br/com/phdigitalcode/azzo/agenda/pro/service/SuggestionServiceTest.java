package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SuggestionDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FeedbackSuggestion;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FeedbackSuggestionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Cobre {@code modules/suggestions/application/SuggestionService.java} — sem testes no original. */
class SuggestionServiceTest {

  private FeedbackSuggestionRepository feedbackSuggestionRepository;
  private UsuarioRepository usuarioRepository;
  private ContextoTenant contextoTenant;
  private AuthenticatedUser authenticatedUser;
  private SuggestionService service;

  @BeforeEach
  void setUp() {
    feedbackSuggestionRepository = mock(FeedbackSuggestionRepository.class);
    usuarioRepository = mock(UsuarioRepository.class);
    contextoTenant = mock(ContextoTenant.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    service =
        new SuggestionService(feedbackSuggestionRepository, usuarioRepository, contextoTenant, authenticatedUser);
  }

  private Usuario usuario(UUID userId) {
    Usuario usuario = new Usuario();
    usuario.setId(userId);
    usuario.setName("Maria Dono");
    usuario.setRole(PapelUsuario.OWNER);
    return usuario;
  }

  // ─── create ────────────────────────────────────────────────────────────

  @Test
  void createPersisteComDadosDoUsuarioAutenticadoEDevolveRespostaCompleta() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId)));
    when(feedbackSuggestionRepository.saveAndFlush(any(FeedbackSuggestion.class)))
        .thenAnswer(
            inv -> {
              FeedbackSuggestion entity = inv.getArgument(0);
              entity.setId(UUID.randomUUID());
              entity.setCreatedAt(Instant.now());
              entity.setUpdatedAt(Instant.now());
              return entity;
            });

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "Titulo";
    request.message = "Descricao da sugestao";
    request.category = "bug";
    request.sourcePage = "/agenda";

    SuggestionDtos.SuggestionItemResponse response = service.create(request);

    assertThat(response.id).isNotBlank();
    assertThat(response.tenantId).isEqualTo(tenantId.toString());
    assertThat(response.userId).isEqualTo(userId.toString());
    assertThat(response.userName).isEqualTo("Maria Dono");
    assertThat(response.userRole).isEqualTo("OWNER");
    assertThat(response.category).isEqualTo("BUG");
    assertThat(response.title).isEqualTo("Titulo");
    assertThat(response.message).isEqualTo("Descricao da sugestao");
    assertThat(response.status).isEqualTo("OPEN");
    assertThat(response.sourcePage).isEqualTo("/agenda");
    assertThat(response.createdAt).isNotBlank();
  }

  @Test
  void createFalhaComUnauthorizedQuandoUsuarioNaoAutenticado() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(UUID.randomUUID());
    when(authenticatedUser.idOuNulo()).thenReturn(null);

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "Titulo";
    request.message = "Msg";

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(401);
  }

  @Test
  void createFalhaComUnauthorizedQuandoUsuarioNaoEncontrado() {
    UUID userId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(UUID.randomUUID());
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.empty());

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "Titulo";
    request.message = "Msg";

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(401);
  }

  @Test
  void createExigeTituloNaoBranco() {
    UUID userId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(UUID.randomUUID());
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId)));

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "   ";
    request.message = "Msg";

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Titulo da sugestao obrigatorio.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  @Test
  void createExigeMensagemNaoBranca() {
    UUID userId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(UUID.randomUUID());
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId)));

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "Titulo";
    request.message = null;

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Descricao da sugestao obrigatoria.");
  }

  @Test
  void createRejeitaCategoriaInvalida() {
    UUID userId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(UUID.randomUUID());
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId)));

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "Titulo";
    request.message = "Msg";
    request.category = "XPTO";

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Categoria invalida. Use BUG, MELHORIA, FUNCIONALIDADE, USABILIDADE ou OUTRO.");
  }

  @Test
  void createUsaMelhoriaComoCategoriaPadrao() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId)));
    when(feedbackSuggestionRepository.saveAndFlush(any(FeedbackSuggestion.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "Titulo";
    request.message = "Msg";
    request.category = null;

    SuggestionDtos.SuggestionItemResponse response = service.create(request);

    assertThat(response.category).isEqualTo("MELHORIA");
  }

  @Test
  void createNormalizaEspacosInternosDoTituloEMensagem() {
    UUID userId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(UUID.randomUUID());
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId)));
    when(feedbackSuggestionRepository.saveAndFlush(any(FeedbackSuggestion.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "  Titulo   com   espacos  ";
    request.message = "Msg";

    SuggestionDtos.SuggestionItemResponse response = service.create(request);

    assertThat(response.title).isEqualTo("Titulo com espacos");
  }

  @Test
  void createUsaUnknownQuandoUsuarioSemRole() {
    UUID userId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(UUID.randomUUID());
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    Usuario semRole = usuario(userId);
    semRole.setRole(null);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(semRole));
    when(feedbackSuggestionRepository.saveAndFlush(any(FeedbackSuggestion.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "Titulo";
    request.message = "Msg";

    SuggestionDtos.SuggestionItemResponse response = service.create(request);

    assertThat(response.userRole).isEqualTo("UNKNOWN");
  }

  // ─── listByTenant ──────────────────────────────────────────────────────

  @Test
  void listByTenantUsaLimitePadraoDeCinquentaQuandoAusente() {
    UUID tenantId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(feedbackSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId), eq(Limit.of(50))))
        .thenReturn(List.of());

    SuggestionDtos.SuggestionListResponse response = service.listByTenant(null);

    assertThat(response.limit).isEqualTo(50);
    assertThat(response.items).isEmpty();
  }

  @Test
  void listByTenantLimitaEmDuzentosMesmoQuePedirMais() {
    UUID tenantId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(feedbackSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId), eq(Limit.of(200))))
        .thenReturn(List.of());

    SuggestionDtos.SuggestionListResponse response = service.listByTenant(500);

    assertThat(response.limit).isEqualTo(200);
  }

  @Test
  void listByTenantUsaPadraoQuandoLimiteMenorQueUm() {
    UUID tenantId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(feedbackSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId), eq(Limit.of(50))))
        .thenReturn(List.of());

    SuggestionDtos.SuggestionListResponse response = service.listByTenant(0);

    assertThat(response.limit).isEqualTo(50);
  }

  @Test
  void listByTenantMapeiaItensNaOrdemDoRepositorio() {
    UUID tenantId = UUID.randomUUID();
    FeedbackSuggestion suggestion = new FeedbackSuggestion();
    suggestion.setId(UUID.randomUUID());
    suggestion.setTenantId(tenantId);
    suggestion.setTitle("Titulo A");
    suggestion.setStatus("OPEN");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(feedbackSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId), any(Limit.class)))
        .thenReturn(List.of(suggestion));

    SuggestionDtos.SuggestionListResponse response = service.listByTenant(10);

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).title).isEqualTo("Titulo A");
  }

  @Test
  void createPersisteComSaveAndFlushParaExporIdImediatamente() {
    UUID userId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(UUID.randomUUID());
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId)));
    when(feedbackSuggestionRepository.saveAndFlush(any(FeedbackSuggestion.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    request.title = "Titulo";
    request.message = "Msg";

    service.create(request);

    verify(feedbackSuggestionRepository).saveAndFlush(any(FeedbackSuggestion.class));
  }
}
