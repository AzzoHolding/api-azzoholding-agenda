package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;

/** Espelha {@code modules/customers/domain/repository/ClienteRepository.java} (parte CRUD). */
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

  /** Clientes ainda nao anonimizados do salao, em lotes (rotina de fim de contrato). */
  @Query("select c.id from Cliente c where c.tenantId = :tenantId and c.anonymizedAt is null order by c.id")
  List<UUID> idsNaoAnonimizados(@Param("tenantId") UUID tenantId, Pageable pagina);

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

  // ---- pontos de fidelidade (V175): o saldo so muda numa instrucao, nunca com ler-conferir-gravar ----
  // A entidade Cliente nao grava loyalty_points (updatable=false): so estas instrucoes mexem nele.

  /** Desconta so se houver saldo. 1 = descontou; 0 = sem pontos suficientes (ou cliente de outro salao). */
  @Modifying(flushAutomatically = true)
  @Query(value = "update clients set loyalty_points = loyalty_points - :pontos"
      + " where id = :id and tenant_id = :tenantId and loyalty_points >= :pontos", nativeQuery = true)
  int debitarPontosSeHouver(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("pontos") int pontos);

  /** Soma (ou subtrai, com valor negativo) ao saldo. Pode deixar negativo: so o estorno aceito pelo dono usa. */
  @Modifying(flushAutomatically = true)
  @Query(value = "update clients set loyalty_points = loyalty_points + :pontos"
      + " where id = :id and tenant_id = :tenantId", nativeQuery = true)
  int somarPontos(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("pontos") int pontos);

  /** Tira ate {@code pontos}, sem passar de zero. Devolve quantos de fato saíram so pela diferenca de saldo. */
  @Modifying(flushAutomatically = true)
  @Query(value = "update clients set loyalty_points = greatest(0, loyalty_points - :pontos)"
      + " where id = :id and tenant_id = :tenantId", nativeQuery = true)
  int tirarPontosAteZero(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("pontos") int pontos);

  /** Trava o saldo da cliente ate o fim da transacao (expiracao x resgate ao mesmo tempo). */
  @Query(value = "select loyalty_points from clients where id = :id and tenant_id = :tenantId for update", nativeQuery = true)
  Integer travarSaldo(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  /** O saldo de verdade, direto do banco (a entidade carregada pode estar desatualizada). */
  @Query(value = "select loyalty_points from clients where id = :id and tenant_id = :tenantId", nativeQuery = true)
  Integer saldoDePontos(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
