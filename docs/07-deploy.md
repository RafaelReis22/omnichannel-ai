# Estratégia de Deploy

## Ambientes

`desenvolvimento` (local) → `staging` (réplica de produção, usada para validar canais reais com número de teste) → `produção`.

## Infraestrutura

| Fase | Escolha | Por quê |
|---|---|---|
| MVP | Plataforma gerenciada (ex.: Railway, Render ou Fly.io) para API, workers e banco | Velocidade de entrega — sem equipe de infraestrutura dedicada, o custo de operar Kubernetes cedo não se paga |
| Tração/mid-market | Migrar para AWS (ECS ou EKS) conforme necessidade de controle de rede, VPC própria e requisitos de compliance de clientes maiores | Clientes mid-market começam a pedir garantias de infraestrutura que plataformas gerenciadas genéricas não oferecem |

- Empacotamento: containers Docker desde o início, independente da plataforma de execução — evita lock-in prematuro e torna a migração descrita acima uma mudança de alvo de deploy, não de artefato.
- Infraestrutura como código (Terraform) a partir do momento em que a infraestrutura sai da plataforma gerenciada.

## CI/CD

- GitHub Actions: lint → type-check → testes → build → deploy.
- Deploy automático em `staging` a cada merge na branch principal; deploy em `produção` via aprovação manual (gate), nunca automático direto para produção no estágio atual do produto.
- Migrations de banco (Prisma Migrate) rodam como etapa própria do pipeline, antes do deploy da nova versão da aplicação, com plano de rollback documentado por migration.

## Estratégia de release

- Rolling deploy (múltiplas réplicas, substituição gradual) como padrão.
- Feature flags para funcionalidades de IA sensíveis (ex.: nova política de autonomia) — permite ativar por tenant piloto antes de expor a todos.
- Rollback: manter a versão anterior do container pronta para redeploy imediato; migrations de banco desenhadas para serem retrocompatíveis por pelo menos uma versão (expand-and-contract), evitando que um rollback de aplicação quebre por causa de uma migration não reversível.

## Observabilidade em produção

- Health checks por serviço (API, AI Worker, Channel Gateway) usados pelo orquestrador para decidir substituição de instância.
- Dashboards de: latência de resposta da IA, lag de fila por canal, taxa de erro de webhook, taxa de handoff para humano.
- Alertas para: canal desconectado, fila de IA acima de um limiar de lag, taxa de erro de API acima do normal.

## Backups e recuperação de desastre

- Backup automático diário do Postgres com retenção mínima de 30 dias; teste periódico de restauração (não só confiar que o backup existe).
- Object storage de mídia com versionamento habilitado.
- Objetivo inicial (MVP): RPO de 24h, RTO de poucas horas — revisitar exigências mais rígidas quando contratos enterprise (Fase 4+) as exigirem contratualmente.
