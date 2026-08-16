package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ChatMessageRetentionService;

/**
 * Espelha {@code modules/chat/infrastructure/scheduler/ChatMessageRetentionScheduler.java}
 * ({@code @Scheduled(cron = "0 15 3 * * ?", concurrentExecution = SKIP)}).
 *
 * <p>O original NAO declara timezone no {@code @Scheduled} (ao contrario de
 * {@code LicenseExpirationNotificationScheduler}) — o cron roda no fuso padrao da JVM do
 * container, sem conversao explicita. Preservado sem {@code zone}, mesma omissao do original.
 */
@Component
public class ChatMessageRetentionScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(ChatMessageRetentionScheduler.class);

  private final ChatMessageRetentionService chatMessageRetentionService;

  public ChatMessageRetentionScheduler(ChatMessageRetentionService chatMessageRetentionService) {
    this.chatMessageRetentionService = chatMessageRetentionService;
  }

  @Scheduled(cron = "0 15 3 * * ?")
  void purgeExpiredMessageContents() {
    try {
      long updated = chatMessageRetentionService.purgeExpiredMessageContents();
      if (updated > 0) {
        LOG.info("chat_retention_purge updated={}", updated);
      }
    } catch (Exception e) {
      LOG.error("ChatMessageRetentionScheduler falhou.", e);
      throw e;
    }
  }
}
