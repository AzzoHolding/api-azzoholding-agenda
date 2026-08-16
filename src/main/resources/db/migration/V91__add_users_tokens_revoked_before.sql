-- Suporte a revogação imediata de access tokens JWT após troca de senha.
-- Quando revokeAllForUser() é chamado, tokens_revoked_before é atualizado para now().
-- O SecurityIdentityAugmentor rejeita JWTs com iat < tokens_revoked_before.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS tokens_revoked_before TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_tokens_revoked_before
    ON users (id)
    WHERE tokens_revoked_before IS NOT NULL;
