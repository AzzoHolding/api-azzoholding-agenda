package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.RecurringTransaction;

/** Espelha {@code modules/finance/domain/repository/RecurringTransactionRepository.java}. */
@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

  List<RecurringTransaction> findByTenantIdAndActiveTrueOrderByCreatedAtDesc(UUID tenantId);

  /** Todos os templates ativos de todos os tenants (usado pelo scheduler). */
  List<RecurringTransaction> findByActiveTrue();

  Optional<RecurringTransaction> findByIdAndTenantId(UUID id, UUID tenantId);
}
