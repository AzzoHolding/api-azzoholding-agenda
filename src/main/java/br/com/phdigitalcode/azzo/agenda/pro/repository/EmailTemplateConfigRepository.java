package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailTemplateConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType;

/**
 * Espelha {@code modules/email/domain/repository/EmailTemplateConfigRepository.java}, mais a
 * consulta por tenant+tipo que o {@code TenantOperationalSettingsService} faz inline no original
 * ({@code find("tenantId = ?1 and templateType = ?2", ...)}).
 */
@Repository
public interface EmailTemplateConfigRepository extends JpaRepository<EmailTemplateConfig, UUID> {

  Optional<EmailTemplateConfig> findFirstByTenantIdAndTemplateType(
      UUID tenantId, EmailTemplateType templateType);

  Optional<EmailTemplateConfig> findFirstByTenantIdIsNullAndTemplateType(
      EmailTemplateType templateType);

  Optional<EmailTemplateConfig> findFirstByTenantIdIsNullAndTemplateTypeAndActiveTrue(
      EmailTemplateType templateType);

  List<EmailTemplateConfig> findByTenantIdIsNullOrderByTemplateTypeAsc();

  default Optional<EmailTemplateConfig> findGlobalByType(EmailTemplateType templateType) {
    return findFirstByTenantIdIsNullAndTemplateType(templateType);
  }

  default Optional<EmailTemplateConfig> findActiveGlobalByType(EmailTemplateType templateType) {
    return findFirstByTenantIdIsNullAndTemplateTypeAndActiveTrue(templateType);
  }

  default List<EmailTemplateConfig> findAllGlobals() {
    return findByTenantIdIsNullOrderByTemplateTypeAsc();
  }
}
