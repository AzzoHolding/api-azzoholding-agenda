package br.com.phdigitalcode.azzo.agenda.pro.specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantSpecialClosureDate;
import jakarta.persistence.criteria.Predicate;

/**
 * Equivalente ao HQL montado dinamicamente em
 * {@code TenantSpecialClosureDateRepository.listByTenantIdFiltered} do original: {@code tenantId}
 * sempre entra, e {@code from}/{@code to}/{@code professionalId} so entram quando nao nulos.
 *
 * <p>Atencao a assimetria preservada: quando {@code professionalId} e informado, o original filtra
 * <b>apenas</b> os fechamentos daquele profissional — diferente dos metodos {@code exists*}, que
 * nesse caso tambem consideram os fechamentos do salao inteiro. A listagem e uma tela de CRUD, os
 * {@code exists*} sao regra de bloqueio de agenda.
 */
public final class TenantSpecialClosureDateSpecifications {

  private TenantSpecialClosureDateSpecifications() {}

  public static Specification<TenantSpecialClosureDate> filtro(
      UUID tenantId, LocalDate from, LocalDate to, UUID professionalId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("closureDate"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("closureDate"), to));
      }
      if (professionalId != null) {
        predicates.add(cb.equal(root.get("professionalId"), professionalId));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
