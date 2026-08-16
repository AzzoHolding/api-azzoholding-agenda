package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ChatDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.ChatRealtimePublisher;
import br.com.phdigitalcode.azzo.agenda.pro.service.ChatService;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/chat/api/ChatResource.java} — mesmos paths, verbos, roles e permissoes.
 *
 * <p>{@code @GET /stream} (SSE, JAX-RS {@code SseEventSink}/{@code Sse}) vira {@link SseEmitter}
 * do Spring MVC, mesmo padrao ja adotado em {@link ChatRealtimePublisher}. Sem timeout explicito
 * no original — {@code SseEmitter} sem argumento usa o timeout default do container, que e o
 * comportamento equivalente ao JAX-RS (sem timeout configurado na Resource).
 */
@RestController
@RequestMapping("/api/v1/chat")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class ChatController {

  private final ChatService chatService;
  private final ContextoTenant contextoTenant;
  private final ChatRealtimePublisher chatRealtimePublisher;

  public ChatController(
      ChatService chatService,
      ContextoTenant contextoTenant,
      ChatRealtimePublisher chatRealtimePublisher) {
    this.chatService = chatService;
    this.contextoTenant = contextoTenant;
    this.chatRealtimePublisher = chatRealtimePublisher;
  }

  @GetMapping("/conversations")
  @RequiresPermission("appointment:read")
  public ChatDtos.ConversationListResponse listConversations(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize) {
    return chatService.listConversations(page, pageSize);
  }

  @GetMapping("/conversations/today")
  @RequiresPermission("appointment:read")
  public ChatDtos.ConversationListResponse listTodayConversations(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize) {
    return chatService.listTodayConversations(page, pageSize);
  }

  @GetMapping("/conversations/{conversationId}/messages")
  @RequiresPermission("appointment:read")
  public ChatDtos.MessageListResponse listMessages(
      @PathVariable("conversationId") String conversationId,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize,
      @RequestParam(name = "cursor", required = false) String cursor) {
    return chatService.listMessages(conversationId, page, pageSize, cursor);
  }

  @PostMapping("/messages")
  @RequiresPermission("appointment:write")
  public ResponseEntity<ChatDtos.SendMessageResponse> sendMessage(
      @Valid @RequestBody ChatDtos.SendMessageRequest request) {
    ChatDtos.SendMessageResponse response = chatService.sendMessage(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @DeleteMapping("/conversations/{conversationId}")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("appointment:write")
  public ResponseEntity<Void> deleteConversation(@PathVariable("conversationId") String conversationId) {
    chatService.deleteConversation(conversationId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/conversations/{conversationId}/read")
  @RequiresPermission("appointment:write")
  public ResponseEntity<Void> markRead(@PathVariable("conversationId") String conversationId) {
    chatService.markConversationRead(conversationId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/conversations/{conversationId}/appointment-marker")
  @RequiresPermission("appointment:write")
  public ChatDtos.UpdateMarkerResponse updateMarker(
      @PathVariable("conversationId") String conversationId,
      @Valid @RequestBody ChatDtos.UpdateMarkerRequest request) {
    return chatService.updateMarker(conversationId, request);
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RequiresPermission("appointment:read")
  public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter(0L);
    chatRealtimePublisher.subscribe(contextoTenant.obterTenantIdOuFalhar(), emitter);
    return emitter;
  }
}
