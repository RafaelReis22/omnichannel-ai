-- Módulo de IA: base de conhecimento (RAG), configurações por tenant,
-- livro-razão de resolução (diferencial central — docs/01-visao-e-produto.md)
-- e o vector store do Spring AI com RLS.

CREATE TABLE kb_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX kb_documents_tenant_id_idx ON kb_documents (tenant_id);

CREATE TABLE ai_settings (
    tenant_id UUID PRIMARY KEY REFERENCES tenants (id),
    enabled BOOLEAN NOT NULL DEFAULT true,
    -- abaixo deste limiar a IA não responde sozinha: handoff para humano
    confidence_threshold NUMERIC(4, 3) NOT NULL DEFAULT 0.700,
    tone TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE resolution_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    conversation_id UUID NOT NULL REFERENCES conversations (id),
    -- mensagem do contato que disparou a decisão; unique = idempotência
    -- contra reentrega do outbox (nunca responder duas vezes)
    trigger_message_id UUID NOT NULL,
    -- mensagem enviada pela IA (nula em handoff)
    ai_message_id UUID,
    action TEXT NOT NULL CHECK (action IN ('ANSWERED', 'HANDOFF')),
    confidence NUMERIC(4, 3) NOT NULL,
    -- resumo da conversa gerado no handoff, para o humano assumir com contexto
    handoff_summary TEXT,
    reviewed_by_human BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, trigger_message_id)
);
CREATE INDEX resolution_ledger_tenant_created_idx ON resolution_ledger (tenant_id, created_at DESC);

-- Vector store do Spring AI (initialize-schema=false: Flyway é o dono).
-- Dimensão 1536 = text-embedding-3-small (OpenAI).
CREATE TABLE vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT,
    metadata JSONB,
    embedding VECTOR(1536)
);
CREATE INDEX vector_store_embedding_idx ON vector_store
    USING hnsw (embedding vector_cosine_ops);
CREATE INDEX vector_store_kb_document_idx ON vector_store ((metadata ->> 'kb_document_id'));

-- RLS
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['kb_documents', 'ai_settings', 'resolution_ledger'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I FOR ALL
            USING (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)
            WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)', t);
    END LOOP;
END
$$;

-- vetores: o tenant vive no metadata JSONB — o RLS isola embeddings entre
-- tenants no banco, não só na aplicação
ALTER TABLE vector_store ENABLE ROW LEVEL SECURITY;
ALTER TABLE vector_store FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON vector_store FOR ALL
    USING (metadata ->> 'tenant_id' = NULLIF(current_setting('app.tenant_id', true), ''))
    WITH CHECK (metadata ->> 'tenant_id' = NULLIF(current_setting('app.tenant_id', true), ''));
