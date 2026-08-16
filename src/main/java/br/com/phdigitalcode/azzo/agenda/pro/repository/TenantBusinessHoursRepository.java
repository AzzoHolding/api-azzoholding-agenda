package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantBusinessHours;

/**
 * Espelha os metodos estaticos Panache de
 * {@code modules/settings/domain/entity/TenantBusinessHoursEntity.java}
 * ({@code findByTenant} e {@code findByTenantAndDay}).
 *
 * <p>A ordenacao de {@code findByTenant} e por {@code dayOfWeek} <b>alfabetica</b> no original
 * ({@code order by dayOfWeek} sobre a coluna {@code VARCHAR}), nao pela ordem natural da semana —
 * ou seja, FRIDAY, MONDAY, SATURDAY, SUNDAY, THURSDAY, TUESDAY, WEDNESDAY. Preservado como esta
 * porque e a ordem que o frontend ja recebe em {@code GET /api/v1/settings/business-hours/table}.
 */
@Repository
public interface TenantBusinessHoursRepository extends JpaRepository<TenantBusinessHours, UUID> {

  List<TenantBusinessHours> findByTenantIdOrderByDayOfWeekAsc(UUID tenantId);

  Optional<TenantBusinessHours> findByTenantIdAndDayOfWeek(UUID tenantId, String dayOfWeek);

  /** Espelha {@code TenantBusinessHoursEntity.count("tenantId = ?1 AND enabled = true")} usado por {@code ServicoOnboarding}. */
  long countByTenantIdAndEnabledTrue(UUID tenantId);
}
