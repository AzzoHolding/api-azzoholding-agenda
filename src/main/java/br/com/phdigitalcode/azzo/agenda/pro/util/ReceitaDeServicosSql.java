package br.com.phdigitalcode.azzo.agenda.pro.util;

/**
 * A RECEITA DE SERVICO, em uma definicao so, para os relatorios por servico e por profissional.
 *
 * <p>Ate 2026-09-17 esses relatorios somavam {@code appointment_items.total_price} dos agendamentos
 * concluidos — o PRECO DO AGENDAMENTO —, enquanto o financeiro somava o que entrou no caixa. Os dois
 * numeros divergiam com desconto dado no PDV, servico extra lancado na comanda, comanda estornada e
 * venda avulsa, que nem aparecia (analise de 2026-09-16, M8). Agora a receita e o que a COMANDA
 * cobrou — a mesma regra da comissao (decisao do usuario, 2026-09-17).
 *
 * <p>Cada linha e um servico vendido:
 *
 * <ul>
 *   <li><b>comanda FECHADA</b> (estornada e cancelada ficam de fora): valor do item com o desconto
 *       da comanda rateado; servico coberto por pacote/assinatura entra com zero — o dinheiro
 *       entrou na venda do pacote. Dia = dia em que a comanda fechou; profissional = o do item, ou o
 *       do agendamento quando o item nao tem;
 *   <li><b>agendamento concluido SEM comanda</b> (regra antiga, ou a abertura automatica falhou):
 *       total do item do agendamento, no dia do agendamento.
 * </ul>
 *
 * <p>Colunas: {@code dia, service_id, professional_id, venda_id, valor}. Uso: {@code WITH } +
 * {@link #CTE} + a consulta, com o parametro {@code :tenantId}; o filtro de data e do chamador.
 */
public final class ReceitaDeServicosSql {

  private ReceitaDeServicosSql() {}

  public static final String CTE =
      """
      receita_servicos AS (
        SELECT (c.closed_at AT TIME ZONE 'America/Sao_Paulo')::date AS dia,
               ci.referencia_id AS service_id,
               COALESCE(ci.professional_id, ap.professional_id) AS professional_id,
               c.id AS venda_id,
               CASE WHEN c.subtotal > 0
                    THEN ROUND(ci.total * GREATEST(c.subtotal - c.desconto, 0) / c.subtotal, 2)
                    ELSE 0 END AS valor
        FROM comandas c
        JOIN comanda_itens ci ON ci.comanda_id = c.id AND ci.tenant_id = c.tenant_id
        LEFT JOIN appointments ap ON ap.id = c.appointment_id AND ap.tenant_id = c.tenant_id
        WHERE c.tenant_id = :tenantId
          AND c.status = 'FECHADA'
          AND ci.tipo = 'SERVICO'
        UNION ALL
        SELECT a.date AS dia,
               ai.service_id,
               a.professional_id,
               a.id AS venda_id,
               ai.total_price AS valor
        FROM appointments a
        JOIN appointment_items ai ON ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id
        WHERE a.tenant_id = :tenantId
          AND a.status = 'Concluido'
          AND NOT EXISTS (
            SELECT 1 FROM comandas c2
            WHERE c2.tenant_id = a.tenant_id AND c2.appointment_id = a.id
          )
      )
      """;
}
