package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoEstoqueJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoEstoque;

/**
 * Espelha {@code modules/inventory/domain/repository/ImportacaoEstoqueJobRepository.java} mais as
 * duas consultas que o original monta direto no {@code EntityManager} de
 * {@code ProcessadorImportacaoEstoqueService}.
 *
 * <p><b>O que nao foi portado, de proposito:</b> o repositorio original declara
 * {@code buscarPendentesParaProcessamento} e {@code buscarConcluidosExpirados}, mas <b>nenhum dos
 * dois tem chamador</b> em todo o projeto Quarkus — o processador nao os usa, monta as proprias
 * queries por id. Portar seria trazer codigo morto. As queries efetivamente usadas estao abaixo,
 * com a mesma clausula e a mesma ordenacao.
 */
@Repository
public interface ImportacaoEstoqueJobRepository extends JpaRepository<ImportacaoEstoqueJob, UUID> {

  Optional<ImportacaoEstoqueJob> findByIdAndTenantId(UUID id, UUID tenantId);

  List<ImportacaoEstoqueJob> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  /**
   * Fila de processamento: jobs em qualquer estagio nao terminal, dos mais antigos para os mais
   * novos. Sem recorte por tenant — a varredura e global, como no original.
   */
  @Query(
      """
      select j.id from ImportacaoEstoqueJob j
       where j.status in (:statuses)
       order by j.createdAt asc
      """)
  List<UUID> listarIdsPendentes(
      @Param("statuses") List<StatusImportacaoEstoque> statuses, Pageable pageable);

  /**
   * Jobs terminais alem do TTL: sucesso ({@code CONCLUIDO}/{@code CONCLUIDO_COM_ERROS}) e falha
   * ({@code FALHOU}/{@code CANCELADO}) tem limites distintos, como no original.
   */
  @Query(
      """
      select j.id from ImportacaoEstoqueJob j
       where (j.status in (:statusSucesso) and j.finishedAt <= :limiteSucesso)
          or (j.status in (:statusFalha) and j.finishedAt <= :limiteFalha)
       order by j.finishedAt asc
      """)
  List<UUID> listarIdsExpirados(
      @Param("statusSucesso") List<StatusImportacaoEstoque> statusSucesso,
      @Param("limiteSucesso") Instant limiteSucesso,
      @Param("statusFalha") List<StatusImportacaoEstoque> statusFalha,
      @Param("limiteFalha") Instant limiteFalha,
      Pageable pageable);
}
