package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
  void purgeSoAlcancaOQueTemMaisDeDoisMeses() {
    ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    when(chatMessageRepository.clearExpiredContents(any())).thenReturn(0L);
    ChatMessageRetentionService service = new ChatMessageRetentionService(chatMessageRepository);

    service.purgeExpiredMessageContents();

    ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
    verify(chatMessageRepository).clearExpiredContents(limite.capture());
    assertThat(limite.getValue())
        .isBefore(Instant.now().minus(58, ChronoUnit.DAYS))
        .isAfter(Instant.now().minus(63, ChronoUnit.DAYS));
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
