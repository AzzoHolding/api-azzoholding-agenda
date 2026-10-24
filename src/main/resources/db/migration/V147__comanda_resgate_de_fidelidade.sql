-- V147 (FIN-007): o resgate de pontos de fidelidade passa a ficar registrado na comanda.
-- Antes, resgatar debitava os pontos do cliente e somava o valor ao desconto, sem guardar nada:
-- cancelar ou estornar a comanda nao devolvia os pontos, e um desconto percentual aplicado depois
-- apagava o valor do resgate sem devolver os pontos.
-- Conferido em producao antes (18/10/2026): comandas sem gatilho, so o CHECK de status; so ADICIONA
-- colunas com valor padrao, sem mexer em linha existente (resgates antigos ficam com 0).

ALTER TABLE comandas
    ADD COLUMN IF NOT EXISTS pontos_fidelidade_resgatados INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS valor_resgate_fidelidade NUMERIC(10, 2) NOT NULL DEFAULT 0;
