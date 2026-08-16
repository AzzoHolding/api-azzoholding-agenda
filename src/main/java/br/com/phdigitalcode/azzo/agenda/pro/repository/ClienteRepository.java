package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;

/** Espelha {@code modules/customers/domain/repository/ClienteRepository.java} (parte CRUD). */
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

  Optional<Cliente> findByIdAndTenantId(UUID id, UUID tenantId);

  List<Cliente> findByTenantIdOrderByName(UUID tenantId);

  /**
   * Espelha {@code tenantId = ?1 and whatsappOptOut = true ORDER BY whatsappOptOutAt DESC} de
   * {@code ReactivationConfigService.listOptOutsPaged} do original. {@link Page} devolve total e
   * conteudo paginado em uma unica chamada (Spring Data faz a query de count internamente).
   */
  Page<Cliente> findByTenantIdAndWhatsappOptOutTrueOrderByWhatsappOptOutAtDesc(UUID tenantId, Pageable pageable);

  /**
   * Usado por {@code AssistantInternalController.buscarClientePorIdentificador} (espelha
   * {@code AssistantInternalResource.findClientByIdentifier} do original, ramo de e-mail).
   */
  Optional<Cliente> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

  /**
   * Espelha {@code findByTenantAndEmail} de {@code modules/customers/domain/repository/
   * ClienteRepository.java} (usado por {@code ProcessadorImportacaoClientesService}):
   * {@code tenantId = ?1 and lower(email) = ?2 order by createdAt desc}, primeiro resultado.
   */
  Optional<Cliente> findFirstByTenantIdAndEmailIgnoreCaseOrderByCreatedAtDesc(UUID tenantId, String email);

  /**
   * Paginacao do endpoint {@code GET /api/v1/clients/paged}. A ordenacao
   * ({@code createdAt desc, name asc}) vem do {@code Pageable} montado no service, replicando
   * exatamente o {@code order by createdAt desc, name asc} do Panache original.
   */
  Page<Cliente> findByTenantId(UUID tenantId, Pageable pageable);

  /**
   * Espelha {@code findByTenantAndNormalizedPhoneDigits} (query nativa com {@code regexp_replace})
   * de {@code modules/customers/domain/repository/ClienteRepository.java}.
   */
  @Query(
      value =
          "SELECT * FROM clients WHERE tenant_id = :tenantId "
              + "AND regexp_replace(COALESCE(phone, ''), '\\D', '', 'g') = :phoneDigits "
              + "ORDER BY created_at DESC LIMIT 1",
      nativeQuery = true)
  Optional<Cliente> findByTenantAndNormalizedPhoneDigitsRaw(
      @Param("tenantId") UUID tenantId, @Param("phoneDigits") String phoneDigits);

  /**
   * Espelha {@code findByTenantAndPhoneDigits} do original: tenta o telefone exato e depois as
   * variantes com/sem o DDI 55 (mesma logica de {@code buildPhoneVariants}).
   */
  default Optional<Cliente> findByTenantAndPhoneDigits(UUID tenantId, String phoneDigits) {
    if (tenantId == null || phoneDigits == null || phoneDigits.isBlank()) return Optional.empty();
    String normalizedDigits = phoneDigits.trim();
    Optional<Cliente> exactMatch = findByTenantAndNormalizedPhoneDigitsRaw(tenantId, normalizedDigits);
    if (exactMatch.isPresent()) {
      return exactMatch;
    }

    for (String variant : buildPhoneVariants(normalizedDigits)) {
      if (variant.equals(normalizedDigits)) continue;
      Optional<Cliente> variantMatch = findByTenantAndNormalizedPhoneDigitsRaw(tenantId, variant);
      if (variantMatch.isPresent()) {
        return variantMatch;
      }
    }
    return Optional.empty();
  }

  /**
   * Espelha o primeiro ramo de busca de {@code WhatsAppWebhookResource.processOptOut}: telefone
   * exatamente igual ao valor armazenado (sem normalizacao).
   */
  Optional<Cliente> findFirstByTenantIdAndPhone(UUID tenantId, String phone);

  /**
   * Espelha o ramo de fallback de {@code processOptOut}:
   * {@code replace(phone, '+', '') like '%' || :phoneNorm || '%'}.
   */
  @Query(
      value =
          "SELECT * FROM clients WHERE tenant_id = :tenantId "
              + "AND replace(COALESCE(phone, ''), '+', '') LIKE CONCAT('%', :phoneNorm, '%') LIMIT 1",
      nativeQuery = true)
  Optional<Cliente> findFirstByTenantIdAndPhoneContainingNormalizedRaw(
      @Param("tenantId") UUID tenantId, @Param("phoneNorm") String phoneNorm);

  private static List<String> buildPhoneVariants(String phoneDigits) {
    if (phoneDigits == null || phoneDigits.isBlank()) return List.of();
    if (phoneDigits.startsWith("55") && phoneDigits.length() > 11) {
      return List.of(phoneDigits, phoneDigits.substring(2));
    }
    if (phoneDigits.length() == 10 || phoneDigits.length() == 11) {
      return List.of(phoneDigits, "55" + phoneDigits);
    }
    return new ArrayList<>(List.of(phoneDigits));
  }
}
