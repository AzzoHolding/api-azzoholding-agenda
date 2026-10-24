package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.LoginRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RegisterRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.AuthResponse;
import jakarta.servlet.http.Cookie;

/**
 * Teste de integracao ponta-a-ponta do dominio {@code auth} contra um PostgreSQL real
 * (Testcontainers), rodando as 124 migrations Flyway copiadas do Quarkus original. Cobre o fluxo
 * completo register -> login -> /me -> refresh -> logout, validando o comportamento real (nao
 * apenas se mocks foram chamados).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthControllerIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private br.com.phdigitalcode.azzo.agenda.pro.repository.EmailJobRepository emailJobRepository;

  private RegisterRequest buildRegisterRequest(String email) {
    RegisterRequest request = new RegisterRequest();
    request.name = "Maria Dona";
    request.email = email;
    request.password = "SenhaForte@123";
    request.salonName = "Salao da Maria";
    request.phone = "11999998888";
    request.cpfCnpj = "52998224725"; // CPF valido em formato (11 digitos)
    request.acceptedTermsOfUse = true;
    request.acceptedPrivacyPolicy = true;
    request.termsOfUseVersion = "v1";
    request.privacyPolicyVersion = "v1";
    return request;
  }

  @Test
  void fluxoCompletoRegistroLoginMeRefreshLogout() throws Exception {
    String email = "maria." + System.nanoTime() + "@example.com";
    RegisterRequest registerRequest = buildRegisterRequest(email);

    // O cadastro NAO devolve sessao: a conta so entra depois de confirmar o e-mail.
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists())
        .andExpect(cookie().doesNotExist("AZZO_ACCESS_TOKEN"))
        .andExpect(cookie().doesNotExist("AZZO_REFRESH_TOKEN"));

    LoginRequest antesDeConfirmar = new LoginRequest();
    antesDeConfirmar.email = email;
    antesDeConfirmar.password = "SenhaForte@123";
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(antesDeConfirmar)))
        .andExpect(status().isForbidden());

    // O link da fila de e-mail traz o token puro.
    String payload = emailJobRepository.findAll().stream()
        .filter(j -> email.equals(j.getRecipientEmail()))
        .map(j -> j.getPayloadJson())
        .findFirst().orElseThrow();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(payload);
    org.junit.jupiter.api.Assertions.assertTrue(m.find());
    mockMvc.perform(post("/api/v1/auth/confirm-email")
            .contentType("application/json")
            .content("{\"token\":\"" + m.group(1) + "\"}"))
        .andExpect(status().isOk());

    // login com a senha recem-cadastrada
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.email = email;
    loginRequest.password = "SenhaForte@123";

    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value(email))
        .andReturn();

    AuthResponse loginResponse =
        objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
    Cookie loginAccessCookie = loginResult.getResponse().getCookie("AZZO_ACCESS_TOKEN");
    Cookie refreshCookie = loginResult.getResponse().getCookie("AZZO_REFRESH_TOKEN");

    // /me autenticado via cookie
    mockMvc.perform(get("/api/v1/auth/me").cookie(loginAccessCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.id", notNullValue()));

    // /me sem cookie -> nao autenticado
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

    // refresh usando o cookie de refresh emitido no login
    mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("AZZO_ACCESS_TOKEN"));

    // logout limpa os cookies
    mockMvc.perform(delete("/api/v1/auth/logout").cookie(refreshCookie))
        .andExpect(status().isNoContent());

    // refresh com o token ja rotacionado (revogado) na primeira chamada de refresh acima deve falhar
    mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginComSenhaErradaRetorna400ComMensagemGenerica() throws Exception {
    String email = "login.errado." + System.nanoTime() + "@example.com";
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(buildRegisterRequest(email))))
        .andExpect(status().isOk());

    LoginRequest loginRequest = new LoginRequest();
    loginRequest.email = email;
    loginRequest.password = "SenhaErrada@999";

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Credenciais invalidas Revise os dados informados e tente novamente."));
  }

  @Test
  void registroComSenhaFracaRetornaErroDeValidacao() throws Exception {
    RegisterRequest request = buildRegisterRequest("fraca." + System.nanoTime() + "@example.com");
    request.password = "123";

    mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rotaProtegidaSemAutenticacaoRetorna401() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  /**
   * Fecha o gap documentado em {@code AuthController}: {@code GET /api/v1/config/menus/current}
   * (via {@code MenuConfigController}, dominio {@code auth}) nunca havia sido portado e o
   * frontend real recebia 404. Registra um OWNER de verdade (mesmo fluxo do teste acima), chama o
   * endpoint com o cookie de acesso emitido no registro e confirma que ele responde 200 com o
   * papel do usuario e as rotas liberadas — nao mais 404.
   */
  @Test
  void menuAtualRespondeComRoleERotasParaUsuarioAutenticado() throws Exception {
    String email = "menu." + System.nanoTime() + "@example.com";
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(buildRegisterRequest(email))))
        .andExpect(status().isOk());
    confirmarEmail(email);
    LoginRequest login = new LoginRequest();
    login.email = email;
    login.password = "SenhaForte@123";
    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(login)))
        .andExpect(status().isOk())
        .andReturn();
    Cookie accessCookie = loginResult.getResponse().getCookie("AZZO_ACCESS_TOKEN");

    mockMvc.perform(get("/api/v1/config/menus/current").cookie(accessCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("OWNER"))
        .andExpect(jsonPath("$.allowedRoutes", notNullValue()))
        .andExpect(jsonPath("$.items", notNullValue()));

    mockMvc.perform(get("/api/v1/config/menus/current")).andExpect(status().isUnauthorized());
  }

  /** Abre o link de confirmacao que o cadastro enfileirou para o e-mail. */
  private void confirmarEmail(String email) throws Exception {
    String payload = emailJobRepository.findAll().stream()
        .filter(j -> email.equals(j.getRecipientEmail()))
        .map(j -> j.getPayloadJson())
        .findFirst().orElseThrow();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(payload);
    org.junit.jupiter.api.Assertions.assertTrue(m.find());
    mockMvc.perform(post("/api/v1/auth/confirm-email")
            .contentType("application/json")
            .content("{\"token\":\"" + m.group(1) + "\"}"))
        .andExpect(status().isOk());
  }
}
