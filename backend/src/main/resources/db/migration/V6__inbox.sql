-- Inbox unificado: contatos com identidades por canal (histórico
-- consolidado — docs/02-plano-de-produto.md), conversas e mensagens.

CREATE TABLE contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    name TEXT NOT NULL,
    tags TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX contacts_tenant_id_idx ON contacts (tenant_id);

-- Um contato pode existir em vários canais (telefone no WhatsApp, e-mail,
-- visitante do webchat). A identidade é a chave de deduplicação na entrada.
CREATE TABLE contact_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    contact_id UUID NOT NULL REFERENCES contacts (id) ON DELETE CASCADE,
    channel_type TEXT NOT NULL,
    external_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, channel_type, external_id)
);
CREATE INDEX contact_identities_contact_id_idx ON contact_identities (contact_id);

CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    contact_id UUID NOT NULL REFERENCES contacts (id),
    channel_id UUID NOT NULL REFERENCES channels (id),
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'WITH_AI', 'WITH_HUMAN', 'RESOLVED')),
    assignee_user_id UUID REFERENCES users (id),
    tags TEXT[] NOT NULL DEFAULT '{}',
    -- desnormalizados para a lista do inbox (CQRS leve — docs/03)
    last_message_preview TEXT,
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX conversations_tenant_status_idx ON conversations (tenant_id, status);
CREATE INDEX conversations_contact_id_idx ON conversations (contact_id);
CREATE INDEX conversations_last_message_at_idx ON conversations (tenant_id, last_message_at DESC);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender TEXT NOT NULL CHECK (sender IN ('CONTACT', 'AGENT', 'AI')),
    kind TEXT NOT NULL CHECK (kind IN ('TEXT', 'IMAGE', 'VIDEO', 'AUDIO', 'DOCUMENT')),
    text TEXT,
    media_url TEXT,
    mime_type TEXT,
    external_message_id TEXT,
    sender_user_id UUID REFERENCES users (id),
    ai_confidence NUMERIC(4, 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX messages_conversation_id_idx ON messages (conversation_id, created_at);
-- deduplicação de reentrega de webhook (ids de provedor são globalmente únicos)
CREATE UNIQUE INDEX messages_external_id_uidx
    ON messages (tenant_id, external_message_id)
    WHERE external_message_id IS NOT NULL;

-- RLS (tenant apenas — nenhum fluxo pré-autenticação toca estas tabelas)
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['contacts', 'contact_identities', 'conversations', 'messages'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I FOR ALL
            USING (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)
            WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)', t);
    END LOOP;
END
$$;
