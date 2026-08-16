package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.OnboardingDtos.AcceptTermsRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.OnboardingDtos.OnboardingStatusResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsAcceptance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantBusinessHoursRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TermsAcceptanceRepository;

/**
 * Espelha {@code modules/onboarding/application/ServicoOnboarding.java}. Preserva o codigo HTTP
 * 422 (nao padrao do {@link ApiClientErrorException}, mas o original tambem usa
 * {@code Response.status(422)} explicito) para as tres validacoes de {@code completeOnboarding}.
 */
@Service
public class ServicoOnboarding {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoOnboarding.class);
  private static final int UNPROCESSABLE_ENTITY = 422;

  private final TenantRepository tenantRepository;
  private final TermsAcceptanceRepository termsAcceptanceRepository;
  private final AuditService auditService;
  private final ProfissionalRepository profissionalRepository;
  private final ServicoRepository servicoRepository;
  private final TenantBusinessHoursRepository tenantBusinessHoursRepository;

  public ServicoOnboarding(
      TenantRepository tenantRepository,
      TermsAcceptanceRepository termsAcceptanceRepository,
      AuditService auditService,
      ProfissionalRepository profissionalRepository,
      ServicoRepository servicoRepository,
      TenantBusinessHoursRepository tenantBusinessHoursRepository) {
    this.tenantRepository = tenantRepository;
    this.termsAcceptanceRepository = termsAcceptanceRepository;
    this.auditService = auditService;
    this.profissionalRepository = profissionalRepository;
    this.servicoRepository = servicoRepository;
    this.tenantBusinessHoursRepository = tenantBusinessHoursRepository;
  }

  @Transactional(readOnly = true)
  public OnboardingStatusResponse getStatus(UUID tenantId) {
    Tenant tenant = buscarTenantOuFalhar(tenantId);

    boolean hasProfessionals = contarProfissionaisAtivos(tenantId) > 0;
    boolean hasServices = contarServicosAtivos(tenantId) > 0;
    boolean hasAssignments = contarVinculosProfissionalServico(tenantId) > 0;
    boolean hasBusinessHours = contarHorariosConfigurados(tenantId) > 0;
    boolean termsAccepted = termsAcceptanceRepository.hasAccepted(tenantId);

    String termsVersion = null;
    if (termsAccepted) {
      termsVersion = termsAcceptanceRepository.findLatestByTenantId(tenantId)
          .map(TermsAcceptance::getTermsVersion)
          .orElse(null);
    }

    return new OnboardingStatusResponse(
        Boolean.TRUE.equals(tenant.getOnboardingComplete()),
        Boolean.TRUE.equals(tenant.getOnboardingSkipped()),
        tenant.getOnboardingStep() != null ? tenant.getOnboardingStep() : 0,
        hasProfessionals,
        hasServices,
        hasAssignments,
        hasBusinessHours,
        termsAccepted,
        termsVersion,
        tenant.getOnboardingCompletedAt());
  }

  @Transactional
  public void acceptTerms(UUID tenantId, UUID userId, AcceptTermsRequest req, String ipAddress, String userAgent) {
    Tenant tenant = buscarTenantOuFalhar(tenantId);

    TermsAcceptance acceptance = new TermsAcceptance();
    acceptance.setTenantId(tenantId);
    acceptance.setUserId(userId);
    acceptance.setTermsVersion(req.termsVersion());
    acceptance.setPrivacyVersion(req.privacyVersion());
    acceptance.setIpAddress(ipAddress);
    acceptance.setUserAgent(userAgent);
    termsAcceptanceRepository.save(acceptance);

    if (tenant.getOnboardingStep() == null || tenant.getOnboardingStep() < 1) {
      tenant.setOnboardingStep(1);
    }
    tenantRepository.save(tenant);

    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.actorUserId = userId;
    command.module = "ONBOARDING";
    command.action = "TERMS_ACCEPTED";
    command.entityType = "TermsAcceptance";
    command.entityId = acceptance.getId() != null ? acceptance.getId().toString() : null;
    command.ipAddress = ipAddress;
    command.userAgent = userAgent;
    command.after = Map.of(
        "termsVersion", req.termsVersion(),
        "privacyVersion", req.privacyVersion());
    auditService.recordSuccess(command);

    LOG.info("onboarding_terms_accepted tenant_id={} user_id={} terms={} privacy={}",
        tenantId, userId, req.termsVersion(), req.privacyVersion());
  }

  @Transactional
  public void updateStep(UUID tenantId, int step) {
    Tenant tenant = buscarTenantOuFalhar(tenantId);
    if (step < 0) {
      throw new ApiClientErrorException("Step invalido.", HttpStatus.BAD_REQUEST.value());
    }
    if (tenant.getOnboardingStep() == null || step > tenant.getOnboardingStep()) {
      tenant.setOnboardingStep(step);
      tenantRepository.save(tenant);
    }
    LOG.info("onboarding_step_updated tenant_id={} step={}", tenantId, step);
  }

  @Transactional
  public void completeOnboarding(UUID tenantId) {
    Tenant tenant = buscarTenantOuFalhar(tenantId);

    boolean hasProfessionals = contarProfissionaisAtivos(tenantId) > 0;
    boolean hasServices = contarServicosAtivos(tenantId) > 0;
    boolean hasAssignments = contarVinculosProfissionalServico(tenantId) > 0;

    if (!hasProfessionals) {
      throw new ApiClientErrorException(
          "Cadastre pelo menos um profissional antes de concluir o onboarding.", UNPROCESSABLE_ENTITY);
    }
    if (!hasServices) {
      throw new ApiClientErrorException(
          "Cadastre pelo menos um servico antes de concluir o onboarding.", UNPROCESSABLE_ENTITY);
    }
    if (!hasAssignments) {
      throw new ApiClientErrorException(
          "Vincule pelo menos um profissional a um servico antes de concluir o onboarding.", UNPROCESSABLE_ENTITY);
    }

    tenant.setOnboardingComplete(true);
    tenant.setOnboardingCompletedAt(LocalDateTime.now());
    tenantRepository.save(tenant);

    LOG.info("onboarding_completed tenant_id={}", tenantId);
  }

  @Transactional
  public void skipOnboarding(UUID tenantId) {
    Tenant tenant = buscarTenantOuFalhar(tenantId);
    tenant.setOnboardingSkipped(true);
    tenantRepository.save(tenant);
    LOG.info("onboarding_skipped tenant_id={}", tenantId);
  }

  private Tenant buscarTenantOuFalhar(UUID tenantId) {
    return tenantRepository.findById(tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Tenant nao encontrado.", HttpStatus.NOT_FOUND.value()));
  }

  private long contarProfissionaisAtivos(UUID tenantId) {
    return profissionalRepository.countByTenantIdAndIsActiveTrue(tenantId);
  }

  private long contarServicosAtivos(UUID tenantId) {
    return servicoRepository.countByTenantIdAndIsActiveTrue(tenantId);
  }

  private long contarVinculosProfissionalServico(UUID tenantId) {
    return profissionalRepository.countServiceAssignmentsByTenantId(tenantId);
  }

  private long contarHorariosConfigurados(UUID tenantId) {
    return tenantBusinessHoursRepository.countByTenantIdAndEnabledTrue(tenantId);
  }
}
