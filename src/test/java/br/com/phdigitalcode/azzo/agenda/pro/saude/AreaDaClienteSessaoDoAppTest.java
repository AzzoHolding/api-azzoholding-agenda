package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/** A sessao vai no corpo so para o app (cabecalho X-Cliente-App: 1); a web fica so com o cookie httpOnly. */
class AreaDaClienteSessaoDoAppTest {

  private final AreaDaClienteController controller = new AreaDaClienteController(null, null, null, null, null, true, "STRICT");

  @Test
  void appRecebeASessaoNoCorpoENoCookie() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-Cliente-App", "1");
    ResponseEntity<Map<String, Object>> r = controller.comSessao("token-de-teste", req);
    assertThat(r.getBody()).containsEntry("sessao", "token-de-teste").containsEntry("ok", true);
    assertThat(r.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("AZZO_CLIENTE_SESSAO=token-de-teste").contains("HttpOnly");
  }

  @Test
  void webNaoRecebeASessaoNoCorpo() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    assertThat(controller.comSessao("token-de-teste", req).getBody()).doesNotContainKey("sessao");
    req.addHeader("X-Cliente-App", "sim");
    assertThat(controller.comSessao("token-de-teste", req).getBody()).doesNotContainKey("sessao");
  }
}
