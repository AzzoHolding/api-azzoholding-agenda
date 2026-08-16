package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Espelha {@code modules/chat/application/ChatRealtimePublisher.java}. O par
 * {@code SseEventSink}/{@code Sse} do JAX-RS vira {@link SseEmitter} do Spring MVC — mesma
 * semantica de "lista de assinantes por tenant, remove ao falhar o envio".
 */
@Service
public class ChatRealtimePublisher {

  private static final Logger LOG = LoggerFactory.getLogger(ChatRealtimePublisher.class);
  private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

  public void subscribe(UUID tenantId, SseEmitter emitter) {
    if (tenantId == null || emitter == null) {
      if (emitter != null) emitter.complete();
      return;
    }

    Subscriber subscriber = new Subscriber(tenantId, emitter);
    subscribers.add(subscriber);
    emitter.onCompletion(() -> subscribers.remove(subscriber));
    emitter.onTimeout(() -> subscribers.remove(subscriber));
    emitter.onError(throwable -> subscribers.remove(subscriber));
    emit(subscriber, "connected", Map.of("status", "ok", "timestamp", Instant.now().toString()));
  }

  public void publishChatUpdate(UUID tenantId, UUID conversationId, UUID clientId, String type) {
    if (tenantId == null) return;

    Map<String, Object> payload = new HashMap<>();
    payload.put("type", type == null || type.isBlank() ? "CHAT_UPDATED" : type);
    payload.put("tenantId", tenantId.toString());
    payload.put("conversationId", conversationId != null ? conversationId.toString() : null);
    payload.put("clientId", clientId != null ? clientId.toString() : null);
    payload.put("timestamp", Instant.now().toString());

    for (Subscriber subscriber : subscribers) {
      if (!tenantId.equals(subscriber.tenantId)) continue;
      emit(subscriber, "chat-update", payload);
    }
  }

  @Scheduled(fixedRateString = "PT25S")
  void heartbeat() {
    if (subscribers.isEmpty()) return;
    Map<String, Object> payload = Map.of("timestamp", Instant.now().toString());
    for (Subscriber subscriber : subscribers) {
      emit(subscriber, "ping", payload);
    }
  }

  private void emit(Subscriber subscriber, String name, Object payload) {
    if (subscriber == null) return;
    try {
      subscriber.emitter.send(SseEmitter.event().name(name).data(payload));
    } catch (Exception ex) {
      LOG.debug("removendo subscriber SSE devido falha: {}", ex.getMessage());
      subscribers.remove(subscriber);
      subscriber.emitter.completeWithError(ex);
    }
  }

  private static final class Subscriber {
    private final UUID tenantId;
    private final SseEmitter emitter;

    private Subscriber(UUID tenantId, SseEmitter emitter) {
      this.tenantId = tenantId;
      this.emitter = emitter;
    }
  }
}
