package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaItem;

/** Espelha {@code modules/pos/domain/repository/ComandaItemRepository.java}. */
@Repository
public interface ComandaItemRepository extends JpaRepository<ComandaItem, UUID> {

  List<ComandaItem> findByComandaIdOrderByCreatedAt(UUID comandaId);

  Optional<ComandaItem> findByIdAndComandaId(UUID id, UUID comandaId);
}
