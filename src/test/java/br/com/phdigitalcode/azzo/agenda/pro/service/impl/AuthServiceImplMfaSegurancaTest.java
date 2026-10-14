package br.com.phdigitalcode.azzo.agenda.pro.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import at.favre.lib.crypto.bcrypt.BCrypt;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.LoginRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
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
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtService;
import br.com.phdigitalcode.azzo.agenda.pro.security.PasswordPolicyValidator;
import br.com.phdigitalcode.azzo.agenda.pro.security.RefreshTokenService;
import br.com.phdigitalcode.azzo.agenda.pro.security.TotpService;
import br.com.phdigitalcode.azzo.agenda.pro.security.AcessoDeProfissional;
import br.com.phdigitalcode.azzo.agenda.pro.integration.EmailJobService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoConfirmacaoDeEmail;
import br.com.phdigitalcode.azzo.agenda.pro.service.TermsService;

/** Pentest 14/10/2026: SEG-007 (MFA sem limite e codigo reutilizavel) e SEG-008 (429 so para e-mail real). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplMfaSegurancaTest {

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
  @Mock private ServicoConfirmacaoDeEmail confirmacaoDeEmail;

  private AuthServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AuthServiceImpl(
        tenantRepository, usuarioRepository, rbacRoleRepository, rbacUserRoleRepository,
        rbacAuthorizationRepository, passwordResetTokenRepository, jwtService,
        refreshTokenService, encryptionService, totpService, auditService,
        passwordPolicyValidator, emailJobService, usuarioMapper, termsService,
        productRepository, checkoutIntentRepository, checkoutOrderRepository,
        licenseEventRepository, acessoDeProfissional, confirmacaoDeEmail);

    Usuario usuario = new Usuario();
    usuario.setId(UUID.randomUUID());
    usuario.setTenantId(UUID.randomUUID());
    usuario.setName("Dona");
    usuario.setEmail("dona@salao.test");
    usuario.setRole(PapelUsuario.OWNER);
    usuario.setPasswordHash(BCrypt.withDefaults().hashToString(4, "Senha@123".toCharArray()));
    usuario.setMfaEnabled(true);
    usuario.setMfaSecretEnc("cifrado");
    when(usuarioRepository.findByEmail("dona@salao.test")).thenReturn(Optional.of(usuario));
    when(encryptionService.decrypt("cifrado")).thenReturn("SEGREDOBASE32");
    when(refreshTokenService.issueForUser(any())).thenReturn("refresh");
    when(jwtService.gerarToken(any())).thenReturn("jwt");
  }

  private static LoginRequest login(String email, String senha, String mfa) {
    LoginRequest r = new LoginRequest();
    r.email = email;
    r.password = senha;
    r.mfaCode = mfa;
    return r;
  }

  @Test
  void codigosMfaErradosContamEBloqueiam() {
    when(totpService.passoValido(eq("SEGREDOBASE32"), anyString(), any())).thenReturn(OptionalLong.empty());
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> service.login(login("dona@salao.test", "Senha@123", "000000")))
          .hasMessageContaining("MFA");
    }
    // Antes: a senha certa zerava o contador e o MFA errado nao contava — nunca chegava ao 429.
    assertThatThrownBy(() -> service.login(login("dona@salao.test", "Senha@123", "000000")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Muitas tentativas");
  }

  @Test
  void mesmoCodigoMfaNaoValeDuasVezes() {
    when(totpService.passoValido(eq("SEGREDOBASE32"), eq("123456"), any())).thenReturn(OptionalLong.of(1000L));

    assertThat(service.login(login("dona@salao.test", "Senha@123", "123456"))).isNotNull();
    assertThatThrownBy(() -> service.login(login("dona@salao.test", "Senha@123", "123456")))
        .hasMessageContaining("MFA");
  }

  @Test
  void emailInexistenteTambemChegaAo429() {
    when(usuarioRepository.findByEmail("ninguem@salao.test")).thenReturn(Optional.empty());
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> service.login(login("ninguem@salao.test", "x", null)))
          .hasMessageContaining("Credenciais invalidas");
    }
    // Antes: e-mail inexistente nunca recebia 429 — o bloqueio dizia quais e-mails tem conta.
    assertThatThrownBy(() -> service.login(login("ninguem@salao.test", "x", null)))
        .hasMessageContaining("Muitas tentativas");
  }
}
