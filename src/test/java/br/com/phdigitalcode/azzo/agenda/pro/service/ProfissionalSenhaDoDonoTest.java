package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ProfissionalRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CredentialsEmailService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PlanLimitsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacAuthorizationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacRoleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacUserRoleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SpecialtyRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtPrincipal;
import br.com.phdigitalcode.azzo.agenda.pro.security.PasswordPolicyValidator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SEG-005: a senha de OWNER/ADMIN so e trocada por ele mesmo pelo cadastro de profissional.
 *
 * <p>Quem tem {@code professional:write} (liberavel a STAFF) nao pode definir nem redefinir a
 * senha do dono: era assim que se assumia a conta dele.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfissionalSenhaDoDonoTest {

  @Mock private ProfissionalRepository profissionalRepository;
  @Mock private ProfissionalWorkingHourRepository profissionalWorkingHourRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private SpecialtyRepository specialtyRepository;
  @Mock private ServicoRepository servicoRepository;
  @Mock private RbacRoleRepository rbacRoleRepository;
  @Mock private RbacUserRoleRepository rbacUserRoleRepository;
  @Mock private RbacAuthorizationRepository rbacAuthorizationRepository;
  @Mock private PlanLimitsRepository planLimitsRepository;
  @Mock private CredentialsEmailService credentialsEmailService;
  @Mock private AfterCommitExecutor afterCommitExecutor;
  @Mock private ContextoTenant contextoTenant;
  @Mock private AuditService auditService;
  @Mock private PasswordPolicyValidator passwordPolicyValidator;
  @Mock private AgendamentoRepository agendamentoRepository;

  @InjectMocks private ProfissionalService service;

  private static final UUID TENANT = UUID.randomUUID();
  private static final String HASH_ORIGINAL = "hash-original-nao-mexer";

  private final UUID donoId = UUID.randomUUID();
  private final UUID atendenteId = UUID.randomUUID();
  private final UUID comumId = UUID.randomUUID();

  @BeforeEach
  void preparar() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT);
    when(profissionalRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    when(usuarioRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    // ensureProfessionalAccess cria o papel PROFESSIONAL quando nao existe: o mock precisa devolver.
    when(rbacRoleRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
  }

  @AfterEach
  void limpar() {
    SecurityContextHolder.clearContext();
  }

  private void logadoComo(UUID usuarioId) {
    JwtPrincipal principal = new JwtPrincipal(usuarioId, TENANT, "x@x.com", "Fulano", 0L);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  private Usuario usuario(UUID id, PapelUsuario papel) {
    Usuario u = new Usuario();
    u.setId(id);
    u.setTenantId(TENANT);
    u.setEmail(id + "@salao.com");
    u.setName("Usuario");
    u.setRole(papel);
    u.setPasswordHash(HASH_ORIGINAL);
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
    return u;
  }

  private Profissional profissionalLigadoA(UUID usuarioId) {
    Profissional p = new Profissional();
    p.setId(UUID.randomUUID());
    p.setTenantId(TENANT);
    p.setName("Dona");
    p.setActive(true);
    p.setUserId(usuarioId);
    when(profissionalRepository.findByIdAndTenantId(p.getId(), TENANT)).thenReturn(Optional.of(p));
    return p;
  }

  private ProfissionalRequest edicaoComSenha(String senha) {
    ProfissionalRequest req = new ProfissionalRequest();
    req.name = "Dona";
    req.isActive = true;
    req.accessPassword = senha;
    return req;
  }

  @Test
  @DisplayName("quem edita o profissional ligado ao dono nao define a senha do dono")
  void atendenteNaoDefineASenhaDoDonoPelaEdicao() {
    Usuario dono = usuario(donoId, PapelUsuario.OWNER);
    Profissional p = profissionalLigadoA(donoId);
    logadoComo(atendenteId);

    assertThatThrownBy(() -> service.atualizar(p.getId(), edicaoComSenha("NovaSenha@123")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("A senha do dono da conta so pode ser trocada por ele mesmo.");

    assertThat(dono.getPasswordHash()).isEqualTo(HASH_ORIGINAL);
    verify(usuarioRepository, never()).save(any());
  }

  /** O cenario exato do achado: ligar o userId do dono a um profissional sem login e dar senha. */
  @Test
  @DisplayName("ligar o login do dono a um profissional e dar senha tambem e recusado")
  void atendenteNaoLigaOLoginDoDonoEDaSenha() {
    Usuario dono = usuario(donoId, PapelUsuario.OWNER);
    Profissional p = profissionalLigadoA(null);
    logadoComo(atendenteId);
    ProfissionalRequest req = edicaoComSenha("NovaSenha@123");
    req.userId = donoId.toString();

    assertThatThrownBy(() -> service.atualizar(p.getId(), req))
        .isInstanceOf(ApiClientErrorException.class);

    assertThat(dono.getPasswordHash()).isEqualTo(HASH_ORIGINAL);
  }

  @Test
  @DisplayName("ADMIN e protegido igual ao OWNER")
  void adminTambemEProtegido() {
    Usuario admin = usuario(donoId, PapelUsuario.ADMIN);
    Profissional p = profissionalLigadoA(donoId);
    logadoComo(atendenteId);

    assertThatThrownBy(() -> service.atualizar(p.getId(), edicaoComSenha("NovaSenha@123")))
        .isInstanceOf(ApiClientErrorException.class);

    assertThat(admin.getPasswordHash()).isEqualTo(HASH_ORIGINAL);
  }

  @Test
  @DisplayName("o proprio dono ainda troca a propria senha")
  void donoTrocaAPropriaSenha() {
    Usuario dono = usuario(donoId, PapelUsuario.OWNER);
    Profissional p = profissionalLigadoA(donoId);
    logadoComo(donoId);

    service.atualizar(p.getId(), edicaoComSenha("NovaSenha@123"));

    assertThat(dono.getPasswordHash()).isNotEqualTo(HASH_ORIGINAL);
    verify(usuarioRepository).save(dono);
  }

  @Test
  @DisplayName("editar o profissional do dono sem senha continua funcionando para qualquer um")
  void editarSemSenhaNaoMudaNada() {
    Usuario dono = usuario(donoId, PapelUsuario.OWNER);
    Profissional p = profissionalLigadoA(donoId);
    logadoComo(atendenteId);

    service.atualizar(p.getId(), edicaoComSenha(null));

    assertThat(dono.getPasswordHash()).isEqualTo(HASH_ORIGINAL);
  }

  @Test
  @DisplayName("o login de um profissional comum continua podendo ser definido por quem cadastra")
  void senhaDeProfissionalComumContinuaPermitida() {
    Usuario comum = usuario(comumId, PapelUsuario.PROFESSIONAL);
    Profissional p = profissionalLigadoA(comumId);
    logadoComo(atendenteId);

    service.atualizar(p.getId(), edicaoComSenha("NovaSenha@123"));

    assertThat(comum.getPasswordHash()).isNotEqualTo(HASH_ORIGINAL);
  }

  // ---- reset-password --------------------------------------------------------------------------

  @Test
  @DisplayName("reset de senha do profissional ligado ao dono e recusado para outra pessoa")
  void resetDaSenhaDoDonoPorOutraPessoaERecusado() {
    Usuario dono = usuario(donoId, PapelUsuario.OWNER);
    Profissional p = profissionalLigadoA(donoId);
    logadoComo(atendenteId);

    assertThatThrownBy(() -> service.resetarSenha(p.getId()))
        .isInstanceOf(ApiClientErrorException.class);

    assertThat(dono.getPasswordHash()).isEqualTo(HASH_ORIGINAL);
    verify(usuarioRepository, never()).save(any());
    verify(afterCommitExecutor, never()).run(any());
  }

  @Test
  @DisplayName("o dono ainda pede o reset da propria senha")
  void donoPodeResetarAPropriaSenha() {
    Usuario dono = usuario(donoId, PapelUsuario.OWNER);
    Profissional p = profissionalLigadoA(donoId);
    logadoComo(donoId);

    service.resetarSenha(p.getId());

    assertThat(dono.getPasswordHash()).isNotEqualTo(HASH_ORIGINAL);
  }

  @Test
  @DisplayName("reset da senha de um profissional comum continua permitido")
  void resetDeProfissionalComumContinuaPermitido() {
    Usuario comum = usuario(comumId, PapelUsuario.PROFESSIONAL);
    Profissional p = profissionalLigadoA(comumId);
    logadoComo(atendenteId);

    service.resetarSenha(p.getId());

    assertThat(comum.getPasswordHash()).isNotEqualTo(HASH_ORIGINAL);
  }
}
