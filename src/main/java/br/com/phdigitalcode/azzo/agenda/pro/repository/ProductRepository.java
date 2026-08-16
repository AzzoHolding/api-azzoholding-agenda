package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;

/** Espelha {@code domain/repository/ProductRepository.java}. */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

  @Query("select p from Product p where p.isTrial = true and p.active = true order by p.createdAt desc")
  List<Product> findActiveTrials();

  default Optional<Product> findLatestActiveTrial() {
    return findActiveTrials().stream().findFirst();
  }

  @Query("select p from Product p where p.id = :id and p.active = true and p.isTrial = false")
  Optional<Product> findActivePaidById(@Param("id") UUID id);

  @Query("""
      select p from Product p
      where p.active = true and p.isTrial = false
        and (lower(p.name) = :token or lower(p.highlight) = :token)
      """)
  List<Product> findActivePaidByExactToken(@Param("token") String token);

  @Query("""
      select p from Product p
      where p.active = true and p.isTrial = false and lower(p.name) like :pattern
      order by p.priority desc, p.createdAt desc
      """)
  List<Product> findActivePaidByNameLike(@Param("pattern") String pattern);

  /**
   * Mesma cascata do original: id exato -> nome/highlight exato -> {@code like} pelo nome, sempre
   * restrito a plano ativo e nao-trial.
   */
  default Optional<Product> findActivePaidByIdentifier(String identifier) {
    if (identifier == null || identifier.isBlank()) return Optional.empty();
    String normalized = identifier.trim();

    try {
      Optional<Product> byId = findActivePaidById(UUID.fromString(normalized));
      if (byId.isPresent()) return byId;
    } catch (IllegalArgumentException ignored) {
      // Fallback para compatibilidade com payloads legados que enviam nome/codigo.
    }

    String token = normalized.toLowerCase();
    Optional<Product> exactMatch = findActivePaidByExactToken(token).stream().findFirst();
    if (exactMatch.isPresent()) return exactMatch;

    // Fallback para payload simples (ex.: "pro", "basic").
    return findActivePaidByNameLike("%" + token + "%").stream().findFirst();
  }

  @Query("""
      select p from Product p
      where p.active = true and p.isTrial = false and p.exclusivoVendaInterna = false
      order by p.priority desc, p.name asc
      """)
  List<Product> listarContrataveisPublicamente();

  @Query("""
      select p from Product p
      where p.active = true and p.isTrial = false and p.exclusivoVendaInterna = true
      order by p.priority desc, p.name asc
      """)
  List<Product> listarExclusivosVendaInterna();

  @Query("""
      select p from Product p
      where p.active = true and p.isTrial = false
      order by p.priority desc, p.name asc
      """)
  List<Product> listarTodosAtivosNaoTrial();

  /**
   * Mesma restricao de {@link #listarTodosAtivosNaoTrial}, <b>outra ordenacao</b>: o fallback do
   * {@code BillingAdminService.resolveProduct} desempata por {@code createdAt desc}, nao por nome.
   * Duas consultas de proposito — o original tem as duas ordens.
   */
  @Query("""
      select p from Product p
      where p.active = true and p.isTrial = false
      order by p.priority desc, p.createdAt desc
      """)
  List<Product> listarAtivosNaoTrialMaisRecentesPrimeiro();

  /**
   * Espelha {@code productRepository.find("order by isTrial desc, priority desc, createdAt desc").list()}
   * de {@code SystemAdminService.listPlans} — lista <b>todos</b> os planos (ativos e inativos, trial
   * e nao-trial), ao contrario das outras queries acima que ja filtram {@code active}/{@code isTrial}.
   */
  @Query("select p from Product p order by p.isTrial desc, p.priority desc, p.createdAt desc")
  List<Product> listarTodosOrdenadosParaAdmin();

  /**
   * Espelha {@code productRepository.find("isTrial = true and active = true and id <> ?1", currentProductId)}
   * de {@code SystemAdminService.enforceSingleActiveTrial}.
   */
  @Query("select p from Product p where p.isTrial = true and p.active = true and p.id <> :excludeId")
  List<Product> findOutrosTrialsAtivos(@Param("excludeId") UUID excludeId);
}
