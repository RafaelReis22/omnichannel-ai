# Estratégia de Testes

## Meta de cobertura

Mínimo de 80% em código de negócio (módulos de conversas, IA, CRM, tenant/permissões). Cobertura não é o objetivo em si — é o proxy para garantir que os fluxos críticos abaixo estejam realmente testados.

## Pirâmide de testes

| Tipo | Ferramenta | Escopo |
|---|---|---|
| Unitário | Vitest | Funções puras, regras de negócio isoladas (cálculo de confiança, roteamento de fila, transformação de payload de canal) |
| Integração | Vitest + banco de teste (Postgres via container) | Repositórios, RLS multi-tenant (garantir que um tenant nunca lê dado de outro), filas (BullMQ) |
| Contrato de canal | Mocks dos webhooks de cada provedor (Meta, BSP de WhatsApp, provedor de e-mail) | Garantir que o `ChannelAdapter` de cada canal normaliza corretamente eventos reais documentados pela API do provedor, sem depender da API real no CI |
| E2E | Playwright | Fluxos críticos de ponta a ponta (ver abaixo) |
| Carga | k6 | Validar os gatilhos de escala definidos em [escalabilidade](05-escalabilidade.md) antes de precisar deles em produção |

## Fluxos críticos cobertos por E2E

1. Login e onboarding de um novo tenant.
2. Conectar um canal (WhatsApp em modo sandbox/teste) e receber uma mensagem real no inbox.
3. IA responder autonomamente uma pergunta coberta pela base de conhecimento (RAG) e registrar no Livro-razão de resolução.
4. IA acionar handoff para humano quando abaixo do limiar de confiança, com resumo gerado corretamente.
5. Criar negócio no pipeline a partir de uma conversa e movê-lo entre estágios.
6. Isolamento multi-tenant: usuário do tenant A não consegue, por nenhum caminho de UI ou API, visualizar dado do tenant B.

## Testes específicos de IA

- **Regressão de qualidade de resposta**: conjunto de perguntas/respostas de referência (golden set) por tenant piloto, reexecutado a cada mudança de prompt ou de modelo, comparando confiança e conteúdo contra a linha de base.
- **Teste de portão de confiança**: casos sintéticos desenhados para ficar deliberadamente abaixo do limiar, garantindo que o handoff realmente aciona e nunca a IA age com confiança insuficiente.
- **Auditoria do Livro-razão**: todo teste de resposta autônoma verifica que um registro correspondente foi criado — se o registro não existir, o teste falha, porque a transparência é um requisito funcional, não um nice-to-have.

## Disciplina de execução

- TDD nos módulos de regra de negócio (confiança da IA, RBAC, pipeline): teste primeiro, depois implementação.
- Testes rodam no CI a cada PR; PR não é mesclável com testes quebrados ou cobertura abaixo da meta nos módulos afetados.
- Falha de teste é tratada como sinal para corrigir a implementação, não o teste — exceto quando a investigação confirma que o teste está desatualizado em relação ao comportamento correto e intencional.
