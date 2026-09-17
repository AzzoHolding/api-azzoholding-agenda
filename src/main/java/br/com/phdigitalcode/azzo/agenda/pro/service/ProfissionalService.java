package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import at.favre.lib.crypto.bcrypt.BCrypt;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ProfissionalRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalLimitsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.WorkingHoursDto;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacRole;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacUserRole;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Specialty;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.id.RbacUserRoleId;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
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

/**
 * Espelha {@code modules/professionals/application/ServicoProfissionais.java}.
 *
 * <p>Os e-mails de credenciais sao disparados via {@link AfterCommitExecutor} (equivalente ao
 * {@code AfterCommitExecutor} CDI do original), agora que {@link CredentialsEmailService} envia
 * de verdade (modulo {@code email} portado) — preserva a semantica original de nunca enviar
 * e-mail se a transacao de criacao/reset do profissional der rollback.
 */
@Service
public class ProfissionalService {

  private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#$%";
  private static final Random RANDOM = new SecureRandom();
  private static final ZoneId ZONA_DO_SALAO = ZoneId.of("America/Sao_Paulo");
  private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

  private final ProfissionalRepository profissionalRepository;
  private final ProfissionalWorkingHourRepository profissionalWorkingHourRepository;
  private final UsuarioRepository usuarioRepository;
  private final SpecialtyRepository specialtyRepository;
  private final ServicoRepository servicoRepository;
  private final RbacRoleRepository rbacRoleRepository;
  private final RbacUserRoleRepository rbacUserRoleRepository;
  private final RbacAuthorizationRepository rbacAuthorizationRepository;
  private final PlanLimitsRepository planLimitsRepository;
  private final CredentialsEmailService credentialsEmailService;
  private final AfterCommitExecutor afterCommitExecutor;
  private final ContextoTenant contextoTenant;
  private final AuditService auditService;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final AgendamentoRepository agendamentoRepository;

  /**
   * Derruba as sessoes de quem e desativado. Campo, e nao construtor: sem ele (teste) o
   * login e a renovacao continuam barrando; so a sessao ja aberta dura ate o token vencer.
   */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private br.com.phdigitalcode.azzo.agenda.pro.security.RefreshTokenService refreshTokenService;

  public ProfissionalService(
      ProfissionalRepository profissionalRepository,
      ProfissionalWorkingHourRepository profissionalWorkingHourRepository,
      UsuarioRepository usuarioRepository,
      SpecialtyRepository specialtyRepository,
      ServicoRepository servicoRepository,
      RbacRoleRepository rbacRoleRepository,
      RbacUserRoleRepository rbacUserRoleRepository,
      RbacAuthorizationRepository rbacAuthorizationRepository,
      PlanLimitsRepository planLimitsRepository,
      CredentialsEmailService credentialsEmailService,
      AfterCommitExecutor afterCommitExecutor,
      ContextoTenant contextoTenant,
      AuditService auditService,
      PasswordPolicyValidator passwordPolicyValidator,
      AgendamentoRepository agendamentoRepository) {
    this.profissionalRepository = profissionalRepository;
    this.profissionalWorkingHourRepository = profissionalWorkingHourRepository;
    this.usuarioRepository = usuarioRepository;
    this.specialtyRepository = specialtyRepository;
    this.servicoRepository = servicoRepository;
    this.rbacRoleRepository = rbacRoleRepository;
    this.rbacUserRoleRepository = rbacUserRoleRepository;
    this.rbacAuthorizationRepository = rbacAuthorizationRepository;
    this.planLimitsRepository = planLimitsRepository;
    this.credentialsEmailService = credentialsEmailService;
    this.afterCommitExecutor = afterCommitExecutor;
    this.contextoTenant = contextoTenant;
    this.auditService = auditService;
    this.passwordPolicyValidator = passwordPolicyValidator;
    this.agendamentoRepository = agendamentoRepository;
  }

  /** A equipe: todos os ativos, inclusive quem nao recebe agendamento. */
  @Transactional(readOnly = true)
  public List<ProfissionalResponse> listar(String serviceId) {
    return listar(serviceId, false);
  }

