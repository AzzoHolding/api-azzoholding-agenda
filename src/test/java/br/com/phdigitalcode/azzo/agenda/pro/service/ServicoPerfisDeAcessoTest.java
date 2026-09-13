package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.AtribuicaoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.NovoMembroRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.PerfilRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CredentialsEmailService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PerfilAcessoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacAuthorizationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AcessoPorPerfil;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.MenuRouteCache;
import br.com.phdigitalcode.azzo.agenda.pro.security.PasswordPolicyValidator;
import br.com.phdigitalcode.azzo.agenda.pro.security.PermissionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.ResolucaoDeAcesso.ItemCatalogo;
import br.com.phdigitalcode.azzo.agenda.pro.security.TokenRevocationService;

/** As regras da gestao de perfis — docs/ESPEC_PERFIS_DE_ACESSO.md (R1–R10, D1–D5). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoPerfisDeAcessoTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID DONO = UUID.randomUUID();
  private static final UUID PESSOA = UUID.randomUUID();
  private static final UUID PERFIL = UUID.randomUUID();
  private static final UUID AGENDA = UUID.randomUUID();
  private static final UUID ESTOQUE = UUID.randomUUID();
  private static final UUID LICENCA = UUID.randomUUID();

  @Mock private PerfilAcessoRepository repositorio;
  @Mock private AcessoPorPerfil acessoPorPerfil;
  @Mock private MenuRouteCache menuRouteCache;
  @Mock private RbacAuthorizationRepository rbacAuthorizationRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private PasswordPolicyValidator passwordPolicyValidator;
  @Mock private CredentialsEmailService credentialsEmailService;
  @Mock private TokenRevocationService tokenRevocationService;
  @Mock private PermissionService permissionService;
  @Mock private AuditService auditService;
  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;

  private ServicoPerfisDeAcesso servico;

  @BeforeEach
  void setUp() {
    servico =
        new ServicoPerfisDeAcesso(
            repositorio,
            acessoPorPerfil,
            menuRouteCache,
            rbacAuthorizationRepository,
            usuarioRepository,
            passwordPolicyValidator,
            credentialsEmailService,
            tokenRevocationService,
            permissionService,
            auditService,
            contextoTenant,
            authenticatedUser);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT);
    when(authenticatedUser.idOuNulo()).thenReturn(DONO);
    when(passwordPolicyValidator.isValid(anyString())).thenReturn(true);
    // O dono tem agenda, estoque e licenca; a licenca e exclusiva dele.
    when(repositorio.catalogoAtivo())
        .thenReturn(
            List.of(
                new ItemCatalogo(AGENDA, "/agenda", "Agenda", null, 1, null, true, false, true),
                new ItemCatalogo(ESTOQUE, "/estoque", "Estoque", null, 2, null, true, false, true),
                new ItemCatalogo(LICENCA, "/financeiro/licenca", "Licenca", null, 3, null, true, true, true)));
    when(acessoPorPerfil.tetoDoDono(TENANT)).thenReturn(Set.of("/agenda", "/financeiro/licenca"));
    // Qualquer id: criar e duplicar releem o perfil pelo id que eles mesmos geraram.
    when(repositorio.buscarPerfil(eq(TENANT), any()))
        .thenAnswer(
            invocacao ->
                Optional.of(new Object[] {invocacao.getArgument(1), "Recepcao", null, false, Instant.now()}));
    when(repositorio.listarEquipe(TENANT))
        .thenReturn(
            List.<Object[]>of(new Object[] {PESSOA, "Bia", "bia@salao.com", null, "PROFESSIONAL", null, null, null}));
  }

  private static PerfilRequest perfil(String nome, UUID... itens) {
    PerfilRequest request = new PerfilRequest();
    request.nome = nome;
    request.itens = java.util.Arrays.stream(itens).map(UUID::toString).toList();
    return request;
  }

  private static int status(Throwable erro) {
    return ((ApiClientErrorException) erro).getStatus();
  }

  // ─── Perfis ──────────────────────────────────────────────────────────────

  @Test
  void criarPerfilGravaAuditaELimpaOCache() {
    servico.criarPerfil(perfil("  Recepcao  ", AGENDA));

    ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
    verify(repositorio).inserirPerfil(id.capture(), eq(TENANT), eq("Recepcao"), any(), eq(false), eq(DONO));
    verify(repositorio).substituirItens(id.getValue(), List.of(AGENDA));
    verify(repositorio).registrarAuditoria(eq(TENANT), eq(DONO), eq("PERFIL_CRIADO"), eq("null"), anyString());
    verify(permissionService).limparCachePermissoesUsuario();
  }

  /** R2: o dono so distribui o que ele mesmo tem. */
  @Test
  void telaForaDoTetoDoDonoE400() {
    assertThatThrownBy(() -> servico.criarPerfil(perfil("Estoquista", ESTOQUE)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("nao disponivel")
        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    verify(repositorio, never()).inserirPerfil(any(), any(), any(), any(), eq(false), any());
  }

  /** R4: exclusiva do dono nao entra em perfil, mesmo estando no teto. */
  @Test
  void exclusivaDoDonoE400() {
    assertThatThrownBy(() -> servico.criarPerfil(perfil("Gerente", LICENCA)))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
  }

  @Test
  void perfilSemTelaE400() {
    assertThatThrownBy(() -> servico.criarPerfil(perfil("Vazio")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("ao menos uma tela");
  }

  /** R7: nome unico no salao. */
  @Test
  void nomeRepetidoE409() {
    when(repositorio.existeNome(TENANT, "Recepcao", null)).thenReturn(true);

    assertThatThrownBy(() -> servico.criarPerfil(perfil("Recepcao", AGENDA)))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(409));
  }

  @Test
  void acessoCompletoNaoEEditavel() {
    when(repositorio.buscarPerfil(TENANT, PERFIL))
        .thenReturn(Optional.of(new Object[] {PERFIL, "Acesso completo", null, true, Instant.now()}));

    assertThatThrownBy(() -> servico.atualizarPerfil(PERFIL, perfil("Outro", AGENDA)))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
  }

  /** R6: perfil com gente nao sai. */
  @Test
  void excluirPerfilComMembrosE409() {
    when(repositorio.contarMembros(PERFIL)).thenReturn(2);

    assertThatThrownBy(() -> servico.excluirPerfil(PERFIL))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("2 pessoas")
        .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    verify(repositorio, never()).excluirPerfil(any());
  }

  @Test
  void perfilDeOutroSalaoE404() {
    UUID alheio = UUID.randomUUID();
    when(repositorio.buscarPerfil(TENANT, alheio)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servico.obterPerfil(alheio))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(404));
  }

  // ─── Equipe ──────────────────────────────────────────────────────────────

  /** D1: varios perfis por pessoa, gravados como vieram. */
  @Test
  void atribuirVariosPerfis() {
    UUID outro = UUID.randomUUID();
    when(repositorio.papelDoUsuario(TENANT, PESSOA)).thenReturn(Optional.of("PROFESSIONAL"));
    when(repositorio.contarPerfisDoTenant(eq(TENANT), any())).thenReturn(2);
    AtribuicaoRequest request = new AtribuicaoRequest();
    request.perfis = List.of(PERFIL.toString(), outro.toString());

    servico.atribuirPerfis(PESSOA, request);

    verify(repositorio).substituirPerfisDoUsuario(TENANT, PESSOA, List.of(PERFIL, outro), DONO);
    verify(permissionService).limparCachePermissoesUsuario();
  }

  /** D5: seguir sem escolher = Acesso completo, criado na primeira vez. */
  @Test
  void atribuirSemPerfilDaOAcessoCompleto() {
    when(repositorio.papelDoUsuario(TENANT, PESSOA)).thenReturn(Optional.of("PROFESSIONAL"));
    when(repositorio.perfilDeAcessoTotal(TENANT)).thenReturn(Optional.empty());

    servico.atribuirPerfis(PESSOA, new AtribuicaoRequest());

    ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
    verify(repositorio).inserirPerfil(id.capture(), eq(TENANT), eq("Acesso completo"), any(), eq(true), eq(DONO));
    verify(repositorio).substituirPerfisDoUsuario(TENANT, PESSOA, List.of(id.getValue()), DONO);
  }

  /** R8: perfil de outro salao responde como inexistente. */
  @Test
  void perfilDeOutroSalaoNaAtribuicaoE404() {
    when(repositorio.papelDoUsuario(TENANT, PESSOA)).thenReturn(Optional.of("PROFESSIONAL"));
    when(repositorio.contarPerfisDoTenant(eq(TENANT), any())).thenReturn(0);
    AtribuicaoRequest request = new AtribuicaoRequest();
    request.perfis = List.of(UUID.randomUUID().toString());

    assertThatThrownBy(() -> servico.atribuirPerfis(PESSOA, request))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(404));
  }

  /** O dono nao e equipe: perfis sao para quem trabalha com ele. */
  @Test
  void donoNaoRecebePerfil() {
    when(repositorio.papelDoUsuario(TENANT, DONO)).thenReturn(Optional.of("OWNER"));

    assertThatThrownBy(() -> servico.atribuirPerfis(DONO, new AtribuicaoRequest()))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(404));
  }

  @Test
  void quemSaiuDaEquipeNaoRecebePerfil() {
    when(repositorio.papelDoUsuario(TENANT, PESSOA)).thenReturn(Optional.of("STAFF"));
    when(repositorio.estaDesligado(PESSOA)).thenReturn(true);

    assertThatThrownBy(() -> servico.atribuirPerfis(PESSOA, new AtribuicaoRequest()))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(409));
  }

  /** D4: membro sem agenda e STAFF, e ganha o Acesso completo se ninguem escolheu perfil. */
  @Test
  void cadastrarMembroSemAgenda() {
    when(usuarioRepository.findByEmail("carla@salao.com")).thenReturn(Optional.empty());
    when(repositorio.perfilDeAcessoTotal(TENANT)).thenReturn(Optional.of(PERFIL));
    when(usuarioRepository.saveAndFlush(any()))
        .thenAnswer(
            invocacao -> {
              Usuario u = invocacao.getArgument(0);
              u.setId(PESSOA);
              return u;
            });
    NovoMembroRequest request = new NovoMembroRequest();
    request.nome = "Carla";
    request.email = "  Carla@Salao.com ";

    servico.cadastrarMembro(request);

    ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
    verify(usuarioRepository).saveAndFlush(usuario.capture());
    assertThat(usuario.getValue().getRole().name()).isEqualTo("STAFF");
    assertThat(usuario.getValue().getEmail()).isEqualTo("carla@salao.com");
    verify(repositorio).substituirPerfisDoUsuario(TENANT, PESSOA, List.of(PERFIL), DONO);
  }

  @Test
  void emailJaUsadoE409() {
    when(usuarioRepository.findByEmail("carla@salao.com")).thenReturn(Optional.of(new Usuario()));
    NovoMembroRequest request = new NovoMembroRequest();
    request.nome = "Carla";
    request.email = "carla@salao.com";

    assertThatThrownBy(() -> servico.cadastrarMembro(request))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(409));
  }

  /** Sair da equipe bloqueia o acesso sem apagar o usuario. */
  @Test
  void desligarBloqueiaORevogaEPreservaOUsuario() {
    when(repositorio.papelDoUsuario(TENANT, PESSOA)).thenReturn(Optional.of("STAFF"));

    servico.desligar(PESSOA);

    verify(repositorio).substituirPerfisDoUsuario(TENANT, PESSOA, List.of(), DONO);
    verify(repositorio).bloquearCredenciais(eq(TENANT), eq(PESSOA), anyString());
    verify(repositorio).registrarDesligamento(TENANT, PESSOA, DONO);
    verify(tokenRevocationService).invalidateCache(PESSOA);
  }

  @Test
  void editarProfissionalPorAquiE400() {
    when(repositorio.papelDoUsuario(TENANT, PESSOA)).thenReturn(Optional.of("PROFESSIONAL"));
    var request = new br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.EdicaoDeMembroRequest();
    request.nome = "Bia";

    assertThatThrownBy(() -> servico.editarMembro(PESSOA, request))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
  }

  // ─── Auditoria ───────────────────────────────────────────────────────────

  @Test
  void jsonDaAuditoriaEscapaTexto() {
    assertThat(ServicoPerfisDeAcesso.json(Map.of("nome", "Sala \"VIP\"\n")))
        .isEqualTo("{\"nome\":\"Sala \\\"VIP\\\"\\n\"}");
    assertThat(ServicoPerfisDeAcesso.json(null)).isEqualTo("null");
  }
}
