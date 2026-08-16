package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Cobre {@code MetaEmbeddedSignupClient}: espelha o Quarkus original em RestClient. */
class MetaEmbeddedSignupClientTest {

  private MockRestServiceServer server;
  private MetaEmbeddedSignupClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new MetaEmbeddedSignupClient(builder.build(), new ObjectMapper(), "app-id-123", "app-secret-456", "v18.0");
  }

  @Test
  void deveTrocarCodePorAccessToken() {
    server.expect(requestTo(
            "https://graph.facebook.com/v18.0/oauth/access_token"
                + "?client_id=app-id-123&client_secret=app-secret-456&code=abc123"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"access_token\":\"EAABsb...\"}", MediaType.APPLICATION_JSON));

    String accessToken = client.exchangeCodeForAccessToken("abc123");

    assertThat(accessToken).isEqualTo("EAABsb...");
    server.verify();
  }

  @Test
  void deveFalharQuandoMetaNaoRetornaAccessToken() {
    server.expect(requestTo(
            "https://graph.facebook.com/v18.0/oauth/access_token"
                + "?client_id=app-id-123&client_secret=app-secret-456&code=abc123"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.exchangeCodeForAccessToken("abc123"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nao retornou access token");
  }

  @Test
  void deveFalharComCredenciaisMetaNaoConfiguradas() {
    MetaEmbeddedSignupClient semCredenciais =
        new MetaEmbeddedSignupClient(RestClient.builder().build(), new ObjectMapper(), "__unset__", "__unset__", "v18.0");

    assertThatThrownBy(() -> semCredenciais.exchangeCodeForAccessToken("abc123"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("META_APP_ID/META_APP_SECRET");
  }

  @Test
  void devePropagarErroDaMetaAoTrocarCode() {
    server.expect(requestTo(
            "https://graph.facebook.com/v18.0/oauth/access_token"
                + "?client_id=app-id-123&client_secret=app-secret-456&code=expirado"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .body("{\"error\":{\"message\":\"Code invalido ou expirado\",\"code\":100}}")
            .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.exchangeCodeForAccessToken("expirado"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Code invalido ou expirado");
  }

  @Test
  void deveBuscarDetalhesDoPhoneNumber() {
    server.expect(requestTo(
            "https://graph.facebook.com/v18.0/1234567890?fields=id,display_phone_number,verified_name"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(
            "{\"id\":\"1234567890\",\"display_phone_number\":\"+55 11 99999-8888\",\"verified_name\":\"Meu Salao\"}",
            MediaType.APPLICATION_JSON));

    MetaEmbeddedSignupClient.PhoneNumberDetails details =
        client.fetchPhoneNumberDetails("token-abc", "1234567890");

    assertThat(details.id).isEqualTo("1234567890");
    assertThat(details.displayPhoneNumber).isEqualTo("+55 11 99999-8888");
    assertThat(details.verifiedName).isEqualTo("Meu Salao");
    server.verify();
  }

  @Test
  void deveFalharQuandoPhoneNumberIdAusente() {
    assertThatThrownBy(() -> client.fetchPhoneNumberDetails("token-abc", " "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Phone Number ID ausente");
  }

  @Test
  void deveFalharQuandoMetaNaoRetornaIdValido() {
    server.expect(requestTo(
            "https://graph.facebook.com/v18.0/1234567890?fields=id,display_phone_number,verified_name"))
        .andRespond(withSuccess("{\"display_phone_number\":\"+55 11 99999-8888\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetchPhoneNumberDetails("token-abc", "1234567890"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nao retornou id valido");
  }
}
