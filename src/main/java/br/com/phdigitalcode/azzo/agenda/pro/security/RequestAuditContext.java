package br.com.phdigitalcode.azzo.agenda.pro.security;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Equivalente Spring de {@code modules/audit/infrastructure/RequestAuditContext.java}
 * ({@code @RequestScoped} no Quarkus). Preenchido por {@link RequestAuditContextFilter} antes da
 * autenticacao e lido por {@code AuditService} para completar {@code requestId}/{@code ipAddress}/
 * {@code userAgent} quando o chamador nao os informa explicitamente.
 *
 * <p>{@code proxyMode = TARGET_CLASS} pelo mesmo motivo de {@link ContextoTenant}: este bean de
 * escopo de requisicao e injetado em beans singleton (o {@code AuditService}).
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestAuditContext {

  private String requestId;
  private String ipAddress;
  private String userAgent;

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }
}
