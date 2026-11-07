package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;

/** Espelha {@code modules/chat/application/ChatMessageRetentionService.java}. */
@Service
public class ChatMessageRetentionService {

  private static final Logger LOG = LoggerFactory.getLogger(ChatMessageRetentionService.class);

  /** So o que tem mais de 2 meses sai, e so se ja foi lido: ver o repositorio. */
  private static final int MESES_DE_GUARDA = 2;

  private final ChatMessageRepository chatMessageRepository;

  public ChatMessageRetentionService(ChatMessageRepository chatMessageRepository) {
    this.chatMessageRepository = chatMessageRepository;
  }

  @Transactional
  public long purgeExpiredMessageContents() {
    Instant limite = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(MESES_DE_GUARDA).toInstant();
    LOG.info("ChatMessageRetention iniciado. referencia={}", limite);
    long updated = chatMessageRepository.clearExpiredContents(limite);
    LOG.info("ChatMessageRetention finalizado. atualizados={}", updated);
    return updated;
  }
}
