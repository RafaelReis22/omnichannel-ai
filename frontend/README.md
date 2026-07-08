# frontend — dashboard omnichannel-ai

Next.js 16 (App Router) + React 19 + Tailwind 4 + TanStack Query.

```bash
cp .env.example .env.local   # aponta para a API (padrão http://localhost:8080)
npm install
npm run dev                  # http://localhost:3000
npm run build                # build de produção
```

Estrutura: `src/app` (rotas), `src/components` (por feature), `src/lib` (client de API + tipos).
Regras e armadilhas do projeto: ver `../CLAUDE.md`.
