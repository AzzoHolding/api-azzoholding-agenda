package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.LoginRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.AuthResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthCookieService;
import br.com.phdigitalcode.azzo.agenda.pro.security.RefreshTokenService;
import br.com.phdigitalcode.azzo.agenda.pro.service.AuthService;

/**
 * Cookie-sinal de sessao ({@code AZZO_SESSAO}): o servidor das paginas so consegue mandar direto
 * ao login quem nunca entrou se souber que NAO ha sessao renovavel. O cookie de acesso vive 15 min
 * e o de refresh so viaja em /api/v1/auth, entao um sinal proprio, sem dado sensivel, resolve.
 */
class AuthControllerCookieDeSessaoTest {

  private AuthService authService;
  private RefreshTokenService refreshTokenService;
  private AuthController controller;

  @BeforeEach
  void preparar() {
    authService = mock(AuthService.class);
    refreshTokenService = mock(RefreshTokenService.class);
    when(refreshTokenService.refreshTokenExpiresInSeconds()).thenReturn(2_592_000L);

    AuthCookieService cookies = new AuthCookieService();
    ReflectionTestUtils.setField(cookies, "secureCookie", true);
    ReflectionTestUtils.setField(cookies, "sameSitePolicy", "STRICT");
    ReflectionTestUtils.setField(cookies, "cookieDomain", "__unset__");

    controller = new AuthController(authService, cookies, refreshTokenService, null, null, null, null);
  }

  private static AuthResponse respostaDeAutenticacao() {
    AuthResponse r = new AuthResponse();
    r.access_token = "acesso";
    r.refresh_token = "refresh";
    r.expires_in = 900L;
    r.token_type = "Bearer";
    return r;
  }

  private static String cookie(ResponseEntity<?> resposta, String nome) {
    List<String> todos = resposta.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(todos).isNotNull();
    return todos.stream().filter(c -> c.startsWith(nome + "=")).findFirst().orElse(null);
  }

  @Test
  void loginGravaOSinalDeSessaoComAValidadeDoRefresh() {
    when(authService.login(any(LoginRequest.class))).thenReturn(respostaDeAutenticacao());

    ResponseEntity<?> resposta = controller.login(new LoginRequest());

    String sinal = cookie(resposta, "AZZO_SESSAO");
    assertThat(sinal).isNotNull();
    assertThat(sinal).contains("AZZO_SESSAO=1").contains("Max-Age=2592000").contains("Path=/;");
    // Nao e credencial: precisa ser legivel pelo servidor da pagina, ao contrario dos tokens.
    assertThat(sinal).doesNotContain("HttpOnly");
    assertThat(cookie(resposta, "AZZO_ACCESS_TOKEN")).contains("HttpOnly");
    assertThat(cookie(resposta, "AZZO_REFRESH_TOKEN")).contains("HttpOnly");
  }

  @Test
  void refreshBemSucedidoRenovaOSinal() {
    when(authService.refresh(anyString())).thenReturn(respostaDeAutenticacao());

    ResponseEntity<?> resposta = controller.refresh("refresh-atual", null);

    assertThat(cookie(resposta, "AZZO_SESSAO")).contains("AZZO_SESSAO=1").contains("Max-Age=2592000");
  }

  @Test
  void refreshRecusadoLimpaOSinal() {
    when(authService.refresh(anyString())).thenThrow(new ApiClientErrorException("expirada", 401));

    ResponseEntity<?> resposta = controller.refresh("refresh-velho", null);

    assertThat(resposta.getStatusCode().value()).isEqualTo(401);
    assertThat(cookie(resposta, "AZZO_SESSAO")).contains("Max-Age=0");
  }

  @Test
  void logoutLimpaOSinal() {
    ResponseEntity<Void> resposta = controller.logout("refresh-atual");

    assertThat(cookie(resposta, "AZZO_SESSAO")).contains("Max-Age=0");
  }
}
