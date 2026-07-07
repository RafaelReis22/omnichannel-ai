# omnichannel-ai — contexto do projeto

Plataforma SaaS de atendimento omnichannel com IA (WhatsApp, Instagram, Messenger, e-mail, webchat; Telegram futuro), multi-tenant, para PME/mid-market Brasil/LatAm primeiro.

**Fonte de verdade**: `README.md` (índice) e `docs/01` a `09`. Decisões técnicas revisadas viram ADR em `docs/adr/`. Divergência entre docs e código sempre se resolve — atualizando o doc (decisão deliberada) ou o código (desvio).

## Stack

- **Backend**: Java 21 + Spring Boot 3.5.x + Spring Modulith, em `backend/`. Maven.
- **Frontend**: Next.js 15 + React 19 + TypeScript, em `frontend/` (ainda não criado).
- **Dados**: Postgres 16 + RLS multi-tenant + pgvector (RAG). Redis (cache). RabbitMQ (filas).
- **IA**: Spring AI — Claude principal, GPT fallback.
- Detalhes e justificativas: `docs/04-stack-tecnologica.md` e `docs/adr/ADR-0001`.

## Comandos

```bash
# backend
cd backend && ./mvnw verify          # build + testes + verificação de arquitetura
cd backend && ./mvnw spring-boot:run # rodar local (exige docker compose up)
docker compose up -d                  # Postgres + Redis + RabbitMQ locais
```

## Regras permanentes (valem para todo o projeto)

1. Nunca gere código duplicado — extraia e reutilize.
2. Sempre refatore quando necessário; não acumule dívida silenciosa.
3. Sempre mantenha a documentação atualizada junto com o código, na mesma PR.
4. Sempre mantenha os testes funcionando — PR com teste quebrado não mescla.
5. Commits pequenos, um por unidade lógica. Nunca um commit gigante.
6. Conventional Commits: `feat:` `fix:` `docs:` `refactor:` `test:` `style:` `build:` `ci:` `perf:` `chore:`.
7. Arquitetura consistente: módulos Spring Modulith (`com.omnichannel.<modulo>`), um módulo nunca importa internals de outro.
8. Priorize legibilidade sobre esperteza.
9. Pense como desenvolvedor sênior: questione requisitos estranhos, sugira melhorias quando encontrar oportunidades.
10. Toda decisão considera segurança, performance e escalabilidade — mas sem overengineering (gatilhos de escala documentados em `docs/05-escalabilidade.md`).

## Regras específicas

- Toda tabela de negócio tem `tenant_id` + política RLS. Teste de isolamento multi-tenant é obrigatório para qualquer repositório novo.
- Toda resposta autônoma da IA gera registro no `resolution_ledger` — é requisito funcional, teste falha sem isso.
- Segredos nunca em código; `.env.example` atualizado sempre que uma variável nova surgir.
- Migrations Flyway são expand-and-contract (retrocompatíveis por pelo menos uma versão).
- Validação Bean Validation em toda borda de entrada; nunca confiar só no frontend.
