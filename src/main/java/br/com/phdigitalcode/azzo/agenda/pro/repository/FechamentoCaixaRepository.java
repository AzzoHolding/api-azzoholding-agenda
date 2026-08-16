package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FechamentoCaixa;

/** Espelha {@code modules/finance/domain/repository/FechamentoCaixaRepository.java}. */
@Repository
public interface FechamentoCaixaRepository extends JpaRepository<FechamentoCaixa, UUID> {

  Optional<FechamentoCaixa> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<FechamentoCaixa> findByTenantIdAndBusinessDate(UUID tenantId, LocalDate businessDate);

  List<FechamentoCaixa> findByTenantIdOrderByBusinessDateDescCreatedAtDesc(UUID tenantId);
}
