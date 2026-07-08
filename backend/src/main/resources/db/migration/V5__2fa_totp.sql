-- 2FA por TOTP (RFC 6238) — docs/06-seguranca.md.
-- Segredo TOTP criptografado (AES-256-GCM, chave TOTP_ENCRYPTION_KEY);
-- códigos de recuperação persistidos apenas como hash SHA-256, uso único.

ALTER TABLE users
    ADD COLUMN totp_secret_encrypted TEXT,
    ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE recovery_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash TEXT NOT NULL UNIQUE,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX recovery_codes_user_id_idx ON recovery_codes (user_id);

ALTER TABLE recovery_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE recovery_codes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recovery_codes FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY auth_context ON recovery_codes FOR ALL
    USING (current_setting('app.auth_context', true) = 'on')
    WITH CHECK (current_setting('app.auth_context', true) = 'on');
