# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Unreleased]

### Adicionado
- CRM simples (MVP): pipeline de vendas com estágios configuráveis por tenant (padrões no primeiro acesso), negócios ligados a contatos, board kanban `GET /api/crm/board` e CRUD de estágios/negócios; frontend com drag-and-drop nativo, total do funil em BRL e botão "+ Negócio" no cabeçalho da conversa.
- Widget de webchat embeddável (`/widget.js`): arquivo único vanilla JS com Shadow DOM (não conflita com o site do cliente), balão flutuante com contador de não lidas, polling, dedup por clientMessageId, eco otimista reconciliado e dark mode; página de demonstração em `/widget-demo.html`.
- Página Canais no dashboard: conectar canais e copiar o snippet de embed pronto.
- Dashboard (frontend/, Next.js 16 + React 19 + Tailwind 4): login com fluxo 2FA, signup de empresa, inbox com fila filtrável por status, conversa com resposta/assumir/resolver, base de conhecimento, configurações da IA e livro-razão com revisão — design system próprio com status semânticos e dark mode.
- CORS configurável para o dashboard (`CORS_ALLOWED_ORIGINS`) e aberto no webchat público (widget roda no site do cliente).
- Módulo de IA (coração do produto): AI Worker consome mensagens materializadas, decide com RAG (Spring AI + pgvector, Claude para chat e OpenAI para embeddings) + histórico da conversa, e aplica o **portão de confiança** — acima do limiar responde sozinho, abaixo devolve à fila humana com resumo gerado.
- **Livro-razão de resolução**: toda decisão autônoma (resposta ou handoff) vira registro auditável com confiança, com revisão humana via API — o diferencial central contra o gap marketing-vs-realidade do mercado.
- Base de conhecimento com chunking e retrieval escopado por tenant; RLS aplicado até nos vetores (política sobre o metadata JSONB).
- APIs: `POST/GET/DELETE /api/knowledge`, `GET/PUT /api/ai/settings` (limiar de confiança, tom, liga/desliga), `GET /api/ai/ledger` + `POST /api/ai/ledger/{id}/review`.
- Idempotência da IA por mensagem-gatilho (reentrega do outbox nunca gera resposta dupla); chamadas de LLM fora de transação de banco.
- Canal de webchat (primeiro canal real): endpoints públicos do widget (`POST/GET /api/webchat/{channelId}/messages`) com channelId como site key, rate limit por IP e por visitante, dedup por `clientMessageId` e polling incremental (`?after=`); adapter registrado no gateway (entrega = persistência + polling).
- Guard de startup contra OSIV ligado — reutilizaria a conexão entre transações e quebraria o RLS silenciosamente.

### Corrigido
- `lastMessageAt` da conversa ficava nulo na resposta de agente (leitura de `createdAt` antes do flush).

### Adicionado (anterior)
- Inbox unificado (Fase 1): contatos com identidades por canal (histórico consolidado), conversas com status/atribuição/etiquetas, mensagens com deduplicação por id de provedor e reabertura automática de conversa resolvida.
- Listener assíncrono do outbox materializando mensagens no inbox com contexto de tenant (RLS) e retry de corrida entre eventos concorrentes.
- Fachada `OutboundMessenger` no Channel Gateway — resposta de agente sai pelo adapter do canal sem nenhum módulo tocar credenciais.
- API do inbox: `GET /api/conversations` (filtros status/atribuído + paginação), `GET /api/conversations/{id}` e `/messages`, `POST .../assign|resolve|reply`, `PUT .../tags`, `GET /api/contacts/{id}` e `/conversations`.

## [0.1.0] — 2026-07-07

Fase 0 (Fundação) completa — ver [roadmap](docs/02-plano-de-produto.md#roadmap).

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
- 2FA por TOTP (RFC 6238): enrolamento com QR (`POST /api/2fa/setup` e `/confirm`), login em duas etapas com token de desafio efêmero (rejeitado em rotas protegidas), códigos de recuperação de uso único, segredo criptografado em repouso.
- Rate limiting de força bruta em autenticação: limite por IP em `/api/auth/**`, bloqueio por conta após falhas seguidas de login e de código MFA (sucesso zera o contador), respostas 429 com `Retry-After`, backing em Redis.
- Channel Gateway (contrato da Fase 0): interface `ChannelAdapter` + registry, mensagens normalizadas entre canais (`MessageContent`, `InboundMessageReceived`), gateway de entrada publicando eventos pelo outbox do Modulith.
- Gestão de canais: `POST/GET /api/channels` e `POST /api/channels/{id}/disable` (Admin), credenciais AES-256-GCM em repouso, RBAC por authority derivada do JWT.
- Contrato OpenAPI (springdoc) em `/v3/api-docs` + Swagger UI, com esquema bearer — base do client TypeScript do frontend.
- Endpoints: `POST /api/auth/{signup,login,refresh}`, `POST /api/auth/2fa/verify`, `POST /api/2fa/{setup,confirm}`, `GET /api/me`, `POST/GET /api/channels`, `POST /api/channels/{id}/disable`.
