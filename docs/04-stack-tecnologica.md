# Stack Tecnológica

> Revisada por [ADR-0001](adr/ADR-0001-backend-java-spring-boot.md): backend em Java (domínio do fundador), frontend em TypeScript.

## Princípio de escolha

Tecnologias modernas e estáveis, com preferência pelo que a equipe domina profundamente — velocidade e qualidade de execução valem mais que uniformidade de linguagem. Serviços gerenciados no início (velocidade de entrega do MVP) com caminho explícito de migração para infraestrutura própria quando a escala justificar.

## Backend

| Camada | Escolha | Por quê |
|---|---|---|
| Linguagem/Runtime | Java 21 LTS (Temurin) | LTS atual com virtual threads (Loom) — concorrência barata para I/O intenso (canais, LLM) sem programação reativa |
| Framework | Spring Boot 3.5.x | Geração madura do ecossistema; Boot 4.x recente demais para fundação (ver ADR-0001) |
| Fronteiras de módulo | Spring Modulith | Verifica em teste que módulos não se acoplam fora dos contratos — mantém a extração futura de serviços como refactor, não reescrita |
| API | REST + OpenAPI (springdoc autogerado) | Contrato consumido pelo frontend via client gerado; base da API pública da Fase 4 |
| Filas / assíncrono | RabbitMQ + Spring AMQP | DLQ, retry e roteamento maduros sem o custo operacional de Kafka (overengineering para este estágio — gatilho de troca documentado em [escalabilidade](05-escalabilidade.md)) |
| Tempo real | WebSocket (Spring) para inbox ao vivo | Mensagens novas e presença de agente sem polling |
| Autenticação | Spring Security + JWT (access curto + refresh com rotação) | 2FA para Admin no MVP; caminho conhecido para SSO/SAML enterprise (Fase 4+) |
| Autorização | Method security + authorities (tenant + papel) | RBAC simples no MVP evolui para permissões granulares sem trocar o motor |
| ORM / migrations | Spring Data JPA (Hibernate) + Flyway | Migrations versionadas, expand-and-contract para rollback seguro |
| Validação | Bean Validation (Jakarta) em toda borda de entrada | Fail-fast com mensagens claras; nunca confiar em validação só no frontend |

## Frontend

| Camada | Escolha | Por quê |
|---|---|---|
| Framework | Next.js 15 (App Router) + React 19 + TypeScript | SSR para dashboard, ecossistema maduro |
| Estado de servidor | TanStack Query | Cache e revalidação sem reinventar |
| Estado de cliente | Zustand | Estado de UI local sem boilerplate |
| Estado de URL | Search params nativos | Filtros de inbox e paginação deep-linkáveis |
| Estilo | Tailwind CSS + componentes próprios (base shadcn/ui fortemente customizada) | Dashboard de atendimento precisa de hierarquia urgente/normal clara, não template genérico |
| Formulários | React Hook Form + Zod | Validação client-side espelhando o contrato OpenAPI |
| Contrato com backend | Client TypeScript gerado do OpenAPI | Substitui o compartilhamento de tipos que existiria num monorepo full-TS |

## Dados

| Necessidade | Escolha | Por quê |
|---|---|---|
| Banco relacional | PostgreSQL 16+ | RLS nativo para isolamento multi-tenant, extensível, maduro |
| Multi-tenancy | Schema único + `tenant_id` + Row-Level Security | O banco impõe o isolamento, não só a aplicação; isolamento físico por tenant disponível para enterprise (Fase 4+) |
| Busca vetorial (RAG) | Extensão `pgvector` no mesmo Postgres | Evita operar banco extra no MVP; interface de repositório permite migrar para Qdrant/Weaviate se o gatilho de escala disparar |
| Cache / sessão | Redis | Padrão de mercado; também backing do rate limiting por tenant |
| Busca textual | `tsvector` nativo no MVP; Meilisearch como upgrade | Adiar operar mais um serviço até o volume exigir |
| Mídia | Object storage S3-compatível (Cloudflare R2) | Egress mais barato; SDK S3 padrão evita lock-in |

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
| Abstração de LLM | Spring AI 1.x | O "LLM Gateway" do plano vira configuração de provedor, não código próprio |
| Modelo principal | Claude (Anthropic) | Qualidade de raciocínio e seguimento de instrução para autonomia com portão de confiança |
| Fallback | GPT (OpenAI), atrás da mesma abstração | Redundância; base do multi-LLM exposto ao cliente (Fase 5) |
| Embeddings + RAG | Spring AI PgVectorStore | Chunking, embedding e retrieval integrados ao mesmo Postgres |
| WhatsApp | BSP homologado (360dialog ou Gupshup) no MVP | Resolve homologação de número — maior gargalo de tempo; Cloud API direta avaliada quando volume justificar |
| Instagram / Messenger | Meta Graph API | API oficial única para ambos |
| E-mail | IMAP/SMTP (ingestão) + provedor transacional (envio) | Separar leitura de entregabilidade |
| Telegram (Fase 4) | Bot API oficial | Simples, sem intermediário |

## Observabilidade

| Necessidade | Escolha |
|---|---|
| Logs estruturados | Logback + logstash-logback-encoder (JSON), correlacionados por `tenant_id` e `trace_id` |
| Métricas e tracing | Micrometer + OpenTelemetry → backend gerenciado (Grafana Cloud ou equivalente) |
| Erros de aplicação | Sentry |
| Health | Spring Boot Actuator (`/actuator/health`) usado pelo orquestrador |

## Infraestrutura (resumo — detalhes em [deploy](07-deploy.md))

- **Docker** multi-stage (build Maven → JRE 21 slim) desde o primeiro commit de código.
- **docker-compose** para desenvolvimento local (Postgres+pgvector, Redis, RabbitMQ).
- **Kubernetes**: alvo de produção quando sairmos da plataforma gerenciada — não no MVP (overengineering para uma equipe de uma pessoa sem carga real); o artefato já é container, então a migração é mudança de alvo de deploy, não de artefato.
- **CI/CD**: GitHub Actions (build → test → verificação Modulith → imagem Docker).
