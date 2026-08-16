package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsLifecycleEvent;

/** Espelha {@code domain/repository/TermsLifecycleEventRepository.java}. */
@Repository
public interface TermsLifecycleEventRepository extends JpaRepository<TermsLifecycleEvent, UUID> {

  List<TermsLifecycleEvent> findByTermsVersionIdOrderByCreatedAtDescIdDesc(UUID termsVersionId, Limit limit);

  /** Espelha {@code termsLifecycleEventRepository.findLastByTermsVersionId(termsVersionId)}. */
  default Optional<TermsLifecycleEvent> findLastByTermsVersionId(UUID termsVersionId) {
    if (termsVersionId == null) return Optional.empty();
    List<TermsLifecycleEvent> result =
        findByTermsVersionIdOrderByCreatedAtDescIdDesc(termsVersionId, Limit.of(1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }
}
