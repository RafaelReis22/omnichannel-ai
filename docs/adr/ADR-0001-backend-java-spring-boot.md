# ADR-0001 — Backend em Java 21 + Spring Boot (substitui NestJS/TypeScript)

**Status:** aceito · **Data:** 2026-07-06

## Contexto

O plano original ([04-stack-tecnologica.md](../04-stack-tecnologica.md)) previa TypeScript de ponta a ponta (NestJS no backend) para reduzir custo de coordenação. Antes de qualquer código ser escrito, o fundador definiu que domina Java profundamente (projeto anterior: backend Java com 9 módulos) e prefere Java no backend.

## Decisão

Backend em **Java 21 LTS + Spring Boot 3.5.x**, mantendo o frontend em Next.js/TypeScript.

Escolhas derivadas:

| Papel | Antes (plano NestJS) | Agora | Por quê |
|---|---|---|---|
| Framework | NestJS | Spring Boot 3.5.16 | Geração madura e suportada; Boot 4.x tem meses de vida — cedo demais para fundação. Migração para 4.x fica para quando o ecossistema (Spring AI/Modulith) estabilizar nela |
| Fronteiras de módulo | Módulos NestJS | **Spring Modulith** | Impõe as fronteiras do monólito modular em tempo de teste (`ApplicationModules.verify()`), mantendo a extração futura de serviços como refactor |
| Filas | BullMQ (Redis, Node-only) | **RabbitMQ + Spring AMQP** | BullMQ é ecossistema Node. RabbitMQ dá DLQ, retry e roteamento maduros sem o custo operacional de Kafka (overengineering neste estágio) |
| ORM/migrations | Prisma | **Spring Data JPA + Flyway** | Padrão do ecossistema; Flyway versiona migrations como o plano exigia |
| Autenticação | Auth.js | **Spring Security + JWT (access + refresh)** | Nativo do ecossistema; 2FA e SSO enterprise têm caminho conhecido |
| Autorização | CASL | **Spring Security method security + authorities por tenant/papel** | Mesmo modelo RBAC→granular do plano |
| IA/RAG | SDK direto + gateway próprio | **Spring AI 1.x** (Anthropic + OpenAI + PgVectorStore) | Abstração de provedor pronta — o "LLM Gateway" do plano vira configuração, não código próprio; RAG sobre pgvector já suportado |
| Logs | Pino | **Logback + logstash-encoder (JSON)** | Logs estruturados equivalentes |
| Métricas | OpenTelemetry (Node) | **Micrometer + OpenTelemetry** | Mesmo destino (Grafana/Prometheus), instrumentação nativa do Spring |

## O que não muda

Postgres + RLS + pgvector, Redis (cache/sessão), object storage S3-compatível, Next.js 15 no frontend, arquitetura de monólito modular com Channel Gateway/Core/AI Worker desacoplados por fila, e todos os gatilhos de escalabilidade do plano.

## Consequências

- (+) Velocidade e qualidade do código refletem o domínio real do fundador — o critério que mais importa numa equipe de uma pessoa.
- (+) Ecossistema Spring cobre RAG (Spring AI), fronteiras de módulo (Modulith) e filas (AMQP) sem dependências exóticas.
- (−) Dois ecossistemas no monorepo (Java + TS): CI e tooling duplicados. Aceito — o frontend seria TS de qualquer forma.
- (−) Tipos não são compartilháveis entre backend e frontend; contrato passa a ser o OpenAPI gerado (springdoc), consumido pelo frontend via geração de client.
