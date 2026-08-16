package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionStatusResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.MetaAcknowledgeResponse;
import br.com.phdigitalcode.azzo.agenda.pro.service.MetaPlatformService;

/** Cobre {@code modules/meta/api/publicapi/MetaPlatformPublicResource.java} (JAX-RS -> Spring MVC). */
class MetaPlatformPublicControllerTest {

  private MetaPlatformService metaPlatformService;
  private MetaPlatformPublicController controller;

  @BeforeEach
  void setUp() {
    metaPlatformService = mock(MetaPlatformService.class);
    controller = new MetaPlatformPublicController(metaPlatformService);
  }

  @Test
  void oauthCallbackSemErroRetornaHtmlDeSucesso() {
    String html = controller.oauthCallback("codigo123", "estado", null, null);

    assertThat(html).contains("Callback recebido com sucesso.");
    assertThat(html).contains("<code>codigo123</code>");
    assertThat(html).contains("<code>estado</code>");
    assertThat(html).doesNotContain("Erro:");
  }

  @Test
  void oauthCallbackComErroRetornaHtmlDeErroEEscapaConteudo() {
    String html = controller.oauthCallback(null, null, "<script>alert(1)</script>", "descricao & detalhe");

    assertThat(html).contains("Callback retornou erro da Meta.");
    assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    assertThat(html).contains("descricao &amp; detalhe");
  }

  @Test
  void deauthorizeDelegaAoService() {
    MetaAcknowledgeResponse esperado = new MetaAcknowledgeResponse();
    when(metaPlatformService.deauthorize("body")).thenReturn(esperado);

    assertThat(controller.deauthorize("body")).isSameAs(esperado);
  }

  @Test
  void dataDeletionDelegaAoServiceComUrlBaseadaNaRequisicao() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setScheme("https");
    request.setServerName("app.azzo.com.br");
    request.setServerPort(443);

    DataDeletionResponse esperado = new DataDeletionResponse();
    when(metaPlatformService.dataDeletion(anyString(), anyString())).thenReturn(esperado);

    assertThat(controller.dataDeletion("body", request)).isSameAs(esperado);
    verify(metaPlatformService).dataDeletion(
        "body", "https://app.azzo.com.br/api/v1/public/meta/data-deletion/status/");
  }

  @Test
  void dataDeletionUsaPortaExplicitaQuandoNaoPadrao() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(8080);

    DataDeletionResponse esperado = new DataDeletionResponse();
    when(metaPlatformService.dataDeletion(anyString(), anyString())).thenReturn(esperado);

    controller.dataDeletion("body", request);

    verify(metaPlatformService).dataDeletion(
        "body", "http://localhost:8080/api/v1/public/meta/data-deletion/status/");
  }

  @Test
  void dataDeletionStatusDelegaAoService() {
    DataDeletionStatusResponse esperado = new DataDeletionStatusResponse();
    when(metaPlatformService.dataDeletionStatus("codigo")).thenReturn(esperado);

    assertThat(controller.dataDeletionStatus("codigo")).isSameAs(esperado);
  }
}
