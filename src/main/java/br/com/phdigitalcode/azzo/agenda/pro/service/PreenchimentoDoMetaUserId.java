package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MetaEmbeddedSignupGateway;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * SEG-014 (parte 2): quem se conectou pelo cadastro embutido antes de o {@code meta_user_id}
 * existir nao tem o id, e o callback de exclusao da Meta nao acharia o salao. Na subida, pergunta
 * a Meta ({@code debug_token}) com o token ja guardado. Depois de preenchido, nao repete; uma falha
 * so deixa para a proxima subida (e, ate la, o callback segue para tratamento manual).
 */
@Component
public class PreenchimentoDoMetaUserId {

  private static final Logger LOG = LoggerFactory.getLogger(PreenchimentoDoMetaUserId.class);
  private static final String TOKEN_SOURCE_EMBEDDED = "EMBEDDED_CODE_EXCHANGE";

  private final TenantWhatsAppConfigRepository repository;
  private final MetaEmbeddedSignupGateway metaEmbeddedSignupGateway;
  private final EncryptionService encryptionService;

  public PreenchimentoDoMetaUserId(
      TenantWhatsAppConfigRepository repository,
      MetaEmbeddedSignupGateway metaEmbeddedSignupGateway,
      EncryptionService encryptionService) {
    this.repository = repository;
    this.metaEmbeddedSignupGateway = metaEmbeddedSignupGateway;
    this.encryptionService = encryptionService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void preencher() {
    List<TenantWhatsAppConfig> semId = repository.findByWhatsappTokenSourceAndMetaUserIdIsNull(TOKEN_SOURCE_EMBEDDED);
    for (TenantWhatsAppConfig config : semId) {
      if (config.getWhatsappAccessTokenEnc() == null || config.getWhatsappAccessTokenEnc().isBlank()) continue;
      try {
        String metaUserId = metaEmbeddedSignupGateway.fetchTokenUserId(
            encryptionService.decrypt(config.getWhatsappAccessTokenEnc()));
        if (metaUserId == null) continue;
        config.setMetaUserId(metaUserId);
        repository.save(config);
        LOG.info("whatsapp.meta_user_id.preenchido tenantId={}", config.getTenantId());
      } catch (RuntimeException e) {
        LOG.warn("whatsapp.meta_user_id.nao_preenchido tenantId={} motivo={}", config.getTenantId(), e.getMessage());
      }
    }
  }
}
