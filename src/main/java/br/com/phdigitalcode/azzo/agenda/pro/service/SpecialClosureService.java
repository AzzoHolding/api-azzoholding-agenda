package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SpecialClosureDto;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SpecialClosureImpactDto;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantSpecialClosureDate;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppAppointmentNotificationService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantSpecialClosureDateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.specification.TenantSpecialClosureDateSpecifications;

/**
 * Espelha {@code modules/settings/application/SpecialClosureService.java} — fechamentos especiais do
 * salao e de profissionais (feriados, folgas, recessos, eventos internos e bloqueios manuais).
 *
 * <p><b>Substitui o placeholder</b> {@code integration/SpecialClosureService}, cujo
 * {@code isClosedAt} devolvia sempre {@code false} e por isso deixava a agenda oferecer horario em
 * data que o salao marcou como fechada.
 *
 * <p><b>LGPD</b>: o campo {@code reason} NUNCA deve aparecer em logs nem em payloads de auditoria.
 * Em qualquer chamada de log use apenas {@code id} e {@code closureDate}; na auditoria ele entra
 * como a flag booleana {@code reason_preenchido}.
 *
 * <p>Diferenca de porte: o original lia o nome do cliente e do profissional pelas associacoes
 * {@code @ManyToOne} de {@code Agendamento}/{@code TenantSpecialClosureDateEntity}, que foram
 * descartadas na migracao (Etapa 6 / entidade {@code TenantSpecialClosureDate}). Aqui os nomes sao
 * resolvidos por repositorio, sempre filtrando por tenant, e em <b>lote</b> — o resultado e o mesmo,
 * ao custo de consultas extras.
 */
@Service
public class SpecialClosureService {

  private static final Logger LOG = LoggerFactory.getLogger(SpecialClosureService.class);

  private static final List<String> TIPOS_VALIDOS =
      List.of("HOLIDAY", "VACATION", "RECESS", "INTERNAL_EVENT", "MANUAL");

  private final TenantSpecialClosureDateRepository closureRepository;
  private final AgendamentoRepository agendamentoRepository;
  private final ClienteRepository clienteRepository;
  private final ProfissionalRepository profissionalRepository;
  private final AuditService auditService;
  private final WhatsAppAppointmentNotificationService whatsAppNotificationService;

  public SpecialClosureService(
      TenantSpecialClosureDateRepository closureRepository,
      AgendamentoRepository agendamentoRepository,
      ClienteRepository clienteRepository,
      ProfissionalRepository profissionalRepository,
      AuditService auditService,
      WhatsAppAppointmentNotificationService whatsAppNotificationService) {
    this.closureRepository = closureRepository;
    this.agendamentoRepository = agendamentoRepository;
    this.clienteRepository = clienteRepository;
    this.profissionalRepository = profissionalRepository;
    this.auditService = auditService;
    this.whatsAppNotificationService = whatsAppNotificationService;
  }

  @Transactional
  public List<SpecialClosureDto> listar(
      UUID tenantId, LocalDate from, LocalDate to, UUID professionalId) {
    List<TenantSpecialClosureDate> entities =
        closureRepository.findAll(
            TenantSpecialClosureDateSpecifications.filtro(tenantId, from, to, professionalId),
            org.springframework.data.domain.Sort.by("closureDate").ascending());
    Map<UUID, String> nomesPorProfissional = carregarNomesProfissionais(tenantId, entities);
    return entities.stream().map(entity -> toDto(entity, nomesPorProfissional)).toList();
  }

