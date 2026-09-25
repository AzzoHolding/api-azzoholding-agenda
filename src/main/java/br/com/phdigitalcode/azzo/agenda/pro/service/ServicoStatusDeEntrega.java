package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;

/**
 * Traz para a tela o que a Meta diz sobre a ENTREGA de cada mensagem.
 *
 * <p>Aceitar nao e entregar. A Cloud API responde 200 com um {@code wamid} e decide depois: a
 * mensagem pode ser entregue, lida, ou descartada — e o unico lugar onde isso aparece e o status
 * que ela manda pelo webhook. Sem ligar esse status a linha do log, "Aceita" nunca virava
 * "Entregue" nem "Falhou", e uma mensagem que nunca chegou ficava indistinguivel de uma entregue.
 *
 * <p>Foi exatamente o que custou dias em 2026-09: tres envios com {@code wamid} valido, nenhum no
 * celular, e nada na tela explicando.
 */
@Service
public class ServicoStatusDeEntrega {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoStatusDeEntrega.class);

  public static final String ENVIADA = "SENT";
  public static final String ENTREGUE = "DELIVERED";
  public static final String LIDA = "READ";
  public static final String FALHOU = "FAILED";

  /**
   * A ordem em que um envio avanca.
   *
   * <p>Status chega fora de ordem: a Meta pode mandar {@code delivered} depois de {@code read}. Sem
   * isto, uma mensagem lida voltaria para "entregue" — e a tela passaria a mentir para menos.
   */
  private static final List<String> PROGRESSO = List.of(ENVIADA, ENTREGUE, LIDA);

  private final WhatsAppMessageLogRepository messageLogRepository;

  public ServicoStatusDeEntrega(WhatsAppMessageLogRepository messageLogRepository) {
    this.messageLogRepository = messageLogRepository;
  }

  /**
   * @return {@code true} quando havia uma linha no log para aquele {@code wamid}
   */
  @Transactional
  public boolean aplicar(
      UUID tenantId, String providerMessageId, String statusDaMeta, String codigoDoErro, String motivo) {
    if (tenantId == null || providerMessageId == null || providerMessageId.isBlank()) return false;
    String novo = traduzir(statusDaMeta);
    if (novo == null) return false;

    return messageLogRepository
        .findByTenantIdAndProviderMessageId(tenantId, providerMessageId)
        .map(
            linha -> {
              if (!vaiParaFrente(linha.getStatus(), novo)) return true;
              linha.setStatus(novo);
              if (FALHOU.equals(novo)) {
                // O motivo e a unica coisa que permite agir: uma mensagem de marketing descartada
                // pela Meta e um template nao aprovado falham igual, e o codigo dela distingue.
                linha.setErrorMessage(montarMotivo(codigoDoErro, motivo));
              }
              messageLogRepository.save(linha);
              LOG.info(
                  "whatsapp.entrega tenantId={} wamid={} status={} motivo={}",
                  tenantId, providerMessageId, novo, linha.getErrorMessage() == null ? "-" : linha.getErrorMessage());
              return true;
            })
        .orElse(false);
  }

  /** O vocabulario da Meta e minusculo; o do log e o nosso. Status desconhecido nao mexe em nada. */
  private String traduzir(String statusDaMeta) {
    if (statusDaMeta == null) return null;
    return switch (statusDaMeta.trim().toLowerCase()) {
      case "sent" -> ENVIADA;
      case "delivered" -> ENTREGUE;
      case "read" -> LIDA;
      case "failed" -> FALHOU;
      default -> null;
    };
  }

  /**
   * Falha sempre vale — e o que a pessoa precisa ver. O resto so avanca.
   */
  private boolean vaiParaFrente(String atual, String novo) {
    if (FALHOU.equals(novo)) return true;
    if (FALHOU.equals(atual)) return false;
    int posicaoAtual = PROGRESSO.indexOf(atual == null ? ENVIADA : atual);
    int posicaoNova = PROGRESSO.indexOf(novo);
    return posicaoNova > posicaoAtual;
  }

  private String montarMotivo(String codigo, String motivo) {
    if (codigo == null && motivo == null) return "A Meta nao entregou a mensagem.";
    if (codigo == null) return motivo;
    return "(#" + codigo + ") " + (motivo == null ? "a Meta nao entregou a mensagem" : motivo);
  }
}
