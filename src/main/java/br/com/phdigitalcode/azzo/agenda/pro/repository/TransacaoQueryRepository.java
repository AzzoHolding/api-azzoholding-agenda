package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Parte de filtro dinamico de {@code modules/finance/domain/repository/TransacaoRepository.java}
 * ({@code findFiltered}, {@code summarizeFiltered}, {@code summarizeNetByPaymentMethod}).
 *
 * <p>Separado da interface Spring Data {@link TransacaoRepository} porque a clausula
 * {@code where} e montada em runtime a partir dos filtros informados — o equivalente ao
 * {@code buildFilterQueryParts} do Panache original. A ordenacao
 * ({@code order by date desc, id desc}) e os filtros aplicados sao identicos ao original,
 * incluindo o {@code deletedAt is null} sempre presente (soft delete).
 */
@Repository
public class TransacaoQueryRepository {

  public record SummaryTotals(BigDecimal totalIncome, BigDecimal totalExpenses) {}

  @PersistenceContext
  private EntityManager entityManager;

  public long countFiltered(TransacaoFilter filter) {
    FilterQueryParts parts = buildFilterQueryParts(filter);
    TypedQuery<Long> query =
        entityManager.createQuery(
            "select count(t) from Transacao t where " + parts.whereClause(), Long.class);
    parts.params().forEach(query::setParameter);
    return query.getSingleResult();
  }

  /** Pagina de resultados; {@code page} 0-indexed. {@code size <= 0} devolve a lista completa. */
  public List<Transacao> listFiltered(TransacaoFilter filter, int page, int size) {
    FilterQueryParts parts = buildFilterQueryParts(filter);
    TypedQuery<Transacao> query =
        entityManager.createQuery(
            "select t from Transacao t where " + parts.whereClause() + " order by t.date desc, t.id desc",
            Transacao.class);
    parts.params().forEach(query::setParameter);
    if (size > 0) {
      query.setFirstResult(Math.max(page, 0) * size).setMaxResults(size);
    }
    return query.getResultList();
  }

  public SummaryTotals summarizeFiltered(TransacaoFilter filter) {
    FilterQueryParts parts = buildFilterQueryParts(filter);
    String jpql =
        """
        select
          coalesce(sum(case when t.type = :incomeType then t.amount else 0 end), 0),
          coalesce(sum(case when t.type = :expenseType then t.amount else 0 end), 0)
        from Transacao t
        where %s
        """
            .formatted(parts.whereClause());

    TypedQuery<Object[]> query =
        entityManager
            .createQuery(jpql, Object[].class)
            .setParameter("incomeType", TipoTransacao.INCOME)
            .setParameter("expenseType", TipoTransacao.EXPENSE);
    parts.params().forEach(query::setParameter);

    Object[] row = query.getSingleResult();
    return new SummaryTotals(toDecimal(row[0]), toDecimal(row[1]));
  }

  /**
   * Total liquido (receita - despesa) por meio de pagamento, em centavos. Todos os meios
   * aparecem no mapa, com zero quando nao houve movimento — igual ao original.
   */
  public Map<MetodoPagamento, Long> summarizeNetByPaymentMethod(UUID tenantId, Instant from, Instant to) {
    String jpql =
        """
        select
          t.paymentMethod,
          coalesce(sum(case
            when t.type = :incomeType then t.amount
            when t.type = :expenseType then -t.amount
            else 0
          end), 0)
        from Transacao t
        where t.tenantId = :tenantId
          and t.deletedAt is null
          and t.date >= :from
          and t.date <= :to
        group by t.paymentMethod
        """;

    List<Object[]> rows =
        entityManager
            .createQuery(jpql, Object[].class)
            .setParameter("tenantId", tenantId)
            .setParameter("from", from)
            .setParameter("to", to)
            .setParameter("incomeType", TipoTransacao.INCOME)
            .setParameter("expenseType", TipoTransacao.EXPENSE)
            .getResultList();

    Map<MetodoPagamento, Long> totals = new LinkedHashMap<>();
    for (MetodoPagamento method : MetodoPagamento.values()) {
      totals.put(method, 0L);
    }
    for (Object[] row : rows) {
      if (row == null || row.length < 2 || row[0] == null) continue;
      MetodoPagamento method =
          row[0] instanceof MetodoPagamento enumValue
              ? enumValue
              : MetodoPagamento.valueOf(String.valueOf(row[0]));
      totals.put(method, NumericUtil.toCents(toDecimal(row[1])));
    }
    return totals;
  }

  private BigDecimal toDecimal(Object value) {
    if (value == null) return NumericUtil.zero();
    if (value instanceof BigDecimal decimal) return NumericUtil.normalize(decimal);
    if (value instanceof Number number) return NumericUtil.normalize(BigDecimal.valueOf(number.doubleValue()));
    return NumericUtil.normalize(new BigDecimal(String.valueOf(value)));
  }

  private FilterQueryParts buildFilterQueryParts(TransacaoFilter filter) {
    StringBuilder sb = new StringBuilder("t.tenantId = :tenantId and t.deletedAt is null");
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", filter.tenantId());

    if (filter.from() != null) {
      sb.append(" and t.date >= :from");
      params.put("from", filter.from());
    }
    if (filter.to() != null) {
      sb.append(" and t.date <= :to");
      params.put("to", filter.to());
    }
    if (filter.type() != null) {
      sb.append(" and t.type = :type");
      params.put("type", filter.type());
    }
    if (filter.categoryId() != null) {
      sb.append(" and t.categoryId = :categoryId");
      params.put("categoryId", filter.categoryId());
    }
    if (filter.paymentMethod() != null) {
      sb.append(" and t.paymentMethod = :paymentMethod");
      params.put("paymentMethod", filter.paymentMethod());
    }
    if (filter.professionalId() != null) {
      sb.append(" and t.professionalId = :professionalId");
      params.put("professionalId", filter.professionalId());
    }
    if (filter.reconciled() != null) {
      sb.append(" and t.reconciled = :reconciled");
      params.put("reconciled", filter.reconciled());
    }

    return new FilterQueryParts(sb.toString(), params);
  }

  /** Conjunto de filtros do endpoint {@code GET /api/v1/finance/transactions}. */
  public record TransacaoFilter(
      UUID tenantId,
      Instant from,
      Instant to,
      TipoTransacao type,
      UUID categoryId,
      MetodoPagamento paymentMethod,
      UUID professionalId,
      Boolean reconciled) {}

  private record FilterQueryParts(String whereClause, Map<String, Object> params) {}
}
