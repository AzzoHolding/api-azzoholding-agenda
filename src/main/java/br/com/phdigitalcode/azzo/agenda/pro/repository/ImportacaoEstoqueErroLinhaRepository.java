package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueErroLinha;

/**
 * Espelha {@code modules/inventory/domain/repository/ImportacaoEstoqueErroLinhaRepository.java}
 * (que no original nao declara metodo proprio — o service monta HQL direto no Panache).
 */
@Repository
public interface ImportacaoEstoqueErroLinhaRepository
    extends JpaRepository<ImportacaoEstoqueErroLinha, UUID> {

  List<ImportacaoEstoqueErroLinha> findByJobIdAndTenantIdOrderByLinhaAsc(UUID jobId, UUID tenantId);

  /**
   * Porte de {@code delete("jobId = ?1 and tenantId = ?2", ...)} da limpeza de jobs expirados. Bulk
   * delete, como no original — nao carrega as linhas antes de apagar.
   */
  @Modifying
  @Query("delete from ImportacaoEstoqueErroLinha e where e.jobId = :jobId and e.tenantId = :tenantId")
  int deleteByJobIdAndTenantId(@Param("jobId") UUID jobId, @Param("tenantId") UUID tenantId);
}
