package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;

/**
 * Espelha a parte "derivavel" de {@code modules/finance/domain/repository/TransacaoRepository.java}.
 * As consultas com filtro dinamico (findFiltered/summarize*) vivem em
 * {@link TransacaoQueryRepository}, porque Spring Data nao expressa where-clause montada em runtime
 * numa interface derivada.
 */
@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, UUID> {

  Optional<Transacao> findByIdAndTenantId(UUID id, UUID tenantId);

  @Query("select t from Transacao t where t.id = :id and t.tenantId = :tenantId and t.deletedAt is null")
  Optional<Transacao> findAtivaByIdAndTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  @Query("select t from Transacao t where t.tenantId = :tenantId and t.comandaId = :comandaId and t.deletedAt is null")
  List<Transacao> listarAtivasPorComanda(@Param("tenantId") UUID tenantId, @Param("comandaId") UUID comandaId);

  @Query("""
      select count(t) > 0 from Transacao t
      where t.tenantId = :tenantId
        and t.deletedAt is null
        and t.date >= :from
        and t.date <= :to
      """)
  boolean existsInPeriod(
      @Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);

  @Query("""
      select count(t) > 0 from Transacao t
      where t.tenantId = :tenantId
        and t.appointmentId = :appointmentId
        and t.type = :type
      """)
  boolean existsByTenantAndAppointmentAndType(
      @Param("tenantId") UUID tenantId,
      @Param("appointmentId") UUID appointmentId,
      @Param("type") TipoTransacao type);

  @Query("""
      select count(t) > 0 from Transacao t
      where t.tenantId = :tenantId
        and t.appointmentId = :appointmentId
        and t.type = :type
        and t.categoryRef.name = :categoryName
      """)
  boolean existsByTenantAndAppointmentAndTypeAndCategoryName(
      @Param("tenantId") UUID tenantId,
      @Param("appointmentId") UUID appointmentId,
      @Param("type") TipoTransacao type,
      @Param("categoryName") String categoryName);

  /**
   * Estorno de receita/comissao de agendamento — o original usa
   * {@code delete("... and categoryRef.name = ?4")}, remocao fisica (nao soft delete).
   */
  @Modifying
  @Query("""
      delete from Transacao t
      where t.tenantId = :tenantId
        and t.appointmentId = :appointmentId
        and t.type = :type
        and t.categoryId in (
          select c.id from TransactionCategory c
          where c.tenantId = :tenantId and c.name = :categoryName
        )
      """)
  int deleteByTenantAndAppointmentAndTypeAndCategoryName(
      @Param("tenantId") UUID tenantId,
      @Param("appointmentId") UUID appointmentId,
      @Param("type") TipoTransacao type,
      @Param("categoryName") String categoryName);

  @Query("""
      select count(t) > 0 from Transacao t
      where t.tenantId = :tenantId
        and t.recurringId = :recurringId
        and t.deletedAt is null
        and t.date >= :from
        and t.date <= :to
      """)
  boolean existsByRecurringInPeriod(
      @Param("tenantId") UUID tenantId,
      @Param("recurringId") UUID recurringId,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
