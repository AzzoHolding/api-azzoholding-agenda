package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEventEntity;

/** Espelha {@code modules/nfse/domain/repository/NfseInvoiceEventRepository.java}. */
@Repository
public interface NfseInvoiceEventRepository extends JpaRepository<NfseInvoiceEventEntity, UUID> {

  List<NfseInvoiceEventEntity> findByTenantIdAndInvoiceIdOrderByCreatedAtDesc(
      UUID tenantId, UUID invoiceId);

  long countByCreatedAtGreaterThanEqualAndProviderCodeIn(Instant threshold, List<String> providerCodes);

  default List<NfseInvoiceEventEntity> listByTenantAndInvoice(UUID tenantId, UUID invoiceId) {
    if (tenantId == null || invoiceId == null) return List.of();
    return findByTenantIdAndInvoiceIdOrderByCreatedAtDesc(tenantId, invoiceId);
  }

  /** Codigos HTTP considerados "provedor indisponivel", como no original. */
  default long countProviderUnavailableSince(Instant threshold) {
    if (threshold == null) return 0;
    return countByCreatedAtGreaterThanEqualAndProviderCodeIn(
        threshold, List.of("408", "429", "502", "503", "504"));
  }
}
