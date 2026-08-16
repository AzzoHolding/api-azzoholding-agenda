package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentCustomerNote;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequestEvent;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentCustomerNoteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Espelha {@code modules/lgpd/application/ServicoAnonimizacaoTitular.java}.
 *
 * <p>{@code TraceContext.traceId()} (Skywalking, sem equivalente configurado neste projeto) foi
 * omitido dos logs, mesmo criterio ja usado em outras fronteiras (ex.:
 * {@code WhatsAppBookingReactivationSchedulerService}).
 */
@Service
public class ServicoAnonimizacaoTitular {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoAnonimizacaoTitular.class);
  private static final String PLACEHOLDER = "[ANONIMIZADO]";

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final ClienteRepository clienteRepository;
  private final AppointmentCustomerNoteRepository noteRepository;
  private final LgpdDataSubjectRequestRepository requestRepository;
  private final LgpdDataSubjectRequestEventRepository eventRepository;
  private final AuditService auditService;

  public ServicoAnonimizacaoTitular(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      ClienteRepository clienteRepository,
      AppointmentCustomerNoteRepository noteRepository,
      LgpdDataSubjectRequestRepository requestRepository,
      LgpdDataSubjectRequestEventRepository eventRepository,
      AuditService auditService) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.clienteRepository = clienteRepository;
    this.noteRepository = noteRepository;
    this.requestRepository = requestRepository;
    this.eventRepository = eventRepository;
    this.auditService = auditService;
  }

  @Transactional
  public AnonimizacaoResponse anonimizar(UUID clientId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    Cliente cliente = clienteRepository.findByIdAndTenantId(clientId, tenantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente nao encontrado"));

    if (cliente.getAnonymizedAt() != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente ja foi anonimizado");
    }

    Instant now = Instant.now();

    // Anonimiza dados pessoais; mantém id, tenantId e createdAt para integridade referencial
    cliente.setName(PLACEHOLDER);
    cliente.setEmail(null);
    cliente.setPhone(null);
    cliente.setCpfCnpj(null);
    cliente.setNotes(null);
    cliente.setAvatar(null);
    cliente.setBirthDate(null);
    cliente.setZipCode(null);
    cliente.setStreet(null);
    cliente.setNumber(null);
    cliente.setComplement(null);
    cliente.setNeighborhood(null);
    cliente.setCity(null);
    cliente.setState(null);
    cliente.setWhatsappOptIn(false);
    cliente.setWhatsappOptOut(true);
    cliente.setWhatsappOptInAt(null);
    cliente.setWhatsappOptOutAt(null);
    cliente.setAnonymizedAt(now);

    // Anonimiza notas de atendimento associadas ao cliente
    List<AppointmentCustomerNote> notes = noteRepository.listByTenantAndClient(tenantId, clientId);
    for (AppointmentCustomerNote note : notes) {
      note.setServiceExecutionNotes(null);
      note.setClientFeedbackNotes(null);
      note.setInternalFollowupNotes(null);
    }

    // Registra solicitação LGPD do tipo EXCLUSAO já encerrada (art. 18, VI)
    LgpdDataSubjectRequest lgpdRequest = new LgpdDataSubjectRequest();
    lgpdRequest.setTenantId(tenantId);
    lgpdRequest.setProtocolCode(gerarProtocolo(tenantId, now));
    lgpdRequest.setRequestType("EXCLUSAO");
    lgpdRequest.setStatus("ENCERRADO");
    lgpdRequest.setRequesterName(PLACEHOLDER);
    lgpdRequest.setRequesterEmail("anonimizado@sistema");
    lgpdRequest.setRequesterDocument(null);
    lgpdRequest.setDescription("Anonimizacao executada pelo operador para cliente " + clientId);
    lgpdRequest.setResponseSummary(
        "Dados pessoais anonimizados. Historico financeiro e de agendamentos preservado para obrigacao fiscal.");
    lgpdRequest.setCreatedByUserId(obterActorId());
    lgpdRequest.setClosedAt(now);
    requestRepository.save(lgpdRequest);

    LgpdDataSubjectRequestEvent event = new LgpdDataSubjectRequestEvent();
    event.setTenantId(tenantId);
    event.setRequestId(lgpdRequest.getId());
    event.setEventType("ANONYMIZATION_EXECUTED");
    event.setPreviousStatus("ABERTO");
    event.setNewStatus("ENCERRADO");
    event.setEventNote("Anonimizacao automatica via operador. " + notes.size() + " nota(s) de atendimento anonimizadas.");
    event.setActorUserId(obterActorId());
    eventRepository.save(event);

    auditar(tenantId, clientId, lgpdRequest.getId(), notes.size());

    LOG.info(
        "lgpd_anonymization_completed clientId={} tenantId={} protocol={} notesAnonymized={}",
        clientId, tenantId, lgpdRequest.getProtocolCode(), notes.size());

    return new AnonimizacaoResponse(
        clientId.toString(),
        lgpdRequest.getProtocolCode(),
        now.toString(),
        notes.size());
  }

  public record AnonimizacaoResponse(
      String clientId,
      String protocolCode,
      String anonymizedAt,
      int notesAnonymized) {}

  private String gerarProtocolo(UUID tenantId, Instant now) {
    String date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now);
    for (int i = 0; i < 5; i++) {
      String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
      String protocol = "LGPD-" + date + "-" + suffix;
      if (requestRepository.findByTenantAndProtocol(tenantId, protocol).isEmpty()) return protocol;
    }
    return "LGPD-" + date + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  private UUID obterActorId() {
    try {
      return authenticatedUser.idOuNulo();
    } catch (Exception e) {
      return null;
    }
  }

  private void auditar(UUID tenantId, UUID clientId, UUID lgpdRequestId, int notesCount) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = obterActorId();
      command.module = AuditConstants.Module.LGPD;
      command.action = "LGPD_CLIENT_ANONYMIZATION";
      command.entityType = "CLIENT";
      command.entityId = clientId.toString();
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.after = Map.of(
          "clientId", clientId.toString(),
          "lgpdRequestId", lgpdRequestId.toString(),
          "notesAnonymized", notesCount,
          "compliance", "LGPD art. 18 VI");
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      LOG.warn("lgpd_audit_failed clientId={} tenantId={}", clientId, tenantId);
    }
  }
}
