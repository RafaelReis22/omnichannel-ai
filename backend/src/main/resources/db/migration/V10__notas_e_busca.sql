-- Notas internas em conversas + índices de busca textual (Fase 2 —
-- docs/02-plano-de-produto.md). tsvector nativo no MVP; Meilisearch é o
-- upgrade documentado em docs/05-escalabilidade.md.

CREATE TABLE conversation_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES users (id),
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX conversation_notes_conversation_idx ON conversation_notes (conversation_id, created_at);

ALTER TABLE conversation_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation_notes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON conversation_notes FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- busca global: full-text em português sobre mensagens e contatos
CREATE INDEX messages_text_fts_idx ON messages
    USING gin (to_tsvector('portuguese', coalesce(text, '')));
CREATE INDEX contacts_name_fts_idx ON contacts
    USING gin (to_tsvector('portuguese', name));
