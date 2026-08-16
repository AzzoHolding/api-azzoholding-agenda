package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantSpecialClosureDate;

/**
 * Espelha {@code modules/settings/domain/repository/TenantSpecialClosureDateRepository.java}.
 *
 * <p>O {@code listByTenantIdFiltered} do original monta HQL dinamicamente conforme quais dos tres
 * filtros opcionais vieram preenchidos. Aqui isso vira
 * {@link br.com.phdigitalcode.azzo.agenda.pro.specification.TenantSpecialClosureDateSpecifications}
 * + {@link JpaSpecificationExecutor}, em vez de JPQL com {@code :param is null} — este ultimo
 * quebra no PostgreSQL com "could not determine data type of parameter" quando o bind e nulo.
 *
 * <p>Os dois {@code exists*} tem duas formas cada, exatamente como no original: com
 * {@code professionalId} informado a consulta aceita <b>tambem</b> os fechamentos do salao inteiro
 * ({@code professional_id IS NULL}); sem ele, apenas os do salao.
 */
@Repository
public interface TenantSpecialClosureDateRepository
    extends JpaRepository<TenantSpecialClosureDate, UUID>,
        JpaSpecificationExecutor<TenantSpecialClosureDate> {

  List<TenantSpecialClosureDate> findByTenantIdOrderByClosureDateAsc(UUID tenantId);

  Optional<TenantSpecialClosureDate> findFirstByTenantIdAndClosureDate(
      UUID tenantId, LocalDate closureDate);

  Optional<TenantSpecialClosureDate> findByIdAndTenantId(UUID id, UUID tenantId);

  boolean existsByTenantIdAndClosureDate(UUID tenantId, LocalDate closureDate);

  boolean existsByTenantIdAndClosureDateAndAllDayTrueAndProfessionalIdIsNull(
      UUID tenantId, LocalDate closureDate);

  @Query(
      """
      select count(e) > 0 from TenantSpecialClosureDate e
       where e.tenantId = :tenantId
         and e.closureDate = :date
         and e.allDay = true
         and (e.professionalId = :professionalId or e.professionalId is null)
      """)
  boolean existsAllDayClosureForProfessional(
      @Param("tenantId") UUID tenantId,
      @Param("date") LocalDate date,
      @Param("professionalId") UUID professionalId);

  /** Conflito de intervalos: {@code closure.start < end AND closure.end > start}. */
  @Query(
      """
      select count(e) > 0 from TenantSpecialClosureDate e
       where e.tenantId = :tenantId
         and e.closureDate = :date
         and e.allDay = false
         and e.startTime < :end
         and e.endTime > :start
         and e.professionalId is null
      """)
  boolean existsPartialClosureForSalon(
      @Param("tenantId") UUID tenantId,
      @Param("date") LocalDate date,
      @Param("start") LocalTime start,
      @Param("end") LocalTime end);

  @Query(
      """
      select count(e) > 0 from TenantSpecialClosureDate e
       where e.tenantId = :tenantId
         and e.closureDate = :date
         and e.allDay = false
         and e.startTime < :end
         and e.endTime > :start
         and (e.professionalId = :professionalId or e.professionalId is null)
      """)
  boolean existsPartialClosureForProfessional(
      @Param("tenantId") UUID tenantId,
      @Param("date") LocalDate date,
      @Param("start") LocalTime start,
      @Param("end") LocalTime end,
      @Param("professionalId") UUID professionalId);

  @Query(
      """
      select distinct e.closureDate from TenantSpecialClosureDate e
       where e.tenantId = :tenantId
         and e.closureDate >= :from
         and e.closureDate <= :to
         and e.allDay = true
         and e.professionalId is null
       order by e.closureDate asc
      """)
  List<LocalDate> listDistinctDatesInRange(
      @Param("tenantId") UUID tenantId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  /** Forma unificada dos dois {@code existsAllDayClosure} do original. */
  default boolean existsAllDayClosure(UUID tenantId, LocalDate date, UUID professionalId) {
    if (professionalId != null) {
      return existsAllDayClosureForProfessional(tenantId, date, professionalId);
    }
    return existsByTenantIdAndClosureDateAndAllDayTrueAndProfessionalIdIsNull(tenantId, date);
  }

  /** Forma unificada dos dois {@code existsPartialClosure} do original. */
  default boolean existsPartialClosure(
      UUID tenantId, UUID professionalId, LocalDate date, LocalTime start, LocalTime end) {
    if (professionalId != null) {
      return existsPartialClosureForProfessional(tenantId, date, start, end, professionalId);
    }
    return existsPartialClosureForSalon(tenantId, date, start, end);
  }

  /**
   * Espelha {@code tenantSpecialClosureDateRepository.delete("closureDate < ?1", limite)} de
   * {@code LgpdRetentionService.purgarFechamentosAntigos} do original — purga de fechamentos
   * antigos por retenção LGPD, varredura global (sem filtro de tenant), igual ao Panache original.
   */
  @Modifying
  @Transactional
  long deleteByClosureDateBefore(LocalDate closureDate);
}
