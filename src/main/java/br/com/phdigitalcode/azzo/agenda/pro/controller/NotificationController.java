package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.notification.NotificationDtos.NotificationListResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.NotificationService;

/** Espelha {@code modules/notifications/api/NotificationsResource.java} — mesmos paths, verbos, roles e permissoes. */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  @RequiresPermission("notification:read")
  public NotificationListResponse listar(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "channel", required = false) String channel,
      @RequestParam(name = "failedOnly", required = false, defaultValue = "false") boolean failedOnly,
      @RequestParam(name = "unreadOnly", required = false, defaultValue = "false") boolean unreadOnly,
      @RequestParam(name = "limit", required = false, defaultValue = "0") int limit,
      @RequestParam(name = "cursorCreatedAt", required = false) String cursorCreatedAt,
      @RequestParam(name = "cursorId", required = false) String cursorId) {
    Cursor cursor = parseCursor(cursorCreatedAt, cursorId);
    return notificationService.listar(
        status, channel, failedOnly, unreadOnly, limit, cursor.createdAt(), cursor.id());
  }

  @GetMapping("/my-appointments")
  @PreAuthorize("hasRole('PROFESSIONAL')")
  @RequiresPermission("notification:read")
  public NotificationListResponse listarMeusAgendamentos(
      @RequestParam(name = "unreadOnly", required = false, defaultValue = "false") boolean unreadOnly,
      @RequestParam(name = "limit", required = false, defaultValue = "0") int limit,
      @RequestParam(name = "cursorCreatedAt", required = false) String cursorCreatedAt,
      @RequestParam(name = "cursorId", required = false) String cursorId) {
    Cursor cursor = parseCursor(cursorCreatedAt, cursorId);
    return notificationService.listarMeusAgendamentos(unreadOnly, limit, cursor.createdAt(), cursor.id());
  }

  @PatchMapping("/{id}/viewed")
  @RequiresPermission("notification:read")
  public ResponseEntity<Map<String, Object>> marcarVisualizada(@PathVariable("id") UUID id) {
    boolean updated = notificationService.marcarComoVisualizada(id);
    if (!updated) {
      throw new ApiClientErrorException("Notificacao nao encontrada", HttpStatus.NOT_FOUND.value());
    }
    return ResponseEntity.ok(Map.of("updated", true));
  }

  @PatchMapping("/viewed/all")
  @RequiresPermission("notification:read")
  public ResponseEntity<Map<String, Object>> marcarTodasVisualizadas() {
    long updated = notificationService.marcarTodasComoVisualizadas();
    return ResponseEntity.ok(Map.of("updated", updated));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("notification:writer")
  public ResponseEntity<Void> remover(@PathVariable("id") UUID id) {
    boolean removed = notificationService.remover(id);
    if (!removed) {
      throw new ApiClientErrorException("Notificacao nao encontrada", HttpStatus.NOT_FOUND.value());
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/all")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("notification:writer")
  public ResponseEntity<Map<String, Object>> removerTodas() {
    long removed = notificationService.removerTodasDoTenant();
    return ResponseEntity.ok(Map.of("deleted", removed));
  }

  private Cursor parseCursor(String cursorCreatedAt, String cursorId) {
    Instant parsedCursorCreatedAt = null;
    UUID parsedCursorId = null;
    try {
      if (cursorCreatedAt != null && !cursorCreatedAt.isBlank()) {
        parsedCursorCreatedAt = Instant.parse(cursorCreatedAt.trim());
      }
      if (cursorId != null && !cursorId.isBlank()) {
        parsedCursorId = UUID.fromString(cursorId.trim());
      }
    } catch (Exception e) {
      throw new ApiClientErrorException(
          "cursorCreatedAt ou cursorId invalido", HttpStatus.BAD_REQUEST.value());
    }

    if ((parsedCursorCreatedAt == null) != (parsedCursorId == null)) {
      throw new ApiClientErrorException(
          "Para paginar, envie cursorCreatedAt e cursorId juntos", HttpStatus.BAD_REQUEST.value());
    }
    return new Cursor(parsedCursorCreatedAt, parsedCursorId);
  }

  private record Cursor(Instant createdAt, UUID id) {}
}
