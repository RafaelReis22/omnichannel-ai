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

## Verificação obrigatória antes de declarar pronto

Teste de backend verde NÃO significa produto funcionando. Toda entrega com
superfície de UI exige, antes de ser declarada pronta:
1. `cd frontend && node audit.mjs` — navega o app como usuário (Playwright),
   tira screenshots em `/tmp/audit-*.png` e imprime problemas detectados.
2. **Olhar os screenshots** (não só o output) — hierarquia, contraste, dark mode.
3. Ampliar o `audit.mjs` a cada fluxo novo (feature nova = passo novo no script).
Regra nascida de bugs reais que os testes de integração não pegam (UI que
descarta dados da API, informação ausente na tela).

## Armadilhas conhecidas (aprendidas em produção de código)

- **OSIV é proibido** (`spring.jpa.open-in-view: false`): ele segura a conexão pela requisição inteira e a conexão preparada pelo primeiro contexto (auth) seria reusada pelos seguintes (tenant), quebrando o RLS silenciosamente. Há um guard no `TenancyConfig` que falha o startup se religado.
- **Nunca criar `src/test/resources/application.yml|properties`**: arquivo com nome base "application" sombreia o `application.yml` principal inteiro (sem merge). Overrides de teste vivem em `application-test.properties` (profile `test`, ativado pelo surefire).
- **Renomeou/deletou resource? `./mvnw clean`**: o Maven não remove de `target/` recursos deletados do source — testes passam/falham contra arquivos fantasma.

## Regras específicas

- Toda tabela de negócio tem `tenant_id` + política RLS. Teste de isolamento multi-tenant é obrigatório para qualquer repositório novo.
- Toda resposta autônoma da IA gera registro no `resolution_ledger` — é requisito funcional, teste falha sem isso.
- Segredos nunca em código; `.env.example` atualizado sempre que uma variável nova surgir.
- Migrations Flyway são expand-and-contract (retrocompatíveis por pelo menos uma versão).
- Validação Bean Validation em toda borda de entrada; nunca confiar só no frontend.
