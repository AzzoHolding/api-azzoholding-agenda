package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatMessageEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;

/**
 * Cobre {@code ChatMessageStatusService} do original: mapeamento de status de provider
 * (sent/delivered/read/failed), timestamp correspondente por status, normalizacao de erro e
 * os casos de "nao encontrado"/"status desconhecido" que retornam {@code false} sem tocar a
 * mensagem.
 */
class ChatMessageStatusServiceTest {

  private ChatMessageRepository chatMessageRepository;
  private ChatMessageStatusService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    chatMessageRepository = mock(ChatMessageRepository.class);
    service = new ChatMessageStatusService(chatMessageRepository);
  }

  @Test
  void tenantOuProviderMessageIdAusenteRetornaFalse() {
    assertThat(service.applyProviderStatus(null, "abc", "sent", null, null, null)).isFalse();
    assertThat(service.applyProviderStatus(tenantId, null, "sent", null, null, null)).isFalse();
    assertThat(service.applyProviderStatus(tenantId, " ", "sent", null, null, null)).isFalse();
  }

  @Test
  void mensagemNaoEncontradaRetornaFalse() {
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.1"))
        .thenReturn(Optional.empty());

    assertThat(service.applyProviderStatus(tenantId, "wamid.1", "sent", null, null, null))
        .isFalse();
  }

  @Test
  void statusDesconhecidoRetornaFalseENaoAlteraMensagem() {
    ChatMessageEntity message = new ChatMessageEntity();
    message.setStatus(ChatMessageStatus.QUEUED);
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.1"))
        .thenReturn(Optional.of(message));

    boolean result = service.applyProviderStatus(tenantId, "wamid.1", "unknown-status", null, null, null);

    assertThat(result).isFalse();
    assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.QUEUED);
  }

  @Test
  void statusSentAtualizaSentAt() {
    ChatMessageEntity message = new ChatMessageEntity();
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.1"))
        .thenReturn(Optional.of(message));
    Instant when = Instant.parse("2026-01-01T10:00:00Z");

    boolean result = service.applyProviderStatus(tenantId, "wamid.1", "sent", null, null, when);

    assertThat(result).isTrue();
    assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.SENT);
    assertThat(message.getSentAt()).isEqualTo(when);
  }

  @Test
  void statusDeliveredAtualizaDeliveredAt() {
    ChatMessageEntity message = new ChatMessageEntity();
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.1"))
        .thenReturn(Optional.of(message));
    Instant when = Instant.parse("2026-01-01T10:05:00Z");

    service.applyProviderStatus(tenantId, "wamid.1", "delivered", null, null, when);

    assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.DELIVERED);
    assertThat(message.getDeliveredAt()).isEqualTo(when);
  }

  @Test
  void statusReadAtualizaReadAt() {
    ChatMessageEntity message = new ChatMessageEntity();
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.1"))
        .thenReturn(Optional.of(message));
    Instant when = Instant.parse("2026-01-01T10:10:00Z");

    service.applyProviderStatus(tenantId, "wamid.1", "READ", null, null, when);

    assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.READ);
    assertThat(message.getReadAt()).isEqualTo(when);
  }

  @Test
  void statusFailedAtualizaFailedAtEErro() {
    ChatMessageEntity message = new ChatMessageEntity();
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.1"))
        .thenReturn(Optional.of(message));
    Instant when = Instant.parse("2026-01-01T10:15:00Z");

    service.applyProviderStatus(tenantId, "wamid.1", "failed", "131047", " Erro de entrega ", when);

    assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.FAILED);
    assertThat(message.getFailedAt()).isEqualTo(when);
    assertThat(message.getProviderErrorCode()).isEqualTo("131047");
    assertThat(message.getProviderErrorMessage()).isEqualTo("Erro de entrega");
  }

  @Test
  void occurredAtNuloUsaInstantNow() {
    ChatMessageEntity message = new ChatMessageEntity();
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.1"))
        .thenReturn(Optional.of(message));
    Instant before = Instant.now();

    service.applyProviderStatus(tenantId, "wamid.1", "sent", null, null, null);

    assertThat(message.getSentAt()).isNotNull();
    assertThat(message.getSentAt()).isAfterOrEqualTo(before);
  }

  @Test
  void erroEmBrancoENormalizadoParaNulo() {
    ChatMessageEntity message = new ChatMessageEntity();
    when(chatMessageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.1"))
        .thenReturn(Optional.of(message));

    service.applyProviderStatus(tenantId, "wamid.1", "sent", "   ", "", Instant.now());

    assertThat(message.getProviderErrorCode()).isNull();
    assertThat(message.getProviderErrorMessage()).isNull();
  }
}
