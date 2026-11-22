package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AssistantApiClient;
import br.com.phdigitalcode.azzo.agenda.pro.service.AfterCommitExecutor;
import br.com.phdigitalcode.azzo.agenda.pro.service.ChatService;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.AreaDaClienteCommunicationChannelAdapter;

/**
 * Chat da area da cliente com o salao (pedido de 20/11/2026). E um canal do chat do salao (AREA_CLIENTE): a conversa
 * aparece na tela de Chat do salao, ao lado do WhatsApp e do Telegram.
 *
 * <p>Decisao do dono: o assistente responde neste canal igual aos outros; so muda por onde a mensagem entra e sai. O
 * fluxo e o do webhook do Telegram: grava a mensagem; sem modo manual, chama o assistente (pelo telefone do cadastro,
 * como no WhatsApp) e grava a resposta. A chamada ao assistente roda depois do commit, fora do pedido da cliente: a
 * resposta chega pela consulta da tela.
 */
@Service
public class ChatDaArea {

  private static final Logger LOG = LoggerFactory.getLogger(ChatDaArea.class);
  static final int MAXIMO = 2000;
  /** Mesmo texto do Telegram quando o assistente falha. */
  static final String RESPOSTA_DE_FALHA =
      "Recebi sua mensagem, mas nao consegui concluir o processamento agora. Tente novamente em instantes.";

  private final NamedParameterJdbcTemplate jdbc;
  private final ContasDaCliente contas;
  private final ChatService chat;
  private final AssistantApiClient assistente;
  private final AfterCommitExecutor depoisDoCommit;
  private final AcessoDeSaude acesso;
  private final LinksDaCliente links;

  public ChatDaArea(NamedParameterJdbcTemplate jdbc, ContasDaCliente contas, ChatService chat, AssistantApiClient assistente,
      AfterCommitExecutor depoisDoCommit, AcessoDeSaude acesso, LinksDaCliente links) {
    this.links = links;
    this.jdbc = jdbc;
    this.contas = contas;
    this.chat = chat;
    this.assistente = assistente;
    this.depoisDoCommit = depoisDoCommit;
    this.acesso = acesso;
  }

  static String textoLimpo(String texto) {
    if (texto == null) throw new IllegalArgumentException("Escreva a mensagem.");
    String t = texto.strip();
    if (t.isEmpty()) throw new IllegalArgumentException("Escreva a mensagem.");
    if (t.length() > MAXIMO) throw new IllegalArgumentException("A mensagem passa de " + MAXIMO + " caracteres.");
    return t;
  }

  /** As conversas da cliente: um salao por linha, com a ultima mensagem e as nao lidas. */
  public Map<String, Object> conversas(UUID conta) {
    List<Map<String, Object>> r = new ArrayList<>();
    long total = 0;
    for (Map<String, Object> v : jdbc.queryForList("""
        select v.tenant_id, v.client_id, t.name, t.slug, c.last_message_at, c.last_message_preview
          from vinculos_da_conta v join tenants t on t.id = v.tenant_id
          left join chat_conversations c on c.tenant_id = v.tenant_id and c.client_id = v.client_id and c.channel = 'AREA_CLIENTE'
         where v.conta_id = :c and v.desfeito_em is null
         order by c.last_message_at desc nulls last, t.name""", Map.of("c", conta))) {
      long naoLidas = chat.naoLidasDaArea((UUID) v.get("tenant_id"), (UUID) v.get("client_id"));
      total += naoLidas;
      r.add(m("salaoId", v.get("tenant_id"), "salao", v.get("name"), "slug", v.get("slug"), "ultimaEm", Saude.instante(v.get("last_message_at")),
          "ultima", v.get("last_message_preview"), "naoLidas", naoLidas));
    }
    return m("conversas", r, "naoLidas", total);
  }

  public List<Map<String, Object>> mensagens(UUID conta, UUID tenantId) {
    UUID clientId = contas.cadastroNoSalao(conta, tenantId);
    return chat.mensagensDaArea(tenantId, clientId);
  }

