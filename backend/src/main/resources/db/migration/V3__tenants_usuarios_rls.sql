-- =====================================================================
-- Identidade multi-tenant: tenants, usuários, refresh tokens — com RLS.
--
-- Modelo de segurança (docs/06-seguranca.md, ADR-0002):
--  * Superusuários ignoram RLS. Por isso a aplicação assume o papel
--    app_runtime (NOBYPASSRLS) via SET ROLE ao abrir conexão com
--    contexto de tenant — o isolamento vale em dev, teste e produção.
--  * Sem contexto definido, as políticas não retornam nenhuma linha
--    (negar por padrão).
--  * app.auth_context é usado apenas pelo fluxo de autenticação
--    (login/signup/refresh), que precisa consultar por e-mail antes de
--    existir um tenant na sessão.
-- =====================================================================

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    plan TEXT NOT NULL DEFAULT 'free',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    name TEXT NOT NULL,
    -- E-mail é único globalmente (não por tenant): é a credencial de login.
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'AGENT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX users_tenant_id_idx ON users (tenant_id);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Só o hash SHA-256 do token é persistido, nunca o token em claro.
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------
-- Papel de runtime sem privilégios de bypass de RLS
-- ---------------------------------------------------------------------
DO $$
BEGIN
    CREATE ROLE app_runtime NOLOGIN NOSUPERUSER NOBYPASSRLS;
EXCEPTION
    WHEN duplicate_object THEN NULL; -- já existe (re-execução em outro banco do mesmo cluster)
END
$$;

GRANT app_runtime TO CURRENT_USER;
GRANT USAGE ON SCHEMA public TO app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_runtime;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_runtime;

-- ---------------------------------------------------------------------
-- Políticas de RLS
-- FORCE: aplica mesmo ao dono da tabela. NULLIF protege contra o valor
-- '' que um RESET de GUC customizada pode deixar na sessão.
-- ---------------------------------------------------------------------
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenants FOR ALL
    USING (id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY auth_context ON tenants FOR ALL
    USING (current_setting('app.auth_context', true) = 'on')
    WITH CHECK (current_setting('app.auth_context', true) = 'on');

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON users FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY auth_context ON users FOR ALL
    USING (current_setting('app.auth_context', true) = 'on')
    WITH CHECK (current_setting('app.auth_context', true) = 'on');

ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON refresh_tokens FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY auth_context ON refresh_tokens FOR ALL
    USING (current_setting('app.auth_context', true) = 'on')
    WITH CHECK (current_setting('app.auth_context', true) = 'on');
