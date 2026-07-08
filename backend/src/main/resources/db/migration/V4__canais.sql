-- Canais conectados por tenant (Channel Gateway — docs/03-arquitetura.md).
-- Credenciais são armazenadas criptografadas (AES-256-GCM) pela aplicação;
-- o banco nunca vê o texto claro (docs/06-seguranca.md).
CREATE TABLE channels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    type TEXT NOT NULL CHECK (type IN ('WHATSAPP', 'INSTAGRAM', 'MESSENGER', 'EMAIL', 'WEBCHAT', 'TELEGRAM')),
    name TEXT NOT NULL,
    credentials_encrypted TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED', 'ERROR')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX channels_tenant_id_idx ON channels (tenant_id);

ALTER TABLE channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE channels FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON channels FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
