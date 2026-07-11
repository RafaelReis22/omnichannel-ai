-- Persona do atendente virtual (Fase 3): a IA responde com o nome de uma
-- pessoa da equipe (ex.: corretor que divulga no próprio número), sem
-- parecer robô. A auditoria interna (livro-razão) continua marcando tudo.
ALTER TABLE ai_settings ADD COLUMN assistant_name TEXT;
