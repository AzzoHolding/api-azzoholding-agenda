package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Espelha {@code modules/audit/infrastructure/RequestAuditContext.java}: bean de dados simples. */
class RequestAuditContextTest {

  @Test
  void armazenaEDevolveOsCamposDefinidos() {
    RequestAuditContext context = new RequestAuditContext();

    context.setRequestId("req-1");
    context.setIpAddress("10.0.0.1");
    context.setUserAgent("agente-teste");

    assertThat(context.getRequestId()).isEqualTo("req-1");
    assertThat(context.getIpAddress()).isEqualTo("10.0.0.1");
    assertThat(context.getUserAgent()).isEqualTo("agente-teste");
  }

  @Test
  void camposComecamNulos() {
    RequestAuditContext context = new RequestAuditContext();

    assertThat(context.getRequestId()).isNull();
    assertThat(context.getIpAddress()).isNull();
    assertThat(context.getUserAgent()).isNull();
  }
}
