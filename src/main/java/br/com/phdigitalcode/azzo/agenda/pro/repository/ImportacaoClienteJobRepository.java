package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoClienteJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoCliente;

/**
 * Espelha {@code modules/customers/domain/repository/ImportacaoClienteJobRepository.java} mais a
 * consulta de fila que {@code ProcessadorImportacaoClientesService} monta direto no Panache
 * ({@code find("status in (?1, ?2, ?3) ...")}).
 *
 * <p><b>O que nao foi portado, de proposito:</b> o repositorio original tambem declara
 * {@code buscarConcluidosExpirados}, mas nenhum chamador em todo o projeto Quarkus o usa (nao ha
 * limpeza automatica de jobs de clientes, ao contrario de estoque). Portar seria trazer codigo
 * morto.
 */
@Repository
public interface ImportacaoClienteJobRepository extends JpaRepository<ImportacaoClienteJob, UUID> {

  Optional<ImportacaoClienteJob> findByIdAndTenantId(UUID id, UUID tenantId);

  List<ImportacaoClienteJob> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  /**
   * Fila de processamento: jobs em qualquer estagio nao terminal, dos mais antigos para os mais
   * novos. Sem recorte por tenant — a varredura e global, como no original.
   */
  @Query(
      """
      select j.id from ImportacaoClienteJob j
       where j.status in (:statuses)
       order by j.createdAt asc
      """)
  List<UUID> listarIdsPendentes(
      @Param("statuses") List<StatusImportacaoCliente> statuses, Pageable pageable);
}
