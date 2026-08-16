package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsVersion;

/** Espelha {@code domain/repository/TermsVersionRepository.java}. */
@Repository
public interface TermsVersionRepository extends JpaRepository<TermsVersion, UUID> {

  @Query("select t from TermsVersion t where t.documentType = :documentType and t.version = :version")
  Optional<TermsVersion> findExactByDocumentTypeAndVersion(
      @Param("documentType") String documentType, @Param("version") String version);

  /** Espelha {@code termsVersionRepository.findByDocumentTypeAndVersion(documentType, version)}. */
  default Optional<TermsVersion> findByDocumentTypeAndVersion(String documentType, String version) {
    if (documentType == null || version == null) return Optional.empty();
    return findExactByDocumentTypeAndVersion(documentType.toUpperCase(), version);
  }

  List<TermsVersion> findByDocumentTypeOrderByCreatedAtDescIdDesc(String documentType);

  /** Espelha {@code termsVersionRepository.listByDocumentTypeNewestFirst(documentType)}. */
  default List<TermsVersion> listByDocumentTypeNewestFirst(String documentType) {
    if (documentType == null || documentType.isBlank()) return List.of();
    return findByDocumentTypeOrderByCreatedAtDescIdDesc(documentType.trim().toUpperCase());
  }
}
