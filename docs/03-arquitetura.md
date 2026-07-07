# Arquitetura

## Visão geral

Modelo inicial: **monólito modular** em vez de microsserviços desde o dia 1. A justificativa é operacional, não técnica: com uma equipe pequena, microsserviços adicionam custo de coordenação antes de existir carga que os justifique. Os módulos são desenhados com fronteiras claras (ver "limites de módulo" abaixo) para que a extração futura de um serviço (ex.: processamento de IA, ingestão de canais) seja um refactor, não uma reescrita.

```text
                              ┌─────────────────────────┐
                              │   Canais externos        │
                              │  WhatsApp · Instagram ·   │
                              │  Messenger · E-mail ·     │
                              │  Webchat · Telegram*      │
                              └────────────┬─────────────┘
                                           │ webhooks / polling
                                           ▼
                              ┌─────────────────────────┐
                              │  Channel Gateway          │
                              │  (adapters por canal,     │
                              │   normaliza para evento   │
                              │   de mensagem interno)    │
                              └────────────┬─────────────┘
                                           │ fila (BullMQ/Redis)
                                           ▼
        ┌───────────────────────────────────────────────────────────┐
        │                        API / Core                          │
        │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌────────────┐  │
        │  │ Inbox      │ │ CRM/      │ │ Admin/     │ │ Analytics  │  │
        │  │ Module     │ │ Pipeline  │ │ Tenant     │ │ Module     │  │
        │  └───────────┘ └───────────┘ └───────────┘ └────────────┘  │
        └───────────────────────────┬─────────────────────────────────┘
                                     │ fila
                                     ▼
                       ┌─────────────────────────┐
                       │   AI Worker               │
                       │  (RAG, confiança,         │
                       │   handoff, resumo,        │
                       │   qualificação)           │
                       └────────────┬─────────────┘
                                    │
                     ┌──────────────┼──────────────┐
                     ▼              ▼              ▼
              ┌───────────┐  ┌────────────┐  ┌──────────────┐
              │ LLM Gateway│  │ Vector Store│  │ Object Storage│
              │(Claude/GPT)│  │ (pgvector)  │  │ (mídia/docs)  │
              └───────────┘  └────────────┘  └──────────────┘

                     Transversal: Postgres (fonte de verdade) · Redis
                     (fila/cache/sessão) · Observabilidade · Auditoria
```
`*Telegram entra na Fase 4.`

## Limites de módulo (para extração futura em serviços)

| Módulo | Responsabilidade | Candidato a extração quando |
|---|---|---|
| Channel Gateway | Normalizar eventos de cada canal em um formato interno único; lidar com rate limits e reconexão | Volume de mensagens por canal justificar escala independente do resto |
| Inbox | Conversas, atribuição, histórico, busca | Raramente extraído sozinho — cresce junto do core |
| AI Worker | RAG, geração de resposta, confiança, resumo, qualificação | Primeiro candidato a serviço separado — é o que mais consome CPU/latência de LLM e mais se beneficia de escalar independente |
| CRM/Pipeline | Contatos, negócios, tags | Extrai junto com Inbox se necessário |
| Admin/Tenant | Autenticação, RBAC, configuração, auditoria | Fica no core até haver motivo forte de isolamento (ex.: exigência de compliance) |
| Analytics | Agregações e dashboards | Extrai para um serviço de leitura (read model) quando volume de eventos justificar CQRS completo |

## Padrões de projeto

- **Adapter** — cada canal (WhatsApp, Instagram, e-mail...) implementa uma interface comum `ChannelAdapter` (receber, enviar, normalizar mídia). Novo canal = novo adapter, sem tocar no resto do sistema.
- **Strategy** — política de autonomia da IA (quando agir sozinha vs. pedir handoff) é uma estratégia configurável por tenant e por tipo de ação, não uma regra fixa no código.
- **Repository** — acesso a dados encapsulado atrás de interfaces (`ConversationRepository`, `ContactRepository`...); a lógica de negócio nunca monta queries diretamente. Facilita testes com mocks e eventual troca de storage.
- **Outbox pattern** — toda alteração que precisa disparar um webhook externo é gravada na mesma transação que o dado de negócio, e um processo separado garante a entrega — evita perder eventos se o webhook falhar no meio da requisição.
- **Event-driven interno** — mudanças relevantes (nova mensagem, conversa resolvida, negócio movido de estágio) publicam eventos de domínio internos; módulos como Analytics e Auditoria consomem esses eventos em vez de serem chamados diretamente, reduzindo acoplamento.
- **Circuit breaker** — chamadas a APIs externas (Meta, provedores de e-mail, LLM) são protegidas por circuit breaker, para que uma instabilidade externa (ex.: rate limit do WhatsApp) não derrube o restante do sistema.
- **CQRS leve** — leituras de alto volume (lista de conversas, dashboards) usam modelos de leitura otimizados/desnormalizados, separados do modelo de escrita transacional, sem adotar CQRS completo com event sourcing.

## Fluxo crítico: mensagem recebida → resposta da IA

1. Canal recebe mensagem → Channel Gateway normaliza e publica evento `message.received` na fila.
2. Core grava mensagem na conversa (Postgres) e atualiza o inbox em tempo real (WebSocket) para agentes conectados.
3. Se a conversa está em modo IA, AI Worker consome o evento: busca contexto (histórico + RAG na base de conhecimento) e decide uma ação.
4. AI Worker calcula confiança da ação. Acima do limiar configurado → responde diretamente (e registra no Livro-razão de resolução). Abaixo do limiar → aciona handoff, gera resumo e notifica um agente humano.
5. Resposta (texto e/ou mídia) volta pelo Channel Gateway até o canal de origem.

Este fluxo é o motivo pelo qual o Channel Gateway e o AI Worker são desenhados como componentes assíncronos desacoplados por fila desde o início — é o caminho mais provável de precisar escalar de forma independente do resto do sistema.
