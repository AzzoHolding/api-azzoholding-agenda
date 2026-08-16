package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoClienteErroLinha;

/** Espelha {@code modules/customers/domain/repository/ImportacaoClienteErroLinhaRepository.java}. */
@Repository
public interface ImportacaoClienteErroLinhaRepository
    extends JpaRepository<ImportacaoClienteErroLinha, UUID> {

  List<ImportacaoClienteErroLinha> findByJobIdAndTenantIdOrderByLinhaAsc(UUID jobId, UUID tenantId);

  /**
   * Porte de {@code delete("jobId = ?1 and tenantId = ?2", ...)} que
   * {@code ProcessadorImportacaoClientesService.processarJob} chama antes de reprocessar um job
   * (limpa erros de uma tentativa anterior).
   */
  @Modifying
  @Query("delete from ImportacaoClienteErroLinha e where e.jobId = :jobId and e.tenantId = :tenantId")
  int deleteByJobIdAndTenantId(@Param("jobId") UUID jobId, @Param("tenantId") UUID tenantId);
}
