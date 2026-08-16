package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatConversationRepository;

/**
 * Cobre {@code CustomerCommunicationChannelResolver} do original: prioridade de conversa
 * Telegram (usa o {@code externalContactId} da conversa, sem fallback), roteamento WhatsApp
 * (usa a conversa existente ou cai para os fallbacks informados, em ordem) e validacao de
 * cliente invalido.
 */
class CustomerCommunicationChannelResolverTest {

  private ChatConversationRepository chatConversationRepository;
  private CustomerCommunicationChannelResolver resolver;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    chatConversationRepository = mock(ChatConversationRepository.class);
    resolver = new CustomerCommunicationChannelResolver(chatConversationRepository);
  }

  private Cliente client() {
    Cliente cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    return cliente;
  }

  @Test
  void clienteNuloLancaIllegalArgument() {
    assertThatThrownBy(() -> resolver.resolve(tenantId, null, "5511999999999"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tenantNuloLancaIllegalArgument() {
    assertThatThrownBy(() -> resolver.resolve(null, client(), "5511999999999"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clienteSemIdLancaIllegalArgument() {
    Cliente semId = new Cliente();
    assertThatThrownBy(() -> resolver.resolve(tenantId, semId, "5511999999999"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void semConversaExistenteUsaPrimeiroFallbackWhatsAppValido() {
    Cliente cliente = client();
    when(chatConversationRepository.findLatestByTenantAndClient(tenantId, cliente.getId()))
        .thenReturn(Optional.empty());

    var resolved = resolver.resolve(tenantId, cliente, " ", "5511988887777");

    assertThat(resolved.channel()).isEqualTo(ChatChannel.WHATSAPP);
    assertThat(resolved.externalContactId()).isEqualTo("5511988887777");
    assertThat(resolved.conversation()).isNull();
  }

  @Test
  void conversaTelegramExistenteIgnoraFallbackWhatsApp() {
    Cliente cliente = client();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setChannel(ChatChannel.TELEGRAM);
    conversation.setExternalContactId("123456");
    when(chatConversationRepository.findLatestByTenantAndClient(tenantId, cliente.getId()))
        .thenReturn(Optional.of(conversation));

    var resolved = resolver.resolve(tenantId, cliente, "5511988887777");

    assertThat(resolved.channel()).isEqualTo(ChatChannel.TELEGRAM);
    assertThat(resolved.externalContactId()).isEqualTo("123456");
    assertThat(resolved.conversation()).isSameAs(conversation);
  }

  @Test
  void conversaTelegramSemExternalContactIdLanca() {
    Cliente cliente = client();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setChannel(ChatChannel.TELEGRAM);
    conversation.setExternalContactId(" ");

    assertThatThrownBy(() -> resolver.resolve(tenantId, cliente, conversation, "5511988887777"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void conversaWhatsAppExistenteTemPrioridadeSobreFallback() {
    Cliente cliente = client();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setChannel(ChatChannel.WHATSAPP);
    conversation.setExternalContactId("5511977776666");

    var resolved = resolver.resolve(tenantId, cliente, conversation, "5511988887777");

    assertThat(resolved.channel()).isEqualTo(ChatChannel.WHATSAPP);
    assertThat(resolved.externalContactId()).isEqualTo("5511977776666");
  }

  @Test
  void preferredConversationEvitaConsultaAoRepositorio() {
    Cliente cliente = client();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setChannel(ChatChannel.WHATSAPP);
    conversation.setExternalContactId("5511977776666");

    resolver.resolve(tenantId, cliente, conversation, "5511988887777");

    org.mockito.Mockito.verifyNoInteractions(chatConversationRepository);
  }
}
