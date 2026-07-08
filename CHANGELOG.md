# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Unreleased]

### Adicionado
- Plano de desenvolvimento completo (`docs/01` a `09`): visão, MVP, backlog, roadmap, arquitetura, stack, escalabilidade, segurança, deploy, testes e documentação.
- ADR-0001: backend em Java 21 + Spring Boot 3.5.x (substitui o plano original NestJS).
- Estrutura inicial do repositório: `.gitignore`, `LICENSE`, `.env.example`, `CONTRIBUTING.md`, `CHANGELOG.md`, `CLAUDE.md` (regras permanentes).
- Esqueleto do backend (`backend/`): Spring Boot 3.5.16, Spring Modulith com 7 módulos (`shared`, `tenant`, `channels`, `inbox`, `ai`, `crm`, `analytics`) e verificação de arquitetura no build.
- Migrations de fundação: extensões `pgvector`/`pgcrypto` e tabela de outbox de eventos do Modulith.
- Infra local: `docker-compose.yml` (Postgres+pgvector, Redis, RabbitMQ) e `Dockerfile` multi-stage.
- CI no GitHub Actions (build + testes com Testcontainers + validação da imagem Docker), Dependabot, CODEOWNERS, templates de issue/PR, labels e milestones por fase do roadmap.
- Multi-tenant com Row-Level Security real (ADR-0002): papel `app_runtime` sem bypass, políticas negar-por-padrão, `TenantAwareDataSource` propagando o contexto — com testes de isolamento contra Postgres real.
- Autenticação JWT: signup (tenant + admin), login e refresh com rotação e revogação; senhas Argon2id; refresh tokens persistidos como hash SHA-256; proteção contra enumeração de e-mails.
- Endpoints: `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `GET /api/me`.
- Channel Gateway (contrato da Fase 0): interface `ChannelAdapter` + registry, mensagens normalizadas entre canais (`MessageContent`, `InboundMessageReceived`), gateway de entrada publicando eventos pelo outbox do Modulith.
- Rate limiting de força bruta em autenticação: limite por IP em `/api/auth/**` e bloqueio por conta após falhas seguidas de login (sucesso zera o contador), respostas 429 com `Retry-After`, backing em Redis.
- Gestão de canais: `POST/GET /api/channels` e `POST /api/channels/{id}/disable` (Admin), credenciais AES-256-GCM em repouso, RBAC por authority derivada do JWT.
