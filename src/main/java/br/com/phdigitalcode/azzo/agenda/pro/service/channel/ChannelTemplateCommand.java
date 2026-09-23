package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import java.util.List;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;

/**
 * Um envio de TEMPLATE aprovado.
 *
 * <p><b>E a unica forma de falar com quem nunca escreveu para o salao.</b> Texto livre so e
 * entregue dentro de 24h da ultima mensagem do cliente; fora dessa janela a Meta aceita, devolve o
 * identificador e descarta. Confirmacao e lembrete de cliente novo caem sempre nesse caso.
 *
 * <p>{@code variaveis} vai na ordem de {@code {{1}}, {@code {{2}}}...} do template aprovado, e
 * {@code textoEquivalente} e a mesma mensagem escrita por extenso — usada onde template nao existe
 * como conceito (Telegram) e para registrar no log o que o cliente de fato leu.
 */
public record ChannelTemplateCommand(
    UUID tenantId,
    ChatChannel channel,
    String recipientExternalId,
    String templateName,
    String templateLanguage,
    List<String> variaveis,
    String textoEquivalente) {

  public ChannelTemplateCommand {
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatorio para envio.");
    if (channel == null) throw new IllegalArgumentException("Canal obrigatorio para envio.");
    if (recipientExternalId == null || recipientExternalId.isBlank()) {
      throw new IllegalArgumentException("Destino obrigatorio para envio.");
    }
    if (templateName == null || templateName.isBlank()) {
      throw new IllegalArgumentException("Nome do template obrigatorio para envio.");
    }
    if (textoEquivalente == null || textoEquivalente.isBlank()) {
      // Sem ele, um canal que nao tem template nao teria o que mandar, e o log guardaria o nome do
      // template em vez do que a pessoa leu.
      throw new IllegalArgumentException("Texto equivalente obrigatorio para envio por template.");
    }
    variaveis = variaveis == null ? List.of() : List.copyOf(variaveis);
  }
}
