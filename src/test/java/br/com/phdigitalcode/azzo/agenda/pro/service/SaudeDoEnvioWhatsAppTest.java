package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;

/** 17/10/2026: a IA respondia (gastando tokens) mesmo com o WhatsApp do salao sem conseguir enviar. */
class SaudeDoEnvioWhatsAppTest {

  private WhatsAppClient whatsApp;
  private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-10-17T12:00:00Z"));
  private SaudeDoEnvioWhatsApp saude;
  private TenantWhatsAppConfig config;

  @BeforeEach
  void preparar() {
    whatsApp = mock(WhatsAppClient.class);
    Clock relogio = new Clock() {
      @Override public ZoneId getZone() { return ZoneOffset.UTC; }
      @Override public Clock withZone(ZoneId z) { return this; }
      @Override public Instant instant() { return agora.get(); }
    };
    saude = new SaudeDoEnvioWhatsApp(whatsApp, new ObjectMapper(), relogio);
    config = new TenantWhatsAppConfig();
    config.setTenantId(UUID.randomUUID());
    config.setWhatsappAccessTokenEnc("token-cifrado");
    config.setWhatsappPhoneNumberId("123456");
  }

  private static HttpClientErrorException erroDaMeta(HttpStatus status, int codigo, String mensagem) {
    String corpo = "{\"error\":{\"message\":\"" + mensagem + "\",\"code\":" + codigo + "}}";
    return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY,
        corpo.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
  }

  private static IllegalStateException comoOClienteLanca(HttpClientErrorException e) {
    return new IllegalStateException("Falha ao enviar mensagem no WhatsApp", e);
  }

  @Test
  void semTokenNemPerguntaAMeta() {
    config.setWhatsappAccessTokenEnc(null);

    assertThat(saude.verificar(config).podeEnviar()).isFalse();
    verify(whatsApp, never()).fetchPhoneNumberDetails(config);
  }

  @Test
  void consultaOkFicaEmCacheEPoupaChamadas() {
    assertThat(saude.verificar(config).podeEnviar()).isTrue();
    assertThat(saude.verificar(config).podeEnviar()).isTrue();

    verify(whatsApp, times(1)).fetchPhoneNumberDetails(config);
  }

  @Test
  void tokenRecusadoNaConsultaBloqueiaEExplicaOMotivo() {
    when(whatsApp.fetchPhoneNumberDetails(config))
        .thenThrow(comoOClienteLanca(erroDaMeta(HttpStatus.UNAUTHORIZED, 190, "Session has expired")));

    SaudeDoEnvioWhatsApp.Situacao s = saude.verificar(config);

    assertThat(s.podeEnviar()).isFalse();
    assertThat(s.motivo()).contains("190", "Session has expired");
  }

  @Test
  void falhaPassageiraNaConsultaLiberaOAtendimento() {
    when(whatsApp.fetchPhoneNumberDetails(config)).thenThrow(new IllegalStateException("timeout"));

    assertThat(saude.verificar(config).podeEnviar()).isTrue();
  }

  @Test
  void falhaDeUmClienteNoEnvioNaoBloqueiaOSalao() {
    // 131047: fora da janela de 24h daquele cliente — os outros clientes continuam sendo atendidos.
    String motivo = saude.registrarFalhaDeEnvio(config,
        comoOClienteLanca(erroDaMeta(HttpStatus.BAD_REQUEST, 131047, "Re-engagement message")));

    assertThat(motivo).isNull();
    assertThat(saude.verificar(config).podeEnviar()).isTrue();
  }

  @Test
  void falhaDoSalaoNoEnvioBloqueiaAteTrocarAConfiguracao() {
    saude.registrarFalhaDeEnvio(config,
        comoOClienteLanca(erroDaMeta(HttpStatus.BAD_REQUEST, 131031, "Account has been locked")));

    assertThat(saude.verificar(config).podeEnviar()).isFalse();

    config.setWhatsappAccessTokenEnc("token-novo"); // alguem trocou o token
    assertThat(saude.verificar(config).podeEnviar()).isTrue();
  }

  @Test
  void depoisDaJanelaUmaMensagemPassaComoTeste() {
    saude.registrarFalhaDeEnvio(config,
        comoOClienteLanca(erroDaMeta(HttpStatus.BAD_REQUEST, 131042, "Payment issue")));
    assertThat(saude.verificar(config).podeEnviar()).isFalse();

    agora.set(agora.get().plus(SaudeDoEnvioWhatsApp.VALIDADE).plusSeconds(1));

    assertThat(saude.verificar(config).podeEnviar()).isTrue();
    verify(whatsApp, never()).fetchPhoneNumberDetails(config);
  }
}
