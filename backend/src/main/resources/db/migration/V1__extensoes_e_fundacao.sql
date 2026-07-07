-- Fundação do banco: extensões exigidas pela plataforma.
-- pgvector: busca vetorial para RAG (docs/04-stack-tecnologica.md)
CREATE EXTENSION IF NOT EXISTS vector;

-- UUIDs gerados pelo banco quando necessário
CREATE EXTENSION IF NOT EXISTS pgcrypto;
