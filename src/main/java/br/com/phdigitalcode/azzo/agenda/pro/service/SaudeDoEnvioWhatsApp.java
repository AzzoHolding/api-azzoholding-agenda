package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;

/**
 * "O WhatsApp deste salao consegue enviar agora?" — perguntado ANTES de chamar o assistente
 * (17/10/2026). Antes a IA respondia (gastando tokens), o envio falhava por token vencido ou numero
 * desconectado, e o fallback "nao foi possivel processar" falhava tambem.
 *
 * <p>Tres fontes, da mais barata para a mais cara:
 * <ol>
 *   <li>configuracao minima (token e Phone Number ID), sem rede;</li>
 *   <li>o resultado dos envios reais: falha que e do SALAO (token, permissao, numero nao registrado,
 *       conta bloqueada, pagamento) marca o envio como indisponivel; falha de UM cliente (janela de
 *       24h, numero dele) ou passageira (limite, rede, 5xx) nao marca;</li>
 *   <li>a consulta do numero na Meta ({@code GET /{phone-number-id}}, gratis, sem token de IA), feita
 *       no maximo a cada {@link #VALIDADE} por salao.</li>
 * </ol>
 * Indisponivel vale ate a configuracao mudar (alguem trocou o token) ou passar {@link #VALIDADE}: ai
 * uma mensagem passa como teste. Na duvida (rede, 5xx) libera — nunca deixa o cliente sem resposta por
 * uma falha passageira. Estado em memoria, por instancia.
 */
@Service
public class SaudeDoEnvioWhatsApp {

  static final Duration VALIDADE = Duration.ofMinutes(10);
  private static final Logger LOG = LoggerFactory.getLogger(SaudeDoEnvioWhatsApp.class);

  /**
   * Codigos da Graph API que significam "este salao nao envia nada", e nao "esta mensagem falhou":
   * 190 token invalido/expirado; 10, 200 e 131005 permissao; 133010 numero nao registrado; 131031
   * conta bloqueada; 131042 problema de pagamento. O 100 (parametro invalido) fica de fora: no envio
   * ele tambem aparece para o numero de UM cliente. Phone Number ID errado e pego na consulta do
   * numero, onde qualquer 4xx e do salao.
   */
  static final Set<Integer> CODIGOS_DO_SALAO = Set.of(190, 10, 200, 131005, 133010, 131031, 131042);

  public record Situacao(boolean podeEnviar, String motivo) {
    static Situacao ok() {
      return new Situacao(true, null);
    }

    static Situacao fora(String motivo) {
      return new Situacao(false, motivo);
    }
  }

  private record Registro(boolean podeEnviar, String motivo, String impressao, Instant em) {}

  private final WhatsAppClient whatsAppClient;
  private final ObjectMapper objectMapper;
  private final Clock relogio;
  private final Map<UUID, Registro> porSalao = new ConcurrentHashMap<>();

  @Autowired
  public SaudeDoEnvioWhatsApp(WhatsAppClient whatsAppClient, ObjectMapper objectMapper) {
    this(whatsAppClient, objectMapper, Clock.systemUTC());
  }

  SaudeDoEnvioWhatsApp(WhatsAppClient whatsAppClient, ObjectMapper objectMapper, Clock relogio) {
    this.whatsAppClient = whatsAppClient;
    this.objectMapper = objectMapper;
    this.relogio = relogio;
  }

