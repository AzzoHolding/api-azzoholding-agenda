-- Item 6 (pedido do dono, 17/11): os codigos gerados pelo sistema comecam com 6 digitos tirados de um hash do
-- CPF/CNPJ do salao, para nao se misturarem com os de outros saloes. Cupom de lote: 482913-NATAL-7K3QPZ (ate 24);
-- vale-presente: 482913 + 10 caracteres (16). As tabelas sao da V180/V181: ainda sem dados em producao.
ALTER TABLE cupons ALTER COLUMN codigo TYPE VARCHAR(30);
ALTER TABLE vales_presente ALTER COLUMN codigo TYPE VARCHAR(20);
