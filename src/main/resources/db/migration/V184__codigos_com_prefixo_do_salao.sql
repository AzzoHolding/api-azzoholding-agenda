-- Item 6 (pedido do dono, 17/11): o codigo do cupom de lote comeca com 6 digitos tirados de um hash do CPF/CNPJ do
-- salao, para nao se misturar com os de outros saloes: 482913-NATAL-7K3QPZ (ate 24 caracteres). A tabela e da V180:
-- ainda sem dados em producao. O vale-presente segue com 10 caracteres (o documento entra no hash dele).
ALTER TABLE cupons ALTER COLUMN codigo TYPE VARCHAR(30);
