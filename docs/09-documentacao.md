# Estratégia de Documentação

## Este repositório é a fonte de verdade

Os documentos em `docs/` são a documentação principal do projeto, definida antes do início do desenvolvimento. Eles devem ser atualizados junto com o código, não depois — uma mudança de arquitetura ou de escopo de MVP que não atualiza o documento correspondente é considerada incompleta.

## Estrutura

| Onde | O quê |
|---|---|
| `README.md` | Ponto de entrada, resumo de uma frase, índice |
| `docs/01` a `docs/09` | Este conjunto de documentos — visão, produto, arquitetura, stack, escalabilidade, segurança, deploy, testes, documentação |
| `docs/adr/` (a criar no início do desenvolvimento) | Architecture Decision Records — um arquivo por decisão técnica relevante (ex.: "por que Postgres+pgvector em vez de um vector store dedicado no MVP") |
| `CLAUDE.md` (a criar no início do desenvolvimento) | Contexto de codebase para assistência de IA durante o desenvolvimento — convenções, comandos de build/test, estrutura de pastas |
| OpenAPI/Swagger (autogerado do código, Fase 4) | Documentação da API pública, gerada a partir das annotations do springdoc no código Spring, nunca escrita manualmente em paralelo ao código |

## Quando criar um ADR

Sempre que uma decisão deste plano for revista durante o desenvolvimento (ex.: trocar BSP de WhatsApp, migrar de `pgvector` para um vector store dedicado antes do gatilho previsto em [escalabilidade](05-escalabilidade.md)). O ADR registra o contexto, a alternativa descartada e o motivo — para que a decisão não pareça arbitrária a quem ler o código seis meses depois.

## Manutenção

- Cada fase do [roadmap](02-plano-de-produto.md#roadmap) concluída é o gatilho para revisar se os documentos de arquitetura, stack e segurança ainda refletem a realidade do sistema construído.
- Divergência entre o que este plano descreve e o que o código faz deve ser resolvida atualizando o documento (se a mudança foi uma decisão deliberada) ou o código (se foi um desvio não intencional) — nunca deixada sem resolução.
