# omnichannel-ai

> **Quer só VER funcionando?** Rode `./demo.sh` e siga o [COMO-TESTAR.md](COMO-TESTAR.md) — 1 comando, duas telas, IA respondendo.

> Plataforma SaaS de atendimento omnichannel com IA. Nome comercial ainda em aberto — trocar antes do lançamento.

Empresas conectam WhatsApp, Instagram, Messenger, e-mail e chat do site em um único painel; uma IA com RAG responde clientes, qualifica leads e transfere para humanos com transparência auditável — o [diferencial central](docs/01-visao-e-produto.md#diferenciais) frente aos 20 concorrentes pesquisados.

## Stack

**Backend** Java 21 + Spring Boot 3.5 (Spring Modulith, Spring AI, Spring AMQP) · **Frontend** Next.js 15 + TypeScript · **Dados** Postgres 16 (RLS multi-tenant + pgvector), Redis, RabbitMQ · **IA** Claude (principal) + GPT (fallback) via Spring AI — justificativas em [ADR-0001](docs/adr/ADR-0001-backend-java-spring-boot.md).

## Desenvolvimento local

```bash
docker compose up -d                # Postgres (host 5433) + Redis (host 6380) + RabbitMQ
cd backend && ./mvnw verify         # build + testes + verificação de arquitetura
cd backend && ./mvnw spring-boot:run    # exige JWT_SECRET, chaves AES e chaves de IA no ambiente
cd frontend && npm install && npm run dev   # dashboard em http://localhost:3000
```

Variáveis de ambiente: copie `.env.example` para `.env` e preencha (idem `frontend/.env.example`). Regras e armadilhas conhecidas: [CLAUDE.md](CLAUDE.md) · Como contribuir: [CONTRIBUTING.md](CONTRIBUTING.md).

## Documentação

| Documento | Conteúdo |
|---|---|
| [01-visao-e-produto.md](docs/01-visao-e-produto.md) | Visão, missão, público-alvo, diferenciais |
| [02-plano-de-produto.md](docs/02-plano-de-produto.md) | MVP, backlog, roadmap por fases |
| [03-arquitetura.md](docs/03-arquitetura.md) | Arquitetura de sistema, padrões de projeto |
| [04-stack-tecnologica.md](docs/04-stack-tecnologica.md) | Stack, banco de dados, integrações de canais e IA |
| [05-escalabilidade.md](docs/05-escalabilidade.md) | Estratégia de escalabilidade |
| [06-seguranca.md](docs/06-seguranca.md) | Estratégia de segurança e compliance |
| [07-deploy.md](docs/07-deploy.md) | Estratégia de deploy e infraestrutura |
| [08-testes.md](docs/08-testes.md) | Estratégia de testes |
| [09-documentacao.md](docs/09-documentacao.md) | Como a documentação é mantida |
| [10-teste-sem-custo.md](docs/10-teste-sem-custo.md) | Testar tudo de graça: Meta for Developers + Ollama + MinIO |
| [docs/adr/](docs/adr/) | Decisões arquiteturais (ADRs) |

A pesquisa de mercado que fundamenta o plano (Zendesk, Intercom, HubSpot, Freshdesk, Gorgias, Respond.io, ManyChat, Tidio, Crisp, Front, LiveChat, Salesforce, Microsoft Dynamics, Zoho Desk, Drift, Ada, Decagon, Sierra, Forethought, Kustomer, Help Scout e players LatAm) está resumida no contexto de cada documento.

## Status

Fase atual: **Fase 0 — fundação** ([roadmap](docs/02-plano-de-produto.md#roadmap)): estrutura do repositório, esqueleto do backend, CI e infraestrutura local.

## Licença

Proprietária — todos os direitos reservados. Ver [LICENSE](LICENSE).
