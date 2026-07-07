# Secrets de CI/CD

## Hoje (Fase 0)

O pipeline atual (build + testes + validação de imagem) **não precisa de nenhum secret**: os testes usam Testcontainers com bancos efêmeros e nenhuma API externa é chamada.

Nunca adicione chaves reais de IA/canais ao CI para "testar de verdade" — testes que dependem de API externa pertencem a uma suíte separada, manual ou agendada, com conta de sandbox.

## Quando o deploy chegar (Fase 1+)

| Secret | Uso | Quando criar |
|---|---|---|
| `REGISTRY_USERNAME` / `REGISTRY_TOKEN` | Push da imagem Docker para o registry | Primeiro deploy automatizado |
| `DEPLOY_TOKEN` (ou credencial da plataforma) | Deploy no Railway/Render/Fly.io | Primeiro deploy automatizado |
| `SENTRY_AUTH_TOKEN` | Upload de source maps/releases no Sentry | Ao ativar Sentry |
| `E2E_ANTHROPIC_API_KEY` (sandbox) | Suíte E2E agendada com IA real (nunca no CI de PR) | Fase 1, suíte E2E |

Criar via: `gh secret set NOME --repo RafaelReis22/omnichannel-ai`

Segredos de runtime (banco, RabbitMQ, chaves de IA de produção) **não vivem no GitHub** — vivem no gerenciador de segredos da plataforma de deploy (docs/06-seguranca.md).
