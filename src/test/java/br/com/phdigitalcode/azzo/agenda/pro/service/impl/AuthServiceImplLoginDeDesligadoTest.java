package br.com.phdigitalcode.azzo.agenda.pro.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import at.favre.lib.crypto.bcrypt.BCrypt;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.LoginRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.EmailJobService;
import br.com.phdigitalcode.azzo.agenda.pro.mapper.UsuarioMapper;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutIntentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LicenseEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PasswordResetTokenRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacAuthorizationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacRoleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacUserRoleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AcessoDeProfissional;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtService;
import br.com.phdigitalcode.azzo.agenda.pro.security.PasswordPolicyValidator;
import br.com.phdigitalcode.azzo.agenda.pro.security.RefreshTokenService;
import br.com.phdigitalcode.azzo.agenda.pro.security.TotpService;
import br.com.phdigitalcode.azzo.agenda.pro.service.TermsService;

/**
 * Quem saiu da equipe nao entra mais (analise de 2026-09-16: o login nao conferia o profissional
 * desativado, e o codigo dizia que a verificacao "fica pendente").
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplLoginDeDesligadoTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private RbacRoleRepository rbacRoleRepository;
  @Mock private RbacUserRoleRepository rbacUserRoleRepository;
  @Mock private RbacAuthorizationRepository rbacAuthorizationRepository;
  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock private JwtService jwtService;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private EncryptionService encryptionService;
  @Mock private TotpService totpService;
  @Mock private AuditService auditService;
  @Mock private PasswordPolicyValidator passwordPolicyValidator;
  @Mock private EmailJobService emailJobService;
  @Mock private UsuarioMapper usuarioMapper;
  @Mock private TermsService termsService;
  @Mock private ProductRepository productRepository;
  @Mock private CheckoutIntentRepository checkoutIntentRepository;
  @Mock private CheckoutOrderRepository checkoutOrderRepository;
  @Mock private LicenseEventRepository licenseEventRepository;
  @Mock private AcessoDeProfissional acessoDeProfissional;

  private AuthServiceImpl service;
  private Usuario usuario;

  @BeforeEach
  void setUp() {
    service =
        new AuthServiceImpl(
            tenantRepository, usuarioRepository, rbacRoleRepository, rbacUserRoleRepository,
            rbacAuthorizationRepository, passwordResetTokenRepository, jwtService,
            refreshTokenService, encryptionService, totpService, auditService,
            passwordPolicyValidator, emailJobService, usuarioMapper, termsService,
            productRepository, checkoutIntentRepository, checkoutOrderRepository,
            licenseEventRepository, acessoDeProfissional);

    usuario = new Usuario();
    usuario.setId(UUID.randomUUID());
    usuario.setTenantId(UUID.randomUUID());
    usuario.setName("Bruna");
    usuario.setEmail("bruna@salao.test");
    usuario.setRole(PapelUsuario.PROFESSIONAL);
    usuario.setPasswordHash(BCrypt.withDefaults().hashToString(4, "Senha@123".toCharArray()));
    when(usuarioRepository.findByEmail("bruna@salao.test")).thenReturn(Optional.of(usuario));
    when(refreshTokenService.issueForUser(any())).thenReturn("refresh");
    when(jwtService.gerarToken(any())).thenReturn("jwt");
  }

  private LoginRequest login(String senha) {
    LoginRequest request = new LoginRequest();
    request.email = "bruna@salao.test";
    request.password = senha;
    return request;
  }

  @Test
  void profissionalDesativadoNaoEntraENaoGanhaSessao() {
    when(acessoDeProfissional.desativado(usuario)).thenReturn(true);

    assertThatThrownBy(() -> service.login(login("Senha@123")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("desativado");

    verify(refreshTokenService, never()).issueForUser(any());
    verify(jwtService, never()).gerarToken(any());
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordDenied(captor.capture());
    assertThat(captor.getValue().action).isEqualTo("AUTH_LOGIN");
  }

  /** Senha errada nao revela que a conta foi desligada: a checagem vem depois da senha. */
  @Test
  void senhaErradaNaoRevelaQueAContaFoiDesligada() {
    when(acessoDeProfissional.desativado(usuario)).thenReturn(true);

    assertThatThrownBy(() -> service.login(login("errada")))
        .hasMessageContaining("Credenciais invalidas");
    verify(acessoDeProfissional, never()).desativado(any());
  }

  @Test
  void profissionalAtivoContinuaEntrando() {
    when(acessoDeProfissional.desativado(usuario)).thenReturn(false);

    assertThat(service.login(login("Senha@123")).access_token).isEqualTo("jwt");
  }
}
