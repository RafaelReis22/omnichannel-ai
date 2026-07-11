# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Unreleased]

### Corrigido
- Canais WhatsApp/Instagram/Messenger eram um beco sem saída na UI: agora têm campos de credencial por tipo e exibem a URL do webhook + verify token uma única vez após conectar, com botões de copiar.
- Lista de conversas não mostrava o nome do contato nem o canal — inviável para uso contínuo.
- IA com LLM indisponível deixava a conversa presa sem resposta: agora faz fail-open para a fila humana com registro explícito no livro-razão.

### Alterado
- Redesign para uso o dia inteiro: base clara e neutra, sidebar clara com estado ativo evidente, status como ponto+texto discreto, notas internas sutis, hierarquia nome→preview→metadados na lista.
- Verificação E2E visual obrigatória (audit.mjs + Playwright) antes de qualquer entrega de UI.

### Adicionado
- Relatórios operacionais (Fase 3): página Relatórios com período de 7/30/90 dias — novas conversas, mensagens recebidas, resolvidas, autonomia da IA (respostas vs. handoffs do livro-razão) e mediana do tempo de primeira resposta; gráfico de barras por dia e distribuição por canal e status (SVG próprio, sem lib). `GET /api/analytics/overview` calcula tudo em SQL com o RLS escopando por tenant automaticamente.
- Agendamento de reuniões/visitas (Fase 2): botão "Agendar" no cabeçalho da conversa (título, data/hora, duração) cria compromisso ligado ao contato e à conversa; página Agenda agrupa por dia com concluir/cancelar e download do convite `.ics` (formato universal — Google/Outlook/Apple importam sem OAuth nesta fase). Tabela `appointments` com RLS; APIs `POST/GET /api/appointments`, `by-conversation`, `done`, `cancel`, `/{id}/ics`. Integração de calendário externo fica para quando houver deploy.
- Edição de tags na conversa: chips no cabeçalho com adicionar/remover (Enter confirma, Esc cancela), refletidas na hora na lista do inbox — a API já existia, faltava a UI.
- Modo de teste sem custo (docs/10): IA por modelo local via Ollama (`AI_CHAT_PROVIDER=ollama` / `AI_EMBEDDING_PROVIDER=ollama`, sem chave Anthropic/OpenAI) — embeddings locais de 768 dimensões são completados com zeros até a coluna de 1536 (não altera o ranking do retrieval); guia cobre também WhatsApp real com número de teste gratuito da Meta + túnel cloudflared.
- Mídia bidirecional em todos os canais: agente envia imagem/vídeo/áudio/documento pelo WhatsApp (Cloud API e 360dialog, por link pré-assinado com legenda), Messenger/Instagram (attachment + legenda como texto na sequência) e e-mail (anexo de verdade embutido, não link que expira); mídia recebida do WhatsApp (download autenticado via Graph/BSP) e dos canais Meta (CDN) vai para o object storage e aparece no inbox como qualquer mensagem — preview da lista mostra 📷/🎬/🎙️/📄. A URL pré-assinada é gerada num ponto único de saída (OutboundMessenger).
- Mídia nas conversas (Fase 2): upload autenticado `POST /api/uploads` (imagem/vídeo/áudio/PDF até 15 MB, tipo validado, 422 nos demais) para object storage S3-compatível (MinIO local / R2 em produção); mensagens guardam só a referência interna `storage://` e toda leitura (inbox e webchat público) resolve para URL pré-assinada de 15 min — quem o RLS não deixa ver a conversa nunca recebe URL. No dashboard: anexar com preview e legenda no compositor, renderização de imagem/vídeo/áudio/documento nos balões; widget exibe imagens recebidas. Testado de ponta a ponta contra MinIO real (bytes servidos conferidos).
- WhatsApp Cloud API oficial (Meta for Developers) no mesmo adapter do 360dialog: connect pede `phone_number_id`/`access_token`/`app_secret`, webhook ganha GET de verificação (`hub.challenge`) e passa a exigir assinatura `X-Hub-Signature-256` quando o canal tem app_secret; envio via `graph.facebook.com/{phone_number_id}/messages` com Bearer. Permite testar com o número gratuito de teste da Meta antes de contratar BSP.
- Busca global (`GET /api/search`): full-text em português (tsvector + índices GIN) sobre mensagens e contatos, escopada pelo RLS; no dashboard, campo de busca com debounce que abre a conversa direto do resultado.
- Notas internas em conversas: visíveis só para a equipe, com autor e horário, notificando os dashboards em tempo real; painel recolhível na tela da conversa.
- Canais Meta (Fase 2): Instagram e Messenger via webhook único `/api/meta/webhook/{channelId}` — GET de verificação (`hub.challenge` com verify_token gerado no connect) e POST autenticado por assinatura `X-Hub-Signature-256` (HMAC do corpo bruto com o app_secret); ecos da própria página ignorados; envio via Graph API `/me/messages`. Testado offline com assinaturas reais e Graph simulado.
- Adapter de WhatsApp via BSP 360dialog: webhook `POST /api/whatsapp/webhook/{channelId}/{token}` (token secreto por canal gerado no connect e exibido uma única vez — BSPs não assinam requisições) parseia o formato Cloud API; envio via `D360-API-KEY` com `base_url` configurável. Testado offline com BSP simulado; falta apenas plugar credenciais reais.
- Inbox ao vivo: WebSocket `/ws` com JWT validado no handshake (mesmo decoder do resource server — token de desafio MFA recusado), push por tenant a cada mutação de conversa após commit; dashboard invalida as queries na hora e mantém polling relaxado como rede de segurança.
- Formulário de credenciais IMAP/SMTP na tela Canais (webchat deixou de exigir credenciais).
- Canal de e-mail: poller IMAP agendado varre as caixas de todos os canais EMAIL ativos (multi-tenant, falha isolada por canal), normaliza para o inbox com dedup por Message-ID; resposta do agente sai por SMTP com as credenciais do próprio canal. Testado de ponta a ponta offline com Greenmail.

## [0.2.0] — 2026-07-08

MVP self-contained completo (Fase 1 parcial): inbox, IA auditável, webchat com widget e CRM.

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
