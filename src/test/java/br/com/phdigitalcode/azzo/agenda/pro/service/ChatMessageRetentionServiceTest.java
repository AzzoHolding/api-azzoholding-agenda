package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;

/** Espelha {@code modules/chat/application/ChatMessageRetentionService.java}. */
class ChatMessageRetentionServiceTest {

  @Test
  void purgeExpiredMessageContentsDelegaAoRepositorioEDevolveContagem() {
    ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    when(chatMessageRepository.clearExpiredContents(any())).thenReturn(7L);
    ChatMessageRetentionService service = new ChatMessageRetentionService(chatMessageRepository);

    long updated = service.purgeExpiredMessageContents();

    assertThat(updated).isEqualTo(7L);
  }

  @Test
  void purgeExpiredMessageContentsDevolveZeroQuandoNadaExpirou() {
    ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    when(chatMessageRepository.clearExpiredContents(any())).thenReturn(0L);
    ChatMessageRetentionService service = new ChatMessageRetentionService(chatMessageRepository);

    long updated = service.purgeExpiredMessageContents();

    assertThat(updated).isZero();
  }
}
