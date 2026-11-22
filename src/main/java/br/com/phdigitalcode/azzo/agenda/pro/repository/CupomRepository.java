package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cupom;

@Repository
public interface CupomRepository extends JpaRepository<Cupom, UUID> {

  /** Os cupons avulsos (fora de lote): os codigos do gerador aparecem so dentro do lote. */
  List<Cupom> findByTenantIdAndLoteIdIsNullOrderByCreatedAtDesc(UUID tenantId);

  List<Cupom> findByTenantIdAndLoteIdOrderByCodigoAsc(UUID tenantId, UUID loteId);

  @Query(value = "select count(*) from cupons where tenant_id = :tenantId and upper(codigo) = upper(:codigo)", nativeQuery = true)
  long contarCodigo(@Param("tenantId") UUID tenantId, @Param("codigo") String codigo);

  /** Desativar o lote: os codigos ainda nao usados param de valer. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(value = "update cupons set ativo = false, updated_at = now() where lote_id = :loteId and tenant_id = :tenantId and usos = 0",
      nativeQuery = true)
  int desativarNaoUsadosDoLote(@Param("loteId") UUID loteId, @Param("tenantId") UUID tenantId);

  Optional<Cupom> findByIdAndTenantId(UUID id, UUID tenantId);

  @Query(value = "select * from cupons where tenant_id = :tenantId and upper(codigo) = upper(:codigo)", nativeQuery = true)
  Optional<Cupom> buscarPorCodigo(@Param("tenantId") UUID tenantId, @Param("codigo") String codigo);

  /** Trava a linha do cupom: o limite por cliente e contado sem corrida. */
  @Query(value = "select * from cupons where id = :id and tenant_id = :tenantId for update", nativeQuery = true)
  Optional<Cupom> travar(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  /**
   * Reserva um uso numa unica instrucao, so se o limite total ainda deixa. Duas comandas ao mesmo tempo
   * nao passam do limite.
   *
   * @return 1 se reservou; 0 se o cupom esgotou
   */
  @Modifying(flushAutomatically = true)
  @Query(value = "update cupons set usos = usos + 1, updated_at = now()"
      + " where id = :id and tenant_id = :tenantId and (limite_total is null or usos < limite_total)", nativeQuery = true)
  int reservarUso(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  @Modifying(flushAutomatically = true)
  @Query(value = "update cupons set usos = greatest(usos - 1, 0), updated_at = now()"
      + " where id = :id and tenant_id = :tenantId", nativeQuery = true)
  int liberarUso(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  @Query(value = "select usos from cupons where id = :id", nativeQuery = true)
  int usosAtuais(@Param("id") UUID id);

  // ─── So alguns servicos (V182) ────────────────────────────────────────────

  @Query(value = "select service_id from cupom_servicos where cupom_id = :cupomId", nativeQuery = true)
  List<UUID> servicosDoCupom(@Param("cupomId") UUID cupomId);

  @Query(value = "select service_id from lote_de_cupom_servicos where lote_id = :loteId", nativeQuery = true)
  List<UUID> servicosDoLote(@Param("loteId") UUID loteId);

  @Modifying(flushAutomatically = true)
  @Query(value = "delete from cupom_servicos where cupom_id = :cupomId", nativeQuery = true)
  int limparServicosDoCupom(@Param("cupomId") UUID cupomId);

  @Modifying(flushAutomatically = true)
  @Query(value = "insert into cupom_servicos (cupom_id, service_id) values (:cupomId, :serviceId) on conflict do nothing",
      nativeQuery = true)
  int incluirServicoNoCupom(@Param("cupomId") UUID cupomId, @Param("serviceId") UUID serviceId);

  @Modifying(flushAutomatically = true)
  @Query(value = "insert into lote_de_cupom_servicos (lote_id, service_id) values (:loteId, :serviceId) on conflict do nothing",
      nativeQuery = true)
  int incluirServicoNoLote(@Param("loteId") UUID loteId, @Param("serviceId") UUID serviceId);
}