  public Situacao verificar(TenantWhatsAppConfig config) {
    if (config == null) return Situacao.fora("WhatsApp nao configurado");
    if (vazio(config.getWhatsappAccessTokenEnc())) return Situacao.fora("token de acesso do WhatsApp nao configurado");
    if (vazio(config.getWhatsappPhoneNumberId())) return Situacao.fora("Phone Number ID do WhatsApp nao configurado");

    UUID salao = config.getTenantId();
    String impressao = impressao(config);
    Instant agora = relogio.instant();
    Registro r = porSalao.get(salao);
    if (r != null && Objects.equals(r.impressao(), impressao) && r.em().plus(VALIDADE).isAfter(agora)) {
      return r.podeEnviar() ? Situacao.ok() : Situacao.fora(r.motivo());
    }
    if (r != null && !r.podeEnviar() && Objects.equals(r.impressao(), impressao)) {
      // Venceu a janela de uma falha vista NO ENVIO: deixa uma mensagem passar como teste (a consulta
      // do numero pode dar certo e o envio continuar bloqueado, ex.: conta sem pagamento).
      porSalao.put(salao, new Registro(true, null, impressao, agora));
      return Situacao.ok();
    }

    try {
      whatsAppClient.fetchPhoneNumberDetails(config);
      porSalao.put(salao, new Registro(true, null, impressao, agora));
      return Situacao.ok();
    } catch (RuntimeException e) {
      String motivo = motivoSeForDoSalao(e, true);
      if (motivo == null) {
        LOG.warn("whatsapp.saude.verificacao_inconclusiva tenantId={} — liberando o atendimento", salao);
        return Situacao.ok();
      }
      porSalao.put(salao, new Registro(false, motivo, impressao, agora));
      LOG.warn("whatsapp.saude.envio_indisponivel tenantId={} motivo={}", salao, motivo);
      return Situacao.fora(motivo);
    }
  }

  public void registrarEnvioOk(TenantWhatsAppConfig config) {
    if (config == null || config.getTenantId() == null) return;
    porSalao.put(config.getTenantId(), new Registro(true, null, impressao(config), relogio.instant()));
  }

  /** Falha de envio real: so marca o salao se a causa for do salao. Devolve o motivo, se marcou. */
  public String registrarFalhaDeEnvio(TenantWhatsAppConfig config, Throwable erro) {
    if (config == null || config.getTenantId() == null) return null;
    String motivo = motivoSeForDoSalao(erro, false);
    if (motivo != null) {
      porSalao.put(config.getTenantId(), new Registro(false, motivo, impressao(config), relogio.instant()));
      LOG.warn("whatsapp.saude.envio_indisponivel tenantId={} motivo={}", config.getTenantId(), motivo);
    }
    return motivo;
  }

  /** Motivo legivel quando a falha impede o salao de enviar qualquer mensagem; null quando nao. */
  String motivoSeForDoSalao(Throwable erro, boolean consultaDoNumeroDoSalao) {
    for (Throwable t = erro; t != null; t = t.getCause()) {
      if (t instanceof IllegalArgumentException iae && iae.getMessage() != null
          && iae.getMessage().toLowerCase().contains("nao configurado")) {
        return iae.getMessage();
      }
      if (t instanceof RestClientResponseException http) {
        int status = http.getStatusCode().value();
        Integer codigo = codigoDaMeta(http.getResponseBodyAsString());
        String mensagem = mensagemDaMeta(http.getResponseBodyAsString());
        boolean doSalao = status == 401 || status == 403
            || (codigo != null && CODIGOS_DO_SALAO.contains(codigo))
            // Na consulta do PROPRIO numero, 4xx (fora limite de taxa) e configuracao do salao errada.
            || (consultaDoNumeroDoSalao && status >= 400 && status < 500 && status != 429);
        if (doSalao) {
          return "a Meta recusou o envio (HTTP " + status + (codigo != null ? ", codigo " + codigo : "") + ")"
              + (mensagem != null ? ": " + mensagem : "");
        }
        return null; // cliente especifico, limite de taxa ou erro passageiro da Meta
      }
    }
    return null;
  }

  private Integer codigoDaMeta(String corpo) {
    JsonNode erro = erroDaMeta(corpo);
    return erro != null && erro.path("code").isInt() ? erro.path("code").asInt() : null;
  }

  private String mensagemDaMeta(String corpo) {
    JsonNode erro = erroDaMeta(corpo);
    String m = erro != null ? erro.path("message").asText(null) : null;
    return m == null || m.isBlank() ? null : (m.length() > 300 ? m.substring(0, 300) : m);
  }

  private JsonNode erroDaMeta(String corpo) {
    if (corpo == null || corpo.isBlank()) return null;
    try {
      JsonNode erro = objectMapper.readTree(corpo).path("error");
      return erro.isMissingNode() ? null : erro;
    } catch (Exception e) {
      return null;
    }
  }

  private static String impressao(TenantWhatsAppConfig config) {
    return Integer.toHexString(Objects.hash(config.getWhatsappAccessTokenEnc(), config.getWhatsappPhoneNumberId()));
  }

  private static boolean vazio(String s) {
    return s == null || s.isBlank();
  }
}
