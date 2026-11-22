package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ValePresente;

@Repository
public interface ValePresenteRepository extends JpaRepository<ValePresente, UUID> {

  List<ValePresente> findTop500ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<ValePresente> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<ValePresente> findByTenantIdAndCodigo(UUID tenantId, String codigo);

  boolean existsByTenantIdAndCodigo(UUID tenantId, String codigo);

  List<ValePresente> findByTenantIdAndComandaVendaId(UUID tenantId, UUID comandaVendaId);

  List<ValePresente> findByTenantIdAndCompradorClientIdOrderByCreatedAtDesc(UUID tenantId, UUID clientId);

  /**
   * Debita o vale numa unica instrucao, so se houver saldo e ele valer hoje. Dois pagamentos ao mesmo
   * tempo nao gastam o mesmo saldo.
   *
   * @return 1 se debitou; 0 se nao havia saldo, venceu ou nao esta ativo
   */
  @Modifying(flushAutomatically = true)
  @Query(value = "update vales_presente set saldo = saldo - :valor,"
      + " status = case when saldo - :valor = 0 then 'ESGOTADO' else status end, updated_at = now()"
      + " where id = :id and tenant_id = :tenantId and status = 'ATIVO' and saldo >= :valor and expira_em >= :hoje",
      nativeQuery = true)
  int debitarSeHouver(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
      @Param("valor") BigDecimal valor, @Param("hoje") LocalDate hoje);

  /** Devolve saldo (cancelamento/estorno). O vale esgotado volta a ATIVO; o vencido fica com a nova data. */
  @Modifying(flushAutomatically = true)
  @Query(value = "update vales_presente set saldo = saldo + :valor,"
      + " status = case when status in ('ESGOTADO', 'VENCIDO') then 'ATIVO' else status end,"
      + " expira_em = greatest(expira_em, :expiraNoMinimo), updated_at = now()"
      + " where id = :id and tenant_id = :tenantId and status <> 'CANCELADO'", nativeQuery = true)
  int devolver(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
      @Param("valor") BigDecimal valor, @Param("expiraNoMinimo") LocalDate expiraNoMinimo);

  /** Ajuste do dono: novo saldo (>= 0). */
  @Modifying(flushAutomatically = true)
  @Query(value = "update vales_presente set saldo = :saldo,"
      + " status = case when :saldo = 0 then 'ESGOTADO' when status = 'ESGOTADO' then 'ATIVO' else status end,"
      + " updated_at = now() where id = :id and tenant_id = :tenantId and status in ('ATIVO', 'ESGOTADO')",
      nativeQuery = true)
  int ajustarSaldo(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("saldo") BigDecimal saldo);

  @Modifying(flushAutomatically = true)
  @Query(value = "update vales_presente set status = :status, updated_at = now() where id = :id and tenant_id = :tenantId",
      nativeQuery = true)
  int mudarStatus(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("status") String status);

  @Modifying(flushAutomatically = true)
  @Query(value = "update vales_presente set expira_em = :data,"
      + " status = case when status = 'VENCIDO' and saldo > 0 then 'ATIVO' else status end, updated_at = now()"
      + " where id = :id and tenant_id = :tenantId and status in ('ATIVO', 'VENCIDO')", nativeQuery = true)
  int prorrogar(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("data") LocalDate data);

  @Query(value = "select saldo from vales_presente where id = :id", nativeQuery = true)
  BigDecimal saldoAtual(@Param("id") UUID id);

  @Query(value = "select status from vales_presente where id = :id", nativeQuery = true)
  String statusAtual(@Param("id") UUID id);

  @Query(value = "select * from vales_presente where status = 'ATIVO' and expira_em < :hoje", nativeQuery = true)
  List<ValePresente> vencidos(@Param("hoje") LocalDate hoje);

  @Query(value = "select coalesce(sum(saldo), 0) from vales_presente where tenant_id = :tenantId and status = 'ATIVO'",
      nativeQuery = true)
  BigDecimal saldoEmAberto(@Param("tenantId") UUID tenantId);
}
