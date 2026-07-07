# Stack Tecnológica

## Princípio de escolha

TypeScript de ponta a ponta (frontend e backend) para reduzir custo de coordenação de uma equipe pequena, e para permitir compartilhar tipos entre a API e o dashboard. Preferência por serviços gerenciados no início (velocidade de entrega do MVP) com caminho explícito de migração para infraestrutura própria quando a escala justificar — nunca o contrário.

## Frontend

| Camada | Escolha | Por quê |
|---|---|---|
| Framework | Next.js 15 (App Router) + React 19 | SSR para o dashboard, ecossistema maduro, mesma linguagem do backend |
| Estado de servidor | TanStack Query | Cache e revalidação de dados remotos sem reinventar |
| Estado de cliente | Zustand | Estado de UI local (painéis abertos, filtros) sem boilerplate |
| Estado de URL | Search params nativos do Next.js | Filtros de inbox, aba ativa e paginação são deep-linkáveis |
| Estilo | Tailwind CSS + componentes próprios (base shadcn/ui, fortemente customizada) | Evitar visual de template genérico — dashboard de atendimento precisa de hierarquia clara entre estado urgente/normal, não um CRUD-by-numbers |
| Tempo real | WebSocket (Socket.IO) para atualização de inbox ao vivo | Mensagens novas e status de agente precisam refletir sem polling |
| Formulários | React Hook Form + Zod | Validação client-side consistente com a validação de schema do backend |

## Backend

| Camada | Escolha | Por quê |
|---|---|---|
| Framework | NestJS (Node.js/TypeScript) | Estrutura modular com injeção de dependência nativa — alinha com o desenho de módulos da arquitetura |
| API | REST (OpenAPI autogerado) no MVP; GraphQL avaliado só se a Fase 4 (API pública) mostrar necessidade real | REST é suficiente e mais simples de documentar/consumir por integradores externos |
| Filas / jobs assíncronos | BullMQ sobre Redis | Processamento de IA, envio de mensagens e webhooks são assíncronos por natureza |
| Tempo real (servidor) | Gateway WebSocket do próprio NestJS | Mesma stack, sem serviço extra no MVP |
| Autenticação | Auth.js (NextAuth) para sessão, com providers próprios de e-mail/senha; avaliar Clerk/WorkOS quando SSO enterprise virar requisito (Fase 4+) | Evita reescrever autenticação ao entrar em enterprise |
| Autorização | CASL (regras de permissão declarativas) | Permite RBAC simples no MVP e evolução para permissões granulares na Fase 4 sem reescrever o motor |

## Dados

| Necessidade | Escolha | Por quê |
|---|---|---|
| Banco relacional (fonte de verdade) | PostgreSQL | Suporte nativo a Row-Level Security (isolamento multi-tenant), extensível, maduro |
| Multi-tenancy | Schema único + `tenant_id` em toda tabela + RLS no Postgres | Mais simples de operar no início que schema-por-tenant; migração para isolamento físico por tenant fica disponível para contas enterprise que exigirem (Fase 4+) |
| Busca vetorial (RAG) | Extensão `pgvector` no mesmo Postgres | Evita operar um banco extra no MVP; migrar para Qdrant/Weaviate só se latência ou volume de embeddings exigir |
| Cache / sessão / fila | Redis | Padrão de mercado, suporta BullMQ e pub/sub para WebSocket |
| Busca textual (global search) | `tsvector` nativo do Postgres no MVP; Meilisearch como upgrade | Adiar operar mais um serviço até o volume de dados exigir |
| Armazenamento de mídia | Object storage compatível com S3 (Cloudflare R2 no início — custo de egress menor) | Imagens, PDFs, vídeos e documentos enviados/recebidos pela IA e por agentes |
| ORM / migrations | Prisma | Tipagem compartilhada com o TypeScript do backend, migrations versionadas |

### Modelo de dados — entidades centrais

```text
tenants
  id, nome, plano, criado_em

users
  id, tenant_id, nome, email, papel, criado_em

channels
  id, tenant_id, tipo (whatsapp|instagram|messenger|email|webchat|telegram),
  credenciais_criptografadas, status, criado_em

contacts
  id, tenant_id, nome, telefone, email, tags[], criado_em

conversations
  id, tenant_id, contact_id, channel_id, status (aberta|ia|humano|resolvida),
  responsavel_user_id, criado_em, atualizado_em

messages
  id, conversation_id, remetente (contato|ia|agente), tipo (texto|imagem|pdf|video|documento),
  conteudo, anexo_url, confianca_ia, criado_em

knowledge_base_documents
  id, tenant_id, titulo, conteudo, arquivo_url, criado_em

knowledge_base_embeddings
  id, document_id, chunk_texto, embedding (vector), criado_em

deals (pipeline de vendas)
  id, tenant_id, contact_id, estagio, valor, criado_em, atualizado_em

resolution_ledger (livro-razão de resolução)
  id, conversation_id, message_id, confianca, acao, revisado_por_humano, criado_em

audit_logs
  id, tenant_id, user_id, acao, entidade, entidade_id, metadata, criado_em

webhooks
  id, tenant_id, url, eventos[], segredo, ativo

api_keys
  id, tenant_id, chave_hash, escopos[], criado_em, revogado_em
```

Toda tabela de negócio carrega `tenant_id` e é protegida por política de RLS que restringe leitura/escrita ao tenant da sessão autenticada — a isolação de dados entre clientes não depende só da aplicação respeitar a regra, o banco impõe.

## IA e canais

| Necessidade | Escolha | Por quê |
|---|---|---|
| Modelo de linguagem principal | Claude (Anthropic), via camada de abstração própria (`LLM Gateway`) | Qualidade de raciocínio e seguimento de instrução para autonomia com portão de confiança |
| Fallback / segundo modelo | GPT (OpenAI) atrás do mesmo gateway | Redundância se o provedor principal cair ou throttle; base para o diferencial futuro de multi-LLM exposto ao cliente (Fase 5) |
| Embeddings (RAG) | Modelo de embedding do próprio provedor de LLM escolhido, indexado em `pgvector` | Evita operar infraestrutura de ML própria no início |
| WhatsApp | BSP (Business Solution Provider) homologado (ex.: 360dialog ou Gupshup) no MVP; avaliar Cloud API direta da Meta quando o volume justificar o custo operacional adicional | BSP acelera o onboarding e resolve homologação de número, que é o maior gargalo de tempo em integrações WhatsApp |
| Instagram / Messenger | Meta Graph API (Messenger Platform) | API oficial única para ambos os canais |
| E-mail | IMAP/SMTP para ingestão + provedor transacional (ex.: Postmark) para envio confiável | Separar "ler caixa do cliente" de "enviar resposta" evita problemas de entregabilidade |
| Telegram (Fase 4) | Bot API oficial | Simples e gratuita, sem intermediário necessário |

## Observabilidade

| Necessidade | Escolha |
|---|---|
| Logs estruturados | Pino (Node.js) → agregador (ver [deploy](07-deploy.md)) |
| Métricas e tracing | OpenTelemetry, exportado para um backend gerenciado (Grafana Cloud ou equivalente) |
| Erros de aplicação | Sentry |
