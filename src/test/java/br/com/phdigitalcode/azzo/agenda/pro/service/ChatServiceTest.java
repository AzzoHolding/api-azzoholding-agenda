package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ChatDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatMessageEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatConversationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.CommunicationChannelDispatcher;

/**
 * Cobre o porte de {@code ChatService} do original: roteamento outbound, envio (sucesso/falha/
 * opt-out), marcador de agendamento, leitura de conversa, exclusao, e a corrida de dedup
 * inbound (cliente/conversa) coberta por {@code ChatServiceInboundDedupRaceUnitTest}.
 */
class ChatServiceTest {

  private ContextoTenant contextoTenant;
  private ChatConversationRepository chatConversationRepository;
  private ChatMessageRepository chatMessageRepository;
  private ClienteRepository clienteRepository;
  private CommunicationChannelDispatcher communicationChannelDispatcher;
  private CustomerCommunicationChannelResolver customerCommunicationChannelResolver;
  private AuditService auditService;
  private AuthenticatedUser authenticatedUser;
  private EncryptionService encryptionService;
  private ChatRealtimePublisher chatRealtimePublisher;
  private WhatsAppBookingReactivationService whatsAppBookingReactivationService;
  private PlatformTransactionManager transactionManager;
  private ChatService service;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    contextoTenant = mock(ContextoTenant.class);
    chatConversationRepository = mock(ChatConversationRepository.class);
    chatMessageRepository = mock(ChatMessageRepository.class);
    clienteRepository = mock(ClienteRepository.class);
    communicationChannelDispatcher = mock(CommunicationChannelDispatcher.class);
    customerCommunicationChannelResolver = mock(CustomerCommunicationChannelResolver.class);
    auditService = mock(AuditService.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    encryptionService = mock(EncryptionService.class);
    chatRealtimePublisher = mock(ChatRealtimePublisher.class);
    whatsAppBookingReactivationService = mock(WhatsAppBookingReactivationService.class);
    transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(mock(TransactionStatus.class));

    // EncryptionService: passthrough para simplificar as asserções de conteúdo.
    when(encryptionService.encrypt(any())).thenAnswer((InvocationOnMock inv) -> "enc:" + inv.getArgument(0));
    when(encryptionService.decrypt(any())).thenAnswer((InvocationOnMock inv) -> {
      String value = inv.getArgument(0);
      return value.startsWith("enc:") ? value.substring(4) : value;
    });
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);

