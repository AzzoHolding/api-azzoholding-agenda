package br.com.phdigitalcode.azzo.agenda.pro.util;

/**
 * O que cada CLIENTE gastou e quando veio, em uma definicao so.
 *
 * <p>Ate 2026-09-17 "valor gasto", "ultima visita" e a curva ABC somavam o PRECO dos agendamentos
 * concluidos: venda avulsa (produto, pacote) nao contava, desconto do PDV e servico extra nao
 * apareciam, e comanda estornada seguia como gasto. Agora e a mesma regra da receita de servicos
 * ({@link ReceitaDeServicosSql}), no nivel da conta do cliente.
 *
 * <p>Cada linha e uma VISITA:
 *
 * <ul>
 *   <li><b>comanda FECHADA do cliente</b>: o total cobrado (sem gorjeta), no dia em que fechou —
 *       inclui produto e pacote vendidos;
 *   <li><b>agendamento concluido sem comanda fechada</b>: conta como visita; o valor e o do
 *       agendamento so quando nao ha comanda nenhuma (regra antiga). Com comanda aberta, estornada ou
 *       cancelada, o cliente veio mas o dinheiro nao entrou: valor zero.
 * </ul>
 *
 * <p>Colunas: {@code client_id, dia, visita_id, valor}. Uso: {@code WITH } + {@link #CTE} + a
 * consulta, com o parametro {@code :tenantId}.
 */
public final class ReceitaDeClientesSql {

  private ReceitaDeClientesSql() {}

  public static final String CTE =
      """
      visitas_clientes AS (
        SELECT c.client_id,
               (c.closed_at AT TIME ZONE 'America/Sao_Paulo')::date AS dia,
               c.id AS visita_id,
               c.total AS valor
        FROM comandas c
        WHERE c.tenant_id = :tenantId
          AND c.status = 'FECHADA'
          AND c.client_id IS NOT NULL
        UNION ALL
        SELECT a.client_id,
               a.date AS dia,
               a.id AS visita_id,
               CASE WHEN EXISTS (
                      SELECT 1 FROM comandas cx
                      WHERE cx.tenant_id = a.tenant_id AND cx.appointment_id = a.id)
                    THEN 0
                    ELSE COALESCE((
                      SELECT SUM(ai.total_price) FROM appointment_items ai
                      WHERE ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id), 0)
               END AS valor
        FROM appointments a
        WHERE a.tenant_id = :tenantId
          AND a.status = 'Concluido'
          AND NOT EXISTS (
            SELECT 1 FROM comandas cf
            WHERE cf.tenant_id = a.tenant_id AND cf.appointment_id = a.id AND cf.status = 'FECHADA'
          )
      )
      """;
}
