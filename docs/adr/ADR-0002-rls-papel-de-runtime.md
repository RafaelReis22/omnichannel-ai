# ADR-0002 — Isolamento multi-tenant via RLS com papel de runtime (`SET ROLE`)

**Status:** aceito · **Data:** 2026-07-06

## Contexto

O plano exige que o isolamento entre tenants seja imposto pelo banco (RLS), não apenas pela aplicação ([06-seguranca.md](../06-seguranca.md)). Dois problemas práticos:

1. **Superusuários ignoram RLS.** O usuário do Postgres em dev (docker-compose), em teste (Testcontainers) e frequentemente em produção gerenciada é dono das tabelas ou superusuário — políticas com `FORCE ROW LEVEL SECURITY` ainda são ignoradas por quem tem `BYPASSRLS`.
2. **O fluxo de autenticação precisa consultar por e-mail antes de existir tenant na sessão** (login/signup/refresh), o que uma política puramente por tenant impediria.

## Decisão

1. A migration V3 cria o papel **`app_runtime`** (`NOLOGIN NOSUPERUSER NOBYPASSRLS`) e o concede ao usuário de conexão. O `TenantAwareDataSource` executa `SET ROLE app_runtime` ao retirar uma conexão do pool **quando há contexto de tenant ou de autenticação**, e `RESET ROLE`/`RESET` das GUCs na devolução. Com o papel assumido, o RLS vale integralmente — o mesmo mecanismo em dev, teste e produção, sem credenciais separadas.
2. As políticas leem a GUC `app.tenant_id` (`NULLIF(current_setting('app.tenant_id', true), '')::uuid`): sem contexto, **nenhuma linha é visível** (negar por padrão).
3. Uma segunda política (`auth_context`) libera as tabelas de identidade apenas quando `app.auth_context = 'on'` — definida exclusivamente por `TenantContext.runAsAuthProcess(...)`, usada só pelo `AuthService`. Como as transações abrem a conexão no início, o `AuthService` usa `TransactionTemplate` *dentro* do modo auth (com `@Transactional` a conexão seria preparada antes do contexto existir).

## Consequências

- (+) Isolamento testável e idêntico em todos os ambientes; testes de RLS provam a garantia com o banco real (Testcontainers).
- (+) Um único usuário de conexão: migrations Flyway rodam como dono (sem contexto → conexão intocada), runtime roda como `app_runtime`.
- (−) Conexões **sem** contexto continuam com os privilégios do usuário de login (necessário para Flyway). Caminhos de negócio sempre têm contexto (interceptor JWT ou modo auth); hardening futuro: usuário de login dedicado sem ownership para o runtime (Fase 4, junto do pacote enterprise).
- (−) `SET ROLE`/`RESET` adiciona um round-trip por checkout de conexão com contexto. Aceitável; revisar apenas se aparecer nos gatilhos de latência de [05-escalabilidade.md](../05-escalabilidade.md).
