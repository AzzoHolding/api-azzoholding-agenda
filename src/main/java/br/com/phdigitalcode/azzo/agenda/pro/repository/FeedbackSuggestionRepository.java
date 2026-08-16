package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FeedbackSuggestion;

/** Espelha {@code modules/suggestions/domain/repository/FeedbackSuggestionRepository.java}. */
@Repository
public interface FeedbackSuggestionRepository extends JpaRepository<FeedbackSuggestion, UUID> {

  /**
   * Espelha {@code find("tenantId = ?1 order by createdAt desc", tenantId).page(0, limit).list()}
   * de {@code SuggestionService.listByTenant}.
   */
  List<FeedbackSuggestion> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Limit limit);
}
