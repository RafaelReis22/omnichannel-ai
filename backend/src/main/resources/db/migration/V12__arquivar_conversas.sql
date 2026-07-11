-- Arquivar conversa: some das listas sem apagar histórico (o livro-razão
-- da IA é registro de auditoria — conversa nunca é excluída de verdade).
-- Mensagem nova do contato desarquiva automaticamente.
ALTER TABLE conversations ADD COLUMN archived_at TIMESTAMPTZ;
CREATE INDEX conversations_tenant_archived_idx ON conversations (tenant_id, archived_at);
