-- Certificado A1 trancado com uma chave que so existe quando o dono digita a senha (pedido do dono,
-- 06/11/2026). Chave = Argon2id(senha, sal do certificado, pimenta do servidor); ver CofreDoCertificado.
--
-- protecao: NULL = legado (so a chave do servidor); 'SENHA_ARGON2ID_V1' = trancado pela senha.
-- kdf_sal:  sal do Argon2id em Base64 (nao e segredo).
--
-- Conferido em producao antes de escrever (06/11/2026): fiscal_certificates sem gatilho, sem CHECK,
-- 0 linhas; nfse_certificate_unlock_sessions com 0 linhas.

ALTER TABLE fiscal_certificates
  ADD COLUMN IF NOT EXISTS protecao VARCHAR(30),
  ADD COLUMN IF NOT EXISTS kdf_sal VARCHAR(64);

ALTER TABLE fiscal_certificates
  ADD CONSTRAINT ck_fiscal_certificates_protecao CHECK (
    protecao IS NULL OR (protecao = 'SENHA_ARGON2ID_V1' AND kdf_sal IS NOT NULL));
