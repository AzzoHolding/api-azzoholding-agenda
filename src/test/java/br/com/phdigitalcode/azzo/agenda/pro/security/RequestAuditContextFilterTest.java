package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

/** Espelha {@code modules/audit/infrastructure/RequestAuditContextFilter.java}. */
class RequestAuditContextFilterTest {

  private RequestAuditContext requestAuditContext;
  private RequestAuditContextFilter filter;
  private FilterChain filterChain;

  @BeforeEach
  void setUp() {
    requestAuditContext = new RequestAuditContext();
    filter = new RequestAuditContextFilter(requestAuditContext);
    filterChain = mock(FilterChain.class);
  }

  @Test
  void preencheRequestIdIpEUserAgentDosHeaders() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "req-123");
    request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
    request.addHeader("User-Agent", "meu-agente");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(requestAuditContext.getRequestId()).isEqualTo("req-123");
    assertThat(requestAuditContext.getIpAddress()).isEqualTo("203.0.113.5");
    assertThat(requestAuditContext.getUserAgent()).isEqualTo("meu-agente");
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void usaXRealIpQuandoNaoHaForwardedFor() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Real-IP", "198.51.100.7");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(requestAuditContext.getIpAddress()).isEqualTo("198.51.100.7");
  }

  @Test
  void usaHeaderForwardedQuandoNaoHaXForwardedForNemXRealIp() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Forwarded", "for=192.0.2.60;proto=https");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(requestAuditContext.getIpAddress()).isEqualTo("192.0.2.60");
  }

  @Test
  void parseiaForwardedComIpv6EColchetes() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Forwarded", "for=\"[2001:db8:cafe::17]:4711\"");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(requestAuditContext.getIpAddress()).isEqualTo("2001:db8:cafe::17");
  }

  @Test
  void caiParaRemoteAddrQuandoNenhumHeaderPresente() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(requestAuditContext.getIpAddress()).isEqualTo("127.0.0.1");
    assertThat(requestAuditContext.getRequestId()).isNull();
  }
}
