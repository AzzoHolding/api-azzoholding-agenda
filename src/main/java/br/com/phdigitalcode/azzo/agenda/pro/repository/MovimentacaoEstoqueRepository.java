package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentacaoEstoque;

/**
 * Espelha {@code modules/inventory/domain/repository/MovimentacaoEstoqueRepository.java} mais as
 * duas contagens que o original faz inline (via {@code count(...)} do Panache) dentro de
 * {@code ServicoEstoque.consumirInsumosPorAgendamento} / {@code consumirInsumosPorItemComanda}.
 *
 * <p>Essas contagens sao a <b>guarda de idempotencia</b> do consumo de insumo: concluir o mesmo
 * agendamento (ou fechar o mesmo item de comanda) duas vezes nao pode baixar o estoque duas vezes.
 */
@Repository
public interface MovimentacaoEstoqueRepository
    extends JpaRepository<MovimentacaoEstoque, UUID>,
        JpaSpecificationExecutor<MovimentacaoEstoque> {

  List<MovimentacaoEstoque> findByTenantIdAndComandaItemId(UUID tenantId, UUID comandaItemId);

  /** Base do dashboard: o original carrega todas as movimentacoes do tenant em memoria. */
  List<MovimentacaoEstoque> findByTenantId(UUID tenantId);

  long countByTenantIdAndAppointmentIdAndItemEstoqueId(
      UUID tenantId, UUID appointmentId, UUID itemEstoqueId);

  long countByTenantIdAndComandaItemIdAndItemEstoqueId(
      UUID tenantId, UUID comandaItemId, UUID itemEstoqueId);
}
