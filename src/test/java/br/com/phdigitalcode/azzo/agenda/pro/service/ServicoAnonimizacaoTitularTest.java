package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentCustomerNote;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequest;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentCustomerNoteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatConversationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NotificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Espelha {@code modules/lgpd/application/ServicoAnonimizacaoTitular.java}. */
class ServicoAnonimizacaoTitularTest {

  private final UUID tenantId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  private ContextoTenant contextoTenant;
  private AuthenticatedUser authenticatedUser;
  private ClienteRepository clienteRepository;
  private AppointmentCustomerNoteRepository noteRepository;
  private LgpdDataSubjectRequestRepository requestRepository;
  private LgpdDataSubjectRequestEventRepository eventRepository;
  private ChatConversationRepository conversationRepository;
  private ChatMessageRepository chatMessageRepository;
  private NotificationRepository notificationRepository;
  private WhatsAppMessageLogRepository whatsAppMessageLogRepository;
  private WhatsAppBookingReactivationCycleRepository reactivationCycleRepository;
  private AuditService auditService;
  private ServicoAnonimizacaoTitular service;

  @BeforeEach
  void setUp() {
    contextoTenant = mock(ContextoTenant.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    clienteRepository = mock(ClienteRepository.class);
    noteRepository = mock(AppointmentCustomerNoteRepository.class);
    requestRepository = mock(LgpdDataSubjectRequestRepository.class);
    eventRepository = mock(LgpdDataSubjectRequestEventRepository.class);
    conversationRepository = mock(ChatConversationRepository.class);
    chatMessageRepository = mock(ChatMessageRepository.class);
    notificationRepository = mock(NotificationRepository.class);
    whatsAppMessageLogRepository = mock(WhatsAppMessageLogRepository.class);
    reactivationCycleRepository = mock(WhatsAppBookingReactivationCycleRepository.class);
    auditService = mock(AuditService.class);
    service = new ServicoAnonimizacaoTitular(
        contextoTenant, authenticatedUser, clienteRepository, noteRepository, requestRepository,
        eventRepository, conversationRepository, chatMessageRepository, notificationRepository,
        whatsAppMessageLogRepository, reactivationCycleRepository, auditService);

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(requestRepository.findByTenantAndProtocol(any(), any())).thenReturn(Optional.empty());
    when(noteRepository.listByTenantAndClient(any(), any())).thenReturn(List.of());
    // O mock do repositorio nao dispara @PrePersist (isso so acontece com um EntityManager real) —
    // simula aqui o id que o Hibernate atribuiria antes do insert, ja que o service usa
    // lgpdRequest.getId() logo em seguida (evento + comando de auditoria).
    when(requestRepository.save(any(LgpdDataSubjectRequest.class))).thenAnswer(invocation -> {
      LgpdDataSubjectRequest entity = invocation.getArgument(0);
      if (entity.getId() == null) entity.setId(UUID.randomUUID());
      return entity;
    });
  }

  private Cliente buildCliente() {
    Cliente cliente = new Cliente();
    cliente.setId(clientId);
    cliente.setTenantId(tenantId);
    cliente.setName("Fulano de Tal");
    cliente.setEmail("fulano@example.com");
    cliente.setPhone("+5511999999999");
    cliente.setCpfCnpj("12345678900");
    cliente.setWhatsappOptIn(true);
    return cliente;
  }

  @Test
  void anonimizarLimpaCamposPessoaisDoClienteERegistraSolicitacaoEncerrada() {
    Cliente cliente = buildCliente();
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(cliente));

    ServicoAnonimizacaoTitular.AnonimizacaoResponse response = service.anonimizar(clientId);

    assertThat(cliente.getName()).isEqualTo("[ANONIMIZADO]");
    assertThat(cliente.getEmail()).isNull();
    assertThat(cliente.getPhone()).isNull();
    assertThat(cliente.getCpfCnpj()).isNull();
    assertThat(cliente.getWhatsappOptIn()).isFalse();
    assertThat(cliente.getWhatsappOptOut()).isTrue();
    assertThat(cliente.getAnonymizedAt()).isNotNull();

    assertThat(response.clientId()).isEqualTo(clientId.toString());
    assertThat(response.protocolCode()).startsWith("LGPD-");
    assertThat(response.notesAnonymized()).isEqualTo(0);

    verify(requestRepository).save(any(LgpdDataSubjectRequest.class));
    verify(eventRepository).save(any());
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void anonimizarLimpaNotasDeAtendimentoAssociadas() {
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(buildCliente()));
    AppointmentCustomerNote note = new AppointmentCustomerNote();
    note.setServiceExecutionNotes("nota sensivel");
    note.setClientFeedbackNotes("feedback sensivel");
    note.setInternalFollowupNotes("followup sensivel");
    when(noteRepository.listByTenantAndClient(tenantId, clientId)).thenReturn(List.of(note));

    ServicoAnonimizacaoTitular.AnonimizacaoResponse response = service.anonimizar(clientId);

    assertThat(note.getServiceExecutionNotes()).isNull();
    assertThat(note.getClientFeedbackNotes()).isNull();
    assertThat(note.getInternalFollowupNotes()).isNull();
    assertThat(response.notesAnonymized()).isEqualTo(1);
  }

  @Test
  void anonimizarLancaNotFoundQuandoClienteNaoEncontrado() {
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.anonimizar(clientId))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void anonimizarLancaBadRequestQuandoJaAnonimizado() {
    Cliente cliente = buildCliente();
    cliente.setAnonymizedAt(java.time.Instant.now());
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(cliente));

    assertThatThrownBy(() -> service.anonimizar(clientId))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  void anonimizarNaoQuebraQuandoAuditoriaFalha() {
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(buildCliente()));
    when(auditService.recordSuccess(any())).thenThrow(new RuntimeException("falhou"));

    ServicoAnonimizacaoTitular.AnonimizacaoResponse response = service.anonimizar(clientId);

    assertThat(response).isNotNull();
  }

  /**
   * O dado pessoal do titular nao mora so na ficha dele.
   *
   * Ate 2026-09-20 a anonimizacao parava no cliente e nas notas: o telefone continuava no chat,
   * nas notificacoes, no log do WhatsApp e na fila de reativacao — pedido de exclusao atendido
   * pela metade.
   */
  @Test
  void anonimizarAlcancaChatNotificacoesEReativacao() {
    Cliente cliente = buildCliente();
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(cliente));

    service.anonimizar(clientId);

    verify(conversationRepository).anonimizarPorClienteRaw(tenantId, clientId);
    verify(chatMessageRepository).anonimizarPorClienteRaw(tenantId, clientId);
    verify(notificationRepository).anonimizarPorClienteRaw(tenantId, clientId);
    verify(whatsAppMessageLogRepository).anonimizarPorClienteRaw(tenantId, clientId);
    verify(reactivationCycleRepository).anonimizarPorClienteRaw(tenantId, clientId);
  }
}
