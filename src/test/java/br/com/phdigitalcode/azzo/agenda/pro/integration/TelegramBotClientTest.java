package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

class TelegramBotClientTest {

  private MockRestServiceServer server;
  private TelegramBotClient client;
  private EncryptionService encryptionService;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    encryptionService = mock(EncryptionService.class);
    client = new TelegramBotClient(builder.build(), new ObjectMapper(), encryptionService);
  }

  private TenantTelegramConfig configComToken() {
    when(encryptionService.decrypt("enc-bot-token")).thenReturn("123456:ABC-token");
    TenantTelegramConfig config = new TenantTelegramConfig();
    config.setTelegramBotTokenEnc("enc-bot-token");
    return config;
  }

  @Test
  void deveBuscarPerfilDoBot() {
    server.expect(requestTo("https://api.telegram.org/bot123456:ABC-token/getMe"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(
            "{\"ok\":true,\"result\":{\"id\":999,\"username\":\"meu_bot\",\"first_name\":\"Meu Bot\"}}",
            MediaType.APPLICATION_JSON));

    TelegramBotClient.BotProfile profile = client.getMe(configComToken());

    assertThat(profile.username).isEqualTo("meu_bot");
    assertThat(profile.displayName).isEqualTo("Meu Bot");
  }

  @Test
  void deveEnviarMensagemERetornarMessageId() {
    server.expect(requestTo("https://api.telegram.org/bot123456:ABC-token/sendMessage"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":555}}", MediaType.APPLICATION_JSON));

    String messageId = client.sendMessage(configComToken(), "42", "Ola!");

    assertThat(messageId).isEqualTo("555");
  }

  @Test
  void deveLancarIllegalArgumentQuandoChatIdVazio() {
    assertThatThrownBy(() -> client.sendMessage("123456:ABC-token", " ", "Ola"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Destino do Telegram invalido.");
  }

  @Test
  void deveLancarIllegalArgumentQuandoMensagemVazia() {
    assertThatThrownBy(() -> client.sendMessage("123456:ABC-token", "42", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Mensagem do Telegram vazia.");
  }

  @Test
  void deveLancarIllegalArgumentQuandoTokenAusente() {
    TenantTelegramConfig config = new TenantTelegramConfig();
    config.setTelegramBotTokenEnc("");

    assertThatThrownBy(() -> client.sendMessage(config, "42", "Ola"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Token do bot do Telegram nao configurado.");
  }

  @Test
  void deveConfigurarWebhookComSecretToken() {
    server.expect(requestTo("https://api.telegram.org/bot123456:ABC-token/setWebhook"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));

    client.setWebhook(configComToken(), "https://exemplo.com/webhook/telegram", "segredo");

    server.verify();
  }

  @Test
  void deveRemoverWebhook() {
    server.expect(requestTo("https://api.telegram.org/bot123456:ABC-token/deleteWebhook"))
        .andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));

    client.deleteWebhook(configComToken());

    server.verify();
  }

  @Test
  void deveConsultarInformacoesDoWebhook() {
    server.expect(requestTo("https://api.telegram.org/bot123456:ABC-token/getWebhookInfo"))
        .andRespond(withSuccess(
            "{\"ok\":true,\"result\":{\"url\":\"https://exemplo.com/webhook/telegram\","
                + "\"has_custom_certificate\":false,\"pending_update_count\":0}}",
            MediaType.APPLICATION_JSON));

    TelegramBotClient.WebhookInfo info = client.getWebhookInfo(configComToken());

    assertThat(info.configured).isTrue();
    assertThat(info.url).isEqualTo("https://exemplo.com/webhook/telegram");
  }

  @Test
  void deveLancarIllegalStateQuandoTelegramRespondeOkFalse() {
    server.expect(requestTo("https://api.telegram.org/bot123456:ABC-token/getMe"))
        .andRespond(withSuccess("{\"ok\":false,\"description\":\"Unauthorized\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.getMe(configComToken()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unauthorized");
  }

  @Test
  void deveLancarIllegalStateQuandoTelegramRespondeStatusDeErro() {
    server.expect(requestTo("https://api.telegram.org/bot123456:ABC-token/getMe"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND)
            .body("{\"ok\":false,\"description\":\"Not Found\"}")
            .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.getMe(configComToken()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Not Found");
  }
}
