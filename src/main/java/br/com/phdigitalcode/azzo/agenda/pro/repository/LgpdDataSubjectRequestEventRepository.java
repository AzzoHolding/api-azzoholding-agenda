package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequestEvent;

/** Espelha {@code modules/lgpd/domain/repository/LgpdDataSubjectRequestEventRepository.java}. */
@Repository
public interface LgpdDataSubjectRequestEventRepository extends JpaRepository<LgpdDataSubjectRequestEvent, UUID> {

  List<LgpdDataSubjectRequestEvent> findByRequestIdOrderByCreatedAtDescIdDesc(UUID requestId);

  /** Espelha {@code eventRepository.listByRequestId(requestId)}. */
  default List<LgpdDataSubjectRequestEvent> listByRequestId(UUID requestId) {
    return findByRequestIdOrderByCreatedAtDescIdDesc(requestId);
  }
}
