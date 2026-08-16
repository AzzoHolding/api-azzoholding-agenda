package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;

/** Espelha {@code modules/nfse/domain/repository/NfseInvoiceItemRepository.java}. */
@Repository
public interface NfseInvoiceItemRepository extends JpaRepository<NfseInvoiceItemEntity, UUID> {

  List<NfseInvoiceItemEntity> findByTenantIdAndInvoiceIdOrderByLineNumberAsc(
      UUID tenantId, UUID invoiceId);

  /**
   * Bulk delete equivalente ao {@code nfseInvoiceItemRepository.delete("tenantId = ?1 and
   * invoiceId = ?2", ...)} do Panache original (usado em {@code atualizarRascunho}). {@code
   * @Modifying} executa o {@code DELETE} imediatamente via {@code executeUpdate()} — sem esperar o
   * flush do fim da transacao (armadilha 2 do briefing), o mesmo comportamento do Panache.
   */
  @Modifying
  @Query(
      "delete from NfseInvoiceItemEntity e where e.tenantId = :tenantId and e.invoiceId = :invoiceId")
  void deleteByTenantAndInvoice(@Param("tenantId") UUID tenantId, @Param("invoiceId") UUID invoiceId);

  default List<NfseInvoiceItemEntity> listByTenantAndInvoice(UUID tenantId, UUID invoiceId) {
    if (tenantId == null || invoiceId == null) return List.of();
    return findByTenantIdAndInvoiceIdOrderByLineNumberAsc(tenantId, invoiceId);
  }
}
