package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCertificateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseCertificateUnlockSessionEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCertificateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseCertificateUnlockSessionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Porte verbatim de {@code modules/nfse/application/NfseCertificateUnlockService.java}
 * (Fronteira 4, ver {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25).
 *
 * <p>Gerencia a sessao de "senha do certificado desbloqueada" com TTL: o usuario informa a senha
 * do certificado digital A1 uma vez, ela e validada e cifrada (via {@link EncryptionService}) e
 * fica associada a um {@code unlockTokenId} por tempo limitado — o fluxo de emissao de NFS-e
 * (Fronteira 6) usa esse token para reobter a senha sem pedi-la de novo a cada emissao.
 *
 * <p>Reusa {@link FiscalCertificateService#validarSenhaCertificadoAtivo(String)} para validar a
 * senha contra o certificado ativo do tenant (mesmo {@code KeyStore} PKCS12 usado pela assinatura
 * XMLDSig da Fronteira 3) e {@link FiscalCertificateRepository#findActiveByTenant(UUID)} para
 * localizar o certificado ativo.
 *
 * <p>{@code getCurrentUserId()} do original (subject do JWT, convertido para {@code UUID}, com
 * {@code IllegalStateException} se ausente/invalido) vira {@link AuthenticatedUser#idOuFalhar()}
 * — mesma abstracao unica de JWT ja usada em todo o projeto migrado (regra 11 do briefing), com
 * mensagem de excecao diferente da original (aceito, mesmo padrao ja usado em outras fronteiras
 * de {@code nfse}, ex.: {@code FiscalDanfeJobService}).
 */
@Service
public class NfseCertificateUnlockService {

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final FiscalCertificateService fiscalCertificateService;
  private final FiscalCertificateRepository fiscalCertificateRepository;
  private final NfseCertificateUnlockSessionRepository nfseCertificateUnlockSessionRepository;
  private final EncryptionService encryptionService;
  private final int unlockTtlMinutes;

  public NfseCertificateUnlockService(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      FiscalCertificateService fiscalCertificateService,
      FiscalCertificateRepository fiscalCertificateRepository,
      NfseCertificateUnlockSessionRepository nfseCertificateUnlockSessionRepository,
      EncryptionService encryptionService,
      @Value("${app.nfse.certificate-unlock.ttl-minutes:15}") int unlockTtlMinutes) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.fiscalCertificateService = fiscalCertificateService;
    this.fiscalCertificateRepository = fiscalCertificateRepository;
    this.nfseCertificateUnlockSessionRepository = nfseCertificateUnlockSessionRepository;
    this.encryptionService = encryptionService;
    this.unlockTtlMinutes = unlockTtlMinutes;
  }

  @Transactional
  public NfseDtos.CertificateUnlockStatusResponse createUnlockSession(
      NfseDtos.CertificateUnlockRequest request) {
    String password = request != null ? request.certificatePassword : null;
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_REQUIRED);
    }

    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = authenticatedUser.idOuFalhar();
    nfseCertificateUnlockSessionRepository.expirePastDueSessions();
    nfseCertificateUnlockSessionRepository.revokeActive(tenantId, userId);

    fiscalCertificateService.validarSenhaCertificadoAtivo(password);
    FiscalCertificateEntity activeCertificate =
        fiscalCertificateRepository
            .findActiveByTenant(tenantId)
            .orElseThrow(
                () -> new IllegalArgumentException(FiscalCertificateService.ERR_CERTIFICATE_ACTIVE_MISSING));

    Instant now = Instant.now();
    NfseCertificateUnlockSessionEntity entity = new NfseCertificateUnlockSessionEntity();
    entity.setTenantId(tenantId);
    entity.setUserId(userId);
    entity.setCertificateId(activeCertificate.getId());
    entity.setUnlockTokenId(UUID.randomUUID().toString());
    entity.setIssuedAt(now);
    entity.setExpiresAt(now.plusSeconds(Math.max(unlockTtlMinutes, 1) * 60L));
    entity.setStatus("ACTIVE");
    entity.setPasswordEnc(encryptionService.encrypt(password));
    nfseCertificateUnlockSessionRepository.save(entity);
    return toResponse(entity, true);
  }

  @Transactional
  public NfseDtos.CertificateUnlockStatusResponse getCurrentStatus() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = authenticatedUser.idOuFalhar();
    nfseCertificateUnlockSessionRepository.expirePastDueSessions();
    NfseCertificateUnlockSessionEntity active =
        nfseCertificateUnlockSessionRepository.findActive(tenantId, userId).orElse(null);
    if (active == null) {
      NfseDtos.CertificateUnlockStatusResponse response = new NfseDtos.CertificateUnlockStatusResponse();
      response.active = false;
      response.status = "INACTIVE";
      return response;
    }
    return toResponse(active, true);
  }

  @Transactional
  public void revokeCurrentSession() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = authenticatedUser.idOuFalhar();
    nfseCertificateUnlockSessionRepository.revokeActive(tenantId, userId);
  }

  @Transactional
  public boolean validateUnlockToken(String unlockTokenId) {
    if (unlockTokenId == null || unlockTokenId.isBlank()) return false;
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = authenticatedUser.idOuFalhar();
    nfseCertificateUnlockSessionRepository.expirePastDueSessions();
    NfseCertificateUnlockSessionEntity session =
        nfseCertificateUnlockSessionRepository.findByToken(tenantId, userId, unlockTokenId).orElse(null);
    if (session == null) return false;
    if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(Instant.now())) {
      session.setStatus("EXPIRED");
      return false;
    }
    return "ACTIVE".equalsIgnoreCase(session.getStatus());
  }

  @Transactional
  public String resolvePasswordFromToken(String unlockTokenId) {
    if (unlockTokenId == null || unlockTokenId.isBlank()) return null;
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = authenticatedUser.idOuFalhar();
    nfseCertificateUnlockSessionRepository.expirePastDueSessions();
    NfseCertificateUnlockSessionEntity session =
        nfseCertificateUnlockSessionRepository.findByToken(tenantId, userId, unlockTokenId).orElse(null);
    if (session == null || !"ACTIVE".equalsIgnoreCase(session.getStatus())) return null;
    if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(Instant.now())) return null;
    if (session.getPasswordEnc() == null || session.getPasswordEnc().isBlank()) return null;
    return encryptionService.decrypt(session.getPasswordEnc());
  }

  private NfseDtos.CertificateUnlockStatusResponse toResponse(
      NfseCertificateUnlockSessionEntity entity, boolean includeToken) {
    NfseDtos.CertificateUnlockStatusResponse response = new NfseDtos.CertificateUnlockStatusResponse();
    response.active =
        "ACTIVE".equalsIgnoreCase(entity.getStatus())
            && entity.getExpiresAt() != null
            && entity.getExpiresAt().isAfter(Instant.now());
    response.unlockTokenId = includeToken ? entity.getUnlockTokenId() : null;
    response.issuedAt = entity.getIssuedAt() != null ? entity.getIssuedAt().toString() : null;
    response.expiresAt = entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null;
    response.status = entity.getStatus();
    return response;
  }
}
