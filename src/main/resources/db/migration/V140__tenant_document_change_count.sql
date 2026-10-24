-- Quantas vezes o salao trocou o CPF/CNPJ depois do cadastro. O documento identifica o periodo de
-- avaliacao (um por CPF/CNPJ) e o cliente da cobranca; trocar sem limite permitiria fugir disso.
ALTER TABLE tenants
  ADD COLUMN IF NOT EXISTS document_change_count INTEGER NOT NULL DEFAULT 0;
