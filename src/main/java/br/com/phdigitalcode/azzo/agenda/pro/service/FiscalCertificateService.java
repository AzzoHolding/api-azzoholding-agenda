package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCertificateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCertificateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Porte verbatim de {@code modules/fiscal/application/FiscalCertificateService.java}.
 *
 * <p>Gerencia o certificado digital A1 (PFX) do tenant: upload, ativação, remoção lógica e
 * extração de material de chave para assinatura. O PFX em si nunca sai em resposta de API — só
 * fica cifrado em {@code certificatePfxEnc} (via {@link EncryptionService}, AES/GCM) e é
 * decifrado sob demanda para validar senha ou extrair a chave privada.
 *
 * <p>{@code jakarta.ws.rs.NotFoundException} do original vira {@link ApiClientErrorException} 404,
 * mesmo padrão dos demais services fiscais.
 */
@Service
public class FiscalCertificateService {

  public static final String ERR_CERTIFICATE_PASSWORD_REQUIRED = "NFSE_CERTIFICATE_PASSWORD_REQUIRED";
  public static final String ERR_CERTIFICATE_ACTIVE_MISSING = "NFSE_CERTIFICATE_ACTIVE_MISSING";
  public static final String ERR_CERTIFICATE_PASSWORD_INVALID = "NFSE_CERTIFICATE_PASSWORD_INVALID";

  private final ContextoTenant contextoTenant;
  private final FiscalCertificateRepository fiscalCertificateRepository;
  private final EncryptionService encryptionService;
  private final AuditService auditService;
  private final AuthenticatedUser authenticatedUser;

  public FiscalCertificateService(
      ContextoTenant contextoTenant,
      FiscalCertificateRepository fiscalCertificateRepository,
      EncryptionService encryptionService,
      AuditService auditService,
      AuthenticatedUser authenticatedUser) {
    this.contextoTenant = contextoTenant;
    this.fiscalCertificateRepository = fiscalCertificateRepository;
    this.encryptionService = encryptionService;
    this.auditService = auditService;
    this.authenticatedUser = authenticatedUser;
  }

  @Transactional(readOnly = true)
  public List<FiscalDtos.CertificateResponse> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fiscalCertificateRepository.listByTenant(tenantId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public FiscalDtos.CertificateResponse salvar(FiscalDtos.CertificateUpsertRequest request) {
    if (request == null) throw new IllegalArgumentException("Request obrigatorio");
    if (request.pfxBase64 == null || request.pfxBase64.isBlank()) {
      throw new IllegalArgumentException("pfxBase64 obrigatorio");
    }
    if (request.password == null || request.password.isBlank()) {
      throw new IllegalArgumentException("password obrigatoria");
    }

    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    byte[] pfx = decodeBase64(request.pfxBase64);
    CertificateInfo info = extrairInfo(pfx, request.password.toCharArray());

    fiscalCertificateRepository.deactivateAllActive(tenantId);

    FiscalCertificateEntity entity = new FiscalCertificateEntity();
    entity.setTenantId(tenantId);
    entity.setCertificatePfxEnc(encryptionService.encrypt(request.pfxBase64));
    entity.setThumbprint(info.thumbprint());
    entity.setSubjectName(info.subjectName());
    entity.setValidTo(info.validTo());
    entity.setStatus("ACTIVE");
    fiscalCertificateRepository.save(entity);
    auditar(
        tenantId,
        entity.getId(),
        "FISCAL_CERTIFICATE_UPLOAD",
        null,
        Map.of("status", entity.getStatus(), "thumbprint", entity.getThumbprint()));

    return toResponse(entity);
  }

  @Transactional
  public void remover(String certificateId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID id = parseUuid(certificateId);
    FiscalCertificateEntity entity =
        fiscalCertificateRepository
            .findByTenantAndId(tenantId, id)
            .orElseThrow(() -> new ApiClientErrorException("Certificado fiscal nao encontrado.", 404));
    String oldStatus = entity.getStatus();
    entity.setStatus("DELETED");
    auditar(
        tenantId,
        entity.getId(),
        "FISCAL_CERTIFICATE_DELETE",
        Map.of("status", oldStatus),
        Map.of("status", entity.getStatus()));
  }

  @Transactional
  public FiscalDtos.CertificateResponse ativar(String certificateId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID id = parseUuid(certificateId);
    FiscalCertificateEntity entity =
        fiscalCertificateRepository
            .findByTenantAndId(tenantId, id)
            .orElseThrow(() -> new ApiClientErrorException("Certificado fiscal nao encontrado.", 404));
    fiscalCertificateRepository.deactivateAllActive(tenantId);
    String oldStatus = entity.getStatus();
    entity.setStatus("ACTIVE");
    auditar(
        tenantId,
        entity.getId(),
        "FISCAL_CERTIFICATE_ACTIVATE",
        Map.of("status", oldStatus),
        Map.of("status", entity.getStatus()));
    return toResponse(entity);
  }

  @Transactional(readOnly = true)
  public void validarSenhaCertificadoAtivo(String password) {
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException(ERR_CERTIFICATE_PASSWORD_REQUIRED);
    }
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalCertificateEntity active =
        fiscalCertificateRepository
            .findActiveByTenant(tenantId)
            .orElseThrow(() -> new IllegalArgumentException(ERR_CERTIFICATE_ACTIVE_MISSING));

    String pfxBase64 = encryptionService.decrypt(active.getCertificatePfxEnc());
    byte[] pfx = decodeBase64(pfxBase64);
    extrairInfo(pfx, password.toCharArray());
  }

