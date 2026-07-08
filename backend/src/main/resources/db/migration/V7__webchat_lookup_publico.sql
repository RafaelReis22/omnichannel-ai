-- O widget de webchat é público: o visitante conhece apenas o channelId
-- (site key). Para resolver o tenant antes de existir contexto, o
-- ChannelDirectory usa o contexto de sistema (app.auth_context) — leitura
-- apenas, escrita continua exclusiva do tenant.
CREATE POLICY auth_context ON channels FOR SELECT
    USING (current_setting('app.auth_context', true) = 'on');
