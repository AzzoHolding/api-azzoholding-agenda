package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

class WhatsAppClientTest {

  private MockRestServiceServer server;
  private WhatsAppClient client;
  private EncryptionService encryptionService;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    encryptionService = mock(EncryptionService.class);
    client = new WhatsAppClient(builder.build(), new ObjectMapper(), encryptionService, "v18.0");
  }

  private TenantWhatsAppConfig configComToken() {
    when(encryptionService.decrypt("enc-token")).thenReturn("plain-token");
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(UUID.randomUUID());
    config.setWhatsappAccessTokenEnc("enc-token");
    config.setWhatsappPhoneNumberId("1234567890");
    return config;
  }

  @Test
  void deveEnviarMensagemERetornarProviderMessageId() {
    TenantWhatsAppConfig config = configComToken();

    server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer plain-token"))
        .andRespond(withSuccess(
            "{\"messages\":[{\"id\":\"wamid.HBg\"}]}",
            MediaType.APPLICATION_JSON));

    String providerMessageId = client.sendMessage(config, "+55 (11) 99999-8888", "Ola!");

    assertThat(providerMessageId).isEqualTo("wamid.HBg");
    server.verify();
  }

  @Test
  void deveNormalizarDestinoRemovendoCaracteresNaoNumericos() {
    TenantWhatsAppConfig config = configComToken();

    server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
        .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

    client.sendMessage(config, "+55 (11) 99999-8888", "Ola!");

    server.verify();
  }

  @Test
  void deveLancarIllegalArgumentQuandoDestinoVazio() {
    TenantWhatsAppConfig config = configComToken();

    assertThatThrownBy(() -> client.sendMessage(config, "", "Ola!"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Destino do WhatsApp invalido");
  }

  @Test
  void deveLancarIllegalArgumentQuandoMensagemVazia() {
    TenantWhatsAppConfig config = configComToken();

    assertThatThrownBy(() -> client.sendMessage(config, "5511999998888", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Mensagem vazia");
  }

  @Test
  void deveLancarIllegalArgumentQuandoConfigNula() {
    assertThatThrownBy(() -> client.sendMessage(null, "5511999998888", "Ola"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Configuracao de WhatsApp invalida");
  }

  @Test
  void deveLancarIllegalArgumentQuandoTokenNaoConfigurado() {
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(UUID.randomUUID());
    config.setWhatsappAccessTokenEnc("");
    config.setWhatsappPhoneNumberId("1234567890");

    assertThatThrownBy(() -> client.sendMessage(config, "5511999998888", "Ola"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Token do WhatsApp nao configurado para o tenant");
  }

  @Test
  void deveConverterRespostaComStatus401EmMensagemDeTokenNaoAutorizado() {
    TenantWhatsAppConfig config = configComToken();

    server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
            .body("{\"error\":{\"message\":\"Invalid OAuth access token\"}}")
            .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.sendMessage(config, "5511999998888", "Ola"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Token nao autorizado")
        .hasMessageContaining("Invalid OAuth access token");
  }

  @Test
  void deveConverterErroGenericoDaMetaNaMensagemDeErro() {
    TenantWhatsAppConfig config = configComToken();

    server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .body("{\"error\":{\"message\":\"Recipient phone number not in allowed list\"}}")
            .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.sendMessage(config, "5511999998888", "Ola"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Recipient phone number not in allowed list");
  }

  @Test
  void deveBuscarDetalhesDoNumeroDeTelefone() {
    server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer plain-token"))
        .andRespond(withSuccess(
            "{\"id\":\"1234567890\",\"display_phone_number\":\"+55 11 99999-8888\",\"verified_name\":\"Salao X\"}",
            MediaType.APPLICATION_JSON));

    WhatsAppClient.PhoneNumberDetails details = client.fetchPhoneNumberDetails("plain-token", "1234567890");

    assertThat(details.id).isEqualTo("1234567890");
    assertThat(details.displayPhoneNumber).isEqualTo("+55 11 99999-8888");
    assertThat(details.verifiedName).isEqualTo("Salao X");
  }

  @Test
  void testConnectionRetornaTrueQuandoRespostaValida() {
    TenantWhatsAppConfig config = configComToken();

    server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890"))
        .andRespond(withSuccess("{\"id\":\"1234567890\"}", MediaType.APPLICATION_JSON));

    assertThat(client.testConnection(config)).isTrue();
  }

  @Test
  void deveLancarIllegalArgumentQuandoAccessTokenAusenteNoFetch() {
    assertThatThrownBy(() -> client.fetchPhoneNumberDetails((String) null, "1234567890"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Token do WhatsApp nao configurado para o tenant");
  }

  @Test
  void deveLancarIllegalArgumentQuandoPhoneNumberIdAusenteNoFetch() {
    assertThatThrownBy(() -> client.fetchPhoneNumberDetails("plain-token", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("phoneNumberId do WhatsApp nao configurado para o tenant");
  }

  /**
   * Texto livre so e entregue dentro de 24h da ultima mensagem do CLIENTE; fora dessa janela a
   * Meta aceita, devolve o wamid e descarta. Confirmacao de cliente novo e sempre primeiro
   * contato, e so template chega.
   */
  @Test
  void deveEnviarTemplateComAsVariaveisNaOrdem() {
    TenantWhatsAppConfig config = configComToken();

    server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer plain-token"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"template\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("confirmacao_agendamento")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("pt_BR")))
        // A ordem e o que liga cada valor a {{1}}, {{2}}...: trocar duas posicoes manda o servico
        // no lugar do nome, e a Meta aceita sem reclamar.
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Marina")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Corte")))
        .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.TPL\"}]}", MediaType.APPLICATION_JSON));

    String providerMessageId =
        client.enviarTemplate(
            config, "5511999998888", "confirmacao_agendamento", "pt_BR",
            java.util.List.of("Marina", "Corte", "23/09", "14:00"));

    assertThat(providerMessageId).isEqualTo("wamid.TPL");
    server.verify();
  }

  /** Template sem variaveis nao pode mandar um `components` vazio: a Meta recusa. */
  @Test
  void templateSemVariaveisNaoMandaComponents() {
    TenantWhatsAppConfig config = configComToken();

    server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("components"))))
        .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.HW\"}]}", MediaType.APPLICATION_JSON));

    client.enviarTemplate(config, "5511999998888", "hello_world", "en_US");

    server.verify();
  }

  /** Idioma vazio cai no padrao, e nao monta um template sem idioma que a Meta recusaria. */
  @Test
  void templateSemIdiomaUsaOPadrao() {
    TenantWhatsAppConfig config = configComToken();

    server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("en_US")))
        .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.HW\"}]}", MediaType.APPLICATION_JSON));

    client.enviarTemplate(config, "5511999998888", "hello_world", "  ");

    server.verify();
  }

  @Test
  void templateSemNomeNaoVaiParaAMeta() {
    TenantWhatsAppConfig config = configComToken();

    assertThatThrownBy(() -> client.enviarTemplate(config, "5511999998888", "  ", "pt_BR"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("template");
  }
}
