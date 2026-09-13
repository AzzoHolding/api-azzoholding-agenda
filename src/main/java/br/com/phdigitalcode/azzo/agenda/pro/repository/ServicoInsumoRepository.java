package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicoInsumo;

/**
 * Espelha {@code modules/inventory/domain/repository/ServicoInsumoRepository.java}.
 *
 * <p>Os tres metodos do original estao aqui. Repare na assimetria, <b>preservada</b>:
 * {@code findByTenantAndService} e {@code findByTenantAndItem} filtram {@code ativo = true}, mas
 * {@code findByTenantServiceAndItem} <b>nao</b> — ele existe para detectar duplicidade no cadastro,
 * onde um insumo desativado ainda conta como ja existente.
 */
@Repository
public interface ServicoInsumoRepository extends JpaRepository<ServicoInsumo, UUID> {

  @Query(
      "select i from ServicoInsumo i where i.tenantId = :tenantId and i.serviceId = :serviceId "
          + "and i.ativo = true order by i.createdAt asc")
  List<ServicoInsumo> findByTenantAndService(
      @Param("tenantId") UUID tenantId, @Param("serviceId") UUID serviceId);

  @Query(
      "select i from ServicoInsumo i where i.tenantId = :tenantId "
          + "and i.itemEstoqueId = :itemEstoqueId and i.ativo = true")
  List<ServicoInsumo> findByTenantAndItem(
      @Param("tenantId") UUID tenantId, @Param("itemEstoqueId") UUID itemEstoqueId);

  List<ServicoInsumo> findByTenantIdAndServiceIdAndItemEstoqueId(
      UUID tenantId, UUID serviceId, UUID itemEstoqueId);

  Optional<ServicoInsumo> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Todos os insumos ativos do tenant — base do custo teorico da margem por servico. */
  List<ServicoInsumo> findByTenantIdAndAtivoTrue(UUID tenantId);

  /**
   * Execucoes e receita, no periodo, dos servicos que consomem estoque: {@code [serviceId,
   * execucoes, receitaEmCentavos]}.
   *
   * <p>Mesma base do relatorio de vendas (agendamento {@code Concluido}, itens do agendamento), e
   * so dos servicos com ao menos um insumo ativo — margem de servico sem insumo seria a propria
   * receita e nao diria nada sobre estoque.
   */
  @Query(
      value =
          """
          SELECT ai.service_id::text, COALESCE(SUM(ai.quantity), 0), COALESCE(SUM(ai.total_price), 0)
          FROM appointments a
          JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
          WHERE a.tenant_id = :tenantId
            AND a.status = 'Concluido'
            AND a.date BETWEEN :inicio AND :fim
            AND EXISTS (
              SELECT 1 FROM servico_insumo si
              WHERE si.tenant_id = a.tenant_id AND si.service_id = ai.service_id AND si.ativo = true)
          GROUP BY ai.service_id
          """,
      nativeQuery = true)
  List<Object[]> somarExecucoesDosServicosComInsumo(
      @Param("tenantId") UUID tenantId,
      @Param("inicio") java.time.LocalDate inicio,
      @Param("fim") java.time.LocalDate fim);

  /** Equivalente ao {@code firstResult()} do original: sem match devolve vazio, nao erro. */
  default Optional<ServicoInsumo> findByTenantServiceAndItem(
      UUID tenantId, UUID serviceId, UUID itemEstoqueId) {
    return findByTenantIdAndServiceIdAndItemEstoqueId(tenantId, serviceId, itemEstoqueId).stream()
        .findFirst();
  }
}
