package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoConfiguracao;

/** Espelha {@code modules/scheduling/domain/repository/AgendamentoConfiguracaoRepository.java}. */
@Repository
public interface AgendamentoConfiguracaoRepository
    extends JpaRepository<AgendamentoConfiguracao, UUID> {

  Optional<AgendamentoConfiguracao> findByTenantId(UUID tenantId);
}