    service = new ChatService(
        contextoTenant,
        chatConversationRepository,
        chatMessageRepository,
        clienteRepository,
        communicationChannelDispatcher,
        customerCommunicationChannelResolver,
        auditService,
        authenticatedUser,
        encryptionService,
        chatRealtimePublisher,
        whatsAppBookingReactivationService,
        transactionManager,
        1,
        2,
        5,
        24);
  }

  // ---- resolveOutboundRoute ----

  @Test
  void deveUsarCanalTelegramDaConversaMaisRecente() {
    UUID clientId = UUID.randomUUID();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setClientId(clientId);
    conversation.setTenantId(tenantId);
    conversation.setChannel(ChatChannel.TELEGRAM);
    conversation.setExternalContactId("123456789");

    when(customerCommunicationChannelResolver.resolve(eq(tenantId), any(), any(String[].class)))
        .thenReturn(new CustomerCommunicationChannelResolver.ResolvedChannel(
            ChatChannel.TELEGRAM, "123456789", conversation));

    Cliente client = new Cliente();
    client.setId(clientId);
    client.setPhone("5511999991111");

    ChatService.OutboundRoute route = service.resolveOutboundRoute(tenantId, client);

    assertThat(route.channel()).isEqualTo(ChatChannel.TELEGRAM);
    assertThat(route.destination()).isEqualTo("123456789");
    assertThat(route.conversation()).isEqualTo(conversation);
  }

  @Test
  void deveUsarWhatsAppQuandoNaoHouverConversaAnterior() {
    UUID clientId = UUID.randomUUID();
    Cliente client = new Cliente();
    client.setId(clientId);
    client.setPhone("(11) 99999-1111");

    when(customerCommunicationChannelResolver.resolve(eq(tenantId), any(), any(String[].class)))
        .thenReturn(new CustomerCommunicationChannelResolver.ResolvedChannel(
            ChatChannel.WHATSAPP, client.getPhone(), null));

    ChatService.OutboundRoute route = service.resolveOutboundRoute(tenantId, client);

    assertThat(route.channel()).isEqualTo(ChatChannel.WHATSAPP);
    assertThat(route.destination()).isEqualTo("11999991111");
  }

  @Test
  void deveFalharQuandoConversaTelegramNaoTiverDestino() {
    Cliente client = new Cliente();
    client.setId(UUID.randomUUID());
    client.setPhone("5511999991111");

    when(customerCommunicationChannelResolver.resolve(eq(tenantId), any(), any(String[].class)))
        .thenThrow(new IllegalArgumentException("Conversa Telegram sem destino configurado."));

    assertThatThrownBy(() -> service.resolveOutboundRoute(tenantId, client))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- sendMessage ----

  @Test
  void sendMessageFalhaQuandoClienteNaoEncontrado() {
    when(clienteRepository.findByIdAndTenantId(any(), eq(tenantId))).thenReturn(Optional.empty());
    ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
    request.clientId = UUID.randomUUID().toString();
    request.content = "Ola";

    assertThatThrownBy(() -> service.sendMessage(request))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(404));
  }

  @Test
  void sendMessageFalhaQuandoClienteOptouPorNaoReceber() {
    UUID clientId = UUID.randomUUID();
    Cliente client = new Cliente();
    client.setId(clientId);
    client.setPhone("5511999991111");
    client.setWhatsappOptOut(true);
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(client));

    ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
    request.clientId = clientId.toString();
    request.content = "Ola";

    assertThatThrownBy(() -> service.sendMessage(request))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(422));
  }

  @Test
  void sendMessageComSucessoMarcaSentEPublicaAtualizacao() {
    UUID clientId = UUID.randomUUID();
    Cliente client = new Cliente();
    client.setId(clientId);
    client.setPhone("5511999991111");
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(client));
    when(customerCommunicationChannelResolver.resolve(eq(tenantId), any(), any(String[].class)))
        .thenReturn(new CustomerCommunicationChannelResolver.ResolvedChannel(
            ChatChannel.WHATSAPP, "5511999991111", null));

    ChatConversationEntity persistedConversation = new ChatConversationEntity();
    persistedConversation.setId(UUID.randomUUID());
    persistedConversation.setTenantId(tenantId);
    persistedConversation.setClientId(clientId);
    persistedConversation.setChannel(ChatChannel.WHATSAPP);
    when(chatConversationRepository.saveAndFlush(any())).thenAnswer(inv -> {
      ChatConversationEntity entity = inv.getArgument(0);
      entity.setId(persistedConversation.getId());
      return entity;
    });
    when(chatConversationRepository.findById(persistedConversation.getId()))
        .thenReturn(Optional.of(persistedConversation));

    when(communicationChannelDispatcher.sendText(any())).thenReturn(ChannelSendResult.sent("wamid-1"));
    when(authenticatedUser.temRole(any())).thenReturn(false);

    ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
    request.clientId = clientId.toString();
    request.content = "Ola cliente";

    ChatDtos.SendMessageResponse response = service.sendMessage(request);

    assertThat(response.status).isEqualTo("SENT");
    assertThat(response.conversationId).isEqualTo(persistedConversation.getId().toString());
    verify(chatRealtimePublisher).publishChatUpdate(eq(tenantId), eq(persistedConversation.getId()), eq(clientId), eq("OUTBOUND_SENT"));
    verify(chatMessageRepository).save(any(ChatMessageEntity.class));
  }

  @Test
  void sendMessageFalhaNoCanalMarcaFailed() {
    UUID clientId = UUID.randomUUID();
    Cliente client = new Cliente();
    client.setId(clientId);
    client.setPhone("5511999991111");
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(client));

    ChatConversationEntity existingConversation = new ChatConversationEntity();
    existingConversation.setId(UUID.randomUUID());
    existingConversation.setTenantId(tenantId);
    existingConversation.setClientId(clientId);
    existingConversation.setChannel(ChatChannel.WHATSAPP);
    when(customerCommunicationChannelResolver.resolve(eq(tenantId), any(), any(String[].class)))
        .thenReturn(new CustomerCommunicationChannelResolver.ResolvedChannel(
            ChatChannel.WHATSAPP, "5511999991111", existingConversation));

    when(communicationChannelDispatcher.sendText(any()))
        .thenReturn(ChannelSendResult.failed("WHATSAPP_NOT_ENABLED", "WhatsApp nao habilitado"));
    when(authenticatedUser.temRole(any())).thenReturn(false);

    ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
    request.clientId = clientId.toString();
    request.content = "Ola cliente";

    ChatDtos.SendMessageResponse response = service.sendMessage(request);

    assertThat(response.status).isEqualTo("FAILED");
    verify(chatRealtimePublisher).publishChatUpdate(eq(tenantId), eq(existingConversation.getId()), eq(clientId), eq("OUTBOUND_FAILED"));
  }

  @Test
  void sendMessageHabilitaModoManualQuandoAtorEProfissional() {
    UUID clientId = UUID.randomUUID();
    Cliente client = new Cliente();
    client.setId(clientId);
    client.setPhone("5511999991111");
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(client));

    ChatConversationEntity existingConversation = new ChatConversationEntity();
    existingConversation.setId(UUID.randomUUID());
    existingConversation.setTenantId(tenantId);
    existingConversation.setClientId(clientId);
    existingConversation.setChannel(ChatChannel.WHATSAPP);
    when(customerCommunicationChannelResolver.resolve(eq(tenantId), any(), any(String[].class)))
        .thenReturn(new CustomerCommunicationChannelResolver.ResolvedChannel(
            ChatChannel.WHATSAPP, "5511999991111", existingConversation));
    when(communicationChannelDispatcher.sendText(any())).thenReturn(ChannelSendResult.sent("wamid-9"));
    when(authenticatedUser.temRole("PROFESSIONAL")).thenReturn(true);

    ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
    request.clientId = clientId.toString();
    request.content = "Ola cliente";

    service.sendMessage(request);

    assertThat(existingConversation.getManualModeUntil()).isNotNull();
    assertThat(existingConversation.getManualModeReason()).isEqualTo("HUMAN_OUTBOUND_MESSAGE");
    verify(whatsAppBookingReactivationService).cancelCyclesForManualMode(existingConversation);
  }

  // ---- updateMarker / markConversationRead / deleteConversation ----

  @Test
  void updateMarkerAtualizaMarcadorEExpiracao() {
    UUID convId = UUID.randomUUID();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setId(convId);
    conversation.setTenantId(tenantId);
    conversation.setClientId(UUID.randomUUID());
    when(chatConversationRepository.findByTenantAndId(tenantId, convId)).thenReturn(Optional.of(conversation));

    ChatDtos.UpdateMarkerRequest request = new ChatDtos.UpdateMarkerRequest();
    request.appointmentMarker = "CONCLUIDO";

    ChatDtos.UpdateMarkerResponse response = service.updateMarker(convId.toString(), request);

    assertThat(response.appointmentMarker).isEqualTo("CONCLUIDO");
    verify(chatMessageRepository).updateExpiresAtByConversation(eq(tenantId), eq(convId), any(Instant.class));
    verify(chatRealtimePublisher).publishChatUpdate(eq(tenantId), eq(convId), any(), eq("MARKER_UPDATED"));
  }

  @Test
  void markConversationReadMarcaMensagensEPublicaAtualizacao() {
    UUID convId = UUID.randomUUID();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setId(convId);
    conversation.setTenantId(tenantId);
    conversation.setClientId(UUID.randomUUID());
    when(chatConversationRepository.findByTenantAndId(tenantId, convId)).thenReturn(Optional.of(conversation));

    service.markConversationRead(convId.toString());

    verify(chatMessageRepository).markInboundMessagesRead(eq(tenantId), eq(convId), any(Instant.class));
    verify(chatConversationRepository).clearUnread(tenantId, convId);
    verify(chatRealtimePublisher).publishChatUpdate(eq(tenantId), eq(convId), any(), eq("CONVERSATION_READ"));
  }

  @Test
  void deleteConversationRemoveMensagensEConversa() {
    UUID convId = UUID.randomUUID();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setId(convId);
    conversation.setTenantId(tenantId);
    when(chatConversationRepository.findByTenantAndId(tenantId, convId)).thenReturn(Optional.of(conversation));

    service.deleteConversation(convId.toString());

    verify(chatMessageRepository).deleteByTenantIdAndConversationId(tenantId, convId);
    verify(chatConversationRepository).delete(conversation);
  }

  @Test
  void listMessagesFalhaQuandoConversaNaoEncontrada() {
    UUID convId = UUID.randomUUID();
    when(chatConversationRepository.findByTenantAndId(tenantId, convId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listMessages(convId.toString(), 1, 20))
        .isInstanceOf(ApiClientErrorException.class);
  }

  // ---- corrida de dedup inbound (createClientFromInbound / createConversation) ----

  @Test
  void createClientFromInboundReaproveitaClienteQuandoOutraTransacaoVenceCorrida() throws Exception {
    String phoneDigits = "11999998888";
    Cliente winner = new Cliente();
    winner.setId(UUID.randomUUID());
    winner.setTenantId(tenantId);
    winner.setPhone(phoneDigits);

    when(clienteRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
        "duplicate key value violates unique constraint \"uq_clients_tenant_phone\""));
    when(clienteRepository.findByTenantAndPhoneDigits(tenantId, phoneDigits)).thenReturn(Optional.of(winner));

    Cliente result = invokeCreateClientFromInbound(tenantId, phoneDigits, "Fulano");

    assertThat(result.getId()).isEqualTo(winner.getId());
    verify(clienteRepository, times(1)).saveAndFlush(any());
  }

  @Test
  void createClientFromInboundPropagaErroQuandoConflitoNaoForRaceConhecida() {
    String phoneDigits = "11999998888";
    when(clienteRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("outra violacao qualquer"));
    when(clienteRepository.findByTenantAndPhoneDigits(tenantId, phoneDigits)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> invokeCreateClientFromInbound(tenantId, phoneDigits, "Fulano"))
        .isInstanceOf(InvocationTargetException.class)
        .satisfies(e -> assertThat(((InvocationTargetException) e).getCause())
            .isInstanceOf(DataIntegrityViolationException.class));
  }

  @Test
  void createConversationReaproveitaConversaQuandoOutraTransacaoVenceCorrida() throws Exception {
    UUID clientId = UUID.randomUUID();
    ChatConversationEntity winner = new ChatConversationEntity();
    winner.setId(UUID.randomUUID());
    winner.setTenantId(tenantId);
    winner.setClientId(clientId);
    winner.setChannel(ChatChannel.WHATSAPP);

    when(chatConversationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
        "duplicate key value violates unique constraint \"uq_chat_conversations_tenant_client_channel\""));
    when(chatConversationRepository.findByTenantClientAndChannel(tenantId, clientId, ChatChannel.WHATSAPP))
        .thenReturn(Optional.of(winner));

    ChatConversationEntity result = invokeCreateConversation(tenantId, clientId, "11999998888", ChatChannel.WHATSAPP);

    assertThat(result.getId()).isEqualTo(winner.getId());
    verify(chatConversationRepository, times(1)).saveAndFlush(any());
  }

  // ---- processInboundWhatsAppMessage ----

  @Test
  void processInboundWhatsAppMessageRetornaDuplicadoQuandoProviderMessageIdJaExiste() {
    UUID clientId = UUID.randomUUID();
    UUID convId = UUID.randomUUID();
    ChatMessageEntity existingMessage = new ChatMessageEntity();
    existingMessage.setId(UUID.randomUUID());
    existingMessage.setConversationId(convId);
    existingMessage.setClientId(clientId);
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid-1"))
        .thenReturn(Optional.of(existingMessage));

    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setId(convId);
    conversation.setTenantId(tenantId);
    conversation.setClientId(clientId);
    conversation.setChannel(ChatChannel.WHATSAPP);
    when(chatConversationRepository.findByTenantAndId(tenantId, convId)).thenReturn(Optional.of(conversation));

    Cliente client = new Cliente();
    client.setId(clientId);
    when(clienteRepository.findById(clientId)).thenReturn(Optional.of(client));

    ChatService.InboundProcessingResult result = service.processInboundWhatsAppMessage(
        tenantId, "5511999991111", "Ola", "Fulano", "wamid-1");

    assertThat(result.duplicateInboundMessage).isTrue();
    assertThat(result.conversation).isEqualTo(conversation);
    verify(clienteRepository, never()).saveAndFlush(any());
  }

  @Test
  void processInboundWhatsAppMessageCriaClienteEConversaNoPrimeiroContato() {
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(any(), any())).thenReturn(Optional.empty());
    when(clienteRepository.findByTenantAndPhoneDigits(eq(tenantId), any())).thenReturn(Optional.empty());

    when(clienteRepository.saveAndFlush(any())).thenAnswer(inv -> {
      Cliente c = inv.getArgument(0);
      c.setId(UUID.randomUUID());
      return c;
    });
    when(clienteRepository.findById(any())).thenAnswer(inv -> Optional.empty());

    when(chatConversationRepository.findByTenantClientAndChannel(eq(tenantId), any(), eq(ChatChannel.WHATSAPP)))
        .thenReturn(Optional.empty());
    when(chatConversationRepository.saveAndFlush(any())).thenAnswer(inv -> {
      ChatConversationEntity conv = inv.getArgument(0);
      conv.setId(UUID.randomUUID());
      return conv;
    });
    when(chatConversationRepository.findById(any())).thenAnswer(inv -> Optional.empty());

    ChatService.InboundProcessingResult result = service.processInboundWhatsAppMessage(
        tenantId, "+55 11 99999-1111", "Oi, quero agendar", "Maria", "wamid-novo");

    assertThat(result.duplicateInboundMessage).isFalse();
    assertThat(result.client).isNotNull();
    assertThat(result.conversation).isNotNull();
    verify(chatMessageRepository).saveAndFlush(any(ChatMessageEntity.class));
    verify(chatConversationRepository).incrementUnread(eq(tenantId), any());
    verify(whatsAppBookingReactivationService).markClientReplyIfNeeded(eq(tenantId), any(), eq("5511999991111"));
  }

  // ---- helpers de reflexao (metodos privados, mesma tecnica do original) ----

  private Cliente invokeCreateClientFromInbound(UUID tenantId, String phoneDigits, String contactName) throws Exception {
    Method method = ChatService.class.getDeclaredMethod(
        "createClientFromInbound", UUID.class, String.class, String.class);
    method.setAccessible(true);
    return (Cliente) method.invoke(service, tenantId, phoneDigits, contactName);
  }

  private ChatConversationEntity invokeCreateConversation(
      UUID tenantId, UUID clientId, String externalContactId, ChatChannel channel) throws Exception {
    Method method = ChatService.class.getDeclaredMethod(
        "createConversation", UUID.class, UUID.class, String.class, ChatChannel.class);
    method.setAccessible(true);
    return (ChatConversationEntity) method.invoke(service, tenantId, clientId, externalContactId, channel);
  }
}
