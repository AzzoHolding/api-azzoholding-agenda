package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;

/** Espelha {@code modules/chat/application/ChatMessageRetentionService.java}. */
@Service
public class ChatMessageRetentionService {

  private static final Logger LOG = LoggerFactory.getLogger(ChatMessageRetentionService.class);

  private final ChatMessageRepository chatMessageRepository;

  public ChatMessageRetentionService(ChatMessageRepository chatMessageRepository) {
    this.chatMessageRepository = chatMessageRepository;
  }

  @Transactional
  public long purgeExpiredMessageContents() {
    Instant now = Instant.now();
    LOG.info("ChatMessageRetention iniciado. referencia={}", now);
    long updated = chatMessageRepository.clearExpiredContents(now);
    LOG.info("ChatMessageRetention finalizado. atualizados={}", updated);
    return updated;
  }
}
