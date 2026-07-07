# Contribuindo

## Fluxo de trabalho

1. Crie uma branch a partir de `main`: `feature/<fase>-<descricao>` (ex.: `feature/fase-0-auth`), `fix/<descricao>` ou `docs/<descricao>`.
2. Commits pequenos, um por unidade lógica — nunca um commit gigante.
3. Antes de qualquer push, rode localmente: build, testes e lint (o pipeline não é o lugar de descobrir que quebrou).
4. Abra PR para `main` usando o template. PR só é mesclável com CI verde.

## Conventional Commits

Formato: `<tipo>: <descrição em português, minúscula, imperativo>`

| Tipo | Uso |
|---|---|
| `feat` | Nova funcionalidade |
| `fix` | Correção de bug |
| `docs` | Só documentação |
| `refactor` | Mudança de código sem mudar comportamento |
| `test` | Adição/ajuste de testes |
| `style` | Formatação, sem mudança de lógica |
| `build` | Build, dependências, Docker |
| `ci` | Pipelines e automação |
| `perf` | Melhoria de performance |
| `chore` | Manutenção que não se encaixa acima |

Escopo opcional quando ajudar: `feat(inbox): ...`, `fix(channels): ...`.

## Padrões de código

- **Java**: siga a organização de módulos do Spring Modulith (`com.omnichannel.<modulo>`) — um módulo nunca importa internals de outro; a verificação de arquitetura falha o build se isso acontecer.
- **Regras permanentes do projeto**: ver [CLAUDE.md](CLAUDE.md) — valem para humanos e para assistentes de IA.
- Testes acompanham o código na mesma PR; cobertura mínima de 80% nos módulos de negócio ([estratégia de testes](docs/08-testes.md)).
- Toda decisão arquitetural relevante vira um ADR em `docs/adr/`.

## Segurança

- Nunca commitar segredos — use `.env` (ignorado) e mantenha o `.env.example` atualizado.
- Mudanças em autenticação, permissões, canais ou dados de cliente exigem atenção redobrada de revisão ([estratégia de segurança](docs/06-seguranca.md)).