  /**
   * Cria um fechamento especial. Se houver agendamentos impactados, devolve a lista <b>sem criar
   * ainda</b>; o frontend deve confirmar via {@link #confirmar(UUID, SpecialClosureDto)} se quiser
   * prosseguir.
   *
   * <p>LGPD: reason nunca e logado nem auditado em claro.
   */
  @Transactional
  public SpecialClosureImpactDto criar(UUID tenantId, SpecialClosureDto dto) {
    validarTipo(dto.closureType);
    validarDatas(dto);

    List<Agendamento> impactados = buscarImpactados(tenantId, dto);
    if (!impactados.isEmpty()) {
      SpecialClosureImpactDto impact = new SpecialClosureImpactDto();
      impact.created = false;
      impact.impactedAppointments = toImpactedList(tenantId, impactados);
      // LGPD: logamos apenas id do tenant e data, nunca o reason
      LOG.info(
          "[SpecialClosure] Criacao pendente de confirmacao (tenantId={}, closureDate={},"
              + " impactados={})",
          tenantId,
          dto.closureDate,
          impactados.size());
      return impact;
    }

    TenantSpecialClosureDate entity = persistir(tenantId, dto);
    LOG.info(
        "[SpecialClosure] Fechamento criado (id={}, closureDate={}, tenantId={})",
        entity.getId(),
        entity.getClosureDate(),
        tenantId);

    registrarAuditoriaCreate(tenantId, entity);

    SpecialClosureImpactDto result = new SpecialClosureImpactDto();
    result.created = true;
    result.closureId = entity.getId();
    return result;
  }

  /**
   * Confirma a criacao de um fechamento mesmo com agendamentos impactados. Se
   * {@code notifyClients=true}, cancela os agendamentos impactados e notifica os clientes via
   * WhatsApp.
   *
   * <p>LGPD: reason nunca e logado nem auditado em claro. O template de notificacao nao menciona o
   * motivo do fechamento (LGPD art. 6, principio da necessidade).
   */
  @Transactional
  public SpecialClosureImpactDto confirmar(
      UUID tenantId, SpecialClosureDto dto, boolean notifyClients) {
    validarTipo(dto.closureType);
    validarDatas(dto);

    List<Agendamento> impactados = buscarImpactados(tenantId, dto);

    TenantSpecialClosureDate entity = persistir(tenantId, dto);
    LOG.info(
        "[SpecialClosure] Fechamento confirmado (id={}, closureDate={}, tenantId={},"
            + " impactados={}, notifyClients={})",
        entity.getId(),
        entity.getClosureDate(),
        tenantId,
        impactados.size(),
        notifyClients);

    registrarAuditoriaCreate(tenantId, entity);

    if (notifyClients && !impactados.isEmpty()) {
      for (Agendamento appt : impactados) {
        cancelarPorFechamento(appt, tenantId, entity.getId());
        notificarClienteFechamento(tenantId, appt);
      }
    }

    SpecialClosureImpactDto result = new SpecialClosureImpactDto();
    result.created = true;
    result.closureId = entity.getId();
    return result;
  }

  /** Mantem compatibilidade com chamadas sem o parametro {@code notifyClients}. */
  @Transactional
  public SpecialClosureImpactDto confirmar(UUID tenantId, SpecialClosureDto dto) {
    return confirmar(tenantId, dto, false);
  }

  @Transactional
  public void editar(UUID tenantId, UUID id, SpecialClosureDto dto) {
    TenantSpecialClosureDate entity =
        closureRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Fechamento nao encontrado"));
    validarTipo(dto.closureType);
    validarDatas(dto);

    // Snapshot before para detectar campos alterados (sem reason)
    Map<String, Object> before = snapshotSemReason(entity);

    entity.setClosureDate(dto.closureDate);
    entity.setClosureType(normalizarTipo(dto.closureType));
    entity.setAllDay(dto.allDay);
    entity.setStartTime(dto.allDay ? null : dto.startTime);
    entity.setEndTime(dto.allDay ? null : dto.endTime);
    entity.setReason(normalizarReason(dto.reason));
    entity.setProfessionalId(dto.professionalId);
    // LGPD: reason nunca e logado
    LOG.info(
        "[SpecialClosure] Fechamento editado (id={}, closureDate={}, tenantId={})",
        id,
        entity.getClosureDate(),
        tenantId);

    Map<String, Object> after = snapshotSemReason(entity);
    registrarAuditoriaUpdate(tenantId, entity, before, after);
  }

