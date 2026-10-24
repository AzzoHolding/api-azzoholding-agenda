package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppTemplateEntity;

/** Os templates de mensagem de cada salao, com o estado da analise da Meta. */
@Repository
public interface WhatsAppTemplateRepository extends JpaRepository<WhatsAppTemplateEntity, UUID> {

  List<WhatsAppTemplateEntity> findByTenantId(UUID tenantId);

  Optional<WhatsAppTemplateEntity> findByTenantIdAndFinalidade(UUID tenantId, String finalidade);

  /** Os que a Meta ainda nao decidiu — e o que o monitoramento precisa reconferir. */
  List<WhatsAppTemplateEntity> findByStatus(String status);
}
