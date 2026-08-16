package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantReactivationSeedRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantStage;

class AssistantApiClientTest {

  private MockRestServiceServer server;
  private AssistantApiClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://assistant-api.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new AssistantApiClient(builder.defaultHeader("X-Internal-Api-Key", "chave-interna").build());
  }

  @Test
  void deveEnviarMensagemComHeadersDeTenantEUsuarioERetornarResposta() {
    server.expect(requestTo("http://assistant-api.test/api/v1/assistant/message"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Internal-Api-Key", "chave-interna"))
        .andExpect(header("X-Tenant-Id", "tenant-1"))
        .andExpect(header("X-User-Identifier", "5511999998888"))
        .andExpect(header("X-User-Name", "Maria"))
        .andRespond(withSuccess(
            "{\"reply\":\"Ola Maria!\",\"stage\":\"ASK_SERVICE\",\"slots\":{\"serviceId\":\"abc\"}}",
            MediaType.APPLICATION_JSON));

    AssistantMessageResponse response = client.processarMensagem(
        "tenant-1", "5511999998888", "Maria", new AssistantMessageRequest("Quero agendar"));

    assertThat(response.reply).isEqualTo("Ola Maria!");
    assertThat(response.stage).isEqualTo(AssistantStage.ASK_SERVICE);
    assertThat(response.slots).containsEntry("serviceId", "abc");
    server.verify();
  }

  @Test
  void deveInvalidarCacheDoPromptViaDelete() {
    server.expect(requestToUriTemplate("http://assistant-api.test/api/v1/assistant/admin/cache?tenantId={tenantId}", "tenant-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(header("X-Internal-Api-Key", "chave-interna"))
        .andRespond(withSuccess());

    client.invalidarCachePrompt("tenant-1");

    server.verify();
  }

  @Test
  void deveSemearContextoDeLembrete() {
    server.expect(requestToUriTemplate(
            "http://assistant-api.test/api/v1/assistant/admin/seed-reminder"
                + "?tenantId={tenantId}&userIdentifier={userIdentifier}&appointmentId={appointmentId}&customerName={customerName}",
            "tenant-1", "5511999998888", "appt-1", "Maria"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());

    client.seedReminderContext("tenant-1", "5511999998888", "appt-1", "Maria");

    server.verify();
  }

  @Test
  void deveSemearContextoDeReativacaoComCorpoDaRequisicao() {
    server.expect(requestToUriTemplate(
            "http://assistant-api.test/api/v1/assistant/admin/seed-reactivation?tenantId={tenantId}&userIdentifier={userIdentifier}",
            "tenant-1", "5511999998888"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());

    AssistantReactivationSeedRequest request = new AssistantReactivationSeedRequest();
    request.cycleId = "cycle-1";
    request.customerName = "Maria";

    client.seedReactivationContext("tenant-1", "5511999998888", request);

    server.verify();
  }
}
