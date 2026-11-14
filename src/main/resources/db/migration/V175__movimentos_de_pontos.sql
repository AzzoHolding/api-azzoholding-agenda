-- Fidelidade (14/11/2026, plano PLANO-FIDELIDADE-PACOTES.md, etapa 1): historico dos pontos.
--
-- Ate aqui o saldo era so um numero em clients.loyalty_points, alterado com ler-conferir-gravar
-- (dois resgates ao mesmo tempo gastavam os mesmos pontos). Agora o saldo muda numa instrucao so
-- (debito condicional) e cada movimento fica aqui: credito da venda, resgate, devolucao, estorno,
-- expiracao, ajuste.
--
-- pontos_restantes + expira_em: o que sobra de cada credito e quando vence (validade definida pelo
-- dono do salao). O resgate consome primeiro o que vence antes.
--
-- clients.loyalty_points continua sendo o saldo (nao e alterado aqui). Tabela nova: nada em
-- producao depende dela. O saldo de hoje entra como SALDO_INICIAL, sem vencimento.

CREATE TABLE IF NOT EXISTS movimentos_de_pontos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
  tipo VARCHAR(20) NOT NULL,
  pontos INTEGER NOT NULL,
  saldo_apos INTEGER NOT NULL,
  comanda_id UUID,
  pontos_restantes INTEGER NOT NULL DEFAULT 0,
  expira_em TIMESTAMP WITH TIME ZONE,
  motivo VARCHAR(200),
  created_by UUID,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT ck_movimentos_de_pontos_tipo CHECK (
    tipo IN ('SALDO_INICIAL', 'CREDITO', 'RESGATE', 'DEVOLUCAO', 'ESTORNO', 'EXPIRACAO', 'AJUSTE')),
  CONSTRAINT ck_movimentos_de_pontos_restantes CHECK (pontos_restantes >= 0)
);

-- Extrato da cliente (mais recente primeiro).
CREATE INDEX IF NOT EXISTS ix_movimentos_de_pontos_cliente
  ON movimentos_de_pontos (client_id, created_at DESC);

-- Creditos com saldo, para o resgate (o que vence antes primeiro) e para a rotina de expiracao.
CREATE INDEX IF NOT EXISTS ix_movimentos_de_pontos_creditos
  ON movimentos_de_pontos (client_id, expira_em) WHERE pontos_restantes > 0;
CREATE INDEX IF NOT EXISTS ix_movimentos_de_pontos_vencendo
  ON movimentos_de_pontos (expira_em) WHERE pontos_restantes > 0 AND expira_em IS NOT NULL;

-- O credito de uma venda, para o estorno saber quanto dele ja foi usado.
CREATE INDEX IF NOT EXISTS ix_movimentos_de_pontos_comanda
  ON movimentos_de_pontos (comanda_id) WHERE comanda_id IS NOT NULL;

INSERT INTO movimentos_de_pontos (tenant_id, client_id, tipo, pontos, saldo_apos, pontos_restantes, motivo)
SELECT c.tenant_id, c.id, 'SALDO_INICIAL', c.loyalty_points, c.loyalty_points, GREATEST(c.loyalty_points, 0),
       'Saldo antes do histórico (sem vencimento)'
FROM clients c
WHERE c.loyalty_points <> 0;
