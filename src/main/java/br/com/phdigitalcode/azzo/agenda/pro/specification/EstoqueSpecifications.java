package br.com.phdigitalcode.azzo.agenda.pro.specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueInventario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ItemEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusInventarioEstoque;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;
import jakarta.persistence.criteria.Predicate;

/**
 * Equivalente ao HQL montado dinamicamente em {@code ServicoEstoque.listarItens} e
 * {@code ServicoEstoque.listarMovimentacoes} do original (Panache {@code StringBuilder} +
 * {@code Parameters}). Armadilha 8: como JPQL com {@code :param is null} isso quebraria no
 * PostgreSQL — cada filtro so entra na consulta quando realmente foi informado.
 *
 * <p>O cursor e o mesmo dos dois metodos: keyset descendente por
 * {@code (createdAt, id)}, aplicado <b>alem</b> do {@code page}/{@code limit} — o original permite
 * os dois juntos e isso foi preservado.
 */
public final class EstoqueSpecifications {

  private EstoqueSpecifications() {}

  public static Specification<ItemEstoque> itens(
      UUID tenantId,
      String search,
      Boolean ativo,
      Boolean abaixoMinimo,
      Instant cursorCreatedAt,
      UUID cursorId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));

      if (search != null && !search.isBlank()) {
        String termo = "%" + search.trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("nome")), termo),
                cb.like(cb.lower(cb.coalesce(root.get("sku"), "")), termo)));
      }
      if (ativo != null) {
        predicates.add(cb.equal(root.get("ativo"), ativo));
      }
      // Só filtra quando TRUE, como no original: `abaixoMinimo=false` nao inverte o filtro.
      if (Boolean.TRUE.equals(abaixoMinimo)) {
        predicates.add(
            cb.lessThanOrEqualTo(root.get("saldoAtual"), root.<java.math.BigDecimal>get("estoqueMinimo")));
      }
      adicionarCursor(predicates, root, cb, cursorCreatedAt, cursorId);
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  public static Specification<MovimentacaoEstoque> movimentacoes(
      UUID tenantId,
      UUID itemId,
      TipoMovimentacaoEstoque tipo,
      Instant cursorCreatedAt,
      UUID cursorId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      if (itemId != null) {
        predicates.add(cb.equal(root.get("itemEstoqueId"), itemId));
      }
      if (tipo != null) {
        predicates.add(cb.equal(root.get("tipo"), tipo));
      }
      adicionarCursor(predicates, root, cb, cursorCreatedAt, cursorId);
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Porte de {@code ServicoEstoque.listarInventarios}. Duas diferencas em relacao as duas listagens
   * acima, ambas do original: <b>nao tem cursor</b> (a paginacao e por pagina/total) e um
   * {@code status} desconhecido e <b>silenciosamente ignorado</b> — o {@code valueOf} do original
   * esta dentro de um {@code try/catch} vazio, ao contrario do {@code tipo} de
   * {@code listarMovimentacoes}, que deixa a excecao subir. A conversao acontece no service; aqui o
   * filtro so entra quando {@code status != null}.
   */
  public static Specification<EstoqueInventario> inventarios(
      UUID tenantId, String search, StatusInventarioEstoque status) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      if (search != null && !search.isBlank()) {
        predicates.add(
            cb.like(cb.lower(root.get("nome")), "%" + search.trim().toLowerCase() + "%"));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Filtro comum das listagens de fornecedor, pedido de compra e transferencia: no original as tres
   * montam exatamente o mesmo HQL ({@code tenantId = :tenantId} + cursor keyset + ordenacao), sem
   * nenhum filtro adicional.
   */
  public static <T> Specification<T> porTenantComCursor(
      UUID tenantId, Instant cursorCreatedAt, UUID cursorId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      adicionarCursor(predicates, root, cb, cursorCreatedAt, cursorId);
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static void adicionarCursor(
      List<Predicate> predicates,
      jakarta.persistence.criteria.Root<?> root,
      jakarta.persistence.criteria.CriteriaBuilder cb,
      Instant cursorCreatedAt,
      UUID cursorId) {
    if (cursorCreatedAt == null || cursorId == null) return;
    predicates.add(
        cb.or(
            cb.lessThan(root.get("createdAt"), cursorCreatedAt),
            cb.and(
                cb.equal(root.get("createdAt"), cursorCreatedAt),
                cb.lessThan(root.get("id"), cursorId))));
  }
}
