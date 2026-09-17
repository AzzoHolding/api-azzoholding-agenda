package br.com.phdigitalcode.azzo.agenda.pro.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RegisterRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutIntent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LicenseEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacRole;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacUserRole;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsVersion;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
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
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtService;
import br.com.phdigitalcode.azzo.agenda.pro.security.PasswordPolicyValidator;
import br.com.phdigitalcode.azzo.agenda.pro.security.RefreshTokenService;
import br.com.phdigitalcode.azzo.agenda.pro.security.TotpService;
import br.com.phdigitalcode.azzo.agenda.pro.service.TermsService;

/**
 * O cadastro ({@code registrar}) completo: status do plano, trial, aceite dos termos e um trial
 * por documento. Ate 2026-09-11 o Spring tinha uma versao REDUZIDA que nao gravava o
 * {@code plan_status_id} (NOT NULL) e o cadastro falhava em banco novo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplRegistrarTest {

  private static final String VERSAO = "1.0";
  private static final String CPF = "529.982.247-25";

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

  private AuthServiceImpl service;
  private final UUID planStatusAtivo = UUID.randomUUID();
  private TermsVersion termosDeUso;
  private TermsVersion politicaDePrivacidade;
  private Product produtoDeTrial;

  @BeforeEach
  void setUp() {
    service = new AuthServiceImpl(
        tenantRepository, usuarioRepository, rbacRoleRepository, rbacUserRoleRepository,
        rbacAuthorizationRepository, passwordResetTokenRepository, jwtService, refreshTokenService,
        encryptionService, totpService, auditService, passwordPolicyValidator, emailJobService,
        usuarioMapper, termsService, productRepository, checkoutIntentRepository,
        checkoutOrderRepository, licenseEventRepository,
        org.mockito.Mockito.mock(br.com.phdigitalcode.azzo.agenda.pro.security.AcessoDeProfissional.class));

    termosDeUso = versao(AuditConstants.TermsDocumentType.TERMS_OF_USE);
    politicaDePrivacidade = versao(AuditConstants.TermsDocumentType.PRIVACY_POLICY);
    when(termsService.requireActiveVersion(AuditConstants.TermsDocumentType.TERMS_OF_USE, VERSAO))
        .thenReturn(termosDeUso);
    when(termsService.requireActiveVersion(AuditConstants.TermsDocumentType.PRIVACY_POLICY, VERSAO))
        .thenReturn(politicaDePrivacidade);

    when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    when(tenantRepository.existsByTrialDocumentHash(anyString())).thenReturn(false);
    when(tenantRepository.buscarPlanStatusIdPorCodigo("ACTIVE")).thenReturn(Optional.of(planStatusAtivo));
    // O @PrePersist nao roda com mock: o id e dado aqui, como o JPA faria.
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
      Tenant tenant = inv.getArgument(0);
      tenant.setId(UUID.randomUUID());
      return tenant;
    });
    when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
      Usuario usuario = inv.getArgument(0);
      usuario.setId(UUID.randomUUID());
      return usuario;
    });
    when(checkoutIntentRepository.save(any(CheckoutIntent.class))).thenAnswer(inv -> {
      CheckoutIntent intencao = inv.getArgument(0);
      intencao.setId(UUID.randomUUID());
      return intencao;
    });

    RbacRole owner = new RbacRole();
    owner.setId(UUID.randomUUID());
    owner.setName("OWNER");
    when(rbacRoleRepository.findByNameIgnoreCase("OWNER")).thenReturn(Optional.of(owner));
    when(rbacUserRoleRepository.findById(any())).thenReturn(Optional.of(new RbacUserRole()));

    produtoDeTrial = new Product();
    produtoDeTrial.setId(UUID.randomUUID());
    produtoDeTrial.setName("Plano Trial");
    produtoDeTrial.setCurrency("BRL");
    produtoDeTrial.setValidityDays(7);
    when(productRepository.findLatestActiveTrial()).thenReturn(Optional.of(produtoDeTrial));
  }

  /** O que faltava na versao reduzida — e o motivo de o cadastro falhar em banco novo. */
  @Test
  void cadastroGravaStatusDoPlanoDocumentoEHashDoTrial() throws Exception {
    service.registrar(request(CPF), "req-1", "203.0.113.5");

    ArgumentCaptor<Tenant> tenant = ArgumentCaptor.forClass(Tenant.class);
    verify(tenantRepository).save(tenant.capture());
    assertThat(tenant.getValue().getPlanStatusId()).isEqualTo(planStatusAtivo);
    assertThat(tenant.getValue().getDocument()).isEqualTo("52998224725");
    // O que se compara e o HASH do documento, nunca o documento.
    assertThat(tenant.getValue().getTrialDocumentHash()).isEqualTo(sha256("52998224725"));
  }

  /** O pedido CONFIRMADO e valido e o que o LicenseStatusService procura: sem ele, EXPIRED. */
  @Test
  void cadastroAtivaOTrialComPedidoConfirmadoEEvento() {
    Instant antes = Instant.now();
    service.registrar(request(CPF), "req-1", "203.0.113.5");

    ArgumentCaptor<CheckoutOrder> pedido = ArgumentCaptor.forClass(CheckoutOrder.class);
    verify(checkoutOrderRepository).save(pedido.capture());
    assertThat(pedido.getValue())
        .extracting("status", "total", "productId")
        .containsExactly(StatusCheckout.CONFIRMED, 0L, produtoDeTrial.getId());
    Instant validade = (Instant) org.assertj.core.util.introspection.PropertyOrFieldSupport.EXTRACTION
        .getValueOf("validUntil", pedido.getValue());
    assertThat(Duration.between(antes, validade)).isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7).plusMinutes(1));

    ArgumentCaptor<LicenseEvent> evento = ArgumentCaptor.forClass(LicenseEvent.class);
    verify(licenseEventRepository).save(evento.capture());
    assertThat(evento.getValue()).extracting("eventType").isEqualTo("TRIAL_ACTIVATED");
  }

  /** O aceite gravado e a prova do consentimento — os dois documentos, com o mesmo requestId. */
  @Test
  void cadastroGravaOAceiteDosDoisDocumentos() {
    service.registrar(request(CPF), "req-1", "203.0.113.5");

    verify(termsService).accept(any(), any(), eq(termosDeUso.getId()), eq("req-1"), eq("203.0.113.5"));
    verify(termsService).accept(any(), any(), eq(politicaDePrivacidade.getId()), eq("req-1"), eq("203.0.113.5"));
  }

  @Test
  void recusaOSegundoTrialDoMesmoDocumentoSemCriarNada() {
    when(tenantRepository.existsByTrialDocumentHash(sha256("52998224725"))).thenReturn(true);

    assertThatThrownBy(() -> service.registrar(request(CPF), "req-1", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("plano gratuito");
    verify(tenantRepository, never()).save(any());
    verify(checkoutOrderRepository, never()).save(any());
  }

  @Test
  void recusaVersaoDeTermoInativaAntesDeCriarQualquerCoisa() {
    when(termsService.requireActiveVersion(AuditConstants.TermsDocumentType.TERMS_OF_USE, VERSAO))
        .thenThrow(new IllegalArgumentException("Versao de termo desativada para aceite"));

    assertThatThrownBy(() -> service.registrar(request(CPF), "req-1", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("desativada");
    verify(tenantRepository, never()).save(any());
  }

  /** Sem produto de trial o salao nasceria bloqueado: melhor falhar (e a transacao desfaz tudo). */
  @Test
  void semProdutoDeTrialConfiguradoOCadastroFalha() {
    when(productRepository.findLatestActiveTrial()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.registrar(request(CPF), "req-1", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("trial");
  }

  @Test
  void validadeEmMesesViraDiasQuandoOProdutoNaoDizOsDias() {
    produtoDeTrial.setValidityDays(null);
    produtoDeTrial.setValidityMonths(1);
    Instant antes = Instant.now();

    service.registrar(request(CPF), "req-1", null);

    ArgumentCaptor<CheckoutOrder> pedido = ArgumentCaptor.forClass(CheckoutOrder.class);
    verify(checkoutOrderRepository).save(pedido.capture());
    Instant validade = (Instant) org.assertj.core.util.introspection.PropertyOrFieldSupport.EXTRACTION
        .getValueOf("validUntil", pedido.getValue());
    assertThat(Duration.between(antes, validade)).isBetween(Duration.ofDays(30).minusMinutes(1), Duration.ofDays(30).plusMinutes(1));
  }

  private static RegisterRequest request(String documento) {
    RegisterRequest request = new RegisterRequest();
    request.name = "Ana Dona";
    request.email = "ana@salao.test";
    request.password = "Senha@123";
    request.salonName = "Salao da Ana";
    request.phone = "(11) 99999-0000";
    request.cpfCnpj = documento;
    request.acceptedTermsOfUse = true;
    request.acceptedPrivacyPolicy = true;
    request.termsOfUseVersion = VERSAO;
    request.privacyPolicyVersion = VERSAO;
    return request;
  }

  private static TermsVersion versao(String tipo) {
    TermsVersion versao = new TermsVersion();
    versao.setId(UUID.randomUUID());
    versao.setDocumentType(tipo);
    versao.setVersion(VERSAO);
    return versao;
  }

  private static String sha256(String valor) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