  @Transactional(readOnly = true)
  public KeyMaterial loadActiveKeyMaterial(String password) {
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException(ERR_CERTIFICATE_PASSWORD_REQUIRED);
    }
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalCertificateEntity active =
        fiscalCertificateRepository
            .findActiveByTenant(tenantId)
            .orElseThrow(() -> new IllegalArgumentException(ERR_CERTIFICATE_ACTIVE_MISSING));

    String pfxBase64 = encryptionService.decrypt(active.getCertificatePfxEnc());
    byte[] pfx = decodeBase64(pfxBase64);
    return extractKeyMaterial(pfx, password.toCharArray());
  }

  private UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("id de certificado invalido");
    }
  }

  private byte[] decodeBase64(String value) {
    try {
      return Base64.getDecoder().decode(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("pfxBase64 invalido");
    }
  }

  private CertificateInfo extrairInfo(byte[] pfx, char[] password) {
    KeyMaterial keyMaterial = extractKeyMaterial(pfx, password);
    X509Certificate x509 = keyMaterial.certificate();
    try {
      String thumbprint = sha256Hex(x509.getEncoded());
      Instant validTo = x509.getNotAfter().toInstant();
      String subjectName =
          x509.getSubjectX500Principal() != null ? x509.getSubjectX500Principal().getName() : "unknown";
      return new CertificateInfo(thumbprint, subjectName, validTo);
    } catch (Exception e) {
      throw new IllegalArgumentException(ERR_CERTIFICATE_PASSWORD_INVALID);
    }
  }

  private KeyMaterial extractKeyMaterial(byte[] pfx, char[] password) {
    try {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(new ByteArrayInputStream(pfx), password);
      String alias = null;
      var aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        String current = aliases.nextElement();
        if (keyStore.isKeyEntry(current)) {
          alias = current;
          break;
        }
      }
      if (alias == null) throw new IllegalArgumentException("PFX sem chave privada valida");

      Object key = keyStore.getKey(alias, password);
      if (!(key instanceof PrivateKey privateKey)) {
        throw new IllegalArgumentException("Chave privada invalida no certificado PFX");
      }

      java.security.cert.Certificate certificate = keyStore.getCertificate(alias);
      if (!(certificate instanceof X509Certificate x509)) {
        throw new IllegalArgumentException("Certificado do PFX nao e X509");
      }
      return new KeyMaterial(privateKey, x509);
    } catch (IllegalArgumentException e) {
      if (ERR_CERTIFICATE_PASSWORD_REQUIRED.equals(e.getMessage())
          || ERR_CERTIFICATE_ACTIVE_MISSING.equals(e.getMessage())
          || ERR_CERTIFICATE_PASSWORD_INVALID.equals(e.getMessage())) {
        throw e;
      }
      throw new IllegalArgumentException(ERR_CERTIFICATE_PASSWORD_INVALID);
    } catch (Exception e) {
      throw new IllegalArgumentException(ERR_CERTIFICATE_PASSWORD_INVALID);
    }
  }

  private String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao calcular thumbprint do certificado", e);
    }
  }

  private FiscalDtos.CertificateResponse toResponse(FiscalCertificateEntity entity) {
    FiscalDtos.CertificateResponse response = new FiscalDtos.CertificateResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.thumbprint = entity.getThumbprint();
    response.subjectName = entity.getSubjectName();
    response.validTo = entity.getValidTo() != null ? entity.getValidTo().toString() : null;
    response.status = entity.getStatus();
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    return response;
  }

  private void auditar(UUID tenantId, UUID certificateId, String action, Object before, Object after) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = resolveActorUserId();
      command.module = AuditConstants.Module.FISCAL;
      command.action = action;
      command.entityType = "FISCAL_CERTIFICATE";
      command.entityId = certificateId != null ? certificateId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve interromper operacao fiscal.
    }
  }

  private UUID resolveActorUserId() {
    try {
      return authenticatedUser.idOuNulo();
    } catch (Exception e) {
      return null;
    }
  }

  private record CertificateInfo(String thumbprint, String subjectName, Instant validTo) {}

  public record KeyMaterial(PrivateKey privateKey, X509Certificate certificate) {}
}
