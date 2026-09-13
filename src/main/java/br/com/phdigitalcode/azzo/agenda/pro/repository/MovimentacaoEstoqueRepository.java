package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;

/**
 * Espelha {@code modules/inventory/domain/repository/MovimentacaoEstoqueRepository.java} mais as
 * duas contagens que o original faz inline (via {@code count(...)} do Panache) dentro de
 * {@code ServicoEstoque.consumirInsumosPorAgendamento} / {@code consumirInsumosPorItemComanda}.
 *
 * <p>Essas contagens sao a <b>guarda de idempotencia</b> do consumo de insumo: concluir o mesmo
 * agendamento (ou fechar o mesmo item de comanda) duas vezes nao pode baixar o estoque duas vezes.
 * Elas so sao seguras porque rodam <b>depois</b> da trava do item
 * ({@code ItemEstoqueRepository.travarPorIdETenant}).
 */
@Repository
public interface MovimentacaoEstoqueRepository
    extends JpaRepository<MovimentacaoEstoque, UUID>,
        JpaSpecificationExecutor<MovimentacaoEstoque> {

  List<MovimentacaoEstoque> findByTenantIdAndComandaItemId(UUID tenantId, UUID comandaItemId);

  long countByTenantIdAndAppointmentIdAndItemEstoqueId(
      UUID tenantId, UUID appointmentId, UUID itemEstoqueId);

  long countByTenantIdAndComandaItemIdAndItemEstoqueId(
      UUID tenantId, UUID comandaItemId, UUID itemEstoqueId);

  /**
   * Quanto cada item PERDEU no periodo, em quantidade: saidas manuais (quebra, vencimento, uso
   * interno) e ajustes para baixo.
   *
   * <p>Fica de fora o que nao e perda: o consumo dos atendimentos (origem {@code SERVICO}), a venda
   * em comanda (origem {@code VENDA}, e as vendas antigas, gravadas como {@code MANUAL} com o motivo
   * "Venda em comanda") e tudo que aumenta saldo. O original somava toda {@code SAIDA}, e o consumo
   * normal do salao aparecia no painel como prejuizo.
   *
   * <p>Agregado no banco: o original carregava todas as movimentacoes do tenant em memoria a cada
   * abertura do painel.
   */
  @Query(
      "select m.itemEstoqueId, sum(m.saldoAnterior - m.saldoPosterior) from MovimentacaoEstoque m "
          + "where m.tenantId = :tenantId and m.createdAt >= :inicio and m.createdAt < :fim "
          + "and m.saldoPosterior < m.saldoAnterior "
          + "and (m.tipo = br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque.AJUSTE "
          + "  or (m.tipo = br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque.SAIDA "
          + "      and m.origem = br.com.phdigitalcode.azzo.agenda.pro.entity.enums.OrigemMovimentacaoEstoque.MANUAL "
          + "      and m.motivo not like 'Venda em comanda%')) "
          + "group by m.itemEstoqueId")
  List<Object[]> somarPerdasPorItem(
      @Param("tenantId") UUID tenantId,
      @Param("inicio") Instant inicio,
      @Param("fim") Instant fim);
}
