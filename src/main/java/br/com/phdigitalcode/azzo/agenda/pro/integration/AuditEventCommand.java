package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.util.UUID;

/**
 * PLACEHOLDER — espelha {@code modules/audit/application/AuditEventCommand.java}. O modulo
 * {@code audit} ainda nao foi migrado; esta classe existe apenas para que
 * {@code GlobalExceptionHandler} e {@code AuthService} possam registrar eventos de auditoria sem
 * bloquear a resposta (mesmo padrao "fire and forget" do original). Mover para
 * {@code modules/audit} quando esse dominio for portado.
 */
public class AuditEventCommand {
  public UUID tenantId;
  public UUID actorUserId;
  public String actorRole;
  public String module;
  public String action;
  public String entityType;
  public String entityId;
  public String errorCode;
  public String errorMessage;
  public String requestId;
  public String idempotencyKey;
  public String sourceChannel;
  public String ipAddress;
  public String userAgent;
  public Object before;
  public Object after;
  public Object metadata;
}
