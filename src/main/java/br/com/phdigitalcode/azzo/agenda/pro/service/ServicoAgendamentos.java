package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ComandaDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoMetricaDiariaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentConflictDetailsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentConflictSummaryResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentCustomerNoteRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentCustomerNoteResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentCustomerNoteUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentDetailResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentManagementReportItemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentManagementReportResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentManagementReportSignalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentTimelineEventResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.ClientAppointmentHistoryItemDto;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.ClientAppointmentHistoryResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.NoShowGroupBy;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.NoShowGroupResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.NoShowReportPointResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.NoShowReportResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.PendingAttendanceResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentCustomerNote;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Comanda;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServiceCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.exception.AppointmentConflictException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.NotificacaoService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppAppointmentNotificationService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoQueryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentCustomerNoteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentDepositRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RelatorioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServiceCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransactionCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;

/**
 * Espelha {@code modules/scheduling/application/ServicoAgendamentos.java} (2.381 linhas no
 * original). Nucleo do dominio de agenda: CRUD, resolucao de conflito, transicao de status com os
 * efeitos financeiros (receita, comissao, comanda, sinal), presenca/no-show, notas operacionais,
 * timeline de auditoria e os relatorios gerencial e de no-show.
 *
 * <p>Diferencas estruturais em relacao ao original, todas ja adotadas nos demais modulos migrados:
 *
 * <ul>
 *   <li>{@code JsonWebToken} vira {@link AuthenticatedUser};
 *   <li>as consultas dinamicas/nativas do {@code AgendamentoRepository} Panache ficam em
 *       {@link AgendamentoQueryRepository};
 *   <li>{@code Profissional.workingHours} nao foi portado como associacao — as janelas vem de
 *       {@link ProfissionalWorkingHourRepository}, como ja faz o {@code AppointmentService};
 *   <li>{@code StreamingOutput} vira {@link StreamingResponseBody}, com as linhas do CSV
 *       materializadas <b>dentro</b> da transacao: o corpo do stream so roda depois que o
 *       controller retorna, com a sessao JPA ja fechada.
 * </ul>
 *
 * <p>Os dois metodos privados {@code registrarDespesaComissaoSeNecessario} e
 * {@code estornarDespesaComissaoSeNecessario} do original <b>nao</b> foram portados: no Quarkus
 * eles ja estao sem nenhum chamador (a comissao passou a ser registrada pelo
 * {@code CommissionService}), entao sao codigo morto sem efeito observavel.
 */
