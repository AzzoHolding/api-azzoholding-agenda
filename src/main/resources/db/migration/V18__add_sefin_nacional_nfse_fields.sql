ALTER TABLE nfse_configs
  ADD COLUMN IF NOT EXISTS application_version VARCHAR(20),
  ADD COLUMN IF NOT EXISTS national_tax_code_default VARCHAR(6),
  ADD COLUMN IF NOT EXISTS nbs_code_default VARCHAR(9),
  ADD COLUMN IF NOT EXISTS simples_nacional_situacao VARCHAR(1),
  ADD COLUMN IF NOT EXISTS simples_nacional_regime_tributacao VARCHAR(1),
  ADD COLUMN IF NOT EXISTS especial_regime_tributacao VARCHAR(1);

ALTER TABLE nfse_invoices
  ADD COLUMN IF NOT EXISTS national_tax_code VARCHAR(6),
  ADD COLUMN IF NOT EXISTS nbs_code VARCHAR(9),
  ADD COLUMN IF NOT EXISTS local_prestacao_codigo_ibge VARCHAR(7);

ALTER TABLE nfse_invoice_items
  ADD COLUMN IF NOT EXISTS national_tax_code VARCHAR(6),
  ADD COLUMN IF NOT EXISTS nbs_code VARCHAR(9);