  @Transactional
  public void remover(UUID tenantId, UUID id) {
    TenantSpecialClosureDate entity =
        closureRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Fechamento nao encontrado"));
    LOG.info(
        "[SpecialClosure] Fechamento removido (id={}, closureDate={}, tenantId={})",
        id,
        entity.getClosureDate(),
        tenantId);

    // Captura antes de deletar
    Map<String, Object> before = snapshotSemReason(entity);
    closureRepository.delete(entity);

    registrarAuditoriaDelete(tenantId, id, before);
  }

  /**
   * Lista apenas datas com fechamento {@code all_day} do salao inteiro (sem profissional
   * especifico). Usado pelo calendario publico de booking.
   */
  @Transactional
  public List<LocalDate> listarDatasIndisponiveis(UUID tenantId, LocalDate from, LocalDate to) {
    if (from == null) from = LocalDate.now();
    if (to == null) to = from.plusMonths(3);
    return closureRepository.listDistinctDatesInRange(tenantId, from, to);
  }

  /** Verifica se o salao esta fechado na data (fechamento {@code all_day} sem profissional). */
  public boolean isClosedOnSpecialDate(UUID tenantId, LocalDate date) {
    if (tenantId == null || date == null) return false;
    return closureRepository.existsAllDayClosure(tenantId, date, null);
  }

  /**
   * Verificacao completa de fechamento para um agendamento, nesta ordem:
   *
   * <ol>
   *   <li>fechamento {@code all_day} do salao;
   *   <li>fechamento parcial do salao que conflita com {@code start}-{@code end};
   *   <li>fechamento {@code all_day} do profissional;
   *   <li>fechamento parcial do profissional que conflita.
   * </ol>
   *
   * <p>Este e o metodo consumido pelo motor de slots ({@code AppointmentService}) e, na parte 2 de
   * {@code scheduling}, pelo {@code ServicoAgendamentos} ao recusar a criacao em data fechada.
   */
  public boolean isClosedAt(
      UUID tenantId, UUID professionalId, LocalDate date, LocalTime start, LocalTime end) {
    if (tenantId == null || date == null) return false;

    if (closureRepository.existsAllDayClosure(tenantId, date, null)) return true;

    if (start != null
        && end != null
        && closureRepository.existsPartialClosure(tenantId, null, date, start, end)) return true;

    if (professionalId == null) return false;

    if (closureRepository.existsAllDayClosure(tenantId, date, professionalId)) return true;

    if (start != null
        && end != null
        && closureRepository.existsPartialClosure(tenantId, professionalId, date, start, end))
      return true;

    return false;
  }

  // ---- helpers de auditoria ----

  private void registrarAuditoriaCreate(UUID tenantId, TenantSpecialClosureDate entity) {
    try {
      AuditEventCommand cmd = new AuditEventCommand();
      cmd.tenantId = tenantId;
      cmd.module = AuditConstants.Module.SETTINGS;
      cmd.action = AuditConstants.Action.SPECIAL_CLOSURE_CREATED;
      cmd.entityType = "TenantSpecialClosureDate";
      cmd.entityId = entity.getId() != null ? entity.getId().toString() : null;
      cmd.sourceChannel = AuditConstants.SourceChannel.API;
      // LGPD: reason auditado apenas como flag booleana, nunca em claro.
      // HashMap e nao Map.of porque professional_id pode ser null (Map.of nao aceita).
      Map<String, Object> after = new HashMap<>();
      after.put("closure_date", String.valueOf(entity.getClosureDate()));
      after.put("closure_type", safeStr(entity.getClosureType()));
      after.put("all_day", entity.isAllDay());
      after.put(
          "professional_id",
          entity.getProfessionalId() != null ? entity.getProfessionalId().toString() : null);
      after.put(
          "reason_preenchido", entity.getReason() != null && !entity.getReason().isBlank());
      cmd.after = after;
      auditService.recordSuccess(cmd);
    } catch (Exception e) {
      LOG.warn("[SpecialClosure] Falha ao registrar auditoria CREATE (id={})", entity.getId(), e);
    }
  }

