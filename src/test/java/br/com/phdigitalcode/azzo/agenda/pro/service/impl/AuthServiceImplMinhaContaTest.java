package br.com.phdigitalcode.azzo.agenda.pro.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.AtualizarMeuPerfilRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.LoginRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.TrocarMinhaSenhaRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ResetPasswordRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.GenericMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.PasswordResetToken;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoConfirmacaoDeEmail;
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

/** Tela Perfil (25/10/2026): a propria pessoa troca nome, telefone e senha. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplMinhaContaTest {

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
            licenseEventRepository, acessoDeProfissional, confirmacaoDeEmail);

    usuario = new Usuario();
    usuario.setId(UUID.randomUUID());
    usuario.setTenantId(UUID.randomUUID());
    usuario.setName("Bruna");
    usuario.setEmail("bruna@salao.test");
    usuario.setRole(PapelUsuario.PROFESSIONAL);
    usuario.setPasswordHash(BCrypt.withDefaults().hashToString(4, "Senha@123".toCharArray()));
    when(usuarioRepository.findByEmail("bruna@salao.test")).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
    when(refreshTokenService.issueForUser(any())).thenReturn("refresh");
    when(jwtService.gerarToken(any())).thenReturn("jwt");
  }

  private TrocarMinhaSenhaRequest troca(String atual, String nova) {
    TrocarMinhaSenhaRequest request = new TrocarMinhaSenhaRequest();
    request.senhaAtual = atual;
    request.novaSenha = nova;
    return request;
  }

  @Test
  void trocaASenhaConferindoAAtualEDerrubaAsSessoes() {
    GenericMessageResponse resposta = service.trocarMinhaSenha(usuario.getId(), troca("Senha@123", "Nova@4567"));

    assertThat(resposta.message).contains("Senha trocada");
    assertThat(BCrypt.verifyer().verify("Nova@4567".toCharArray(), usuario.getPasswordHash()).verified).isTrue();
    verify(passwordPolicyValidator).validateOrThrow("Nova@4567");
    verify(refreshTokenService).revokeAllForUser(usuario.getId());
  }

  @Test
  void senhaAtualErradaNaoTrocaNada() {
    String hashAntes = usuario.getPasswordHash();

    assertThatThrownBy(() -> service.trocarMinhaSenha(usuario.getId(), troca("Errada@1", "Nova@4567")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageStartingWith("A atual não confere");

    assertThat(usuario.getPasswordHash()).isEqualTo(hashAntes);
    verify(refreshTokenService, never()).revokeAllForUser(any());
  }

  @Test
  void novaIgualAAtualERecusada() {
    assertThatThrownBy(() -> service.trocarMinhaSenha(usuario.getId(), troca("Senha@123", "Senha@123")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("diferente da atual");
  }

  @Test
  void atualizaNomeETelefoneNormalizado() {
    AtualizarMeuPerfilRequest request = new AtualizarMeuPerfilRequest();
    request.name = "  Bruna Souza ";
    request.phone = "(21) 98765-4321";

    service.atualizarMeuPerfil(usuario.getId(), request);

    assertThat(usuario.getName()).isEqualTo("Bruna Souza");
    assertThat(usuario.getPhone()).isEqualTo("21987654321");
    verify(usuarioRepository).save(usuario);
  }
}
