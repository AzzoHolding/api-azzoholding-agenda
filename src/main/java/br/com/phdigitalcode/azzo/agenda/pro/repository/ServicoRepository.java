package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;

/** Espelha {@code modules/services/domain/repository/ServicoRepository.java}. */
@Repository
public interface ServicoRepository extends JpaRepository<Servico, UUID> {

  List<Servico> findByTenantId(UUID tenantId);

  Optional<Servico> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Espelha o {@code list("tenantId = ?1 and id in ?2")} do resource de agendamentos. */
  List<Servico> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

  /** Espelha {@code Servico.count("tenantId = ?1 AND isActive = true")} usado por {@code ServicoOnboarding}. */
  long countByTenantIdAndIsActiveTrue(UUID tenantId);
}
