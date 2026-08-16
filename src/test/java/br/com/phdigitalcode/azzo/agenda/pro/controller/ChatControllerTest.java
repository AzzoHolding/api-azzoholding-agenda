package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ChatDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.ChatRealtimePublisher;
import br.com.phdigitalcode.azzo.agenda.pro.service.ChatService;

/**
 * Espelha {@code modules/chat/api/ChatResource.java}: contrato de rota/verbo/permissao e
 * delegacao pura para {@link ChatService}/{@link ChatRealtimePublisher}.
 */
class ChatControllerTest {

  private final UUID tenantId = UUID.randomUUID();

  private ChatService chatService;
  private ContextoTenant contextoTenant;
  private ChatRealtimePublisher chatRealtimePublisher;
  private ChatController controller;

  @BeforeEach
  void setUp() {
    chatService = mock(ChatService.class);
    contextoTenant = mock(ContextoTenant.class);
    chatRealtimePublisher = mock(ChatRealtimePublisher.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    controller = new ChatController(chatService, contextoTenant, chatRealtimePublisher);
  }

  // ─── contrato de classe ─────────────────────────────────────────────────

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(ChatController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/chat");
  }

  @Test
  void classeMantemRolesPermitidasDoOriginal() {
    PreAuthorize preAuthorize = ChatController.class.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('OWNER', 'PROFESSIONAL')");
  }

  @Test
  void cadaRotaMantemVerboECaminhoDoOriginal() throws NoSuchMethodException {
    assertThat(metodo("listConversations", Integer.class, Integer.class)
            .getAnnotation(GetMapping.class).value())
        .containsExactly("/conversations");
    assertThat(metodo("listTodayConversations", Integer.class, Integer.class)
            .getAnnotation(GetMapping.class).value())
        .containsExactly("/conversations/today");
    assertThat(metodo("listMessages", String.class, Integer.class, Integer.class, String.class)
            .getAnnotation(GetMapping.class).value())
        .containsExactly("/conversations/{conversationId}/messages");
    assertThat(metodo("sendMessage", ChatDtos.SendMessageRequest.class)
            .getAnnotation(PostMapping.class).value())
        .containsExactly("/messages");
    assertThat(metodo("deleteConversation", String.class)
            .getAnnotation(DeleteMapping.class).value())
        .containsExactly("/conversations/{conversationId}");
    assertThat(metodo("markRead", String.class).getAnnotation(PatchMapping.class).value())
        .containsExactly("/conversations/{conversationId}/read");
    assertThat(metodo("updateMarker", String.class, ChatDtos.UpdateMarkerRequest.class)
            .getAnnotation(PatchMapping.class).value())
        .containsExactly("/conversations/{conversationId}/appointment-marker");
    assertThat(metodo("stream").getAnnotation(GetMapping.class).value()).containsExactly("/stream");
  }

  @Test
  void rotaDeExclusaoExigeRoleOwnerAlemDaClasse() throws NoSuchMethodException {
    PreAuthorize preAuthorize = metodo("deleteConversation", String.class).getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo("hasRole('OWNER')");
  }

  @Test
  void rotaDeStreamProduzServerSentEvents() throws NoSuchMethodException {
    assertThat(metodo("stream").getAnnotation(GetMapping.class).produces())
        .containsExactly(MediaType.TEXT_EVENT_STREAM_VALUE);
  }

  private Method metodo(String nome, Class<?>... parametros) throws NoSuchMethodException {
    return ChatController.class.getDeclaredMethod(nome, parametros);
  }

  // ─── delegacao ────────────────────────────────────────────────────────────

  @Test
  void listConversationsDelegaParaChatService() {
    ChatDtos.ConversationListResponse response = new ChatDtos.ConversationListResponse();
    when(chatService.listConversations(1, 20)).thenReturn(response);

    assertThat(controller.listConversations(1, 20)).isSameAs(response);
  }

  @Test
  void listTodayConversationsDelegaParaChatService() {
    ChatDtos.ConversationListResponse response = new ChatDtos.ConversationListResponse();
    when(chatService.listTodayConversations(2, 10)).thenReturn(response);

    assertThat(controller.listTodayConversations(2, 10)).isSameAs(response);
  }

  @Test
  void listMessagesDelegaParaChatServiceComCursor() {
    ChatDtos.MessageListResponse response = new ChatDtos.MessageListResponse();
    when(chatService.listMessages("conv-1", 1, 20, "cursor-1")).thenReturn(response);

    assertThat(controller.listMessages("conv-1", 1, 20, "cursor-1")).isSameAs(response);
  }

  @Test
  void sendMessageDelegaERetorna202Accepted() {
    ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
    ChatDtos.SendMessageResponse response = new ChatDtos.SendMessageResponse();
    when(chatService.sendMessage(request)).thenReturn(response);

    ResponseEntity<ChatDtos.SendMessageResponse> result = controller.sendMessage(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(result.getBody()).isSameAs(response);
  }

  @Test
  void deleteConversationDelegaERetorna204() {
    ResponseEntity<Void> result = controller.deleteConversation("conv-1");

    verify(chatService).deleteConversation("conv-1");
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void markReadDelegaERetorna204() {
    ResponseEntity<Void> result = controller.markRead("conv-1");

    verify(chatService).markConversationRead("conv-1");
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void updateMarkerDelegaParaChatService() {
    ChatDtos.UpdateMarkerRequest request = new ChatDtos.UpdateMarkerRequest();
    ChatDtos.UpdateMarkerResponse response = new ChatDtos.UpdateMarkerResponse();
    when(chatService.updateMarker("conv-1", request)).thenReturn(response);

    assertThat(controller.updateMarker("conv-1", request)).isSameAs(response);
  }

  @Test
  void streamAssinaAPublisherComOTenantDoContexto() {
    SseEmitter emitter = controller.stream();

    assertThat(emitter).isNotNull();
    verify(chatRealtimePublisher, times(1)).subscribe(eq(tenantId), any(SseEmitter.class));
  }
}
