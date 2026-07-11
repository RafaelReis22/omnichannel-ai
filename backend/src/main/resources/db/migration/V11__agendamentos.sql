-- Agendamento de reuniões/visitas (Fase 2, docs/02-plano-de-produto.md):
-- compromissos ligados a contato (e opcionalmente à conversa de origem).
-- Integração com calendário externo fica para depois — o convite sai como
-- arquivo .ics, que qualquer calendário importa.

CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    contact_id UUID NOT NULL REFERENCES contacts (id),
    conversation_id UUID REFERENCES conversations (id),
    created_by_user_id UUID NOT NULL,
    title TEXT NOT NULL,
    notes TEXT,
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 30,
    status TEXT NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX appointments_tenant_scheduled_idx ON appointments (tenant_id, scheduled_at);
CREATE INDEX appointments_contact_idx ON appointments (contact_id);
CREATE INDEX appointments_conversation_idx ON appointments (conversation_id);

ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON appointments FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