  private void registrarAuditoriaUpdate(
      UUID tenantId,
      TenantSpecialClosureDate entity,
      Map<String, Object> before,
      Map<String, Object> after) {
    try {
      AuditEventCommand cmd = new AuditEventCommand();
      cmd.tenantId = tenantId;
      cmd.module = AuditConstants.Module.SETTINGS;
      cmd.action = AuditConstants.Action.SPECIAL_CLOSURE_UPDATED;
      cmd.entityType = "TenantSpecialClosureDate";
      cmd.entityId = entity.getId() != null ? entity.getId().toString() : null;
      cmd.sourceChannel = AuditConstants.SourceChannel.API;
      cmd.before = before;
      cmd.after = after;
      auditService.recordSuccess(cmd);
    } catch (Exception e) {
      LOG.warn("[SpecialClosure] Falha ao registrar auditoria UPDATE (id={})", entity.getId(), e);
    }
  }

  private void registrarAuditoriaDelete(UUID tenantId, UUID id, Map<String, Object> before) {
    try {
      AuditEventCommand cmd = new AuditEventCommand();
      cmd.tenantId = tenantId;
      cmd.module = AuditConstants.Module.SETTINGS;
      cmd.action = AuditConstants.Action.SPECIAL_CLOSURE_DELETED;
      cmd.entityType = "TenantSpecialClosureDate";
      cmd.entityId = id != null ? id.toString() : null;
      cmd.sourceChannel = AuditConstants.SourceChannel.API;
      cmd.before = before;
      auditService.recordSuccess(cmd);
    } catch (Exception e) {
      LOG.warn("[SpecialClosure] Falha ao registrar auditoria DELETE (id={})", id, e);
    }
  }

  /**
   * Snapshot seguro da entidade sem o campo {@code reason} (LGPD). Registra apenas flag booleana
   * indicando se {@code reason} estava preenchido.
   */
  private Map<String, Object> snapshotSemReason(TenantSpecialClosureDate entity) {
    return Map.of(
        "closure_date", String.valueOf(entity.getClosureDate()),
        "closure_type", safeStr(entity.getClosureType()),
        "all_day", entity.isAllDay(),
        "start_time", entity.getStartTime() != null ? entity.getStartTime().toString() : "",
        "end_time", entity.getEndTime() != null ? entity.getEndTime().toString() : "",
        "professional_id",
            entity.getProfessionalId() != null ? entity.getProfessionalId().toString() : "",
        "reason_preenchido", entity.getReason() != null && !entity.getReason().isBlank());
  }

  // ---- cancelamento e notificacao ----

  /**
   * Cancela um agendamento impactado por fechamento especial. Usa {@code CANCELLED} (transicao
   * valida a partir de {@code PENDING} e {@code CONFIRMED}).
   *
   * <p>LGPD: nao loga dados pessoais do cliente (nome, telefone).
   */
  private void cancelarPorFechamento(Agendamento appt, UUID tenantId, UUID closureId) {
    try {
      if (appt.getStatus() == null
          || !appt.getStatus().canTransitionTo(StatusAgendamento.CANCELLED)) {
        LOG.debug(
            "[SpecialClosure] Agendamento {} nao pode ser cancelado (status={})",
            appt.getId(),
            appt.getStatus());
        return;
      }
      appt.setStatus(StatusAgendamento.CANCELLED);

      AuditEventCommand cmd = new AuditEventCommand();
      cmd.tenantId = tenantId;
      cmd.module = AuditConstants.Module.APPOINTMENT;
      cmd.action = AuditConstants.Action.APPOINTMENT_CANCELLED_BY_CLOSURE;
      cmd.entityType = "Agendamento";
      cmd.entityId = appt.getId() != null ? appt.getId().toString() : null;
      cmd.sourceChannel = AuditConstants.SourceChannel.SYSTEM;
      cmd.metadata =
          Map.of(
              "closure_id", closureId != null ? closureId.toString() : "",
              "appointment_date", appt.getDate() != null ? appt.getDate().toString() : "");
      auditService.recordSuccess(cmd);

      LOG.info("[SpecialClosure] Agendamento {} cancelado por fechamento {}", appt.getId(), closureId);
    } catch (Exception e) {
      LOG.warn("[SpecialClosure] Falha ao cancelar agendamento {} por fechamento", appt.getId(), e);
    }
  }

