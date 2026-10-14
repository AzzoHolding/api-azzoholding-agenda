package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AssistantApiClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatConversationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NotificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;

/**
 * O dado pessoal do titular que mora FORA da ficha dele: chat, notificacoes, log do WhatsApp, fila
 * de reativacao e a foto no storage.
 *
 * <p>Usado pela anonimizacao (LGPD art. 18, VI) e pela exclusao do cliente. Ate 2026-10-13 a
 * exclusao apagava so a linha do cliente: a conversa, as notificacoes e o ciclo de reativacao
 * (que e {@code ON DELETE SET NULL} e guarda nome e telefone) ficavam, e depois ninguem mais os
 * alcancava pelo id. Notificacao e log do WhatsApp so eram achados pelo agendamento; mensagem sem
 * agendamento (erro de entrega, alerta) para o mesmo numero ficava — agora tambem pelo telefone.
 *
 * <p>Precisa ser chamado ANTES de zerar telefone e foto do cliente: e com eles que acha o resto.
 */
@Component
public class RastrosDoTitular {

  private static final Logger LOG = LoggerFactory.getLogger(RastrosDoTitular.class);

  private final ChatConversationRepository conversationRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final NotificationRepository notificationRepository;
  private final WhatsAppMessageLogRepository whatsAppMessageLogRepository;
  private final WhatsAppBookingReactivationCycleRepository reactivationCycleRepository;
  private final MinioStorageService minioStorageService;
  private final AssistantApiClient assistantApiClient;

  public RastrosDoTitular(
      ChatConversationRepository conversationRepository,
      ChatMessageRepository chatMessageRepository,
      NotificationRepository notificationRepository,
      WhatsAppMessageLogRepository whatsAppMessageLogRepository,
      WhatsAppBookingReactivationCycleRepository reactivationCycleRepository,
      MinioStorageService minioStorageService,
      AssistantApiClient assistantApiClient) {
    this.conversationRepository = conversationRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.notificationRepository = notificationRepository;
    this.whatsAppMessageLogRepository = whatsAppMessageLogRepository;
    this.reactivationCycleRepository = reactivationCycleRepository;
    this.minioStorageService = minioStorageService;
    this.assistantApiClient = assistantApiClient;
  }

  public record Resultado(int conversas, int mensagens, int notificacoes, int mensagensWhatsapp, int reativacoes) {}

  public Resultado limpar(Cliente cliente) {
    UUID tenantId = cliente.getTenantId();
    UUID clientId = cliente.getId();

    int conversas = conversationRepository.anonimizarPorClienteRaw(tenantId, clientId);
    int mensagens = chatMessageRepository.anonimizarPorClienteRaw(tenantId, clientId);
    int notificacoes = notificationRepository.anonimizarPorClienteRaw(tenantId, clientId);
    int mensagensWhatsapp = whatsAppMessageLogRepository.anonimizarPorClienteRaw(tenantId, clientId);
    String telefone = telefoneLocal(cliente.getPhone());
    if (telefone != null) {
      notificacoes += notificationRepository.anonimizarPorTelefoneRaw(tenantId, telefone, telefone.length());
      mensagensWhatsapp +=
          whatsAppMessageLogRepository.anonimizarPorTelefoneRaw(tenantId, telefone, telefone.length());
    }
    int reativacoes = reactivationCycleRepository.anonimizarPorClienteRaw(tenantId, clientId);
    removerFoto(cliente);
    esquecerNoAssistente(tenantId, telefone);
    return new Resultado(conversas, mensagens, notificacoes, mensagensWhatsapp, reativacoes);
  }

  /**
   * DDD + numero (10 ou 11 digitos), sem o 55: e assim que o mesmo numero aparece gravado com ou
   * sem DDI, com ou sem mascara. Curto demais para identificar alguem, devolve null.
   */
  static String telefoneLocal(String telefone) {
    if (telefone == null) return null;
    String digitos = telefone.replaceAll("\\D", "");
    if (digitos.startsWith("55") && digitos.length() >= 12) digitos = digitos.substring(2);
    return digitos.length() >= 10 ? digitos : null;
  }

  /**
   * O assistente guarda o estado da conversa por ate 8 horas, pelo telefone (com e sem 55). Melhor
   * esforco: o assistente fora do ar nao segura a anonimizacao, e o TTL apaga de qualquer forma.
   */
  private void esquecerNoAssistente(UUID tenantId, String telefone) {
    if (telefone == null) return;
    for (String identificador : java.util.List.of("55" + telefone, telefone)) {
      try {
        assistantApiClient.esquecerConversa(tenantId.toString(), identificador);
      } catch (RuntimeException e) {
        LOG.warn("lgpd.rastros.assistenteNaoLimpo tenantId={}", tenantId, e);
        return;
      }
    }
  }

  /** A foto e do titular: sai do storage. Melhor esforco — o arquivo orfao nao segura a anonimizacao. */
  private void removerFoto(Cliente cliente) {
    String avatar = cliente.getAvatar();
    if (avatar == null || avatar.isBlank() || avatar.startsWith("http://") || avatar.startsWith("https://")) return;
    try {
      minioStorageService.removerArquivoClienteAvatar(avatar, cliente.getTenantId());
    } catch (RuntimeException e) {
      LOG.warn("lgpd.rastros.fotoNaoRemovida tenantId={} clientId={}", cliente.getTenantId(), cliente.getId(), e);
    }
  }
}