  /**
   * {@code somenteQuemAgenda}: so quem aceita agendamento — para as listas de MARCAR horario (o
   * assistente). A tela de equipe usa a outra: quem nao agenda continua sendo da equipe.
   */
  @Transactional(readOnly = true)
  public List<ProfissionalResponse> listar(String serviceId, boolean somenteQuemAgenda) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<Profissional> encontrados;
    if (serviceId == null || serviceId.isBlank()) {
      encontrados = profissionalRepository.findByTenantIdAndIsActiveTrue(tenantId);
    } else {
      UUID serviceUuid = UUID.fromString(serviceId);
      Servico servico = servicoRepository.findByIdAndTenantId(serviceUuid, tenantId)
          .filter(Servico::isActive)
          .orElseThrow(() -> new IllegalArgumentException("Servico nao encontrado"));
      encontrados = servico.getProfissionais() == null || servico.getProfissionais().isEmpty()
          ? profissionalRepository.findByTenantIdAndIsActiveTrue(tenantId)
          : servico.getProfissionais().stream().filter(Profissional::isActive).toList();
    }
    return encontrados.stream()
        .filter(p -> !somenteQuemAgenda || p.isAcceptsAppointments())
        .map(this::toResponse)
        .toList();
  }

  /**
   * Quantos atendimentos por vir (pendentes e confirmados, de agora em diante) estao com o
   * profissional. E o numero que a tela mostra ao desligar "aceita agendamento": os ja marcados
   * continuam — nada e cancelado —, e o dono precisa saber quantos sao.
   */
  @Transactional(readOnly = true)
  public long contarAgendamentosFuturos(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    profissionalRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Profissional nao encontrado"));
    ZonedDateTime agora = ZonedDateTime.now(ZONA_DO_SALAO);
    return agendamentoRepository.countFutureActiveForProfessional(
        tenantId,
        id,
        List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED),
        agora.toLocalDate(),
        agora.toLocalTime().format(HORA));
  }

  @Transactional(readOnly = true)
  public ProfissionalResponse obterPorId(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Profissional profissional = profissionalRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Profissional nao encontrado"));
    return toResponse(profissional);
  }

  @Transactional(readOnly = true)
  public void validarAcessoProprio(UUID profissionalId, UUID userId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Profissional profissional = profissionalRepository.findByIdAndTenantId(profissionalId, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Profissional nao encontrado"));
    if (!userId.equals(profissional.getUserId())) {
      throw new ApiClientErrorException("Acesso negado", 403);
    }
  }

  @Transactional(readOnly = true)
  public ProfissionalLimitsResponse obterLimites() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    long current = profissionalRepository.countByTenantIdAndIsActiveTrue(tenantId);
    int max = obterLimiteProfissionaisDoPlanoObrigatorio(tenantId);

    ProfissionalLimitsResponse response = new ProfissionalLimitsResponse();
    response.currentProfessionals = current;
    response.maxProfessionals = max;
    response.remaining = Math.max(0L, max - current);
    return response;
  }

  @Transactional
  public ProfissionalResponse criar(ProfissionalRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    validarLimiteProfissionaisPlano(tenantId);
    Usuario user = resolveUserForCreate(tenantId, req);

    Profissional p = new Profissional();
    p.setTenantId(tenantId);
    p.setUserId(user != null ? user.getId() : null);
    aplicar(req, p, tenantId);

    p = profissionalRepository.save(p);
    replaceWorkingHours(p, tenantId, req.workingHours);

    ProfissionalResponse response = toResponse(p);
    registrarAuditoriaProfissional(tenantId, "PROFESSIONAL_CREATE", null, response, p.getId().toString());
    return response;
  }

  @Transactional
  public ProfissionalResponse atualizar(UUID id, ProfissionalRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Profissional p = profissionalRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Profissional nao encontrado"));

    ProfissionalResponse before = toResponse(p);
    // Fidelidade ao original: email/telefone do profissional NAO sao alterados no update
    // (ServicoProfissionais.atualizar sobrescreve req.email/req.phone com os valores atuais).
    req.email = p.getEmail();
    req.phone = p.getPhone();
    boolean estavaAtivo = p.isActive();
    exigirSemAgendamentosFuturosAoDesativar(estavaAtivo, req.isActive, p.getId());
    syncLinkedUserOnUpdate(tenantId, p, req);
    aplicar(req, p, tenantId);
    p = profissionalRepository.save(p);
    encerrarSessoesSeFoiDesativado(estavaAtivo, p);
    replaceWorkingHours(p, tenantId, req.workingHours);

    ProfissionalResponse after = toResponse(p);
    registrarAuditoriaProfissional(tenantId, "PROFESSIONAL_UPDATE", before, after, p.getId().toString());
    return after;
  }

  @Transactional
  public ProfissionalResponse toggleStatus(UUID id, boolean isActive) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Profissional p = profissionalRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Profissional nao encontrado"));

    ProfissionalResponse before = toResponse(p);
    boolean estavaAtivo = p.isActive();
    exigirSemAgendamentosFuturosAoDesativar(estavaAtivo, isActive, p.getId());
    p.setActive(isActive);
    p = profissionalRepository.save(p);
    encerrarSessoesSeFoiDesativado(estavaAtivo, p);
    ProfissionalResponse after = toResponse(p);
    registrarAuditoriaProfissional(tenantId, "PROFESSIONAL_UPDATE", before, after, p.getId().toString());
    return after;
  }

  /**
   * Quem acabou de ser desativado sai do sistema AGORA.
   *
   * <p>Barrar so o login deixava a sessao aberta viva: o access token vale 15 minutos e o refresh,
   * 30 dias. {@code revokeAllForUser} revoga os refresh tokens e marca {@code tokensRevokedBefore},
   * que o filtro do JWT ja confere a cada requisicao. O dono nunca e desconectado por aqui.
   */
  private void encerrarSessoesSeFoiDesativado(boolean estavaAtivo, Profissional profissional) {
    if (!estavaAtivo || profissional.isActive() || profissional.getUserId() == null) return;
    if (refreshTokenService == null) return;
    boolean ehDono =
        usuarioRepository
            .findById(profissional.getUserId())
            .map(
                usuario ->
                    usuario.getRole() == br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario.OWNER
                        || usuario.getRole()
                            == br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario.ADMIN)
            .orElse(false);
    if (ehDono) return;
    refreshTokenService.revokeAllForUser(profissional.getUserId());
  }

  /**
   * Desativar quem tem atendimento marcado deixava os clientes com um profissional que nao trabalha
   * mais ali — e ninguem avisado (analise de 2026-09-16, M9). Agora recusa, dizendo quantos sao: o
   * salao realoca (ou cancela) antes.
   */
  private void exigirSemAgendamentosFuturosAoDesativar(
      boolean estavaAtivo, boolean ficaAtivo, UUID profissionalId) {
    if (!estavaAtivo || ficaAtivo) return;
    long futuros = contarAgendamentosFuturos(profissionalId);
    if (futuros <= 0) return;
    throw new IllegalArgumentException(
        "Este profissional tem "
            + futuros
            + (futuros == 1 ? " agendamento marcado" : " agendamentos marcados")
            + ". Realoque ou cancele antes de desativar.");
  }

  /** Soft delete: marca {@code isActive=false}, igual ao original (nunca apaga a linha). */
  @Transactional
  public void deletar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Profissional profissional = profissionalRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Profissional nao encontrado"));
    ProfissionalResponse before = toResponse(profissional);

    UUID usuarioAutenticadoId = obterUsuarioAutenticadoId();
    if (usuarioAutenticadoId != null
        && profissional.getUserId() != null
        && profissional.getUserId().equals(usuarioAutenticadoId)) {
      throw new IllegalArgumentException("Nao e permitido remover o proprio usuario");
    }

    boolean estavaAtivo = profissional.isActive();
    exigirSemAgendamentosFuturosAoDesativar(estavaAtivo, false, profissional.getId());
    profissional.setActive(false);
    profissionalRepository.save(profissional);
    encerrarSessoesSeFoiDesativado(estavaAtivo, profissional);
    registrarAuditoriaProfissional(tenantId, "PROFESSIONAL_DELETE", before, null, profissional.getId().toString());
  }

  @Transactional
  public ProfissionalResponse.PasswordResetResponse resetarSenha(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Profissional p = profissionalRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Profissional nao encontrado"));
    if (p.getUserId() == null) {
      throw new IllegalArgumentException("Profissional nao possui usuario de acesso vinculado");
    }

    Usuario user = usuarioRepository.findById(p.getUserId())
        .filter(item -> tenantId.equals(item.getTenantId()))
        .orElseThrow(() -> new IllegalArgumentException("Usuario vinculado ao profissional nao encontrado"));
    if (user.getEmail() == null || user.getEmail().isBlank()) {
      throw new IllegalArgumentException("Usuario do profissional nao possui email configurado");
    }

    String temporaryPassword = generatePassword(12);
    user.setPasswordHash(BCrypt.withDefaults().hashToString(12, temporaryPassword.toCharArray()));
    usuarioRepository.save(user);
    String userEmail = user.getEmail();
    String userName = user.getName();
    afterCommitExecutor.run(
        () -> credentialsEmailService.sendTemporaryPasswordReset(userEmail, userName, userEmail, temporaryPassword));

    ProfissionalResponse.PasswordResetResponse response = new ProfissionalResponse.PasswordResetResponse();
    response.professionalId = p.getId().toString();
    response.userId = user.getId().toString();
    response.email = user.getEmail();
    response.message = "Senha temporaria gerada e enviada";

    Map<String, Object> before = new HashMap<>();
    before.put("professionalId", p.getId().toString());
    before.put("userId", user.getId().toString());
    before.put("email", user.getEmail());
    Map<String, Object> after = new HashMap<>(before);
    after.put("passwordReset", true);
    registrarAuditoriaProfissional(tenantId, "PROFESSIONAL_PASSWORD_RESET", before, after, p.getId().toString());
    return response;
  }

  // -------------------------------------------------------------------------
  // Internals
  // -------------------------------------------------------------------------

  private void registrarAuditoriaProfissional(UUID tenantId, String action, Object before, Object after, String entityId) {
    if (tenantId == null) return;
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = obterUsuarioAutenticadoId();
      command.module = AuditConstants.Module.PROFESSIONAL;
      command.action = action;
      command.entityType = "PROFESSIONAL";
      command.entityId = entityId;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve bloquear o fluxo principal.
    }
  }

  private void aplicar(ProfissionalRequest req, Profissional p, UUID tenantId) {
    p.setName(req.name);
    p.setEmail(req.email != null ? req.email.trim().toLowerCase(Locale.ROOT) : null);
    p.setPhone(req.phone);
    p.setAvatar(req.avatar);
    p.setSpecialties(resolveSpecialties(tenantId, req.specialties));
    // A coluna `commission_rate` e NOT NULL (como no original Quarkus): "sem comissao" se
    // grava como ZERO, nao como ausente. Cliente que manda `null` — a tela manda quando o campo
    // fica em branco — derrubava o cadastro com 400 generico ("null value in column
    // commission_rate", producao 2026-09-16). Ausente na edicao MANTEM o que ja estava.
    if (req.commissionRate != null) {
      p.setCommissionRate(req.commissionRate);
    } else if (p.getCommissionRate() == null) {
      p.setCommissionRate(BigDecimal.ZERO);
    }
    p.setActive(req.isActive);
    // Ausente mantem o que ja estava (e o cadastro nasce com o padrao da entidade, TRUE): um
    // cliente antigo que nao conhece o campo nao pode tirar ninguem da agenda.
    if (req.acceptsAppointments != null) p.setAcceptsAppointments(req.acceptsAppointments);
  }

  private void replaceWorkingHours(Profissional profissional, UUID tenantId, List<WorkingHoursDto> requestedHours) {
    profissionalWorkingHourRepository.deleteByProfessional(tenantId, profissional.getId());
    if (requestedHours == null || requestedHours.isEmpty()) return;

    List<ProfissionalWorkingHour> rows = requestedHours.stream()
        .filter(java.util.Objects::nonNull)
        .map(item -> toWorkingHour(profissional, tenantId, item))
        .sorted(
            Comparator.comparingInt(ProfissionalWorkingHour::getDayOfWeek)
                .thenComparing(ProfissionalWorkingHour::getStartTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ProfissionalWorkingHour::getEndTime, Comparator.nullsFirst(Comparator.naturalOrder())))
        .toList();

    profissionalWorkingHourRepository.saveAll(rows);
  }

  private ProfissionalWorkingHour toWorkingHour(Profissional profissional, UUID tenantId, WorkingHoursDto dto) {
    ProfissionalWorkingHour row = new ProfissionalWorkingHour();
    row.setTenantId(tenantId);
    row.setProfessionalId(profissional.getId());
    row.setDayOfWeek(dto.dayOfWeek);
    row.setStartTime(parseTimeOrNull(dto.startTime));
    row.setEndTime(parseTimeOrNull(dto.endTime));
    row.setWorking(dto.isWorking);
    return row;
  }

  private LocalTime parseTimeOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalTime.parse(value.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private Usuario resolveUserForCreate(UUID tenantId, ProfissionalRequest req) {
    if (req.userId != null && !req.userId.isBlank()) {
      Usuario user = usuarioRepository.findById(UUID.fromString(req.userId))
          .filter(item -> tenantId.equals(item.getTenantId()))
          .orElseThrow(() -> new IllegalArgumentException("Usuario informado nao encontrado no tenant"));
      return prepareLinkedUser(tenantId, user, req, null);
    }

    if (!Boolean.TRUE.equals(req.createUser)) return null;

    if (req.email == null || req.email.isBlank()) {
      throw new IllegalArgumentException("Email obrigatorio para criar usuario de acesso do profissional");
    }
    String effectivePassword = req.accessPassword;
    boolean generatedPassword = false;
    if (effectivePassword == null || effectivePassword.isBlank()) {
      effectivePassword = generatePassword(12);
      generatedPassword = true;
    } else {
      passwordPolicyValidator.validateOrThrow(effectivePassword);
    }

    Usuario existentGlobal = usuarioRepository.findByEmail(req.email.trim()).orElse(null);
    if (existentGlobal != null) {
      if (tenantId.equals(existentGlobal.getTenantId())
          && isPrivilegedUser(existentGlobal)
          && isCurrentAuthenticatedUser(existentGlobal.getId())) {
        return prepareLinkedUser(tenantId, existentGlobal, req, null);
      }
      throw new IllegalArgumentException("Ja existe usuario com este email");
    }

    Usuario user = new Usuario();
    user.setTenantId(tenantId);
    user.setName(req.name);
    user.setEmail(req.email.trim().toLowerCase(Locale.ROOT));
    user.setPhone(req.phone);
    user.setAvatar(req.avatar);
    user.setRole(PapelUsuario.PROFESSIONAL);
    validarCriacaoUsuarioPorRole(user.getRole());
    user.setPasswordHash(BCrypt.withDefaults().hashToString(12, effectivePassword.toCharArray()));
    user = usuarioRepository.save(user);

    ensureProfessionalAccess(user);
    if (generatedPassword) {
      String userEmail = user.getEmail();
      String userName = user.getName();
      String generatedAccessPassword = effectivePassword;
      afterCommitExecutor.run(
          () -> credentialsEmailService.sendProfessionalAccess(userEmail, userName, userEmail, generatedAccessPassword));
    }
    return user;
  }

  private void syncLinkedUserOnUpdate(UUID tenantId, Profissional p, ProfissionalRequest req) {
    Usuario user = null;
    if (req.userId != null && !req.userId.isBlank()) {
      UUID novoUserId = UUID.fromString(req.userId);
      // Ligar um login (o do dono, por exemplo) so vale para quem ainda nao tem: trocar deixaria o
      // login anterior sem cadastro, com acesso e sem ninguem saber de quem e.
      if (p.getUserId() != null && !p.getUserId().equals(novoUserId)) {
        throw new IllegalArgumentException("Este profissional ja tem login proprio");
      }
      user = usuarioRepository.findById(novoUserId)
          .filter(item -> tenantId.equals(item.getTenantId()))
          .orElseThrow(() -> new IllegalArgumentException("Usuario informado nao encontrado no tenant"));
      p.setUserId(user.getId());
    } else if (p.getUserId() != null) {
      user = usuarioRepository.findById(p.getUserId()).filter(item -> tenantId.equals(item.getTenantId())).orElse(null);
    }

    if (user == null) return;
    prepareLinkedUser(tenantId, user, req, p.getId());
  }

  private Usuario prepareLinkedUser(UUID tenantId, Usuario user, ProfissionalRequest req, UUID currentProfessionalId) {
    validateProfessionalUserLinkAvailable(tenantId, user.getId(), currentProfessionalId);
    boolean preservePrimaryRole = isPrivilegedUser(user);
    updateUserBasics(user, req, preservePrimaryRole);
    applyAccessChanges(user, req.accessPassword, preservePrimaryRole);
    Usuario saved = usuarioRepository.save(user);
    ensureProfessionalAccess(saved);
    return saved;
  }

  private void updateUserBasics(Usuario user, ProfissionalRequest req, boolean preservePrimaryRole) {
    if (req.email != null && !req.email.isBlank()) {
      Usuario existentGlobal = usuarioRepository.findByEmail(req.email.trim()).orElse(null);
      if (existentGlobal != null && !existentGlobal.getId().equals(user.getId())) {
        throw new IllegalArgumentException("Ja existe usuario com este email");
      }
      if (!preservePrimaryRole) {
        user.setEmail(req.email.trim().toLowerCase(Locale.ROOT));
      }
    }
    if (!preservePrimaryRole) {
      user.setName(req.name);
      user.setPhone(req.phone);
      user.setAvatar(req.avatar);
    }
  }

  private void applyAccessChanges(Usuario user, String newPassword, boolean preservePrimaryRole) {
    if (!preservePrimaryRole) {
      user.setRole(PapelUsuario.PROFESSIONAL);
      validarCriacaoUsuarioPorRole(user.getRole());
    }
    if (newPassword != null && !newPassword.isBlank()) {
      passwordPolicyValidator.validateOrThrow(newPassword);
      user.setPasswordHash(BCrypt.withDefaults().hashToString(12, newPassword.toCharArray()));
    }
  }

  private void validateProfessionalUserLinkAvailable(UUID tenantId, UUID userId, UUID currentProfessionalId) {
    if (tenantId == null || userId == null) return;
    Profissional linked = profissionalRepository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
    if (linked == null) return;
    if (currentProfessionalId != null && linked.getId() != null && linked.getId().equals(currentProfessionalId)) return;
    throw new IllegalArgumentException("Usuario ja vinculado a outro profissional");
  }

  private boolean isPrivilegedUser(Usuario user) {
    if (user == null || user.getRole() == null) return false;
    return PapelUsuario.OWNER.equals(user.getRole()) || PapelUsuario.ADMIN.equals(user.getRole());
  }

  private boolean isCurrentAuthenticatedUser(UUID userId) {
    UUID authenticatedUserId = obterUsuarioAutenticadoId();
    return authenticatedUserId != null && authenticatedUserId.equals(userId);
  }

  private void validarCriacaoUsuarioPorRole(PapelUsuario targetRole) {
    if (!PapelUsuario.ADMIN.equals(targetRole)) return;
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean isAdminActor = authentication != null
        && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    if (!isAdminActor) {
      throw new ApiClientErrorException("Somente ADMIN pode criar usuario com role ADMIN", 403);
    }
  }

  private void ensureProfessionalAccess(Usuario user) {
    RbacRole professionalRole = rbacRoleRepository
        .findByNameIgnoreCase(PapelUsuario.PROFESSIONAL.name())
        .orElseGet(() -> {
          RbacRole role = new RbacRole();
          role.setName(PapelUsuario.PROFESSIONAL.name());
          return rbacRoleRepository.save(role);
        });

    rbacAuthorizationRepository.grantDefaultProfessionalPermissions(professionalRole.getId());

    RbacUserRoleId id = new RbacUserRoleId();
    id.setUserId(user.getId());
    id.setRoleId(professionalRole.getId());
    if (rbacUserRoleRepository.findById(id).isEmpty()) {
      RbacUserRole userRole = new RbacUserRole();
      userRole.setId(id);
      rbacUserRoleRepository.save(userRole);
    }
  }

  private Set<Specialty> resolveSpecialties(UUID tenantId, List<String> requestedSpecialties) {
    if (requestedSpecialties == null || requestedSpecialties.isEmpty()) return Set.of();

    Map<String, String> normalizedByLower = new LinkedHashMap<>();
    for (String raw : requestedSpecialties) {
      if (raw == null) continue;
      String trimmed = raw.trim();
      if (trimmed.isBlank()) continue;
      normalizedByLower.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }

    Set<Specialty> result = new LinkedHashSet<>();
    for (String name : normalizedByLower.values()) {
      Specialty specialty = specialtyRepository.findByTenantAndName(tenantId, name).orElse(null);
      if (specialty == null) {
        specialty = new Specialty();
        specialty.setTenantId(tenantId);
        specialty.setName(name);
        specialty = specialtyRepository.save(specialty);
      }
      result.add(specialty);
    }
    return result;
  }

  private String generatePassword(int size) {
    for (int attempt = 0; attempt < 20; attempt++) {
      StringBuilder sb = new StringBuilder(size);
      for (int i = 0; i < size; i++) {
        sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
      }
      String candidate = sb.toString();
      if (passwordPolicyValidator.isValid(candidate)) return candidate;
    }
    return "Azzo@12345";
  }

  private void validarLimiteProfissionaisPlano(UUID tenantId) {
    int maxProfessionals = obterLimiteProfissionaisDoPlanoObrigatorio(tenantId);
    long current = profissionalRepository.countByTenantIdAndIsActiveTrue(tenantId);
    if (current >= maxProfessionals) {
      throw new IllegalArgumentException("Limite de profissionais do plano atingido (" + maxProfessionals + ")");
    }
  }

  private int obterLimiteProfissionaisDoPlanoObrigatorio(UUID tenantId) {
    UUID productId = planLimitsRepository.findActivePlanProductId(tenantId, Instant.now())
        .orElseThrow(() -> new IllegalArgumentException("Nao existe plano ativo para o tenant"));
    return planLimitsRepository.findMaxProfessionals(productId)
        .orElseThrow(() -> new IllegalArgumentException("Plano sem configuracao de limite de profissionais"));
  }

  private UUID obterUsuarioAutenticadoId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal) {
      return jwtPrincipal.userId();
    }
    return null;
  }

  private ProfissionalResponse toResponse(Profissional p) {
    ProfissionalResponse r = new ProfissionalResponse();
    r.id = p.getId().toString();
    r.tenantId = p.getTenantId().toString();
    r.userId = p.getUserId() != null ? p.getUserId().toString() : null;
    r.accessUserCreated = p.getUserId() != null;
    r.name = p.getName();
    r.email = p.getEmail();
    r.phone = p.getPhone();
    r.avatar = p.getAvatar();
    r.specialties = p.getSpecialties().stream().map(Specialty::getName).sorted().toList();
    r.specialtiesDetailed = p.getSpecialties().stream()
        .sorted(Comparator.comparing(Specialty::getName))
        .map(s -> {
          ProfissionalResponse.SpecialidadeInfoDto dto = new ProfissionalResponse.SpecialidadeInfoDto();
          dto.name = s.getName();
          dto.description = s.getDescription();
          return dto;
        })
        .toList();
    r.commissionRate = p.getCommissionRate();
    r.workingHours = new ArrayList<>(
        profissionalWorkingHourRepository.listByProfessional(p.getTenantId(), p.getId()).stream()
            .map(this::toWorkingHoursDto)
            .toList());
    r.isActive = p.isActive();
    r.acceptsAppointments = p.isAcceptsAppointments();
    r.createdAt = p.getCreatedAt() != null ? p.getCreatedAt().toString() : null;
    return r;
  }

  private WorkingHoursDto toWorkingHoursDto(ProfissionalWorkingHour row) {
    WorkingHoursDto dto = new WorkingHoursDto();
    dto.dayOfWeek = row.getDayOfWeek();
    dto.startTime = row.getStartTime() != null ? row.getStartTime().toString() : null;
    dto.endTime = row.getEndTime() != null ? row.getEndTime().toString() : null;
    dto.isWorking = row.isWorking();
    return dto;
  }
}