  /** A cliente manda a mensagem. A resposta do assistente (se ele estiver ativo na conversa) chega depois. */
  public List<Map<String, Object>> enviar(UUID conta, UUID tenantId, String texto) {
    String t = textoLimpo(texto);
    UUID clientId = contas.cadastroNoSalao(conta, tenantId);
    String nome = jdbc.queryForObject("select name from clients where id = :c", Map.of("c", clientId), String.class);
    String telefone = jdbc.queryForObject("select phone from clients where id = :c", Map.of("c", clientId), String.class);
    chat.garantirConversaDaArea(tenantId, clientId, conta.toString());
    ChatService.InboundProcessingResult entrada = chat.processInboundAreaMessage(tenantId, conta.toString(), t, nome);
    acesso.auditar(tenantId, "CLIENT_AREA_CHAT_MESSAGE", clientId, m("conta", conta), false);
    if (!entrada.duplicateInboundMessage && !entrada.manualModeEnabled) {
      UUID conversa = entrada.conversation.getId();
      depoisDoCommit.run(() -> responderComAssistente(tenantId, conversa, clientId, conta.toString(), telefone, nome, t));
    }
    return chat.mensagensDaArea(tenantId, clientId);
  }

  /** O mesmo assistente do WhatsApp e do Telegram. Identifica a cliente pelo telefone do cadastro no salao. */
  void responderComAssistente(UUID tenantId, UUID conversa, UUID clientId, String contaId, String telefone, String nome, String texto) {
    String identificador = telefone != null && telefone.replaceAll("\\D", "").length() >= 10 ? telefone.replaceAll("\\D", "") : contaId;
    String resposta;
    ChatMessageStatus status = ChatMessageStatus.SENT;
    String erro = null;
    try {
      AssistantMessageResponse r = assistente.processarMensagem(tenantId.toString(), identificador, nome, new AssistantMessageRequest(texto));
      resposta = r == null || r.reply == null || r.reply.isBlank() ? null : r.reply.strip();
      if (resposta == null) return;
    } catch (RuntimeException e) {
      LOG.warn("areaDaCliente.chat.assistenteFalhou tenant={} conversa={}", tenantId, conversa, e);
      resposta = RESPOSTA_DE_FALHA;
      erro = "ASSISTENTE_INDISPONIVEL";
    }
    try {
      chat.registerOutboundAssistantMessage(tenantId, conversa, clientId, resposta, "area-" + UUID.randomUUID(), status,
          erro, null);
    } catch (RuntimeException e) {
      LOG.warn("areaDaCliente.chat.registroFalhou tenant={} conversa={}", tenantId, conversa, e);
    }
  }

  /** Pessoa do salao respondeu pela tela de Chat: e-mail para a cliente (no maximo 1 por hora, com e-mail confirmado). */
  @EventListener
  public void aoResponderPeloSalao(AreaDaClienteCommunicationChannelAdapter.MensagemDoSalaoNaArea evento) {
    try {
      UUID conta = UUID.fromString(evento.contaId());
      Boolean recente = jdbc.queryForObject("""
          select exists(select 1 from email_jobs j join contas_de_cliente c on lower(c.email) = lower(j.recipient_email)
                         where c.id = :c and j.email_type = 'CLIENTE_AVISO' and j.payload_json like '%Nova mensagem do salão%'
                           and j.created_at > now() - interval '1 hour')""", Map.of("c", conta), Boolean.class);
      if (Boolean.TRUE.equals(recente)) return;
      String salao = jdbc.queryForObject("select name from tenants where id = :t", Map.of("t", evento.tenantId()), String.class);
      contas.avisarConta(conta, "Nova mensagem do salão", "O " + salao + " respondeu a sua mensagem na área da cliente.",
          "Ler a mensagem", links.urlDaArea() + "/mensagens?salao=" + evento.tenantId());
    } catch (RuntimeException e) {
      LOG.debug("areaDaCliente.chat.avisoFalhou", e);
    }
  }
}