  /**
   * Notifica o cliente via WhatsApp sobre o cancelamento do agendamento por fechamento.
   *
   * <p>LGPD: o template nao inclui o motivo do fechamento ({@code reason}) nem dados pessoais do
   * profissional. O cliente recebe apenas data, horario e telefone de contato.
   */
  private void notificarClienteFechamento(UUID tenantId, Agendamento appt) {
    try {
      // Reutiliza o mecanismo de sendCancellation ja existente. O
      // WhatsAppAppointmentNotificationService usa o template configurado pelo tenant
      // (DEFAULT_CANCELLATION), que nao expoe motivo nem dados de profissional.
      whatsAppNotificationService.sendCancellation(tenantId, appt);
    } catch (Exception e) {
      LOG.warn("[SpecialClosure] Falha ao notificar cliente do agendamento {}", appt.getId(), e);
    }
  }

  // ---- helpers de persistencia ----

  private TenantSpecialClosureDate persistir(UUID tenantId, SpecialClosureDto dto) {
    TenantSpecialClosureDate entity = new TenantSpecialClosureDate();
    entity.setTenantId(tenantId);
    entity.setClosureDate(dto.closureDate);
    entity.setClosureType(normalizarTipo(dto.closureType));
    entity.setAllDay(dto.allDay);
    entity.setStartTime(dto.allDay ? null : dto.startTime);
    entity.setEndTime(dto.allDay ? null : dto.endTime);
    entity.setReason(normalizarReason(dto.reason));
    entity.setProfessionalId(dto.professionalId);
    // saveAndFlush porque o chamador le o id gerado logo em seguida (o @PrePersist so roda no
    // flush) e devolve `closureId` na resposta — com Panache o persist() ja emitia o INSERT.
    return closureRepository.saveAndFlush(entity);
  }

  private List<Agendamento> buscarImpactados(UUID tenantId, SpecialClosureDto dto) {
    return agendamentoRepository.listImpactedByClosure(
        tenantId,
        dto.closureDate,
        dto.allDay,
        dto.startTime != null ? dto.startTime.toString() : null,
        dto.endTime != null ? dto.endTime.toString() : null,
        dto.professionalId);
  }

  private SpecialClosureDto toDto(
      TenantSpecialClosureDate entity, Map<UUID, String> nomesPorProfissional) {
    SpecialClosureDto dto = new SpecialClosureDto();
    dto.id = entity.getId();
    dto.closureDate = entity.getClosureDate();
    dto.closureType = entity.getClosureType();
    dto.allDay = entity.isAllDay();
    dto.startTime = entity.getStartTime();
    dto.endTime = entity.getEndTime();
    dto.reason = entity.getReason();
    dto.professionalId = entity.getProfessionalId();
    if (entity.getProfessionalId() != null) {
      dto.professionalName = nomesPorProfissional.get(entity.getProfessionalId());
    }
    return dto;
  }

  private Map<UUID, String> carregarNomesProfissionais(
      UUID tenantId, List<TenantSpecialClosureDate> entities) {
    Set<UUID> ids = new LinkedHashSet<>();
    for (TenantSpecialClosureDate entity : entities) {
      if (entity.getProfessionalId() != null) ids.add(entity.getProfessionalId());
    }
    if (ids.isEmpty()) return Map.of();
    Map<UUID, String> nomes = new HashMap<>();
    for (Profissional profissional :
        profissionalRepository.findByIdInAndTenantId(new ArrayList<>(ids), tenantId)) {
      nomes.put(profissional.getId(), profissional.getName());
    }
    return nomes;
  }