@Service
public class ServicoAgendamentos {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoAgendamentos.class);

  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

  private final AgendamentoRepository agendamentoRepository;
  private final AgendamentoQueryRepository agendamentoQueryRepository;
  private final AgendamentoItemRepository agendamentoItemRepository;
  private final AppointmentCustomerNoteRepository appointmentCustomerNoteRepository;
  private final AppointmentDepositRepository appointmentDepositRepository;
  private final ClienteRepository clienteRepository;
  private final ClienteStatsRepository clienteStatsRepository;
  private final ProfissionalRepository profissionalRepository;
  private final ProfissionalWorkingHourRepository profissionalWorkingHourRepository;
  private final ServicoRepository servicoRepository;
  private final ServiceCategoryRepository serviceCategoryRepository;
  private final TransacaoRepository transacaoRepository;
  private final TransactionCategoryRepository transactionCategoryRepository;
  private final ComandaRepository comandaRepository;
  private final RelatorioRepository relatorioRepository;
  private final AuditEventRepository auditEventRepository;
  private final UsuarioRepository usuarioRepository;

  private final CommissionService commissionService;
  private final AppointmentSettingsService appointmentSettingsService;
  private final TenantOperationalSettingsService tenantOperationalSettingsService;
  private final SpecialClosureService specialClosureService;
  private final ServicoComanda servicoComanda;
  private final NotificacaoService notificacaoService;
  private final WhatsAppAppointmentNotificationService whatsAppNotificationService;
  private final EstoqueMovimentacaoService estoqueMovimentacaoService;
  private final AuditService auditService;

  private final ObjectMapper objectMapper;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;

  public ServicoAgendamentos(
      AgendamentoRepository agendamentoRepository,
      AgendamentoQueryRepository agendamentoQueryRepository,
      AgendamentoItemRepository agendamentoItemRepository,
      AppointmentCustomerNoteRepository appointmentCustomerNoteRepository,
      AppointmentDepositRepository appointmentDepositRepository,
      ClienteRepository clienteRepository,
      ClienteStatsRepository clienteStatsRepository,
      ProfissionalRepository profissionalRepository,
      ProfissionalWorkingHourRepository profissionalWorkingHourRepository,
      ServicoRepository servicoRepository,
      ServiceCategoryRepository serviceCategoryRepository,
      TransacaoRepository transacaoRepository,
      TransactionCategoryRepository transactionCategoryRepository,
      ComandaRepository comandaRepository,
      RelatorioRepository relatorioRepository,
      AuditEventRepository auditEventRepository,
      UsuarioRepository usuarioRepository,
      CommissionService commissionService,
      AppointmentSettingsService appointmentSettingsService,
      TenantOperationalSettingsService tenantOperationalSettingsService,
      SpecialClosureService specialClosureService,
      ServicoComanda servicoComanda,
      NotificacaoService notificacaoService,
      WhatsAppAppointmentNotificationService whatsAppNotificationService,
      EstoqueMovimentacaoService estoqueMovimentacaoService,
      AuditService auditService,
      ObjectMapper objectMapper,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser) {
    this.agendamentoRepository = agendamentoRepository;
    this.agendamentoQueryRepository = agendamentoQueryRepository;
    this.agendamentoItemRepository = agendamentoItemRepository;
    this.appointmentCustomerNoteRepository = appointmentCustomerNoteRepository;
    this.appointmentDepositRepository = appointmentDepositRepository;
    this.clienteRepository = clienteRepository;
    this.clienteStatsRepository = clienteStatsRepository;
    this.profissionalRepository = profissionalRepository;
    this.profissionalWorkingHourRepository = profissionalWorkingHourRepository;
    this.servicoRepository = servicoRepository;
    this.serviceCategoryRepository = serviceCategoryRepository;
    this.transacaoRepository = transacaoRepository;
    this.transactionCategoryRepository = transactionCategoryRepository;
    this.comandaRepository = comandaRepository;
    this.relatorioRepository = relatorioRepository;
    this.auditEventRepository = auditEventRepository;
    this.usuarioRepository = usuarioRepository;
    this.commissionService = commissionService;
    this.appointmentSettingsService = appointmentSettingsService;
    this.tenantOperationalSettingsService = tenantOperationalSettingsService;
    this.specialClosureService = specialClosureService;
    this.servicoComanda = servicoComanda;
    this.notificacaoService = notificacaoService;
    this.whatsAppNotificationService = whatsAppNotificationService;
    this.estoqueMovimentacaoService = estoqueMovimentacaoService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
  }

  // ─── LEITURA ──────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<AgendamentoResponse> listar(LocalDate date, int page, int size) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int safeSize = Math.min(Math.max(size, 1), 500);
    int safePage = Math.max(page - 1, 0); // frontend envia 1-indexed, o repositorio e 0-indexed
    PageRequest pageable = PageRequest.of(safePage, safeSize);

    if (isProfessional()) {
      UUID userId = obterUserIdOuFalhar();
      Profissional profissional =
          profissionalRepository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
      if (profissional == null) return List.of();
      List<Agendamento> agendamentos =
          date != null
              ? agendamentoRepository.listByTenantAndProfessionalAndDate(
                  tenantId, profissional.getId(), date, pageable)
              : agendamentoRepository.listByTenantAndProfessional(
                  tenantId, profissional.getId(), pageable);
      return agendamentos.stream().map(this::toResponse).toList();
    }

    List<Agendamento> agendamentos =
        date != null
            ? agendamentoRepository.listByTenantAndDate(tenantId, date, pageable)
            : agendamentoRepository.listByTenant(tenantId, pageable);
    return agendamentos.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public AppointmentDetailResponse obterDetalhe(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Agendamento appointment =
        agendamentoRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento nao encontrado"));

    AppointmentDetailResponse response = new AppointmentDetailResponse();
    response.appointment = toResponse(appointment);
    response.careNotes =
        appointmentCustomerNoteRepository
            .findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, id)
            .stream()
            .map(this::toCustomerNoteResponse)
            .toList();
    response.timeline = montarTimelineAgendamento(tenantId, id);
    return response;
  }

  @Transactional(readOnly = true)
  public List<AgendamentoMetricaDiariaResponse> listarMetricasDiarias(Integer mes, Integer ano) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate hoje = LocalDate.now(ZONE_BR);

    int mesEfetivo = mes != null ? mes : hoje.getMonthValue();
    int anoEfetivo = ano != null ? ano : hoje.getYear();

    if (mesEfetivo < 1 || mesEfetivo > 12) {
      throw new IllegalArgumentException("Mes invalido. Informe valor entre 1 e 12");
    }

    List<Object[]> linhas;
    if (isProfessional()) {
      UUID userId = obterUserIdOuFalhar();
      Profissional profissional =
          profissionalRepository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
      if (profissional == null) return List.of();
      linhas =
          agendamentoQueryRepository.contarPorDiaNoMesProfissional(
              tenantId, profissional.getId(), mesEfetivo, anoEfetivo);
    } else {
      linhas = agendamentoQueryRepository.contarPorDiaNoMes(tenantId, mesEfetivo, anoEfetivo);
    }

    return linhas.stream().map(this::toMetricaDiariaResponse).toList();
  }

  // ─── ESCRITA ──────────────────────────────────────────────────────────────

  @Transactional
  public AgendamentoResponse criar(AgendamentoRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    Agendamento a = new Agendamento();
    a.setTenantId(tenantId);
    List<ResolvedAppointmentItem> resolvedItems = aplicar(req, a, tenantId);
    // Fix 3: serializa criacoes concorrentes para o mesmo profissional/data antes de checar
    // conflitos, fechando a janela de corrida entre o SELECT de conflitos e o INSERT do
    // agendamento (ver AgendamentoQueryRepository#lockProfessionalDateForWrite). Precisa estar
    // dentro deste @Transactional: pg_advisory_xact_lock e liberado no fim da transacao.
    agendamentoQueryRepository.lockProfessionalDateForWrite(
        tenantId, a.getProfessionalId(), a.getDate());
    AppointmentConflictResolution conflito = resolverConflitoCriacao(tenantId, a, req);

    agendamentoRepository.saveAndFlush(a);
    persistItems(a, resolvedItems);

    if (a.getStatus() == StatusAgendamento.COMPLETED) {
      registrarReceitaConclusaoSeNecessario(tenantId, a, null);
      commissionService.registerServiceCommissionsIfApplicable(
          tenantId, a.getId(), a.getProfessionalId(), a.getItems(), a.getDate());
    }

    notificacaoService.registrarCriacaoAgendamento(
        tenantId, a.getId(), a.getClientId(), "APPOINTMENT_CREATED");
    try {
      whatsAppNotificationService.sendConfirmation(tenantId, a);
    } catch (Exception e) {
      // nao bloqueia criacao
    }
    auditarAgendamento(
        tenantId,
        conflito.overrideApplied()
            ? "APPOINTMENT_CREATE_WITH_CONFLICT_OVERRIDE"
            : "APPOINTMENT_CREATE",
        null,
        snapshotAgendamento(a),
        a.getId(),
        conflito.auditMetadata());
    LOG.info(
        CorrelatedLogging.context(
            "Agendamento criado",
            "tenantId", tenantId,
            "appointmentId", a.getId(),
            "professionalId", a.getProfessionalId(),
            "clientId", a.getClientId(),
            "date", a.getDate(),
            "startTime", a.getStartTime(),
            "status", a.getStatus(),
            "conflictOverride", conflito.overrideApplied()));

    return toResponse(a);
  }

  @Transactional
  public AgendamentoResponse atualizarStatus(UUID id, String status) {
    return atualizarStatus(id, status, null, null);
  }

  @Transactional
  public AgendamentoResponse atualizarStatus(UUID id, String status, String paymentMethod) {
    return atualizarStatus(id, status, paymentMethod, null);
  }

  @Transactional
  public AgendamentoResponse atualizarStatus(
      UUID id, String status, String paymentMethod, String conclusionAction) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Agendamento a =
        agendamentoRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento nao encontrado"));
    Map<String, Object> before = snapshotAgendamento(a);

    StatusAgendamento statusAnterior = a.getStatus();
    StatusAgendamento novoStatus = StatusAgendamento.fromValue(status);
    StatusAgendamento.validarTransicao(statusAnterior, novoStatus);
    validarNoShowPermitido(a, novoStatus);
    validarNotaOperacionalObrigatoria(tenantId, a, novoStatus);
    if (statusAnterior == StatusAgendamento.COMPLETED && novoStatus != StatusAgendamento.COMPLETED) {
      validarComandaNaoFechadaParaSairDeCompleted(tenantId, a);
    }
    a.setStatus(novoStatus);

    if (statusAnterior != StatusAgendamento.COMPLETED && novoStatus == StatusAgendamento.COMPLETED) {
      if ("PAY_NOW".equals(conclusionAction)) {
        pagarComandaAgoraSeNecessario(tenantId, a, paymentMethod);
      }
      registrarReceitaConclusaoSeNecessario(tenantId, a, paymentMethod);
      commissionService.registerServiceCommissionsIfApplicable(
          tenantId, a.getId(), a.getProfessionalId(), a.getItems(), a.getDate());
      List<UUID> serviceIds =
          a.getItems() == null
              ? List.of()
              : a.getItems().stream()
                  .filter(i -> i.getServiceId() != null)
                  .map(AgendamentoItem::getServiceId)
                  .toList();
      estoqueMovimentacaoService.consumirInsumosPorAgendamento(tenantId, a.getId(), serviceIds);
    } else if (statusAnterior == StatusAgendamento.COMPLETED
        && novoStatus != StatusAgendamento.COMPLETED) {
      estornarReceitaConclusaoSeNecessario(tenantId, a);
      commissionService.reverseServiceCommissionIfApplicable(
          tenantId, a.getId(), "Agendamento saiu do status COMPLETED");
    }
    UUID comandaAbertaId = null;
    if (statusAnterior != StatusAgendamento.IN_PROGRESS
        && novoStatus == StatusAgendamento.IN_PROGRESS) {
      comandaAbertaId = abrirComandaAutomaticaSeNecessario(tenantId, a);
    }
    if (novoStatus == StatusAgendamento.CANCELLED || novoStatus == StatusAgendamento.NO_SHOW) {
      cancelarComandaAbertaSeNecessario(tenantId, a);
    }
    if (novoStatus == StatusAgendamento.CANCELLED) {
      try {
        whatsAppNotificationService.sendCancellation(tenantId, a);
      } catch (Exception e) {
        // nao bloqueia
      }
    }
    if (novoStatus == StatusAgendamento.NO_SHOW) {
      try {
        whatsAppNotificationService.sendNoShow(a);
      } catch (Exception e) {
        // nao bloqueia
      }
    }
    agendamentoRepository.save(a);
    auditarAgendamento(
        tenantId, "APPOINTMENT_STATUS_UPDATE", before, snapshotAgendamento(a), a.getId());
    LOG.info(
        CorrelatedLogging.context(
            "Status de agendamento atualizado",
            "tenantId", tenantId,
            "appointmentId", a.getId(),
            "fromStatus", statusAnterior,
            "toStatus", novoStatus,
            "paymentMethod", paymentMethod));

    AgendamentoResponse resposta = toResponse(a);
    if (comandaAbertaId != null) resposta.comandaId = comandaAbertaId.toString();
    return resposta;
  }

  @Transactional
  public AgendamentoResponse realocarProfissional(UUID id, UUID novoProfessionalId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Agendamento agendamento =
        agendamentoRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento nao encontrado"));
    Map<String, Object> before = snapshotAgendamento(agendamento);
    if (agendamento.getStatus() == StatusAgendamento.COMPLETED
        || agendamento.getStatus() == StatusAgendamento.CANCELLED) {
      throw new IllegalArgumentException(
          "Nao e permitido realocar agendamento concluido ou cancelado");
    }
    if (novoProfessionalId == null) throw new IllegalArgumentException("Profissional invalido");
    if (novoProfessionalId.equals(agendamento.getProfessionalId())) return toResponse(agendamento);

    Profissional novoProfissional =
        profissionalRepository
            .findByIdAndTenantIdAndIsActiveTrue(novoProfessionalId, tenantId)
            .orElse(null);
    if (novoProfissional == null) {
      throw new IllegalArgumentException("Profissional nao encontrado ou inativo");
    }

    List<Servico> services =
        agendamento.getItems().stream()
            .map(
                item ->
                    item.getService() != null
                        ? item.getService()
                        : servicoRepository
                            .findByIdAndTenantId(item.getServiceId(), tenantId)
                            .orElse(null))
            .filter(Objects::nonNull)
            .toList();
    if (services.isEmpty()) throw new IllegalArgumentException("Servico nao encontrado");
    boolean profissionalApto =
        services.stream()
            .allMatch(
                servico ->
                    servico.getProfissionais() == null
                        || servico.getProfissionais().isEmpty()
                        || servico.getProfissionais().stream()
                            .anyMatch(p -> p.getId().equals(novoProfessionalId)));
    if (!profissionalApto) {
      throw new IllegalArgumentException(
          "Profissional nao atende um ou mais servicos do agendamento");
    }

    validarConflitoHorarioComNovoProfissional(tenantId, agendamento, novoProfessionalId);
    agendamento.setProfessionalId(novoProfessionalId);

    // Ramo inalcancavel pela guarda de status logo acima (COMPLETED ja foi recusado) — mantido
    // por fidelidade ao original, que tambem o mantem.
    if (agendamento.getStatus() == StatusAgendamento.COMPLETED) {
      commissionService.reverseServiceCommissionIfApplicable(
          tenantId,
          agendamento.getId(),
          "Comissao anterior revertida por realocacao de profissional");
      commissionService.registerServiceCommissionsIfApplicable(
          tenantId,
          agendamento.getId(),
          agendamento.getProfessionalId(),
          agendamento.getItems(),
          agendamento.getDate());
    }
    agendamentoRepository.save(agendamento);
    auditarAgendamento(
        tenantId,
        "APPOINTMENT_REALLOCATE_PROFESSIONAL",
        before,
        snapshotAgendamento(agendamento),
        agendamento.getId());
    LOG.info(
        CorrelatedLogging.context(
            "Profissional do agendamento realocado",
            "tenantId", tenantId,
            "appointmentId", agendamento.getId(),
            "newProfessionalId", novoProfessionalId,
            "date", agendamento.getDate(),
            "startTime", agendamento.getStartTime()));

    return toResponse(agendamento);
  }

  @Transactional
  public AgendamentoResponse atualizar(UUID id, AppointmentUpdateRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Agendamento a =
        agendamentoRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento nao encontrado"));
    if (a.getStatus() == StatusAgendamento.COMPLETED
        || a.getStatus() == StatusAgendamento.CANCELLED) {
      throw new IllegalArgumentException(
          "Nao e permitido editar agendamento concluido ou cancelado");
    }
    Map<String, Object> before = snapshotAgendamento(a);

    if (req.notes != null) a.setNotes(req.notes.isBlank() ? null : req.notes.trim());

    if (req.professionalId != null && !req.professionalId.isBlank()) {
      UUID novoProfId = UUID.fromString(req.professionalId);
      if (!novoProfId.equals(a.getProfessionalId())) {
        Profissional prof =
            profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(novoProfId, tenantId).orElse(null);
        if (prof == null) throw new IllegalArgumentException("Profissional nao encontrado ou inativo");
        a.setProfessionalId(novoProfId);
      }
    }

    boolean dateOrTimeChanged = false;
    if (req.date != null && !req.date.isBlank()) {
      LocalDate novaData = DataUtil.parseDataISO(req.date);
      if (novaData == null) throw new IllegalArgumentException("Data invalida");
      if (tenantOperationalSettingsService.isClosedOnSpecialDate(tenantId, novaData)) {
        throw new IllegalArgumentException("Salao fechado na data informada");
      }
      if (!novaData.equals(a.getDate())) {
        a.setDate(novaData);
        dateOrTimeChanged = true;
      }
    }
    if (req.startTime != null && !req.startTime.isBlank()) {
      LocalTime novoInicio = parseTimeOrThrow(req.startTime);
      String formatted = formatTime(novoInicio);
      if (!formatted.equals(a.getStartTime())) {
        a.setStartTime(formatted);
        dateOrTimeChanged = true;
      }
    }

    if (req.items != null && !req.items.isEmpty()) {
      List<ResolvedAppointmentItem> resolvedItems =
          resolveRequestedItems(buildRequestForItems(req, a), tenantId, a.getProfessionalId());
      long totalDuration =
          resolvedItems.stream()
              .mapToLong(item -> (long) item.service().getDuration() * item.quantity())
              .sum();
      LocalTime inicio = parseTimeOrThrow(a.getStartTime());
      a.setEndTime(formatTime(inicio.plusMinutes(totalDuration)));
      dateOrTimeChanged = true;
      // orphanRemoval na colecao apaga os itens antigos; o flush explicito e obrigatorio porque
      // persistItems reconsulta appointment_items logo em seguida (guarda de idempotencia).
      a.getItems().clear();
      agendamentoRepository.saveAndFlush(a);
      persistItems(a, resolvedItems);
    } else if (dateOrTimeChanged && !a.getItems().isEmpty()) {
      long totalDuration =
          a.getItems().stream()
              .mapToLong(
                  item -> {
                    Servico s =
                        item.getService() != null
                            ? item.getService()
                            : servicoRepository
                                .findByIdAndTenantId(item.getServiceId(), tenantId)
                                .orElse(null);
                    return s != null ? (long) s.getDuration() * item.getQuantity() : 0L;
                  })
              .sum();
      if (totalDuration > 0) {
        LocalTime inicio = parseTimeOrThrow(a.getStartTime());
        a.setEndTime(formatTime(inicio.plusMinutes(totalDuration)));
      }
    }

    if (dateOrTimeChanged) {
      LocalTime editStart = parseTimeOrThrow(a.getStartTime());
      LocalTime editEnd = parseTimeOrThrow(a.getEndTime());
      if (!tenantOperationalSettingsService.isBusinessOpenAt(
          tenantId, a.getDate(), editStart, editEnd)) {
        throw new IllegalArgumentException(
            "Salao fechado ou fora do horario de funcionamento informado");
      }
      if (specialClosureService.isClosedAt(
          tenantId, a.getProfessionalId(), a.getDate(), editStart, editEnd)) {
        throw new IllegalArgumentException(
            "Salao ou profissional com fechamento especial no horario informado");
      }
      Profissional profParaValidar =
          profissionalRepository
              .findByIdAndTenantIdAndIsActiveTrue(a.getProfessionalId(), tenantId)
              .orElse(null);
      if (profParaValidar != null
          && !isProfessionalAvailableAt(profParaValidar, a.getDate(), editStart, editEnd)) {
        throw new IllegalArgumentException("O profissional nao atende neste horario");
      }
      validarConflitoEdicao(tenantId, a, req);
    }

    agendamentoRepository.save(a);
    auditarAgendamento(tenantId, "APPOINTMENT_UPDATE", before, snapshotAgendamento(a), a.getId());
    LOG.info(
        CorrelatedLogging.context(
            "Agendamento atualizado",
            "tenantId", tenantId,
            "appointmentId", a.getId(),
            "professionalId", a.getProfessionalId(),
            "date", a.getDate(),
            "startTime", a.getStartTime(),
            "endTime", a.getEndTime(),
            "dateOrTimeChanged", dateOrTimeChanged));
    return toResponse(a);
  }

  @Transactional
  public void deletar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Agendamento before = agendamentoRepository.findByIdAndTenantId(id, tenantId).orElse(null);
    if (before == null) throw new IllegalArgumentException("Agendamento nao encontrado");
    Map<String, Object> snapshot = snapshotAgendamento(before);
    agendamentoRepository.delete(before);
    auditarAgendamento(tenantId, "APPOINTMENT_DELETE", snapshot, null, id);
    LOG.info(
        CorrelatedLogging.context(
            "Agendamento excluido",
            "tenantId", tenantId,
            "appointmentId", id,
            "professionalId", before.getProfessionalId(),
            "clientId", before.getClientId()));
  }

  // ─── PRESENCA / NO-SHOW OPERACIONAL ───────────────────────────────────────

  @Transactional(readOnly = true)
  public List<PendingAttendanceResponse> listarPendentesConfirmacaoPresenca() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate hoje = LocalDate.now(ZONE_BR);
    LocalTime agora = LocalTime.now(ZONE_BR);
    LocalTime limiteMinimo = agora.minusMinutes(5);
    LocalTime limiteMaximo = agora.minusMinutes(360);

    List<StatusAgendamento> statusAlvo =
        List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED);
    List<Agendamento> candidatos =
        agendamentoRepository.listPendingAttendanceCandidates(
            tenantId, statusAlvo, hoje, PageRequest.of(0, 200));

    UUID professionalIdFiltro = null;
    if (isProfessional()) {
      UUID userId = obterUserIdOuFalhar();
      Profissional prof =
          profissionalRepository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
      if (prof != null) professionalIdFiltro = prof.getId();
    }

    final UUID filtro = professionalIdFiltro;
    List<Agendamento> resultado =
        candidatos.stream()
            .filter(
                a -> {
                  if (a.getStartTime() == null || a.getStartTime().isBlank()) return false;
                  try {
                    LocalTime inicio = LocalTime.parse(a.getStartTime());
                    // inicio ocorreu ha pelo menos 5 min e ha no maximo 6h (janela de atuacao)
                    return !a.getDate().isBefore(hoje)
                        ? inicio.isBefore(limiteMinimo) && inicio.isAfter(limiteMaximo)
                        : true; // dias anteriores: todos passaram da janela de 5min
                  } catch (Exception e) {
                    return false;
                  }
                })
            .filter(a -> filtro == null || filtro.equals(a.getProfessionalId()))
            .toList();

    return resultado.stream().map(a -> toPendingAttendanceResponse(tenantId, a)).toList();
  }

  @Transactional
  public AgendamentoResponse registrarPresenca(UUID id, boolean attended) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Agendamento a =
        agendamentoRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento nao encontrado"));

    if (isProfessional()) {
      UUID userId = obterUserIdOuFalhar();
      Profissional prof =
          profissionalRepository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
      if (prof == null || !prof.getId().equals(a.getProfessionalId())) {
        throw new IllegalArgumentException(
            "Acesso negado: agendamento nao pertence ao profissional");
      }
    }

    Map<String, Object> before = snapshotAgendamento(a);

    if (attended) {
      if (a.getStatus() == StatusAgendamento.PENDING) {
        StatusAgendamento.validarTransicao(a.getStatus(), StatusAgendamento.CONFIRMED);
        a.setStatus(StatusAgendamento.CONFIRMED);
      }
      // se ja CONFIRMED, nenhuma acao de status
    } else {
      StatusAgendamento.validarTransicao(a.getStatus(), StatusAgendamento.NO_SHOW);
      validarNoShowPermitido(a, StatusAgendamento.NO_SHOW);
      a.setStatus(StatusAgendamento.NO_SHOW);
      try {
        whatsAppNotificationService.sendNoShow(a);
      } catch (Exception e) {
        // nao bloqueia
      }
    }

    agendamentoRepository.save(a);
    auditarAgendamento(
        tenantId, "APPOINTMENT_ATTENDANCE_MARK", before, snapshotAgendamento(a), a.getId());
    LOG.info(
        CorrelatedLogging.context(
            "Presenca de agendamento registrada",
            "tenantId", tenantId,
            "appointmentId", a.getId(),
            "attended", attended,
            "status", a.getStatus()));
    return toResponse(a);
  }

  private PendingAttendanceResponse toPendingAttendanceResponse(UUID tenantId, Agendamento a) {
    PendingAttendanceResponse r = new PendingAttendanceResponse();
    r.id = a.getId().toString();
    r.date = a.getDate() != null ? a.getDate().toString() : null;
    r.startTime = normalizeTimeOrOriginal(a.getStartTime());
    r.endTime = normalizeTimeOrOriginal(a.getEndTime());
    r.status = a.getStatus() != null ? a.getStatus().name() : null;

    if (a.getClientId() != null) {
      Cliente cliente = clienteRepository.findByIdAndTenantId(a.getClientId(), tenantId).orElse(null);
      if (cliente != null) {
        r.clientName = cliente.getName();
        r.clientPhone = mascararTelefone(cliente.getPhone());
      }
    }

    if (a.getProfessionalId() != null) {
      Profissional prof =
          profissionalRepository.findByIdAndTenantId(a.getProfessionalId(), tenantId).orElse(null);
      if (prof != null) r.professionalName = prof.getName();
    }

    if (a.getItems() != null) {
      r.serviceNames =
          a.getItems().stream()
              .filter(item -> item.getService() != null)
              .map(item -> item.getService().getName())
              .filter(Objects::nonNull)
              .toList();
    } else {
      r.serviceNames = List.of();
    }

    return r;
  }

  private String mascararTelefone(String phone) {
    if (phone == null || phone.isBlank()) return null;
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() < 8) return "(**) *****-****";
    String ultimos4 = digits.substring(digits.length() - 4);
    return "(**) *****-" + ultimos4;
  }

  // ─── NOTAS OPERACIONAIS DO CLIENTE ────────────────────────────────────────

  @Transactional
  public void deletarNotaCliente(UUID appointmentId, UUID noteId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Agendamento appointment =
        agendamentoRepository
            .findByIdAndTenantId(appointmentId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento nao encontrado"));
    AppointmentCustomerNote note =
        appointmentCustomerNoteRepository
            .findByIdAndTenantIdAndAppointmentId(noteId, tenantId, appointmentId)
            .orElseThrow(() -> new IllegalArgumentException("Nota operacional nao encontrada"));
    Map<String, Object> before = snapshotCustomerNote(note);
    appointmentCustomerNoteRepository.delete(note);
    auditarAgendamento(
        tenantId, "APPOINTMENT_CUSTOMER_NOTE_DELETE", before, null, appointment.getId());
  }

  @Transactional
  public AppointmentCustomerNoteResponse adicionarNotaCliente(
      UUID appointmentId, AppointmentCustomerNoteRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Agendamento appointment =
        agendamentoRepository
            .findByIdAndTenantId(appointmentId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento nao encontrado"));
    validarConteudoNota(request);

    AppointmentCustomerNote note = new AppointmentCustomerNote();
    note.setTenantId(tenantId);
    note.setAppointmentId(appointment.getId());
    note.setClientId(appointment.getClientId());
    note.setRecordedByUserId(obterUserIdOptional());
    note.setServiceExecutionNotes(normalizeNoteText(request.serviceExecutionNotes));
    note.setClientFeedbackNotes(normalizeNoteText(request.clientFeedbackNotes));
    note.setInternalFollowupNotes(normalizeNoteText(request.internalFollowupNotes));
    appointmentCustomerNoteRepository.save(note);

    auditarAgendamento(
        tenantId,
        "APPOINTMENT_CUSTOMER_NOTE_CREATE",
        null,
        snapshotCustomerNote(note),
        appointment.getId());

    return toCustomerNoteResponse(note);
  }

  @Transactional
  public AppointmentCustomerNoteResponse atualizarNotaCliente(
      UUID appointmentId, UUID noteId, AppointmentCustomerNoteUpdateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Agendamento appointment =
        agendamentoRepository
            .findByIdAndTenantId(appointmentId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento nao encontrado"));
    AppointmentCustomerNote note =
        appointmentCustomerNoteRepository
            .findByIdAndTenantIdAndAppointmentId(noteId, tenantId, appointmentId)
            .orElseThrow(() -> new IllegalArgumentException("Nota operacional nao encontrada"));
    validarConteudoNota(request);

    Map<String, Object> before = snapshotCustomerNote(note);
    note.setServiceExecutionNotes(normalizeNoteText(request.serviceExecutionNotes));
    note.setClientFeedbackNotes(normalizeNoteText(request.clientFeedbackNotes));
    note.setInternalFollowupNotes(normalizeNoteText(request.internalFollowupNotes));
    appointmentCustomerNoteRepository.save(note);

    auditarAgendamento(
        tenantId,
        "APPOINTMENT_CUSTOMER_NOTE_UPDATE",
        before,
        snapshotCustomerNote(note),
        appointment.getId());

    return toCustomerNoteResponse(note);
  }

  // ─── HISTORICO DO CLIENTE ─────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public ClientAppointmentHistoryResponse obterHistoricoCliente(
      UUID clientId, Integer page, Integer size, LocalDate from, LocalDate to, UUID serviceId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente client =
        clienteRepository
            .findByIdAndTenantId(clientId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Cliente nao encontrado"));

    int safePage = page == null || page < 0 ? 0 : page;
    int safeSize = size == null || size <= 0 ? 20 : Math.min(size, 100);
    LocalDate effectiveFrom = from;
    LocalDate effectiveTo = to;

    if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
      throw new IllegalArgumentException("Periodo de filtro invalido");
    }

    long total =
        agendamentoQueryRepository.contarHistoricoClienteFiltrado(
            tenantId, clientId, effectiveFrom, effectiveTo, serviceId);
    List<Agendamento> appointments =
        agendamentoQueryRepository.listarHistoricoClienteFiltrado(
            tenantId, clientId, effectiveFrom, effectiveTo, serviceId, safePage, safeSize);

    List<UUID> appointmentIds = appointments.stream().map(Agendamento::getId).toList();
    Map<UUID, List<AppointmentCustomerNoteResponse>> notesByAppointment =
        appointmentCustomerNoteRepository
            .listByTenantAndAppointmentIds(tenantId, appointmentIds)
            .stream()
            .map(this::toCustomerNoteResponse)
            .collect(
                Collectors.groupingBy(
                    note -> UUID.fromString(note.appointmentId),
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> {
                          list.sort(
                              Comparator.comparing(
                                  (AppointmentCustomerNoteResponse item) -> item.recordedAt,
                                  Comparator.nullsLast(Comparator.reverseOrder())));
                          return list;
                        })));

    ClientAppointmentHistoryResponse response = new ClientAppointmentHistoryResponse();
    response.clientId = client.getId().toString();
    response.page = safePage;
    response.size = safeSize;
    response.totalItems = total;

    for (Agendamento appointment : appointments) {
      ClientAppointmentHistoryItemDto item = new ClientAppointmentHistoryItemDto();
      item.appointmentId = appointment.getId() != null ? appointment.getId().toString() : null;
      item.date = appointment.getDate() != null ? appointment.getDate().toString() : null;
      item.status = appointment.getStatus() != null ? appointment.getStatus().name() : null;
      item.professionalId =
          appointment.getProfessionalId() != null
              ? appointment.getProfessionalId().toString()
              : null;
      item.notes = appointment.getNotes();
      Profissional professional =
          profissionalRepository
              .findByIdAndTenantId(appointment.getProfessionalId(), tenantId)
              .orElse(null);
      item.professionalName = professional != null ? professional.getName() : null;
      item.services =
          appointment.getItems() == null
              ? List.of()
              : appointment.getItems().stream()
                  .sorted(
                      Comparator.comparing(
                          AgendamentoItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                  .map(this::toItemResponse)
                  .toList();
      item.careNotes =
          new ArrayList<>(notesByAppointment.getOrDefault(appointment.getId(), Collections.emptyList()));
      response.items.add(item);
    }

    return response;
  }

  // ─── RELATORIO GERENCIAL ──────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public AppointmentManagementReportResponse listarRelatorioGerencial(
      LocalDate from, LocalDate to, UUID professionalId, UUID serviceId, String statusRaw, Integer limit) {
    return listarRelatorioGerencial(from, to, professionalId, serviceId, statusRaw, limit, null, null);
  }

  @Transactional(readOnly = true)
  public AppointmentManagementReportResponse listarRelatorioGerencial(
      LocalDate from,
      LocalDate to,
      UUID professionalId,
      UUID serviceId,
      String statusRaw,
      Integer limit,
      Integer page,
      Integer pageSize) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate[] period = resolveReportPeriod(from, to);
    LocalDate effectiveFrom = period[0];
    LocalDate effectiveTo = period[1];
    validarPeriodo(effectiveFrom, effectiveTo);

    String status = parseAppointmentStatusDescription(statusRaw);
    int safeLimit = limit == null ? 50 : Math.min(Math.max(limit, 1), 200);
    UUID effectiveProfessionalId = resolveProfessionalFilter(tenantId, professionalId);

    RelatorioRepository.AppointmentManagementSummaryRow summary =
        relatorioRepository.obterResumoRelatorioAgendamentos(
            tenantId, effectiveFrom, effectiveTo, effectiveProfessionalId, serviceId, status);

    AppointmentManagementReportResponse response = new AppointmentManagementReportResponse();
    response.startDate = effectiveFrom.toString();
    response.endDate = effectiveTo.toString();
    Instant lastRefreshAt = relatorioRepository.findLastRefreshAt("mv_relatorio_agendamentos");
    response.lastUpdatedAt = lastRefreshAt != null ? lastRefreshAt.toString() : null;
    response.totalAppointments = summary.totalAppointments;
    response.totalConfirmed = summary.totalConfirmed;
    response.totalPending = summary.totalPending;
    response.totalCancelled = summary.totalCancelled;
    response.totalNoShow = summary.totalNoShow;
    response.totalCompleted = summary.totalCompleted;
    response.totalRevenue = summary.totalRevenue;
    response.totalGapOpportunities = summary.totalGapOpportunities;
    response.totalUnconfirmed = summary.totalUnconfirmed;
    response.totalAbandonmentSignalDays = summary.totalAbandonmentSignalDays;
    response.occupancyRate =
        summary.totalAppointments <= 0
            ? 0.0
            : ((summary.totalConfirmed + summary.totalCompleted) * 100.0) / summary.totalAppointments;
    response.cancellationRate =
        summary.totalAppointments <= 0
            ? 0.0
            : (summary.totalCancelled * 100.0) / summary.totalAppointments;
    response.noShowRate =
        summary.totalAppointments <= 0
            ? 0.0
            : (summary.totalNoShow * 100.0) / summary.totalAppointments;
    response.limit = safeLimit;
    response.totalItems = summary.totalAppointments;
    if (page != null && pageSize != null) {
      int safePageSize = Math.min(Math.max(pageSize, 1), 100);
      int safePage = Math.max(page, 0);
      List<RelatorioRepository.AppointmentManagementItemRow> pageRows =
          relatorioRepository.listarRelatorioAgendamentosPaginado(
              tenantId,
              effectiveFrom,
              effectiveTo,
              effectiveProfessionalId,
              serviceId,
              status,
              safePage,
              safePageSize);
      response.hasMore = pageRows.size() > safePageSize;
      response.page = safePage;
      response.pageSize = safePageSize;
      response.items =
          pageRows.stream()
              .limit(safePageSize)
              .map(this::toAppointmentManagementReportItemResponse)
              .toList();
    } else {
      response.hasMore = false;
      response.page = 0;
      response.pageSize = safeLimit;
      response.items =
          relatorioRepository
              .listarRelatorioAgendamentos(
                  tenantId, effectiveFrom, effectiveTo, effectiveProfessionalId, serviceId, status, safeLimit)
              .stream()
              .map(this::toAppointmentManagementReportItemResponse)
              .toList();
    }
    response.alerts = buildAppointmentManagementAlerts(summary, response);
    response.opportunities = buildAppointmentManagementOpportunities(summary);
    return response;
  }

  /**
   * As linhas do CSV sao geradas ainda dentro desta transacao — o {@link StreamingResponseBody} so
   * e executado depois que o controller retorna, quando a sessao JPA ja esta fechada.
   */
  @Transactional(readOnly = true)
  public StreamingResponseBody exportarRelatorioGerencialCsv(
      LocalDate from, LocalDate to, UUID professionalId, UUID serviceId, String statusRaw, Integer limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate[] period = resolveReportPeriod(from, to);
    LocalDate effectiveFrom = period[0];
    LocalDate effectiveTo = period[1];
    validarPeriodo(effectiveFrom, effectiveTo);

    String status = parseAppointmentStatusDescription(statusRaw);
    int safeLimit = limit == null ? 1000 : Math.min(Math.max(limit, 1), 2000);
    UUID effectiveProfessionalId = resolveProfessionalFilter(tenantId, professionalId);

    List<String> csvLines =
        relatorioRepository
            .listarRelatorioAgendamentos(
                tenantId, effectiveFrom, effectiveTo, effectiveProfessionalId, serviceId, status, safeLimit)
            .stream()
            .map(this::toAppointmentManagementCsvLine)
            .toList();

    return output -> {
      StringBuilder csv = new StringBuilder();
      csv.append((char) 0xFEFF);
      csv.append(
          "data,horario_inicio,horario_fim,cliente,servico,profissional,status,origem,valor,horario_vago,nao_confirmado,abandono_fluxo\n");
      for (String line : csvLines) {
        csv.append(line).append('\n');
      }
      output.write(csv.toString().getBytes(StandardCharsets.UTF_8));
    };
  }

  // ─── RELATORIO DE NO-SHOW ─────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public NoShowReportResponse listarNoShows(
      UUID afterId,
      Integer limit,
      LocalDate from,
      LocalDate to,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery,
      String groupByRaw) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate[] period = resolveNoShowPeriod(from, to);
    LocalDate effectiveFrom = period[0];
    LocalDate effectiveTo = period[1];
    validarPeriodo(effectiveFrom, effectiveTo);
    NoShowGroupBy groupBy = NoShowGroupBy.fromValue(groupByRaw);

    UUID effectiveProfessionalId = resolveProfessionalFilter(tenantId, professionalId);
    // afterId/limit continuam aceitos por compatibilidade com chamadas legadas; o relatorio
    // passou a ser agregado e nao usa mais cursor.

    long daysInPeriod = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1L;
    LocalDate previousEnd = effectiveFrom.minusDays(1);
    LocalDate previousStart = previousEnd.minusDays(daysInPeriod - 1L);
    LocalDate lastSevenDaysStart =
        effectiveFrom.isAfter(effectiveTo.minusDays(6)) ? effectiveFrom : effectiveTo.minusDays(6);

    RelatorioRepository.NoShowSummaryRow summary =
        relatorioRepository.obterResumoNoShowFiltrado(
            tenantId,
            effectiveFrom,
            effectiveTo,
            previousStart,
            previousEnd,
            lastSevenDaysStart,
            effectiveProfessionalId,
            serviceId,
            clientIds,
            clientQuery);

    NoShowReportResponse response = new NoShowReportResponse();
    response.startDate = effectiveFrom.toString();
    response.endDate = effectiveTo.toString();
    response.groupBy = groupBy.name();
    Instant lastRefreshAt = relatorioRepository.findLastRefreshAt("mv_no_show_appointments");
    response.lastUpdatedAt = lastRefreshAt != null ? lastRefreshAt.toString() : null;
    response.totalNoShows = summary.totalNoShows;
    response.previousPeriodNoShows = summary.previousPeriodNoShows;
    response.lastSevenDaysNoShows = summary.lastSevenDaysNoShows;
    response.completedAppointments = summary.completedAppointments;
    response.revenueAtRisk = summary.revenueAtRisk;
    int denominator = summary.totalNoShows + summary.completedAppointments;
    response.noShowRate = denominator <= 0 ? 0.0 : (summary.totalNoShows * 100.0) / denominator;
    response.limit = limit == null ? 0 : limit;
    response.afterId = afterId != null ? afterId.toString() : null;
    response.nextAfterId = null;
    response.hasMore = false;
    response.items = List.of();
    response.points =
        groupBy == NoShowGroupBy.DAY
            ? relatorioRepository
                .listarSerieNoShowDiaria(
                    tenantId,
                    effectiveFrom,
                    effectiveTo,
                    effectiveProfessionalId,
                    serviceId,
                    clientIds,
                    clientQuery)
                .stream()
                .map(this::toNoShowReportPointResponse)
                .toList()
            : List.of();
    response.groups =
        listarGruposNoShow(
            tenantId,
            effectiveFrom,
            effectiveTo,
            effectiveProfessionalId,
            serviceId,
            clientIds,
            clientQuery,
            groupBy);
    response.totalItems = response.groups.size();
    return response;
  }

  /** Ver a nota de materializacao em {@link #exportarRelatorioGerencialCsv}. */
  @Transactional(readOnly = true)
  public StreamingResponseBody exportarNoShowsCsv(
      LocalDate from,
      LocalDate to,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery,
      String groupByRaw) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate[] period = resolveNoShowPeriod(from, to);
    LocalDate effectiveFrom = period[0];
    LocalDate effectiveTo = period[1];
    validarPeriodo(effectiveFrom, effectiveTo);
    NoShowGroupBy groupBy = NoShowGroupBy.fromValue(groupByRaw);
    UUID effectiveProfessionalId = resolveProfessionalFilter(tenantId, professionalId);

    List<String> csvLines =
        listarGruposNoShow(
                tenantId,
                effectiveFrom,
                effectiveTo,
                effectiveProfessionalId,
                serviceId,
                clientIds,
                clientQuery,
                groupBy)
            .stream()
            .map(
                item ->
                    csvCell(item.label) + "," + item.totalNoShows + "," + item.revenueAtRisk.toPlainString())
            .toList();

    boolean daily = groupBy == NoShowGroupBy.DAY;
    return output -> {
      StringBuilder csv = new StringBuilder();
      csv.append((char) 0xFEFF);
      csv.append(daily ? "data,total_no_shows,receita_em_risco\n" : "grupo,total_no_shows,receita_em_risco\n");
      for (String line : csvLines) {
        csv.append(line).append('\n');
      }
      output.write(csv.toString().getBytes(StandardCharsets.UTF_8));
    };
  }

  // ─── AUDITORIA / TIMELINE ─────────────────────────────────────────────────

  private void auditarAgendamento(
      UUID tenantId, String action, Object before, Object after, UUID entityId) {
    auditarAgendamento(tenantId, action, before, after, entityId, null);
  }

  private void auditarAgendamento(
      UUID tenantId, String action, Object before, Object after, UUID entityId, Object metadata) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = obterUserIdOptional();
      command.actorRole = obterRoleOptional();
      command.module = AuditConstants.Module.APPOINTMENT;
      command.action = action;
      command.entityType = "APPOINTMENT";
      command.entityId = entityId != null ? entityId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      command.metadata = metadata;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve quebrar fluxo principal.
    }
  }

  private List<AppointmentTimelineEventResponse> montarTimelineAgendamento(
      UUID tenantId, UUID appointmentId) {
    List<AuditEvent> events =
        auditEventRepository.listByTenantAndEntity(
            tenantId, "APPOINTMENT", appointmentId != null ? appointmentId.toString() : null, 100);
    if (events.isEmpty()) return List.of();

    Map<UUID, String> actorNamesById =
        usuarioRepository.mapNamesByTenantAndIds(
            tenantId,
            events.stream()
                .map(AuditEvent::getActorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

    return events.stream().map(event -> toTimelineEventResponse(event, actorNamesById)).toList();
  }

  private Map<String, Object> snapshotAgendamento(Agendamento a) {
    if (a == null) return null;
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("id", a.getId() != null ? a.getId().toString() : null);
    snapshot.put("tenantId", a.getTenantId() != null ? a.getTenantId().toString() : null);
    snapshot.put("clientId", a.getClientId() != null ? a.getClientId().toString() : null);
    snapshot.put(
        "professionalId", a.getProfessionalId() != null ? a.getProfessionalId().toString() : null);
    UUID primaryServiceId = a.resolvePrimaryServiceId();
    snapshot.put("serviceId", primaryServiceId != null ? primaryServiceId.toString() : null);
    snapshot.put("date", a.getDate() != null ? a.getDate().toString() : null);
    snapshot.put("startTime", a.getStartTime());
    snapshot.put("endTime", a.getEndTime());
    snapshot.put("status", a.getStatus() != null ? a.getStatus().name() : null);
    snapshot.put("notes", a.getNotes());
    snapshot.put("totalPrice", a.resolveEffectiveTotalPrice());
    return snapshot;
  }

  private AppointmentTimelineEventResponse toTimelineEventResponse(
      AuditEvent event, Map<UUID, String> actorNamesById) {
    AppointmentTimelineEventResponse response = new AppointmentTimelineEventResponse();
    response.eventId = event.getId() != null ? event.getId().toString() : null;
    response.action = event.getAction();
    response.actionLabel = translateAppointmentAction(event.getAction());
    response.actorUserId = event.getActorUserId() != null ? event.getActorUserId().toString() : null;
    response.actorName =
        event.getActorUserId() != null ? actorNamesById.get(event.getActorUserId()) : null;
    response.actorRole = event.getActorRole();
    response.status = event.getStatus();
    response.sourceChannel = event.getSourceChannel();
    response.createdAt = event.getCreatedAt() != null ? event.getCreatedAt().toString() : null;
    response.changedFields = parseStringListSafely(event.getChangedFieldsJson());
    response.before = parseJsonSafely(event.getBeforeJson());
    response.after = parseJsonSafely(event.getAfterJson());
    response.metadata = parseJsonSafely(event.getMetadataJson());
    return response;
  }

  // ─── MAPEAMENTO DE RELATORIO ──────────────────────────────────────────────

  private AppointmentManagementReportItemResponse toAppointmentManagementReportItemResponse(
      RelatorioRepository.AppointmentManagementItemRow row) {
    AppointmentManagementReportItemResponse response = new AppointmentManagementReportItemResponse();
    response.appointmentId = row.appointmentId != null ? row.appointmentId.toString() : null;
    response.date = row.date != null ? row.date.toString() : null;
    response.startTime = normalizeTimeOrOriginal(row.startTime);
    response.endTime = normalizeTimeOrOriginal(row.endTime);
    response.clientId = row.clientId != null ? row.clientId.toString() : null;
    response.clientName = row.clientName;
    response.professionalId = row.professionalId != null ? row.professionalId.toString() : null;
    response.professionalName = row.professionalName;
    response.serviceLabel = row.serviceLabel != null ? row.serviceLabel.replace("||", " | ") : null;
    response.status = normalizeAppointmentStatus(row.status);
    response.origin = normalizeAppointmentOrigin(row.origin);
    response.totalPrice = row.totalPrice;
    response.flagHorarioVago = row.flagHorarioVago;
    response.flagNaoConfirmado = row.flagNaoConfirmado;
    response.flagAbandonoFluxo = row.flagAbandonoFluxo;
    return response;
  }

  private String toAppointmentManagementCsvLine(
      RelatorioRepository.AppointmentManagementItemRow row) {
    return String.join(
        ",",
        csvCell(row.date != null ? row.date.toString() : null),
        csvCell(normalizeTimeOrOriginal(row.startTime)),
        csvCell(normalizeTimeOrOriginal(row.endTime)),
        csvCell(row.clientName),
        csvCell(row.serviceLabel != null ? row.serviceLabel.replace("||", " | ") : null),
        csvCell(row.professionalName),
        csvCell(normalizeAppointmentStatus(row.status)),
        csvCell(normalizeAppointmentOrigin(row.origin)),
        row.totalPrice != null ? row.totalPrice.toPlainString() : "0.00",
        csvCell(formatBooleanFlag(row.flagHorarioVago)),
        csvCell(formatBooleanFlag(row.flagNaoConfirmado)),
        csvCell(formatBooleanFlag(row.flagAbandonoFluxo)));
  }

  private List<AppointmentManagementReportSignalResponse> buildAppointmentManagementAlerts(
      RelatorioRepository.AppointmentManagementSummaryRow summary,
      AppointmentManagementReportResponse response) {
    List<AppointmentManagementReportSignalResponse> alerts = new ArrayList<>();
    if (summary.totalPending > 0) {
      alerts.add(
          buildManagementSignal(
              "UNCONFIRMED_APPOINTMENTS",
              "Agendamentos sem confirmacao",
              summary.totalPending
                  + " agendamento(s) ainda estao pendentes e exigem confirmacao manual.",
              "warning"));
    }
    if (response.cancellationRate >= 15.0) {
      alerts.add(
          buildManagementSignal(
              "HIGH_CANCELLATION_RATE",
              "Taxa de cancelamento elevada",
              "A taxa de cancelamento esta em "
                  + formatPercentage(response.cancellationRate)
                  + " no periodo filtrado.",
              "critical"));
    }
    if (response.noShowRate >= 10.0) {
      alerts.add(
          buildManagementSignal(
              "HIGH_NO_SHOW_RATE",
              "Taxa de no-show elevada",
              "O no-show representa "
                  + formatPercentage(response.noShowRate)
                  + " dos agendamentos do periodo.",
              "critical"));
    }
    return alerts;
  }

  private List<AppointmentManagementReportSignalResponse> buildAppointmentManagementOpportunities(
      RelatorioRepository.AppointmentManagementSummaryRow summary) {
    List<AppointmentManagementReportSignalResponse> opportunities = new ArrayList<>();
    if (summary.totalGapOpportunities > 0) {
      opportunities.add(
          buildManagementSignal(
              "GAP_OPPORTUNITIES",
              "Janelas livres entre atendimentos",
              summary.totalGapOpportunities
                  + " agendamento(s) possuem intervalo livre relevante apos o atendimento.",
              "opportunity"));
    }
    if (summary.totalAbandonmentSignalDays > 0) {
      opportunities.add(
          buildManagementSignal(
              "ABANDONMENT_SIGNAL",
              "Abandono de fluxo identificado",
              "Foram detectados sinais de abandono em "
                  + summary.totalAbandonmentSignalDays
                  + " dia(s) do periodo, sugerindo oportunidade de recuperacao.",
              "info"));
    }
    if (summary.totalCancelled > 0 || summary.totalNoShow > 0) {
      opportunities.add(
          buildManagementSignal(
              "REACTIVATION_WINDOW",
              "Recuperacao de agenda",
              (summary.totalCancelled + summary.totalNoShow)
                  + " horario(s) perdidos podem ser reofertados ou reativados com contato ativo.",
              "opportunity"));
    }
    return opportunities;
  }

  private AppointmentManagementReportSignalResponse buildManagementSignal(
      String code, String title, String description, String severity) {
    AppointmentManagementReportSignalResponse response =
        new AppointmentManagementReportSignalResponse();
    response.code = code;
    response.title = title;
    response.description = description;
    response.severity = severity;
    return response;
  }

  private NoShowReportPointResponse toNoShowReportPointResponse(
      RelatorioRepository.NoShowDailySummaryRow row) {
    NoShowReportPointResponse response = new NoShowReportPointResponse();
    response.date = row.date != null ? row.date.toString() : null;
    response.totalNoShows = row.totalNoShows;
    response.revenueAtRisk = row.revenueAtRisk;
    return response;
  }

  private NoShowGroupResponse toNoShowGroupResponse(
      RelatorioRepository.NoShowGroupedSummaryRow row) {
    NoShowGroupResponse response = new NoShowGroupResponse();
    response.key = row.key;
    response.label = row.label;
    response.totalNoShows = row.totalNoShows;
    response.revenueAtRisk = row.revenueAtRisk;
    return response;
  }

  private NoShowGroupResponse toNoShowGroupResponse(
      RelatorioRepository.NoShowDailySummaryRow row) {
    NoShowGroupResponse response = new NoShowGroupResponse();
    response.key = row.date != null ? row.date.toString() : null;
    response.label = response.key;
    response.totalNoShows = row.totalNoShows;
    response.revenueAtRisk = row.revenueAtRisk;
    return response;
  }

  private List<NoShowGroupResponse> listarGruposNoShow(
      UUID tenantId,
      LocalDate from,
      LocalDate to,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery,
      NoShowGroupBy groupBy) {
    return switch (groupBy) {
      case DAY ->
          relatorioRepository
              .listarSerieNoShowDiaria(tenantId, from, to, professionalId, serviceId, clientIds, clientQuery)
              .stream()
              .filter(item -> item.totalNoShows > 0)
              .map(this::toNoShowGroupResponse)
              .toList();
      case PROFESSIONAL ->
          relatorioRepository
              .listarResumoNoShowAgrupadoPorProfissional(
                  tenantId, from, to, professionalId, serviceId, clientIds, clientQuery)
              .stream()
              .map(this::toNoShowGroupResponse)
              .toList();
      case CLIENT ->
          relatorioRepository
              .listarResumoNoShowAgrupadoPorCliente(
                  tenantId, from, to, professionalId, serviceId, clientIds, clientQuery)
              .stream()
              .map(this::toNoShowGroupResponse)
              .toList();
      case SERVICE ->
          relatorioRepository
              .listarResumoNoShowAgrupadoPorServico(
                  tenantId, from, to, professionalId, serviceId, clientIds, clientQuery)
              .stream()
              .map(this::toNoShowGroupResponse)
              .toList();
    };
  }

  private LocalDate[] resolveReportPeriod(LocalDate from, LocalDate to) {
    if (from != null && to != null) return new LocalDate[] {from, to};
    if (from != null) return new LocalDate[] {from, from};
    if (to != null) return new LocalDate[] {to, to};
    LocalDate today = LocalDate.now(ZONE_BR);
    return new LocalDate[] {today.withDayOfMonth(1), today};
  }

  private LocalDate[] resolveNoShowPeriod(LocalDate from, LocalDate to) {
    return resolveReportPeriod(from, to);
  }

  private String parseAppointmentStatusDescription(String statusRaw) {
    if (statusRaw == null || statusRaw.isBlank() || "all".equalsIgnoreCase(statusRaw)) return null;
    return StatusAgendamento.fromValue(statusRaw).getDescription();
  }

  private String normalizeAppointmentStatus(String status) {
    if (status == null || status.isBlank()) return null;
    return StatusAgendamento.fromValue(status).name();
  }

  private String normalizeAppointmentOrigin(String origin) {
    if (origin == null || origin.isBlank()) return "NAO_IDENTIFICADA";
    String normalized = origin.trim().toUpperCase(Locale.ROOT);
    if ("INTERNAL_MANUAL".equals(normalized)) return "MANUAL";
    return normalized;
  }

  private String formatPercentage(double value) {
    return String.format(Locale.US, "%.1f%%", value);
  }

  private String formatBooleanFlag(boolean value) {
    return value ? "SIM" : "NAO";
  }

  private String translateAppointmentAction(String action) {
    if (action == null || action.isBlank()) return "Registro operacional";
    return switch (action) {
      case "APPOINTMENT_CREATE" -> "Agendamento criado";
      case "APPOINTMENT_CREATE_WITH_CONFLICT_OVERRIDE" -> "Agendamento criado com conflito assumido";
      case "APPOINTMENT_STATUS_UPDATE" -> "Status atualizado";
      case "APPOINTMENT_REALLOCATE_PROFESSIONAL" -> "Profissional realocado";
      case "APPOINTMENT_DELETE" -> "Agendamento removido";
      case "APPOINTMENT_UPDATE" -> "Agendamento editado";
      case "APPOINTMENT_CUSTOMER_NOTE_CREATE" -> "Nota operacional criada";
      case "APPOINTMENT_CUSTOMER_NOTE_UPDATE" -> "Nota operacional atualizada";
      case "APPOINTMENT_CUSTOMER_NOTE_DELETE" -> "Nota operacional removida";
      default -> action;
    };
  }

  private List<String> parseStringListSafely(String json) {
    if (json == null || json.isBlank() || objectMapper == null) return List.of();
    try {
      return objectMapper.readerForListOf(String.class).readValue(json);
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private Object parseJsonSafely(String json) {
    if (json == null || json.isBlank()) return null;
    if (objectMapper == null) return json;
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (Exception ignored) {
      return json;
    }
  }

  private Map<String, Object> snapshotCustomerNote(AppointmentCustomerNote note) {
    if (note == null) return null;
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("id", note.getId() != null ? note.getId().toString() : null);
    snapshot.put(
        "appointmentId", note.getAppointmentId() != null ? note.getAppointmentId().toString() : null);
    snapshot.put("clientId", note.getClientId() != null ? note.getClientId().toString() : null);
    snapshot.put(
        "recordedByUserId",
        note.getRecordedByUserId() != null ? note.getRecordedByUserId().toString() : null);
    snapshot.put("serviceExecutionNotes", note.getServiceExecutionNotes());
    snapshot.put("clientFeedbackNotes", note.getClientFeedbackNotes());
    snapshot.put("internalFollowupNotes", note.getInternalFollowupNotes());
    snapshot.put("createdAt", note.getCreatedAt() != null ? note.getCreatedAt().toString() : null);
    snapshot.put("updatedAt", note.getUpdatedAt() != null ? note.getUpdatedAt().toString() : null);
    return snapshot;
  }

  // ─── RESOLUCAO DE REQUISICAO / ITENS ──────────────────────────────────────

  private List<ResolvedAppointmentItem> aplicar(
      AgendamentoRequest req, Agendamento a, UUID tenantId) {
    a.setClientId(UUID.fromString(req.clientId));
    a.setProfessionalId(UUID.fromString(req.professionalId));
    a.setDate(DataUtil.parseDataISO(req.date));
    if (a.getDate() == null) throw new IllegalArgumentException("Data invalida");
    if (tenantOperationalSettingsService.isClosedOnSpecialDate(tenantId, a.getDate())) {
      throw new IllegalArgumentException("Salao fechado na data informada");
    }
    LocalTime start = parseTimeOrThrow(req.startTime);
    a.setStartTime(formatTime(start));

    Profissional profissional =
        profissionalRepository
            .findByIdAndTenantIdAndIsActiveTrue(a.getProfessionalId(), tenantId)
            .orElse(null);
    if (profissional == null) {
      throw new IllegalArgumentException("Profissional nao encontrado ou inativo");
    }

    List<ResolvedAppointmentItem> items = resolveRequestedItems(req, tenantId, a.getProfessionalId());
    long totalDuration =
        items.stream().mapToLong(item -> (long) item.service().getDuration() * item.quantity()).sum();
    try {
      a.setEndTime(formatTime(start.plusMinutes(totalDuration)));
    } catch (Exception e) {
      throw new IllegalArgumentException("Horario inicial invalido");
    }
    LocalTime end = parseTimeOrThrow(a.getEndTime());
    if (!tenantOperationalSettingsService.isBusinessOpenAt(tenantId, a.getDate(), start, end)) {
      throw new IllegalArgumentException("Salao fechado ou fora do horario de funcionamento informado");
    }
    // Verificar fechamentos especiais parciais e por profissional
    if (specialClosureService.isClosedAt(tenantId, a.getProfessionalId(), a.getDate(), start, end)) {
      throw new IllegalArgumentException(
          "Salao ou profissional com fechamento especial no horario informado");
    }
    // Validar horario de atendimento do profissional
    if (!isProfessionalAvailableAt(profissional, a.getDate(), start, end)) {
      throw new IllegalArgumentException("O profissional nao atende neste horario");
    }
    a.setNotes(req.notes);
    if (req.status != null && !req.status.isBlank()) {
      a.setStatus(StatusAgendamento.fromValue(req.status));
    }
    return items;
  }

  private AgendamentoRequest buildRequestForItems(AppointmentUpdateRequest req, Agendamento a) {
    AgendamentoRequest r = new AgendamentoRequest();
    r.clientId = a.getClientId().toString();
    r.professionalId = a.getProfessionalId().toString();
    r.date = a.getDate().toString();
    r.startTime = a.getStartTime();
    r.items = req.items;
    r.serviceId = req.serviceId;
    r.totalPrice = req.totalPrice;
    return r;
  }

  private List<ResolvedAppointmentItem> resolveRequestedItems(
      AgendamentoRequest req, UUID tenantId, UUID professionalId) {
    List<AgendamentoRequest.ItemRequest> requestedItems =
        req.items != null && !req.items.isEmpty() ? req.items : fallbackSingleItem(req);

    List<ResolvedAppointmentItem> resolvedItems = new ArrayList<>();
    for (AgendamentoRequest.ItemRequest item : requestedItems) {
      if (item == null || item.serviceId == null || item.serviceId.isBlank()) {
        throw new IllegalArgumentException("Item de agendamento invalido");
      }
      UUID serviceId = UUID.fromString(item.serviceId);
      Servico servico =
          servicoRepository
              .findByIdAndTenantId(serviceId, tenantId)
              .orElseThrow(() -> new IllegalArgumentException("Servico nao encontrado"));
      if (servico.getProfissionais() != null
          && !servico.getProfissionais().isEmpty()
          && servico.getProfissionais().stream().noneMatch(p -> p.getId().equals(professionalId))) {
        throw new IllegalArgumentException("Profissional nao atende o servico selecionado");
      }

      int quantity = item.quantity <= 0 ? 1 : item.quantity;
      BigDecimal unitPrice =
          NumericUtil.isPositive(item.unitPrice) ? NumericUtil.normalize(item.unitPrice) : servico.getPrice();
      BigDecimal discountAmount = NumericUtil.maxZero(item.discountAmount);
      BigDecimal inferredGrossAmount =
          NumericUtil.isPositive(item.grossAmount)
              ? NumericUtil.normalize(item.grossAmount)
              : NumericUtil.multiply(unitPrice, quantity);
      BigDecimal totalPrice =
          NumericUtil.isPositive(item.totalPrice)
              ? NumericUtil.normalize(item.totalPrice)
              : NumericUtil.maxZero(NumericUtil.subtract(inferredGrossAmount, discountAmount));
      BigDecimal grossAmount =
          NumericUtil.normalize(inferredGrossAmount.max(NumericUtil.add(totalPrice, discountAmount)));
      resolvedItems.add(
          new ResolvedAppointmentItem(
              serviceId, quantity, unitPrice, grossAmount, discountAmount, totalPrice, servico));
    }
    return resolvedItems;
  }

  private List<AgendamentoRequest.ItemRequest> fallbackSingleItem(AgendamentoRequest req) {
    if (req.serviceId == null || req.serviceId.isBlank()) {
      throw new IllegalArgumentException("Agendamento deve informar ao menos um item");
    }
    AgendamentoRequest.ItemRequest item = new AgendamentoRequest.ItemRequest();
    item.serviceId = req.serviceId;
    item.quantity = 1;
    item.grossAmount = req.totalPrice;
    item.discountAmount = NumericUtil.zero();
    item.totalPrice = req.totalPrice;
    return List.of(item);
  }

  /**
   * Grava os itens resolvidos. A colecao {@code items} do agendamento e atualizada in loco (nunca
   * substituida) porque e {@code orphanRemoval = true}: trocar a instancia da colecao faria o
   * Hibernate abortar com "collection with cascade=all-delete-orphan was no longer referenced".
   */
  private void persistItems(Agendamento appointment, List<ResolvedAppointmentItem> items) {
    if (appointment == null || appointment.getId() == null || items == null || items.isEmpty()) return;
    if (agendamentoItemRepository.existsByAppointmentId(appointment.getId())) return;

    List<AgendamentoItem> persistedItems = new ArrayList<>();
    for (ResolvedAppointmentItem resolvedItem : items) {
      AgendamentoItem item = new AgendamentoItem();
      item.setTenantId(appointment.getTenantId());
      item.setAppointmentId(appointment.getId());
      item.setServiceId(resolvedItem.serviceId());
      item.setService(resolvedItem.service());
      item.setQuantity(resolvedItem.quantity());
      item.setUnitPrice(resolvedItem.unitPrice());
      item.setGrossAmount(resolvedItem.grossAmount());
      item.setDiscountAmount(resolvedItem.discountAmount());
      item.setTotalPrice(resolvedItem.totalPrice());
      persistedItems.add(agendamentoItemRepository.save(item));
    }
    appointment.getItems().clear();
    appointment.getItems().addAll(persistedItems);
  }

  // ─── CONFLITO DE AGENDA ───────────────────────────────────────────────────

  private AppointmentConflictResolution resolverConflitoCriacao(
      UUID tenantId, Agendamento agendamento, AgendamentoRequest req) {
    LocalTime inicio = parseTimeOrThrow(agendamento.getStartTime());
    LocalTime fim = parseTimeOrThrow(agendamento.getEndTime());
    List<Agendamento> conflitos =
        listarConflitosHorario(
            tenantId, agendamento.getProfessionalId(), agendamento.getDate(), inicio, fim, null);
    if (conflitos.isEmpty()) {
      return new AppointmentConflictResolution(false, null);
    }

    boolean internalManual = isInternalManualOrigin(req != null ? req.origin : null);
    boolean settingsAllow = appointmentSettingsService.allowsManualConflictByTenantId(tenantId);
    boolean requestedOverride =
        req != null
            && Boolean.TRUE.equals(req.allowConflict)
            && Boolean.TRUE.equals(req.conflictAcknowledged);
    boolean canOverride = internalManual && settingsAllow;

    if (canOverride && requestedOverride) {
      return new AppointmentConflictResolution(
          true, buildConflictAuditMetadata(agendamento, req.origin, conflitos));
    }

    LOG.warn(
        CorrelatedLogging.context(
            "Conflito de agenda detectado na criacao",
            "tenantId", tenantId,
            "professionalId", agendamento.getProfessionalId(),
            "date", agendamento.getDate(),
            "startTime", agendamento.getStartTime(),
            "endTime", agendamento.getEndTime(),
            "conflicts", conflitos.size(),
            "overrideAllowed", canOverride,
            "origin", req != null ? req.origin : null));
    throw new AppointmentConflictException(
        canOverride
            ? "Horario ja utilizado para este profissional. Confirme o conflito para continuar."
            : "Profissional indisponivel neste horario.",
        buildConflictDetails(
            tenantId, agendamento, req != null ? req.origin : null, canOverride, settingsAllow, conflitos));
  }

  private void validarConflitoEdicao(UUID tenantId, Agendamento a, AppointmentUpdateRequest req) {
    LocalTime inicio = parseTimeOrThrow(a.getStartTime());
    LocalTime fim = parseTimeOrThrow(a.getEndTime());
    List<Agendamento> conflitos =
        listarConflitosHorario(tenantId, a.getProfessionalId(), a.getDate(), inicio, fim, a.getId());
    if (conflitos.isEmpty()) return;

    boolean settingsAllow = appointmentSettingsService.allowsManualConflictByTenantId(tenantId);
    boolean requestedOverride =
        req != null
            && Boolean.TRUE.equals(req.allowConflict)
            && Boolean.TRUE.equals(req.conflictAcknowledged);

    if (settingsAllow && requestedOverride) return;

    LOG.warn(
        CorrelatedLogging.context(
            "Conflito de agenda detectado na edicao",
            "tenantId", tenantId,
            "appointmentId", a.getId(),
            "professionalId", a.getProfessionalId(),
            "date", a.getDate(),
            "startTime", a.getStartTime(),
            "endTime", a.getEndTime(),
            "conflicts", conflitos.size(),
            "overrideAllowed", settingsAllow));
    throw new AppointmentConflictException(
        settingsAllow
            ? "Horario ja utilizado para este profissional. Confirme o conflito para continuar."
            : "Profissional indisponivel neste horario.",
        buildConflictDetails(tenantId, a, "INTERNAL_MANUAL", settingsAllow, settingsAllow, conflitos));
  }

  private void validarConflitoHorarioComNovoProfissional(
      UUID tenantId, Agendamento agendamento, UUID novoProfessionalId) {
    LocalTime novoInicio = parseTimeOrThrow(agendamento.getStartTime());
    LocalTime novoFim = parseTimeOrThrow(agendamento.getEndTime());
    List<Agendamento> conflitos =
        listarConflitosHorario(
            tenantId,
            novoProfessionalId,
            agendamento.getDate(),
            novoInicio,
            novoFim,
            agendamento.getId());
    if (!conflitos.isEmpty()) {
      LOG.warn(
          CorrelatedLogging.context(
              "Realocacao bloqueada por conflito",
              "tenantId", tenantId,
              "appointmentId", agendamento.getId(),
              "newProfessionalId", novoProfessionalId,
              "date", agendamento.getDate(),
              "startTime", agendamento.getStartTime(),
              "endTime", agendamento.getEndTime(),
              "conflicts", conflitos.size()));
      throw new IllegalArgumentException("Profissional indisponivel neste horario");
    }
  }

  /**
   * {@code CANCELLED} e {@code COMPLETED} nao ocupam mais o horario para novos agendamentos: o
   * agendamento foi encerrado (cancelado ou atendimento ja finalizado), entao o profissional volta
   * a ficar disponivel naquele slot.
   */
  private List<Agendamento> listarConflitosHorario(
      UUID tenantId,
      UUID professionalId,
      LocalDate date,
      LocalTime inicio,
      LocalTime fim,
      UUID excludeAppointmentId) {
    List<StatusAgendamento> excluidos =
        List.of(StatusAgendamento.CANCELLED, StatusAgendamento.COMPLETED);
    List<Agendamento> agendaProfissional =
        excludeAppointmentId == null
            ? agendamentoRepository.listActiveByProfessionalAndDate(
                tenantId, professionalId, date, excluidos)
            : agendamentoRepository.listActiveByProfessionalAndDateExcluding(
                tenantId, professionalId, date, excludeAppointmentId, excluidos);

    return agendaProfissional.stream()
        .sorted(Comparator.comparing(a -> parseTimeOrThrow(a.getStartTime())))
        .filter(
            item -> {
              LocalTime existenteInicio = parseTimeOrThrow(item.getStartTime());
              LocalTime existenteFim = parseTimeOrThrow(item.getEndTime());
              return inicio.isBefore(existenteFim) && fim.isAfter(existenteInicio);
            })
        .toList();
  }

  private AppointmentConflictDetailsResponse buildConflictDetails(
      UUID tenantId,
      Agendamento agendamento,
      String origin,
      boolean canOverride,
      boolean settingsAllow,
      List<Agendamento> conflitos) {
    AppointmentConflictDetailsResponse details = new AppointmentConflictDetailsResponse();
    details.requestedDate = agendamento.getDate() != null ? agendamento.getDate().toString() : null;
    details.requestedStartTime = agendamento.getStartTime();
    details.requestedEndTime = agendamento.getEndTime();
    details.origin = origin;
    details.canOverride = canOverride;
    details.allowConflictingAppointmentsOnManualScheduling = settingsAllow;
    details.conflicts = conflitos.stream().map(conflict -> toConflictSummary(tenantId, conflict)).toList();
    return details;
  }

  private Map<String, Object> buildConflictAuditMetadata(
      Agendamento agendamento, String origin, List<Agendamento> conflitos) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("origin", origin);
    metadata.put("conflictOverride", Boolean.TRUE);
    metadata.put("requestedDate", agendamento.getDate() != null ? agendamento.getDate().toString() : null);
    metadata.put("requestedStartTime", agendamento.getStartTime());
    metadata.put("requestedEndTime", agendamento.getEndTime());
    metadata.put(
        "conflicts",
        conflitos.stream()
            .map(
                conflict -> {
                  Map<String, Object> item = new HashMap<>();
                  item.put("appointmentId", conflict.getId() != null ? conflict.getId().toString() : null);
                  item.put("startTime", conflict.getStartTime());
                  item.put("endTime", conflict.getEndTime());
                  item.put("status", conflict.getStatus() != null ? conflict.getStatus().name() : null);
                  return item;
                })
            .toList());
    return metadata;
  }

  private AppointmentConflictSummaryResponse toConflictSummary(UUID tenantId, Agendamento appointment) {
    AppointmentConflictSummaryResponse response = new AppointmentConflictSummaryResponse();
    response.appointmentId = appointment.getId() != null ? appointment.getId().toString() : null;
    response.clientId = appointment.getClientId() != null ? appointment.getClientId().toString() : null;
    response.startTime = normalizeTimeOrOriginal(appointment.getStartTime());
    response.endTime = normalizeTimeOrOriginal(appointment.getEndTime());
    response.status = appointment.getStatus() != null ? appointment.getStatus().name() : null;

    Cliente client = clienteRepository.findByIdAndTenantId(appointment.getClientId(), tenantId).orElse(null);
    response.clientName = client != null ? client.getName() : null;

    UUID serviceId = appointment.resolvePrimaryServiceId();
    response.serviceId = serviceId != null ? serviceId.toString() : null;
    Servico service =
        serviceId != null ? servicoRepository.findByIdAndTenantId(serviceId, tenantId).orElse(null) : null;
    response.serviceName = service != null ? service.getName() : null;
    return response;
  }

  private boolean isInternalManualOrigin(String origin) {
    return origin != null && "INTERNAL_MANUAL".equalsIgnoreCase(origin.trim());
  }

  /**
   * Verifica se o profissional tem horario de atendimento configurado para o dia e intervalo dado.
   * Reutiliza a mesma logica de {@code AppointmentService.obterJanelasDeTrabalho}.
   *
   * @return true se o profissional atende no horario; false se nao atende ou nao tem horario
   *     configurado
   */
  private boolean isProfessionalAvailableAt(
      Profissional profissional, LocalDate date, LocalTime start, LocalTime end) {
    if (profissional == null) return true;
    List<ProfissionalWorkingHour> workingHours =
        profissionalWorkingHourRepository.listByProfessional(
            profissional.getTenantId(), profissional.getId());
    if (workingHours == null || workingHours.isEmpty()) {
      // Sem horario configurado: sem restricao (nao bloqueia)
      return true;
    }

    int targetDayIso = date.getDayOfWeek().getValue(); // ISO: segunda=1 ... domingo=7
    boolean hasDayConfig = false;

    for (ProfissionalWorkingHour wh : workingHours) {
      if (wh == null) continue;

      // dayOfWeek pode ser ISO (1-7) ou JS (0=domingo,1=segunda...6=sabado)
      boolean matchesDay =
          wh.getDayOfWeek() == targetDayIso || (targetDayIso == 7 && wh.getDayOfWeek() == 0);

      if (!matchesDay) continue;

      if (!wh.isWorking()) {
        // Dia configurado como nao trabalhado
        hasDayConfig = true;
        continue;
      }
      if (wh.getStartTime() == null
          || wh.getEndTime() == null
          || !wh.getStartTime().isBefore(wh.getEndTime())) {
        hasDayConfig = true;
        continue;
      }
      // Verifica se o agendamento cabe dentro da janela do profissional
      if (!start.isBefore(wh.getStartTime()) && !end.isAfter(wh.getEndTime())) {
        return true;
      }
      hasDayConfig = true;
    }

    // Se havia configuracao para o dia mas nenhuma janela comportou o horario -> nao disponivel
    // Se nao havia configuracao para o dia -> sem restricao (nao bloqueia)
    return !hasDayConfig;
  }

  // ─── EFEITOS FINANCEIROS DA CONCLUSAO ─────────────────────────────────────

  private void registrarReceitaConclusaoSeNecessario(
      UUID tenantId, Agendamento agendamento, String paymentMethod) {
    if (agendamento == null || agendamento.getId() == null) return;

    // Se ha uma Comanda vinculada a este agendamento (aberta automaticamente ao iniciar o
    // atendimento), ela e quem registra a receita por item ao ser fechada
    // (ServicoComanda.criarTransacaoVenda) - registrar aqui duplicaria.
    if (comandaRepository.existsByAppointmentIdAndTenantId(agendamento.getId(), tenantId)) return;

    if (transacaoRepository.existsByTenantAndAppointmentAndType(
        tenantId, agendamento.getId(), TipoTransacao.INCOME)) {
      return;
    }

    UUID primaryServiceId = agendamento.resolvePrimaryServiceId();
    Servico servico =
        primaryServiceId != null
            ? servicoRepository.findByIdAndTenantId(primaryServiceId, tenantId).orElse(null)
            : null;

    Transacao transacao = new Transacao();
    transacao.setTenantId(tenantId);
    transacao.setAppointmentId(agendamento.getId());
    transacao.setType(TipoTransacao.INCOME);
    transacao.setCategoryId(resolveTransactionCategoryId(tenantId, "APPOINTMENT"));
    transacao.setDescription(
        servico != null ? "Receita de agendamento - " + servico.getName() : "Receita de agendamento");
    transacao.setAmount(agendamento.resolveEffectiveTotalPrice());
    transacao.setPaymentMethod(resolveMetodoPagamentoConclusao(paymentMethod));
    transacao.setDate(
        agendamento.getDate() != null
            ? agendamento.getDate().atStartOfDay(ZONE_BR).toInstant()
            : Instant.now());
    transacaoRepository.save(transacao);
  }

  private void estornarReceitaConclusaoSeNecessario(UUID tenantId, Agendamento agendamento) {
    if (agendamento == null || agendamento.getId() == null) return;
    transacaoRepository.deleteByTenantAndAppointmentAndTypeAndCategoryName(
        tenantId, agendamento.getId(), TipoTransacao.INCOME, "APPOINTMENT");
  }

  /**
   * Abre uma Comanda automaticamente ao iniciar o atendimento (CONFIRMED -&gt; IN_PROGRESS),
   * pre-populada com os servicos do agendamento, para dar rastreabilidade financeira ao
   * atendimento independente do canal de origem (WhatsApp, agendamento publico ou manual).
   * Idempotente: nao abre uma segunda comanda se ja existir uma para este agendamento. Falha aqui
   * nao bloqueia a transicao de status - o atendimento sempre pode comecar, mesmo que a abertura
   * automatica da comanda falhe (equipe abre manualmente no /pos).
   */
  private UUID abrirComandaAutomaticaSeNecessario(UUID tenantId, Agendamento agendamento) {
    if (agendamento == null || agendamento.getId() == null) return null;
    if (comandaRepository.existsByAppointmentIdAndTenantId(agendamento.getId(), tenantId)) return null;

    try {
      return criarComandaComItens(tenantId, agendamento);
    } catch (Exception e) {
      LOG.warn(
          CorrelatedLogging.context(
              "Falha ao abrir comanda automatica para atendimento iniciado - equipe pode abrir manualmente no PDV",
              "tenantId", tenantId,
              "appointmentId", agendamento.getId(),
              "erro", e.getMessage()));
      return null;
    }
  }

  /**
   * Garante que existe uma Comanda vinculada ao agendamento, reaproveitando a ja aberta (ex.: pela
   * transicao para IN_PROGRESS) ou criando uma nova se por algum motivo ainda nao existir. Ao
   * contrario de {@link #abrirComandaAutomaticaSeNecessario}, propaga qualquer falha - usado no
   * fluxo de "pagar agora", onde um erro silencioso deixaria um pagamento real sem comanda.
   */
  private UUID garantirComandaId(UUID tenantId, Agendamento agendamento) {
    Comanda existente =
        comandaRepository.findFirstByAppointmentAndTenant(agendamento.getId(), tenantId).orElse(null);
    if (existente != null) return existente.getId();
    return criarComandaComItens(tenantId, agendamento);
  }

  private UUID criarComandaComItens(UUID tenantId, Agendamento agendamento) {
    ComandaDtos.AbrirComandaRequest abrirRequest = new ComandaDtos.AbrirComandaRequest();
    abrirRequest.appointmentId = agendamento.getId().toString();
    abrirRequest.clientId =
        agendamento.getClientId() != null ? agendamento.getClientId().toString() : null;
    ComandaDtos.ComandaResponse comanda = servicoComanda.abrir(abrirRequest);

    List<AgendamentoItem> itens =
        agendamento.getItems() != null ? agendamento.getItems() : Collections.emptyList();
    for (AgendamentoItem item : itens) {
      if (item.getServiceId() == null) continue;
      ComandaDtos.AdicionarItemRequest itemRequest = new ComandaDtos.AdicionarItemRequest();
      itemRequest.tipo = "SERVICO";
      itemRequest.referenciaId = item.getServiceId().toString();
      itemRequest.professionalId =
          agendamento.getProfessionalId() != null ? agendamento.getProfessionalId().toString() : null;
      itemRequest.quantidade = BigDecimal.valueOf(item.getQuantity());
      itemRequest.precoUnitario = item.getUnitPrice();
      servicoComanda.adicionarItem(UUID.fromString(comanda.id), itemRequest);
    }

    LOG.info(
        CorrelatedLogging.context(
            "Comanda aberta automaticamente para o agendamento",
            "tenantId", tenantId,
            "appointmentId", agendamento.getId(),
            "comandaId", comanda.id));
    return UUID.fromString(comanda.id);
  }

  /**
   * Fluxo "Pagar agora" ao concluir o agendamento: garante a comanda, abate automaticamente o sinal
   * (deposito antecipado, F02) ja pago e ainda nao usado para este agendamento, registra o pagamento
   * do saldo restante pela forma informada e fecha a comanda - o fechamento e quem lanca a receita
   * no caixa (ServicoComanda.criarTransacaoVenda), evitando um segundo caminho de registro
   * financeiro.
   */
  private void pagarComandaAgoraSeNecessario(
      UUID tenantId, Agendamento agendamento, String paymentMethod) {
    if (agendamento == null || agendamento.getId() == null) return;
    UUID comandaId = garantirComandaId(tenantId, agendamento);

    ComandaDtos.ComandaResponse comanda = servicoComanda.obter(comandaId);
    if (!Comanda.STATUS_ABERTA.equals(comanda.status)) {
      return;
    }

    BigDecimal totalPago =
        (comanda.pagamentos != null ? comanda.pagamentos : List.<ComandaDtos.ComandaPagamentoResponse>of())
            .stream()
            .filter(p -> ComandaPagamento.STATUS_CONFIRMADO.equals(p.status))
            .map(p -> p.valor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal restante = comanda.total.subtract(totalPago);

    restante = abaterSinalPagoSeDisponivel(comandaId, agendamento.getId(), restante);

    if (restante.compareTo(BigDecimal.ZERO) > 0) {
      ComandaDtos.RegistrarPagamentoRequest pagamentoRequest =
          new ComandaDtos.RegistrarPagamentoRequest();
      pagamentoRequest.meio = mapearMeioPagamentoParaComanda(paymentMethod);
      pagamentoRequest.valor = restante;
      servicoComanda.registrarPagamento(comandaId, pagamentoRequest);
    }
    servicoComanda.fechar(comandaId);
  }

  /**
   * Abate o sinal (F02) ja pago e ainda nao usado para este agendamento contra o saldo restante da
   * comanda, registrando um pagamento CREDITO_SINAL pelo menor entre o valor do sinal e o restante
   * devido. Retorna o novo restante apos o abatimento (0 se o sinal cobriu tudo). Sem sinal pago
   * disponivel, retorna o restante original inalterado.
   */
  private BigDecimal abaterSinalPagoSeDisponivel(
      UUID comandaId, UUID appointmentId, BigDecimal restante) {
    if (restante.compareTo(BigDecimal.ZERO) <= 0) return restante;
    AppointmentDeposit deposit =
        appointmentDepositRepository.findPaidUnusedByAppointmentId(appointmentId).orElse(null);
    if (deposit == null) return restante;

    BigDecimal valorDisponivel = NumericUtil.fromCents(deposit.getAmountCents());
    BigDecimal valorAbatido = valorDisponivel.min(restante);
    if (valorAbatido.compareTo(BigDecimal.ZERO) <= 0) return restante;

    ComandaDtos.RegistrarPagamentoRequest sinalRequest = new ComandaDtos.RegistrarPagamentoRequest();
    sinalRequest.meio = ComandaPagamento.MEIO_CREDITO_SINAL;
    sinalRequest.valor = valorAbatido;
    servicoComanda.registrarPagamento(comandaId, sinalRequest);

    return restante.subtract(valorAbatido);
  }

  private String mapearMeioPagamentoParaComanda(String paymentMethod) {
    if (paymentMethod == null || paymentMethod.isBlank()) {
      throw new IllegalArgumentException("Forma de pagamento e obrigatoria para 'pagar agora'.");
    }
    return switch (paymentMethod.trim().toUpperCase(Locale.ROOT)) {
      case "CASH" -> ComandaPagamento.MEIO_DINHEIRO;
      case "CREDIT_CARD" -> ComandaPagamento.MEIO_CARTAO_CREDITO_EXTERNO;
      case "DEBIT_CARD" -> ComandaPagamento.MEIO_CARTAO_DEBITO_EXTERNO;
      default ->
          throw new IllegalArgumentException(
              "Forma de pagamento nao suportada para 'pagar agora': "
                  + paymentMethod
                  + ". Use dinheiro, cartao de credito/debito, ou registre PIX diretamente na tela de Comandas.");
    };
  }

  /**
   * Impede sair do status COMPLETED quando a comanda vinculada ja foi FECHADA (venda real lancada no
   * caixa via "Pagar agora" ou manualmente no PDV) - o estorno dessa receita e uma operacao
   * financeira que deve ser feita explicitamente no financeiro/caixa, nunca como efeito colateral de
   * trocar o status do agendamento.
   */
  private void validarComandaNaoFechadaParaSairDeCompleted(UUID tenantId, Agendamento agendamento) {
    if (agendamento == null || agendamento.getId() == null) return;
    Comanda comanda =
        comandaRepository.findFirstByAppointmentAndTenant(agendamento.getId(), tenantId).orElse(null);
    if (comanda != null && Comanda.STATUS_FECHADA.equals(comanda.getStatus())) {
      throw new IllegalArgumentException(
          "Nao e possivel alterar o status: a comanda vinculada ja foi fechada com venda registrada no caixa. "
              + "Estorne a venda manualmente no financeiro antes de alterar o status do agendamento.");
    }
  }

  /**
   * Cancela automaticamente a Comanda auto-aberta ao iniciar o atendimento quando o agendamento e
   * cancelado ou marcado como nao-compareceu, evitando deixar uma comanda ABERTA orfa. Reaproveita
   * {@code ServicoComanda.cancelar}, que ja libera credito de sinal e cancela PIX pendente. So atua
   * se a comanda ainda estiver ABERTA. Falha aqui nao bloqueia a transicao de status.
   */
  private void cancelarComandaAbertaSeNecessario(UUID tenantId, Agendamento agendamento) {
    if (agendamento == null || agendamento.getId() == null) return;
    try {
      Comanda comanda =
          comandaRepository.findFirstByAppointmentAndTenant(agendamento.getId(), tenantId).orElse(null);
      if (comanda == null || !Comanda.STATUS_ABERTA.equals(comanda.getStatus())) {
        return;
      }
      ComandaDtos.CancelarComandaRequest request = new ComandaDtos.CancelarComandaRequest();
      request.motivo = "Agendamento cancelado ou marcado como nao-compareceu";
      servicoComanda.cancelar(comanda.getId(), request);
    } catch (Exception e) {
      LOG.warn(
          CorrelatedLogging.context(
              "Falha ao cancelar comanda automaticamente apos cancelamento/no-show do agendamento",
              "tenantId", tenantId,
              "appointmentId", agendamento.getId(),
              "erro", e.getMessage()));
    }
  }

  private void validarNoShowPermitido(Agendamento agendamento, StatusAgendamento novoStatus) {
    if (novoStatus != StatusAgendamento.NO_SHOW) return;
    if (agendamento == null
        || agendamento.getDate() == null
        || agendamento.getStartTime() == null
        || agendamento.getStartTime().isBlank()) {
      throw new IllegalArgumentException(
          "Nao foi possivel validar horario para marcar nao compareceu");
    }

    LocalTime inicio = parseTimeOrThrow(agendamento.getStartTime());
    ZonedDateTime limiteNoShow =
        agendamento.getDate().atTime(inicio).atZone(ZONE_BR).plusMinutes(5);
    ZonedDateTime agora = ZonedDateTime.now(ZONE_BR);
    if (agora.isBefore(limiteNoShow)) {
      throw new IllegalArgumentException(
          "Nao compareceu so pode ser marcado 5 minutos apos o horario agendado");
    }
  }

  private UUID resolveTransactionCategoryId(UUID tenantId, String categoryName) {
    TransactionCategory category =
        transactionCategoryRepository.findByTenantAndName(tenantId, categoryName).orElse(null);
    if (category == null) {
      category = new TransactionCategory();
      category.setTenantId(tenantId);
      category.setName(categoryName);
      category = transactionCategoryRepository.save(category);
    }
    return category.getId();
  }

  private MetodoPagamento resolveMetodoPagamentoConclusao(String paymentMethod) {
    if (paymentMethod == null || paymentMethod.isBlank()) return MetodoPagamento.OTHER;
    try {
      return MetodoPagamento.valueOf(paymentMethod.trim().toUpperCase(Locale.ROOT));
    } catch (Exception e) {
      throw new IllegalArgumentException("paymentMethod invalido para conclusao do agendamento");
    }
  }

  // ─── MAPEAMENTO DE RESPOSTA ───────────────────────────────────────────────

  private AgendamentoResponse toResponse(Agendamento a) {
    UUID tenantId = a.getTenantId();

    AgendamentoResponse r = new AgendamentoResponse();
    r.id = a.getId().toString();
    r.tenantId = tenantId.toString();
    r.clientId = a.getClientId().toString();
    r.professionalId = a.getProfessionalId().toString();
    UUID primaryServiceId = a.resolvePrimaryServiceId();
    r.serviceId = primaryServiceId != null ? primaryServiceId.toString() : null;
    r.date = a.getDate() != null ? a.getDate().toString() : null;
    r.startTime = normalizeTimeOrOriginal(a.getStartTime());
    r.endTime = normalizeTimeOrOriginal(a.getEndTime());
    r.status = a.getStatus() != null ? a.getStatus().name() : null;
    r.notes = a.getNotes();
    r.totalPrice = a.resolveEffectiveTotalPrice();
    r.createdAt = a.getCreatedAt() != null ? a.getCreatedAt().toString() : null;

    Cliente c = clienteRepository.findByIdAndTenantId(a.getClientId(), tenantId).orElse(null);
    if (c != null) {
      ClienteStatsRepository.ClienteStats stats =
          clienteStatsRepository.findStatsByTenantAndClient(tenantId, c.getId());
      ClienteResponse cr = new ClienteResponse();
      cr.id = c.getId().toString();
      cr.tenantId = c.getTenantId().toString();
      cr.name = c.getName();
      cr.email = c.getEmail();
      cr.phone = c.getPhone();
      cr.birthDate = c.getBirthDate() != null ? c.getBirthDate().toString() : null;
      cr.notes = c.getNotes();
      cr.cpfCnpj = c.getCpfCnpj();
      cr.clientType = c.getClientType();
      cr.totalVisits = stats.totalVisits();
      cr.totalSpent = stats.totalSpent();
      cr.lastVisit = stats.lastVisit() != null ? stats.lastVisit().toString() : null;
      cr.createdAt = c.getCreatedAt() != null ? c.getCreatedAt().toString() : null;
      r.client = cr;
    }

    Profissional p =
        profissionalRepository.findByIdAndTenantId(a.getProfessionalId(), tenantId).orElse(null);
    if (p != null) {
      ProfissionalResponse pr = new ProfissionalResponse();
      pr.id = p.getId().toString();
      pr.tenantId = p.getTenantId().toString();
      pr.userId = p.getUserId() != null ? p.getUserId().toString() : null;
      pr.name = p.getName();
      pr.email = p.getEmail();
      pr.phone = p.getPhone();
      pr.avatar = p.getAvatar();
      pr.commissionRate = p.getCommissionRate();
      pr.isActive = p.isActive();
      pr.createdAt = p.getCreatedAt() != null ? p.getCreatedAt().toString() : null;
      r.professional = pr;
    }

    Servico s =
        primaryServiceId != null
            ? servicoRepository.findByIdAndTenantId(primaryServiceId, tenantId).orElse(null)
            : null;
    if (s != null) {
      r.service = toServicoResponse(s);
    }

    r.items =
        a.getItems().stream()
            .sorted(
                Comparator.comparing(
                    AgendamentoItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(this::toItemResponse)
            .toList();

    return r;
  }

  private AgendamentoResponse.ItemResponse toItemResponse(AgendamentoItem item) {
    AgendamentoResponse.ItemResponse response = new AgendamentoResponse.ItemResponse();
    response.serviceId = item.getServiceId() != null ? item.getServiceId().toString() : null;
    response.quantity = item.getQuantity();
    response.unitPrice = item.getUnitPrice();
    response.grossAmount = item.getGrossAmount();
    response.discountAmount = item.getDiscountAmount();
    response.totalPrice = item.getTotalPrice();
    if (item.getService() != null) {
      response.service = toServicoResponse(item.getService());
    }
    return response;
  }

  /**
   * Mesmo subconjunto de campos que o original preenche inline: {@code professionalIds} e os campos
   * de sinal ({@code requiresDeposit}/{@code depositType}/{@code depositValue}) ficam de fora
   * porque o original tambem nao os preenche aqui.
   */
  private ServicoResponse toServicoResponse(Servico servico) {
    ServicoResponse response = new ServicoResponse();
    response.id = servico.getId() != null ? servico.getId().toString() : null;
    response.tenantId = servico.getTenantId() != null ? servico.getTenantId().toString() : null;
    response.name = servico.getName();
    response.description = servico.getDescription();
    response.duration = servico.getDuration();
    response.price = servico.getPrice();
    response.category = resolveCategoryName(servico.getCategoryId());
    response.isActive = servico.isActive();
    response.createdAt = servico.getCreatedAt() != null ? servico.getCreatedAt().toString() : null;
    return response;
  }

  private String resolveCategoryName(UUID categoryId) {
    if (categoryId == null) return null;
    return serviceCategoryRepository.findById(categoryId).map(ServiceCategory::getName).orElse(null);
  }

  private AppointmentCustomerNoteResponse toCustomerNoteResponse(AppointmentCustomerNote note) {
    AppointmentCustomerNoteResponse response = new AppointmentCustomerNoteResponse();
    response.noteId = note.getId() != null ? note.getId().toString() : null;
    response.appointmentId =
        note.getAppointmentId() != null ? note.getAppointmentId().toString() : null;
    response.clientId = note.getClientId() != null ? note.getClientId().toString() : null;
    response.recordedByUserId =
        note.getRecordedByUserId() != null ? note.getRecordedByUserId().toString() : null;
    response.recordedAt = note.getCreatedAt() != null ? note.getCreatedAt().toString() : null;
    response.updatedAt = note.getUpdatedAt() != null ? note.getUpdatedAt().toString() : null;
    response.serviceExecutionNotes = note.getServiceExecutionNotes();
    response.clientFeedbackNotes = note.getClientFeedbackNotes();
    response.internalFollowupNotes = note.getInternalFollowupNotes();
    return response;
  }

  private AgendamentoMetricaDiariaResponse toMetricaDiariaResponse(Object[] row) {
    AgendamentoMetricaDiariaResponse response = new AgendamentoMetricaDiariaResponse();
    response.dia = ((Number) row[0]).intValue();
    response.mes = ((Number) row[1]).intValue();
    response.quantidadeAgendamentos = ((Number) row[2]).longValue();
    return response;
  }

  // ─── UTILITARIOS ──────────────────────────────────────────────────────────

  private void validarPeriodo(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("Periodo de filtro invalido");
    }
  }

  private UUID resolveProfessionalFilter(UUID tenantId, UUID requestedProfessionalId) {
    if (!isProfessional()) return requestedProfessionalId;
    UUID userId = obterUserIdOuFalhar();
    Profissional profissional =
        profissionalRepository.findByTenantIdAndUserId(tenantId, userId).orElse(null);
    if (profissional == null) {
      throw new IllegalArgumentException("Profissional autenticado nao encontrado");
    }
    return profissional.getId();
  }

  private String csvCell(String value) {
    if (value == null) return "\"\"";
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private boolean isProfessional() {
    return authenticatedUser.isProfessional();
  }

  private UUID obterUserIdOuFalhar() {
    return authenticatedUser.idOuFalhar();
  }

  private UUID obterUserIdOptional() {
    return authenticatedUser.idOuNulo();
  }

  private String obterRoleOptional() {
    return authenticatedUser.roleOuNulo();
  }

  private LocalTime parseTimeOrThrow(String value) {
    try {
      return LocalTime.parse(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Horario inicial invalido");
    }
  }

  private String normalizeTimeOrOriginal(String value) {
    if (value == null || value.isBlank()) return value;
    try {
      return formatTime(LocalTime.parse(value));
    } catch (Exception e) {
      return value;
    }
  }

  private String formatTime(LocalTime time) {
    return time.format(TIME_FMT);
  }

  private void validarConteudoNota(AppointmentCustomerNoteRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Nota operacional obrigatoria");
    }
    if (normalizeNoteText(request.serviceExecutionNotes) == null
        && normalizeNoteText(request.clientFeedbackNotes) == null
        && normalizeNoteText(request.internalFollowupNotes) == null) {
      throw new IllegalArgumentException("Informe ao menos um detalhe operacional do atendimento");
    }
  }

  private String normalizeNoteText(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank()
        ? null
        : normalized.substring(0, Math.min(normalized.length(), 1000));
  }

  private void validarNotaOperacionalObrigatoria(
      UUID tenantId, Agendamento appointment, StatusAgendamento novoStatus) {
    if (appointment == null || novoStatus != StatusAgendamento.COMPLETED) return;
    if (appointment.getStatus() == StatusAgendamento.COMPLETED) return;
    long totalNotes =
        appointmentCustomerNoteRepository.countByTenantIdAndAppointmentId(
            tenantId, appointment.getId());
    if (totalNotes <= 0) {
      throw new IllegalArgumentException(
          "Antes de concluir o atendimento, registre ao menos um detalhe operacional do cliente.");
    }
  }

  private record ResolvedAppointmentItem(
      UUID serviceId,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal grossAmount,
      BigDecimal discountAmount,
      BigDecimal totalPrice,
      Servico service) {}

  private record AppointmentConflictResolution(boolean overrideApplied, Object auditMetadata) {}
}
