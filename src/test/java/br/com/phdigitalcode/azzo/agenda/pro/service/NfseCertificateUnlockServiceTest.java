package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCertificateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseCertificateUnlockSessionEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCertificateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseCertificateUnlockSessionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Cobre {@code modules/nfse/application/NfseCertificateUnlockService.java} (Fronteira 4 do porte
 * de {@code nfse}, ver {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25).
 *
 * <p>{@code findActive}/{@code findByToken}/{@code revokeActive}/{@code expirePastDueSessions} sao
 * metodos {@code default} de {@link NfseCertificateUnlockSessionRepository} — o mock do Mockito
 * NAO executa o corpo desses defaults, entao este teste estuba diretamente os metodos que o
 * service realmente chama (armadilha 6 do briefing, ja documentada em outras fronteiras de
 * {@code nfse}), nao as queries derivadas por baixo deles.
 */
@ExtendWith(MockitoExtension.class)
class NfseCertificateUnlockServiceTest {

  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private FiscalCertificateService fiscalCertificateService;
  @Mock private FiscalCertificateRepository fiscalCertificateRepository;
  @Mock private NfseCertificateUnlockSessionRepository nfseCertificateUnlockSessionRepository;
  @Mock private EncryptionService encryptionService;

  private NfseCertificateUnlockService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new NfseCertificateUnlockService(
            contextoTenant,
            authenticatedUser,
            fiscalCertificateService,
            fiscalCertificateRepository,
            nfseCertificateUnlockSessionRepository,
            encryptionService,
            15);
  }

  private FiscalCertificateEntity certificadoAtivo() {
    FiscalCertificateEntity entity = new FiscalCertificateEntity();
    entity.setId(UUID.randomUUID());
    return entity;
  }

  @Test
  void criaSessaoDeDesbloqueioValidandoSenhaEEncerrandoSessoesAnteriores() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    FiscalCertificateEntity certificado = certificadoAtivo();
    when(fiscalCertificateRepository.findActiveByTenant(tenantId)).thenReturn(Optional.of(certificado));
    when(encryptionService.encrypt("senha-correta")).thenReturn("senha-cifrada");

    NfseDtos.CertificateUnlockRequest request = new NfseDtos.CertificateUnlockRequest();
    request.certificatePassword = "senha-correta";

    NfseDtos.CertificateUnlockStatusResponse response = service.createUnlockSession(request);

    verify(nfseCertificateUnlockSessionRepository).expirePastDueSessions();
    verify(nfseCertificateUnlockSessionRepository).revokeActive(tenantId, userId);
    verify(fiscalCertificateService).validarSenhaCertificadoAtivo("senha-correta");

    ArgumentCaptor<NfseCertificateUnlockSessionEntity> captor =
        ArgumentCaptor.forClass(NfseCertificateUnlockSessionEntity.class);
    verify(nfseCertificateUnlockSessionRepository).save(captor.capture());
    NfseCertificateUnlockSessionEntity salvo = captor.getValue();
    assertThat(salvo.getTenantId()).isEqualTo(tenantId);
    assertThat(salvo.getUserId()).isEqualTo(userId);
    assertThat(salvo.getCertificateId()).isEqualTo(certificado.getId());
    assertThat(salvo.getStatus()).isEqualTo("ACTIVE");
    assertThat(salvo.getPasswordEnc()).isEqualTo("senha-cifrada");
    assertThat(salvo.getUnlockTokenId()).isNotBlank();
    assertThat(salvo.getExpiresAt()).isAfter(salvo.getIssuedAt());
    assertThat(salvo.getExpiresAt()).isEqualTo(salvo.getIssuedAt().plusSeconds(15 * 60L));

    assertThat(response.active).isTrue();
    assertThat(response.status).isEqualTo("ACTIVE");
    assertThat(response.unlockTokenId).isEqualTo(salvo.getUnlockTokenId());
  }

  @Test
  void rejeitaSenhaEmBrancoAntesDeConsultarQualquerRepositorio() {
    NfseDtos.CertificateUnlockRequest request = new NfseDtos.CertificateUnlockRequest();
    request.certificatePassword = "   ";

    assertThatThrownBy(() -> service.createUnlockSession(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_REQUIRED);

    verifyNoInteractions(
        contextoTenant, fiscalCertificateService, fiscalCertificateRepository, nfseCertificateUnlockSessionRepository);
  }

  @Test
  void rejeitaRequestNulo() {
    assertThatThrownBy(() -> service.createUnlockSession(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_REQUIRED);
  }

  @Test
  void propagaErroQuandoSenhaEValidaMasNaoHaCertificadoAtivo() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    when(fiscalCertificateRepository.findActiveByTenant(tenantId)).thenReturn(Optional.empty());

    NfseDtos.CertificateUnlockRequest request = new NfseDtos.CertificateUnlockRequest();
    request.certificatePassword = "senha-correta";

    assertThatThrownBy(() -> service.createUnlockSession(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_ACTIVE_MISSING);

    verify(fiscalCertificateService).validarSenhaCertificadoAtivo("senha-correta");
    verify(nfseCertificateUnlockSessionRepository, never()).save(any());
  }

  @Test
  void propagaErroDeSenhaInvalidaSemCriarSessao() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    org.mockito.Mockito.doThrow(new IllegalArgumentException(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_INVALID))
        .when(fiscalCertificateService)
        .validarSenhaCertificadoAtivo("senha-errada");

    NfseDtos.CertificateUnlockRequest request = new NfseDtos.CertificateUnlockRequest();
    request.certificatePassword = "senha-errada";

    assertThatThrownBy(() -> service.createUnlockSession(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_INVALID);

    verify(nfseCertificateUnlockSessionRepository, never()).save(any());
  }

  @Test
  void statusAtualDevolveInativoQuandoNaoHaSessaoAtiva() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    when(nfseCertificateUnlockSessionRepository.findActive(tenantId, userId)).thenReturn(Optional.empty());

    NfseDtos.CertificateUnlockStatusResponse response = service.getCurrentStatus();

    verify(nfseCertificateUnlockSessionRepository).expirePastDueSessions();
    assertThat(response.active).isFalse();
    assertThat(response.status).isEqualTo("INACTIVE");
    assertThat(response.unlockTokenId).isNull();
  }

  @Test
  void statusAtualDevolveSessaoAtivaComToken() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);

    NfseCertificateUnlockSessionEntity sessao = sessaoAtiva();
    when(nfseCertificateUnlockSessionRepository.findActive(tenantId, userId)).thenReturn(Optional.of(sessao));

    NfseDtos.CertificateUnlockStatusResponse response = service.getCurrentStatus();

    assertThat(response.active).isTrue();
    assertThat(response.status).isEqualTo("ACTIVE");
    assertThat(response.unlockTokenId).isEqualTo(sessao.getUnlockTokenId());
  }

  @Test
  void revogarSessaoAtualDelegaParaRepositorioComTenantEUsuarioCorretos() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);

    service.revokeCurrentSession();

    verify(nfseCertificateUnlockSessionRepository).revokeActive(tenantId, userId);
  }

  @Test
  void validaTokenDeDesbloqueioAtivoENaoExpirado() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    NfseCertificateUnlockSessionEntity sessao = sessaoAtiva();
    when(nfseCertificateUnlockSessionRepository.findByToken(tenantId, userId, sessao.getUnlockTokenId()))
        .thenReturn(Optional.of(sessao));

    boolean valido = service.validateUnlockToken(sessao.getUnlockTokenId());

    assertThat(valido).isTrue();
  }

  @Test
  void tokenAusenteOuEmBrancoNaoValidaSemConsultarRepositorio() {
    assertThat(service.validateUnlockToken(null)).isFalse();
    assertThat(service.validateUnlockToken("  ")).isFalse();
    verifyNoInteractions(contextoTenant, nfseCertificateUnlockSessionRepository);
  }

  @Test
  void tokenExpiradoMarcaSessaoComoExpiradaERetornaInvalido() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    NfseCertificateUnlockSessionEntity sessao = sessaoAtiva();
    sessao.setExpiresAt(Instant.now().minusSeconds(60));
    when(nfseCertificateUnlockSessionRepository.findByToken(tenantId, userId, sessao.getUnlockTokenId()))
        .thenReturn(Optional.of(sessao));

    boolean valido = service.validateUnlockToken(sessao.getUnlockTokenId());

    assertThat(valido).isFalse();
    assertThat(sessao.getStatus()).isEqualTo("EXPIRED");
  }

  @Test
  void resolvePasswordFromTokenDecifraSenhaDeSessaoAtivaValida() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    NfseCertificateUnlockSessionEntity sessao = sessaoAtiva();
    sessao.setPasswordEnc("senha-cifrada");
    when(nfseCertificateUnlockSessionRepository.findByToken(tenantId, userId, sessao.getUnlockTokenId()))
        .thenReturn(Optional.of(sessao));
    when(encryptionService.decrypt("senha-cifrada")).thenReturn("senha-em-claro");

    String senha = service.resolvePasswordFromToken(sessao.getUnlockTokenId());

    assertThat(senha).isEqualTo("senha-em-claro");
  }

  @Test
  void resolvePasswordFromTokenDevolveNuloQuandoSessaoNaoEstaAtiva() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    when(nfseCertificateUnlockSessionRepository.findByToken(eq(tenantId), eq(userId), anyString()))
        .thenReturn(Optional.empty());

    String senha = service.resolvePasswordFromToken("token-desconhecido");

    assertThat(senha).isNull();
    verify(encryptionService, never()).decrypt(anyString());
  }

  @Test
  void resolvePasswordFromTokenDevolveNuloParaTokenAusente() {
    assertThat(service.resolvePasswordFromToken(null)).isNull();
    assertThat(service.resolvePasswordFromToken("")).isNull();
    verifyNoInteractions(contextoTenant, nfseCertificateUnlockSessionRepository, encryptionService);
  }

  private NfseCertificateUnlockSessionEntity sessaoAtiva() {
    NfseCertificateUnlockSessionEntity sessao = new NfseCertificateUnlockSessionEntity();
    sessao.setId(UUID.randomUUID());
    sessao.setTenantId(tenantId);
    sessao.setUserId(userId);
    sessao.setCertificateId(UUID.randomUUID());
    sessao.setUnlockTokenId(UUID.randomUUID().toString());
    sessao.setIssuedAt(Instant.now());
    sessao.setExpiresAt(Instant.now().plusSeconds(900));
    sessao.setStatus("ACTIVE");
    return sessao;
  }
}
