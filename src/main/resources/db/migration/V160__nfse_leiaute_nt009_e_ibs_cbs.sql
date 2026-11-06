-- NFS-e nacional, leiaute da NT SE/CGNFS-e 009 (DPS versao 1.01) e reforma tributaria.
--
-- Producao restrita da Receita valida o leiaute novo desde 20/10/2026; producao a partir de
-- 01/12/2026. Conferido em producao antes de escrever (06/11/2026): nfse_configs nao tem gatilho,
-- e os CHECKs existentes (ambiente, emission_mode, emit_for_cpf_mode) nao tocam estas colunas.
--
-- 1) IBS/CBS: a DPS informa so a classificacao; os valores sao calculados pela Receita.
--    O grupo e opcional em 2026 (obrigatorio para o Simples a partir de 01/01/2027).
-- 2) Total aproximado de tributos (Lei da Transparencia): ME/EPP do Simples nao pode mais mandar
--    "nao informar" (regra E0712) e precisa do percentual do Simples; nao optante precisa dos
--    percentuais federal, estadual e municipal (regra E0713).

ALTER TABLE nfse_configs
  ADD COLUMN IF NOT EXISTS ibs_cbs_cst VARCHAR(3),
  ADD COLUMN IF NOT EXISTS ibs_cbs_class_trib VARCHAR(6),
  ADD COLUMN IF NOT EXISTS ibs_cbs_ind_op VARCHAR(6),
  ADD COLUMN IF NOT EXISTS percentual_tributos_sn NUMERIC(5, 2),
  ADD COLUMN IF NOT EXISTS percentual_tributos_federais NUMERIC(5, 2),
  ADD COLUMN IF NOT EXISTS percentual_tributos_estaduais NUMERIC(5, 2),
  ADD COLUMN IF NOT EXISTS percentual_tributos_municipais NUMERIC(5, 2);

ALTER TABLE nfse_configs
  ADD CONSTRAINT ck_nfse_configs_ibs_cbs_cst CHECK (ibs_cbs_cst IS NULL OR ibs_cbs_cst ~ '^[0-9]{3}$'),
  ADD CONSTRAINT ck_nfse_configs_ibs_cbs_class_trib
    CHECK (ibs_cbs_class_trib IS NULL OR ibs_cbs_class_trib ~ '^[0-9]{6}$'),
  ADD CONSTRAINT ck_nfse_configs_ibs_cbs_ind_op CHECK (ibs_cbs_ind_op IS NULL OR ibs_cbs_ind_op ~ '^[0-9]{6}$'),
  ADD CONSTRAINT ck_nfse_configs_percentuais_tributos CHECK (
    (percentual_tributos_sn IS NULL OR percentual_tributos_sn BETWEEN 0 AND 99.99)
    AND (percentual_tributos_federais IS NULL OR percentual_tributos_federais BETWEEN 0 AND 100)
    AND (percentual_tributos_estaduais IS NULL OR percentual_tributos_estaduais BETWEEN 0 AND 100)
    AND (percentual_tributos_municipais IS NULL OR percentual_tributos_municipais BETWEEN 0 AND 100));