  private List<SpecialClosureImpactDto.ImpactedAppointment> toImpactedList(
      UUID tenantId, List<Agendamento> agendamentos) {
    Map<UUID, String> nomesClientes = carregarNomesClientes(tenantId, agendamentos);
    Map<UUID, String> nomesProfissionais = carregarNomesProfissionaisDeAgendamentos(tenantId, agendamentos);

    List<SpecialClosureImpactDto.ImpactedAppointment> result = new ArrayList<>();
    for (Agendamento a : agendamentos) {
      SpecialClosureImpactDto.ImpactedAppointment item =
          new SpecialClosureImpactDto.ImpactedAppointment();
      item.appointmentId = a.getId();
      item.clientName = a.getClientId() != null ? nomesClientes.get(a.getClientId()) : null;
      item.professionalName =
          a.getProfessionalId() != null ? nomesProfissionais.get(a.getProfessionalId()) : null;
      item.date = a.getDate() != null ? a.getDate().toString() : null;
      item.startTime = a.getStartTime();
      item.endTime = a.getEndTime();
      item.status = a.getStatus() != null ? a.getStatus().name() : null;
      result.add(item);
    }
    return result;
  }

  private Map<UUID, String> carregarNomesClientes(UUID tenantId, List<Agendamento> agendamentos) {
    Set<UUID> ids = new LinkedHashSet<>();
    for (Agendamento a : agendamentos) {
      if (a.getClientId() != null) ids.add(a.getClientId());
    }
    if (ids.isEmpty()) return Map.of();
    Map<UUID, String> nomes = new HashMap<>();
    for (UUID id : ids) {
      clienteRepository
          .findByIdAndTenantId(id, tenantId)
          .map(Cliente::getName)
          .ifPresent(name -> nomes.put(id, name));
    }
    return nomes;
  }

  private Map<UUID, String> carregarNomesProfissionaisDeAgendamentos(
      UUID tenantId, List<Agendamento> agendamentos) {
    Set<UUID> ids = new LinkedHashSet<>();
    for (Agendamento a : agendamentos) {
      if (a.getProfessionalId() != null) ids.add(a.getProfessionalId());
    }
    if (ids.isEmpty()) return Map.of();
    Map<UUID, String> nomes = new HashMap<>();
    for (Profissional profissional :
        profissionalRepository.findByIdInAndTenantId(new ArrayList<>(ids), tenantId)) {
      nomes.put(profissional.getId(), profissional.getName());
    }
    return nomes;
  }

  private void validarTipo(String closureType) {
    if (closureType != null
        && !closureType.isBlank()
        && !TIPOS_VALIDOS.contains(closureType.toUpperCase(Locale.ROOT))) {
      throw new IllegalArgumentException(
          "Tipo de fechamento invalido. Valores aceitos: " + String.join(", ", TIPOS_VALIDOS));
    }
  }

  private void validarDatas(SpecialClosureDto dto) {
    if (dto.closureDate == null) {
      throw new IllegalArgumentException("Data de fechamento obrigatoria");
    }
    if (!dto.allDay) {
      if (dto.startTime == null || dto.endTime == null) {
        throw new IllegalArgumentException(
            "Para fechamento parcial, startTime e endTime sao obrigatorios");
      }
      if (!dto.startTime.isBefore(dto.endTime)) {
        throw new IllegalArgumentException(
            "startTime deve ser anterior a endTime no fechamento parcial");
      }
    }
  }

  private String normalizarTipo(String closureType) {
    if (closureType == null || closureType.isBlank()) return "MANUAL";
    return closureType.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizarReason(String reason) {
    if (reason == null) return null;
    String normalized = reason.trim();
    if (normalized.isBlank()) return null;
    return normalized.substring(0, Math.min(normalized.length(), 160));
  }

  private String safeStr(String value) {
    return value != null ? value : "";
  }
}
